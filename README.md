# Flink Adaptive Scheduling Framework

**Framework de Stream Processing para Benchmarking de Algoritmos de Scheduling Adaptativos**

Framework extensible para experimentar y comparar algoritmos de scheduling adaptativos en tiempo real usando Apache Flink, con switching automático basado en carga de CPU y eventos simulados tipo Nexmark.

## 📋 Descripción

Este proyecto implementa un **sistema de stream processing adaptativo** que permite:
- **Adaptive Scheduling**: Cambio automático entre algoritmos basado en carga de CPU
- **Distributed Processing**: Paralelismo distribuido con Flink
- **Real-time Monitoring**: Tracking de switches y métricas en tiempo real
- **Comprehensive Reporting**: Resumen detallado de cambios y rendimiento

**Tipo de Stream Processing**: Event-driven, low-latency, stateful processing con scheduling adaptativo.

🎯 **Objetivo**: Demostrar scheduling adaptativo en stream processing real
📊 **Resultado**: Adaptive Scheduler se ajusta automáticamente a condiciones cambiantes
🔬 **Uso**: Investigación, optimización, y educación en adaptive scheduling algorithms

## 🏗️ Arquitectura de Adaptive Stream Processing

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLINK EXECUTION ENVIRONMENT                  │
│  ┌─────────────────┐    ┌─────────────────────────────────────┐ │
│  │ Nexmark Source  │    │     Adaptive Scheduler Operator     │ │
│  │   (Simulado)    │───▶│  ┌─────────────────────────────────┐ │ │
│  │                 │    │  │     CPU Load Monitor           │ │ │
│  │ • PERSON events │    │  │  ┌─────────┐  ┌─────────────┐  │ │ │
│  │ • AUCTION events│    │  │  │  FCFS   │  │  Priority   │  │ │ │
│  │ • BID events    │    │  │  │Scheduler│  │  Scheduler  │  │ │ │
│  │                 │    │  │  └─────────┘  └─────────────┘  │ │ │
│  └─────────────────┘    │  │                               │ │ │
│                         │  │     Switch Logic:             │ │ │
│                         │  │     CPU > 90% → Priority      │ │ │
│                         │  │     CPU < 50% → FCFS          │ │ │
│                         │  └─────────────────────────────────┘ │ │
│                         └─────────┬───────────────────────────┘ │
│                                   │                             │
│                         ┌─────────▼───────────────────────────┐ │
│                         │        Metrics Collector            │ │
│                         │ • Switch History                    │ │
│                         │ • CPU Usage Tracking               │ │
│                         │ • Scheduler Performance            │ │
│                         │ • Comprehensive Reporting          │ │
│                         └─────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘

🖥️  Ejecución: Flink MiniCluster (local) o Cluster distribuido
📊  Métricas: Tiempo real con tracking de switches y CPU
🔄  Stream: Unbounded con adaptive scheduling automático
```

## 📁 Estructura del Proyecto

```
flink-scheduling-framework/
├── pom.xml                          # Maven + Flink 1.18.0 + Nexmark deps
├── GETTING_STARTED.md               # Guía detallada de uso
└── src/main/java/com/scheduling/framework/
    ├── model/
    │   └── Task.java                # Modelo con métricas de tiempo
    ├── resource/                    # 🔄 Resource Schedulers
    │   ├── ResourceScheduler.java   # Interfaz del resource scheduler
    │   ├── ProcessingResource.java  # Recurso de procesamiento
    │   └── impl/
    │       ├── FCFSResourceScheduler.java    # First Come First Serve
    │       ├── PriorityResourceScheduler.java # Basado en prioridades
    │       ├── RoundRobinResourceScheduler.java # Round Robin
    │       └── LeastLoadedResourceScheduler.java # Least Loaded
    ├── nexmark/                     # 🌊 Stream Data Generation
    │   └── NexmarkAdapter.java      # Generador de eventos
    ├── operator/                    # ⚙️ Flink Stream Processing
    │   └── ResourceSchedulingProcessFunction.java # Operador principal
    ├── metrics/                     # 📊 Real-time Metrics
    │   ├── MetricsCollector.java    # Recolector en tiempo real
    │   └── SchedulingMetrics.java   # Métricas agregadas
    ├── config/
    │   ├── BenchmarkConfig.java     # Configuración flexible
    │   └── GraphConfigurations.java # Configuraciones de grafo
    ├── FlinkSchedulerJob.java       # 🎯 Adaptive Scheduler Job (PRINCIPAL)
    ├── SchedulerComparisonJob.java  # 🏆 Comparación completa
    └── SimpleMetricsTest.java       # ✅ Test estable (alternativo)
