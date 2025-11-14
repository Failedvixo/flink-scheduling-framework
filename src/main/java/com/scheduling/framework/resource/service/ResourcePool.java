package com.scheduling.framework.resource.service;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pool de recursos de procesamiento
 * Gestiona un conjunto de recursos que pueden ser asignados a tareas
 */
public class ResourcePool {
    
    private final String name;
    private final BlockingQueue<ProcessingResource> availableResources;
    private final List<ProcessingResource> allResources;
    private final int capacity;
    
    public ResourcePool(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        this.availableResources = new LinkedBlockingQueue<>();
        this.allResources = new ArrayList<>();
        
        // Inicializar recursos
        for (int i = 0; i < capacity; i++) {
            ProcessingResource resource = new ProcessingResource(
                name + "-Resource-" + i, 
                1  // Capacity 1 por recurso
            );
            allResources.add(resource);
            availableResources.offer(resource);
        }
        
        System.out.println(String.format(
            "[POOL] Created '%s' with %d resources", name, capacity
        ));
    }
    
    /**
     * Adquirir un recurso del pool para una tarea
     * Retorna null si no hay recursos disponibles
     */
    public synchronized ProcessingResource acquireResource(Task task) {
        try {
            // Intentar obtener recurso (timeout 100ms para evitar bloqueo)
            ProcessingResource resource = availableResources.poll(
                100, 
                TimeUnit.MILLISECONDS
            );
            
            if (resource != null) {
                // Asignar tarea al recurso
                boolean assigned = resource.assignTask(task);
                if (!assigned) {
                    // Si no se pudo asignar, retornar al pool
                    availableResources.offer(resource);
                    return null;
                }
                
                return resource;
            }
            
            return null; // No hay recursos disponibles
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Retornar recurso al pool después de usar
     */
    public synchronized void releaseResource(ProcessingResource resource) {
        if (resource == null) return;
        
        // Liberar la tarea del recurso
        resource.releaseTask();
        
        // Retornar recurso al pool de disponibles
        if (!availableResources.contains(resource)) {
            availableResources.offer(resource);
        }
    }
    
    /**
     * Obtener estadísticas del pool
     */
    public synchronized PoolStatistics getStatistics() {
        return new PoolStatistics(
            name,
            capacity,
            availableResources.size(),
            capacity - availableResources.size()
        );
    }
    
    // Getters
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public int getAvailableCount() { return availableResources.size(); }
    public int getUsedCount() { return capacity - availableResources.size(); }
    public double getUtilization() { 
        return (double) getUsedCount() / capacity; 
    }
    
    /**
     * Clase interna para estadísticas del pool
     */
    public static class PoolStatistics {
        public final String poolName;
        public final int totalCapacity;
        public final int availableResources;
        public final int usedResources;
        public final double utilization;
        
        public PoolStatistics(String poolName, int totalCapacity, 
                            int availableResources, int usedResources) {
            this.poolName = poolName;
            this.totalCapacity = totalCapacity;
            this.availableResources = availableResources;
            this.usedResources = usedResources;
            this.utilization = (double) usedResources / totalCapacity;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Pool[%s]: %d/%d used (%.1f%% utilization)", 
                poolName, usedResources, totalCapacity, utilization * 100
            );
        }
    }
}