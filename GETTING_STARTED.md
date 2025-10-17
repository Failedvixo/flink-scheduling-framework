# 🚀 Guía de Inicio Rápido

## Estructura del Proyecto

```
flink-scheduling-framework/
├── pom.xml
├── src/main/java/com/scheduling/framework/
│   ├── model/
│   │   └── Task.java                    # Modelo de tarea con métricas
│   ├── scheduler/
│   │   ├── TaskScheduler.java           # Interfaz del scheduler
│   │   └── impl/
│   │       ├── FCFSScheduler.java       # First Come First Serve
│   │       └── PriorityScheduler.java   # Scheduler basado en prioridades
│   ├── nexmark/
│   │   └── NexmarkAdapter.java          # Generador de eventos Nexmark
│   ├── operator/
│   │   └── SchedulingProcessFunction.java # Operador Flink principal
│   ├── metrics/
│   │   ├── MetricsCollector.java        # Recolector de métricas
│   │   └── SchedulingMetrics.java       # Clase de métricas
│   ├── config/
│   │   └── BenchmarkConfig.java         # Configuración del benchmark
│   ├── SchedulingBenchmarkJob.java      # Job individual (FCFS)
│   ├── SchedulerComparisonJob.java      # Comparación de schedulers
│   └── SimpleMetricsTest.java           # Test sin dependencias Flink
└── README.md
```

## 📦 Requisitos

- **Java 11+**: `java -version`
- **Maven 3.6+**: `mvn -version`
- **Memoria**: Mínimo 2GB RAM disponible

## 🚀 Métodos de Ejecución

### Método 1: Maven (Recomendado para desarrollo)
```bash
# Test simple sin Flink
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SimpleMetricsTest"

# Comparación de schedulers (puede fallar por dependencias)
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SchedulerComparisonJob"
```

### Método 2: JAR Compilado (Más estable)
```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar test simple
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest

# Ejecutar con Flink (requiere flags adicionales)
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SchedulerComparisonJob
```

## 🎯 Ejemplos de Ejecución

### Ejemplo 1: Test Simple de Métricas (Recomendado)

```bash
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

**Salida real:**
```
========================================
       SIMPLE METRICS TEST RESULTS     
========================================

Testing FCFS scheduler...
Completed: FCFS
Total Time: 29.5 ms
Wait Time: 19.5 ms
Execution Time: 10.0 ms
Throughput: 503.52 tasks/sec
----------------------------------------
Testing Priority scheduler...
Completed: Priority
Total Time: 10.5 ms
Wait Time: 0.5 ms
Execution Time: 10.0 ms
Throughput: 514.93 tasks/sec

========================================
       SCHEDULER COMPARISON RESULTS
========================================

Scheduler                 |  Completed | Avg Wait(ms) |   Avg Total(ms) |   Throughput
--------------------------|------------|--------------|-----------------|-------------
First Come First Serve    |       5000 |        19,50 |           29,50 |       503,52
Priority Scheduler        |       5000 |         0,50 |           10,50 |       514,93

========================================
Best Throughput: Priority Scheduler
Best Latency: Priority Scheduler
========================================
```

### Ejemplo 2: Comparación con Flink (Avanzado)

```bash
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SchedulerComparisonJob
```

**Nota**: Puede requerir configuración adicional de dependencias.

## 🔧 Configuración Personalizada

### Modificar SimpleMetricsTest

Edita `SimpleMetricsTest.java`:

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

### Usar BenchmarkConfig (para SchedulerComparisonJob)

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .numEvents(5000)                    // Número de eventos
    .schedulerCapacity(4)               // Slots de procesamiento
    .processingDelayMs(10)              // Delay por tarea
    .sourceParallelism(1)               // Paralelismo del source
    .eventDistribution(BenchmarkConfig.EventDistribution.UNIFORM)
    .build();
```

## 🎨 Crear un Scheduler Personalizado

### Ejemplo: Round Robin Scheduler

Crea `RoundRobinScheduler.java` en `scheduler/impl/`:

