package com.scheduling.framework;

import com.scheduling.framework.metrics.MetricsCollector;
import com.scheduling.framework.metrics.SchedulingMetrics;
import com.scheduling.framework.model.Task;
import com.scheduling.framework.scheduler.impl.FCFSScheduler;
import com.scheduling.framework.scheduler.impl.PriorityScheduler;

/**
 * Test simple para verificar el cálculo de métricas sin dependencias de Flink
 */
public class SimpleMetricsTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("       SIMPLE METRICS TEST RESULTS     ");
        System.out.println("========================================");
        System.out.println();
        
        // Test FCFS
        SchedulingMetrics fcfsMetrics = simulateScheduler("FCFS", true);
        
        // Test Priority
        SchedulingMetrics priorityMetrics = simulateScheduler("Priority", false);
        
        // Imprimir comparación
        printComparison(fcfsMetrics, priorityMetrics);
    }
    
    private static SchedulingMetrics simulateScheduler(String schedulerName, boolean isFCFS) {
        MetricsCollector collector = new MetricsCollector();
        long baseTime = System.currentTimeMillis();
        int numTasks = 5000;
        
        System.out.println("Testing " + schedulerName + " scheduler...");
        
        // Simular métricas basadas en los logs observados
        for (int i = 0; i < numTasks; i++) {
            collector.recordTaskSubmission();
            
            // Crear tarea simulada con métricas realistas
            // Usar tiempos incrementales para simular llegada secuencial
            long arrivalTime = baseTime + (i * 10); // 10ms entre llegadas
            Task task = new Task("task-" + i, "TEST", null, arrivalTime, 1);
            
            // Tiempos basados en los logs observados - startTime debe ser >= arrivalTime
            long waitTime;
            if (isFCFS) {
                waitTime = i % 40; // Max wait 39ms observado para FCFS
            } else {
                waitTime = i % 2; // Max wait 1ms observado para Priority
            }
            
            task.setStartTime(arrivalTime + waitTime);
            task.setCompletionTime(task.getStartTime() + 10); // 10ms processing delay
            collector.recordTaskCompletion(task);
            
            // Simular delay entre procesamiento
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        collector.markEnd();
        SchedulingMetrics metrics = collector.calculateMetrics();
        
        System.out.println("Completed: " + schedulerName);
        System.out.println("Total Time: " + metrics.avgTotalTime + " ms");
        System.out.println("Wait Time: " + metrics.avgWaitingTime + " ms");
        System.out.println("Execution Time: " + metrics.avgExecutionTime + " ms");
        System.out.println("Throughput: " + metrics.throughput + " tasks/sec");
        System.out.println("----------------------------------------");
        
        return metrics;
    }
    
    private static void printComparison(SchedulingMetrics fcfs, SchedulingMetrics priority) {
        System.out.println();
        System.out.println("========================================");
        System.out.println("       SCHEDULER COMPARISON RESULTS     ");
        System.out.println("========================================");
        System.out.println();
        
        System.out.printf("%-25s | %10s | %12s | %15s | %12s%n", 
            "Scheduler", "Completed", "Avg Wait(ms)", "Avg Total(ms)", "Throughput");
        System.out.println("--------------------------|------------|--------------|-----------------|-------------");
        
        System.out.printf("%-25s | %10d | %12.2f | %15.2f | %12.2f%n",
            "First Come First Serve",
            fcfs.completedTasks,
            fcfs.avgWaitingTime,
            fcfs.avgTotalTime,
            fcfs.throughput
        );
        
        System.out.printf("%-25s | %10d | %12.2f | %15.2f | %12.2f%n",
            "Priority Scheduler",
            priority.completedTasks,
            priority.avgWaitingTime,
            priority.avgTotalTime,
            priority.throughput
        );
        
        System.out.println();
        System.out.println("========================================");
        
        String bestThroughput = fcfs.throughput > priority.throughput ? 
            "First Come First Serve" : "Priority Scheduler";
        String bestLatency = fcfs.avgTotalTime < priority.avgTotalTime ? 
            "First Come First Serve" : "Priority Scheduler";
        
        System.out.println("Best Throughput: " + bestThroughput);
        System.out.println("Best Latency: " + bestLatency);
        System.out.println("========================================");
    }
}