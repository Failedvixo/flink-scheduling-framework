package com.scheduling.framework;

import com.scheduling.framework.config.BenchmarkConfig;
import com.scheduling.framework.config.GraphConfigurations;
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
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Flink Job con Event Router pattern para benchmark Nexmark
 */
public class FlinkSchedulerJob {
    
    public static void main(String[] args) throws Exception {
        
        // Ejecutar Adaptive Scheduler
        System.out.println("========================================");
        System.out.println("       TESTING ADAPTIVE SCHEDULER      ");
        System.out.println("========================================");
        
        // Opción 1: Usar configuración predefinida (descomenta para usar)
        runWithPredefinedConfig();
        
        // Opción 2: Usar configuración hardcodeada (actual)
        // runAdaptiveSchedulerTest();
        
        // Mostrar resumen final
        AdaptiveResultCollector.printFinalSummary();
    }
    
    private static void runWithPredefinedConfig() throws Exception {
        // Cambiar entre: simpleGraph(), scaledGraph(), highLoadGraph()
        BenchmarkConfig config = GraphConfigurations.scaledGraph();
        
        System.out.println("========================================");
        System.out.println("       PREDEFINED GRAPH CONFIG         ");
        System.out.println("========================================");
        System.out.println("Graph Type: Scaled Graph");
        System.out.println("Events: " + config.getNumEvents());
        System.out.println("Source Parallelism: " + config.getSourceParallelism());
        System.out.println("Scheduler Parallelism: " + config.getSchedulerParallelism());
        System.out.println("Filter Parallelism: " + config.getFilterParallelism());
        System.out.println("Sink Parallelism: " + config.getSinkParallelism());
        System.out.println("========================================");
        
        runAdaptiveSchedulerWithConfig(config);
    }
    
    private static void runAdaptiveSchedulerWithConfig(BenchmarkConfig config) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(config.getSchedulerParallelism());
        
        DataStream<Task> taskStream = env
            .addSource(new NexmarkEventSource(config.getNumEvents()))
            .name("Nexmark Event Generator");
        
        DataStream<TaskResult> results = taskStream
            .keyBy(task -> task.getTaskId().hashCode() % config.getSchedulerParallelism())
            .map(new AdaptiveSchedulerProcessor())
            .name("Adaptive Distributed Scheduler");
        
        results.map(new AdaptiveResultCollector()).name("Adaptive Result Collector");
        
