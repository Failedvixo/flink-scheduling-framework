package com.scheduling.framework.scheduler.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.scheduler.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Implementación de un scheduler basado en prioridades
 * Las tareas con mayor prioridad se procesan primero
 */
public class PriorityScheduler implements TaskScheduler {
    
    private static final Logger LOG = LoggerFactory.getLogger(PriorityScheduler.class);
    
    // Cola de prioridad: mayor prioridad primero, luego FCFS para empates
    private final PriorityQueue<Task> taskQueue;
    private int capacity;
    private int runningTasks;
    
    public PriorityScheduler() {
        this.taskQueue = new PriorityQueue<>(
            Comparator.comparingInt(Task::getPriority).reversed()
                .thenComparingLong(Task::getArrivalTime)
        );
        this.runningTasks = 0;
    }
    
    @Override
    public void initialize(int capacity) {
        this.capacity = capacity;
        this.runningTasks = 0;
        LOG.info("Priority Scheduler initialized with capacity: {}", capacity);
    }
    
    @Override
    public synchronized void submitTask(Task task) {
        taskQueue.offer(task);
        LOG.debug("Task submitted with priority {}: {} - Queue size: {}", 
                 task.getPriority(), task.getTaskId(), taskQueue.size());
    }
    
    @Override
    public synchronized Task getNextTask() {
        if (runningTasks < capacity && !taskQueue.isEmpty()) {
            Task task = taskQueue.poll();
            if (task != null) {
                runningTasks++;
                task.setStartTime(System.currentTimeMillis());
                LOG.debug("Task scheduled (priority {}): {} - Running: {}/{}", 
                         task.getPriority(), task.getTaskId(), runningTasks, capacity);
                return task;
            }
        }
        return null;
    }
    
    @Override
    public synchronized void completeTask(String taskId) {
        runningTasks--;
        LOG.debug("Task completed: {} - Running: {}/{}", 
                 taskId, runningTasks, capacity);
    }
    
    @Override
    public synchronized int getPendingTasksCount() {
        return taskQueue.size();
    }
    
    @Override
    public synchronized List<Task> getPendingTasks() {
        return new ArrayList<>(taskQueue);
    }
    
    @Override
    public String getAlgorithmName() {
        return "Priority Scheduler";
    }
    
    @Override
    public synchronized void reset() {
        taskQueue.clear();
        runningTasks = 0;
        LOG.info("Priority Scheduler reset");
    }
    
    public int getRunningTasksCount() {
        return runningTasks;
    }
    
    /**
     * Obtiene estadísticas de la distribución de prioridades en la cola
     */
    public synchronized String getPriorityDistribution() {
        int[] distribution = new int[4]; // Asumiendo prioridades 0-3
        
        for (Task task : taskQueue) {
            int priority = Math.min(task.getPriority(), 3);
            distribution[priority]++;
        }
        
        return String.format("Priority distribution [P0:%d, P1:%d, P2:%d, P3:%d]",
                           distribution[0], distribution[1], distribution[2], distribution[3]);
    }
}