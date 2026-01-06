package com.sarahmaas.kafka.repository;

import com.sarahmaas.kafka.model.PageExtraction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PageExtractionRepository extends MongoRepository<PageExtraction, String> {
    
    List<PageExtraction> findByBookId(String bookId);
    
    PageExtraction findByBookIdAndPageNum(String bookId, Integer pageNum);
}
