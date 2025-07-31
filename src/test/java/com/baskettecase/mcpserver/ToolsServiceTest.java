package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.model.Customer;
import com.baskettecase.mcpserver.model.Accident;
import com.baskettecase.mcpserver.repository.CustomerRepository;
import com.baskettecase.mcpserver.repository.AccidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolsServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private AccidentRepository accidentRepository;

    private ToolsService toolsService;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService(customerRepository, accidentRepository);
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

    @DisplayName("Accident Query Tests")
    @Test
    void testQueryAccidents_validCustomerIdWithAccidents() {
        // Given
        Integer customerId = 100001;
        Accident accident1 = new Accident(1, 201, 301, customerId, 
            OffsetDateTime.parse("2024-03-15T14:30:00-05:00"),
            new BigDecimal("33.7490"), new BigDecimal("-84.3880"), 
            new BigDecimal("12.5"), "Rear-end collision at intersection");
        Accident accident2 = new Accident(2, 203, 303, customerId,
            OffsetDateTime.parse("2024-04-02T16:45:00-05:00"),
            new BigDecimal("33.4734"), new BigDecimal("-82.0105"),
            new BigDecimal("15.8"), "Side impact collision at four-way stop");
        
        List<Accident> mockAccidents = Arrays.asList(accident1, accident2);
        when(accidentRepository.findAccidentsByCustomerId(customerId)).thenReturn(mockAccidents);

        // When
        String result = toolsService.queryAccidents(customerId);

        // Then
        assertTrue(result.contains("Found 2 accident(s) for customer ID 100001"));
        assertTrue(result.contains("Accident #1:"));
        assertTrue(result.contains("Accident #2:"));
        assertTrue(result.contains("Accident ID: 1"));
        assertTrue(result.contains("Accident ID: 2"));
        assertTrue(result.contains("G-Force: 12.5"));
        assertTrue(result.contains("G-Force: 15.8"));
        assertTrue(result.contains("Rear-end collision at intersection"));
        assertTrue(result.contains("Side impact collision at four-way stop"));
    }

    @Test
    void testQueryAccidents_noAccidentsFound() {
        // Given
        Integer customerId = 100005;
        when(accidentRepository.findAccidentsByCustomerId(customerId)).thenReturn(Collections.emptyList());

        // When
        String result = toolsService.queryAccidents(customerId);

        // Then
        assertEquals("No accidents found for customer ID: 100005", result);
    }

    @Test
    void testQueryAccidents_nullCustomerId() {
        // When
        String result = toolsService.queryAccidents(null);

        // Then
        assertEquals("Error: Customer ID cannot be null", result);
    }

    @Test
    void testQueryAccidents_singleAccident() {
        // Given
        Integer customerId = 100002;
        Accident accident = new Accident(5, 205, 305, customerId,
            OffsetDateTime.parse("2024-04-22T19:30:00-05:00"),
            new BigDecimal("32.8407"), new BigDecimal("-83.6324"),
            new BigDecimal("6.9"), "Low-speed collision in residential area");
        
        List<Accident> mockAccidents = Collections.singletonList(accident);
        when(accidentRepository.findAccidentsByCustomerId(customerId)).thenReturn(mockAccidents);

        // When
        String result = toolsService.queryAccidents(customerId);

        // Then
        assertTrue(result.contains("Found 1 accident(s) for customer ID 100002"));
        assertTrue(result.contains("Accident #1:"));
        assertFalse(result.contains("Accident #2:"));
        assertTrue(result.contains("Accident ID: 5"));
        assertTrue(result.contains("G-Force: 6.9"));
        assertTrue(result.contains("Low-speed collision in residential area"));
    }
}