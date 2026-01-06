package com.sarahmaas.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessage {
    
    @JsonProperty("book_id")
    private String bookId;
    
    @JsonProperty("page_num")
    private Integer pageNum;
    
    @JsonProperty("image_path")
    private String imagePath;
}
