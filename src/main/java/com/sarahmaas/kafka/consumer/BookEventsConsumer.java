package com.sarahmaas.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarahmaas.kafka.client.PythonApiClient;
import com.sarahmaas.kafka.model.KafkaMessage;
import com.sarahmaas.kafka.model.PageExtraction;
import com.sarahmaas.kafka.repository.PageExtractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookEventsConsumer {
    
    private final PythonApiClient pythonApiClient;
    private final PageExtractionRepository repository;
    private final ObjectMapper objectMapper;
    
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    
    @KafkaListener(
            topics = "mytopic",
            groupId = "test-group-2025",
            concurrency = "10",  // 10 concurrent consumers
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Worker received message from partition: {} at offset: {}", 
                    record.partition(), record.offset());
            
            // Parse Kafka message
            KafkaMessage message = objectMapper.readValue(record.value(), KafkaMessage.class);
            
            log.info("Processing page: {} for image path: {}", 
                    message.getPageNum(), "/" + message.getImagePath());
            
            // Call Python API to extract text from image
            String extractedText = pythonApiClient.extractTextFromImage(
                    "/" + message.getImagePath(), 
                    message.getPageNum()
            );
            
            log.debug("Extracted text for page {}: {}...", 
                     message.getPageNum(), 
                     extractedText != null && extractedText.length() > 5 
                             ? extractedText.substring(0, Math.min(5, extractedText.length())) 
                             : "");
            
            // Save to MongoDB
            PageExtraction extraction = PageExtraction.builder()
                    .bookId(message.getBookId())
                    .pageNum(message.getPageNum())
                    .extractedText(extractedText != null && extractedText.length() > 15 
                            ? extractedText.substring(0, 15) 
                            : extractedText)
                    .build();
            
            repository.save(extraction);
            
            // Manually acknowledge the message
            if (ack != null) {
                ack.acknowledge();
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            long totalProcessed = messagesProcessed.incrementAndGet();
            
            log.info("Successfully processed message for page {} in {}ms. Total processed: {}", 
                    message.getPageNum(), processingTime, totalProcessed);
            
        } catch (Exception e) {
            log.error("Error processing message from partition {} at offset {}: {}", 
                     record.partition(), record.offset(), e.getMessage(), e);
            
            // Depending on your error handling strategy:
            // 1. Acknowledge to skip the message: ack.acknowledge()
            // 2. Don't acknowledge to retry: don't call ack.acknowledge()
            // 3. Send to DLQ (Dead Letter Queue) - requires additional configuration
            
            // For now, we'll acknowledge to avoid infinite retries
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }
    
    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }
}
