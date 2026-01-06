package com.sarahmaas.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sarah-maas-map-page-nos-chapter-heading")
public class PageExtraction {
    
    @Id
    private String id;
    
    @Field("book_id")
    private String bookId;
    
    @Field("page_num")
    private Integer pageNum;
    
    @Field("extracted_text")
    private String extractedText;
    
    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
