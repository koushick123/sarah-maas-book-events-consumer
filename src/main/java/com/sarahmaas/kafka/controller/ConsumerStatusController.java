package com.sarahmaas.kafka.controller;

import com.sarahmaas.kafka.consumer.BookEventsConsumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConsumerStatusController {
    
    private final BookEventsConsumer bookEventsConsumer;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    
    @GetMapping("/")
    public ResponseEntity<ServiceInfo> root() {
        return ResponseEntity.ok(new ServiceInfo(
                "running",
                "Kafka Consumer Service"
        ));
    }
    
    @GetMapping("/status")
    public ResponseEntity<ServiceStatus> getStatus() {
        Collection<MessageListenerContainer> containers = 
                kafkaListenerEndpointRegistry.getAllListenerContainers();
        
        List<ConsumerStatus> statuses = new ArrayList<>();
        int activeConsumers = 0;
        
        for (MessageListenerContainer container : containers) {
            boolean isRunning = container.isRunning();
            if (isRunning) {
                activeConsumers++;
            }
            
            statuses.add(new ConsumerStatus(
                    container.getListenerId(),
                    isRunning ? "running" : "stopped",
                    container.getMetrics()
            ));
        }
        
        ServiceStatus status = new ServiceStatus(
                activeConsumers,
                bookEventsConsumer.getMessagesProcessed(),
                statuses
        );
        
        return ResponseEntity.ok(status);
    }
    
    @PostMapping("/shutdown")
    public ResponseEntity<ShutdownResponse> shutdown() {
        try {
            kafkaListenerEndpointRegistry.getAllListenerContainers()
                    .forEach(MessageListenerContainer::stop);
            
            return ResponseEntity.ok(new ShutdownResponse(
                    "success",
                    "Shutdown initiated for all consumers"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ShutdownResponse("error", e.getMessage()));
        }
    }
    
    @PostMapping("/start")
    public ResponseEntity<ShutdownResponse> start() {
        try {
            kafkaListenerEndpointRegistry.getAllListenerContainers()
                    .forEach(MessageListenerContainer::start);
            
            return ResponseEntity.ok(new ShutdownResponse(
                    "success",
                    "All consumers started"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ShutdownResponse("error", e.getMessage()));
        }
    }
    
    // DTOs
    @Data
    @AllArgsConstructor
    static class ServiceInfo {
        private String status;
        private String service;
    }
    
    @Data
    @AllArgsConstructor
    static class ServiceStatus {
        private int activeConsumers;
        private long totalMessagesProcessed;
        private List<ConsumerStatus> consumers;
    }
    
    @Data
    @AllArgsConstructor
    static class ConsumerStatus {
        private String listenerId;
        private String status;
        private Object metrics;
    }
    
    @Data
    @AllArgsConstructor
    static class ShutdownResponse {
        private String status;
        private String message;
    }
}
