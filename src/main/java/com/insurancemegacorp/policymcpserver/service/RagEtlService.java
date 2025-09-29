package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RagEtlService {

    private static final Logger logger = LoggerFactory.getLogger(RagEtlService.class);
    private static final int DEFAULT_CHUNK_SIZE = 500;  // Smaller chunks for better granularity
    private static final int DEFAULT_OVERLAP = 100;
    private static final int BATCH_SIZE = 50;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Processes PDF files from the specified directory and populates the vector store
     */
    public void processPdfFiles(String sourceDirectory) {
        logger.info("=== STARTING RAG ETL PIPELINE ===");
        logger.info("Source directory: {}", sourceDirectory);
        
        try {
            // Step 1: Validate source directory and find PDF files
            List<Path> pdfFiles = findPdfFiles(sourceDirectory);
            if (pdfFiles.isEmpty()) {
                logger.warn("No PDF files found in directory: {}", sourceDirectory);
                return;
            }
            logger.info("Found {} PDF files to process", pdfFiles.size());
            
            // Step 2: Clear existing vector store
            clearVectorStore();
            
            // Step 3: Process each PDF file
            List<Document> allDocuments = new ArrayList<>();
            for (Path pdfFile : pdfFiles) {
                logger.info("Processing PDF: {}", pdfFile.getFileName());
                List<Document> documents = processPdfFile(pdfFile);
                allDocuments.addAll(documents);
                logger.info("Extracted {} document chunks from {}", documents.size(), pdfFile.getFileName());
            }
            
            logger.info("Total document chunks extracted: {}", allDocuments.size());
            
            // Step 4: Add documents to vector store in batches
            if (!allDocuments.isEmpty()) {
                addDocumentsInBatches(allDocuments);
            }
            
            // Step 5: Verify results
            long finalCount = countVectorStoreDocuments();
            logger.info("=== RAG ETL PIPELINE COMPLETED SUCCESSFULLY ===");
            logger.info("Processed {} PDF files", pdfFiles.size());
            logger.info("Generated {} document chunks", allDocuments.size());
            logger.info("Vector store now contains {} documents", finalCount);
            
        } catch (Exception e) {
            logger.error("RAG ETL pipeline failed: {}", e.getMessage(), e);
            throw new RuntimeException("RAG ETL processing failed", e);
        }
    }

    /**
     * Finds all PDF files in the specified directory
     */
    private List<Path> findPdfFiles(String sourceDirectory) throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDirectory);
        }
        
        if (!Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source path is not a directory: " + sourceDirectory);
        }
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                .collect(Collectors.toList());
        }
    }

    /**
     * Processes a single PDF file using Tika and TokenTextSplitter
     */
    private List<Document> processPdfFile(Path pdfFile) throws IOException {
        logger.debug("Reading PDF file: {}", pdfFile);
        
        // Step 1: Read PDF using Tika
        TikaDocumentReader tikaReader = new TikaDocumentReader(new FileSystemResource(pdfFile));
        List<Document> documents = tikaReader.get();
        
        if (documents.isEmpty()) {
            logger.warn("No content extracted from PDF: {}", pdfFile);
            return new ArrayList<>();
        }
        
        logger.debug("Tika extracted {} documents from PDF", documents.size());
        
        // Step 2: Split documents into chunks using TokenTextSplitter
        TokenTextSplitter splitter = new TokenTextSplitter(
            DEFAULT_CHUNK_SIZE,  // defaultChunkSize (500 tokens)
            150,                 // minChunkSizeChars (smaller minimum)
            10,                  // minChunkLengthToEmbed
            10000,               // maxNumChunks (allow more chunks)
            true                 // keepSeparator
        );
        List<Document> allChunks = new ArrayList<>();
        
        for (Document document : documents) {
            List<Document> chunks = splitter.apply(List.of(document));
            
            // Add metadata to each chunk
            for (Document chunk : chunks) {
                // Parse refnum1 and refnum2 from filename (e.g., "100004-200004.pdf")
                String fileName = pdfFile.getFileName().toString();
                parseAndAddRefnumMetadata(chunk, fileName);
                
                // Add standard metadata
                chunk.getMetadata().put("timestamp", System.currentTimeMillis());
                chunk.getMetadata().put("sourcePath", pdfFile.toString());
                
                allChunks.add(chunk);
            }
        }
        
        logger.debug("Generated {} chunks from PDF: {}", allChunks.size(), pdfFile.getFileName());
        return allChunks;
    }

    /**
     * Adds documents to vector store in batches with progress logging
     */
    private void addDocumentsInBatches(List<Document> documents) {
        int totalDocuments = documents.size();
        int processedCount = 0;
        
        logger.info("Adding {} documents to vector store in batches...", totalDocuments);
        
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
        
        logger.info("Successfully added all {} documents to vector store", processedCount);
    }

    /**
     * Clears all documents from the vector store
     */
    private void clearVectorStore() {
        try {
            long beforeCount = countVectorStoreDocuments();
            logger.info("Clearing existing vector store ({} documents)...", beforeCount);
            
            jdbcTemplate.execute("TRUNCATE TABLE vector_store");
            
            long afterCount = countVectorStoreDocuments();
            logger.info("Vector store cleared (now contains {} documents)", afterCount);
            
        } catch (Exception e) {
            logger.error("Failed to clear vector store: {}", e.getMessage());
            throw new RuntimeException("Could not clear vector store", e);
        }
    }

    /**
     * Counts documents in the vector store
     */
    private long countVectorStoreDocuments() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            logger.warn("Could not count vector store documents: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Parses refnum1 and refnum2 from filename and adds to document metadata
     * Expected format: "100004-200004.pdf" -> refnum1=100004, refnum2=200004
     */
    private void parseAndAddRefnumMetadata(Document document, String fileName) {
        try {
            // Remove .pdf extension
            String baseName = fileName;
            if (fileName.toLowerCase().endsWith(".pdf")) {
                baseName = fileName.substring(0, fileName.length() - 4);
            }
            
            // Split on dash
            String[] parts = baseName.split("-");
            if (parts.length == 2) {
                try {
                    int refnum1 = Integer.parseInt(parts[0]);
                    int refnum2 = Integer.parseInt(parts[1]);
                    
                    document.getMetadata().put("refnum1", refnum1);
                    document.getMetadata().put("refnum2", refnum2);
                    
                    logger.debug("Parsed filename '{}' -> refnum1={}, refnum2={}", fileName, refnum1, refnum2);
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse refnum integers from filename '{}': {}", fileName, e.getMessage());
                }
            } else {
                logger.warn("Filename '{}' does not match expected pattern 'refnum1-refnum2.pdf'", fileName);
            }
        } catch (Exception e) {
            logger.warn("Error parsing refnum metadata from filename '{}': {}", fileName, e.getMessage());
        }
    }
}