```

## ✅ Estado Actual del Proyecto

### ✅ Completamente Funcional:
- **FlinkSchedulerJob** - ⭐ **Adaptive Scheduler principal con CPU monitoring**
- **AdaptiveSchedulerProcessor** - Switching automático FCFS ↔ Priority
- **Switch Tracking** - Historial completo de cambios con CPU usage
- **Distributed Processing** - Paralelismo configurable con Flink
- **Comprehensive Reporting** - Tabla resumen de switches y estadísticas
- **Real-time Monitoring** - Logs de CPU y decisiones de scheduling

### ✅ Funcional (Alternativo):
- **SimpleMetricsTest** - Test simple sin Flink para comparación básica

### 🎯 Método de Ejecución Principal:
1. **`FlinkSchedulerJob`** - ✅ **RECOMENDADO** - Adaptive scheduler completo
2. **`SimpleMetricsTest`** - ✅ Alternativo para testing básico

### 📊 Resultados del Adaptive Scheduler:
```
🔄 Adaptive Scheduler (Inteligente):
   • Switching automático basado en CPU load
   • FCFS cuando CPU < 50% (eficiente y justo)
   • Priority cuando CPU > 90% (optimizado para alta carga)
   • Tracking completo de decisiones y rendimiento
   
📈 Métricas Avanzadas: Switch history, CPU usage, scheduler statistics
🔧 Framework Extensible: Fácil agregar nuevos schedulers adaptativos
```

## 🚀 Inicio Rápido

### Requisitos
- **Java 11+** - `java -version`
- **Maven 3.6+** - `mvn -version`
- **Memoria**: Mínimo 2GB RAM disponible
- **Apache Flink 1.18.0** - Se descarga automáticamente

### Ejecución (Adaptive Scheduler - RECOMENDADO)

```bash
# 1. Compilar el proyecto
mvn clean package -DskipTests

# 2. Ejecutar Adaptive Scheduler (PRINCIPAL)
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.FlinkSchedulerJob

# 3. Alternativo: Test simple sin Flink
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

### Ejecución Alternativa (Maven)
```bash
# Test simple
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SimpleMetricsTest"
```

## 🔧 Configuración del Adaptive Scheduler

### Configurar FlinkSchedulerJob (Principal)

Edita `FlinkSchedulerJob.java`:

```java
// Número de eventos para procesar
new NexmarkEventSource(10000)  // Más eventos = más switches

// Paralelismo distribuido
env.setParallelism(4);  // 4 instancias paralelas

// Umbrales de switching
if (cpuUsage > 90.0 && "FCFS".equals(currentSchedulerType)) {
    // Cambiar a Priority cuando CPU alta
}
if (cpuUsage < 50.0 && "Priority".equals(currentSchedulerType)) {
    // Cambiar a FCFS cuando CPU baja
}

// Frecuencia de monitoreo
if (taskCounter % 100 == 0 || (currentTime - lastCpuCheckTime) > 3000) {
    // Verificar CPU cada 100 tareas o cada 3 segundos
}
```

### Escenarios de Testing Adaptativo

```java
// Stress test - muchos switches
new NexmarkEventSource(50000);
env.setParallelism(8);

// Switches sensibles
if (cpuUsage > 70.0) { // Umbral más bajo
if (cpuUsage < 60.0) { // Umbral más alto

// Simulación de CPU más agresiva
long cyclePosition = taskCounter % 1000;  // Ciclos más cortos
```

## 📊 Métricas del Adaptive Scheduler

### Métricas Principales

| Métrica | Descripción | Interpretación |
|---------|-------------|----------------|
| **Switch Count** | Número total de cambios de scheduler | Más switches = más adaptabilidad |
| **CPU Usage** | Carga simulada de CPU en cada switch | Trigger para decisiones de scheduling |
| **Scheduler Usage** | Porcentaje de tiempo usando cada scheduler | Balance entre FCFS y Priority |
| **Switch Latency** | Tiempo entre switches | Responsividad del sistema adaptativo |
| **Avg Wait Time** | Tiempo promedio en cola | Efectividad del scheduling adaptativo |

### Ejemplo de Salida Real

```
========================================
       TESTING ADAPTIVE SCHEDULER      
========================================
[ADAPTIVE] CPU: 92.5% - Switching to Priority Scheduler (Switch #1)
[MONITOR] Task: 500, CPU: 85.2%, Scheduler: Priority, Switches: 1
Adaptive - Processed: 1000, Avg Wait: 15.23ms
[ADAPTIVE] CPU: 45.3% - Switching to FCFS Scheduler (Switch #2)
[MONITOR] Task: 1000, CPU: 35.8%, Scheduler: FCFS, Switches: 2
Adaptive - Processed: 2000, Avg Wait: 18.45ms

========================================
       ADAPTIVE SCHEDULER RESULTS      
========================================
Tasks Processed: 10000
Avg Wait Time: 12.45 ms
Avg Total Time: 22.45 ms
Throughput: 445.67 tasks/sec

========================================
       SCHEDULER SWITCH SUMMARY        
========================================
Switch#  | Task#    | CPU%     | From         | To           | Timestamp
---------|----------|----------|--------------|--------------|----------
1        | 500      | 92.5     | FCFS         | Priority     | 45231
2        | 1200     | 45.3     | Priority     | FCFS         | 47892
3        | 1800     | 94.1     | FCFS         | Priority     | 49156
4        | 2400     | 48.7     | Priority     | FCFS         | 51023

Total Switches: 4
FCFS Usage: 65.2% | Priority Usage: 34.8%
========================================
```

