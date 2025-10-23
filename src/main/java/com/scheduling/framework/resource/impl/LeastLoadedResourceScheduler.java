package com.scheduling.framework.resource.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.ResourceScheduler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resource Scheduler que asigna tareas al recurso con menor carga
 */
public class LeastLoadedResourceScheduler implements ResourceScheduler {
    
    private static final long serialVersionUID = 1L;
    
    private List<ProcessingResource> resources;
    
    @Override
    public void initialize(List<ProcessingResource> resources) {
        this.resources = new ArrayList<>(resources);
    }
    
    @Override
    public synchronized ProcessingResource assignTaskToResource(Task task) {
        // Encontrar el recurso con menor carga
        ProcessingResource leastLoaded = resources.stream()
            .filter(ProcessingResource::isAvailable)
            .min(Comparator.comparingInt(ProcessingResource::getCurrentLoad))
            .orElse(null);
        
        if (leastLoaded != null && leastLoaded.assignTask(task)) {
            return leastLoaded;
        }
        
        return null;
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
        return "Least Loaded Resource Scheduler";
    }
    
    @Override
    public void reset() {
        resources.forEach(ProcessingResource::releaseTask);
    }
}