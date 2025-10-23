package com.scheduling.framework.resource.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.ResourceScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resource Scheduler que asigna tareas en round-robin entre recursos
 */
public class RoundRobinResourceScheduler implements ResourceScheduler {
    
    private static final long serialVersionUID = 1L;
    
    private List<ProcessingResource> resources;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    @Override
    public void initialize(List<ProcessingResource> resources) {
        this.resources = new ArrayList<>(resources);
    }
    
    @Override
    public synchronized ProcessingResource assignTaskToResource(Task task) {
        if (resources.isEmpty()) {
            return null;
        }
        
        // Round robin entre recursos
        int attempts = 0;
        while (attempts < resources.size()) {
            int index = currentIndex.getAndIncrement() % resources.size();
            ProcessingResource resource = resources.get(index);
            
            if (resource.assignTask(task)) {
                return resource;
            }
            attempts++;
        }
        
        return null; // No hay recursos disponibles
    }
    
    @Override
    public synchronized void releaseResource(String resourceId, Task completedTask) {
        resources.stream()
            .filter(r -> r.getResourceId().equals(resourceId))
            .findFirst()
            .ifPresent(ProcessingResource::releaseTask);
    }
    
    @Override
    public List<ProcessingResource> getAvailableResources() {
        return resources.stream()
            .filter(ProcessingResource::isAvailable)
            .toList();
    }
    
    @Override
    public String getAlgorithmName() {
        return "Round Robin Resource Scheduler";
    }
    
    @Override
    public void reset() {
        currentIndex.set(0);
        resources.forEach(ProcessingResource::releaseTask);
    }
}