package com.scheduling.framework.metrics;

import com.scheduling.framework.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Recolector de métricas para evaluar el rendimiento del scheduler
 */
public class MetricsCollector {
    
    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);
    
    private final ConcurrentLinkedQueue<Task> completedTasks;
    private long startTime;
    private long endTime;
    private int totalTasksSubmitted;
    
    public MetricsCollector() {
        this.completedTasks = new ConcurrentLinkedQueue<>();
        this.startTime = System.currentTimeMillis();
        this.totalTasksSubmitted = 0;
    }
    
    public void recordTaskSubmission() {
        totalTasksSubmitted++;
    }
    
    public void recordTaskCompletion(Task task) {
        task.setCompletionTime(System.currentTimeMillis());
        completedTasks.offer(task);
    }
    
    public void markEnd() {
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * Calcula y retorna las métricas de rendimiento
     */
    public SchedulingMetrics calculateMetrics() {
        List<Task> tasks = new ArrayList<>(completedTasks);
        
        if (tasks.isEmpty()) {
            return new SchedulingMetrics();
        }
        
        LongSummaryStatistics waitingTimeStats = tasks.stream()
            .mapToLong(Task::getWaitingTime)
            .summaryStatistics();
            
        LongSummaryStatistics executionTimeStats = tasks.stream()
            .mapToLong(Task::getExecutionTime)
            .summaryStatistics();
            
        LongSummaryStatistics totalTimeStats = tasks.stream()
            .mapToLong(Task::getTotalTime)
            .summaryStatistics();
        
        long totalDuration = endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
        double throughput = (double) tasks.size() / (totalDuration / 1000.0);
        
        return new SchedulingMetrics(
            tasks.size(),
            totalTasksSubmitted,
            waitingTimeStats.getAverage(),
            waitingTimeStats.getMax(),
            waitingTimeStats.getMin(),
            executionTimeStats.getAverage(),
            executionTimeStats.getMax(),
            executionTimeStats.getMin(),
            totalTimeStats.getAverage(),
            throughput,
            totalDuration
        );
    }
    
    public void printMetrics(String schedulerName) {
        SchedulingMetrics metrics = calculateMetrics();
        
        LOG.info("=== Metrics for {} ===", schedulerName);
        LOG.info("Total Tasks Completed: {}", metrics.completedTasks);
        LOG.info("Total Tasks Submitted: {}", metrics.submittedTasks);
        LOG.info("Completion Rate: {:.2f}%", metrics.getCompletionRate());
        LOG.info("Average Waiting Time: {:.2f} ms", metrics.avgWaitingTime);
        LOG.info("Max Waiting Time: {} ms", metrics.maxWaitingTime);
        LOG.info("Min Waiting Time: {} ms", metrics.minWaitingTime);
        LOG.info("Average Execution Time: {:.2f} ms", metrics.avgExecutionTime);
        LOG.info("Average Total Time: {:.2f} ms", metrics.avgTotalTime);
        LOG.info("Throughput: {:.2f} tasks/sec", metrics.throughput);
        LOG.info("Total Duration: {} ms", metrics.totalDuration);
        LOG.info("================================");
    }
    
    public void reset() {
        completedTasks.clear();
        startTime = System.currentTimeMillis();
        endTime = 0;
        totalTasksSubmitted = 0;
    }
}