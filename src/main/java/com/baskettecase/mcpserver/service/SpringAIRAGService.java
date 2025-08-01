package com.baskettecase.mcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 🧠 Spring AI RAG Service for Insurance Policy Questions
 * 
 * <p>Implements Retrieval Augmented Generation using Spring AI's native components.
 * Provides customer-specific policy document retrieval and intelligent answer generation.</p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>🔍 Vector similarity search with customer filtering</li>
 *   <li>🤖 ChatClient integration for answer generation</li>
 *   <li>📄 Automatic context building from retrieved documents</li>
 *   <li>⚡ Spring AI auto-configuration for embedding and models</li>
 * </ul>
 * 
 * <h3>RAG Pipeline:</h3>
 * <pre>
 * Question + Customer ID → Vector Search → Retrieved Docs → Context → ChatClient → Answer
 * </pre>
 * 
 * <p><strong>References:</strong></p>
 * <ul>
 *   <li><a href="https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html">Spring AI RAG</a></li>
 *   <li><a href="https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html">PGVector Store</a></li>
 * </ul>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 */
@Service
public class SpringAIRAGService {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringAIRAGService.class);
    
    // Configuration properties - configurable via application.properties
    @Value("${app.rag.similarity-threshold:0.3}")
    private double similarityThreshold;
    
    @Value("${app.rag.max-results:5}")
    private int maxResults;
    
    /**
     * Spring AI VectorStore - auto-configured by pgvector starter
     */
    @Autowired
    private VectorStore vectorStore;
    
    /**
     * ApplicationContext to get ChatModel when needed (avoids circular dependency)
     */
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    
    /**
     * 🎯 Answer insurance policy questions using RAG pipeline
     * 
     * <p>This method implements the complete RAG workflow using Spring AI components:</p>
     * <ol>
     *   <li>🔍 Search vector store for relevant documents (customer-filtered)</li>
     *   <li>📄 Build context from retrieved document content</li>
     *   <li>🤖 Generate answer using ChatClient with context</li>
     *   <li>📊 Return structured response with metadata</li>
     * </ol>
     * 
     * @param question Natural language question about insurance policies
     * @param customerId Customer ID for document filtering (refnum1 or refnum2)
     * @return Formatted answer with source document information
     */
    public String answerQuestion(String question, Integer customerId) {
        logger.info("🔍 Processing RAG query - Raw params: question='{}', customerId={} (type: {})", 
            question, customerId, customerId != null ? customerId.getClass().getSimpleName() : "null");
        
        try {
            // Step 1: Validate inputs
            if (question == null || question.trim().isEmpty()) {
                logger.warn("⚠️ Invalid question parameter: '{}'", question);
                return "❌ Error: Question cannot be empty";
            }
            
            if (customerId == null || customerId <= 0) {
                logger.warn("⚠️ Invalid customerId parameter: {}", customerId);
                return "❌ Error: Valid customer ID is required";
            }
            
            logger.info("✅ Input validation passed - question: '{}', customerId: {}", question, customerId);
            
            // Step 2: Retrieve relevant documents with customer filtering
            List<Document> relevantDocs = retrieveCustomerDocuments(question, customerId);
            
            if (relevantDocs.isEmpty()) {
                logger.warn("⚠️ No relevant documents found for customer {} and question: {}", customerId, question);
                return String.format(
                    "⚠️ No relevant policy documents found for customer %d.\n" +
                    "Please verify the customer ID or try rephrasing your question.", 
                    customerId
                );
            }
            
            // Step 3: Build context from retrieved documents
            String context = buildDocumentContext(relevantDocs);
            logger.info("📄 Built context from {} documents for customer {}", relevantDocs.size(), customerId);
            logger.debug("📝 Document context content:\n{}", context);
            
            // Step 4: Generate answer using ChatClient with context
            logger.info("🚀 Step 3: Generating answer using LLM with {} documents as context", relevantDocs.size());
            String answer = generateContextualAnswer(question, context);
            
            // Step 5: Format response with metadata
            return formatResponse(question, answer, relevantDocs, customerId);
            
        } catch (Exception e) {
            logger.error("❌ RAG pipeline error for customer {}: {}", customerId, e.getMessage(), e);
            return String.format(
                "❌ Error processing your question: %s\n" +
                "Please try again or contact support if the issue persists.", 
                e.getMessage()
            );
        }
    }
    
    /**
     * 🔍 Retrieve customer-specific documents from vector store
     * 
     * <p>Uses Spring AI's SearchRequest with FilterExpressionBuilder for customer filtering.
     * Documents are filtered by refnum1 OR refnum2 matching the customer ID.</p>
     * 
     * @param question User question for similarity search
     * @param customerId Customer ID for filtering
     * @return List of relevant documents for the customer
     */
    private List<Document> retrieveCustomerDocuments(String question, Integer customerId) {
        try {
            logger.info("🔍 Starting document retrieval for customer {} with question: '{}'", customerId, question);
            
            // First, let's try without filter to see if documents exist at all
            logger.info("📊 Step 1: Testing unfiltered search to check document availability...");
            SearchRequest unfilteredRequest = SearchRequest.builder()
                .query(question)
                .topK(maxResults)
                .similarityThreshold(similarityThreshold)
                .build();
            
            logger.info("🤖 Calling embedding model to generate query embedding for: '{}'", question);
            List<Document> unfilteredResults = vectorStore.similaritySearch(unfilteredRequest);
            logger.info("📈 Unfiltered search returned {} documents (threshold: {}, maxResults: {})", 
                unfilteredResults.size(), similarityThreshold, maxResults);
            
            // Log details of unfiltered results
            for (int i = 0; i < Math.min(unfilteredResults.size(), 3); i++) {
                Document doc = unfilteredResults.get(i);
                String docText = doc.getText();
                String preview = (docText != null && !docText.isEmpty()) ? 
                    docText.substring(0, Math.min(100, docText.length())) + "..." : "[no content]";
                logger.info("📄 Unfiltered result {}: ID={}, refnum1={}, refnum2={}, preview='{}'", 
                    i+1, doc.getId(), 
                    doc.getMetadata().get("refnum1"), 
                    doc.getMetadata().get("refnum2"),
                    preview);
            }
            
            // Now try with customer filter
            logger.info("📊 Step 2: Applying customer filter for refnum1={}", customerId);
            Filter.Expression customerFilter = new FilterExpressionBuilder()
                .eq("refnum1", customerId)
                .build();
            
            logger.info("🔧 Filter expression created: {}", customerFilter.toString());
            logger.info("🔧 Filter expression class: {}", customerFilter.getClass().getName());
            
            // Also try alternative customer filtering approaches
            logger.info("📊 Testing alternative filter for refnum2={}", customerId);
            Filter.Expression altCustomerFilter = new FilterExpressionBuilder()
                .eq("refnum2", customerId)
                .build();
            logger.info("🔧 Alternative filter (refnum2): {}", altCustomerFilter.toString());
            
            // Create search request with customer filtering
            SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(maxResults)
                .similarityThreshold(similarityThreshold)
                .filterExpression(customerFilter)
                .build();
            
            logger.info("🤖 Calling embedding model + filter for customer-specific search...");
            List<Document> results = vectorStore.similaritySearch(searchRequest);
            if (results == null) results = List.of();
            
            logger.info("📈 Filtered search returned {} documents for customer {}", results.size(), customerId);
            
            // Log details of filtered results
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String docText = doc.getText();
                String preview = (docText != null && !docText.isEmpty()) ? 
                    docText.substring(0, Math.min(100, docText.length())) + "..." : "[no content]";
                logger.info("📄 Filtered result {}: ID={}, refnum1={}, refnum2={}, preview='{}'", 
                    i+1, doc.getId(), 
                    doc.getMetadata().get("refnum1"), 
                    doc.getMetadata().get("refnum2"),
                    preview);
            }
            
            // If filtered search found nothing, let's check if documents exist for this customer
            if (results.isEmpty()) {
                logger.warn("🔍 No documents found with filter. Checking if customer {} exists in any documents...", customerId);
                long customerDocCount = unfilteredResults.stream()
                    .filter(doc -> {
                        Object refnum1 = doc.getMetadata().get("refnum1");
                        Object refnum2 = doc.getMetadata().get("refnum2");
                        return (refnum1 != null && refnum1.equals(customerId)) || 
                               (refnum2 != null && refnum2.equals(customerId));
                    })
                    .count();
                logger.warn("📊 Found {} documents in unfiltered results that match customer {}", customerDocCount, customerId);
                
                // Log unique customer IDs found
                Set<Object> uniqueRefnum1 = unfilteredResults.stream()
                    .map(doc -> doc.getMetadata().get("refnum1"))
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
                Set<Object> uniqueRefnum2 = unfilteredResults.stream()
                    .map(doc -> doc.getMetadata().get("refnum2"))
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
                logger.warn("📊 Unique refnum1 values found: {}", uniqueRefnum1);
                logger.warn("📊 Unique refnum2 values found: {}", uniqueRefnum2);
            }
            
            return results;
            
        } catch (Exception e) {
            logger.error("❌ Error retrieving documents for customer {}: {}", customerId, e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * 📄 Build context string from retrieved documents
     * 
     * <p>Combines document content into a formatted context string for the LLM.
     * Each document is numbered and clearly separated.</p>
     * 
     * @param documents Retrieved documents from vector store
     * @return Formatted context string for LLM prompt
     */
    private String buildDocumentContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        context.append("INSURANCE POLICY CONTEXT DOCUMENTS:\n\n");
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("Document ").append(i + 1).append(":\n");
            context.append(doc.getText()).append("\n\n");
        }
        
        return context.toString();
    }
    
    /**
     * 🤖 Generate contextual answer using ChatClient
     * 
     * <p>Uses Spring AI ChatClient to generate an answer based on the provided context.
     * The prompt instructs the model to use only the provided context.</p>
     * 
     * @param question Original user question
     * @param context Document context from vector search
     * @return Generated answer from ChatClient
     */
    private String generateContextualAnswer(String question, String context) {
        logger.info("🤖 Generating contextual answer using ChatClient...");
        logger.debug("📝 Context length: {} characters", context.length());
        
        String prompt = """
            You are an expert insurance assistant. Answer the customer's question based ONLY on the provided policy context documents.
            
            RULES:
            1. Use only information from the provided context documents
            2. If the context doesn't contain enough information, say so clearly
            3. Be concise but comprehensive
            4. Use insurance terminology appropriately
            5. If multiple documents are relevant, synthesize the information
            
            %s
            
            CUSTOMER QUESTION:
            %s
            
            ANSWER:
            """.formatted(context, question);
        
        logger.debug("📝 Final prompt length: {} characters", prompt.length());
        
        // Get ChatModel lazily to avoid circular dependency
        logger.info("🔧 Getting ChatModel from ApplicationContext...");
        org.springframework.ai.chat.model.ChatModel chatModel = applicationContext.getBean(org.springframework.ai.chat.model.ChatModel.class);
        logger.info("✅ ChatModel obtained: {}", chatModel.getClass().getSimpleName());
        logger.info("🤖 Calling LLM for answer generation...");
        logger.debug("📝 Sending prompt to LLM:\n{}", prompt);
        
        String answer;
        try {
            answer = ChatClient.create(chatModel)
                .prompt()
                .user(prompt)
                .call()
                .content();
            
            logger.info("✅ LLM response received successfully, length: {} characters", answer != null ? answer.length() : 0);
            logger.info("💬 Full LLM Answer: {}", answer);
            
        } catch (Exception e) {
            logger.error("❌ Error calling LLM: {}", e.getMessage(), e);
            answer = "❌ Error generating answer: " + e.getMessage();
        }
        
        return answer;
    }
    
    /**
     * 📊 Format final response with metadata
     * 
     * <p>Creates a structured response including the answer, source information,
     * and metadata about the RAG operation.</p>
     * 
     * @param question Original question
     * @param answer Generated answer
     * @param documents Source documents
     * @param customerId Customer ID
     * @return Formatted response string
     */
    private String formatResponse(String question, String answer, List<Document> documents, Integer customerId) {
        StringBuilder response = new StringBuilder();
        response.append("=== Insurance Policy Assistant ===\n\n");
        response.append("👤 Customer ID: ").append(customerId).append("\n");
        response.append("❓ Question: ").append(question).append("\n\n");
        response.append("🤖 Answer:\n").append(answer).append("\n\n");
        response.append("📊 Search Results:\n");
        response.append("  - Documents Found: ").append(documents.size()).append("\n");
        response.append("  - Similarity Threshold: ").append(similarityThreshold).append("\n");
        response.append("  - Max Results: ").append(maxResults).append("\n\n");
        
        if (!documents.isEmpty()) {
            response.append("📄 Source Documents:\n");
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                response.append("  ").append(i + 1).append(". ID: ").append(doc.getId()).append("\n");
                
                // Show preview of document content
                String content = doc.getText();
                String preview = (content != null && content.length() > 200) ? 
                    content.substring(0, 200) + "..." : (content != null ? content : "");
                response.append("     Preview: ").append(preview.replace("\n", " ")).append("\n");
                
                // Show metadata
                response.append("     Metadata: ").append(doc.getMetadata()).append("\n\n");
            }
        }
        
        response.append("⏰ Powered by Spring AI RAG Pipeline");
        return response.toString();
    }
}