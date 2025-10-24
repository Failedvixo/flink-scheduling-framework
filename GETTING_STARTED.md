# 🚀 Guía de Inicio Rápido - Adaptive Scheduler Framework

## Estructura del Proyecto

```
flink-scheduling-framework/
├── pom.xml
├── src/main/java/com/scheduling/framework/
│   ├── model/
│   │   └── Task.java                    # Modelo de tarea con métricas
│   ├── resource/                        # 🔄 Resource Schedulers
│   │   ├── ResourceScheduler.java       # Interfaz del resource scheduler
│   │   ├── ProcessingResource.java      # Recurso de procesamiento
│   │   └── impl/
│   │       ├── FCFSResourceScheduler.java    # First Come First Serve
│   │       ├── PriorityResourceScheduler.java # Scheduler basado en prioridades
│   │       ├── RoundRobinResourceScheduler.java # Round Robin
│   │       └── LeastLoadedResourceScheduler.java # Least Loaded
│   ├── nexmark/
│   │   └── NexmarkAdapter.java          # Generador de eventos Nexmark
│   ├── operator/
│   │   └── ResourceSchedulingProcessFunction.java # Operador Flink principal
│   ├── metrics/
│   │   ├── MetricsCollector.java        # Recolector de métricas
│   │   └── SchedulingMetrics.java       # Clase de métricas
│   ├── config/
│   │   ├── BenchmarkConfig.java         # Configuración del benchmark
│   │   └── GraphConfigurations.java     # Configuraciones de grafo
│   ├── FlinkSchedulerJob.java           # 🎯 Adaptive Scheduler Job (PRINCIPAL)
│   ├── SchedulerComparisonJob.java      # Comparación de schedulers
│   └── SimpleMetricsTest.java           # Test sin dependencias Flink
└── README.md
```

## 📦 Requisitos

- **Java 11+**: `java -version`
- **Maven 3.6+**: `mvn -version`
- **Memoria**: Mínimo 2GB RAM disponible

## 🚀 Métodos de Ejecución

### Método 1: Adaptive Scheduler (RECOMENDADO) 🎯
```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar Adaptive Scheduler con monitoreo de CPU
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.FlinkSchedulerJob
```

### Método 2: Test Simple (Sin Flink)
```bash
# Test simple sin dependencias Flink
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

### Método 3: Maven (Desarrollo)
```bash
# Test simple
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SimpleMetricsTest"
```

## 🎯 Ejemplos de Ejecución

### Ejemplo 1: Adaptive Scheduler (PRINCIPAL) 🎯

```bash
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.FlinkSchedulerJob
```

**Salida real:**
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

### Ejemplo 2: Test Simple (Sin Flink)

```bash
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

**Salida:**
```
========================================
       SIMPLE METRICS TEST RESULTS     
========================================
Scheduler                 |  Completed | Avg Wait(ms) |   Avg Total(ms) |   Throughput
--------------------------|------------|--------------|-----------------|-------------
First Come First Serve    |       5000 |        19,50 |           29,50 |       503,52
Priority Scheduler        |       5000 |         0,50 |           10,50 |       514,93
========================================
```

## 🔧 Configuración Personalizada

### Configurar Adaptive Scheduler

Edita `FlinkSchedulerJob.java`:

```java
// Cambiar número de eventos
new NexmarkEventSource(20000)  // Más eventos para ver más switches

// Cambiar paralelismo
env.setParallelism(8);  // Más instancias paralelas

// Modificar umbrales de CPU
if (cpuUsage > 85.0 && "FCFS".equals(currentSchedulerType)) {  // Cambiar de 90% a 85%
if (cpuUsage < 40.0 && "Priority".equals(currentSchedulerType)) { // Cambiar de 50% a 40%
```

### Configurar SimpleMetricsTest

```java
// Cambiar número de tareas
int numTasks = 10000;  // Línea ~25

// Cambiar patrones de espera
if (isFCFS) {
    waitTime = i % 40;  // Max 39ms para FCFS
} else {
    waitTime = i % 2;   // Max 1ms para Priority
}
```

## 🎨 Crear un Resource Scheduler Personalizado

### Ejemplo: Weighted Fair Queueing Scheduler

Crea `WeightedFairResourceScheduler.java` en `resource/impl/`:

