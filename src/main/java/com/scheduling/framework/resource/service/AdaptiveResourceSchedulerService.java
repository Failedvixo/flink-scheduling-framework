package com.scheduling.framework.resource.service;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ProcessingResource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de Resource Scheduling Adaptativo EXTERNO al pipeline de Flink
 * 
 * Este servicio gestiona pools de recursos y cambia entre ellos
 * basándose en la carga del sistema (CPU usage)
 */
public class AdaptiveResourceSchedulerService {
    
    // Singleton instance
    private static volatile AdaptiveResourceSchedulerService instance;
    
    // Pools de recursos separados
    private final ResourcePool fcfsPool;
    private final ResourcePool priorityPool;
    
    // Estado actual del scheduler
    private volatile SchedulingMode currentMode = SchedulingMode.FCFS;
    private final SystemMonitor systemMonitor;
    
    // Tracking de asignaciones activas
    private final ConcurrentHashMap<String, ResourceAssignment> activeAssignments;
    
    // Tracking de switches
    private final List<SchedulerSwitch> switchHistory;
    private final AtomicInteger switchCounter;
    
    // Configuración de thresholds
    private double highCpuThreshold = 90.0;
    private double lowCpuThreshold = 50.0;
    
    // Thread de monitoreo
    private Thread monitorThread;
    private volatile boolean running = true;
    
    /**
     * Constructor privado (Singleton)
     */
    private AdaptiveResourceSchedulerService(int fcfsPoolSize, int priorityPoolSize) {
        this.fcfsPool = new ResourcePool("FCFS-Pool", fcfsPoolSize);
        this.priorityPool = new ResourcePool("Priority-Pool", priorityPoolSize);
        this.systemMonitor = new SystemMonitor();
        this.activeAssignments = new ConcurrentHashMap<>();
        this.switchHistory = new ArrayList<>();
        this.switchCounter = new AtomicInteger(0);
        
        System.out.println("========================================");
        System.out.println("  ADAPTIVE RESOURCE SCHEDULER SERVICE  ");
        System.out.println("========================================");
        System.out.println("FCFS Pool: " + fcfsPoolSize + " resources");
        System.out.println("Priority Pool: " + priorityPoolSize + " resources");
        System.out.println("Initial Mode: " + currentMode);
        System.out.println("========================================");
        
        // Iniciar monitoreo adaptativo
        startAdaptiveMonitoring();
    }
    
    /**
     * Inicializar el servicio (llamar una vez al inicio)
     */
    public static void initialize(int fcfsPoolSize, int priorityPoolSize) {
        if (instance == null) {
            synchronized (AdaptiveResourceSchedulerService.class) {
                if (instance == null) {
                    instance = new AdaptiveResourceSchedulerService(fcfsPoolSize, priorityPoolSize);
                }
            }
        }
    }
    
