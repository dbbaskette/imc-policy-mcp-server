package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Direct cache warmer that fetches documents from Postgres by ID
 * and inserts them into GemFire VectorDB using authenticated REST API.
 *
 * This bypasses Spring AI's limitation by:
 * 1. Getting document IDs from cache misses
 * 2. Fetching full document data (including embeddings) from Postgres
 * 3. Inserting into GemFire with proper authentication
 */
@Component
public class DirectGemFireCacheWarmer {

    private static final Logger logger = LoggerFactory.getLogger(DirectGemFireCacheWarmer.class);

    private final JdbcTemplate jdbcTemplate;
    private final WebClient webClient;
    private final String indexName;
    private final String tableName;
    private final boolean enabled;
    private final VectorCacheMetrics metrics;

    public DirectGemFireCacheWarmer(
            JdbcTemplate jdbcTemplate,
            VectorCacheMetrics metrics,
            @Value("${spring.ai.vectorstore.gemfire.host:localhost}") String host,
            @Value("${spring.ai.vectorstore.gemfire.port:8080}") int port,
            @Value("${spring.ai.vectorstore.gemfire.username:}") String username,
            @Value("${spring.ai.vectorstore.gemfire.password:}") String password,
            @Value("${spring.ai.vectorstore.gemfire.index:spring-ai-gemfire-index}") String indexName,
            @Value("${app.vectorstore.table-name:vector_store}") String tableName) {

        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
        this.indexName = indexName;
        this.tableName = tableName;
        this.enabled = username != null && !username.isEmpty();

        if (enabled) {
            String protocol = (port == 443) ? "https" : "http";
            String baseUrl = String.format("%s://%s:%d/gemfire-vectordb/v1", protocol, host, port);

            logger.info("Initializing direct GemFire cache warmer: {} (user: {})", baseUrl, username);

            this.webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                    .build();
        } else {
            logger.warn("Direct GemFire cache warmer disabled - no credentials configured");
            this.webClient = null;
        }
    }

