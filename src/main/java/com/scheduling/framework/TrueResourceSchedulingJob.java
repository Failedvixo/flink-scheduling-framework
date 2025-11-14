package com.scheduling.framework;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.service.AdaptiveResourceSchedulerService;
import com.scheduling.framework.resource.service.ResourceAssignment;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * TRUE Resource Scheduling Job - SOLUCIÓN DEFINITIVA
 * Cada operador inicializa su propio scheduler - NO usa singleton compartido
 */
public class TrueResourceSchedulingJob {
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("========================================");
        System.out.println("   TRUE RESOURCE SCHEDULING JOB        ");
        System.out.println("   (Per-Operator Scheduler)            ");
        System.out.println("========================================");
        System.out.println();
        
        // Configuración que se pasa a los operadores
        final int fcfsPoolSize = 4;
        final int priorityPoolSize = 4;
        final int numEvents = 10000;
        final int parallelism = 4;
        
        System.out.println("Configuration:");
        System.out.println("  FCFS Pool: " + fcfsPoolSize + " resources");
        System.out.println("  Priority Pool: " + priorityPoolSize + " resources");
        System.out.println("  Events: " + numEvents);
        System.out.println("  Parallelism: " + parallelism);
        System.out.println();
        
        // Pipeline de Flink
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        
        // Source
        DataStream<Task> taskStream = env
            .addSource(new NexmarkEventSource(numEvents))
            .name("Event Source")
            .setParallelism(1);
        
        // Process - PASAMOS configuración como parámetros
        DataStream<Task> processedStream = taskStream
            .process(new ResourceAwareProcessFunction(fcfsPoolSize, priorityPoolSize))
            .name("Task Processor")
            .setParallelism(parallelism);
        
        // Sink
        processedStream
            .map(new MetricsCollectorFunction())
            .name("Metrics Collector")
            .setParallelism(1);
        
        System.out.println("Starting Flink job...");
        System.out.println("========================================");
        System.out.println();
        
        env.execute("True Resource Scheduling Job");
        
        Thread.sleep(2000);
        
        System.out.println();
        System.out.println("========================================");
        System.out.println("Job completed");
        System.out.println("========================================");
        
        MetricsCollectorFunction.printFinalMetrics();
    }
}

/**
 * Source de eventos Nexmark
 */
class NexmarkEventSource extends RichSourceFunction<Task> {
    
    private static final long serialVersionUID = 1L;
    private final int numEvents;
    private volatile boolean running = true;
    
    public NexmarkEventSource(int numEvents) {
        this.numEvents = numEvents;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        System.out.println("[SOURCE] ✅ Source opened - ready to generate " + numEvents + " events");
    }
    
    @Override
    public void run(SourceContext<Task> ctx) throws Exception {
        long baseTime = System.currentTimeMillis();
        List<String> eventTypes = Arrays.asList("PERSON", "AUCTION", "BID");
        
        System.out.println("[SOURCE] Starting event generation...");
        
        for (int i = 0; i < numEvents && running; i++) {
            String eventType = eventTypes.get(i % 3);
            int priority = eventType.equals("BID") ? 3 : 
                          eventType.equals("AUCTION") ? 2 : 1;
            
            Task task = new Task("event-" + i, eventType, null, baseTime + i, priority);
            ctx.collect(task);
            
            if (i > 0 && i % 2000 == 0) {
                System.out.println("[SOURCE] Generated " + i + " / " + numEvents + " events");
                Thread.sleep(1);
            }
        }
        
        System.out.println("[SOURCE] ✅ Completed - generated all " + numEvents + " events");
    }
    
    @Override
    public void cancel() {
        running = false;
        System.out.println("[SOURCE] Cancelled");
    }
}

/**
 * Process Function con SCHEDULER PROPIO (no compartido)
 */
class ResourceAwareProcessFunction extends ProcessFunction<Task, Task> {
    
    private static final long serialVersionUID = 1L;
    
