package com.baskettecase.mcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 📄 Policy Document Loader for Spring AI Vector Store
 * 
 * <p>Loads insurance policy documents from CSV into Spring AI's VectorStore.
 * Uses Spring AI's Document abstraction instead of custom entities.</p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>🔄 Converts CSV data to Spring AI Document objects</li>
 *   <li>👤 Preserves customer metadata for filtering</li>
 *   <li>🗄️ Uses VectorStore.add() for automatic embedding generation</li>
 *   <li>📊 Only runs on local development profiles</li>
 * </ul>
 * 
 * <h3>Data Flow:</h3>
 * <pre>
 * CSV File → Document Objects → VectorStore.add() → Auto Embedding → PostgreSQL
 * </pre>
 * 
 * <p><strong>Reference:</strong> 
 * <a href="https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html">
 * Spring AI VectorStore Documentation
 * </a></p>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 */
@Service
@Profile({"local-sse", "local-stdio"}) // Only load test data in local development profiles
public class PolicyDocumentLoader implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(PolicyDocumentLoader.class);
    
    /**
     * Spring AI VectorStore - auto-configured by spring-ai-starter-vector-store-pgvector
     */
    @Autowired
    private VectorStore vectorStore;
    
    /**
     * 🚀 Load policy documents on application startup
     * 
     * <p>This method is called automatically when the application starts.
     * It checks if documents already exist and loads test data if needed.</p>
     */
    @Override
    public void run(String... args) throws Exception {
        logger.info("📄 Checking vector store for existing policy documents...");
        
        // Check if we already have documents (basic existence check)
        try {
            // Try a simple similarity search to see if data exists
            // Note: Spring AI VectorStore doesn't have a direct count() method
            List<Document> testSearch = vectorStore.similaritySearch("test");
            if (!testSearch.isEmpty()) {
                logger.info("📊 Vector store already contains documents, skipping CSV load");
                return;
            }
        } catch (Exception e) {
            logger.debug("Vector store appears empty, proceeding with data load: {}", e.getMessage());
        }
        
        loadPolicyDocumentsFromCSV();
    }
    
    /**
     * 📥 Load policy documents from CSV file
     * 
     * <p>Reads the vector_store_testdata.csv file and converts each row to a Spring AI Document.
     * The Document includes the policy content and customer metadata for filtering.</p>
     * 
     * <h3>Document Structure:</h3>
     * <ul>
     *   <li><strong>ID:</strong> Unique document identifier</li>
     *   <li><strong>Content:</strong> Policy text content</li>
     *   <li><strong>Metadata:</strong> Customer filtering info (refnum1, refnum2)</li>
     * </ul>
     */
    private void loadPolicyDocumentsFromCSV() {
        try {
            logger.info("📥 Loading policy documents from vector_store_testdata.csv...");
            
            ClassPathResource resource = new ClassPathResource("vector_store_testdata.csv");
            List<Document> documents = new ArrayList<>();
            
            try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(resource.getInputStream()))
                    .withSkipLines(1) // Skip header row
                    .withMultilineLimit(-1) // No limit on multiline fields
                    .build()) {
                
                List<String[]> allRecords = csvReader.readAll();
                logger.info("📊 Found {} records in CSV file", allRecords.size());
                
                for (String[] record : allRecords) {
                    if (record.length < 4) {
                        logger.warn("⚠️ Invalid CSV record (expected 4+ fields, got {})", record.length);
                        continue;
                    }
                    
                    String documentId = cleanField(record[0]);
                    String content = cleanField(record[1]);
                    String metadataJson = cleanField(record[2]);
                    // record[3] is the embedding - Spring AI will generate this automatically
                    
                    // Parse metadata JSON to extract customer information
                    Map<String, Object> metadata = parseMetadata(metadataJson);
                    metadata.put("source", "vector_store_testdata.csv");
                    metadata.put("document_type", "insurance_policy");
                    
                    // Create Spring AI Document object
                    Document document = new Document(documentId, content, metadata);
                    documents.add(document);
                    
                    logger.debug("📄 Prepared document: {} (customer IDs: {}, {})", 
                        documentId, 
                        metadata.get("refnum1"), 
                        metadata.get("refnum2"));
                }
            }
            
            if (!documents.isEmpty()) {
                logger.info("🔄 Adding {} documents to vector store (Spring AI will generate embeddings)...", documents.size());
                
                // Use Spring AI VectorStore.add() - this automatically generates embeddings
                vectorStore.add(documents);
                
                logger.info("✅ Successfully loaded {} policy documents into vector store", documents.size());
                logger.info("🧠 Documents are now available for RAG queries with customer filtering");
            } else {
                logger.warn("⚠️ No valid documents found in CSV file");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to load policy documents from CSV: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 🧹 Clean CSV field by removing quotes and trimming whitespace
     */
    private String cleanField(String field) {
        if (field == null) return "";
        String cleaned = field.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
    
    /**
     * 📊 Parse metadata JSON string to Map
     * 
     * <p>Converts the metadata JSON string to a Map that Spring AI can use for filtering.
     * Handles both proper JSON format and {key=value} format from CSV.</p>
     * 
     * @param metadataJson JSON or key=value formatted metadata string
     * @return Map containing parsed metadata for Spring AI Document
     */
    private Map<String, Object> parseMetadata(String metadataJson) {
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            if (metadataJson == null || metadataJson.trim().isEmpty()) {
                return metadata;
            }
            
            String trimmed = metadataJson.trim();
            
            // Handle proper JSON format: {"refnum1":100001,"refnum2":200001}
            if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains(":")) {
                // Parse as JSON format
                String content = trimmed.substring(1, trimmed.length() - 1);
                String[] pairs = content.split(",");
                
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replace("\"", "");
                        String value = keyValue[1].trim().replace("\"", "");
                        
                        // Try to parse as number for customer IDs
                        try {
                            if (key.equals("refnum1") || key.equals("refnum2")) {
                                metadata.put(key, Integer.parseInt(value));
                            } else {
                                metadata.put(key, value);
                            }
                        } catch (NumberFormatException e) {
                            metadata.put(key, value);
                        }
                    }
                }
            }
            // Handle {key=value,key2=value2} format (legacy support)
            else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                String content = trimmed.substring(1, trimmed.length() - 1);
                String[] pairs = content.split(",");
                
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replace("\"", "");
                        String value = keyValue[1].trim().replace("\"", "");
                        
                        // Try to parse as number for customer IDs
                        try {
                            if (key.equals("refnum1") || key.equals("refnum2")) {
                                metadata.put(key, Integer.parseInt(value));
                            } else {
                                metadata.put(key, value);
                            }
                        } catch (NumberFormatException e) {
                            metadata.put(key, value);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("⚠️ Error parsing metadata '{}': {}", metadataJson, e.getMessage());
        }
        
        return metadata;
    }
}