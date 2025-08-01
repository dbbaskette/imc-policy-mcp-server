package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.model.Customer;
import com.baskettecase.mcpserver.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolsServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    private ToolsService toolsService;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService(customerRepository);
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

    @DisplayName("Database Connectivity Tests")
    @Test
    void testDatabase_withCustomers() {
        // Given
        List<Customer> mockCustomers = Arrays.asList(
            new Customer(100001, "John", "Doe", "john.doe@email.com", "555-0101", "123 Main St", "Atlanta", "GA", "30309"),
            new Customer(100002, "Jane", "Smith", "jane.smith@email.com", "555-0102", "456 Oak Ave", "Nashville", "TN", "37203")
        );
        when(customerRepository.count()).thenReturn(2L);
        when(customerRepository.findAll()).thenReturn(mockCustomers);

        // When
        String result = toolsService.testDatabase();

        // Then
        assertTrue(result.contains("Database connection successful"));
        assertTrue(result.contains("Total customers in database: 2"));
        assertTrue(result.contains("Customer ID: 100001"));
        assertTrue(result.contains("Name: John Doe"));
        assertTrue(result.contains("Customer ID: 100002"));
        assertTrue(result.contains("Name: Jane Smith"));
        assertTrue(result.contains("Database test completed successfully"));
    }

    @Test
    void testDatabase_noCustomers() {
        // Given
        when(customerRepository.count()).thenReturn(0L);

        // When
        String result = toolsService.testDatabase();

        // Then
        assertTrue(result.contains("Database connection successful"));
        assertTrue(result.contains("Total customers in database: 0"));
        assertTrue(result.contains("Database test completed successfully"));
    }

}