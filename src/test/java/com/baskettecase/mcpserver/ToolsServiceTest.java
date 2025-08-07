package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.model.Customer;
import com.baskettecase.mcpserver.repository.CustomerRepository;
import com.baskettecase.mcpserver.service.SpringAIRAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolsServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private SpringAIRAGService ragService;

    private ToolsService toolsService;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService(customerRepository);
        // Manually set the RAG service for testing
        try {
            java.lang.reflect.Field ragField = ToolsService.class.getDeclaredField("ragService");
            ragField.setAccessible(true);
            ragField.set(toolsService, ragService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject RAG service for testing", e);
        }
    }

    @DisplayName("Customer Query Tests")
    @Test
    void testQueryCustomer_validCustomerId() {
        // Given
        Integer customerId = 100001;
        Customer mockCustomer = new Customer(customerId, "John", "Doe", "john.doe@email.com", 
            "555-0101", "123 Main St", "Atlanta", "GA", "30309");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(mockCustomer));

        // When
        String result = toolsService.queryCustomer(customerId);

        // Then
        assertTrue(result.contains("Customer ID: 100001"));
        assertTrue(result.contains("Name: John Doe"));
        assertTrue(result.contains("Email: john.doe@email.com"));
        assertTrue(result.contains("Phone: 555-0101"));
        assertTrue(result.contains("Address: 123 Main St, Atlanta, GA 30309"));
    }

    @Test
    void testQueryCustomer_customerNotFound() {
        // Given
        Integer customerId = 999999;
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        // When
        String result = toolsService.queryCustomer(customerId);

        // Then
        assertEquals("No customer found with ID: 999999", result);
    }

    @Test
    void testQueryCustomer_nullCustomerId() {
        // When
        String result = toolsService.queryCustomer(null);

        // Then
        assertEquals("Error: Customer ID cannot be null", result);
    }

    // @DisplayName("Database Connectivity Tests")
    // @Test
    // void testDatabase_withCustomers() {
    //     // Given
    //     List<Customer> mockCustomers = Arrays.asList(
    //         new Customer(100001, "John", "Doe", "john.doe@email.com", "555-0101", "123 Main St", "Atlanta", "GA", "30309"),
    //         new Customer(100002, "Jane", "Smith", "jane.smith@email.com", "555-0102", "456 Oak Ave", "Nashville", "TN", "37203")
    //     );
    //     when(customerRepository.count()).thenReturn(2L);
    //     when(customerRepository.findAll()).thenReturn(mockCustomers);

    //     // When
    //     String result = toolsService.testDatabase();

    //     // Then
    //     assertTrue(result.contains("Database connection successful"));
    //     assertTrue(result.contains("Total customers in database: 2"));
    //     assertTrue(result.contains("Customer ID: 100001"));
    //     assertTrue(result.contains("Name: John Doe"));
    //     assertTrue(result.contains("Customer ID: 100002"));
    //     assertTrue(result.contains("Name: Jane Smith"));
    //     assertTrue(result.contains("Database test completed successfully"));
    // }

    // @Test
    // void testDatabase_noCustomers() {
    //     // Given
    //     when(customerRepository.count()).thenReturn(0L);

    //     // When
    //     String result = toolsService.testDatabase();

    //     // Then
    //     assertTrue(result.contains("Database connection successful"));
    //     assertTrue(result.contains("Total customers in database: 0"));
    //     assertTrue(result.contains("Database test completed successfully"));
    // }

    @DisplayName("Spring AI RAG Answer Question Tests")
    @Test
    void testAnswerQuestion_success() {
        // Given
        String question = "What does my policy cover?";
        Integer customerId = 100001;
        
        String mockResponse = """
            === Insurance Policy Assistant ===
            
            👤 Customer ID: 100001
            ❓ Question: What does my policy cover?
            
            🤖 Answer:
            Your policy covers comprehensive, collision, and liability insurance.
            
            📊 Search Results:
              - Documents Found: 2
              - Similarity Threshold: 0.7
              - Max Results: 5
            
            📄 Source Documents:
              1. ID: doc1
                 Preview: Policy coverage details...
                 Metadata: {refnum1=100001}
            
            ⏰ Powered by Spring AI RAG Pipeline
            """;
        
        when(ragService.answerQuestion(question, customerId)).thenReturn(mockResponse);

        // When
        String result = toolsService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("Insurance Policy Assistant"));
        assertTrue(result.contains("Customer ID: 100001"));
        assertTrue(result.contains("Question: What does my policy cover?"));
        assertTrue(result.contains("comprehensive, collision, and liability"));
        assertTrue(result.contains("Documents Found: 2"));
        assertTrue(result.contains("Source Documents:"));
        assertTrue(result.contains("Spring AI RAG Pipeline"));
        
        verify(ragService).answerQuestion(question, customerId);
    }

    @Test
    void testAnswerQuestion_error() {
        // Given
        String question = "";
        Integer customerId = 100001;
        
        String mockResponse = "❌ Error: Question cannot be empty";
        
        when(ragService.answerQuestion(question, customerId)).thenReturn(mockResponse);

        // When
        String result = toolsService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("❌ Error: Question cannot be empty"));
        
        verify(ragService).answerQuestion(question, customerId);
    }

    @Test
    void testAnswerQuestion_ragServiceException() {
        // Given
        String question = "What is covered?";
        Integer customerId = 100001;
        
        String mockResponse = """
            ❌ Error processing your question: Vector store connection failed
            Please try again or contact support if the issue persists.
            """;
        
        when(ragService.answerQuestion(question, customerId)).thenReturn(mockResponse);

        // When
        String result = toolsService.answerQuestion(question, customerId);

        // Then
        assertTrue(result.contains("❌ Error processing your question"));
        assertTrue(result.contains("Vector store connection failed"));
        assertTrue(result.contains("Please try again"));
        
        verify(ragService).answerQuestion(question, customerId);
    }

}