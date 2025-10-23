package com.scheduling.framework.resource.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.ResourceScheduler;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource Scheduler FCFS: Asigna tareas al primer recurso disponible (First Come First Serve)
 */
public class FCFSResourceScheduler implements ResourceScheduler {
    
    private static final long serialVersionUID = 1L;
    
    private List<ProcessingResource> resources;
    
    @Override
    public void initialize(List<ProcessingResource> resources) {
        this.resources = new ArrayList<>(resources);
    }
    
    @Override
    public synchronized ProcessingResource assignTaskToResource(Task task) {
        // FCFS: Asignar al primer recurso disponible en orden
        for (ProcessingResource resource : resources) {
            if (resource.assignTask(task)) {
                return resource;
            }
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
        return "FCFS Resource Scheduler";
    }
    
    @Override
    public void reset() {
        resources.forEach(ProcessingResource::releaseTask);
    }
}