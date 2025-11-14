package com.scheduling.framework.resource.service;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

/**
 * Monitorea métricas del sistema (CPU, memoria, etc.)
 */
public class SystemMonitor {
    
    private final OperatingSystemMXBean osBean;
    private long taskCounter = 0;
    private boolean useRealCpu = false; // Por defecto usar simulado
    
    public SystemMonitor() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }
    
    /**
     * Obtener uso actual de CPU
     */
    public double getCurrentCpuUsage() {
        if (useRealCpu) {
            return getRealCpuUsage();
        } else {
            return getSimulatedCpuUsage();
        }
    }
    
    /**
     * CPU real del sistema
     */
    private double getRealCpuUsage() {
        try {
            double cpuLoad = osBean.getProcessCpuLoad() * 100.0;
            // Si no está disponible, usar load average del sistema
            if (cpuLoad < 0) {
                double systemLoad = osBean.getSystemCpuLoad() * 100.0;
                return systemLoad >= 0 ? systemLoad : getSimulatedCpuUsage();
            }
            return cpuLoad;
        } catch (Exception e) {
            // Fallback a simulado si hay error
            return getSimulatedCpuUsage();
        }
    }
    
    /**
     * CPU simulada para testing reproducible
     */
    private double getSimulatedCpuUsage() {
        taskCounter++;
        long cyclePosition = taskCounter % 400;  // Ciclo más corto para más switches
        
        if (cyclePosition < 100) {
            // Carga baja: 20-40% (debajo del threshold de 50%)
            return 20.0 + (cyclePosition * 0.2);
        } else if (cyclePosition < 200) {
            // Subida rápida: 40-95% (cruza threshold de 90%)
            return 40.0 + ((cyclePosition - 100) * 0.55);
        } else if (cyclePosition < 300) {
            // Carga alta: 95-92% (arriba del threshold de 90%)
            return 95.0 - ((cyclePosition - 200) * 0.03);
        } else {
            // Descenso rápido: 92-20% (cruza threshold de 50%)
            return 92.0 - ((cyclePosition - 300) * 0.72);
        }
    }
    
    /**
     * Habilitar uso de CPU real del sistema
     */
    public void enableRealCpuMonitoring() {
        this.useRealCpu = true;
        System.out.println("[MONITOR] Real CPU monitoring enabled");
    }
    
    /**
     * Usar CPU simulada (para testing reproducible)
     */
    public void enableSimulatedCpuMonitoring() {
        this.useRealCpu = false;
        System.out.println("[MONITOR] Simulated CPU monitoring enabled");
    }
    
    public boolean isUsingRealCpu() {
        return useRealCpu;
    }
}