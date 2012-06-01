package net.floodlightcontroller.flowcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.ListenerDispatcher;
import net.floodlightcontroller.flowcache.IFlowReconcileListener;
import net.floodlightcontroller.flowcache.OFMatchReconcile;

import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * This class registers for various network events that may require flow 
 * reconciliation. Examples include host-move, new attachment-point,
 * switch connection etc. 
 * 
 * @author subrata
 *
 */
public class FlowReconcileManager 
        implements IFloodlightModule, IFlowReconcileService {

    /** The logger. */
    private static Logger logger =
                        LoggerFactory.getLogger(FlowReconcileManager.class);

    /**
     * The list of flow reconcile listeners that have registered to get
     * flow reconcile callbacks. Such callbacks are invoked, for example, when
     * a switch with existing flow-mods joins this controller and those flows
     * need to be reconciled with the current configuration of the controller.
     */
    protected ListenerDispatcher<OFType, IFlowReconcileListener> flowReconcileListeners;
    
    public OFMatchReconcile newOFMatchReconcile() {
        return new OFMatchReconcile();
    }

    @Override
    public synchronized void addFlowReconcileListener(IFlowReconcileListener listener) {
        flowReconcileListeners.addListener(OFType.FLOW_MOD, listener);

        if (logger.isDebugEnabled()) {
            StringBuffer sb = new StringBuffer();
            sb.append("FlowReconcileManager FlowMod Listeners: ");
            for (IFlowReconcileListener l : flowReconcileListeners.getOrderedListeners()) {
                sb.append(l.getName());
                sb.append(",");
            }
            logger.debug(sb.toString());
        }
    }

    @Override
    public synchronized void removeFlowReconcileListener(IFlowReconcileListener listener) {
        flowReconcileListeners.removeListener(listener);
    }
    
    @Override
    public synchronized void clearFlowReconcileListeners() {
        flowReconcileListeners.clearListeners();
    }
    
    /**
     * Reconcile flow.
     *
     * @param ofmRcIn the ofm rc in
     */
    public void reconcileFlow(OFMatchReconcile ofmRcIn) {
        if (logger.isTraceEnabled()) {
            logger.trace("Reconciliating flow: {}", ofmRcIn.toString());
        }
        ArrayList<OFMatchReconcile> ofmRcList =
                                            new ArrayList<OFMatchReconcile>();
        ofmRcList.add(ofmRcIn);
        // Run the flow through all the flow reconcile listeners
        IFlowReconcileListener.Command retCmd;
        for (IFlowReconcileListener flowReconciler : flowReconcileListeners.getOrderedListeners()) {
            if (logger.isTraceEnabled()) {
                logger.trace("Reconciliatng flow: call listener {}", flowReconciler.getName());
            }
            retCmd = flowReconciler.reconcileFlows(ofmRcList);
            if (retCmd == IFlowReconcileListener.Command.STOP) {
                break;
            }
        }
    }
    
    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> 
                                                            getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> 
                                                    getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        flowReconcileListeners = 
                new ListenerDispatcher<OFType, IFlowReconcileListener>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
    }
}

