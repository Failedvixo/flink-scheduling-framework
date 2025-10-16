package com.scheduling.framework;

import com.scheduling.framework.config.BenchmarkConfig;
import com.scheduling.framework.metrics.MetricsCollector;
import com.scheduling.framework.metrics.SchedulingMetrics;
import com.scheduling.framework.model.Task;
import com.scheduling.framework.nexmark.NexmarkAdapter;
import com.scheduling.framework.operator.SchedulingProcessFunction;
import com.scheduling.framework.scheduler.TaskScheduler;
import com.scheduling.framework.scheduler.impl.FCFSScheduler;
import com.scheduling.framework.scheduler.impl.PriorityScheduler;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Job para comparar el rendimiento de múltiples schedulers
 */
public class SchedulerComparisonJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerComparisonJob.class);
    
    public static void main(String[] args) throws Exception {
        
        // Configuración del benchmark
        BenchmarkConfig config = BenchmarkConfig.builder()
            .numEvents(5000)
            .schedulerCapacity(4)
            .processingDelayMs(10)
            .sourceParallelism(1)
            .eventDistribution(BenchmarkConfig.EventDistribution.UNIFORM)
            .build();
        
        LOG.info("Starting scheduler comparison");
        LOG.info("Configuration: {}", config);
        LOG.info("========================================");
        
        // Lista de schedulers a comparar
        List<TaskScheduler> schedulers = Arrays.asList(
            new FCFSScheduler(),
            new PriorityScheduler()
        );
        
        Map<String, SchedulingMetrics> results = new HashMap<>();
        
        // Ejecutar benchmark para cada scheduler
        for (TaskScheduler scheduler : schedulers) {
            LOG.info("Testing scheduler: {}", scheduler.getAlgorithmName());
            
            MetricsCollector metricsCollector = runBenchmark(config, scheduler);
            SchedulingMetrics metrics = metricsCollector.calculateMetrics();
            results.put(scheduler.getAlgorithmName(), metrics);
            
            LOG.info("Completed: {}", scheduler.getAlgorithmName());
            LOG.info("----------------------------------------");
            
            // Esperar entre ejecuciones
            Thread.sleep(2000);
        }
        
        // Imprimir comparación
        printComparison(results);
    }
    
    private static MetricsCollector runBenchmark(BenchmarkConfig config, TaskScheduler scheduler) 
            throws Exception {
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(config.getOperatorParallelism());
        
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Source de datos
        DataStream<Task> eventStream = env
            .addSource(new ConfigurableNexmarkSource(config))
            .setParallelism(config.getSourceParallelism())
            .name("Nexmark Source - " + scheduler.getAlgorithmName());
        
        // Procesador con scheduler
        DataStream<Task> processedStream = eventStream
            .process(new SchedulingProcessFunction(
                scheduler, 
                metricsCollector, 
                config.getProcessingDelayMs()))
            .name("Scheduler: " + scheduler.getAlgorithmName());
        
        // Sink silencioso (solo para testing)
        processedStream
            .map(new MapFunction<Task, Integer>() {
                @Override
                public Integer map(Task task) {
                    return 1;
                }
            })
            .name("Counter");
        
        // Ejecutar
        env.execute("Benchmark - " + scheduler.getAlgorithmName());
        
        return metricsCollector;
    }
    
    private static void printComparison(Map<String, SchedulingMetrics> results) {
        LOG.info("");
        LOG.info("========================================");
        LOG.info("       SCHEDULER COMPARISON RESULTS     ");
        LOG.info("========================================");
        LOG.info("");
        
        LOG.info(String.format("%-25s | %10s | %12s | %15s | %12s", 
            "Scheduler", "Completed", "Avg Wait(ms)", "Avg Total(ms)", "Throughput"));
        LOG.info("--------------------------|------------|--------------|-----------------|-------------");
        
        for (Map.Entry<String, SchedulingMetrics> entry : results.entrySet()) {
            SchedulingMetrics m = entry.getValue();
            LOG.info(String.format("%-25s | %10d | %12.2f | %15.2f | %12.2f",
                entry.getKey(),
                m.completedTasks,
                m.avgWaitingTime,
                m.avgTotalTime,
                m.throughput
            ));
        }
        
        LOG.info("");
        LOG.info("========================================");
        
        // Encontrar el mejor scheduler
        String bestThroughput = results.entrySet().stream()
            .max(Comparator.comparingDouble(e -> e.getValue().throughput))
            .map(Map.Entry::getKey)
            .orElse("N/A");
            
        String bestLatency = results.entrySet().stream()
            .min(Comparator.comparingDouble(e -> e.getValue().avgTotalTime))
            .map(Map.Entry::getKey)
            .orElse("N/A");
        
        LOG.info("Best Throughput: {}", bestThroughput);
        LOG.info("Best Latency: {}", bestLatency);
        LOG.info("========================================");
    }
    
    /**
     * Source configurable para testing
     */
    private static class ConfigurableNexmarkSource implements SourceFunction<Task> {
        
        private final BenchmarkConfig config;
        private volatile boolean running = true;
        private final Random random = new Random();
        
        public ConfigurableNexmarkSource(BenchmarkConfig config) {
            this.config = config;
        }
        
        @Override
        public void run(SourceContext<Task> ctx) throws Exception {
            int count = 0;
            
            while (running && count < config.getNumEvents()) {
                Task task = generateTask();
                ctx.collect(task);
                count++;
                
                // Simular diferentes patrones de llegada
                switch (config.getEventDistribution()) {
                    case BURSTY:
                        if (count % 100 == 0) {
                            Thread.sleep(random.nextInt(100));
                        }
                        break;
                    case UNIFORM:
                    default:
                        // Llegada constante
                        break;
                }
            }
        }
        
        private Task generateTask() {
            switch (config.getEventDistribution()) {
                case PERSON_HEAVY:
                    return random.nextInt(10) < 6 ? 
                        NexmarkAdapter.generatePersonTask() : 
                        generateRandomTask();
                case BID_HEAVY:
                    return random.nextInt(10) < 6 ? 
                        NexmarkAdapter.generateBidTask() : 
                        generateRandomTask();
                default:
                    return generateRandomTask();
            }
        }
        
        private Task generateRandomTask() {
            int type = random.nextInt(3);
            switch (type) {
                case 0: return NexmarkAdapter.generatePersonTask();
                case 1: return NexmarkAdapter.generateAuctionTask();
                default: return NexmarkAdapter.generateBidTask();
            }
        }
        
        @Override
        public void cancel() {
            running = false;
        }
    }
}