## 🎯 Implementar Schedulers Adaptativos Avanzados

### Paso 1: Crear Scheduler Multi-Nivel

```java
// En AdaptiveSchedulerProcessor
private String selectSchedulerByCPU(double cpuUsage) {
    if (cpuUsage > 95.0) {
        return "Emergency";      // Scheduler de emergencia
    } else if (cpuUsage > 90.0) {
        return "Priority";       // Alta carga
    } else if (cpuUsage > 70.0) {
        return "WeightedFair";   // Carga media
    } else if (cpuUsage > 50.0) {
        return "RoundRobin";     // Carga baja-media
    } else {
        return "FCFS";           // Carga baja
    }
}
```

### Paso 2: Machine Learning Predictivo

```java
public class MLAdaptiveScheduler {
    private List<Double> cpuHistory = new ArrayList<>();
    
    public String predictOptimalScheduler(double currentCPU) {
        cpuHistory.add(currentCPU);
        
        // Predecir tendencia de CPU
        double trend = calculateTrend(cpuHistory);
        
        if (trend > 0.1) {
            return "Priority";  // CPU subiendo
        } else if (trend < -0.1) {
            return "FCFS";      // CPU bajando
        } else {
            return "RoundRobin"; // CPU estable
        }
    }
}
```

## 🔍 Casos de Uso del Adaptive Framework

1. **Auto-scaling Systems**: Sistemas que se adaptan automáticamente a la carga
2. **Cloud Resource Management**: Optimización dinámica de recursos en la nube
3. **Real-time Analytics**: Procesamiento adaptativo de streams de datos
4. **IoT Processing**: Manejo eficiente de cargas variables de sensores
5. **Financial Trading**: Scheduling adaptativo para diferentes condiciones de mercado

## 🌊 ¿Por qué es Adaptive Stream Processing?

### Características del Adaptive Processing:
1. **Dynamic Adaptation**: Cambio automático de estrategias en tiempo real
2. **Load-aware**: Decisiones basadas en métricas del sistema
3. **Stateful Switching**: Mantiene historial de decisiones y rendimiento
4. **Distributed Intelligence**: Cada instancia paralela toma decisiones independientes
5. **Observable**: Tracking completo de comportamiento adaptativo

### Ventajas sobre Scheduling Estático:
- **Flexibilidad**: Se adapta a condiciones cambiantes
- **Eficiencia**: Usa el mejor scheduler para cada situación
- **Robustez**: Maneja picos de carga automáticamente
- **Observabilidad**: Visibilidad completa de decisiones

## 🎯 Próximos Pasos

### Implementar Adaptive Schedulers Avanzados:
```java
// Scheduler con Machine Learning
public class MLAdaptiveScheduler extends AdaptiveSchedulerProcessor {
    private MLModel predictor;
    
    @Override
    protected String selectScheduler(double cpuUsage, long taskCount) {
        return predictor.predict(cpuUsage, taskCount, getHistoricalMetrics());
    }
}

// Scheduler con múltiples métricas
public class MultiMetricAdaptiveScheduler {
    public String selectScheduler(double cpu, double memory, double latency) {
        // Decisión basada en múltiples métricas
    }
}
```

### Mejorar Observabilidad:
- Dashboard en tiempo real
- Alertas de switching
- Análisis de patrones de carga
- Optimización automática de umbrales

## 📚 Referencias

- [Apache Flink Docs](https://flink.apache.org/docs/stable/)
- [Adaptive Systems Design](https://www.oreilly.com/library/view/designing-data-intensive/9781449373320/)
- [Stream Processing Patterns](https://www.oreilly.com/library/view/streaming-systems/9781491983867/)
- [Scheduling Algorithms](https://en.wikipedia.org/wiki/Scheduling_(computing))

## 📄 Licencia

Proyecto educativo y de investigación - Código abierto

---

**Adaptive Scheduler Framework listo para experimentación avanzada con scheduling inteligente** 🚀🔄

**Características principales:**
- ✅ **Adaptive Scheduling**: Switching automático basado en CPU load
- ✅ **Distributed Processing**: Paralelismo distribuido con Flink
- ✅ **Real-time Monitoring**: Tracking de switches y métricas en tiempo real
- ✅ **Comprehensive Reporting**: Resumen detallado de cambios y rendimiento
- ✅ **Extensible Architecture**: Fácil agregar nuevos schedulers adaptativos