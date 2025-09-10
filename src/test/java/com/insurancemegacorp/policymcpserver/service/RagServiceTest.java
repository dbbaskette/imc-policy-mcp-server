package com.insurancemegacorp.policymcpserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RagService.
 */
@ExtendWith(MockitoExtension.class)
@Disabled("VectorStore interface mocking issues with current JDK/Mockito setup")
class RagServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private QuestionAnswerAdvisor questionAnswerAdvisor;

    @InjectMocks
    private RagService ragService;

    @Test
    void testProcessQuery_Success() {
        // Setup
        ReflectionTestUtils.setField(ragService, "topK", 5);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.7);

        String query = "What is covered under my policy?";
        Integer customerId = 100003;

        // Mock documents
        Document doc1 = new Document("1", "Auto insurance covers collision and comprehensive damage.", Map.of("refnum1", 100003));
        Document doc2 = new Document("2", "Your policy includes liability coverage up to $100,000.", Map.of("refnum1", 100003));
        List<Document> mockDocuments = Arrays.asList(doc1, doc2);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockDocuments);

        // Execute
        String result = ragService.processQuery(query, customerId);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Auto insurance covers collision"));
        assertTrue(result.contains("liability coverage"));
        assertTrue(result.contains("Document 1:"));
        assertTrue(result.contains("Document 2:"));
        assertTrue(result.contains("---")); // Document separator
    }

    @Test
    void testProcessQuery_NoDocumentsFound() {
        // Setup
        String query = "What is my deductible?";
        Integer customerId = 999999; // Non-existent customer

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Arrays.asList());

        // Execute
        String result = ragService.processQuery(query, customerId);

        // Verify
        assertEquals("No relevant policy information found for this customer and query.", result);
    }

    @Test
    void testProcessQuery_NullQuery() {
        // Execute & Verify
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ragService.processQuery(null, 200003)
        );
        assertEquals("Query cannot be null or empty", exception.getMessage());
    }

    @Test
    void testProcessQuery_EmptyQuery() {
        // Execute & Verify
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ragService.processQuery("", 200003)
        );
        assertEquals("Query cannot be null or empty", exception.getMessage());
    }

    @Test
    void testProcessQuery_NullCustomerId() {
        // Execute & Verify
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ragService.processQuery("What is my coverage?", null)
        );
        assertEquals("Customer ID cannot be null", exception.getMessage());
    }

    @Test
    void testGetServiceInfo() {
        // Setup
        ReflectionTestUtils.setField(ragService, "topK", 5);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.7);

        // Execute
        RagService.RagServiceInfo info = ragService.getServiceInfo();

        // Verify
        assertNotNull(info);
        assertEquals(5, info.getTopK());
        assertEquals(0.7, info.getSimilarityThreshold(), 0.001);
        assertNotNull(info.getRetrieverType());
        assertNotNull(info.getAdvisorType());
    }

    @Test
    void testRagServiceInfo_ToString() {
        // Setup
        RagService.RagServiceInfo info = new RagService.RagServiceInfo(
                5, 0.7, "VectorStore", "QuestionAnswerAdvisor", true, false, 3
        );

        // Execute
        String result = info.toString();

        // Verify
        assertTrue(result.contains("topK=5"));
        assertTrue(result.contains("similarityThreshold=0.70"));
        assertTrue(result.contains("VectorStore"));
        assertTrue(result.contains("QuestionAnswerAdvisor"));
    }
}