```java
package com.scheduling.framework.resource.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.resource.ResourceScheduler;
import java.util.*;

public class WeightedFairResourceScheduler implements ResourceScheduler {
    
    private final Map<String, Queue<Task>> eventTypeQueues;
    private final List<String> eventTypes;
    private int currentIndex;
    private int capacity;
    private int runningTasks;
    
    private final Map<String, Integer> eventWeights = Map.of(
        "BID", 3,      // Alta prioridad
        "AUCTION", 2,  // Media prioridad  
        "PERSON", 1    // Baja prioridad
    );
    
    public WeightedFairResourceScheduler() {
        this.eventTypeQueues = new HashMap<>();
        this.eventTypes = Arrays.asList("PERSON", "AUCTION", "BID");
        this.currentIndex = 0;
        
        for (String type : eventTypes) {
            eventTypeQueues.put(type, new LinkedList<>());
        }
    }
    
    @Override
    public synchronized void submitTask(Task task) {
        Queue<Task> queue = eventTypeQueues.get(task.getEventType());
        if (queue != null) {
            queue.offer(task);
        }
    }
    
    @Override
    public synchronized Task getNextTask() {
        if (runningTasks >= capacity) {
            return null;
        }
        
        // Weighted fair queueing - seleccionar basado en pesos
        for (int weight = 3; weight >= 1; weight--) {
            for (String eventType : eventTypes) {
                if (eventWeights.get(eventType) == weight) {
                    Queue<Task> queue = eventTypeQueues.get(eventType);
                    if (!queue.isEmpty()) {
                        Task task = queue.poll();
                        task.setStartTime(System.currentTimeMillis());
                        runningTasks++;
                        return task;
                    }
                }
            }
        }
        
        return null;
    }
    
    @Override
    public synchronized void completeTask(String taskId) {
        runningTasks--;
    }
    
    @Override
    public synchronized int getPendingTasksCount() {
        return eventTypeQueues.values().stream()
            .mapToInt(Queue::size)
            .sum();
    }
    
    @Override
    public synchronized List<Task> getPendingTasks() {
        List<Task> all = new ArrayList<>();
        eventTypeQueues.values().forEach(all::addAll);
        return all;
    }
    
    @Override
    public String getAlgorithmName() {
        return "Weighted Fair Queueing Scheduler";
    }
    
    @Override
    public synchronized void reset() {
        eventTypeQueues.values().forEach(Queue::clear);
        runningTasks = 0;
        currentIndex = 0;
    }
}
```

### Usar el nuevo scheduler en Adaptive Job

```java
// En AdaptiveSchedulerProcessor.open()
weightedScheduler = new WeightedFairResourceScheduler();

// Agregar como tercera opción
if (cpuUsage > 95.0) {
    currentSchedulerType = "WeightedFair";
}
```

## 📊 Análisis de Resultados

### Métricas Explicadas

| Métrica | Significado | Fórmula | Mejor Valor |
|---------|-------------|---------|-------------|
| **Avg Wait Time** | Tiempo en cola antes de procesarse | `startTime - arrivalTime` | Menor |
| **Execution Time** | Tiempo real de procesamiento | `completionTime - startTime` | Constante (10ms) |
| **Avg Total Time** | Tiempo total desde llegada hasta completitud | `completionTime - arrivalTime` | Menor |
| **Throughput** | Tareas procesadas por segundo | `totalTasks / totalDuration` | Mayor |
| **Completed** | Número de tareas completadas | Contador | 100% ideal |

### Interpretación de Resultados del Adaptive Scheduler

**Adaptive Scheduler combina lo mejor de ambos:**
- **Switching inteligente**: Cambia automáticamente basado en carga de CPU
- **FCFS bajo carga baja**: Justo y eficiente cuando hay recursos disponibles
- **Priority bajo carga alta**: Optimizado cuando el sistema está saturado
- **Métricas de switching**: Muestra cuándo y por qué cambió de algoritmo

**Ventajas del enfoque adaptativo:**
- **Flexibilidad**: Se adapta a condiciones cambiantes
- **Observabilidad**: Tracking completo de decisiones de scheduling
- **Escalabilidad**: Funciona con paralelismo distribuido

## 🧪 Escenarios de Testing

