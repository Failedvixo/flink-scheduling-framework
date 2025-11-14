package com.scheduling.framework.operator;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import com.scheduling.framework.resource.service.AdaptiveResourceSchedulerService;
import com.scheduling.framework.resource.service.ResourceAssignment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

/**
 * Process Function que consulta el Resource Scheduler EXTERNO
 * 
 * Este operador NO es un scheduler - solo ejecuta tareas usando
 * recursos asignados por el servicio externo de scheduling
 */
public class ResourceAwareProcessFunction extends ProcessFunction<Task, Task> {
    
    private transient AdaptiveResourceSchedulerService resourceScheduler;
    private transient long tasksProcessed = 0;
    private transient long tasksWaited = 0;
    
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        
        // Obtener instancia del scheduler externo
        this.resourceScheduler = AdaptiveResourceSchedulerService.getInstance();
        
        System.out.println("[OPERATOR] ResourceAwareProcessFunction initialized");
    }
    
    @Override
    public void processElement(Task task, Context ctx, Collector<Task> out) throws Exception {
        
        // 1. PEDIR recurso al scheduler externo
        ResourceAssignment assignment = resourceScheduler.assignResource(task);
        
        // Registrar tiempo de inicio
        task.setStartTime(System.currentTimeMillis());
        
        if (assignment.isWaiting()) {
            // No hay recursos disponibles - tarea debe esperar
            tasksWaited++;
            
            // Esperar un poco y reintentar (simple retry logic)
            Thread.sleep(10);
            
            // Reintentar asignación
            assignment = resourceScheduler.assignResource(task);
            
            if (assignment.isWaiting()) {
                // Aún no hay recursos - rechazar o encolar
                System.out.println("[OPERATOR] Task " + task.getTaskId() + " REJECTED - no resources");
                task.setStatus(Task.TaskStatus.FAILED);
                out.collect(task);
                return;
            }
        }
        
        // 2. EJECUTAR tarea con el recurso asignado
        ProcessingResource resource = assignment.getResource();
        
        try {
            // Simular procesamiento según tipo de evento
            long processingTime = getProcessingTime(task);
            Thread.sleep(processingTime);
            
            // Marcar como completada
            task.setCompletionTime(System.currentTimeMillis());
            task.setStatus(Task.TaskStatus.COMPLETED);
            
            tasksProcessed++;
            
            // Log periódico
            if (tasksProcessed % 100 == 0) {
                System.out.println(String.format(
                    "[OPERATOR] Processed: %d tasks, Waited: %d tasks",
                    tasksProcessed, tasksWaited
                ));
            }
            
            // Log detallado ocasional
            if (tasksProcessed % 500 == 0) {
                System.out.println(String.format(
                    "[TASK] %s processed on %s (%s) - Wait: %dms, Exec: %dms",
                    task.getTaskId(),
                    resource.getResourceId(),
                    assignment.getPoolName(),
                    task.getWaitingTime(),
                    task.getExecutionTime()
                ));
            }
            
        } finally {
            // 3. LIBERAR recurso (SIEMPRE ejecutar)
            resourceScheduler.releaseResource(task.getTaskId());
        }
        
        // Emitir tarea procesada
        out.collect(task);
    }
    
    /**
     * Calcular tiempo de procesamiento según tipo de evento
     */
    private long getProcessingTime(Task task) {
        switch (task.getEventType()) {
            case "BID":
                return 5;      // BIDs son rápidos
            case "AUCTION":
                return 15;     // AUCTIONs son complejos
            case "PERSON":
                return 10;     // PERSONs son normales
            default:
                return 10;
        }
    }
    
    @Override
    public void close() throws Exception {
        super.close();
        
        System.out.println(String.format(
            "[OPERATOR] Closing - Total processed: %d, Total waited: %d",
            tasksProcessed, tasksWaited
        ));
    }
}