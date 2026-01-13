package com.sarahmaas.kafka.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;

@Service
public class VaultTokenService {

    @Value("${VAULT_RETRIEVER_ADDR:null}")
    private String vaultRetrieverAddr;

    @Value("${VAULT_ADDR:null}")
    private String vaultAddr;

    @Value("${SSL_FLAG:false}")
    private String sslFlag;

    @Value("${HARDCODED_VAULT_TOKEN:null}")
    private String hardcodedVaultToken;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public VaultTokenService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Fetch Vault access token by retrieving VM metadata (vmId, publicKeys)
     * and sending it to the Vault token retrieval service.
     *
     * @return Vault access token if success, or error message if unauthorized.
     */
    public String fetchVaultToken() {
        try {
            // Fetch VM metadata
            // Using IMDS metadata service for DigitalOcean. This IP address is non-routable and cannot be accessed externally.
            String vmId = fetchMetadata("http://169.254.169.254/metadata/v1/id");
            String publicKeys = fetchMetadata("http://169.254.169.254/metadata/v1/public-keys");
            
            // Vault token retrieval service
            String url = String.format("http://%s/fetchVaultToken", vaultRetrieverAddr);
            
            // Create payload
            String payload = objectMapper.writeValueAsString(new VaultTokenRequest(vmId, publicKeys));
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode data = objectMapper.readTree(response.body());
                if ("success".equals(data.get("result").asText())) {
                    return data.get("token").asText();
                } else {
                    String error = data.has("error") ? data.get("error").asText() : "Unauthorized VM for accessing Vault token";
                    return "Error: " + error;
                }
            } else {
                return "Error: HTTP " + response.statusCode();
            }
        } catch (IOException | InterruptedException e) {
            return "Request failed: " + e.getMessage();
        }
    }
    
    /**
     * Helper method to fetch metadata from the given URL.
     */
    private String fetchMetadata(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch metadata from " + url);
        }
        
        return response.body().trim();
    }
    
    /**
     * Fetch decryption key from Vault.
     *
     * @param key The key to retrieve from Vault
     * @return The value associated with the key
     * @throws IllegalStateException if unable to fetch the key
     */
    public String fetchDecryptionKeyFromVault(String key) {
        String vaultToken;
        
        if (hardcodedVaultToken != null && !hardcodedVaultToken.isEmpty()) {
            vaultToken = hardcodedVaultToken;
        } else {
            vaultToken = fetchVaultToken();
        }
        
        if (vaultToken.startsWith("Error:") || vaultToken.startsWith("Request failed:")) {
            throw new IllegalStateException(vaultToken);
        }
        
        try {
            String url;
            HttpClient client;
            
            if ("true".equals(sslFlag)) {
                url = String.format("https://%s/v1/sm-secrets/data/openapi_mongodb_credentials", vaultAddr);
                client = createSSLHttpClient("vault-droplet/ssl/ca.crt");
            } else {
                url = "http://localhost:8300/v1/sm-secrets/data/openapi_mongodb_credentials";
                client = httpClient;
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("accept", "application/json")
                    .header("X-Vault-Token", vaultToken)
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP error: " + response.statusCode());
            }
            
            JsonNode jsonData = objectMapper.readTree(response.body());
            JsonNode dataNode = jsonData.get("data").get("data");
            
            if (!dataNode.has(key)) {
                throw new IllegalStateException("Key '" + key + "' not found in Vault");
            }
            
            String keyValue = dataNode.get(key).asText();
            System.out.println("Fetched value for " + key + ": " + (keyValue != null));
            
            return keyValue;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to fetch key from Vault: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create an HTTP client with SSL support using the specified certificate.
     */
    private HttpClient createSSLHttpClient(String certPath) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca;
            try (FileInputStream fis = new FileInputStream(certPath)) {
                ca = cf.generateCertificate(fis);
            }
            
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);
            
            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL HTTP client", e);
        }
    }
    
    // Inner class for Vault token request payload
    private static class VaultTokenRequest {
        public String vmId;
        public String publicKeys;
        
        public VaultTokenRequest(String vmId, String publicKeys) {
            this.vmId = vmId;
            this.publicKeys = publicKeys;
        }
    }
}