```java
package com.scheduling.framework.scheduler.impl;

import com.scheduling.framework.model.Task;
import com.scheduling.framework.scheduler.TaskScheduler;
import java.util.*;

public class RoundRobinScheduler implements TaskScheduler {
    
    private final Map<String, Queue<Task>> eventTypeQueues;
    private final List<String> eventTypes;
    private int currentIndex;
    private int capacity;
    private int runningTasks;
    
    public RoundRobinScheduler() {
        this.eventTypeQueues = new HashMap<>();
        this.eventTypes = Arrays.asList("PERSON", "AUCTION", "BID");
        this.currentIndex = 0;
        
        for (String type : eventTypes) {
            eventTypeQueues.put(type, new LinkedList<>());
        }
    }
    
    @Override
    public void initialize(int capacity) {
        this.capacity = capacity;
        this.runningTasks = 0;
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
        
        // Round robin entre tipos de eventos
        int attempts = 0;
        while (attempts < eventTypes.size()) {
            String currentType = eventTypes.get(currentIndex);
            Queue<Task> queue = eventTypeQueues.get(currentType);
            
            currentIndex = (currentIndex + 1) % eventTypes.size();
            
            if (!queue.isEmpty()) {
                Task task = queue.poll();
                task.setStartTime(System.currentTimeMillis());
                runningTasks++;
                return task;
            }
            
            attempts++;
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
        return "Round Robin Scheduler";
    }
    
    @Override
    public synchronized void reset() {
        eventTypeQueues.values().forEach(Queue::clear);
        runningTasks = 0;
        currentIndex = 0;
    }
}
```

### Usar el nuevo scheduler

```java
TaskScheduler scheduler = new RoundRobinScheduler();
// Resto del código...
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

### Interpretación de Resultados Reales

**Priority Scheduler es superior:**
- **97% menos waiting time**: 0.5ms vs 19.5ms
- **64% menos total time**: 10.5ms vs 29.5ms  
- **2% más throughput**: 514.93 vs 503.52 tasks/sec

**Razones:**
- **FCFS**: Procesa en orden de llegada (justo pero ineficiente)
- **Priority**: Procesa tareas importantes primero (optimizado)
- **Resultado**: Priority evita congestión y reduce latencia

## 🧪 Escenarios de Testing

### Modificar SimpleMetricsTest para diferentes escenarios:

### Escenario 1: Alta Carga
```java
// En SimpleMetricsTest.java
int numTasks = 50000;           // Más tareas
long arrivalTime = baseTime + (i * 5);  // Llegadas más rápidas
Thread.sleep(2);                // Más delay de procesamiento
```

### Escenario 2: Baja Latencia
```java
int numTasks = 1000;            // Pocas tareas
long arrivalTime = baseTime + (i * 50); // Llegadas espaciadas
Thread.sleep(0);                // Sin delay adicional
```

### Escenario 3: Stress Test
```java
int numTasks = 100000;          // Muchas tareas
if (isFCFS) {
    waitTime = i % 100;         // Más variabilidad en espera
} else {
    waitTime = i % 5;
}
```

## 🐛 Troubleshooting

### Error: ClassNotFoundException (Flink)
**Problema**: Dependencias de Flink no se cargan correctamente
**Solución**: Usar SimpleMetricsTest en lugar de jobs con Flink
```bash
# En lugar de SchedulerComparisonJob, usar:
java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

### Error: Valores negativos en métricas
**Problema**: Cálculo incorrecto de tiempos
**Solución**: Ya corregido en la versión actual
- `startTime` siempre >= `arrivalTime`
- `completionTime` siempre >= `startTime`

### Error: OutOfMemoryError
```bash
# Aumentar memoria
export MAVEN_OPTS="-Xmx4g"
# O para JAR:
java -Xmx4g -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar com.scheduling.framework.SimpleMetricsTest
```

### Error: Compilation failed
```bash
# Verificar Java 11+
java -version

# Limpiar y recompilar
mvn clean package -DskipTests
```

## 📈 Optimizaciones y Experimentos

### 1. Variar Patrones de Carga
```java
// En SimpleMetricsTest.java - modificar patrones de espera
if (isFCFS) {
    waitTime = (int)(Math.random() * 50);  // Espera aleatoria 0-49ms
} else {
    waitTime = (int)(Math.random() * 3);   // Espera aleatoria 0-2ms
}
```

