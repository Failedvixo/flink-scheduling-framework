package com.scheduling.framework.scheduler;

import com.scheduling.framework.model.Task;
import java.util.List;

/**
 * Interfaz base para algoritmos de scheduling
 */
public interface TaskScheduler {
    
    /**
     * Inicializa el scheduler con la capacidad de recursos
     * @param capacity Número de slots disponibles
     */
    void initialize(int capacity);
    
    /**
     * Añade una tarea a la cola del scheduler
     * @param task Tarea a programar
     */
    void submitTask(Task task);
    
    /**
     * Obtiene la siguiente tarea a ejecutar según el algoritmo
     * @return La siguiente tarea o null si no hay tareas
     */
    Task getNextTask();
    
    /**
     * Marca una tarea como completada
     * @param taskId ID de la tarea completada
     */
    void completeTask(String taskId);
    
    /**
     * Obtiene el número de tareas pendientes
     * @return Cantidad de tareas en cola
     */
    int getPendingTasksCount();
    
    /**
     * Obtiene todas las tareas pendientes
     * @return Lista de tareas pendientes
     */
    List<Task> getPendingTasks();
    
    /**
     * Obtiene el nombre del algoritmo
     * @return Nombre del scheduler
     */
    String getAlgorithmName();
    
    /**
     * Reinicia el estado del scheduler
     */
    void reset();
}