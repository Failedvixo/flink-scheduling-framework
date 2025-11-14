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
 * TRUE Resource Scheduling Job - SOLUCIÓN FINAL
 * Inicialización estática del scheduler para compartir entre threads de Flink
 */
public class TrueResourceSchedulingJob {
    
    // STATIC reference para compartir entre threads
    private static volatile boolean schedulerInitialized = false;
    private static final Object INIT_LOCK = new Object();
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("========================================");
        System.out.println("   TRUE RESOURCE SCHEDULING JOB        ");
        System.out.println("========================================");
        System.out.println();
        
        // Configuración
        final int fcfsPoolSize = 4;
        final int priorityPoolSize = 4;
        final int numEvents = 10000;
        final int parallelism = 4;
        
        // INICIALIZAR una vez aquí
        System.out.println("Initializing Resource Scheduler...");
        initializeScheduler(fcfsPoolSize, priorityPoolSize);
        System.out.println("✅ Scheduler ready");
        System.out.println();
        
        // Pipeline de Flink
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        
        DataStream<Task> taskStream = env
            .addSource(new NexmarkEventSource(numEvents))
            .name("Event Source")
            .setParallelism(1);
        
        DataStream<Task> processedStream = taskStream
            .process(new ResourceAwareProcessFunction())
            .name("Task Processor")
            .setParallelism(parallelism);
        
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
        System.out.println("Shutting down scheduler");
        System.out.println("========================================");
        
        // Print scheduler metrics BEFORE shutdown
        try {
            AdaptiveResourceSchedulerService scheduler = AdaptiveResourceSchedulerService.getInstance();
            scheduler.printFinalSummary();
        } catch (Exception e) {
            System.err.println("Warning: Error getting scheduler metrics: " + e.getMessage());
        }
        
        try {
            AdaptiveResourceSchedulerService.getInstance().shutdown();
        } catch (Exception e) {
            System.err.println("Warning: Error shutting down scheduler: " + e.getMessage());
        }
        
        MetricsCollectorFunction.printFinalMetrics();
    }
    
    /**
     * Inicializar scheduler de forma thread-safe
     */
    private static void initializeScheduler(int fcfsSize, int prioritySize) {
        synchronized (INIT_LOCK) {
            if (!schedulerInitialized) {
                AdaptiveResourceSchedulerService.initialize(fcfsSize, prioritySize);
                AdaptiveResourceSchedulerService scheduler = AdaptiveResourceSchedulerService.getInstance();
                scheduler.setThresholds(90.0, 50.0);
                schedulerInitialized = true;
            }
        }
    }
    
    /**
     * Asegurar que scheduler esté inicializado (llamar desde operadores)
     */
    static AdaptiveResourceSchedulerService ensureScheduler() {
        if (!schedulerInitialized) {
            synchronized (INIT_LOCK) {
                if (!schedulerInitialized) {
                    // Fallback initialization con valores default
                    AdaptiveResourceSchedulerService.initialize(4, 4);
                    AdaptiveResourceSchedulerService scheduler = AdaptiveResourceSchedulerService.getInstance();
                    scheduler.setThresholds(90.0, 50.0);
                    schedulerInitialized = true;
                }
            }
        }
        return AdaptiveResourceSchedulerService.getInstance();
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
        System.out.println("[SOURCE] Opened - preparing to generate " + numEvents + " events");
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
                System.out.println("[SOURCE] Generated " + i + " events");
                Thread.sleep(1);
            }
        }
        
        System.out.println("[SOURCE] ✅ Completed generating " + numEvents + " events");
    }
    
    @Override
    public void cancel() {
        running = false;
    }
}

/**
 * Process Function que usa el scheduler externo
 */
class ResourceAwareProcessFunction extends ProcessFunction<Task, Task> {
    
    private static final long serialVersionUID = 1L;
    private transient AdaptiveResourceSchedulerService resourceScheduler;
    private transient long tasksProcessed = 0;
    private transient long tasksWaited = 0;
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        System.out.println("[OPERATOR] Opening process function...");
        
        try {
            // Asegurar que scheduler esté disponible
            this.resourceScheduler = TrueResourceSchedulingJob.ensureScheduler();
            System.out.println("[OPERATOR] ✅ Connected to scheduler");
        } catch (Exception e) {
            System.err.println("[OPERATOR] ❌ ERROR getting scheduler: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    @Override
    public void processElement(Task task, Context ctx, Collector<Task> out) throws Exception {
        
        if (resourceScheduler == null) {
            System.err.println("[OPERATOR] ❌ Scheduler not available!");
            task.setStatus(Task.TaskStatus.FAILED);
            out.collect(task);
            return;
        }
        
        // Log primera tarea
        if (tasksProcessed == 0) {
            System.out.println("[OPERATOR] Processing first task...");
        }
        
        // 1. Asignar recurso
        ResourceAssignment assignment = resourceScheduler.assignResource(task);
        task.setStartTime(System.currentTimeMillis());
        
        // Manejo de espera
        if (assignment.isWaiting()) {
            tasksWaited++;
            Thread.sleep(10);
            assignment = resourceScheduler.assignResource(task);
            
            if (assignment.isWaiting()) {
                task.setStatus(Task.TaskStatus.FAILED);
                out.collect(task);
                return;
            }
        }
        
        // 2. Procesar
        ProcessingResource resource = assignment.getResource();
        
        try {
            // Log primeras asignaciones
            if (tasksProcessed < 3) {
                System.out.println("[OPERATOR] Task " + task.getTaskId() + 
                    " → " + resource.getResourceId() + " (" + assignment.getPoolName() + ")");
            }
            
            long processingTime = getProcessingTime(task);
            Thread.sleep(processingTime);
            
            task.setCompletionTime(System.currentTimeMillis());
            task.setStatus(Task.TaskStatus.COMPLETED);
            
            tasksProcessed++;
            
            if (tasksProcessed % 2000 == 0) {
                System.out.println(String.format(
                    "[OPERATOR] Progress: %d tasks processed, %d waited",
                    tasksProcessed, tasksWaited
                ));
            }
            
        } finally {
            // 3. Liberar recurso
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
            "[OPERATOR] Closing - Processed: %d, Waited: %d",
            tasksProcessed, tasksWaited
        ));
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
                System.out.println("[METRICS] Starting metrics collection");
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
            
            if (totalTasks % 2000 == 0) {
                double avgWait = completedTasks > 0 ? (double) totalWaitTime / completedTasks : 0;
                System.out.println(String.format(
                    "[METRICS] Progress: %d tasks (Completed: %d, Failed: %d) - Avg Wait: %.2fms",
                    totalTasks, completedTasks, failedTasks, avgWait
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
    }
}