    /**
     * Warm the cache by fetching ALL documents for a customer from Postgres.
     * This is more efficient than similarity-based warming as it:
     * - Fetches all customer documents in one query (no vector similarity needed)
     * - Ensures complete cache coverage for the customer
     * - Provides better cache hit rate for all future customer queries
     *
     * @param customerId The customer ID to warm cache for
     * @return true if successful, false otherwise
     */
    public boolean warmCacheForCustomer(Integer customerId) {
        if (!enabled || customerId == null) {
            return false;
        }

        try {
            logger.info("Warming GemFire cache with ALL documents for customer {}", customerId);

            // Fetch ALL documents for this customer from Postgres (metadata filter only, no vector similarity)
            String sql = String.format(
                    "SELECT id, content, metadata, embedding FROM %s WHERE metadata->>'refnum1' = ?",
                    tableName
            );

            // Collect all embeddings to batch POST them together
            List<Map<String, Object>> allEmbeddings = new java.util.ArrayList<>();
            final int[] processedCount = {0};

            jdbcTemplate.query(sql, rs -> {
                try {
                    String id = rs.getString("id");
                    String content = rs.getString("content");
                    String metadataJson = rs.getString("metadata");

                    // Get embedding as array
                    Object embeddingObj = rs.getObject("embedding");
                    float[] embedding = pgVectorToFloatArray(embeddingObj);

                    // Parse metadata JSON and flatten it for GemFire filtering
                    Map<String, Object> gemfireMetadata = new java.util.HashMap<>();
                    gemfireMetadata.put("content", content);

                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsedMetadata = mapper.readValue(metadataJson, Map.class);

                        logger.debug("Parsed metadata for {}: keys={}", id, parsedMetadata.keySet());

                        // Add metadata fields at top level for filtering
                        // Convert all values to strings for GemFire filtering compatibility
                        for (Map.Entry<String, Object> entry : parsedMetadata.entrySet()) {
                            Object value = entry.getValue();
                            // Convert integers/numbers to strings for filtering
                            gemfireMetadata.put(entry.getKey(), value != null ? value.toString() : null);
                        }

                        logger.debug("Flattened metadata for {}: keys={}", id, gemfireMetadata.keySet());
                    } catch (Exception e) {
                        logger.error("Failed to parse metadata JSON for {}, storing as string: {}", id, e.getMessage(), e);
                        gemfireMetadata.put("metadata", metadataJson);
                    }

                    // Build GemFire embedding object - use HashMap instead of Map.of to allow modification
                    Map<String, Object> gemfireEmbedding = new java.util.HashMap<>();
                    gemfireEmbedding.put("key", id);
                    gemfireEmbedding.put("vector", embedding);
                    gemfireEmbedding.put("metadata", gemfireMetadata);

                    logger.debug("Prepared embedding for GemFire - metadata keys: {}", gemfireMetadata.keySet());
                    logger.debug("Metadata values for {}: refnum1={} (type: {}), refnum2={} (type: {})",
                            id,
                            gemfireMetadata.get("refnum1"),
                            gemfireMetadata.get("refnum1") != null ? gemfireMetadata.get("refnum1").getClass().getSimpleName() : "null",
                            gemfireMetadata.get("refnum2"),
                            gemfireMetadata.get("refnum2") != null ? gemfireMetadata.get("refnum2").getClass().getSimpleName() : "null");

                    // Add to batch list
                    allEmbeddings.add(gemfireEmbedding);
                    processedCount[0]++;

                } catch (Exception e) {
                    logger.warn("Failed to process document for customer {}: {}", customerId, e.getMessage());
                }
            }, customerId.toString());

            logger.info("Processed {} documents, now batch POSTing to GemFire", processedCount[0]);

            // Batch POST all embeddings at once
            int successCount = 0;
            if (!allEmbeddings.isEmpty()) {
                try {
                    logger.info("Posting batch of {} embeddings to GemFire index: {}", allEmbeddings.size(), indexName);

                    String response = webClient.post()
                            .uri("/indexes/{index}/embeddings", indexName)
                            .bodyValue(allEmbeddings)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    successCount = allEmbeddings.size();
                    logger.info("✅ Successfully warmed {} documents in GemFire cache (batch POST)", successCount);
                    logger.debug("GemFire batch response: {}", response);

                } catch (Exception e) {
                    logger.error("Failed to batch POST embeddings to GemFire: {}", e.getMessage(), e);
                }
            }

            logger.info("Cache warming completed for customer {}: {} documents warmed", customerId, successCount);

            // Record metrics
            if (successCount > 0) {
                metrics.recordCacheWarmingSuccess(successCount);
                logger.debug("Recorded warming success metrics: {} documents", successCount);
            } else {
                metrics.recordCacheWarmingFailure();
                logger.debug("Recorded warming failure metric");
            }

            return successCount > 0;

        } catch (Exception e) {
            logger.warn("Failed to warm GemFire cache for customer {}: {}", customerId, e.getMessage());
            logger.debug("Cache warming error details", e);
            metrics.recordCacheWarmingFailure();
            return false;
        }
    }

    /**
     * Warm the cache by fetching documents from Postgres and inserting into GemFire.
     * (Legacy method - kept for backward compatibility)
     *
     * @param documents Documents to add (we'll use their IDs to fetch from Postgres)
     * @return true if successful, false otherwise
     */
    public boolean warmCache(List<Document> documents) {
        if (!enabled || documents == null || documents.isEmpty()) {
            return false;
        }

        try {
            logger.debug("Warming GemFire cache with {} documents fetched from Postgres", documents.size());

            // For each document, fetch the full row from Postgres (including embedding)
            int successCount = 0;
            for (Document doc : documents) {
                try {
                    warmSingleDocument(doc.getId());
                    successCount++;
                } catch (Exception e) {
                    logger.warn("Failed to warm document {}: {}", doc.getId(), e.getMessage(), e);
                    // Continue with other documents
                }
            }

            logger.info("Cache warming completed: {}/{} documents successfully warmed", successCount, documents.size());

            // Record metrics
            if (successCount > 0) {
                metrics.recordCacheWarmingSuccess(successCount);
            } else {
                metrics.recordCacheWarmingFailure();
            }

            return successCount > 0;

        } catch (Exception e) {
            logger.warn("Failed to warm GemFire cache: {}", e.getMessage());
            logger.debug("Cache warming error details", e);
            metrics.recordCacheWarmingFailure();
            return false;
        }
    }

    /**
     * Fetch a single document from Postgres by ID and insert into GemFire.
     */
    private void warmSingleDocument(String documentId) {
        // Fetch the document from Postgres with embedding vector
        // Use PreparedStatement with proper type handling
        String sql = String.format(
                "SELECT id, content, metadata, embedding FROM %s WHERE CAST(id AS TEXT) = ?",
                tableName
        );

        jdbcTemplate.query(sql, rs -> {
            String id = rs.getString("id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");

            // Get embedding as array
            Object embeddingObj = rs.getObject("embedding");
            float[] embedding = pgVectorToFloatArray(embeddingObj);

            // Build GemFire embedding object
            Map<String, Object> gemfireEmbedding = Map.of(
                    "key", id,
                    "vector", embedding,
                    "metadata", Map.of(
                            "content", content,
                            "metadata", metadataJson
                    )
            );

            // POST to GemFire VectorDB
            logger.debug("Posting document {} to GemFire (vector length: {})", id, embedding.length);

            try {
                String response = webClient.post()
                        .uri("/indexes/{index}/embeddings", indexName)
                        .bodyValue(List.of(gemfireEmbedding))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                logger.debug("GemFire response for document {}: {}", id, response);
                logger.info("Successfully warmed document {} in GemFire cache", id);
            } catch (Exception e) {
                logger.error("Failed to POST document {} to GemFire: {}", id, e.getMessage(), e);
                throw e;
            }
        }, documentId);
    }

    /**
     * Convert PGVector object to float array.
     */
    private float[] pgVectorToFloatArray(Object pgVector) {
        if (pgVector == null) {
            return new float[0];
        }

        // PGvector returns vectors as String in format: "[1.0,2.0,3.0]"
        String vectorStr = pgVector.toString();
        vectorStr = vectorStr.substring(1, vectorStr.length() - 1); // Remove [ ]
        String[] parts = vectorStr.split(",");

        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * ADMIN: Recreate the GemFire index with filterable fields.
     * This will delete the existing index and create a new one with proper metadata field configuration.
     *
     * @return true if successful, false otherwise
     */
    public boolean recreateIndex() {
        if (!enabled) {
            logger.error("Cannot recreate index - cache warmer is disabled (no credentials)");
            return false;
        }

        logger.warn("ADMIN: Recreating GemFire index: {}", indexName);

        try {
            // Step 1: Delete existing index
            logger.info("Deleting existing index: {}", indexName);
            try {
                webClient.delete()
                        .uri("/indexes/{index}", indexName)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                logger.info("Index deleted successfully");
            } catch (Exception e) {
                logger.warn("Delete may have failed (index might not exist): {}", e.getMessage());
            }

            // Step 2: Create new index with filterable fields
            logger.info("Creating new index with filterable fields: {}", indexName);
            String createRequest = String.format("""
                {
                    "name": "%s",
                    "beam-width": 100,
                    "max-connections": 16,
                    "vector-similarity-function": "COSINE",
                    "fields": ["refnum1", "refnum2", "sourcePath", "timestamp"],
                    "buckets": 0
                }
                """, indexName);

            webClient.post()
                    .uri("/indexes")
                    .header("Content-Type", "application/json")
                    .bodyValue(createRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            logger.info("✅ GemFire index recreated successfully: {}", indexName);
            logger.info("Filterable fields: refnum1, refnum2, sourcePath, timestamp");
            return true;

        } catch (Exception e) {
            logger.error("Failed to recreate GemFire index: {}", e.getMessage(), e);
            return false;
        }
    }
}
