package com.sarahmaas.kafka.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Configuration
@EnableMongoRepositories(basePackages = "com.sarahmaas.kafka.repository")
@RequiredArgsConstructor
public class MongoConfig extends AbstractMongoClientConfiguration {
    
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${credentials.uri}")
    private String credentialsUrl;
        
    @Override
    protected String getDatabaseName() {
        return "sarah-maas-db";
    }
    
    /**
     * Fetches MongoDB credentials from a remote HTTP endpoint
     * @return Map containing username, password, and url
     */
    private Map<String, String> fetchMongoCredentials() {
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            log.info("Fetching MongoDB credentials from: {}", credentialsUrl);

            @SuppressWarnings("unchecked")
            Map<String, String> credentials = restTemplate.getForObject(credentialsUrl, Map.class);

            if (credentials == null || credentials.isEmpty()) {
                throw new RuntimeException("No credentials returned from endpoint");
            }

            log.debug("Successfully fetched MongoDB credentials = {}", credentials);
            return credentials;
            
        } catch (Exception e) {
            log.error("Error fetching MongoDB credentials from {}: {}", credentialsUrl, e.getMessage());
            throw new RuntimeException("Failed to fetch MongoDB credentials", e);
        }
    }
    
    @Override
    @Bean
    public MongoClient mongoClient() {
        try {
            // Fetch credentials from HTTP endpoint
            Map<String, String> credentials = fetchMongoCredentials();
            
            String username = credentials.get("mongo_user");
            String password = credentials.get("mongo_password");
            String hostUrl = credentials.get("mongo_hosturl");
            
            if (username == null || password == null || hostUrl == null) {
                throw new RuntimeException("Missing required credentials: username, password, or url");
            }
            
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
