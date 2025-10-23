package com.scheduling.framework.resource.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.ResourceScheduler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resource Scheduler Priority: Asigna tareas de alta prioridad a recursos con menor carga
 */
public class PriorityResourceScheduler implements ResourceScheduler {
    
    private static final long serialVersionUID = 1L;
    
    private List<ProcessingResource> resources;
    
    @Override
    public void initialize(List<ProcessingResource> resources) {
        this.resources = new ArrayList<>(resources);
    }
    
    @Override
    public synchronized ProcessingResource assignTaskToResource(Task task) {
        // Priority: Tareas de alta prioridad van a recursos con menor carga
        ProcessingResource bestResource;
        
        if (task.getPriority() > 5) {
            // Alta prioridad: buscar el recurso con menor carga
            bestResource = resources.stream()
                .filter(ProcessingResource::isAvailable)
                .min(Comparator.comparingInt(ProcessingResource::getCurrentLoad))
                .orElse(null);
        } else {
            // Baja prioridad: cualquier recurso disponible
            bestResource = resources.stream()
                .filter(ProcessingResource::isAvailable)
                .findFirst()
                .orElse(null);
        }
        
        if (bestResource != null && bestResource.assignTask(task)) {
            return bestResource;
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
        return "Priority Resource Scheduler";
    }
    
    @Override
    public void reset() {
        resources.forEach(ProcessingResource::releaseTask);
    }
}