    // Configuración serializable
    private final int fcfsPoolSize;
    private final int priorityPoolSize;
    
    // Scheduler transient - se inicializa en open()
    private transient AdaptiveResourceSchedulerService resourceScheduler;
    private transient long tasksProcessed = 0;
    private transient long tasksWaited = 0;
    private transient long tasksRejected = 0;
    
    public ResourceAwareProcessFunction(int fcfsPoolSize, int priorityPoolSize) {
        this.fcfsPoolSize = fcfsPoolSize;
        this.priorityPoolSize = priorityPoolSize;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        System.out.println("[OPERATOR] ✅ Opening process function...");
        System.out.println("[OPERATOR] Initializing scheduler with FCFS=" + fcfsPoolSize + 
                          ", Priority=" + priorityPoolSize);
        
        try {
            // Cada instancia del operador crea su PROPIO scheduler
            // Esto es thread-safe porque cada operador corre en su propio thread
            AdaptiveResourceSchedulerService.initialize(fcfsPoolSize, priorityPoolSize);
            this.resourceScheduler = AdaptiveResourceSchedulerService.getInstance();
            this.resourceScheduler.setThresholds(90.0, 50.0);
            
            System.out.println("[OPERATOR] ✅ Scheduler initialized successfully");
            
        } catch (Exception e) {
            System.err.println("[OPERATOR] ❌ ERROR initializing scheduler: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public void processElement(Task task, Context ctx, Collector<Task> out) throws Exception {
        
        if (resourceScheduler == null) {
            System.err.println("[OPERATOR] ❌ Scheduler is null!");
            task.setStatus(Task.TaskStatus.FAILED);
            out.collect(task);
            return;
        }
        
        // Log primera tarea
        if (tasksProcessed == 0) {
            System.out.println("[OPERATOR] ✅ Processing first task: " + task.getTaskId());
        }
        
        // 1. Solicitar recurso
        ResourceAssignment assignment = resourceScheduler.assignResource(task);
        task.setStartTime(System.currentTimeMillis());
        
        // Manejo de espera
        if (assignment.isWaiting()) {
            tasksWaited++;
            
            // Retry simple
            Thread.sleep(10);
            assignment = resourceScheduler.assignResource(task);
            
            if (assignment.isWaiting()) {
                // Aún sin recursos - rechazar
                tasksRejected++;
                
                if (tasksRejected <= 5) {
                    System.out.println("[OPERATOR] Task " + task.getTaskId() + " REJECTED - no resources available");
                }
                
                task.setStatus(Task.TaskStatus.FAILED);
                out.collect(task);
                return;
            }
        }
        
        // 2. Procesar tarea
        ProcessingResource resource = assignment.getResource();
        
        try {
            // Log primeras asignaciones
            if (tasksProcessed < 5) {
                System.out.println("[OPERATOR] Task " + task.getTaskId() + 
                    " → Resource: " + resource.getResourceId() + 
                    " (Pool: " + assignment.getPoolName() + ")");
            }
            
            // Simular procesamiento
            long processingTime = getProcessingTime(task);
            Thread.sleep(processingTime);
            
            // Marcar como completada
            task.setCompletionTime(System.currentTimeMillis());
            task.setStatus(Task.TaskStatus.COMPLETED);
            
            tasksProcessed++;
            
            // Log progreso
            if (tasksProcessed % 2000 == 0) {
                System.out.println(String.format(
                    "[OPERATOR] Progress: %d processed, %d waited, %d rejected",
                    tasksProcessed, tasksWaited, tasksRejected
                ));
            }
            
        } finally {
            // 3. SIEMPRE liberar recurso
            resourceScheduler.releaseResource(task.getTaskId());
        }
        
        out.collect(task);
    }
    
    private long getProcessingTime(Task task) {
        switch (task.getEventType()) {
            case "BID": return 5;
            case "AUCTION": return 15;
            case "PERSON": return 10;
            default: return 10;
        }
    }
    
    @Override
    public void close() throws Exception {
        super.close();
        
        System.out.println(String.format(
            "[OPERATOR] ✅ Closing - Stats: Processed=%d, Waited=%d, Rejected=%d",
            tasksProcessed, tasksWaited, tasksRejected
        ));
        
        // Shutdown del scheduler de esta instancia
        if (resourceScheduler != null) {
            try {
                resourceScheduler.shutdown();
            } catch (Exception e) {
                System.err.println("[OPERATOR] Warning: Error shutting down scheduler: " + e.getMessage());
            }
        }
    }
}

/**
 * Collector de métricas
 */
class MetricsCollectorFunction implements MapFunction<Task, String>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private static volatile long totalTasks = 0;
    private static volatile long completedTasks = 0;
    private static volatile long failedTasks = 0;
    private static volatile long totalWaitTime = 0;
    private static volatile long totalExecutionTime = 0;
    private static volatile long startTime = 0;
    private static volatile long endTime = 0;
    
    @Override
    public String map(Task task) {
        synchronized (MetricsCollectorFunction.class) {
            if (totalTasks == 0) {
                startTime = System.currentTimeMillis();
                System.out.println("[METRICS] ✅ Starting metrics collection");
            }
            
            totalTasks++;
            
            if (task.getStatus() == Task.TaskStatus.COMPLETED) {
                completedTasks++;
                totalWaitTime += task.getWaitingTime();
                totalExecutionTime += task.getExecutionTime();
            } else if (task.getStatus() == Task.TaskStatus.FAILED) {
                failedTasks++;
            }
            
            endTime = System.currentTimeMillis();
            
            // Log progreso cada 2000 tareas
            if (totalTasks % 2000 == 0) {
                double avgWait = completedTasks > 0 ? (double) totalWaitTime / completedTasks : 0;
                double successRate = totalTasks > 0 ? (completedTasks * 100.0) / totalTasks : 0;
                
                System.out.println(String.format(
                    "[METRICS] Progress: %d tasks | Completed: %d (%.1f%%) | Failed: %d | Avg Wait: %.2fms",
                    totalTasks, completedTasks, successRate, failedTasks, avgWait
                ));
            }
        }
        
        return task.getTaskId();
    }
    
    public static void printFinalMetrics() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("       FINAL TASK METRICS              ");
        System.out.println("========================================");
        
        double avgWait = completedTasks > 0 ? (double) totalWaitTime / completedTasks : 0;
        double avgExecution = completedTasks > 0 ? (double) totalExecutionTime / completedTasks : 0;
        double avgTotal = avgWait + avgExecution;
        long duration = endTime - startTime;
        double throughput = duration > 0 ? (completedTasks * 1000.0) / duration : 0;
        double successRate = totalTasks > 0 ? (completedTasks * 100.0) / totalTasks : 0;
        
        System.out.println("Total Tasks: " + totalTasks);
        System.out.println("Completed: " + completedTasks);
        System.out.println("Failed: " + failedTasks);
        System.out.println("Success Rate: " + String.format("%.2f%%", successRate));
        System.out.println();
        System.out.println("Performance:");
        System.out.println("  Avg Wait Time: " + String.format("%.2f ms", avgWait));
        System.out.println("  Avg Execution Time: " + String.format("%.2f ms", avgExecution));
        System.out.println("  Avg Total Time: " + String.format("%.2f ms", avgTotal));
        System.out.println("  Throughput: " + String.format("%.2f tasks/sec", throughput));
        System.out.println();
        System.out.println("Duration: " + duration + " ms");
        System.out.println("========================================");
        
        // Reset para próxima ejecución
        reset();
    }
    
    public static void reset() {
        totalTasks = 0;
        completedTasks = 0;
        failedTasks = 0;
        totalWaitTime = 0;
        totalExecutionTime = 0;
        startTime = 0;
        endTime = 0;
    }
}