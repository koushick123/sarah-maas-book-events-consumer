package com.sarahmaas.kafka.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.sarahmaas.kafka.client.PythonApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@EnableMongoRepositories(basePackages = "com.sarahmaas.kafka.repository")
@RequiredArgsConstructor
public class MongoConfig extends AbstractMongoClientConfiguration {
    
    private final PythonApiClient pythonApiClient;
    
    @Value("${spring.data.mongodb.database}")
    private String databaseName;
    
    @Override
    protected String getDatabaseName() {
        return databaseName;
    }
    
    @Override
    @Bean
    public MongoClient mongoClient() {
        try {
            // Get decrypted credentials from Python API
            String username = pythonApiClient.decryptMongoUser();
            String password = pythonApiClient.decryptMongoPassword();
            String hostUrl = pythonApiClient.decryptMongoHost();
            
            // URL encode credentials
            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8.toString());
            String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8.toString());
            
            // Build MongoDB URI
            String uri = String.format(
                    "mongodb+srv://%s:%s@%s/?retryWrites=true&w=majority&appName=dev-cluster",
                    encodedUsername, encodedPassword, hostUrl
            );
            
            log.info("Connecting to MongoDB with host: {}", hostUrl);
            
            ConnectionString connectionString = new ConnectionString(uri);
            MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build();
            
            return MongoClients.create(mongoClientSettings);
            
        } catch (UnsupportedEncodingException e) {
            log.error("Error encoding MongoDB credentials", e);
            throw new RuntimeException("Failed to encode MongoDB credentials", e);
        } catch (Exception e) {
            log.error("Error creating MongoDB client", e);
            throw new RuntimeException("Failed to create MongoDB client", e);
        }
    }
}
