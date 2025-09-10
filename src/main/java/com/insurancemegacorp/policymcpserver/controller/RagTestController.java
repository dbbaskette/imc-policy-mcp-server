package com.insurancemegacorp.policymcpserver.controller;

import com.insurancemegacorp.policymcpserver.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for RAG functionality.
 * Only active in local and dev profiles for testing purposes.
 */
@RestController
@RequestMapping("/api/test")
@Profile({"local", "dev"})
public class RagTestController {

    private static final Logger logger = LoggerFactory.getLogger(RagTestController.class);

    @Autowired
    private RagService ragService;

    /**
     * Test endpoint for RAG query processing.
     * 
     * @param query The query string
     * @param customerId The customer ID for filtering
     * @return Response with retrieved context or error message
     */
    @GetMapping("/rag")
    public ResponseEntity<Map<String, Object>> testRagQuery(
            @RequestParam String query,
            @RequestParam Integer customerId) {
        
        logger.info("Test RAG query request - Customer: {}, Query: '{}'", customerId, query);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            String context = ragService.processQuery(query, customerId);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            response.put("success", true);
            response.put("customerId", customerId);
            response.put("query", query);
            response.put("context", context);
            response.put("contextLength", context.length());
            response.put("processingTimeMs", processingTime);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("RAG query successful - Customer: {}, Processing time: {}ms, Context length: {}", 
                       customerId, processingTime, context.length());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("RAG query failed - Customer: {}, Query: '{}', Error: {}", 
                        customerId, query, e.getMessage());
            
            response.put("success", false);
            response.put("customerId", customerId);
            response.put("query", query);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get RAG service configuration information.
     */
    @GetMapping("/rag/info")
    public ResponseEntity<Map<String, Object>> getRagServiceInfo() {
        logger.info("RAG service info requested");
        
        try {
            RagService.RagServiceInfo info = ragService.getServiceInfo();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("topK", info.getTopK());
            response.put("similarityThreshold", info.getSimilarityThreshold());
            response.put("retrieverType", info.getRetrieverType());
            response.put("advisorType", info.getAdvisorType());
            response.put("queryRewriteEnabled", info.isQueryRewriteEnabled());
            response.put("multiQueryEnabled", info.isMultiQueryEnabled());
            response.put("multiQueryCount", info.getMultiQueryCount());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get RAG service info: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Health check endpoint for RAG testing.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "RAG Test Controller");
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "RAG test endpoints are available");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get sample customer IDs and queries for testing.
     * These are based on the data that should be in the CSV file.
     */
    @GetMapping("/samples")
    public ResponseEntity<Map<String, Object>> getSampleQueries() {
        Map<String, Object> response = new HashMap<>();
        
        // Sample customer IDs from the CSV data (refnum1 values)
        response.put("sampleCustomerIds", new Integer[]{100003, 100004, 100005});
        
        // Sample queries to test
        response.put("sampleQueries", new String[]{
                "What is covered under my auto insurance policy?",
                "What are the definitions in my policy?",
                "What happens if I get into an accident?",
                "What is my deductible?",
                "What is not covered by my insurance?"
        });
        
        response.put("usage", "Use GET /api/test/rag?query={query}&customerId={customerId} to test");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
