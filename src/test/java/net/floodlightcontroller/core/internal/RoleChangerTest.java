package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.internal.RoleChanger.PendingRoleRequestEntry;
import net.floodlightcontroller.core.internal.RoleChanger.RoleChangeTask;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.jboss.netty.channel.Channel;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.vendor.OFVendorData;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleRequestVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;

public class RoleChangerTest {
    public RoleChanger roleChanger;
    IFloodlightProviderService controller;
    
    @Before
    public void setUp() throws Exception {
        controller = createMock(IFloodlightProviderService.class);
        roleChanger = new RoleChanger(controller);
        BasicFactory factory = new BasicFactory();
        expect(controller.getOFMessageFactory()).andReturn(factory).anyTimes();
    }
    
    /**
     * Send a role request for SLAVE to a switch that doesn't support it. 
     * The connection should be closed.
     */
    @Test
    public void testSendRoleRequestSlaveNotSupported() throws Exception {
        LinkedList<IOFSwitch> switches = new LinkedList<IOFSwitch>();
        
        // a switch that doesn't support role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(false);
        sw1.disconnectOutputStream();
        switches.add(sw1);
        
        replay(sw1);
        roleChanger.sendRoleRequest(switches, Role.SLAVE, 123456);
        verify(sw1);
        
        // sendRoleRequest needs to remove the switch from the list since
        // it closed its connection
        assertTrue(switches.isEmpty());
    }
    
    /**
     * Send a role request for MASTER to a switch that doesn't support it. 
     * The connection should stay open.
     */
    @Test
    public void testSendRoleRequestMasterNotSupported() throws Exception {
        LinkedList<IOFSwitch> switches = new LinkedList<IOFSwitch>();
        
        // a switch that doesn't support role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(false);
        switches.add(sw1);
        
        replay(sw1);
        roleChanger.sendRoleRequest(switches, Role.MASTER, 123456);
        verify(sw1);
        
        assertEquals(1, switches.size());
    }
    
