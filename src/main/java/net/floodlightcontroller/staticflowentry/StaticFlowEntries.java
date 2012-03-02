package net.floodlightcontroller.staticflowentry;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;


import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionNetworkTypeOfService;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.protocol.action.OFActionVirtualLanPriorityCodePoint;

public class StaticFlowEntries {
    
    private static class SubActionStruct {
        OFAction action;
        int      len;
    }
    
    public static String getEntryNameFromJson(String fmJson) throws IOException{
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(fmJson);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            
            if (n == "name")
                return jp.getText();
        }
        
        return null;
    }
    
    /**
     * Turns a JSON formatted Static Flow Pusher string into a storage entry
     * Expects a string in JSON along the lines of:
     *        {
     *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
     *            "name":         "flow-mod-1",
     *            "cookie":       "0",
     *            "priority":     "32768",
     *            "ingress-port": "1",
     *            "actions":      "output=2",
     *        }
     * @param fmJson The JSON formatted static flow pusher entry
     * @return The map of the storage entry
     * @throws IOException If there was an error parsing the JSON
     */
    public static Map<String, Object> jsonToStorageEntry(String fmJson) throws IOException {
        Map<String, Object> entry = new HashMap<String, Object>();
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(fmJson);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            
            if (n == "name")
                entry.put(StaticFlowEntryPusher.COLUMN_NAME, jp.getText());
            else if (n == "switch")
                entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, jp.getText());
            else if (n == "actions")
                entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, jp.getText());
            else if (n == "priority")
                entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, jp.getText());
            else if (n == "active")
                entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, jp.getText());
            else if (n == "wildcards")
                entry.put(StaticFlowEntryPusher.COLUMN_WILDCARD, jp.getText());
            else if (n == "ingress-port")
                entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, jp.getText());
            else if (n == "src-mac")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, jp.getText());
            else if (n == "dst-mac")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, jp.getText());
            else if (n == "vlan-id")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, jp.getText());
            else if (n == "vlan-priority")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, jp.getText());
            else if (n == "ether-type")
                entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, jp.getText());
            else if (n == "tos-bits")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_TOS, jp.getText());
            else if (n == "protocol")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, jp.getText());
            else if (n == "src-ip")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, jp.getText());
            else if (n == "dst-ip")
                entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, jp.getText());
            else if (n == "src-port")
                entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, jp.getText());
            else if (n == "dst-port")
                entry.put(StaticFlowEntryPusher.COLUMN_TP_DST, jp.getText());
        }
        
        return entry;
    }
    
    public static void parseActionString(String entryName, String switchName,
            OFFlowMod flowMod, String actionstr, Logger log) {
        List<OFAction> actions = new LinkedList<OFAction>();
        int actionsLength = 0;
        if (actionstr != null) {
            actionstr = actionstr.toLowerCase();
            for (String subaction : actionstr.split(",")) {
                String action = subaction.split("[=:]")[0];
                SubActionStruct subaction_struct = null;
                
                if (action.equals("output")) {
                    subaction_struct = StaticFlowEntries.decode_output(subaction, log);
                }
                else if (action.equals("enqueue")) {
                    subaction_struct = decode_enqueue(subaction, log);
                }
                else if (action.equals("strip-vlan")) {
                    subaction_struct = decode_strip_vlan(subaction, log);
                }
                else if (action.equals("set-vlan-id")) {
                    subaction_struct = decode_set_vlan_id(subaction, log);
                }
                else if (action.equals("set-vlan-priority")) {
                    subaction_struct = decode_set_vlan_priority(subaction, log);
                }
                else if (action.equals("set-src-mac")) {
                    subaction_struct = decode_set_src_mac(subaction, log);
                }
                else if (action.equals("set-dst-mac")) {
                    subaction_struct = decode_set_dst_mac(subaction, log);
                }
                else if (action.equals("set-tos-bits")) {
                    subaction_struct = decode_set_tos_bits(subaction, log);
                }
                else if (action.equals("set-src-ip")) {
                    subaction_struct = decode_set_src_ip(subaction, log);
                }
                else if (action.equals("set-dst-ip")) {
                    subaction_struct = decode_set_dst_ip(subaction, log);
                }
                else if (action.equals("set-src-port")) {
                    subaction_struct = decode_set_src_port(subaction, log);
                }
                else if (action.equals("set-dst-port")) {
                    subaction_struct = decode_set_dst_port(subaction, log);
                }
                else {
                    log.error("  Unexpected action '{}', '{}'", action, subaction);
                }
                
                if (subaction_struct != null) {
                    actions.add(subaction_struct.action);
                    actionsLength += subaction_struct.len;
                }
            }
        }
        log.debug("  action {}", actions);
        
        flowMod.setActions(actions);
        flowMod.setLengthU(OFFlowMod.MINIMUM_LENGTH + actionsLength);
    } 
    private static SubActionStruct decode_output(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("output=(?:((?:0x)?\\d+)|(all)|(controller)|(local)|(ingress-port)|(normal)|(flood))").matcher(subaction);
        if (n.matches()) {
            OFActionOutput action = new OFActionOutput();
            action.setMaxLength((short) 0);
            short port = OFPort.OFPP_NONE.getValue();
            if (n.group(1) != null) {
                try {
                    port = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid port in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else if (n.group(2) != null)
                port = OFPort.OFPP_ALL.getValue();
            else if (n.group(3) != null)
                port = OFPort.OFPP_CONTROLLER.getValue();
            else if (n.group(4) != null)
                port = OFPort.OFPP_LOCAL.getValue();
            else if (n.group(5) != null)
                port = OFPort.OFPP_IN_PORT.getValue();
            else if (n.group(6) != null)
                port = OFPort.OFPP_NORMAL.getValue();
            else if (n.group(7) != null)
                port = OFPort.OFPP_FLOOD.getValue();
            action.setPort(port);
            log.debug("  action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionOutput.MINIMUM_LENGTH;
        }
        else {
            log.error("  Invalid action: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    private static SubActionStruct decode_enqueue(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;
        
        n = Pattern.compile("enqueue=(?:((?:0x)?\\d+)\\:((?:0x)?\\d+))").matcher(subaction);
        if (n.matches()) {
            short portnum = 0;
            if (n.group(1) != null) {
                try {
                    portnum = get_short(n.group(1));
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid port-num in: '{}' (error ignored)", subaction);
                    return null;
                }
            }

            int queueid = 0;
            if (n.group(2) != null) {
                try {
                    queueid = get_int(n.group(2));
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid queue-id in: '{}' (error ignored)", subaction);
                    return null;
               }
            }
            
            OFActionEnqueue action = new OFActionEnqueue();
            action.setPort(portnum);
            action.setQueueId(queueid);
            log.debug("  action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionEnqueue.MINIMUM_LENGTH;
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }
        
        return sa;
    }
    
    private static SubActionStruct decode_strip_vlan(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    

        n = Pattern.compile("strip-vlan").matcher(subaction);
        if (n.matches()) {
            OFActionStripVirtualLan action = new OFActionStripVirtualLan();
            log.debug("  action {}", action);
            
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionStripVirtualLan.MINIMUM_LENGTH;
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }
        

        return sa;
    }
    
    private static SubActionStruct decode_set_vlan_id(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    
        
        n = Pattern.compile("set-vlan-id=((?:0x)?\\d+)").matcher(subaction);
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    short vlanid = get_short(n.group(1));
                    OFActionVirtualLanIdentifier action = new OFActionVirtualLanIdentifier();
                    action.setVirtualLanIdentifier(vlanid);
                    log.debug("  action {}", action);

                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid VLAN in: {} (error ignored)", subaction);
                    return null;
                }
            }          
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_vlan_priority(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    
        
        n = Pattern.compile("set-vlan-priority=((?:0x)?\\d+)").matcher(subaction);
        if (n.matches()) {            
            if (n.group(1) != null) {
                try {
                    byte prior = get_byte(n.group(1));
                    OFActionVirtualLanPriorityCodePoint action = new OFActionVirtualLanPriorityCodePoint();
                    action.setVirtualLanPriorityCodePoint(prior);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionVirtualLanPriorityCodePoint.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid VLAN priority in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_src_mac(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    
        
        n = Pattern.compile("set-src-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction);
        if (n.matches()) {
            byte[] macaddr = get_mac_addr(n, subaction, log);
            if (macaddr != null) {
                OFActionDataLayerSource action = new OFActionDataLayerSource();
                action.setDataLayerAddress(macaddr);
                log.debug("  action {}", action);

                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionDataLayerSource.MINIMUM_LENGTH;
            }            
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_mac(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    
        
        n = Pattern.compile("set-dst-mac=(?:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+)\\:(\\p{XDigit}+))").matcher(subaction);
        if (n.matches()) {
            byte[] macaddr = get_mac_addr(n, subaction, log);            
            if (macaddr != null) {
                OFActionDataLayerDestination action = new OFActionDataLayerDestination();
                action.setDataLayerAddress(macaddr);
                log.debug("  action {}", action);
                
                sa = new SubActionStruct();
                sa.action = action;
                sa.len = OFActionDataLayerDestination.MINIMUM_LENGTH;
            }
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_tos_bits(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    

        n = Pattern.compile("set-tos-bits=((?:0x)?\\d+)").matcher(subaction);
        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    byte tosbits = get_byte(n.group(1));
                    OFActionNetworkTypeOfService action = new OFActionNetworkTypeOfService();
                    action.setNetworkTypeOfService(tosbits);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionNetworkTypeOfService.MINIMUM_LENGTH;
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }
    
    private static SubActionStruct decode_set_src_ip(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    

        n = Pattern.compile("set-src-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);
        if (n.matches()) {
            int ipaddr = get_ip_addr(n, subaction, log);
            OFActionNetworkLayerSource action = new OFActionNetworkLayerSource();
            action.setNetworkAddress(ipaddr);
            log.debug("  action {}", action);

            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionNetworkLayerSource.MINIMUM_LENGTH;
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_ip(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    

        n = Pattern.compile("set-dst-ip=(?:(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))").matcher(subaction);
        if (n.matches()) {
            int ipaddr = get_ip_addr(n, subaction, log);
            OFActionNetworkLayerDestination action = new OFActionNetworkLayerDestination();
            action.setNetworkAddress(ipaddr);
            log.debug("  action {}", action);
 
            sa = new SubActionStruct();
            sa.action = action;
            sa.len = OFActionNetworkLayerDestination.MINIMUM_LENGTH;
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_src_port(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    

        n = Pattern.compile("set-src-port=((?:0x)?\\d+)").matcher(subaction);
        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    short portnum = get_short(n.group(1));
                    OFActionTransportLayerSource action = new OFActionTransportLayerSource();
                    action.setTransportPort(portnum);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionTransportLayerSource.MINIMUM_LENGTH;;
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid src-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static SubActionStruct decode_set_dst_port(String subaction, Logger log) {
        SubActionStruct sa = null;
        Matcher n;    

        n = Pattern.compile("set-dst-port=((?:0x)?\\d+)").matcher(subaction);
        if (n.matches()) {
            if (n.group(1) != null) {
                try {
                    short portnum = get_short(n.group(1));
                    OFActionTransportLayerDestination action = new OFActionTransportLayerDestination();
                    action.setTransportPort(portnum);
                    log.debug("  action {}", action);
                    
                    sa = new SubActionStruct();
                    sa.action = action;
                    sa.len = OFActionTransportLayerDestination.MINIMUM_LENGTH;;
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid dst-port in: {} (error ignored)", subaction);
                    return null;
                }
            }
        }
        else {
            log.debug("  Invalid action: '{}'", subaction);
            return null;
        }

        return sa;
    }

    private static byte[] get_mac_addr(Matcher n, String subaction, Logger log) {
        byte[] macaddr = new byte[6];
        
        for (int i=0; i<6; i++) {
            if (n.group(i+1) != null) {
                try {
                    macaddr[i] = get_byte("0x" + n.group(i+1));
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid src-mac in: '{}' (error ignored)", subaction);
                    return null;
                }
            }
            else { 
                log.debug("  Invalid src-mac in: '{}' (null, error ignored)", subaction);
                return null;
            }
        }
        
        return macaddr;
    }
    
    private static int get_ip_addr(Matcher n, String subaction, Logger log) {
        int ipaddr = 0;

        for (int i=0; i<4; i++) {
            if (n.group(i+1) != null) {
                try {
                    ipaddr = ipaddr<<8;
                    ipaddr = ipaddr | get_int(n.group(i+1));
                }
                catch (NumberFormatException e) {
                    log.debug("  Invalid src-ip in: '{}' (error ignored)", subaction);
                    return 0;
                }
            }
            else {
                log.debug("  Invalid src-ip in: '{}' (null, error ignored)", subaction);
                return 0;
            }
        }
        
        return ipaddr;
    }
    
    // Parse int as decimal, hex (start with 0x or #) or octal (starts with 0)
    private static int get_int(String str)
    {
        return (int)Integer.decode(str);
    }
   
    // Parse short as decimal, hex (start with 0x or #) or octal (starts with 0)
    private static short get_short(String str)
    {
        return (short)(int)Integer.decode(str);
    }
   
    // Parse byte as decimal, hex (start with 0x or #) or octal (starts with 0)
    private static byte get_byte(String str)
    {
        return Integer.decode(str).byteValue();
    }

}

