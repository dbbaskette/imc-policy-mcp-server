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

    @Autowired
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

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

    /**
     * Get full record from Postgres including content and embeddings
     */
    @GetMapping("/postgres-full/{docId}")
    public Map<String, Object> getPostgresFullRecord(@PathVariable String docId) {
        logger.info("Diagnostic: Getting full Postgres record for document {}", docId);

        Map<String, Object> result = new HashMap<>();
        result.put("documentId", docId);

        try {
            String sql = "SELECT id::text as id, content, metadata::text as metadata, " +
                        "embedding::text as embedding_text, " +
                        "array_length(embedding, 1) as vector_dimensions " +
                        "FROM vector_store WHERE id::text = ?";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, docId);

            if (rows.isEmpty()) {
                result.put("error", "Document not found: " + docId);
                return result;
            }

            Map<String, Object> row = rows.get(0);
            String embeddingText = (String) row.get("embedding_text");

            // Parse first 10 dimensions from embedding
            String vectorPreview = "Not available";
            if (embeddingText != null && embeddingText.length() > 2) {
                String cleaned = embeddingText.substring(1, embeddingText.indexOf(']') > 0 ?
                    Math.min(embeddingText.indexOf(']'), embeddingText.length()) : embeddingText.length());
                String[] dims = cleaned.split(",");
                vectorPreview = java.util.Arrays.stream(dims).limit(10).collect(java.util.stream.Collectors.joining(", ", "[", ", ...]"));
            }

            result.put("postgres", Map.of(
                "id", row.get("id"),
                "content", row.get("content"),
                "content_length", row.get("content") != null ? row.get("content").toString().length() : 0,
                "metadata", row.get("metadata"),
                "vector_dimensions", row.get("vector_dimensions"),
                "vector_preview", vectorPreview
            ));

        } catch (Exception e) {
            logger.error("Error getting Postgres record: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Compare Postgres vs GemFire data for a specific customer
     * Shows exactly how the data is structured in both stores
     */
    @GetMapping("/compare/{customerId}")
    public Map<String, Object> compareStores(@PathVariable Integer customerId,
                                             @RequestParam(required = false, defaultValue = "1") int docNumber) {
        logger.info("Diagnostic: Comparing Postgres vs GemFire data for customer {} (doc #{})", customerId, docNumber);

        Map<String, Object> result = new HashMap<>();
        result.put("customerId", customerId);

        try {
            // Get one document from Postgres
            String postgresSql = "SELECT id, content, metadata, LEFT(CAST(embedding AS TEXT), 100) as embedding_preview " +
                               "FROM vector_store WHERE metadata->>'refnum1' = ? LIMIT 1 OFFSET ?";
            List<Map<String, Object>> postgresRows = jdbcTemplate.queryForList(postgresSql, customerId.toString(), docNumber - 1);

            if (postgresRows.isEmpty()) {
                result.put("error", "No documents found in Postgres for customer " + customerId);

                // Show what customers exist
                String customerCheckSql = "SELECT DISTINCT metadata->>'refnum1' as customer_id FROM vector_store ORDER BY customer_id";
                List<String> existingCustomers = jdbcTemplate.queryForList(customerCheckSql, String.class);
                result.put("existingCustomerIds", existingCustomers);

                return result;
            }

            Map<String, Object> postgresDoc = postgresRows.get(0);
            String docId = postgresDoc.get("id").toString();

            // Parse metadata to show structure
            Object metadataObj = postgresDoc.get("metadata");
            String metadataJson = metadataObj != null ? metadataObj.toString() : "{}";
            String content = postgresDoc.get("content") != null ? postgresDoc.get("content").toString() : "";

            result.put("postgres", Map.of(
                "id", docId,
                "content_length", content.length(),
                "content_preview", content.substring(0, Math.min(200, content.length())),
                "metadata_raw", metadataJson,
                "metadata_parsed", parseJsonToMap(metadataJson),
                "embedding_preview", postgresDoc.get("embedding_preview")
            ));

            // Now query GemFire for the same document ID
            String gemfireUrl = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_HOST");
            String gemfirePort = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_PORT");
            String gemfireUsername = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_USERNAME");
            String gemfirePassword = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_PASSWORD");

            if (gemfireUrl != null && gemfireUsername != null) {
                try {
                    org.springframework.web.reactive.function.client.WebClient webClient = webClientBuilder
                        .baseUrl("https://" + gemfireUrl + ":" + (gemfirePort != null ? gemfirePort : "443"))
                        .defaultHeaders(headers -> {
                            String auth = gemfireUsername + ":" + gemfirePassword;
                            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                            headers.set("Authorization", "Basic " + encodedAuth);
                        })
                        .build();

                    // Get the embedding from GemFire
                    String gemfireResponse = webClient.get()
                        .uri("/gemfire-vectordb/v1/indexes/spring-ai-gemfire-index/embeddings/" + docId)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                    if (gemfireResponse != null) {
                        Map<String, Object> gemfireDoc = parseJsonToMap(gemfireResponse);
                        result.put("gemfire", Map.of(
                            "key", gemfireDoc.getOrDefault("key", "N/A"),
                            "metadata", gemfireDoc.getOrDefault("metadata", "N/A"),
                            "vector_length", gemfireDoc.get("vector") != null ? ((List<?>) gemfireDoc.get("vector")).size() : 0,
                            "raw_response", gemfireResponse
                        ));

                        // Compare metadata structures
                        Map<String, Object> postgresMetadata = parseJsonToMap(metadataJson);
                        Object gemfireMetadata = gemfireDoc.get("metadata");

                        result.put("comparison", Map.of(
                            "postgres_refnum1", postgresMetadata.get("refnum1"),
                            "gemfire_refnum1", gemfireMetadata instanceof Map ? ((Map<?, ?>) gemfireMetadata).get("refnum1") : "NOT_FOUND",
                            "postgres_metadata_structure", postgresMetadata.keySet(),
                            "gemfire_metadata_structure", gemfireMetadata instanceof Map ? ((Map<?, ?>) gemfireMetadata).keySet() : "NOT_A_MAP"
                        ));
                    } else {
                        result.put("gemfire", Map.of("error", "Document not found in GemFire cache"));
                    }

                } catch (Exception e) {
                    result.put("gemfire", Map.of("error", "Failed to query GemFire: " + e.getMessage()));
                    logger.error("Error querying GemFire: {}", e.getMessage(), e);
                }
            } else {
                result.put("gemfire", Map.of("error", "GemFire connection details not available"));
            }

        } catch (Exception e) {
            logger.error("Error comparing stores: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Get complete comparison of Postgres vs GemFire for a specific document
     */
    @GetMapping("/full-compare/{docId}")
    public Map<String, Object> fullCompare(@PathVariable String docId) {
        logger.info("Diagnostic: Full comparison for document {}", docId);

        Map<String, Object> result = new HashMap<>();
        result.put("documentId", docId);

        // Get Postgres data
        try {
            String sql = "SELECT id::text as id, content, metadata::text as metadata, " +
                        "embedding::text as embedding_text, " +
                        "array_length(embedding, 1) as vector_dimensions " +
                        "FROM vector_store WHERE id::text = ?";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, docId);

            if (rows.isEmpty()) {
                result.put("postgres_error", "Document not found");
            } else {
                Map<String, Object> row = rows.get(0);
                String embeddingText = (String) row.get("embedding_text");

                // Parse first 10 dimensions
                String vectorPreview = "[]";
                if (embeddingText != null && embeddingText.length() > 2) {
                    String cleaned = embeddingText.substring(1);
                    int endIdx = cleaned.indexOf(']');
                    if (endIdx > 0) {
                        cleaned = cleaned.substring(0, endIdx);
                    }
                    String[] dims = cleaned.split(",");
                    vectorPreview = java.util.Arrays.stream(dims).limit(10)
                        .collect(java.util.stream.Collectors.joining(", ", "[", ", ...]"));
                }

                Map<String, Object> pgData = new HashMap<>();
                pgData.put("id", row.get("id"));
                pgData.put("content", row.get("content"));
                pgData.put("content_length", row.get("content") != null ? row.get("content").toString().length() : 0);
                pgData.put("metadata", row.get("metadata"));
                pgData.put("vector_dimensions", row.get("vector_dimensions"));
                pgData.put("vector_preview", vectorPreview);
                result.put("postgres", pgData);
            }
        } catch (Exception e) {
            result.put("postgres_error", e.getMessage());
            logger.error("Error getting Postgres data: {}", e.getMessage(), e);
        }

        // Get GemFire data
        String gemfireUrl = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_HOST");
        String gemfirePort = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_PORT");
        String gemfireUsername = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_USERNAME");
        String gemfirePassword = System.getenv("SPRING_AI_VECTORSTORE_GEMFIRE_PASSWORD");

        if (gemfireUrl != null && gemfireUsername != null) {
            try {
                org.springframework.web.reactive.function.client.WebClient webClient = webClientBuilder
                    .baseUrl("https://" + gemfireUrl + ":" + (gemfirePort != null ? gemfirePort : "443"))
                    .defaultHeaders(headers -> {
                        String auth = gemfireUsername + ":" + gemfirePassword;
                        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                        headers.set("Authorization", "Basic " + encodedAuth);
                    })
                    .build();

                String gemfireResponse = webClient.get()
                    .uri("/gemfire-vectordb/v1/indexes/spring-ai-gemfire-index/embeddings/" + docId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

                if (gemfireResponse != null) {
                    Map<String, Object> gemfireDoc = parseJsonToMap(gemfireResponse);
                    Map<String, Object> gfData = new HashMap<>();
                    gfData.put("key", gemfireDoc.get("key"));
                    gfData.put("metadata", gemfireDoc.get("metadata"));
                    gfData.put("vector_length", gemfireDoc.get("vector") != null ?
                        ((java.util.List<?>) gemfireDoc.get("vector")).size() : 0);
                    gfData.put("vector_preview", gemfireDoc.get("vector") != null ?
                        ((java.util.List<?>) gemfireDoc.get("vector")).subList(0,
                            Math.min(10, ((java.util.List<?>) gemfireDoc.get("vector")).size())) : "[]");
                    result.put("gemfire", gfData);
                } else {
                    result.put("gemfire_error", "Document not found in cache");
                }
            } catch (Exception e) {
                result.put("gemfire_error", e.getMessage());
                logger.error("Error getting GemFire data: {}", e.getMessage(), e);
            }
        } else {
            result.put("gemfire_error", "GemFire credentials not available (env vars not accessible)");
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("parse_error", e.getMessage());
            return errorMap;
        }
    }
}
