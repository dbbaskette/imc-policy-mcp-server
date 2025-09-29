package com.insurancemegacorp.policymcpserver.controller;

import com.insurancemegacorp.policymcpserver.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Debug controller for testing vector search functionality.
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    @Autowired
    private RagService ragService;

    @Value("${spring.ai.openai.base-url:not-set}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:not-set}")
    private String apiKey;

    /**
     * Debug endpoint to test vector search directly.
     */
    @GetMapping("/search")
    public ResponseEntity<String> debugSearch(
            @RequestParam String query,
            @RequestParam Integer customerId,
            @RequestParam(defaultValue = "0.4") Double threshold) {
        
        try {
            String result = ragService.debugSearch(query, customerId, threshold);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Direct test endpoint with very low threshold.
     */
    @GetMapping("/direct")
    public ResponseEntity<String> directTest(
            @RequestParam String query,
            @RequestParam Integer customerId) {

        try {
            String result = ragService.testDirectSearch(query, customerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Check Spring AI configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<String> checkConfig() {
        StringBuilder config = new StringBuilder();
        config.append("Spring AI Configuration:\n");
        config.append("Base URL: ").append(baseUrl).append("\n");
        config.append("API Key: ").append(apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "***").append("\n");
        return ResponseEntity.ok(config.toString());
    }
}