    /**
     * Obtener instancia del servicio
     */
    public static AdaptiveResourceSchedulerService getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "AdaptiveResourceSchedulerService not initialized. Call initialize() first."
            );
        }
        return instance;
    }
    
    /**
     * Asignar tarea a un recurso real
     * ESTE método es llamado ANTES de ejecutar la tarea en Flink
     */
    public ResourceAssignment assignResource(Task task) {
        // Seleccionar pool basado en modo actual
        ResourcePool selectedPool = selectPool();
        
        // Intentar obtener recurso del pool
        ProcessingResource resource = selectedPool.acquireResource(task);
        
        if (resource == null) {
            // No hay recursos disponibles - la tarea debe esperar
            return ResourceAssignment.waiting(task);
        }
        
        // Crear asignación exitosa
        ResourceAssignment assignment = new ResourceAssignment(
            task.getTaskId(),
            resource,
            selectedPool.getName(),
            System.currentTimeMillis()
        );
        
        // Registrar asignación activa
        activeAssignments.put(task.getTaskId(), assignment);
        
        return assignment;
    }
    
    /**
     * Liberar recurso cuando tarea termina
     */
    public void releaseResource(String taskId) {
        ResourceAssignment assignment = activeAssignments.remove(taskId);
        
        if (assignment != null && !assignment.isWaiting()) {
            // Determinar a qué pool pertenece
            ResourcePool pool = assignment.getPoolName().startsWith("FCFS") ? 
                fcfsPool : priorityPool;
            
            // Retornar recurso al pool
            pool.releaseResource(assignment.getResource());
        }
    }
    
    /**
     * Seleccionar pool basado en modo actual
     */
    private ResourcePool selectPool() {
        return currentMode == SchedulingMode.FCFS ? fcfsPool : priorityPool;
    }
    
    /**
     * Verificar si debemos cambiar de modo basado en CPU
     */
    private void checkAndSwitch() {
        double cpuUsage = systemMonitor.getCurrentCpuUsage();
        SchedulingMode oldMode = currentMode;
        
        // Lógica de switching adaptativo
        if (cpuUsage > highCpuThreshold && currentMode == SchedulingMode.FCFS) {
            // CPU alta: cambiar a Priority
            currentMode = SchedulingMode.PRIORITY;
            recordSwitch(oldMode, currentMode, cpuUsage);
            
        } else if (cpuUsage < lowCpuThreshold && currentMode == SchedulingMode.PRIORITY) {
            // CPU baja: cambiar a FCFS
            currentMode = SchedulingMode.FCFS;
            recordSwitch(oldMode, currentMode, cpuUsage);
        }
    }
    
    /**
     * Registrar un switch de scheduler
     */
    private void recordSwitch(SchedulingMode from, SchedulingMode to, double cpuUsage) {
        int switchNumber = switchCounter.incrementAndGet();
        
        SchedulerSwitch switchEvent = new SchedulerSwitch(
            switchNumber,
            cpuUsage,
            from.toString(),
            to.toString(),
            System.currentTimeMillis()
        );
        
        synchronized (switchHistory) {
            switchHistory.add(switchEvent);
        }
        
        System.out.println(String.format(
            "[RESOURCE SCHEDULER] Switch #%d: %s → %s (CPU: %.1f%%)",
            switchNumber, from, to, cpuUsage
        ));
    }
    
    /**
     * Iniciar thread de monitoreo adaptativo
     */
    private void startAdaptiveMonitoring() {
        monitorThread = new Thread(() -> {
            System.out.println("[MONITOR] Adaptive monitoring started");
            
            while (running) {
                try {
                    // Check cada 1 segundo
                    Thread.sleep(1000);
                    
                    // Verificar si debemos cambiar de modo
                    checkAndSwitch();
                    
                    // Log periódico de estadísticas (cada 5 segundos)
                    if (System.currentTimeMillis() % 5000 < 1000) {
                        logStatistics();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            System.out.println("[MONITOR] Adaptive monitoring stopped");
        });
        
        monitorThread.setDaemon(true);
        monitorThread.setName("AdaptiveSchedulerMonitor");
        monitorThread.start();
    }
    
    /**
     * Log de estadísticas del sistema
     */
    private void logStatistics() {
        double cpuUsage = systemMonitor.getCurrentCpuUsage();
        
        ResourcePool.PoolStatistics fcfsStats = fcfsPool.getStatistics();
        ResourcePool.PoolStatistics priorityStats = priorityPool.getStatistics();
        
        System.out.println(String.format(
            "[STATS] CPU: %.1f%% | Mode: %s | %s | %s",
            cpuUsage, currentMode, fcfsStats, priorityStats
        ));
    }
    
    /**
     * Configurar thresholds de CPU
     */
    public void setThresholds(double highCpuThreshold, double lowCpuThreshold) {
        this.highCpuThreshold = highCpuThreshold;
        this.lowCpuThreshold = lowCpuThreshold;
        System.out.println(String.format(
            "[CONFIG] Thresholds updated: High=%.1f%%, Low=%.1f%%",
            highCpuThreshold, lowCpuThreshold
        ));
    }
    
    /**
     * Habilitar monitoreo de CPU real (en lugar de simulado)
     */
    public void enableRealCpuMonitoring() {
        systemMonitor.enableRealCpuMonitoring();
    }
    
    /**
     * Obtener historial de switches
     */
    public List<SchedulerSwitch> getSwitchHistory() {
        synchronized (switchHistory) {
            return new ArrayList<>(switchHistory);
        }
    }
    
    /**
     * Obtener estadísticas actuales
     */
    public SchedulerStatistics getStatistics() {
        return new SchedulerStatistics(
            currentMode,
            fcfsPool.getStatistics(),
            priorityPool.getStatistics(),
            systemMonitor.getCurrentCpuUsage(),
            switchCounter.get(),
            activeAssignments.size()
        );
    }
    
    /**
     * Imprimir resumen final
     */
    public void printFinalSummary() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  RESOURCE SCHEDULER FINAL SUMMARY     ");
        System.out.println("========================================");
        System.out.println();
        
        // Estadísticas de switches
        System.out.println("SCHEDULER SWITCHES:");
        System.out.println("Total Switches: " + switchCounter.get());
        
        if (!switchHistory.isEmpty()) {
            System.out.println();
            System.out.printf("%-8s | %-8s | %-12s | %-12s | %-10s%n", 
                "Switch#", "CPU%", "From", "To", "Timestamp");
            System.out.println("---------|----------|--------------|--------------|----------");
            
            synchronized (switchHistory) {
                for (SchedulerSwitch sw : switchHistory) {
                    System.out.printf("%-8d | %-8.1f | %-12s | %-12s | %-10d%n",
                        sw.switchNumber, sw.cpuUsage, sw.fromMode, sw.toMode,
                        sw.timestamp % 100000);
                }
            }
        }
        
        System.out.println();
        System.out.println("RESOURCE POOL UTILIZATION:");
        System.out.println(fcfsPool.getStatistics());
        System.out.println(priorityPool.getStatistics());
        
        System.out.println();
        System.out.println("========================================");
    }
    
    /**
     * Shutdown del servicio
     */
    public void shutdown() {
        System.out.println("[SCHEDULER] Shutting down...");
        running = false;
        
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        
        printFinalSummary();
        System.out.println("[SCHEDULER] Shutdown complete");
    }
    
    /**
     * Enum para modos de scheduling
     */
    public enum SchedulingMode {
        FCFS, PRIORITY
    }
    
    /**
     * Clase interna para eventos de switch
     */
    public static class SchedulerSwitch {
        public final int switchNumber;
        public final double cpuUsage;
        public final String fromMode;
        public final String toMode;
        public final long timestamp;
        
        public SchedulerSwitch(int switchNumber, double cpuUsage, 
                              String fromMode, String toMode, long timestamp) {
            this.switchNumber = switchNumber;
            this.cpuUsage = cpuUsage;
            this.fromMode = fromMode;
            this.toMode = toMode;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Clase para estadísticas del scheduler
     */
    public static class SchedulerStatistics {
        public final SchedulingMode currentMode;
        public final ResourcePool.PoolStatistics fcfsStats;
        public final ResourcePool.PoolStatistics priorityStats;
        public final double currentCpuUsage;
        public final int totalSwitches;
        public final int activeAssignments;
        
        public SchedulerStatistics(SchedulingMode currentMode,
                                  ResourcePool.PoolStatistics fcfsStats,
                                  ResourcePool.PoolStatistics priorityStats,
                                  double currentCpuUsage,
                                  int totalSwitches,
                                  int activeAssignments) {
            this.currentMode = currentMode;
            this.fcfsStats = fcfsStats;
            this.priorityStats = priorityStats;
            this.currentCpuUsage = currentCpuUsage;
            this.totalSwitches = totalSwitches;
            this.activeAssignments = activeAssignments;
        }
    }
}