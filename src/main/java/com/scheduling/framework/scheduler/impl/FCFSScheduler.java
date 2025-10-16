package com.scheduling.framework.scheduler.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.scheduler.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Implementación del algoritmo First Come First Serve (FCFS)
 * Las tareas se procesan en el orden en que llegan
 */
public class FCFSScheduler implements TaskScheduler {
    
    private static final Logger LOG = LoggerFactory.getLogger(FCFSScheduler.class);
    
    private final Queue<Task> taskQueue;
    private int capacity;
    private int runningTasks;
    
    public FCFSScheduler() {
        this.taskQueue = new LinkedList<>();
        this.runningTasks = 0;
    }
    
    @Override
    public void initialize(int capacity) {
        this.capacity = capacity;
        this.runningTasks = 0;
        LOG.info("FCFS Scheduler initialized with capacity: {}", capacity);
    }
    
    @Override
    public synchronized void submitTask(Task task) {
        taskQueue.offer(task);
        LOG.debug("Task submitted: {} - Queue size: {}", task.getTaskId(), taskQueue.size());
    }
    
    @Override
    public synchronized Task getNextTask() {
        // Si hay capacidad disponible y tareas en cola
        if (runningTasks < capacity && !taskQueue.isEmpty()) {
            Task task = taskQueue.poll();
            if (task != null) {
                runningTasks++;
                task.setStartTime(System.currentTimeMillis());
                LOG.debug("Task scheduled: {} - Running tasks: {}/{}", 
                         task.getTaskId(), runningTasks, capacity);
                return task;
            }
        }
        return null;
    }
    
    @Override
    public synchronized void completeTask(String taskId) {
        runningTasks--;
        LOG.debug("Task completed: {} - Running tasks: {}/{}", 
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
        return "First Come First Serve (FCFS)";
    }
    
    @Override
    public synchronized void reset() {
        taskQueue.clear();
        runningTasks = 0;
        LOG.info("FCFS Scheduler reset");
    }
    
    public int getRunningTasksCount() {
        return runningTasks;
    }
}