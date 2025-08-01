package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.model.Customer;
import com.baskettecase.mcpserver.repository.CustomerRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 🛠️ MCP Tools Service for Insurance MegaCorp
 * 
 * <p>This service provides Model Context Protocol (MCP) tools for insurance policy operations.
 * All methods annotated with {@code @Tool} are automatically registered as MCP tools and become
 * available to MCP clients like Claude Desktop.</p>
 * 
 * <h3>Available Tools:</h3>
 * <ul>
 *   <li>👤 {@link #queryCustomer(Integer)} - Retrieve customer information by ID</li>
 *   <li>🗄️ {@link #testDatabase()} - Test database connectivity and show sample data</li>
 * </ul>
 * 
 * <h3>Architecture:</h3>
 * <p>This service is designed to be stateless and uses Spring Data JPA repositories for
 * database access. It integrates seamlessly with Spring AI's MCP server auto-configuration.</p>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 * @see org.springframework.ai.tool.annotation.Tool
 * @see com.baskettecase.mcpserver.repository.CustomerRepository
 */
@Service
public class ToolsService {

	/**
	 * Repository for customer data access operations
	 */
	private final CustomerRepository customerRepository;

	/**
	 * Constructs a new ToolsService with the specified customer repository.
	 * 
	 * @param customerRepository the repository for customer data operations
	 */
	@Autowired
	public ToolsService(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	/**
	 * Query customer information by customer ID
	 * @param customerId The customer ID to search for
	 * @return Customer information including contact details and address
	 */
	@Tool(description = "Query customer information by customer ID. Returns customer contact details and address.")
	public String queryCustomer(Integer customerId) {
		if (customerId == null) {
			return "Error: Customer ID cannot be null";
		}

		Optional<Customer> customerOpt = customerRepository.findById(customerId);
		
		if (customerOpt.isEmpty()) {
			return "No customer found with ID: " + customerId;
		}

		Customer customer = customerOpt.get();
		return String.format("Customer ID: %d\nName: %s %s\nEmail: %s\nPhone: %s\nAddress: %s, %s, %s %s",
			customer.getCustomerId(),
			customer.getFirstName(),
			customer.getLastName(),
			customer.getEmail(),
			customer.getPhoneNumber(),
			customer.getAddress(),
			customer.getCity(),
			customer.getState(),
			customer.getZipCode());
	}

	/**
	 * Test basic database connectivity and tool registration
	 * @return Status of database connectivity and available customers
	 */
	@Tool(description = "Test database connectivity and show available customer IDs for testing")
	public String testDatabase() {
		StringBuilder result = new StringBuilder();
		result.append("=== Database Connectivity Test ===\n\n");
		
		try {
			long customerCount = customerRepository.count();
			result.append("✅ Database connection successful\n");
			result.append("📊 Total customers in database: ").append(customerCount).append("\n\n");
			
			if (customerCount > 0) {
				result.append("🔍 Sample customer IDs for testing:\n");
				customerRepository.findAll().stream()
					.limit(5)
					.forEach(customer -> 
						result.append("  - Customer ID: ").append(customer.getCustomerId())
							.append(" (Name: ").append(customer.getFirstName())
							.append(" ").append(customer.getLastName()).append(")\n")
					);
			}
			
			result.append("\n✅ Database test completed successfully.");
		} catch (Exception e) {
			result.append("❌ Database Error: ").append(e.getMessage());
		}
		
		return result.toString();
	}

}