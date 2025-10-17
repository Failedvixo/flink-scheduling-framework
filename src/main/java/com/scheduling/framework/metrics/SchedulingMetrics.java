package com.scheduling.framework.metrics;

/**
 * Métricas de rendimiento del scheduler
 */
public class SchedulingMetrics {
    
    public final int completedTasks;
    public final int submittedTasks;
    public final double avgWaitingTime;
    public final long maxWaitingTime;
    public final long minWaitingTime;
    public final double avgExecutionTime;
    public final long maxExecutionTime;
    public final long minExecutionTime;
    public final double avgTotalTime;
    public final double throughput;
    public final long totalDuration;
    
    public SchedulingMetrics() {
        this(0, 0, 0.0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0);
    }
    
    public SchedulingMetrics(int completedTasks, int submittedTasks, 
                           double avgWaitingTime, long maxWaitingTime, long minWaitingTime,
                           double avgExecutionTime, long maxExecutionTime, long minExecutionTime,
                           double avgTotalTime, double throughput, long totalDuration) {
        this.completedTasks = completedTasks;
        this.submittedTasks = submittedTasks;
        this.avgWaitingTime = avgWaitingTime;
        this.maxWaitingTime = maxWaitingTime;
        this.minWaitingTime = minWaitingTime;
        this.avgExecutionTime = avgExecutionTime;
        this.maxExecutionTime = maxExecutionTime;
        this.minExecutionTime = minExecutionTime;
        this.avgTotalTime = avgTotalTime;
        this.throughput = throughput;
        this.totalDuration = totalDuration;
    }
    
    public double getCompletionRate() {
        return submittedTasks > 0 ? (completedTasks * 100.0) / submittedTasks : 0.0;
    }
}