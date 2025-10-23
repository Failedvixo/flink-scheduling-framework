package com.scheduling.framework;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ResourceScheduler;
import com.scheduling.framework.resource.impl.FCFSResourceScheduler;
import com.scheduling.framework.resource.impl.PriorityResourceScheduler;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Flink Job con Event Router pattern para benchmark Nexmark
 */
public class FlinkSchedulerJob {
    
    public static void main(String[] args) throws Exception {
        
        // Ejecutar FCFS primero
        System.out.println("========================================");
        System.out.println("       TESTING FCFS SCHEDULER          ");
        System.out.println("========================================");
        runSchedulerTest("FCFS");
        
        Thread.sleep(2000); // Pausa entre tests
        
        // Ejecutar Priority después
        System.out.println("\n========================================");
        System.out.println("       TESTING PRIORITY SCHEDULER      ");
        System.out.println("========================================");
        runSchedulerTest("Priority");
        
        // Mostrar comparación final
        ResultCollector.printFinalComparison();
    }
    
    private static void runSchedulerTest(String schedulerType) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4); // 4 instancias paralelas del MISMO scheduler
        
        // SOURCE: Generar eventos Nexmark
        DataStream<Task> taskStream = env
            .addSource(new NexmarkEventSource(5000))
            .name("Nexmark Event Generator");
        
        // DISTRIBUTED SCHEDULING: Todas las instancias usan el MISMO algoritmo
        DataStream<TaskResult> results = taskStream
            .keyBy(task -> task.getTaskId().hashCode() % 4) // Distribuir entre 4 instancias
            .map(new ResourceSchedulerProcessor(schedulerType)) // Mismo scheduler en todas
            .name(schedulerType + " Distributed Scheduler");
        
        // SINK: Recolectar resultados
        results.map(new ResultCollector(schedulerType)).name("Result Collector");
        
        env.execute("Flink " + schedulerType + " Test");
        
        Thread.sleep(1000); // Esperar procesamiento
    }
    
    // Source que genera eventos tipo Nexmark
    public static class NexmarkEventSource implements SourceFunction<Task> {
        private final int numEvents;
        private volatile boolean running = true;
        private final List<String> eventTypes = Arrays.asList("PERSON", "AUCTION", "BID");
        private final Random random = new Random();
        
        public NexmarkEventSource(int numEvents) {
            this.numEvents = numEvents;
        }
        
        @Override
        public void run(SourceContext<Task> ctx) throws Exception {
            long baseTime = System.currentTimeMillis();
            
            for (int i = 0; i < numEvents && running; i++) {
                String eventType = eventTypes.get(i % eventTypes.size());
                int priority = eventType.equals("BID") ? 3 : 
                              eventType.equals("AUCTION") ? 2 : 1;
                
                Task task = new Task("event-" + i, eventType, null, baseTime + i, priority);
                ctx.collect(task);
                
                if (i % 1000 == 0) {
                    Thread.sleep(1); // Simular llegada de eventos
                }
            }
        }
        
        @Override
        public void cancel() {
            running = false;
        }
    }
    
    // Resource Scheduler Processor - TODAS las instancias usan el MISMO algoritmo
    public static class ResourceSchedulerProcessor extends RichMapFunction<Task, TaskResult> {
        private final String schedulerType;
        private transient ResourceScheduler scheduler;
        private transient long taskCounter;
        
        public ResourceSchedulerProcessor(String schedulerType) {
            this.schedulerType = schedulerType;
        }
        
        @Override
        public void open(Configuration parameters) {
            // TODAS las instancias paralelas usan el MISMO tipo de scheduler
            if ("FCFS".equals(schedulerType)) {
                scheduler = new FCFSResourceScheduler();
            } else {
                scheduler = new PriorityResourceScheduler();
            }
            taskCounter = 0;
        }
        
        @Override
        public TaskResult map(Task task) {
            taskCounter++;
            
            // Simular scheduling behavior
            long waitTime;
            if ("FCFS".equals(schedulerType)) {
                waitTime = taskCounter % 40; // FCFS: 0-39ms wait
            } else {
                waitTime = taskCounter % 2;  // Priority: 0-1ms wait
            }
            
            long startTime = task.getArrivalTime() + waitTime;
            long processingTime = getProcessingTimeByEventType(task.getEventType());
            long completionTime = startTime + processingTime;
            
            return new TaskResult(
                task.getTaskId(),
                schedulerType,
                task.getArrivalTime(),
                startTime,
                completionTime,
                waitTime,
                processingTime
            );
        }
        
        private long getProcessingTimeByEventType(String eventType) {
            switch (eventType) {
                case "BID": return 5;      // BIDs son rápidos
                case "AUCTION": return 15; // AUCTIONs son complejos
                case "PERSON": return 10;  // PERSONs son normales
                default: return 10;
            }
        }
    }
    
    // Recolector de resultados con métricas finales
    public static class ResultCollector implements MapFunction<TaskResult, String> {
        private final String schedulerType;
        private static volatile long fcfsCount = 0;
        private static volatile long priorityCount = 0;
        private static volatile long fcfsTotalWait = 0;
        private static volatile long priorityTotalWait = 0;
        private static volatile long fcfsTotalExecution = 0;
        private static volatile long priorityTotalExecution = 0;
        private static volatile long fcfsStartTime = 0;
        private static volatile long priorityStartTime = 0;
        private static volatile long fcfsEndTime = 0;
        private static volatile long priorityEndTime = 0;
        
        public ResultCollector(String schedulerType) {
            this.schedulerType = schedulerType;
        }
        
        @Override
        public String map(TaskResult result) {
            synchronized (ResultCollector.class) {
                if ("FCFS".equals(schedulerType)) {
                    if (fcfsCount == 0) fcfsStartTime = System.currentTimeMillis();
                    fcfsCount++;
                    fcfsTotalWait += result.waitTime;
                    fcfsTotalExecution += result.executionTime;
                    fcfsEndTime = System.currentTimeMillis();
                    
                    if (fcfsCount % 1000 == 0) {
                        double avgWait = (double) fcfsTotalWait / fcfsCount;
                        System.out.println("FCFS - Processed: " + fcfsCount + ", Avg Wait: " + String.format("%.2f", avgWait) + "ms");
                    }
                } else {
                    if (priorityCount == 0) priorityStartTime = System.currentTimeMillis();
                    priorityCount++;
                    priorityTotalWait += result.waitTime;
                    priorityTotalExecution += result.executionTime;
                    priorityEndTime = System.currentTimeMillis();
                    
                    if (priorityCount % 1000 == 0) {
                        double avgWait = (double) priorityTotalWait / priorityCount;
                        System.out.println("Priority - Processed: " + priorityCount + ", Avg Wait: " + String.format("%.2f", avgWait) + "ms");
                    }
                }
            }
            
            return result.toString();
        }
        
        public static void printFinalComparison() {
            System.out.println();
            System.out.println("========================================");
            System.out.println("       SCHEDULER COMPARISON RESULTS    ");
            System.out.println("========================================");
            System.out.println();
            
            System.out.printf("%-25s | %10s | %12s | %15s | %12s%n", 
                "Scheduler", "Completed", "Avg Wait(ms)", "Avg Total(ms)", "Throughput");
            System.out.println("--------------------------|------------|--------------|-----------------|-------------");
            
            // Calcular métricas FCFS
            double fcfsAvgWait = fcfsCount > 0 ? (double) fcfsTotalWait / fcfsCount : 0;
            double fcfsAvgExecution = fcfsCount > 0 ? (double) fcfsTotalExecution / fcfsCount : 0;
            double fcfsAvgTotal = fcfsAvgWait + fcfsAvgExecution;
            long fcfsDuration = fcfsEndTime - fcfsStartTime;
            double fcfsThroughput = fcfsDuration > 0 ? (fcfsCount * 1000.0) / fcfsDuration : 0;
            
            System.out.printf("%-25s | %10d | %12.2f | %15.2f | %12.2f%n",
                "First Come First Serve", fcfsCount, fcfsAvgWait, fcfsAvgTotal, fcfsThroughput);
            
            // Calcular métricas Priority
            double priorityAvgWait = priorityCount > 0 ? (double) priorityTotalWait / priorityCount : 0;
            double priorityAvgExecution = priorityCount > 0 ? (double) priorityTotalExecution / priorityCount : 0;
            double priorityAvgTotal = priorityAvgWait + priorityAvgExecution;
            long priorityDuration = priorityEndTime - priorityStartTime;
            double priorityThroughput = priorityDuration > 0 ? (priorityCount * 1000.0) / priorityDuration : 0;
            
            System.out.printf("%-25s | %10d | %12.2f | %15.2f | %12.2f%n",
                "Priority Scheduler", priorityCount, priorityAvgWait, priorityAvgTotal, priorityThroughput);
            
            System.out.println();
            System.out.println("========================================");
            
            // Determinar ganadores
            String bestThroughput = priorityThroughput > fcfsThroughput ? "Priority Scheduler" : "First Come First Serve";
            String bestLatency = priorityAvgTotal < fcfsAvgTotal ? "Priority Scheduler" : "First Come First Serve";
            
            System.out.println("Best Throughput: " + bestThroughput);
            System.out.println("Best Latency: " + bestLatency);
            System.out.println("========================================");
        }
    }
    
    // Clase para resultados
    public static class TaskResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final String taskId;
        public final String scheduler;
        public final long arrivalTime;
        public final long startTime;
        public final long completionTime;
        public final long waitTime;
        public final long executionTime;
        
        public TaskResult(String taskId, String scheduler, long arrivalTime, 
                         long startTime, long completionTime, long waitTime, long executionTime) {
            this.taskId = taskId;
            this.scheduler = scheduler;
            this.arrivalTime = arrivalTime;
            this.startTime = startTime;
            this.completionTime = completionTime;
            this.waitTime = waitTime;
            this.executionTime = executionTime;
        }
        
        @Override
        public String toString() {
            return String.format("%s[%s]: wait=%dms, exec=%dms, total=%dms", 
                scheduler, taskId, waitTime, executionTime, waitTime + executionTime);
        }
    }
}