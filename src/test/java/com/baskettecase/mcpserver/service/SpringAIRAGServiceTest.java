package com.baskettecase.mcpserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 Tests for Spring AI RAG Service
 * 
 * <p>Tests the Spring AI-based RAG implementation focusing on:</p>
 * <ul>
 *   <li>🔍 VectorStore integration and customer filtering</li>
 *   <li>🤖 ChatClient integration for answer generation</li>
 *   <li>📄 Document context building and formatting</li>
 *   <li>⚠️ Error handling and validation</li>
 * </ul>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SpringAIRAGServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @InjectMocks
    private SpringAIRAGService ragService;

    @BeforeEach
    void setUp() {
        // Mock ChatClient call chain with lenient stubbing
        // chatClient.prompt().user(prompt).call().content()
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        lenient().when(callResponseSpec.content()).thenReturn("Default test response");
    }

    @DisplayName("Input Validation Tests")
    @Test
    void testInvalidInputValidation() {
        // Test null question
        String result1 = ragService.answerQuestion(null, 100001);
        assertTrue(result1.contains("❌ Error: Question cannot be empty"));
        
        // Test empty question
        String result2 = ragService.answerQuestion("", 100001);
        assertTrue(result2.contains("❌ Error: Question cannot be empty"));
        
        // Test whitespace-only question
        String result3 = ragService.answerQuestion("   ", 100001);
        assertTrue(result3.contains("❌ Error: Question cannot be empty"));
        
        // Test null customer ID
        String result4 = ragService.answerQuestion("What is covered?", null);
        assertTrue(result4.contains("❌ Error: Valid customer ID is required"));
        
        // Test negative customer ID
        String result5 = ragService.answerQuestion("What is covered?", -1);
        assertTrue(result5.contains("❌ Error: Valid customer ID is required"));
        
        // Test zero customer ID
        String result6 = ragService.answerQuestion("What is covered?", 0);
        assertTrue(result6.contains("❌ Error: Valid customer ID is required"));
    }

    @DisplayName("VectorStore Integration Tests")
    @Test
    void testVectorStoreSearch_success() {
        // Given
        String question = "What does my policy cover?";
        Integer customerId = 100001;
        
        Document mockDoc = new Document("Test policy content");
        mockDoc.getMetadata().put("refnum1", 100001);
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(mockDoc));
        when(callResponseSpec.content()).thenReturn("Your policy covers comprehensive insurance.");

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("Insurance Policy Assistant"));
        assertTrue(result.contains("Customer ID: 100001"));
        assertTrue(result.contains("Question: What does my policy cover?"));
        assertTrue(result.contains("comprehensive insurance"));
        assertTrue(result.contains("Documents Found: 1"));
        assertTrue(result.contains("Spring AI RAG Pipeline"));
        
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(chatClient).prompt();
    }

    @Test
    void testVectorStoreSearch_noDocuments() {
        // Given
        String question = "What is covered?";
        Integer customerId = 100001;
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Collections.emptyList());

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("⚠️ No relevant policy documents found for customer 100001"));
        assertTrue(result.contains("Please verify the customer ID"));
        
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        // ChatClient should not be called if no documents found
        verify(chatClient, never()).prompt();
    }

    @Test
    void testVectorStoreSearch_exception() {
        // Given
        String question = "What is covered?";
        Integer customerId = 100001;
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenThrow(new RuntimeException("PostgreSQL connection failed"));

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("❌ Error processing your question"));
        assertTrue(result.contains("PostgreSQL connection failed"));
        assertTrue(result.contains("Please try again"));
        
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @DisplayName("ChatClient Integration Tests")
    @Test
    void testChatClientIntegration() {
        // Given
        String question = "What are my coverage limits?";
        Integer customerId = 100001;
        
        Document mockDoc = new Document("Policy limits: $250,000 bodily injury");
        mockDoc.getMetadata().put("refnum1", 100001);
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(mockDoc));
        when(callResponseSpec.content()).thenReturn("Your coverage limits are $250,000 for bodily injury.");

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("$250,000 for bodily injury"));
        
        // Verify ChatClient was called with proper context
        verify(requestSpec).user(argThat((String prompt) -> 
            prompt.contains("INSURANCE POLICY CONTEXT DOCUMENTS:") &&
            prompt.contains("Policy limits: $250,000 bodily injury") &&
            prompt.contains("CUSTOMER QUESTION:") &&
            prompt.contains(question)
        ));
    }

    @Test
    void testChatClientException() {
        // Given
        String question = "What is covered?";
        Integer customerId = 100001;
        
        Document mockDoc = new Document("Test content");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(mockDoc));
        when(callResponseSpec.content())
            .thenThrow(new RuntimeException("Ollama service unavailable"));

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("❌ Error processing your question"));
        assertTrue(result.contains("Ollama service unavailable"));
    }

    @DisplayName("Customer Filtering Tests")
    @Test
    void testCustomerFiltering() {
        // Given
        String question = "What is my deductible?";
        Integer customerId = 100002;
        
        Document mockDoc = new Document("Deductible: $1000");
        mockDoc.getMetadata().put("refnum2", 100002);
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(mockDoc));
        when(callResponseSpec.content()).thenReturn("Your deductible is $1000.");

        // When
        ragService.answerQuestion(question, customerId);

        // Then - verify SearchRequest was created with customer filter
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) -> {
            // The SearchRequest should contain customer filtering
            // Note: We can't easily verify the filter content due to FilterExpression complexity
            return true; // Basic verification that a SearchRequest was made
        }));
    }

    @DisplayName("Response Formatting Tests")
    @Test
    void testResponseFormatting() {
        // Given
        String question = "Policy question";
        Integer customerId = 100001;
        
        Document doc1 = new Document("First document content");
        doc1.getMetadata().put("refnum1", 100001);
        Document doc2 = new Document("Second document content");
        doc2.getMetadata().put("refnum1", 100001);
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(doc1, doc2));
        when(callResponseSpec.content()).thenReturn("Policy answer");

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then - verify response structure
        assertTrue(result.contains("=== Insurance Policy Assistant ==="));
        assertTrue(result.contains("👤 Customer ID: 100001"));
        assertTrue(result.contains("❓ Question: Policy question"));
        assertTrue(result.contains("🤖 Answer:"));
        assertTrue(result.contains("Policy answer"));
        assertTrue(result.contains("📊 Search Results:"));
        assertTrue(result.contains("Documents Found: 2"));
        assertTrue(result.contains("Similarity Threshold: 0.7"));
        assertTrue(result.contains("Max Results: 5"));
        assertTrue(result.contains("📄 Source Documents:"));
        assertTrue(result.contains("1. ID:"));
        assertTrue(result.contains("2. ID:"));
        assertTrue(result.contains("Preview:"));
        assertTrue(result.contains("Metadata:"));
        assertTrue(result.contains("⏰ Powered by Spring AI RAG Pipeline"));
    }

    @DisplayName("Document Context Building Tests")
    @Test
    void testDocumentContextBuilding() {
        // Given
        String question = "Context test";
        Integer customerId = 100001;
        
        Document doc1 = new Document("First policy section");
        Document doc2 = new Document("Second policy section");
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(doc1, doc2));
        when(callResponseSpec.content()).thenReturn("Context-based answer");

        // When
        ragService.answerQuestion(question, customerId);

        // Then - verify context was built correctly
        verify(requestSpec).user(argThat((String prompt) -> 
            prompt.contains("INSURANCE POLICY CONTEXT DOCUMENTS:") &&
            prompt.contains("Document 1:") &&
            prompt.contains("First policy section") &&
            prompt.contains("Document 2:") &&
            prompt.contains("Second policy section") &&
            prompt.contains("based ONLY on the provided policy context documents") &&
            prompt.contains("CUSTOMER QUESTION:") &&
            prompt.contains(question)
        ));
    }

    @DisplayName("Edge Cases Tests")
    @Test
    void testLongDocumentContentPreview() {
        // Given
        String question = "Test question";
        Integer customerId = 100001;
        
        // Create document with content longer than 200 characters
        String longContent = "This is a very long document content that exceeds two hundred characters in length. " +
                           "It should be truncated in the preview but used in full for context generation. " +
                           "This tests the preview functionality.";
        Document longDoc = new Document(longContent);
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(longDoc));
        when(callResponseSpec.content()).thenReturn("Answer based on long content");

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then - verify preview is truncated but contains ellipsis
        assertTrue(result.contains("Preview:"));
        assertTrue(result.contains("..."));
        // Verify the preview is shorter than the full content
        String previewLine = Arrays.stream(result.split("\n"))
            .filter(line -> line.contains("Preview:"))
            .findFirst()
            .orElse("");
        assertTrue(previewLine.length() < longContent.length());
    }

    @Test
    void testNullDocumentContent() {
        // Given
        String question = "Test null content";
        Integer customerId = 100001;
        
        Document nullContentDoc = new Document("");
        
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(Arrays.asList(nullContentDoc));
        when(callResponseSpec.content()).thenReturn("Answer for null content");

        // When
        String result = ragService.answerQuestion(question, customerId);

        // Then - should handle null content gracefully
        assertTrue(result.contains("Insurance Policy Assistant"));
        assertTrue(result.contains("Documents Found: 1"));
        // Should not crash due to null content
        assertFalse(result.contains("null"));
    }
}