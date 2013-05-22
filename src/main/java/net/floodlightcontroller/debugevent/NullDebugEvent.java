package net.floodlightcontroller.debugevent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class NullDebugEvent implements IFloodlightModule, IDebugEventService {

    @Override
    public int registerEvent(String moduleName, String eventName,
                             boolean flushNow, String eventDescription,
                             EventType et, int bufferCapacity,
                             String formatStr, Object[] params) {
        return -1;
    }

    @Override
    public void updateEvent(int eventId, Object[] params) {

    }

    @Override
    public void flushEvents() {

    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(IDebugEventService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        m.put(IDebugEventService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {

    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {

    }

    @Override
    public boolean containsMEName(String param) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean containsModName(String param) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<DebugEventInfo> getAllEventHistory() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<DebugEventInfo> getModuleEventHistory(String param) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DebugEventInfo getSingleEventHistory(String param) {
        // TODO Auto-generated method stub
        return null;
    }


}
