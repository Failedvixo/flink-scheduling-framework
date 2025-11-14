package com.scheduling.framework.resource.service;

import com.scheduling.framework.resource.ProcessingResource;
import java.io.Serializable;

/**
 * Representa una asignación de recurso a una tarea
 */
public class ResourceAssignment implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String taskId;
    private final ProcessingResource resource;
    private final String poolName;
    private final long assignmentTime;
    private final boolean isWaiting;
    
    // Constructor para asignación exitosa
    public ResourceAssignment(String taskId, ProcessingResource resource, 
                             String poolName, long assignmentTime) {
        this.taskId = taskId;
        this.resource = resource;
        this.poolName = poolName;
        this.assignmentTime = assignmentTime;
        this.isWaiting = false;
    }
    
    // Constructor para estado de espera
    private ResourceAssignment(String taskId) {
        this.taskId = taskId;
        this.resource = null;
        this.poolName = "WAITING";
        this.assignmentTime = System.currentTimeMillis();
        this.isWaiting = true;
    }
    
    // Factory method para crear asignación en espera
    public static ResourceAssignment waiting(com.scheduling.framework.model.Task task) {
        return new ResourceAssignment(task.getTaskId());
    }
    
    // Getters
    public String getTaskId() { return taskId; }
    public ProcessingResource getResource() { return resource; }
    public String getPoolName() { return poolName; }
    public long getAssignmentTime() { return assignmentTime; }
    public boolean isWaiting() { return isWaiting; }
    
    @Override
    public String toString() {
        if (isWaiting) {
            return String.format("Assignment{task=%s, status=WAITING}", taskId);
        }
        return String.format("Assignment{task=%s, resource=%s, pool=%s}", 
            taskId, resource.getResourceId(), poolName);
    }
}