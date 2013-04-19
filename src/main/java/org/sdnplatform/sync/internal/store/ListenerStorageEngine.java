package org.sdnplatform.sync.internal.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.IStoreListener.UpdateType;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.sdnplatform.sync.internal.util.ByteArray;


/**
 * A storage engine that proxies to another storage engine and notifies
 * registered listeners of changes
 * @author readams
 */
public class ListenerStorageEngine 
    implements IStorageEngine<ByteArray, byte[]> {

    /**
     * Listeners for this store
     */
    protected List<MappingStoreListener> listeners = 
            new ArrayList<MappingStoreListener>();

    /**
     * The local storage for this storage engine
     */
    protected IStorageEngine<ByteArray, byte[]> localStorage;

    /**
     * Debug counter service
     */
    protected IDebugCounterService debugCounter;
    
    /**
     * Allocate new {@link ListenerStorageEngine}
     * @param localStorage the delegate store
     * @param debugCounter debug counter service
     */
    public ListenerStorageEngine(IStorageEngine<ByteArray,
                                                byte[]> localStorage,
                                                IDebugCounterService debugCounter) {
        this.localStorage = localStorage;
        this.debugCounter = debugCounter;
    }

    // *************************
    // StorageEngine<Key,byte[]>
    // *************************

    @Override
    public List<Versioned<byte[]>> get(ByteArray key) throws SyncException {
        updateCounter(SyncManager.COUNTER_GETS);
        return localStorage.get(key);
    }

    @Override
    public IClosableIterator<Entry<ByteArray,List<Versioned<byte[]>>>> entries() {
        updateCounter(SyncManager.COUNTER_ITERATORS);
        return localStorage.entries();
    }

    @Override
    public void put(ByteArray key, Versioned<byte[]> value)
            throws SyncException {
        updateCounter(SyncManager.COUNTER_PUTS);
        localStorage.put(key, value);
        notifyListeners(key, UpdateType.LOCAL);
    }

    @Override
    public IClosableIterator<ByteArray> keys() {
        return localStorage.keys();
    }

    @Override
    public void truncate() throws SyncException {
        localStorage.truncate();
    }

    @Override
    public String getName() {
        return localStorage.getName();
    }

    @Override
    public void close() throws SyncException {
        localStorage.close();
    }

    @Override
    public List<IVersion> getVersions(ByteArray key) throws SyncException {
        return localStorage.getVersions(key);
    }

    @Override
    public boolean writeSyncValue(ByteArray key,
                                  Iterable<Versioned<byte[]>> values) {
        boolean r = localStorage.writeSyncValue(key, values);
        if (r) notifyListeners(key, UpdateType.REMOTE);
        return r;
    }

    @Override
    public void cleanupTask() throws SyncException {
        localStorage.cleanupTask();
    }

    @Override
    public boolean isPersistent() {
        return localStorage.isPersistent();
    }

    @Override
    public void setTombstoneInterval(int interval) {
        localStorage.setTombstoneInterval(interval);
    }
    
    // *********************
    // ListenerStorageEngine
    // *********************

    public void addListener(MappingStoreListener listener) {
        listeners.add(listener);
    }

    protected void notifyListeners(ByteArray key, UpdateType type) {
        notifyListeners(Collections.singleton(key).iterator(), type);
    }

    protected void notifyListeners(Iterator<ByteArray> keys, UpdateType type) {
        for (MappingStoreListener msl : listeners) {
            msl.notify(keys, type);
        }
    }

    protected void updateCounter(String counterName) {
        if (debugCounter != null) {
            debugCounter.updateCounter(counterName);
        }
    }    
}
