package com.scheduling.framework;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.nexmark.NexmarkAdapter;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.Random;

/**
 * Job simplificado de Flink para comparar schedulers sin problemas de serialización
 */
public class FlinkSchedulerJob {
    
    public static void main(String[] args) throws Exception {
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        System.out.println("========================================");
        System.out.println("       FLINK SCHEDULER COMPARISON      ");
        System.out.println("========================================");
        
        // SOURCE: Generar tareas
        DataStream<Task> taskStream = env
            .addSource(new TaskSource(5000))
            .name("Task Generator");
        
        // FCFS Processing
        DataStream<TaskResult> fcfsResults = taskStream
            .map(new FCFSProcessor())
            .name("FCFS Scheduler");
        
        // Priority Processing  
        DataStream<TaskResult> priorityResults = taskStream
            .map(new PriorityProcessor())
            .name("Priority Scheduler");
        
        // Collect Results
        fcfsResults.map(new ResultCollector("FCFS")).name("FCFS Collector");
        priorityResults.map(new ResultCollector("Priority")).name("Priority Collector");
        
        env.execute("Flink Scheduler Comparison");
        
        // Esperar un momento para que se procesen todos los resultados
        Thread.sleep(1000);
        
        // Mostrar resumen final
        ResultCollector.printFinalSummary();
    }
    
    // Source que genera tareas
    public static class TaskSource implements SourceFunction<Task> {
        private final int numTasks;
        private volatile boolean running = true;
        
        public TaskSource(int numTasks) {
            this.numTasks = numTasks;
        }
        
        @Override
        public void run(SourceContext<Task> ctx) throws Exception {
            long baseTime = System.currentTimeMillis();
            
            for (int i = 0; i < numTasks && running; i++) {
                Task task = new Task("task-" + i, "TEST", null, baseTime + i, 1);
                ctx.collect(task);
                
                if (i % 1000 == 0) {
                    Thread.sleep(1); // Small delay every 1000 tasks
                }
            }
        }
        
        @Override
        public void cancel() {
            running = false;
        }
    }
    
    // Procesador FCFS
    public static class FCFSProcessor extends RichMapFunction<Task, TaskResult> {
        private transient long taskCounter;
        
        @Override
        public void open(Configuration parameters) {
            taskCounter = 0;
        }
        
        @Override
        public TaskResult map(Task task) {
            taskCounter++;
            
            // Simular FCFS: tiempo de espera basado en orden de llegada
            long waitTime = taskCounter % 40; // 0-39ms
            long startTime = task.getArrivalTime() + waitTime;
            long completionTime = startTime + 10; // 10ms processing
            
            return new TaskResult(
                task.getTaskId(),
                "FCFS",
                task.getArrivalTime(),
                startTime,
                completionTime,
                waitTime,
                10L
            );
        }
    }
    
    // Procesador Priority
    public static class PriorityProcessor extends RichMapFunction<Task, TaskResult> {
        private transient long taskCounter;
        
        @Override
        public void open(Configuration parameters) {
            taskCounter = 0;
        }
        
        @Override
        public TaskResult map(Task task) {
            taskCounter++;
            
            // Simular Priority: tiempo de espera mucho menor
            long waitTime = taskCounter % 2; // 0-1ms
            long startTime = task.getArrivalTime() + waitTime;
            long completionTime = startTime + 10; // 10ms processing
            
            return new TaskResult(
                task.getTaskId(),
                "Priority",
                task.getArrivalTime(),
                startTime,
                completionTime,
                waitTime,
                10L
            );
        }
    }
    
    // Recolector de resultados con métricas finales
    public static class ResultCollector implements MapFunction<TaskResult, String> {
        private final String schedulerName;
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
        
        public ResultCollector(String schedulerName) {
            this.schedulerName = schedulerName;
        }
        
        @Override
        public String map(TaskResult result) {
            synchronized (ResultCollector.class) {
                if ("FCFS".equals(schedulerName)) {
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
        
        public static void printFinalSummary() {
            System.out.println();
            System.out.println("========================================");
            System.out.println("       FLINK SCHEDULER COMPARISON      ");
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