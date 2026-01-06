package com.sarahmaas.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class BookEventsConsumerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BookEventsConsumerApplication.class, args);
    }
}
