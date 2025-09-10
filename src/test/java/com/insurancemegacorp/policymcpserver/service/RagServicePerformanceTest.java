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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Performance tests for RagService to ensure acceptable response times.
 */
@ExtendWith(MockitoExtension.class)
@Disabled("VectorStore interface mocking issues with current JDK/Mockito setup")
class RagServicePerformanceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private QuestionAnswerAdvisor questionAnswerAdvisor;

    @InjectMocks
    private RagService ragService;

    @Test
    void testProcessQuery_PerformanceWithMultipleDocuments() {
        // Setup
        ReflectionTestUtils.setField(ragService, "topK", 10);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.7);

        String query = "What is covered under my auto insurance policy?";
        Integer customerId = 100003;

        // Create multiple mock documents to simulate realistic load
        List<Document> mockDocuments = Arrays.asList(
                new Document("1", generateLargeContent("Auto insurance covers collision"), Map.of("refnum1", 100003)),
                new Document("2", generateLargeContent("Comprehensive coverage includes"), Map.of("refnum1", 100003)),
                new Document("3", generateLargeContent("Liability protection covers"), Map.of("refnum1", 100003)),
                new Document("4", generateLargeContent("Deductible information states"), Map.of("refnum1", 100003)),
                new Document("5", generateLargeContent("Policy exclusions include"), Map.of("refnum1", 100003))
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockDocuments);

        // Execute and measure time
        long startTime = System.currentTimeMillis();
        String result = ragService.processQuery(query, customerId);
        long processingTime = System.currentTimeMillis() - startTime;

        // Verify performance (should complete in under 1 second for mocked data)
        assertTrue(processingTime < 1000, 
                   "Processing took " + processingTime + "ms, expected under 1000ms");
        
        // Verify result quality
        assertTrue(result.length() > 0, "Result should not be empty");
        assertTrue(result.contains("Document 1:"), "Result should contain document markers");
        
        System.out.println("Performance test completed in " + processingTime + "ms");
        System.out.println("Result length: " + result.length() + " characters");
    }

    @Test
    void testProcessQuery_PerformanceWithLargeContent() {
        // Setup
        ReflectionTestUtils.setField(ragService, "topK", 3);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.7);

        String query = "What are the policy terms?";
        Integer customerId = 100003;

        // Create documents with very large content
        String largeContent = generateVeryLargeContent();
        List<Document> mockDocuments = Arrays.asList(
                new Document("1", largeContent, Map.of("refnum1", 100003)),
                new Document("2", largeContent, Map.of("refnum1", 100003)),
                new Document("3", largeContent, Map.of("refnum1", 100003))
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockDocuments);

        // Execute and measure time
        long startTime = System.currentTimeMillis();
        String result = ragService.processQuery(query, customerId);
        long processingTime = System.currentTimeMillis() - startTime;

        // Verify performance (should handle large content efficiently)
        assertTrue(processingTime < 2000, 
                   "Processing large content took " + processingTime + "ms, expected under 2000ms");
        
        System.out.println("Large content test completed in " + processingTime + "ms");
        System.out.println("Result length: " + result.length() + " characters");
    }

    private String generateLargeContent(String prefix) {
        StringBuilder content = new StringBuilder(prefix);
        content.append(" ");
        
        // Add realistic policy content
        for (int i = 0; i < 10; i++) {
            content.append("This policy section ").append(i + 1)
                   .append(" provides important coverage details and terms. ")
                   .append("Please review all conditions and limitations carefully. ");
        }
        
        return content.toString();
    }

    private String generateVeryLargeContent() {
        StringBuilder content = new StringBuilder();
        
        // Generate ~5KB of content per document
        for (int i = 0; i < 50; i++) {
            content.append("Section ").append(i + 1).append(": ")
                   .append("This comprehensive auto insurance policy provides extensive coverage ")
                   .append("for collision, comprehensive, liability, and additional protection. ")
                   .append("Terms and conditions apply as specified in the policy documents. ")
                   .append("Deductibles, limits, and exclusions are clearly defined herein. ");
        }
        
        return content.toString();
    }
}
