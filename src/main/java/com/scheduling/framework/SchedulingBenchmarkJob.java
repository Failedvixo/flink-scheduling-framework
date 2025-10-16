package com.scheduling.framework;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.nexmark.NexmarkAdapter;
import com.scheduling.framework.operator.SchedulingProcessFunction;
import com.scheduling.framework.scheduler.TaskScheduler;
import com.scheduling.framework.scheduler.impl.FCFSScheduler;
import com.scheduling.framework.metrics.MetricsCollector;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Job principal que ejecuta el benchmark con el scheduler configurado
 */
public class SchedulingBenchmarkJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchedulingBenchmarkJob.class);
    
    public static void main(String[] args) throws Exception {
        
        // Configuración del benchmark
        final int numEvents = 10000;
        final int sourceParallelism = 1;
        final int processingDelayMs = 10; // ms de delay por tarea
        
        // Configurar el entorno de Flink
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Para control determinístico en el benchmark
        
        // Crear el scheduler (FCFS en este caso)
        TaskScheduler scheduler = new FCFSScheduler();
        MetricsCollector metricsCollector = new MetricsCollector();
        
        LOG.info("Starting benchmark with {} scheduler", scheduler.getAlgorithmName());
        LOG.info("Number of events: {}", numEvents);
        LOG.info("Processing delay: {} ms", processingDelayMs);
        
        // Crear stream de datos simulado (Nexmark-like)
        DataStream<Task> eventStream = env
            .addSource(new NexmarkSimulatedSource(numEvents))
            .setParallelism(sourceParallelism)
            .name("Nexmark Event Source");
        
        // Aplicar el scheduler
        DataStream<Task> processedStream = eventStream
            .process(new SchedulingProcessFunction(scheduler, metricsCollector, processingDelayMs))
            .name("Scheduling Processor");
        
        // Sink para contar resultados
        processedStream
            .map(new MapFunction<Task, String>() {
                @Override
                public NexmarkSimulatedSource(int numEvents) {
            this.numEvents = numEvents;
        }
        
        @Override
        public void run(SourceContext<Task> ctx) throws Exception {
            int count = 0;
            
            while (running && count < numEvents) {
                // Generar evento aleatorio (33% cada tipo)
                int eventType = random.nextInt(3);
                Task task;
                
                switch (eventType) {
                    case 0:
                        task = NexmarkAdapter.generatePersonTask();
                        break;
                    case 1:
                        task = NexmarkAdapter.generateAuctionTask();
                        break;
                    case 2:
                        task = NexmarkAdapter.generateBidTask();
                        break;
                    default:
                        task = NexmarkAdapter.generateBidTask();
                }
                
                ctx.collect(task);
                count++;
                
                // Simular llegada de eventos con rate variable
                if (count % 100 == 0) {
                    Thread.sleep(random.nextInt(50)); // Burst ocasional
                }
            }
        }
        
        @Override
        public void cancel() {
            running = false;
        }
    }
} String map(Task task) {
                    return String.format("Processed: %s [Wait: %d ms, Exec: %d ms]",
                        task.getTaskId(),
                        task.getWaitingTime(),
                        task.getExecutionTime());
                }
            })
            .print()
            .name("Result Sink");
        
        // Ejecutar el job
        env.execute("Flink Scheduling Benchmark - " + scheduler.getAlgorithmName());
    }
    
    /**
     * Source que simula eventos de Nexmark
     */
    private static class NexmarkSimulatedSource implements SourceFunction<Task> {
        
        private final int numEvents;
        private volatile boolean running = true;
        private final Random random = new Random();
        
        public