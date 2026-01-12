package com.sarahmaas.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarahmaas.kafka.model.KafkaMessage;
import com.sarahmaas.kafka.model.PageExtraction;
import com.sarahmaas.kafka.repository.PageExtractionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class BookEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookEventsConsumer.class);

    private final PageExtractionRepository repository;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    private final AtomicLong messagesProcessed = new AtomicLong(0);

    // Prefer environment variable names when running in Docker; fall back to application property or default.
    @Value("${OCR_URI:${ocr.uri:http://localhost:8081/ocr}}")
    private String ocrUrl;

    // Kafka listener settings from env (pass with -e), with defaults
    // Example docker run: -e KAFKA_TOPIC=mytopic -e KAFKA_GROUP=test-group-2025 -e SPRING_KAFKA_LISTENER_CONCURRENCY=4
    @Value("${KAFKA_TOPIC:mytopic}")
    private String topic;

    @Value("${KAFKA_GROUP:test-group-2025}")
    private String groupId;

    public BookEventsConsumer(PageExtractionRepository repository,
                              ObjectMapper objectMapper,
                              RestTemplateBuilder restTemplateBuilder) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @KafkaListener(
            topics = "${KAFKA_TOPIC:mytopic}",
            groupId = "${KAFKA_GROUP:test-group-2025}",
            concurrency = "${SPRING_KAFKA_LISTENER_CONCURRENCY:1}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Worker received message from partition: {} at offset: {}",
                    record.partition(), record.offset());

            KafkaMessage message = objectMapper.readValue(record.value(), KafkaMessage.class);

            log.info("Processing page: {} for image path: {}",
                    message.getPageNum(), "/" + message.getImagePath());

            RestTemplate restTemplate = restTemplateBuilder.build();
            String extractedText = restTemplate.getForObject(ocrUrl + "/" + message.getImagePath(), String.class);

                        // Normalize extracted text: trim and strip surrounding quotes if present
                        if (extractedText != null) {
                                extractedText = extractedText.trim();
                                if (extractedText.length() >= 2 && extractedText.startsWith("\"") && extractedText.endsWith("\"")) {
                                        extractedText = extractedText.substring(1, extractedText.length() - 1);
                                }
                        }

                        log.debug("Extracted text for page {}: {}...",
                                        message.getPageNum(),
                                        extractedText != null && extractedText.length() > 5
                                                        ? extractedText.substring(0, Math.min(5, extractedText.length()))
                                                        : "");

            PageExtraction extraction = PageExtraction.builder()
                    .bookId(message.getBookId())
                    .pageNum(message.getPageNum())
                    .extractedText(extractedText != null && extractedText.length() > 15
                            ? extractedText.substring(0, 15)
                            : extractedText)
                    .build();

            repository.save(extraction);

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

            if (ack != null) {
                ack.acknowledge();
            }
        }
    }

    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }
}
