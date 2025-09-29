package com.insurancemegacorp.policymcpserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic controller to help debug RAG issues
 */
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Check what documents exist for a specific customer
     */
    @GetMapping("/customer/{customerId}")
    public Map<String, Object> getCustomerDocuments(@PathVariable Integer customerId) {
        logger.info("Diagnostic: Checking documents for customer {}", customerId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("customerId", customerId);
        
        try {
            // Check if any documents exist for this customer
            String sql = "SELECT id, content, metadata FROM vector_store WHERE metadata->>'refnum1' = ?";
            List<Map<String, Object>> documents = jdbcTemplate.queryForList(sql, customerId.toString());
            
            result.put("documentCount", documents.size());
            result.put("documents", documents);
            
            if (documents.isEmpty()) {
                // Check what customer IDs actually exist
                String customerCheckSql = "SELECT DISTINCT metadata->>'refnum1' as customer_id FROM vector_store ORDER BY customer_id";
                List<String> existingCustomers = jdbcTemplate.queryForList(customerCheckSql, String.class);
                result.put("existingCustomerIds", existingCustomers);
                result.put("message", "No documents found for customer " + customerId);
            } else {
                result.put("message", "Found " + documents.size() + " documents for customer " + customerId);
                
                // Show content preview for first few documents
                for (int i = 0; i < Math.min(documents.size(), 3); i++) {
                    Map<String, Object> doc = documents.get(i);
                    String content = (String) doc.get("content");
                    if (content != null && content.length() > 200) {
                        doc.put("contentPreview", content.substring(0, 200) + "...");
                        doc.remove("content"); // Remove full content to keep response manageable
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error checking customer documents: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Search for documents containing specific keywords
     */
    @GetMapping("/search")
    public Map<String, Object> searchDocuments(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer customerId) {
        
        logger.info("Diagnostic: Searching for keyword '{}' for customer {}", keyword, customerId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("customerId", customerId);
        
        try {
            String sql;
            List<Map<String, Object>> documents;
            
            if (customerId != null) {
                sql = "SELECT id, content, metadata FROM vector_store WHERE " +
                      "metadata->>'refnum1' = ? AND LOWER(content) LIKE LOWER(?)";
                documents = jdbcTemplate.queryForList(sql, customerId.toString(), "%" + keyword + "%");
            } else {
                sql = "SELECT id, content, metadata FROM vector_store WHERE LOWER(content) LIKE LOWER(?)";
                documents = jdbcTemplate.queryForList(sql, "%" + keyword + "%");
            }
            
            result.put("documentCount", documents.size());
            
            if (documents.isEmpty()) {
                result.put("message", "No documents found containing keyword '" + keyword + "'");
            } else {
                result.put("message", "Found " + documents.size() + " documents containing '" + keyword + "'");
                
                // Show content preview with keyword context
                for (Map<String, Object> doc : documents) {
                    String content = (String) doc.get("content");
                    if (content != null) {
                        // Find keyword context
                        String lowerContent = content.toLowerCase();
                        String lowerKeyword = keyword.toLowerCase();
                        int index = lowerContent.indexOf(lowerKeyword);
                        
                        if (index >= 0) {
                            int start = Math.max(0, index - 100);
                            int end = Math.min(content.length(), index + keyword.length() + 100);
                            String context = content.substring(start, end);
                            doc.put("keywordContext", "..." + context + "...");
                        }
                        
                        doc.remove("content"); // Remove full content
                    }
                }
                
                result.put("documents", documents);
            }
            
        } catch (Exception e) {
            logger.error("Error searching documents: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Get vector store statistics
     */
    @GetMapping("/stats")
    public Map<String, Object> getVectorStoreStats() {
        logger.info("Diagnostic: Getting vector store statistics");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Total document count
            Long totalDocs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class);
            result.put("totalDocuments", totalDocs);
            
            // Customer distribution
            String customerSql = "SELECT metadata->>'refnum1' as customer_id, COUNT(*) as doc_count " +
                               "FROM vector_store GROUP BY metadata->>'refnum1' ORDER BY customer_id";
            List<Map<String, Object>> customerStats = jdbcTemplate.queryForList(customerSql);
            result.put("documentsByCustomer", customerStats);
            
            // Sample content keywords
            String keywordSql = "SELECT id, SUBSTRING(content, 1, 100) as content_preview " +
                              "FROM vector_store LIMIT 5";
            List<Map<String, Object>> sampleContent = jdbcTemplate.queryForList(keywordSql);
            result.put("sampleContent", sampleContent);
            
        } catch (Exception e) {
            logger.error("Error getting vector store stats: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