    /**
     * Check error handling 
     * hasn't had a role request send to it yet
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSendRoleRequestErrorHandling () throws Exception {
        LinkedList<IOFSwitch> switches = new LinkedList<IOFSwitch>();
        
        // a switch that supports role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // No support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(true);
        expect(sw1.getNextTransactionId()).andReturn(1);
        sw1.write((List<OFMessage>)EasyMock.anyObject(),
                (FloodlightContext)EasyMock.anyObject());
        expectLastCall().andThrow(new IOException());
        sw1.disconnectOutputStream();
        switches.add(sw1);
        
        replay(sw1, controller);
        roleChanger.sendRoleRequest(switches, Role.MASTER, 123456);
        verify(sw1, controller);
        
        assertTrue(switches.isEmpty());
    }
    
    /**
     * Send a role request a switch that supports it and one that 
     * hasn't had a role request send to it yet
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSendRoleRequestSupported() throws Exception {
        LinkedList<IOFSwitch> switches = new LinkedList<IOFSwitch>();
        
        // a switch that supports role requests
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        // Support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(true);
        expect(sw1.getNextTransactionId()).andReturn(1);
        sw1.write((List<OFMessage>)EasyMock.anyObject(),
                (FloodlightContext)EasyMock.anyObject());
        switches.add(sw1);
        
        // second switch
        OFSwitchImpl sw2 = EasyMock.createMock(OFSwitchImpl.class);
        // No role request yet
        expect(sw2.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(null);
        expect(sw2.getNextTransactionId()).andReturn(1);
        sw2.write((List<OFMessage>)EasyMock.anyObject(),
                (FloodlightContext)EasyMock.anyObject());
        switches.add(sw2);        
        
        replay(sw1, sw2, controller);
        roleChanger.sendRoleRequest(switches, Role.MASTER, 123456);
        verify(sw1, sw2, controller);
        
        assertEquals(2, switches.size());
    }
    
    @Test
    public void testVerifyRoleReplyReceived() throws Exception {
        Collection<IOFSwitch> switches = new LinkedList<IOFSwitch>();
        
        // Add a switch that has received a role reply
        OFSwitchImpl sw1 = EasyMock.createMock(OFSwitchImpl.class);
        LinkedList<PendingRoleRequestEntry> pendingList1 =
                new LinkedList<PendingRoleRequestEntry>();
        roleChanger.pendingRequestMap.put(sw1, pendingList1);
        switches.add(sw1);
        
        // Add a switch that has not yet received a role reply
        OFSwitchImpl sw2 = EasyMock.createMock(OFSwitchImpl.class);
        LinkedList<PendingRoleRequestEntry> pendingList2 =
                new LinkedList<PendingRoleRequestEntry>();
        roleChanger.pendingRequestMap.put(sw2, pendingList2);
        PendingRoleRequestEntry entry =
                new PendingRoleRequestEntry(1, Role.MASTER, 123456);
        pendingList2.add(entry);
        // Timed out switch should disconnect
        sw2.setHARole(null, false);
        EasyMock.expectLastCall();
        switches.add(sw2);
        
        
        replay(sw1, sw2);
        roleChanger.verifyRoleReplyReceived(switches, 123456);
        verify(sw1, sw2);
        
        assertEquals(2, switches.size());
    }
    
    @Test
    public void testRoleChangeTask() {
        @SuppressWarnings("unchecked")
        Collection<IOFSwitch> switches = 
                EasyMock.createMock(Collection.class);
        long now = System.nanoTime();
        long dt1 = 10 * 1000*1000*1000L;
        long dt2 = 20 * 1000*1000*1000L;
        long dt3 = 15 * 1000*1000*1000L;
        RoleChangeTask t1 = new RoleChangeTask(switches, null, now+dt1);
        RoleChangeTask t2 = new RoleChangeTask(switches, null, now+dt2);
        RoleChangeTask t3 = new RoleChangeTask(switches, null, now+dt3);
        
        // FIXME: cannot test comparison against self. grrr
        //assertTrue( t1.compareTo(t1) <= 0 );
        assertTrue( t1.compareTo(t2) < 0 );
        assertTrue( t1.compareTo(t3) < 0 );
        
        assertTrue( t2.compareTo(t1) > 0 );
        //assertTrue( t2.compareTo(t2) <= 0 );
        assertTrue( t2.compareTo(t3) > 0 );
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitRequest() throws Exception {
        LinkedList<IOFSwitch> switches = new LinkedList<IOFSwitch>();
        roleChanger.timeout = 500*1000*1000; // 500 ms
        
        // a switch that supports role requests
        OFSwitchImpl sw1 = EasyMock.createStrictMock(OFSwitchImpl.class);
        // Support for NX_ROLE
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(true);
        expect(sw1.getNextTransactionId()).andReturn(1);
        sw1.write((List<OFMessage>)EasyMock.anyObject(),
                (FloodlightContext)EasyMock.anyObject());
        // Second request
        expect(sw1.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
                .andReturn(true);
        expect(sw1.getNextTransactionId()).andReturn(2);
        sw1.write((List<OFMessage>)EasyMock.anyObject(),
                (FloodlightContext)EasyMock.anyObject());
        switches.add(sw1);
        
        replay(sw1, controller);
        roleChanger.submitRequest(switches, Role.MASTER);
        roleChanger.submitRequest(switches, Role.SLAVE);
        // Wait until role request has been sent. 
        // TODO: need to get rid of this sleep somehow
        Thread.sleep(100);
        // Now there should be exactly one timeout task pending
        assertEquals(2, roleChanger.pendingTasks.size());
        // Make sure it's indeed a timeout task
        assertSame(RoleChanger.RoleChangeTask.Type.TIMEOUT, 
                     roleChanger.pendingTasks.peek().type);
        // Check that RoleChanger indeed made a copy of switches collection
        assertNotSame(switches, roleChanger.pendingTasks.peek().switches);
        
        // Wait until the timeout triggers 
        // TODO: get rid of this sleep too.
        Thread.sleep(500);
        assertEquals(0, roleChanger.pendingTasks.size());
        verify(sw1, controller);
        
    }
    
    // Helper function
    protected void setupPendingRoleRequest(IOFSwitch sw, int xid, Role role,
            long cookie) {
        LinkedList<PendingRoleRequestEntry> pendingList =
                new LinkedList<PendingRoleRequestEntry>();
        roleChanger.pendingRequestMap.put(sw, pendingList);
        PendingRoleRequestEntry entry =
                new PendingRoleRequestEntry(xid, role, cookie);
        pendingList.add(entry);
    }
    

    @Test
    public void testDeliverRoleReplyOk() {
        // test normal case
        int xid = (int) System.currentTimeMillis();
        long cookie = System.nanoTime();
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        roleChanger.deliverRoleReply(sw, xid, role);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(role, sw.role);
        assertEquals(0, roleChanger.pendingRequestMap.get(sw).size());
    }
    
    @Test
    public void testDeliverRoleReplyOkRepeated() {
        // test normal case. Not the first role reply
        int xid = (int) System.currentTimeMillis();
        long cookie = System.nanoTime();
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
        roleChanger.deliverRoleReply(sw, xid, role);
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(role, sw.role);
        assertEquals(0, roleChanger.pendingRequestMap.get(sw).size());
    }
    
    @Test
    public void testDeliverRoleReplyNonePending() {
        // nothing pending 
        OFSwitchImpl sw = new OFSwitchImpl();
        Channel ch = createMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(ch.getRemoteAddress()).andReturn(sa).anyTimes();
        sw.setChannel(ch);
        roleChanger.deliverRoleReply(sw, 1, Role.MASTER);
        assertEquals(null, sw.role);
    }
    
    @Test
    public void testDeliverRoleReplyWrongXid() {
        // wrong xid received 
        int xid = (int) System.currentTimeMillis();
        long cookie = System.nanoTime();
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        Channel ch = createMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(ch.getRemoteAddress()).andReturn(sa).anyTimes();
        sw.setChannel(ch);
        expect(ch.close()).andReturn(null);
        replay(ch);
        roleChanger.deliverRoleReply(sw, xid+1, role);
        verify(ch);
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(0, roleChanger.pendingRequestMap.get(sw).size());
    }
    
    @Test
    public void testDeliverRoleReplyWrongRole() {
        // correct xid but incorrect role received
        int xid = (int) System.currentTimeMillis();
        long cookie = System.nanoTime();
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        Channel ch = createMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(ch.getRemoteAddress()).andReturn(sa).anyTimes();
        sw.setChannel(ch);
        expect(ch.close()).andReturn(null);
        replay(ch);
        roleChanger.deliverRoleReply(sw, xid, Role.SLAVE);
        verify(ch);
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(0, roleChanger.pendingRequestMap.get(sw).size());
    }
    
    @Test
    public void testCheckFirstPendingRoleRequestXid() {
        int xid = 54321;
        long cookie = 232323;
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        assertEquals(true,
                roleChanger.checkFirstPendingRoleRequestXid(sw, xid));
        assertEquals(false,
                roleChanger.checkFirstPendingRoleRequestXid(sw, 0));
        roleChanger.pendingRequestMap.get(sw).clear();
        assertEquals(false,
                roleChanger.checkFirstPendingRoleRequestXid(sw, xid));
    }
    
    @Test
    public void testCheckFirstPendingRoleRequestCookie() {
        int xid = 54321;
        long cookie = 232323;
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        assertEquals(true,
                roleChanger.checkFirstPendingRoleRequestCookie(sw, cookie));
        assertEquals(false,
                roleChanger.checkFirstPendingRoleRequestCookie(sw, 0));
        roleChanger.pendingRequestMap.get(sw).clear();
        assertEquals(false,
                roleChanger.checkFirstPendingRoleRequestCookie(sw, cookie));
    }
    
    @Test
    public void testDeliverRoleRequestNotSupported () {
        // normal case. xid is pending 
        int xid = (int) System.currentTimeMillis();
        long cookie = System.nanoTime();
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        roleChanger.deliverRoleRequestNotSupported(sw, xid);
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(role, sw.role);
        assertEquals(0, roleChanger.pendingRequestMap.get(sw).size());
    }
    
    @Test
    public void testDeliverRoleRequestNotSupportedNonePending() {
        // nothing pending 
        OFSwitchImpl sw = new OFSwitchImpl();
        Channel ch = createMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(ch.getRemoteAddress()).andReturn(sa).anyTimes();
        sw.setChannel(ch);
        roleChanger.deliverRoleRequestNotSupported(sw, 1);
        assertEquals(null, sw.role);
    }
    
    @Test
    public void testDeliverRoleRequestNotSupportedWrongXid() {
        // wrong xid received 
        // wrong xid received 
        int xid = (int) System.currentTimeMillis();
        long cookie = System.nanoTime();
        Role role = Role.MASTER;
        OFSwitchImpl sw = new OFSwitchImpl();
        setupPendingRoleRequest(sw, xid, role, cookie);
        Channel ch = createMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(ch.getRemoteAddress()).andReturn(sa).anyTimes();
        sw.setChannel(ch);
        expect(ch.close()).andReturn(null);
        replay(ch);
        roleChanger.deliverRoleRequestNotSupported(sw, xid+1);
        verify(ch);
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        assertEquals(0, roleChanger.pendingRequestMap.get(sw).size());
    }
    
    public void doSendNxRoleRequest(Role role, int nx_role) throws Exception {
        long cookie = System.nanoTime();
        OFSwitchImpl sw = new OFSwitchImpl();
        Channel ch = createMock(Channel.class);
        sw.setChannel(ch);
        sw.setFloodlightProvider(controller);
        
        // verify that the correct OFMessage is sent
        Capture<List<OFMessage>> msgCapture = new Capture<List<OFMessage>>();
        // expect(sw.channel.getRemoteAddress()).andReturn(null);
        controller.handleOutgoingMessage(
                (IOFSwitch)EasyMock.anyObject(),
                (OFMessage)EasyMock.anyObject(),
                (FloodlightContext)EasyMock.anyObject());
        expect(ch.write(capture(msgCapture))).andReturn(null);
        replay(ch, controller);
        int xid = roleChanger.sendHARoleRequest(sw, role, cookie);
        verify(ch, controller);
        List<OFMessage> msgList = msgCapture.getValue();
        assertEquals(1, msgList.size());
        OFMessage msg = msgList.get(0);
        assertEquals("Transaction Ids must match", xid, msg.getXid()); 
        assertTrue("Message must be an OFVendor type", msg instanceof OFVendor);
        assertEquals(OFType.VENDOR, msg.getType());
        OFVendor vendorMsg = (OFVendor)msg;
        assertEquals("Vendor message must be vendor Nicira",
                     OFNiciraVendorData.NX_VENDOR_ID, vendorMsg.getVendor());
        OFVendorData vendorData = vendorMsg.getVendorData();
        assertTrue("Vendor Data must be an OFRoleRequestVendorData",
                     vendorData instanceof OFRoleRequestVendorData);
        OFRoleRequestVendorData roleRequest = (OFRoleRequestVendorData)vendorData;
        assertEquals(nx_role, roleRequest.getRole());
        
        reset(ch);
    }
    
    @Test
    public void testSendNxRoleRequestMaster() throws Exception {
        doSendNxRoleRequest(Role.MASTER, OFRoleVendorData.NX_ROLE_MASTER);
    }
    
    @Test
    public void testSendNxRoleRequestSlave() throws Exception {
        doSendNxRoleRequest(Role.SLAVE, OFRoleVendorData.NX_ROLE_SLAVE);
    }

    @Test
    public void testSendNxRoleRequestEqual() throws Exception {
        doSendNxRoleRequest(Role.EQUAL, OFRoleVendorData.NX_ROLE_OTHER);
    }


}
