package com.scheduling.framework.model;

import java.io.Serializable;

/**
 * Representa una tarea de procesamiento en el stream
 */
public class Task implements Serializable {
    private final String taskId;
    private final String eventType;
    private final Object payload;
    private final long arrivalTime;
    private final int priority;
    private long startTime;
    private long completionTime;
    private TaskStatus status;
    
    public enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
    
    public Task(String taskId, String eventType, Object payload, long arrivalTime, int priority) {
        this.taskId = taskId;
        this.eventType = eventType;
        this.payload = payload;
        this.arrivalTime = arrivalTime;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
    }
    
    public Task(String taskId, String eventType, Object payload, long arrivalTime) {
        this(taskId, eventType, payload, arrivalTime, 0);
    }
    
    // Getters
    public String getTaskId() { return taskId; }
    public String getEventType() { return eventType; }
    public Object getPayload() { return payload; }
    public long getArrivalTime() { return arrivalTime; }
    public int getPriority() { return priority; }
    public long getStartTime() { return startTime; }
    public long getCompletionTime() { return completionTime; }
    public TaskStatus getStatus() { return status; }
    
    // Setters
    public void setStartTime(long startTime) { 
        this.startTime = startTime;
        this.status = TaskStatus.RUNNING;
    }
    
    public void setCompletionTime(long completionTime) { 
        this.completionTime = completionTime;
        this.status = TaskStatus.COMPLETED;
    }
    
    public void setStatus(TaskStatus status) { 
        this.status = status; 
    }
    
    // Métricas útiles
    public long getWaitingTime() {
        return startTime > 0 ? startTime - arrivalTime : System.currentTimeMillis() - arrivalTime;
    }
    
    public long getExecutionTime() {
        return completionTime > 0 && startTime > 0 ? completionTime - startTime : 0;
    }
    
    public long getTotalTime() {
        return completionTime > 0 ? completionTime - arrivalTime : 0;
    }
    
    @Override
    public String toString() {
        return String.format("Task{id='%s', type='%s', status=%s, arrival=%d, priority=%d}",
                taskId, eventType, status, arrivalTime, priority);
    }
}