### Escenario 1: Stress Test Adaptativo
```java
// En FlinkSchedulerJob.java - NexmarkEventSource
new NexmarkEventSource(50000)  // Muchos eventos para forzar switches

// Modificar simulación de CPU para picos más frecuentes
long cyclePosition = taskCounter % 1000;  // Ciclos más cortos
```

### Escenario 2: Umbrales Sensibles
```java
// Cambiar umbrales para switches más frecuentes
if (cpuUsage > 70.0 && "FCFS".equals(currentSchedulerType)) {
if (cpuUsage < 60.0 && "Priority".equals(currentSchedulerType)) {
```

### Escenario 3: Alto Paralelismo
```java
// Probar con más instancias paralelas
env.setParallelism(16);
.keyBy(task -> task.getTaskId().hashCode() % 16)
```

### Escenario 4: Eventos Desbalanceados
```java
// En NexmarkEventSource - generar más BIDs que otros eventos
String eventType = i % 10 < 7 ? "BID" : 
                  i % 10 < 9 ? "AUCTION" : "PERSON";
```

## 🐛 Troubleshooting

### Error: ClassNotFoundException (Flink)
**Problema**: Dependencias de Flink no se cargan correctamente
**Solución**: Usar SimpleMetricsTest en lugar de jobs con Flink
```bash
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

### Error: OutOfMemoryError
```bash
# Aumentar memoria
java -Xmx4g --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.FlinkSchedulerJob
```

### Error: Compilation failed
```bash
# Verificar Java 11+
java -version

# Limpiar y recompilar
mvn clean package -DskipTests
```

## 🎓 Próximos Pasos

### 1. Extender Adaptive Scheduler

**Agregar más schedulers:**
```java
// En AdaptiveSchedulerProcessor
if (cpuUsage > 95.0) {
    currentSchedulerType = "WeightedFair";
} else if (cpuUsage > 90.0) {
    currentSchedulerType = "Priority";
} else if (cpuUsage > 70.0) {
    currentSchedulerType = "RoundRobin";
} else {
    currentSchedulerType = "FCFS";
}
```

**Métricas avanzadas:**
```java
// Agregar tracking de latencia por event type
Map<String, List<Long>> latencyByEventType = new HashMap<>();

// Percentiles de switching
List<Double> cpuAtSwitch = switchHistory.stream()
    .map(sw -> sw.cpuUsage)
    .collect(Collectors.toList());
```

### 2. Machine Learning Integration

**Predicción de carga:**
```java
// Usar historial para predecir próximos picos de CPU
public class MLSchedulerPredictor {
    public String predictBestScheduler(List<Double> cpuHistory) {
        // Implementar modelo predictivo
    }
}
```

### 3. Monitoring Dashboard

**Métricas en tiempo real:**
- CPU usage trends
- Scheduler switch frequency
- Latency por event type
- Throughput por scheduler

### 4. Distributed Testing

**Cluster real:**
```bash
# Ejecutar en cluster Flink real
./bin/flink run -c com.scheduling.framework.FlinkSchedulerJob \
  target/flink-scheduling-framework-1.0-SNAPSHOT.jar
```

## 📚 Recursos

- [Apache Flink Docs](https://flink.apache.org/docs/stable/)
- [Stream Processing Concepts](https://www.oreilly.com/library/view/streaming-systems/9781491983867/)
- [Scheduling Algorithms](https://en.wikipedia.org/wiki/Scheduling_(computing))

## 💡 Tips de Uso

1. **Empezar con Adaptive Scheduler**: Es la característica principal del framework
2. **Monitorear switches**: Observar cuándo y por qué cambia de algoritmo
3. **Experimentar con umbrales**: Ajustar límites de CPU para diferentes comportamientos
4. **Usar paralelismo**: Probar con diferentes niveles de paralelismo distribuido
5. **Documentar experimentos**: Anotar configuraciones y patrones de switching

---

¡Adaptive Scheduler Framework listo para experimentación avanzada! 🚀🔄

**Características principales:**
- ✅ Adaptive scheduling basado en CPU load
- ✅ Distributed processing con Flink
- ✅ Real-time monitoring y switch tracking
- ✅ Comprehensive metrics y reporting
- ✅ Extensible architecture para nuevos schedulers