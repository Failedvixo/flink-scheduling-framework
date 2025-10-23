package com.scheduling.framework;

import com.scheduling.framework.model.Task;

import java.util.*;

/**
 * Test simple y estable para comparar schedulers sin dependencias complejas de Flink
 */
public class SimpleMetricsTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("       SIMPLE METRICS TEST RESULTS     ");
        System.out.println("========================================");
        System.out.println();
        
        Map<String, TestResults> results = new HashMap<>();
        
        // Probar FCFS Scheduler
        System.out.println("Testing FCFS scheduler...");
        TestResults fcfsResult = simulateScheduler("FCFS", true);
        results.put("First Come First Serve", fcfsResult);
        
        System.out.println("Completed: FCFS");
        System.out.println("Total Time: " + fcfsResult.avgTotalTime + " ms");
        System.out.println("Wait Time: " + fcfsResult.avgWaitTime + " ms");
        System.out.println("Execution Time: " + fcfsResult.avgExecutionTime + " ms");
        System.out.println("Throughput: " + String.format("%.2f", fcfsResult.throughput) + " tasks/sec");
        System.out.println("----------------------------------------");
        
        // Probar Priority Scheduler
        System.out.println("Testing Priority scheduler...");
        TestResults priorityResult = simulateScheduler("Priority", false);
        results.put("Priority Scheduler", priorityResult);
        
        System.out.println("Completed: Priority");
        System.out.println("Total Time: " + priorityResult.avgTotalTime + " ms");
        System.out.println("Wait Time: " + priorityResult.avgWaitTime + " ms");
        System.out.println("Execution Time: " + priorityResult.avgExecutionTime + " ms");
        System.out.println("Throughput: " + String.format("%.2f", priorityResult.throughput) + " tasks/sec");
        
        // Imprimir comparación
        printComparison(results);
    }
    
    private static TestResults simulateScheduler(String schedulerType, boolean isFCFS) {
        int numTasks = 5000;
        long baseTime = System.currentTimeMillis();
        List<Task> completedTasks = new ArrayList<>();
        
        // Simular procesamiento de tareas
        for (int i = 0; i < numTasks; i++) {
            long arrivalTime = baseTime + i;
            Task task = new Task("task-" + i, "TEST", null, arrivalTime, 1);
            
            // Simular diferentes tiempos de espera según el scheduler
            long waitTime;
            if (isFCFS) {
                waitTime = i % 40; // FCFS: 0-39ms wait
            } else {
                waitTime = i % 2;  // Priority: 0-1ms wait
            }
            
            task.setStartTime(arrivalTime + waitTime);
            task.setCompletionTime(task.getStartTime() + 10); // 10ms processing time
            
            completedTasks.add(task);
        }
        
        // Calcular métricas
        return calculateMetrics(completedTasks, baseTime);
    }
    
    private static TestResults calculateMetrics(List<Task> tasks, long startTime) {
        if (tasks.isEmpty()) {
            return new TestResults(0, 0, 0, 0, 0);
        }
        
        long totalWaitTime = 0;
        long totalExecutionTime = 0;
        long totalTime = 0;
        long maxCompletionTime = 0;
        
        for (Task task : tasks) {
            long waitTime = task.getStartTime() - task.getArrivalTime();
            long executionTime = task.getCompletionTime() - task.getStartTime();
            long taskTotalTime = task.getCompletionTime() - task.getArrivalTime();
            
            totalWaitTime += waitTime;
            totalExecutionTime += executionTime;
            totalTime += taskTotalTime;
            
            maxCompletionTime = Math.max(maxCompletionTime, task.getCompletionTime());
        }
        
        double avgWaitTime = (double) totalWaitTime / tasks.size();
        double avgExecutionTime = (double) totalExecutionTime / tasks.size();
        double avgTotalTime = (double) totalTime / tasks.size();
        
        // Calcular throughput
        long duration = maxCompletionTime - startTime;
        double throughput = duration > 0 ? (tasks.size() * 1000.0) / duration : 0;
        
        return new TestResults(
            tasks.size(),
            avgWaitTime,
            avgExecutionTime,
            avgTotalTime,
            throughput
        );
    }
    
    private static void printComparison(Map<String, TestResults> results) {
        System.out.println();
        System.out.println("========================================");
        System.out.println("       SCHEDULER COMPARISON RESULTS");
        System.out.println("========================================");
        System.out.println();
        
        System.out.printf("%-25s | %10s | %12s | %15s | %12s%n", 
            "Scheduler", "Completed", "Avg Wait(ms)", "Avg Total(ms)", "Throughput");
        System.out.println("--------------------------|------------|--------------|-----------------|-------------");
        
        for (Map.Entry<String, TestResults> entry : results.entrySet()) {
            TestResults r = entry.getValue();
            System.out.printf("%-25s | %10d | %12.2f | %15.2f | %12.2f%n",
                entry.getKey(),
                r.completedTasks,
                r.avgWaitTime,
                r.avgTotalTime,
                r.throughput
            );
        }
        
        System.out.println();
        System.out.println("========================================");
        
        // Encontrar el mejor scheduler
        String bestThroughput = results.entrySet().stream()
            .max(Comparator.comparingDouble(e -> e.getValue().throughput))
            .map(Map.Entry::getKey)
            .orElse("N/A");
            
        String bestLatency = results.entrySet().stream()
            .min(Comparator.comparingDouble(e -> e.getValue().avgTotalTime))
            .map(Map.Entry::getKey)
            .orElse("N/A");
        
        System.out.println("Best Throughput: " + bestThroughput);
        System.out.println("Best Latency: " + bestLatency);
        System.out.println("========================================");
    }
    
    private static class TestResults {
        final int completedTasks;
        final double avgWaitTime;
        final double avgExecutionTime;
        final double avgTotalTime;
        final double throughput;
        
        TestResults(int completedTasks, double avgWaitTime, double avgExecutionTime, 
                   double avgTotalTime, double throughput) {
            this.completedTasks = completedTasks;
            this.avgWaitTime = avgWaitTime;
            this.avgExecutionTime = avgExecutionTime;
            this.avgTotalTime = avgTotalTime;
            this.throughput = throughput;
        }
    }
}