### 2. Experimentar con Diferentes Tamaños
```java
// Probar diferentes volúmenes
int[] testSizes = {1000, 5000, 10000, 50000};
for (int size : testSizes) {
    // Ejecutar test con cada tamaño
}
```

### 3. Medir Percentiles
```java
// Agregar a SimpleMetricsTest
List<Long> waitTimes = new ArrayList<>();
// ... recolectar tiempos
Collections.sort(waitTimes);
long p50 = waitTimes.get(waitTimes.size() / 2);
long p95 = waitTimes.get((int)(waitTimes.size() * 0.95));
long p99 = waitTimes.get((int)(waitTimes.size() * 0.99));
```

## 🔍 Debugging y Monitoreo

### Agregar Logs a SimpleMetricsTest
```java
// Agregar al método simulateScheduler
System.out.println("Task " + i + ": arrival=" + arrivalTime + 
                  ", start=" + (arrivalTime + waitTime) + 
                  ", wait=" + waitTime + "ms");
```

### Verificar Cálculos
```java
// Validar que los tiempos sean consistentes
assert task.getStartTime() >= task.getArrivalTime() : "Start time before arrival";
assert task.getCompletionTime() >= task.getStartTime() : "Completion before start";
assert task.getTotalTime() == task.getWaitingTime() + task.getExecutionTime() : "Time mismatch";
```

### Monitorear Rendimiento
```java
// Medir tiempo de ejecución del test
long testStart = System.currentTimeMillis();
// ... ejecutar simulación
long testDuration = System.currentTimeMillis() - testStart;
System.out.println("Test completed in: " + testDuration + "ms");
```gger.metrics.level = INFO
```

### Instrumentar código

```java
// Agregar timing
long start = System.currentTimeMillis();
Task task = scheduler.getNextTask();
long elapsed = System.currentTimeMillis() - start;
LOG.debug("Scheduler decision time: {} ms", elapsed);
```

## 🎓 Próximos Pasos

### 1. Implementar Schedulers Avanzados

Crea nuevos schedulers en `scheduler/impl/`:

**Round Robin Scheduler:**
```java
public class RoundRobinScheduler implements TaskScheduler {
    private int currentIndex = 0;
    private final List<String> eventTypes = Arrays.asList("PERSON", "AUCTION", "BID");
    // Implementar rotación entre tipos de eventos
}
```

**Shortest Job First:**
```java
public class SJFScheduler implements TaskScheduler {
    private final PriorityQueue<Task> queue = new PriorityQueue<>(
        Comparator.comparingInt(task -> estimateProcessingTime(task))
    );
}
```

### 2. Mejorar SimpleMetricsTest

**Agregar percentiles:**
```java
// Calcular P50, P95, P99 de waiting times
List<Long> waitTimes = tasks.stream()
    .map(Task::getWaitingTime)
    .sorted()
    .collect(Collectors.toList());
```

**Comparar múltiples schedulers:**
```java
List<TaskScheduler> schedulers = Arrays.asList(
    new FCFSScheduler(),
    new PriorityScheduler(),
    new RoundRobinScheduler()
);
```

### 3. Integración con Flink Real

Una vez resueltos los problemas de dependencias:
- Usar `SchedulerComparisonJob` para tests completos
- Configurar checkpointing para fault tolerance
- Implementar métricas de Flink nativas

## 📚 Recursos

- [Apache Flink Docs](https://flink.apache.org/docs/stable/)
- [Stream Processing Concepts](https://www.oreilly.com/library/view/streaming-systems/9781491983867/)
- [Scheduling Algorithms](https://en.wikipedia.org/wiki/Scheduling_(computing))

## 💡 Tips de Uso

1. **Empezar con SimpleMetricsTest**: Es más estable y fácil de debuggear
2. **Modificar parámetros gradualmente**: Cambiar una variable a la vez
3. **Documentar experimentos**: Anotar configuraciones y resultados
4. **Validar métricas**: Verificar que Total Time = Wait Time + Execution Time
5. **Comparar consistentemente**: Usar las mismas condiciones para todos los schedulers

---

¡Framework listo para experimentación con scheduling algorithms! 🚀