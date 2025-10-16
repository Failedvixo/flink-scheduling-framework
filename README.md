# Flink Scheduling Framework

Framework para probar y comparar algoritmos de scheduling en Apache Flink usando el benchmark Nexmark.

## 📋 Descripción

Este proyecto proporciona una infraestructura extensible para experimentar con diferentes algoritmos de scheduling en stream processing. Utiliza Apache Flink como motor de procesamiento y Nexmark como fuente de eventos de benchmark.

## 🏗️ Arquitectura

```
┌─────────────────┐
│  Nexmark Source │
│   (Simulado)    │
└────────┬────────┘
         │
         │ Task Stream
         ▼
┌─────────────────────────┐
│  Scheduling Operator    │
│  ┌──────────────────┐   │
│  │  Task Scheduler  │   │
│  │  - FCFS          │   │
│  │  - Priority      │   │
│  │  - Custom...     │   │
│  └──────────────────┘   │
└────────┬────────────────┘
         │
         │ Processed Tasks
         ▼
┌─────────────────┐
│ Metrics & Sink  │
└─────────────────┘
```

## 📁 Estructura del Proyecto

```
src/main/java/com/scheduling/framework/
├── model/
│   └── Task.java                    # Modelo de tarea
├── scheduler/
│   ├── TaskScheduler.java           # Interfaz del scheduler
│   └── impl/
│       └── FCFSScheduler.java       # Implementación FCFS
├── nexmark/
│   └── NexmarkAdapter.java          # Adaptador de eventos Nexmark
├── operator/
│   └── SchedulingProcessFunction.java  # Operador Flink
├── metrics/
│   ├── MetricsCollector.java        # Recolector de métricas
│   └── SchedulingMetrics.java       # Clase de métricas
├── config/
│   └── BenchmarkConfig.java         # Configuración del benchmark
└── SchedulingBenchmarkJob.java      # Job principal
```

## 🚀 Inicio Rápido

### Requisitos

- Java 11+
- Maven 3.6+
- Apache Flink 1.18.0

### Compilación

```bash
mvn clean package
```

### Ejecución

```bash
# Ejecución local
mvn exec:java -Dexec.mainClass="com.scheduling.framework.SchedulingBenchmarkJob"

# O con el JAR compilado
java -jar target/flink-scheduling-framework-1.0-SNAPSHOT.jar
```

## 🔧 Configuración

Puedes configurar el benchmark modificando los parámetros en `SchedulingBenchmarkJob.java`:

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .numEvents(10000)           // Número de eventos a procesar
    .schedulerCapacity(4)       // Slots de procesamiento disponibles
    .processingDelayMs(10)      // Delay simulado por tarea (ms)
    .eventDistribution(UNIFORM) // Distribución de eventos
    .build();
```

## 📊 Métricas Recolectadas

El framework recolecta las siguientes métricas:

- **Completed Tasks**: Tareas completadas exitosamente
- **Submitted Tasks**: Total de tareas enviadas
- **Completion Rate**: Porcentaje de completitud
- **Average Waiting Time**: Tiempo promedio en cola
- **Max/Min Waiting Time**: Tiempos de espera extremos
- **Average Execution Time**: Tiempo promedio de ejecución
- **Average Total Time**: Tiempo total promedio (espera + ejecución)
- **Throughput**: Tareas procesadas por segundo

## 🎯 Implementar un Nuevo Scheduler

Para agregar un nuevo algoritmo de scheduling:

1. Crear una clase que implemente `TaskScheduler`:

```java
public class MyScheduler implements TaskScheduler {
    
    @Override
    public void initialize(int capacity) {
        // Inicialización
    }
    
    @Override
    public void submitTask(Task task) {
        // Lógica de admisión
    }
    
    @Override
    public Task getNextTask() {
        // Lógica de selección
        return nextTask;
    }
    
    // ... implementar otros métodos
}
```

2. Usar el scheduler en el job principal:

```java
TaskScheduler scheduler = new MyScheduler();
// Resto de la configuración...
```

## 📈 Ejemplos de Schedulers a Implementar

- **Priority Scheduler**: Basado en prioridades de eventos
- **Shortest Job First (SJF)**: Basado en estimación de tiempo
- **Round Robin**: Rotación equitativa
- **Weighted Fair Queueing**: Colas con pesos
- **Earliest Deadline First (EDF)**: Basado en deadlines

## 🔍 Casos de Uso

1. **Comparación de Algoritmos**: Evaluar diferentes estrategias de scheduling
2. **Optimización de Recursos**: Encontrar la mejor capacidad de procesamiento
3. **Análisis de Latencia**: Medir impacto en tiempos de respuesta
4. **Testing de Carga**: Probar bajo diferentes cargas de trabajo

## 📝 Ejemplo de Salida

```
INFO  FCFSScheduler - FCFS Scheduler initialized with capacity: 4
INFO  SchedulingBenchmarkJob - Starting benchmark with First Come First Serve (FCFS) scheduler
INFO  SchedulingBenchmarkJob - Number of events: 10000
...
INFO  MetricsCollector - === Metrics for First Come First Serve (FCFS) ===
INFO  MetricsCollector - Total Tasks Completed: 10000
INFO  MetricsCollector - Completion Rate: 100.00%
INFO  MetricsCollector - Average Waiting Time: 15.34 ms
INFO  MetricsCollector - Average Execution Time: 10.02 ms
INFO  MetricsCollector - Throughput: 345.67 tasks/sec
```

## 🤝 Contribuir

Para agregar nuevas funcionalidades:

1. Implementa la interfaz correspondiente
2. Agrega tests unitarios
3. Actualiza la documentación
4. Crea un pull request

## 📄 Licencia

Este proyecto es de código abierto para fines educativos y de investigación.

## 🔗 Referencias

- [Apache Flink Documentation](https://flink.apache.org/)
- [Nexmark Benchmark](https://beam.apache.org/documentation/sdks/java/testing/nexmark/)
- [Stream Processing Scheduling](https://dl.acm.org/)