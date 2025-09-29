package com.insurancemegacorp.policymcpserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ReEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(ReEmbeddingService.class);
    private static final String CSV_PATH = "local_data/vector_store.csv";
    private static final int BATCH_SIZE = 100;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Re-embeds all documents using the current embedding model configuration
     */
    public void reEmbedAllDocuments() {
        logger.info("=== STARTING RE-EMBEDDING PROCESS ===");
        
        try {
            // Step 1: Backup existing CSV
            String backupPath = backupExistingCSV();
            logger.info("Created backup: {}", backupPath);
            
            // Step 2: Read documents from CSV (content + metadata only)
            List<Document> documents = readDocumentsFromCSV();
            logger.info("Loaded {} documents from CSV", documents.size());
            
            if (documents.isEmpty()) {
                logger.warn("No documents found in CSV. Nothing to re-embed.");
                return;
            }
            
            // Step 3: Clear existing vector store
            logger.info("Clearing existing vector store...");
            clearVectorStore();
            logger.info("Vector store cleared");
            
            // Step 4: Add documents with new embeddings (in batches for progress tracking)
            logger.info("Re-embedding {} documents with current model...", documents.size());
            addDocumentsInBatches(documents);
            logger.info("Re-embedding completed successfully");
            
            // Step 5: Query vector store to get documents with embeddings and export to CSV
            logger.info("Retrieving documents with embeddings and exporting to CSV...");
            exportVectorStoreToCSV();
            logger.info("CSV export completed");
            
            logger.info("=== RE-EMBEDDING PROCESS COMPLETED SUCCESSFULLY ===");
            logger.info("Original data backed up to: {}", backupPath);
            logger.info("New embeddings saved to: {}", CSV_PATH);
            
        } catch (Exception e) {
            logger.error("Re-embedding process failed: {}", e.getMessage(), e);
            logger.error("PARTIAL STATE LEFT FOR DEBUGGING");
            logger.error("Check backup files and vector store state manually");
            throw new RuntimeException("Re-embedding failed", e);
        }
    }

    /**
     * Creates a backup of the existing CSV with .old.N versioning
     */
    private String backupExistingCSV() throws IOException {
        Path csvPath = Paths.get(CSV_PATH);
        
        if (!Files.exists(csvPath)) {
            logger.warn("No existing CSV found at: {}. Skipping backup.", CSV_PATH);
            return "No backup needed";
        }
        
        // Find the next available backup number
        int backupNumber = 1;
        Path backupPath;
        do {
            backupPath = Paths.get(CSV_PATH + ".old." + backupNumber);
            backupNumber++;
        } while (Files.exists(backupPath));
        
        Files.copy(csvPath, backupPath);
        logger.info("Backed up existing CSV to: {}", backupPath);
        
        return backupPath.toString();
    }

    /**
     * Reads documents from CSV, extracting only content and metadata (ignoring embeddings)
     */
    private List<Document> readDocumentsFromCSV() throws IOException, CsvException {
        Path csvPath = Paths.get(CSV_PATH);
        
        if (!Files.exists(csvPath)) {
            throw new IllegalStateException("CSV file not found: " + CSV_PATH);
        }
        
        List<Document> documents = new ArrayList<>();
        
        try (CSVReader csvReader = new CSVReaderBuilder(new FileReader(csvPath.toFile()))
                .withSkipLines(1) // Skip header
                .build()) {
            
            List<String[]> allRows = csvReader.readAll();
            logger.info("Reading {} rows from CSV", allRows.size());
            
            for (int i = 0; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                int rowNumber = i + 2; // +2 for header and 0-based index
                
                try {
                    Document document = parseRowToDocument(row, rowNumber);
                    if (document != null) {
                        documents.add(document);
                        
                        // Log progress every 100 documents
                        if (documents.size() % 100 == 0) {
                            logger.info("Parsed {} documents...", documents.size());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse row {}: {}. Skipping.", rowNumber, e.getMessage());
                }
            }
            
            logger.info("Successfully parsed {} documents from CSV", documents.size());
            return documents;
        }
    }

    /**
     * Parses a CSV row into a Document (content + metadata only, ignoring old embeddings)
     */
    private Document parseRowToDocument(String[] row, int rowNumber) {
        if (row.length != 4) {
            logger.warn("Row {} has {} columns, expected 4. Skipping.", rowNumber, row.length);
            return null;
        }
        
        try {
            // Extract content (column 1)
            String content = row[1];
            if (content == null || content.trim().isEmpty()) {
                logger.warn("Row {} has empty content. Skipping.", rowNumber);
                return null;
            }
            
            // Extract and parse metadata (column 2)
            String metadataStr = row[2];
            Map<String, Object> metadata = new HashMap<>();
            
            if (metadataStr != null && !metadataStr.trim().isEmpty()) {
                try {
                    JsonNode metadataNode = objectMapper.readTree(metadataStr);
                    if (metadataNode.isObject()) {
                        metadataNode.fields().forEachRemaining(entry -> {
                            String key = entry.getKey();
                            JsonNode value = entry.getValue();
                            
                            // Convert JsonNode to appropriate Java type
                            if (value.isTextual()) {
                                metadata.put(key, value.asText());
                            } else if (value.isNumber()) {
                                if (value.isInt()) {
                                    metadata.put(key, value.asInt());
                                } else {
                                    metadata.put(key, value.asDouble());
                                }
                            } else if (value.isBoolean()) {
                                metadata.put(key, value.asBoolean());
                            } else {
                                metadata.put(key, value.toString());
                            }
                        });
                    }
                } catch (JsonProcessingException e) {
                    logger.warn("Row {} has invalid JSON metadata: {}. Using empty metadata.", rowNumber, e.getMessage());
                }
            }
            
            // Create Document with content and metadata (Spring AI will generate new ID and embedding)
            return new Document(content, metadata);
            
        } catch (Exception e) {
            logger.warn("Unexpected error parsing row {}: {}. Skipping.", rowNumber, e.getMessage());
            return null;
        }
    }

    /**
     * Adds documents to vector store in batches with progress logging
     */
    private void addDocumentsInBatches(List<Document> documents) {
        int totalDocuments = documents.size();
        int processedCount = 0;
        
        for (int i = 0; i < totalDocuments; i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, totalDocuments);
            List<Document> batch = documents.subList(i, endIndex);
            
            logger.info("Processing batch {}/{} ({} documents)...", 
                       (i / BATCH_SIZE) + 1, 
                       (totalDocuments + BATCH_SIZE - 1) / BATCH_SIZE,
                       batch.size());
            
            try {
                vectorStore.add(batch);
                processedCount += batch.size();
                
                double progress = (processedCount * 100.0) / totalDocuments;
                logger.info("Progress: {}/{} documents ({:.1f}%)", processedCount, totalDocuments, progress);
                
            } catch (Exception e) {
                logger.error("Failed to process batch starting at index {}: {}", i, e.getMessage());
                throw new RuntimeException("Batch processing failed at document " + i, e);
            }
        }
        
        logger.info("All {} documents successfully added to vector store", processedCount);
    }

    /**
     * Exports all documents from the vector store (with embeddings) back to CSV format
     */
    private void exportVectorStoreToCSV() throws IOException {
        logger.info("Querying vector store and writing documents to CSV: {}", CSV_PATH);
        
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(CSV_PATH))) {
            // Write header
            String[] header = {"id", "content", "metadata", "embedding"};
            csvWriter.writeNext(header);
            
            // Query all documents from vector store
            String sql = "SELECT id, content, metadata, embedding FROM vector_store ORDER BY id";
            
            jdbcTemplate.query(sql, rs -> {
                try {
                    String[] row = new String[4];
                    
                    // Column 0: ID
                    row[0] = rs.getString("id");
                    
                    // Column 1: Content
                    row[1] = rs.getString("content");
                    
                    // Column 2: Metadata (already JSON)
                    row[2] = rs.getString("metadata");
                    
                    // Column 3: Embedding (convert from vector to JSON array)
                    String embeddingStr = rs.getString("embedding");
                    if (embeddingStr != null && !embeddingStr.trim().isEmpty()) {
                        // PostgreSQL vector format is like "[1.0,2.0,3.0]" - already JSON-like
                        row[3] = embeddingStr;
                    } else {
                        logger.warn("Document {} has no embedding", row[0]);
                        row[3] = "[]";
                    }
                    
                    csvWriter.writeNext(row);
                    
                } catch (Exception e) {
                    logger.error("Failed to export row: {}", e.getMessage());
                    throw new RuntimeException("CSV export failed", e);
                }
            });
            
            // Get final count
            Long totalCount = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            logger.info("Successfully exported {} documents to CSV", totalCount != null ? totalCount : 0);
        }
    }

    /**
     * Clears all documents from the vector store by truncating the table
     */
    private void clearVectorStore() {
        try {
            // Get count before deletion for logging
            Long beforeCount = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            logger.info("Vector store contains {} documents before clearing", beforeCount != null ? beforeCount : 0);
            
            // Truncate the table (faster than DELETE)
            jdbcTemplate.execute("TRUNCATE TABLE vector_store");
            
            // Verify it's empty
            Long afterCount = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            logger.info("Vector store contains {} documents after clearing", afterCount != null ? afterCount : 0);
            
        } catch (Exception e) {
            logger.error("Failed to clear vector store: {}", e.getMessage());
            throw new RuntimeException("Could not clear vector store", e);
        }
    }
}