        env.execute("Flink Adaptive Scheduler Test - Predefined Config");
        Thread.sleep(2000);
    }
    
    private static void runAdaptiveSchedulerTest() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Configuración del grafo actual
        int parallelism = 4;
        int numEvents = 10000;
        
        System.out.println("========================================");
        System.out.println("       CURRENT GRAPH CONFIGURATION     ");
        System.out.println("========================================");
        System.out.println("Graph Type: Custom Hardcoded (Current)");
        System.out.println("Events: " + numEvents);
        System.out.println("Parallelism: " + parallelism);
        System.out.println("Source Instances: 1");
        System.out.println("Scheduler Instances: " + parallelism);
        System.out.println("Sink Instances: " + parallelism);
        System.out.println("========================================");
        
        env.setParallelism(parallelism); // 4 instancias paralelas adaptativas
        
        // SOURCE: Generar eventos Nexmark con carga variable
        DataStream<Task> taskStream = env
            .addSource(new NexmarkEventSource(numEvents)) // Más eventos para ver adaptación
            .name("Nexmark Event Generator");
        
        // ADAPTIVE SCHEDULING: Cambia scheduler basado en CPU
        DataStream<TaskResult> results = taskStream
            .keyBy(task -> task.getTaskId().hashCode() % 4)
            .map(new AdaptiveSchedulerProcessor())
            .name("Adaptive Distributed Scheduler");
        
        // SINK: Recolectar resultados
        results.map(new AdaptiveResultCollector()).name("Adaptive Result Collector");
        
        env.execute("Flink Adaptive Scheduler Test");
        
        Thread.sleep(2000); // Esperar procesamiento
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
    
    // Adaptive Scheduler Processor - Cambia scheduler basado en carga de CPU
    public static class AdaptiveSchedulerProcessor extends RichMapFunction<Task, TaskResult> {
        private transient ResourceScheduler fcfsScheduler;
        private transient ResourceScheduler priorityScheduler;
        private transient long taskCounter;
        private transient OperatingSystemMXBean osBean;
        private transient String currentSchedulerType;
        private transient long lastCpuCheckTime;
        private transient int schedulerSwitches;
        private static volatile List<SchedulerSwitch> switchHistory = new ArrayList<>();
        
        @Override
        public void open(Configuration parameters) {
            fcfsScheduler = new FCFSResourceScheduler();
            priorityScheduler = new PriorityResourceScheduler();
            osBean = ManagementFactory.getOperatingSystemMXBean();
            currentSchedulerType = "FCFS"; // Empezar con FCFS
            taskCounter = 0;
            lastCpuCheckTime = System.currentTimeMillis();
            schedulerSwitches = 0;
        }
        
        @Override
        public TaskResult map(Task task) {
            taskCounter++;
            
            // Verificar CPU cada 100 tareas o cada 3 segundos
            long currentTime = System.currentTimeMillis();
            if (taskCounter % 100 == 0 || (currentTime - lastCpuCheckTime) > 3000) {
                checkAndSwitchScheduler();
                lastCpuCheckTime = currentTime;
            }
            
            // Calcular wait time basado en scheduler actual y carga
            long waitTime = calculateAdaptiveWaitTime();
            
            long startTime = task.getArrivalTime() + waitTime;
            long processingTime = getProcessingTimeByEventType(task.getEventType());
            long completionTime = startTime + processingTime;
            
            return new TaskResult(
                task.getTaskId(),
                currentSchedulerType + "(Adaptive)",
                task.getArrivalTime(),
                startTime,
                completionTime,
                waitTime,
                processingTime
            );
        }
        
        private void checkAndSwitchScheduler() {
            double cpuUsage = getSimulatedCpuUsage();
            String previousScheduler = currentSchedulerType;
            
            // Cambiar scheduler basado en carga de CPU
            if (cpuUsage > 90.0 && "FCFS".equals(currentSchedulerType)) {
                currentSchedulerType = "Priority";
                schedulerSwitches++;
                synchronized (AdaptiveSchedulerProcessor.class) {
                    switchHistory.add(new SchedulerSwitch(taskCounter, cpuUsage, "FCFS", "Priority", System.currentTimeMillis()));
                }
                System.out.println(String.format(
                    "[ADAPTIVE] CPU: %.1f%% - Switching to Priority Scheduler (Switch #%d)", 
                    cpuUsage, schedulerSwitches));
            } else if (cpuUsage < 50.0 && "Priority".equals(currentSchedulerType)) {
                currentSchedulerType = "FCFS";
                schedulerSwitches++;
                synchronized (AdaptiveSchedulerProcessor.class) {
                    switchHistory.add(new SchedulerSwitch(taskCounter, cpuUsage, "Priority", "FCFS", System.currentTimeMillis()));
                }
                System.out.println(String.format(
                    "[ADAPTIVE] CPU: %.1f%% - Switching to FCFS Scheduler (Switch #%d)", 
                    cpuUsage, schedulerSwitches));
            }
            
            // Log CPU usage periodically
            if (taskCounter % 500 == 0) {
                System.out.println(String.format(
                    "[MONITOR] Task: %d, CPU: %.1f%%, Scheduler: %s, Switches: %d", 
                    taskCounter, cpuUsage, currentSchedulerType, schedulerSwitches));
            }
        }
        
        private double getSimulatedCpuUsage() {
            // Simular carga de CPU basada en número de tareas procesadas
            long cyclePosition = taskCounter % 2000;
            
            if (cyclePosition < 500) {
                return 30.0 + (cyclePosition * 0.1); // Carga baja: 30-80%
            } else if (cyclePosition < 1000) {
                return 80.0 + ((cyclePosition - 500) * 0.02); // Carga alta: 80-90%
            } else if (cyclePosition < 1200) {
                return 90.0 + ((cyclePosition - 1000) * 0.025); // Pico: 90-95%
            } else {
                return 95.0 - ((cyclePosition - 1200) * 0.08); // Descenso: 95-30%
            }
        }
        
        private long calculateAdaptiveWaitTime() {
            double cpuUsage = getSimulatedCpuUsage();
            
            if ("FCFS".equals(currentSchedulerType)) {
                // FCFS: más wait time cuando hay alta carga
                return (long)(taskCounter % 40 * (1 + cpuUsage / 100.0));
            } else {
                // Priority: menos wait time, especialmente bajo alta carga
                return Math.max(0, (long)(taskCounter % 2 * (1 - cpuUsage / 200.0)));
            }
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
    
    // Recolector de resultados para scheduler adaptativo
    public static class AdaptiveResultCollector implements MapFunction<TaskResult, String> {
        private static volatile long totalCount = 0;
        private static volatile long totalWait = 0;
        private static volatile long totalExecution = 0;
        private static volatile long startTime = 0;
        private static volatile long endTime = 0;
        
        @Override
        public String map(TaskResult result) {
            synchronized (AdaptiveResultCollector.class) {
                if (totalCount == 0) startTime = System.currentTimeMillis();
                totalCount++;
                totalWait += result.waitTime;
                totalExecution += result.executionTime;
                endTime = System.currentTimeMillis();
                
                if (totalCount % 1000 == 0) {
                    double avgWait = (double) totalWait / totalCount;
                    System.out.println("Adaptive - Processed: " + totalCount + ", Avg Wait: " + String.format("%.2f", avgWait) + "ms");
                }
            }
            return result.toString();
        }
        
        public static void printFinalSummary() {
            System.out.println();
            System.out.println("========================================");
            System.out.println("       ADAPTIVE SCHEDULER RESULTS      ");
            System.out.println("========================================");
            
            double avgWait = totalCount > 0 ? (double) totalWait / totalCount : 0;
            double avgExecution = totalCount > 0 ? (double) totalExecution / totalCount : 0;
            double avgTotal = avgWait + avgExecution;
            long duration = endTime - startTime;
            double throughput = duration > 0 ? (totalCount * 1000.0) / duration : 0;
            
            System.out.printf("Tasks Processed: %d%n", totalCount);
            System.out.printf("Avg Wait Time: %.2f ms%n", avgWait);
            System.out.printf("Avg Total Time: %.2f ms%n", avgTotal);
            System.out.printf("Throughput: %.2f tasks/sec%n", throughput);
            
            // Mostrar tabla de cambios de scheduler
            printSchedulerSwitchSummary();
            
            System.out.println("========================================");
        }
        
        private static void printSchedulerSwitchSummary() {
            System.out.println();
            System.out.println("========================================");
            System.out.println("       SCHEDULER SWITCH SUMMARY        ");
            System.out.println("========================================");
            
            if (AdaptiveSchedulerProcessor.switchHistory.isEmpty()) {
                System.out.println("No scheduler switches occurred during execution.");
                return;
            }
            
            System.out.printf("%-8s | %-8s | %-8s | %-12s | %-12s | %-10s%n", 
                "Switch#", "Task#", "CPU%", "From", "To", "Timestamp");
            System.out.println("---------|----------|----------|--------------|--------------|----------");
            
            for (int i = 0; i < AdaptiveSchedulerProcessor.switchHistory.size(); i++) {
                SchedulerSwitch sw = AdaptiveSchedulerProcessor.switchHistory.get(i);
                System.out.printf("%-8d | %-8d | %-8.1f | %-12s | %-12s | %-10d%n",
                    i + 1, sw.taskNumber, sw.cpuUsage, sw.fromScheduler, sw.toScheduler, 
                    sw.timestamp % 100000); // Solo últimos 5 dígitos del timestamp
            }
            
            System.out.println();
            System.out.printf("Total Switches: %d%n", AdaptiveSchedulerProcessor.switchHistory.size());
            
            // Estadísticas de uso por scheduler
            long fcfsTime = 0, priorityTime = 0;
            String currentScheduler = "FCFS";
            long lastTime = AdaptiveSchedulerProcessor.switchHistory.isEmpty() ? 0 : 
                AdaptiveSchedulerProcessor.switchHistory.get(0).timestamp;
            
            for (SchedulerSwitch sw : AdaptiveSchedulerProcessor.switchHistory) {
                long duration = sw.timestamp - lastTime;
                if ("FCFS".equals(currentScheduler)) {
                    fcfsTime += duration;
                } else {
                    priorityTime += duration;
                }
                currentScheduler = sw.toScheduler;
                lastTime = sw.timestamp;
            }
            
            long totalTime = fcfsTime + priorityTime;
            if (totalTime > 0) {
                System.out.printf("FCFS Usage: %.1f%% | Priority Usage: %.1f%%%n", 
                    (fcfsTime * 100.0) / totalTime, (priorityTime * 100.0) / totalTime);
            }
        }
    }
    
    // Recolector de resultados original (no usado en adaptive)
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
    
    // Clase para registrar cambios de scheduler
    public static class SchedulerSwitch implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final long taskNumber;
        public final double cpuUsage;
        public final String fromScheduler;
        public final String toScheduler;
        public final long timestamp;
        
        public SchedulerSwitch(long taskNumber, double cpuUsage, String fromScheduler, 
                              String toScheduler, long timestamp) {
            this.taskNumber = taskNumber;
            this.cpuUsage = cpuUsage;
            this.fromScheduler = fromScheduler;
            this.toScheduler = toScheduler;
            this.timestamp = timestamp;
        }
    }
}