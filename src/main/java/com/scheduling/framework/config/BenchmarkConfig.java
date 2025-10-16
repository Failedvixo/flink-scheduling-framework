package com.scheduling.framework.config;

import java.io.Serializable;

/**
 * Configuración para el benchmark de scheduling
 */
public class BenchmarkConfig implements Serializable {
    
    private final int numEvents;
    private final int schedulerCapacity;
    private final int processingDelayMs;
    private final int sourceParallelism;
    private final int operatorParallelism;
    private final EventDistribution eventDistribution;
    private final boolean enableMetrics;
    
    public enum EventDistribution {
        UNIFORM,        // Distribución uniforme entre tipos de eventos
        PERSON_HEAVY,   // Más eventos de tipo PERSON
        BID_HEAVY,      // Más eventos de tipo BID
        BURSTY          // Eventos en ráfagas
    }
    
    private BenchmarkConfig(Builder builder) {
        this.numEvents = builder.numEvents;
        this.schedulerCapacity = builder.schedulerCapacity;
        this.processingDelayMs = builder.processingDelayMs;
        this.sourceParallelism = builder.sourceParallelism;
        this.operatorParallelism = builder.operatorParallelism;
        this.eventDistribution = builder.eventDistribution;
        this.enableMetrics = builder.enableMetrics;
    }
    
    // Getters
    public int getNumEvents() { return numEvents; }
    public int getSchedulerCapacity() { return schedulerCapacity; }
    public int getProcessingDelayMs() { return processingDelayMs; }
    public int getSourceParallelism() { return sourceParallelism; }
    public int getOperatorParallelism() { return operatorParallelism; }
    public EventDistribution getEventDistribution() { return eventDistribution; }
    public boolean isEnableMetrics() { return enableMetrics; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int numEvents = 10000;
        private int schedulerCapacity = 4;
        private int processingDelayMs = 10;
        private int sourceParallelism = 1;
        private int operatorParallelism = 1;
        private EventDistribution eventDistribution = EventDistribution.UNIFORM;
        private boolean enableMetrics = true;
        
        public Builder numEvents(int numEvents) {
            this.numEvents = numEvents;
            return this;
        }
        
        public Builder schedulerCapacity(int capacity) {
            this.schedulerCapacity = capacity;
            return this;
        }
        
        public Builder processingDelayMs(int delayMs) {
            this.processingDelayMs = delayMs;
            return this;
        }
        
        public Builder sourceParallelism(int parallelism) {
            this.sourceParallelism = parallelism;
            return this;
        }
        
        public Builder operatorParallelism(int parallelism) {
            this.operatorParallelism = parallelism;
            return this;
        }
        
        public Builder eventDistribution(EventDistribution distribution) {
            this.eventDistribution = distribution;
            return this;
        }
        
        public Builder enableMetrics(boolean enable) {
            this.enableMetrics = enable;
            return this;
        }
        
        public BenchmarkConfig build() {
            return new BenchmarkConfig(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "BenchmarkConfig{events=%d, capacity=%d, delay=%dms, distribution=%s}",
            numEvents, schedulerCapacity, processingDelayMs, eventDistribution
        );
    }
}