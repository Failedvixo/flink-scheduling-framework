package com.scheduling.framework.operator;

import com.scheduling.framework.metrics.MetricsCollector;
import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.ResourceScheduler;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Función de procesamiento que usa Resource Scheduling
 */
public class ResourceSchedulingProcessFunction extends ProcessFunction<Task, Task> {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResourceSchedulingProcessFunction.class);
    
    private final ResourceScheduler resourceScheduler;
    private final MetricsCollector metricsCollector;
    private final int processingDelayMs;
    private final int resourceCount;
    
    public ResourceSchedulingProcessFunction(ResourceScheduler resourceScheduler, 
                                           MetricsCollector metricsCollector,
                                           int processingDelayMs,
                                           int resourceCount) {
        this.resourceScheduler = resourceScheduler;
        this.metricsCollector = metricsCollector;
        this.processingDelayMs = processingDelayMs;
        this.resourceCount = resourceCount;
    }
    
    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        super.open(parameters);
        
        // Crear recursos de procesamiento
        List<ProcessingResource> resources = new ArrayList<>();
        for (int i = 0; i < resourceCount; i++) {
            resources.add(new ProcessingResource("Resource-" + i, 1));
        }
        
        resourceScheduler.initialize(resources);
        LOG.info("Initialized {} with {} resources", 
                resourceScheduler.getAlgorithmName(), resourceCount);
    }
    
    @Override
    public void processElement(Task task, Context ctx, Collector<Task> out) throws Exception {
        metricsCollector.recordTaskSubmission();
        
        // Asignar tarea a un recurso
        ProcessingResource assignedResource = resourceScheduler.assignTaskToResource(task);
        
        if (assignedResource != null) {
            // Simular procesamiento
            task.setStartTime(System.currentTimeMillis());
            
            // Simular delay de procesamiento
            Thread.sleep(processingDelayMs);
            
            task.setCompletionTime(System.currentTimeMillis());
            
            // Liberar recurso
            resourceScheduler.releaseResource(assignedResource.getResourceId(), task);
            
            // Registrar métricas
            metricsCollector.recordTaskCompletion(task);
            
            // Emitir tarea procesada
            out.collect(task);
            
            LOG.debug("Task {} processed on {}", task.getTaskId(), assignedResource.getResourceId());
        } else {
            LOG.warn("No available resources for task {}", task.getTaskId());
        }
    }
}