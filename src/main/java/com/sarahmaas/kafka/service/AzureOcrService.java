package com.sarahmaas.kafka.service;

import com.azure.ai.vision.imageanalysis.ImageAnalysisClient;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClientBuilder;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisResult;
import com.azure.ai.vision.imageanalysis.models.VisualFeatures;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.BinaryData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AzureOcrService {

    private final ImageAnalysisClient client;
    @Value("${file-path.prefix}")
    private String FILE_PATH_PREFIX;

    /**
     * Constructor that initializes the Azure Computer Vision client.     *
     */
    @Autowired
    public AzureOcrService(CredentialsDecryptorService credentialsDecryptor) {

        // Decrypt Azure credentials
        String endpoint = credentialsDecryptor.decryptAzureOcrHost();
        String subscriptionKey = credentialsDecryptor.decryptAzureOcrApi();

        // Initialize Image Analysis client
        this.client = new ImageAnalysisClientBuilder()
                .endpoint(endpoint)
                .credential(new KeyCredential(subscriptionKey))
                .buildClient();
    }

    /**
     * Read text from cropped OCR image using Azure Computer Vision API.
     * Iteratively adjusts crop ratio to find text in the image header.
     *
     * @param imagePath Path to the image file (relative to FILE_PATH_PREFIX)
     * @return Extracted text as a single string
     */
    public String readTextFromCroppedOcrImage(String imagePath) {
        double startImageRatio = 0.13;
        double endImageRatio = 0.19;
        List<String> extractedText = new ArrayList<>();

        // Iteratively increase the crop ratio until text is found or max ratio is reached
        System.out.println("File path prefix = "+FILE_PATH_PREFIX);
        while ((endImageRatio - startImageRatio) <= 0.1 && extractedText.isEmpty()) {
            try {
                // Load image
                String final_image_path = FILE_PATH_PREFIX +"/"+ imagePath;
                BufferedImage img = ImageIO.read(new File(final_image_path));
                int width = img.getWidth();
                int height = img.getHeight();

                // Calculate crop dimensions
                int headerHeightStart = (int) (height * startImageRatio);
                int headerHeightEnd = (int) (height * endImageRatio);

                // Crop the header section
                BufferedImage headerCrop = img.getSubimage(
                        0,
                        headerHeightStart,
                        width,
                        headerHeightEnd - headerHeightStart
                );

                // Convert cropped image to bytes
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ImageIO.write(headerCrop, "PNG", buffer);
                byte[] headerBytes = buffer.toByteArray();

                try {
                    long startTime = System.currentTimeMillis();
                    // Call Azure Image Analysis API with READ feature
                    BinaryData imageData = BinaryData.fromBytes(headerBytes);

                    ImageAnalysisResult result = client.analyze(
                            imageData,
                            Arrays.asList(VisualFeatures.READ),
                            null
                    );

                    // Extract text from result
                    if (result.getRead() != null && result.getRead().getBlocks() != null) {
                        result.getRead().getBlocks().forEach(block -> {
                            block.getLines().forEach(line -> {
                                String text = line.getText();
                                if (text != null && !text.trim().isEmpty()) {
                                    extractedText.add(text);
                                }
                            });
                        });

                        long endTime = System.currentTimeMillis();
                        System.out.println("Time taken for OCR = "+(endTime - startTime)+ " milli seconds ");

                        if (!extractedText.isEmpty()) {
                            System.out.printf("Extracted text at ratio %.2f-%.2f: %s%n",
                                    startImageRatio, endImageRatio,
                                    extractedText.isEmpty() ? "[]" : "[" + extractedText.get(0) + "...]");
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Exception during OCR image processing: " + e.getMessage());

                    // Check if it's a rate limit error
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        System.out.println("Error during OCR image processing: " + e.getMessage());
                        System.out.println("API call limit reached. Wait for a minute...");
                        Thread.sleep(60000); // Wait for a minute before continuing
                        continue; // Repeat the same ratio
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException("Failed to read or process image: " + imagePath, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("OCR operation interrupted", e);
            }

            // Adjust ratio for next iteration
            startImageRatio -= 0.01;
        }

        return String.join("", extractedText);
    }

    /**
     * Alternative method that allows specifying custom file path prefix.
     *
     * @param imagePath Path to the image file
     * @param filePathPrefix Custom prefix for the file path
     * @return Extracted text as a single string
     */
    public String readTextFromCroppedOcrImage(String imagePath, String filePathPrefix) {
        return readTextFromCroppedOcrImageWithPrefix(imagePath, filePathPrefix);
    }

    private String readTextFromCroppedOcrImageWithPrefix(String imagePath, String prefix) {
        double startImageRatio = 0.13;
        double endImageRatio = 0.19;
        List<String> extractedText = new ArrayList<>();

        while ((endImageRatio - startImageRatio) <= 0.1 && extractedText.isEmpty()) {
            try {
                BufferedImage img = ImageIO.read(new File(prefix + imagePath));
                int width = img.getWidth();
                int height = img.getHeight();

                int headerHeightStart = (int) (height * startImageRatio);
                int headerHeightEnd = (int) (height * endImageRatio);

                BufferedImage headerCrop = img.getSubimage(
                        0, headerHeightStart, width, headerHeightEnd - headerHeightStart
                );

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ImageIO.write(headerCrop, "PNG", buffer);
                byte[] headerBytes = buffer.toByteArray();

                try {
                    // Call Azure Image Analysis API with READ feature
                    BinaryData imageData = BinaryData.fromBytes(headerBytes);

                    ImageAnalysisResult result = client.analyze(
                            imageData,
                            Arrays.asList(VisualFeatures.READ),
                            null
                    );

                    // Extract text from result
                    if (result.getRead() != null && result.getRead().getBlocks() != null) {
                        result.getRead().getBlocks().forEach(block -> {
                            block.getLines().forEach(line -> {
                                String text = line.getText();
                                if (text != null && !text.trim().isEmpty()) {
                                    extractedText.add(text);
                                }
                            });
                        });

                        if (!extractedText.isEmpty()) {
                            System.out.printf("Extracted text at ratio %.2f-%.2f: %s%n",
                                    startImageRatio, endImageRatio,
                                    "[" + extractedText.get(0) + "...]");
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Exception during OCR image processing: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        System.out.println("API call limit reached. Wait for a minute...");
                        Thread.sleep(60000);
                        continue;
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException("Failed to read or process image: " + imagePath, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("OCR operation interrupted", e);
            }

            startImageRatio -= 0.01;
        }

        return String.join("", extractedText);
    }
}