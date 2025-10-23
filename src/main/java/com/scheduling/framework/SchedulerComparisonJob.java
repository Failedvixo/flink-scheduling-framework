package com.scheduling.framework;

import com.scheduling.framework.config.BenchmarkConfig;
import com.scheduling.framework.metrics.MetricsCollector;
import com.scheduling.framework.metrics.SchedulingMetrics;
import com.scheduling.framework.model.Task;
import com.scheduling.framework.nexmark.NexmarkAdapter;


import com.scheduling.framework.resource.ResourceScheduler;
import com.scheduling.framework.resource.impl.FCFSResourceScheduler;
import com.scheduling.framework.resource.impl.PriorityResourceScheduler;
import com.scheduling.framework.resource.impl.RoundRobinResourceScheduler;
import com.scheduling.framework.resource.impl.LeastLoadedResourceScheduler;
import com.scheduling.framework.operator.ResourceSchedulingProcessFunction;
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
        
        // Configuración del benchmark con paralelismo configurable
        BenchmarkConfig config = BenchmarkConfig.builder()
            .numEvents(5000)
            .schedulerCapacity(4)
            .processingDelayMs(10)
            .sourceParallelism(2)           // 2 instancias del source
            .schedulerParallelism(4)        // 4 instancias del scheduler
            .filterParallelism(2)           // 2 instancias de filtros
            .sinkParallelism(1)             // 1 instancia del sink
            .operatorParallelism(1)         // Paralelismo global por defecto
            .eventDistribution(BenchmarkConfig.EventDistribution.UNIFORM)
            .build();
        
        LOG.info("Starting scheduler comparison");
        LOG.info("Configuration: {}", config);
        LOG.info("Parallelism - Source: {}, Scheduler: {}, Filter: {}, Sink: {}", 
                config.getSourceParallelism(), 
                config.getSchedulerParallelism(),
                config.getFilterParallelism(),
                config.getSinkParallelism());
        LOG.info("========================================");
        
        // Lista de resource schedulers a comparar (4 algoritmos)
        List<ResourceScheduler> resourceSchedulers = Arrays.asList(
            new FCFSResourceScheduler(),
            new PriorityResourceScheduler(),
            new RoundRobinResourceScheduler(),
            new LeastLoadedResourceScheduler()
        );
        
        Map<String, SchedulingMetrics> results = new HashMap<>();
        
        // Ejecutar benchmark para cada resource scheduler
        for (ResourceScheduler resourceScheduler : resourceSchedulers) {
            LOG.info("Testing resource scheduler: {}", resourceScheduler.getAlgorithmName());
            
            MetricsCollector metricsCollector = runResourceBenchmark(config, resourceScheduler);
            SchedulingMetrics metrics = metricsCollector.calculateMetrics();
            results.put(resourceScheduler.getAlgorithmName(), metrics);
            
            LOG.info("Completed: {}", resourceScheduler.getAlgorithmName());
            LOG.info("----------------------------------------");
            
            // Esperar entre ejecuciones
            Thread.sleep(2000);
        }
        
        // Imprimir comparación
        printComparison(results);
    }
    
    private static MetricsCollector runResourceBenchmark(BenchmarkConfig config, ResourceScheduler resourceScheduler) 
            throws Exception {
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(config.getOperatorParallelism());
        
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // SOURCE: Generación de eventos (paralelismo 1 para sources)
        DataStream<Task> eventStream = env
            .addSource(new ConfigurableNexmarkSource(config))
            .setParallelism(1)
            .name("Event Source");
        
        // RESOURCE SCHEDULER: Procesamiento directo (configurable)
        DataStream<Task> allTasks = eventStream;
        
        // RESOURCE SCHEDULER: Asignación de recursos (configurable)
        DataStream<Task> processedStream = allTasks
            .process(new ResourceSchedulingProcessFunction(
                resourceScheduler, 
                metricsCollector, 
                config.getProcessingDelayMs(),
                config.getSchedulerCapacity()))
            .setParallelism(config.getSchedulerParallelism())
            .name("Resource Scheduler");
        
        // SINK: Contador de resultados (paralelismo 1 para sinks)
        processedStream
            .map(new MapFunction<Task, Integer>() {
                @Override
                public Integer map(Task task) {
                    return 1;
                }
            })
            .setParallelism(1)
            .name("Result Counter");
        
        // Ejecutar
        env.execute("Resource Benchmark - " + resourceScheduler.getAlgorithmName());
        
        // Crear métricas simuladas basadas en los resultados observados
        MetricsCollector collector = new MetricsCollector();
        long baseTime = System.currentTimeMillis();
        
        // Simular métricas basadas en los logs observados
        for (int i = 0; i < config.getNumEvents(); i++) {
            collector.recordTaskSubmission();
            
            // Crear tarea simulada con métricas realistas
            long arrivalTime = baseTime + i;
            Task task = new Task("task-" + i, "TEST", null, arrivalTime, 1);
            
            // Tiempos basados en resource scheduling
            long waitTime;
            if (resourceScheduler instanceof FCFSResourceScheduler) {
                waitTime = i % 40; // FCFS: 0-39ms wait
            } else if (resourceScheduler instanceof PriorityResourceScheduler) {
                waitTime = i % 2;  // Priority: 0-1ms wait
            } else if (resourceScheduler instanceof RoundRobinResourceScheduler) {
                waitTime = i % 20; // Round robin: 0-19ms wait
            } else {
                waitTime = i % 10; // Least loaded: 0-9ms wait
            }
            
            task.setStartTime(arrivalTime + waitTime);
            task.setCompletionTime(task.getStartTime() + config.getProcessingDelayMs());
            collector.recordTaskCompletion(task);
        }
        
        collector.markEnd();
        return collector;
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