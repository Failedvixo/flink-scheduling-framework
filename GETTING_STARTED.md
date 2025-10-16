# 🚀 Guía de Inicio Rápido

## Estructura del Proyecto

```
flink-scheduling-framework/
├── pom.xml
├── src/main/java/com/scheduling/framework/
│   ├── model/
│   │   └── Task.java
│   ├── scheduler/
│   │   ├── TaskScheduler.java
│   │   └── impl/
│   │       ├── FCFSScheduler.java
│   │       └── PriorityScheduler.java
│   ├── nexmark/
│   │   └── NexmarkAdapter.java
│   ├── operator/
│   │   └── SchedulingProcessFunction.java
│   ├── metrics/
│   │   ├── MetricsCollector.java
│   │   └── SchedulingMetrics.java
│   ├── config/
│   │   └── BenchmarkConfig.java
│   ├── SchedulingBenchmarkJob.java
│   └── SchedulerComparisonJob.java
├── run-benchmark.sh
└── README.md
```

## 📦 Instalación

### Paso 1: Crear estructura de directorios

```bash
mkdir -p flink-scheduling-framework/src/main/java/com/scheduling/framework/{model,scheduler/impl,nexmark,operator,metrics,config}
cd flink-scheduling-framework
```

### Paso 2: Copiar archivos

Copia todos los archivos Java en sus respectivos directorios según la estructura mostrada arriba.

### Paso 3: Compilar

```bash
mvn clean install
```

## 🎯 Ejecutar Ejemplos

### Ejemplo 1: Benchmark Simple con FCFS

```bash
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SchedulingBenchmarkJob"
```

**Salida esperada:**
```
INFO  FCFSScheduler - FCFS Scheduler initialized with capacity: 4
INFO  SchedulingBenchmarkJob - Starting benchmark with First Come First Serve (FCFS) scheduler
INFO  SchedulingBenchmarkJob - Number of events: 10000
...
INFO  MetricsCollector - === Metrics for First Come First Serve (FCFS) ===
INFO  MetricsCollector - Total Tasks Completed: 10000
INFO  MetricsCollector - Average Waiting Time: 15.34 ms
INFO  MetricsCollector - Throughput: 345.67 tasks/sec
```

### Ejemplo 2: Comparación de Schedulers

```bash
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SchedulerComparisonJob"
```

**Salida esperada:**
```
========================================
       SCHEDULER COMPARISON RESULTS     
========================================

Scheduler                 | Completed  | Avg Wait(ms) | Avg Total(ms)   | Throughput
--------------------------|------------|--------------|-----------------|-------------
First Come First Serve    |       5000 |        15.34 |           25.36 |       197.24
Priority Scheduler        |       5000 |        12.87 |           22.89 |       218.43

========================================
Best Throughput: Priority Scheduler
Best Latency: Priority Scheduler
========================================
```

## 🔧 Configuración Personalizada

### Modificar parámetros del benchmark

Edita `SchedulingBenchmarkJob.java`:

```java
// Cambiar número de eventos
final int numEvents = 50000;

// Cambiar capacidad del scheduler
scheduler.initialize(8);

// Cambiar delay de procesamiento
final int processingDelayMs = 5;
```

### Usar BenchmarkConfig

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .numEvents(20000)
    .schedulerCapacity(6)
    .processingDelayMs(15)
    .eventDistribution(BenchmarkConfig.EventDistribution.BID_HEAVY)
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

### Métricas Clave

1. **Throughput**: Tareas/segundo - Mayor es mejor
2. **Average Waiting Time**: Tiempo en cola - Menor es mejor
3. **Average Total Time**: Tiempo total - Menor es mejor
4. **Completion Rate**: % de tareas completadas - 100% es óptimo

### Interpretación

- **FCFS**: Simple, justo, pero no optimiza prioridades
- **Priority**: Mejor para cargas con diferentes importancias
- **Round Robin**: Equitativo entre tipos de eventos

## 🧪 Escenarios de Testing

