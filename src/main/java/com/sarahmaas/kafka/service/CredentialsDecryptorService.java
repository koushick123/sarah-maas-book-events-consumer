package com.sarahmaas.kafka.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CredentialsDecryptorService {

    @Autowired
    private VaultTokenService vaultService;

    public CredentialsDecryptorService(){}
    /**
     * Function to decrypt Azure OCR API key.
     * 
     * @return Decrypted Azure OCR API key
     * @throws IllegalStateException if decryption fails
     */
    public String decryptAzureOcrApi() {
        String encryptionKey = vaultService.fetchDecryptionKeyFromVault("FERNET_KEY_AZURE_OCR_KEY");
        
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException("FERNET_KEY_AZURE_OCR_KEY not set");
        }
        
        try {
            String encryptedData = Files.readString(Paths.get("src/main/resources/encryptedazureocrapi.txt"));
            return decryptFernet(encryptionKey, encryptedData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read encryptedazureocrapi.txt", e);
        }
    }
    
    /**
     * Function to decrypt Azure OCR Host.
     * 
     * @return Decrypted Azure OCR Host
     * @throws IllegalStateException if decryption fails
     */
    public String decryptAzureOcrHost() {
        String encryptionKey = vaultService.fetchDecryptionKeyFromVault("FERNET_KEY_AZURE_OCR_HOST");
        
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException("FERNET_KEY_AZURE_OCR_HOST not set");
        }
        
        try {
            String encryptedData = Files.readString(Paths.get("src/main/resources/encryptedazureocrhost.txt"));
            return decryptFernet(encryptionKey, encryptedData);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read encryptedazureocrhost.txt", e);
        }
    }
    
    /**
     * Decrypt data using Fernet-compatible encryption.
     * Fernet uses AES-128-CBC with HMAC-SHA256 for authentication.
     * 
     * @param fernetKey Base64-encoded Fernet key (44 characters)
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted string
     */
    private String decryptFernet(String fernetKey, String encryptedData) {
        try {
            // Decode the Fernet key (32 bytes: 16 for signing, 16 for encryption)
            byte[] keyBytes = Base64.getUrlDecoder().decode(fernetKey);
            
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Invalid Fernet key length");
            }
            
            // Split key: first 16 bytes for signing, last 16 bytes for encryption
            byte[] signingKey = Arrays.copyOfRange(keyBytes, 0, 16);
            byte[] encryptionKeyBytes = Arrays.copyOfRange(keyBytes, 16, 32);
            
            // Decode the encrypted token
            byte[] token = Base64.getUrlDecoder().decode(encryptedData.trim());
            
            // Fernet token format:
            // Version (1 byte) | Timestamp (8 bytes) | IV (16 bytes) | Ciphertext (variable) | HMAC (32 bytes)
            
            if (token.length < 57) { // Minimum: 1 + 8 + 16 + 0 + 32
                throw new IllegalArgumentException("Invalid Fernet token");
            }
            
            // Extract components
            byte version = token[0];
            if (version != (byte) 0x80) {
                throw new IllegalArgumentException("Unsupported Fernet version");
            }
            
            // Verify HMAC (last 32 bytes)
            int hmacStart = token.length - 32;
            byte[] receivedHmac = Arrays.copyOfRange(token, hmacStart, token.length);
            byte[] dataToVerify = Arrays.copyOfRange(token, 0, hmacStart);
            
            byte[] computedHmac = computeHmacSha256(signingKey, dataToVerify);
            
            if (!MessageDigest.isEqual(receivedHmac, computedHmac)) {
                throw new IllegalStateException("HMAC verification failed");
            }
            
            // Extract IV and ciphertext
            byte[] iv = Arrays.copyOfRange(token, 9, 25); // After version (1) and timestamp (8)
            byte[] ciphertext = Arrays.copyOfRange(token, 25, hmacStart);
            
            // Decrypt using AES-128-CBC
            SecretKeySpec secretKey = new SecretKeySpec(encryptionKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new javax.crypto.spec.IvParameterSpec(iv));
            
            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, "UTF-8");
            
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt Fernet data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Compute HMAC-SHA256.
     */
    private byte[] computeHmacSha256(byte[] key, byte[] data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
}
