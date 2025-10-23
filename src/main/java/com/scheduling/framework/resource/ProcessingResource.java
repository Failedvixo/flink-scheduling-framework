package com.scheduling.framework.resource;

import com.scheduling.framework.model.Task;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Representa un recurso de procesamiento (CPU, slot, máquina)
 */
public class ProcessingResource implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String resourceId;
    private final int capacity;
    private final AtomicInteger currentLoad;
    private volatile Task currentTask;
    private final long creationTime;
    
    public ProcessingResource(String resourceId, int capacity) {
        this.resourceId = resourceId;
        this.capacity = capacity;
        this.currentLoad = new AtomicInteger(0);
        this.currentTask = null;
        this.creationTime = System.currentTimeMillis();
    }
    
    public boolean isAvailable() {
        return currentLoad.get() < capacity;
    }
    
    public boolean assignTask(Task task) {
        if (isAvailable()) {
            currentLoad.incrementAndGet();
            this.currentTask = task;
            return true;
        }
        return false;
    }
    
    public void releaseTask() {
        currentLoad.decrementAndGet();
        this.currentTask = null;
    }
    
    public String getResourceId() { return resourceId; }
    public int getCapacity() { return capacity; }
    public int getCurrentLoad() { return currentLoad.get(); }
    public Task getCurrentTask() { return currentTask; }
    public double getUtilization() { return (double) currentLoad.get() / capacity; }
    
    @Override
    public String toString() {
        return String.format("Resource{id='%s', load=%d/%d, util=%.2f%%}", 
            resourceId, currentLoad.get(), capacity, getUtilization() * 100);
    }
}