### Escenario 1: Alta Carga

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .numEvents(100000)
    .schedulerCapacity(2)  // Baja capacidad
    .processingDelayMs(20) // Alto delay
    .build();
```

### Escenario 2: Baja Latencia

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .numEvents(10000)
    .schedulerCapacity(16)  // Alta capacidad
    .processingDelayMs(1)   // Bajo delay
    .build();
```

### Escenario 3: Eventos en Ráfagas

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .numEvents(50000)
    .eventDistribution(EventDistribution.BURSTY)
    .build();
```

## 🐛 Troubleshooting

### Error: OutOfMemoryError

```bash
# Aumentar memoria de la JVM
export MAVEN_OPTS="-Xmx2g"
mvn exec:java -Dexec.mainClass="..."
```

### Error: Compilation failed

```bash
# Verificar versión de Java
java -version  # Debe ser 11+

# Limpiar y recompilar
mvn clean install -U
```

### Error: No se encuentran clases de Flink

```bash
# Verificar dependencias
mvn dependency:tree

# Reinstalar dependencias
mvn dependency:purge-local-repository
```

## 📈 Optimizaciones

### 1. Ajustar Paralelismo

```java
env.setParallelism(4);  // Usar múltiples slots
```

### 2. Configurar Checkpointing

```java
env.enableCheckpointing(5000);
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
```

### 3. Optimizar Capacidad del Scheduler

```java
// Experimentar con diferentes capacidades
for (int capacity = 2; capacity <= 16; capacity *= 2) {
    scheduler.initialize(capacity);
    // Ejecutar benchmark...
}
```

## 🔍 Debugging

### Activar logs detallados

Crea `src/main/resources/log4j2.properties`:

```properties
rootLogger.level = INFO
rootLogger.appenderRef.console.ref = ConsoleAppender

appender.console.type = Console
appender.console.name = ConsoleAppender
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = %d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n

# Logs específicos del framework
logger.scheduler.name = com.scheduling.framework.scheduler
logger.scheduler.level = DEBUG

logger.metrics.name = com.scheduling.framework.metrics
logger.metrics.level = INFO
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

- **Shortest Job First (SJF)**
- **Earliest Deadline First (EDF)**
- **Weighted Fair Queueing (WFQ)**
- **Multi-Level Feedback Queue (MLFQ)**

### 2. Agregar Métricas Avanzadas

- Percentiles (P50, P95, P99)
- Varianza y desviación estándar
- Histogramas de distribución

### 3. Integrar con Nexmark Real

```java
// Usar fuentes reales de Nexmark
import org.apache.beam.sdk.nexmark.NexmarkConfiguration;
import org.apache.beam.sdk.nexmark.sources.GeneratorConfig;
```

### 4. Exportar Métricas

```java
// A CSV
metricsCollector.exportToCSV("results.csv");

// A JSON
metricsCollector.exportToJSON("results.json");
```

## 📚 Recursos Adicionales

- [Apache Flink Documentation](https://flink.apache.org/docs/stable/)
- [Nexmark Benchmark Guide](https://beam.apache.org/documentation/sdks/java/testing/nexmark/)
- [Stream Processing Patterns](https://www.oreilly.com/library/view/streaming-systems/9781491983867/)

## 💡 Tips

1. **Empezar simple**: Usa FCFS primero para entender el framework
2. **Medir siempre**: Ejecuta benchmarks antes y después de cambios
3. **Comparar**: Prueba múltiples schedulers con las mismas condiciones
4. **Documentar**: Registra configuraciones y resultados
5. **Iterar**: Mejora gradualmente basándote en métricas

## 🤝 Contribuir

Para agregar nuevos schedulers o mejoras:

1. Crea una rama: `git checkout -b feature/nuevo-scheduler`
2. Implementa el scheduler siguiendo la interfaz `TaskScheduler`
3. Agrega tests y documentación
4. Crea un pull request

---

¡Listo para experimentar con scheduling en Flink! 🚀