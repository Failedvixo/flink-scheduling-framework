package com.scheduling.framework.config;

/**
 * Configuraciones predefinidas para diferentes topologías de grafo
 */
public class GraphConfigurations {
    
    /**
     * Configuración simple: 1 operador por tipo
     */
    public static BenchmarkConfig simpleGraph() {
        return BenchmarkConfig.builder()
            .numEvents(5000)
            .sourceParallelism(1)
            .schedulerParallelism(1)
            .filterParallelism(1)
            .sinkParallelism(1)
            .build();
    }
    
    /**
     * Configuración escalada: Múltiples operadores
     */
    public static BenchmarkConfig scaledGraph() {
        return BenchmarkConfig.builder()
            .numEvents(10000)
            .sourceParallelism(2)      // 2 sources
            .schedulerParallelism(4)   // 4 schedulers
            .filterParallelism(3)      // 3 filters
            .sinkParallelism(2)        // 2 sinks
            .build();
    }
    
    /**
     * Configuración de alta carga: Máximo paralelismo
     */
    public static BenchmarkConfig highLoadGraph() {
        return BenchmarkConfig.builder()
            .numEvents(50000)
            .sourceParallelism(4)      // 4 sources
            .schedulerParallelism(8)   // 8 schedulers
            .filterParallelism(6)      // 6 filters
            .sinkParallelism(2)        // 2 sinks
            .build();
    }
    
    /**
     * Configuración personalizada
     */
    public static BenchmarkConfig customGraph(int sources, int schedulers, int filters, int sinks) {
        return BenchmarkConfig.builder()
            .numEvents(10000)
            .sourceParallelism(sources)
            .schedulerParallelism(schedulers)
            .filterParallelism(filters)
            .sinkParallelism(sinks)
            .build();
    }
}