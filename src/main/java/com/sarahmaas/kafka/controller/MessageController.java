package com.sarahmaas.kafka.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarahmaas.kafka.model.KafkaMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/send-message")
    public String sendMessage(@RequestBody KafkaMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            kafkaTemplate.send("mytopic", jsonMessage);
            return "Message sent successfully: " + jsonMessage;
        } catch (Exception e) {
            return "Error sending message: " + e.getMessage();
        }
    }

    @GetMapping("/healthcheck")
    public String hello(){
        return "Application UP";
    }
}
