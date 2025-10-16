package com.scheduling.framework.nexmark;

import com.scheduling.framework.model.Task;
import org.apache.beam.sdk.nexmark.model.Bid;
import org.apache.beam.sdk.nexmark.model.Person;
import org.apache.beam.sdk.nexmark.model.Auction;
import org.apache.beam.sdk.nexmark.model.Event;

import java.io.Serializable;
import java.util.UUID;

/**
 * Adaptador para convertir eventos de Nexmark en tareas del framework
 */
public class NexmarkAdapter implements Serializable {
    
    /**
     * Convierte un evento de Nexmark en una tarea
     */
    public static Task eventToTask(Event event) {
        String taskId = UUID.randomUUID().toString();
        long arrivalTime = System.currentTimeMillis();
        
        if (event.newPerson != null) {
            return new Task(taskId, "PERSON", event.newPerson, arrivalTime, 1);
        } else if (event.newAuction != null) {
            return new Task(taskId, "AUCTION", event.newAuction, arrivalTime, 2);
        } else if (event.bid != null) {
            return new Task(taskId, "BID", event.bid, arrivalTime, 3);
        }
        
        return new Task(taskId, "UNKNOWN", event, arrivalTime, 0);
    }
    
    /**
     * Genera un evento de tipo Person simulado
     */
    public static Task generatePersonTask() {
        String taskId = UUID.randomUUID().toString();
        Person person = new Person(
            System.currentTimeMillis(),
            "Person-" + taskId,
            "person@example.com",
            "CC",
            "City",
            "State",
            System.currentTimeMillis()
        );
        return new Task(taskId, "PERSON", person, System.currentTimeMillis(), 1);
    }
    
    /**
     * Genera un evento de tipo Auction simulado
     */
    public static Task generateAuctionTask() {
        String taskId = UUID.randomUUID().toString();
        Auction auction = new Auction(
            System.currentTimeMillis(),
            "Auction-" + taskId,
            "Description",
            100L,
            200L,
            System.currentTimeMillis(),
            System.currentTimeMillis() + 3600000,
            1L,
            10L
        );
        return new Task(taskId, "AUCTION", auction, System.currentTimeMillis(), 2);
    }
    
    /**
     * Genera un evento de tipo Bid simulado
     */
    public static Task generateBidTask() {
        String taskId = UUID.randomUUID().toString();
        Bid bid = new Bid(
            1L,
            2L,
            100L,
            System.currentTimeMillis(),
            "extra"
        );
        return new Task(taskId, "BID", bid, System.currentTimeMillis(), 3);
    }
}