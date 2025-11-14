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
        long cyclePosition = taskCounter % 2000;
        
        if (cyclePosition < 500) {
            // Carga baja: 30-80%
            return 30.0 + (cyclePosition * 0.1);
        } else if (cyclePosition < 1000) {
            // Carga alta: 80-90%
            return 80.0 + ((cyclePosition - 500) * 0.02);
        } else if (cyclePosition < 1200) {
            // Pico: 90-95%
            return 90.0 + ((cyclePosition - 1000) * 0.025);
        } else {
            // Descenso: 95-30%
            return 95.0 - ((cyclePosition - 1200) * 0.08);
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