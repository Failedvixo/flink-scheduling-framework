package com.scheduling.framework.operator;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.scheduler.TaskScheduler;
import com.scheduling.framework.metrics.MetricsCollector;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operador de Flink que procesa eventos usando un scheduler configurable
 */
public class SchedulingProcessFunction extends ProcessFunction<Task, Task> {
    
    private static final Logger LOG = LoggerFactory.getLogger(SchedulingProcessFunction.class);
    
    private final TaskScheduler scheduler;
    private final MetricsCollector metricsCollector;
    private final int processingDelayMs;
    
    public SchedulingProcessFunction(TaskScheduler scheduler, MetricsCollector metricsCollector, int processingDelayMs) {
        this.scheduler = scheduler;
        this.metricsCollector = metricsCollector;
        this.processingDelayMs = processingDelayMs;
    }
    
    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        super.open(parameters);
        scheduler.initialize(4); // Capacidad por defecto
        LOG.info("SchedulingProcessFunction opened with scheduler: {}", scheduler.getAlgorithmName());
    }
    
    @Override
    public void processElement(Task task, Context ctx, Collector<Task> out) throws Exception {
        // Enviar tarea al scheduler
        scheduler.submitTask(task);
        metricsCollector.recordTaskSubmission();
        
        // Intentar procesar tareas
        Task nextTask;
        while ((nextTask = scheduler.getNextTask()) != null) {
            // Simular procesamiento
            processTask(nextTask);
            
            // Marcar como completada
            scheduler.completeTask(nextTask.getTaskId());
            metricsCollector.recordTaskCompletion(nextTask);
            
            // Emitir tarea procesada
            out.collect(nextTask);
        }
    }
    
    /**
     * Simula el procesamiento de una tarea
     */
    private void processTask(Task task) throws InterruptedException {
        // Simular trabajo de procesamiento
        if (processingDelayMs > 0) {
            Thread.sleep(processingDelayMs);
        }
        
        // Aquí iría la lógica real de procesamiento según el tipo de evento
        switch (task.getEventType()) {
            case "PERSON":
                // Procesar evento de persona
                LOG.debug("Processing PERSON event: {}", task.getTaskId());
                break;
            case "AUCTION":
                // Procesar evento de subasta
                LOG.debug("Processing AUCTION event: {}", task.getTaskId());
                break;
            case "BID":
                // Procesar evento de puja
                LOG.debug("Processing BID event: {}", task.getTaskId());
                break;
            default:
                LOG.warn("Unknown event type: {}", task.getEventType());
        }
    }
    
    @Override
    public void close() throws Exception {
        super.close();
        metricsCollector.markEnd();
        metricsCollector.printMetrics(scheduler.getAlgorithmName());
    }
}