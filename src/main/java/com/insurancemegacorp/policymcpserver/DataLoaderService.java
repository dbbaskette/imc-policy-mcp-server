package com.insurancemegacorp.policymcpserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Profile("local")
@ConditionalOnProperty(name = "app.data.load-sample-data", havingValue = "true", matchIfMissing = true)
public class DataLoaderService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoaderService.class);
    private static final int BATCH_SIZE = 100;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Integer expectedEmbeddingDimension = null;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting data ingestion check for local profile...");
        
        try {
            // Check if table exists and create if necessary
            ensureVectorStoreTableExists();
            
            long existingRecords = countExistingRecords();
            if (existingRecords > 0) {
                logger.info("Vector store already contains {} records. Skipping data load.", existingRecords);
                return;
            }
            
            logger.info("Vector store is empty. Proceeding with data load.");
            loadVectorStoreData();
            
            long newRecordCount = countExistingRecords();
            logger.info("Data ingestion complete. Vector store now contains {} records.", newRecordCount);

        } catch (Exception e) {
            logger.error("Failed during data loading process", e);
            throw e;
        }
    }

    private void ensureVectorStoreTableExists() {
        try {
            // Check if table exists
            jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            logger.debug("Vector store table already exists.");
            
            // Try to detect the expected embedding dimension
            detectEmbeddingDimension();
        } catch (Exception e) {
            logger.warn("Vector store table does not exist or is not accessible. This is expected on first run if schema.sql hasn't been executed yet.");
            // The table will be created by schema.sql or by the vector store configuration
        }
    }

    private void detectEmbeddingDimension() {
        try {
            // Query the column information to get the vector dimension
            String sql = "SELECT atttypmod FROM pg_attribute " +
                        "JOIN pg_class ON pg_attribute.attrelid = pg_class.oid " +
                        "WHERE pg_class.relname = 'vector_store' AND pg_attribute.attname = 'embedding'";
            
            List<Integer> results = jdbcTemplate.queryForList(sql, Integer.class);
            if (!results.isEmpty() && results.get(0) != null && results.get(0) > 0) {
                expectedEmbeddingDimension = results.get(0);
                logger.info("Detected expected embedding dimension: {}", expectedEmbeddingDimension);
            } else {
                logger.warn("Could not detect embedding dimension from database schema. Will validate against data.");
            }
        } catch (Exception e) {
            logger.debug("Could not detect embedding dimension: {}", e.getMessage());
            // This is not critical, we'll validate against the data
        }
    }

    private long countExistingRecords() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            logger.warn("Could not count existing records (table may not exist yet): {}", e.getMessage());
            return 0L;
        }
    }

    private void loadVectorStoreData() throws IOException, CsvException {
        Path csvPath = Paths.get("local_data/vector_store.csv");
        
        if (!Files.exists(csvPath)) {
            logger.warn("Vector store CSV file not found at: {}. Skipping data load.", csvPath);
            return;
        }

        logger.info("Loading vector store data with pre-computed embeddings from: {}", csvPath);
        
        try (CSVReader csvReader = new CSVReaderBuilder(new FileReader(csvPath.toFile()))
                .withSkipLines(1) // Skip header
                .build()) {
            
            List<String[]> allRows = csvReader.readAll();
            logger.info("Read {} rows from CSV file", allRows.size());
            
            List<Object[]> batchArgs = new ArrayList<>();
            int processedRows = 0;
            int skippedRows = 0;
            
            for (int i = 0; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                
                try {
                    Object[] parsedRow = parseAndValidateRow(row, i + 2); // +2 because of header and 0-based index
                    if (parsedRow != null) {
                        batchArgs.add(parsedRow);
                        processedRows++;
                        
                        // Process in batches to avoid memory issues
                        if (batchArgs.size() >= BATCH_SIZE) {
                            insertBatch(batchArgs);
                            batchArgs.clear();
                        }
                    } else {
                        skippedRows++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse row {}: {}", i + 2, e.getMessage());
                    skippedRows++;
                }
            }
            
            // Insert remaining records
            if (!batchArgs.isEmpty()) {
                insertBatch(batchArgs);
            }
            
            logger.info("Data loading complete. Processed: {}, Skipped: {}", processedRows, skippedRows);
            
            // Provide guidance if many records were skipped due to dimension mismatch
            if (skippedRows > processedRows && expectedEmbeddingDimension != null) {
                logger.error("DIMENSION MISMATCH DETECTED!");
                logger.error("Database expects {} dimensions, but CSV data contains different dimensions.", expectedEmbeddingDimension);
                logger.error("Solutions:");
                logger.error("1. Recreate the vector_store table with the correct dimension in schema.sql");
                logger.error("2. Or regenerate the CSV data with {}-dimensional embeddings", expectedEmbeddingDimension);
                logger.error("3. Or check your embedding model configuration");
            }
        }
    }

    private Object[] parseAndValidateRow(String[] row, int rowNumber) {
        if (row.length != 4) {
            logger.warn("Row {} has {} columns, expected 4. Skipping.", rowNumber, row.length);
            return null;
        }

        try {
            // Parse and validate ID
            String idStr = row[0].trim();
            if (idStr.isEmpty()) {
                logger.warn("Row {} has empty ID. Skipping.", rowNumber);
                return null;
            }
            UUID id = UUID.fromString(idStr);

            // Validate content
            String content = row[1];
            if (content == null || content.trim().isEmpty()) {
                logger.warn("Row {} has empty content. Skipping.", rowNumber);
                return null;
            }

            // Parse and validate metadata JSON
            String metadataStr = row[2];
            if (metadataStr == null || metadataStr.trim().isEmpty()) {
                logger.warn("Row {} has empty metadata. Skipping.", rowNumber);
                return null;
            }
            
            // Validate that metadata is valid JSON
            try {
                JsonNode metadataNode = objectMapper.readTree(metadataStr);
                // Ensure it's an object, not an array or primitive
                if (!metadataNode.isObject()) {
                    logger.warn("Row {} metadata is not a JSON object. Skipping.", rowNumber);
                    return null;
                }
            } catch (JsonProcessingException e) {
                logger.warn("Row {} has invalid JSON metadata: {}. Skipping.", rowNumber, e.getMessage());
                return null;
            }

            // Parse and validate embedding
            String embeddingStr = row[3];
            if (embeddingStr == null || embeddingStr.trim().isEmpty()) {
                logger.warn("Row {} has empty embedding. Skipping.", rowNumber);
                return null;
            }
            
            // Validate that embedding is a valid JSON array
            try {
                JsonNode embeddingNode = objectMapper.readTree(embeddingStr);
                if (!embeddingNode.isArray()) {
                    logger.warn("Row {} embedding is not a JSON array. Skipping.", rowNumber);
                    return null;
                }
                
                // Check that all elements are numbers
                for (JsonNode element : embeddingNode) {
                    if (!element.isNumber()) {
                        logger.warn("Row {} embedding contains non-numeric values. Skipping.", rowNumber);
                        return null;
                    }
                }
                
                int actualDimensions = embeddingNode.size();
                
                // Log embedding dimensionality for the first few records to understand the data
                if (rowNumber <= 5) {
                    logger.info("Row {} embedding has {} dimensions", rowNumber, actualDimensions);
                }
                
                // Validate against expected dimension if we know it
                if (expectedEmbeddingDimension != null) {
                    if (actualDimensions != expectedEmbeddingDimension) {
                        logger.warn("Row {} embedding has {} dimensions, but database expects {}. Skipping.", 
                                   rowNumber, actualDimensions, expectedEmbeddingDimension);
                        return null;
                    }
                } else {
                    // Validate that embedding has a reasonable number of dimensions
                    if (actualDimensions < 100 || actualDimensions > 2000) {
                        logger.warn("Row {} embedding has {} dimensions, which seems unusual. Skipping.", 
                                   rowNumber, actualDimensions);
                        return null;
                    }
                }
                
            } catch (JsonProcessingException e) {
                logger.warn("Row {} has invalid JSON embedding: {}. Skipping.", rowNumber, e.getMessage());
                return null;
            }

            return new Object[]{id, content, metadataStr, embeddingStr};

        } catch (IllegalArgumentException e) {
            logger.warn("Row {} has invalid UUID: {}. Skipping.", rowNumber, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error parsing row {}: {}. Skipping.", rowNumber, e.getMessage());
            return null;
        }
    }

    private void insertBatch(List<Object[]> batchArgs) {
        if (batchArgs.isEmpty()) {
            return;
        }

        logger.debug("Inserting batch of {} records...", batchArgs.size());
        
        String sql = "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?, ?, CAST(? AS jsonb), CAST(? AS vector))";
        
        try {
            int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
            int successCount = Arrays.stream(updateCounts).map(count -> count > 0 ? 1 : 0).sum();
            logger.debug("Successfully inserted {}/{} records in batch", successCount, batchArgs.size());
        } catch (Exception e) {
            logger.error("Failed to insert batch: {}", e.getMessage());
            // Try inserting records one by one to identify problematic records
            insertRecordsIndividually(sql, batchArgs);
        }
    }

    private void insertRecordsIndividually(String sql, List<Object[]> batchArgs) {
        logger.info("Attempting to insert {} records individually...", batchArgs.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 0; i < batchArgs.size(); i++) {
            try {
                jdbcTemplate.update(sql, batchArgs.get(i));
                successCount++;
            } catch (Exception e) {
                failureCount++;
                logger.warn("Failed to insert individual record {}: {}", i, e.getMessage());
            }
        }
        
        logger.info("Individual insert results: {} successful, {} failed", successCount, failureCount);
    }
}
