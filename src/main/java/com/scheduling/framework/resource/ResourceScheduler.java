package com.scheduling.framework.resource;

import com.scheduling.framework.model.Task;
import java.io.Serializable;
import java.util.List;

/**
 * Interfaz para scheduling de recursos de procesamiento
 */
public interface ResourceScheduler extends Serializable {
    
    /**
     * Inicializar el scheduler con recursos disponibles
     */
    void initialize(List<ProcessingResource> resources);
    
    /**
     * Asignar una tarea a un recurso específico
     */
    ProcessingResource assignTaskToResource(Task task);
    
    /**
     * Liberar un recurso cuando termina una tarea
     */
    void releaseResource(String resourceId, Task completedTask);
    
    /**
     * Obtener recursos disponibles
     */
    List<ProcessingResource> getAvailableResources();
    
    /**
     * Obtener nombre del algoritmo de resource scheduling
     */
    String getAlgorithmName();
    
    /**
     * Resetear el estado del scheduler
     */
    void reset();
}