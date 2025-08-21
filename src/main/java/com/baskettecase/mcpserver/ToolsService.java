package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.model.Customer;
import com.baskettecase.mcpserver.repository.CustomerRepository;
import com.baskettecase.mcpserver.service.SpringAIRAGService;
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
 *   <li>🧠 {@link #answerQuestion(String, Integer)} - Answer questions using RAG pipeline with policy documents</li>
 *   <li>📚 {@link #queryInformation(String)} - Query information documents for terms, legalese, and contract concepts</li>
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
	 * Spring AI RAG service for intelligent question answering with policy documents
	 */
	@Autowired
	private SpringAIRAGService ragService;

	/**
	 * Constructs a new ToolsService with the specified repository.
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
	@Tool(description = "Retrieves detailed information for a specific customer, including their name, contact details like email or phone, and address. Use this tool when asked to find, get, or look up a customer's data. Not data found within a policy")
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
	 * 🧠 Answer questions using Spring AI RAG pipeline
	 * <p>This tool uses Spring AI's native RAG capabilities to find relevant policy documents
	 * for the specified customer and generates intelligent answers using ChatClient.</p>
	 * 
	 * <h3>Spring AI Features:</h3>
	 * <ul>
	 *   <li>🔍 VectorStore with customer-specific filtering</li>
	 *   <li>📄 Automatic embedding generation and similarity search</li>
	 *   <li>🤖 ChatClient integration for answer generation</li>
	 *   <li>📊 Built-in context management and prompt engineering</li>
	 * </ul>
	 * <pre>Question → VectorStore Search → Context → ChatClient → Answer</pre>
	 * @param question The question to answer about insurance policies
	 * @param customerId The customer ID for document filtering (refnum1 or refnum2)
	 * @return Formatted answer with source document information
	 */
	@Tool(description = "MUST be used to answer ANY question about a user's specific insurance policy details, coverages, declarations, limits, or endorsements. This is the ONLY source for policy information. Also use for ALL follow-up questions (e.g., 'what about that?', 'does it include...', 'tell me more'), as the tool will use the conversation context to find the relevant information. for instnace, if you show policy information and they ask about something after that there is a great chance they are asking a follow up question. Do NOT use general knowledge for policy questions, unless it is asking about the meaning of a word or phrase.  You may interpret the language of the policy to help the user understand")
	public String answerQuestion(String question, Integer customerId) {
		return ragService.answerQuestion(question, customerId);
	}

	/**
	 * 📚 Query information documents for terms, legalese, and contract concepts
	 * <p>This tool uses Spring AI's RAG capabilities to find relevant information documents
	 * that explain insurance terms, legal concepts, and contract language. It filters results
	 * to only include documents with doctype=information in the metadata.</p>
	 * 
	 * <h3>Use Cases:</h3>
	 * <ul>
	 *   <li>🔍 Understanding insurance terminology and definitions</li>
	 *   <li>📖 Explaining legal concepts found in policy documents</li>
	 *   <li>💡 Clarifying contract language and clauses</li>
	 *   <li>🎯 Finding user-friendly explanations of complex terms</li>
	 * </ul>
	 * 
	 * @param question Question about insurance terms, legal concepts, or contract language
	 * @return Formatted answer with source information document details
	 */
	@Tool(description = "Retrieves user friendly information about terms, legalese, contract info that a user might find in a Policy Document, like What is an Act of God. This tool searches information documents (doctype=information) to provide clear explanations of insurance terminology, legal concepts, and contract language. Use this tool when returning information to a user so that it is clear and easy to understand.")
	public String queryInformation(String question) {
		return ragService.queryInformation(question);
	}

	/**
	 * Test basic database connectivity and tool registration
	 * @return Status of database connectivity and available customers
	 */
	// @Tool(description = "Test database connectivity and show available customer IDs for testing")
	// public String testDatabase() {
	// 	StringBuilder result = new StringBuilder();
	// 	result.append("=== Database Connectivity Test ===\n\n");
		
	// 	try {
	// 		long customerCount = customerRepository.count();
	// 		result.append("✅ Database connection successful\n");
	// 		result.append("📊 Total customers in database: ").append(customerCount).append("\n\n");
			
	// 		if (customerCount > 0) {
	// 			result.append("🔍 Sample customer IDs for testing:\n");
	// 			customerRepository.findAll().stream()
	// 				.limit(5)
	// 				.forEach(customer -> 
	// 					result.append("  - Customer ID: ").append(customer.getCustomerId())
	// 						.append(" (Name: ").append(customer.getFirstName())
	// 						.append(" ").append(customer.getLastName()).append(")\n")
	// 				);
	// 		}
			
	// 		result.append("\n✅ Database test completed successfully.");
	// 	} catch (Exception e) {
	// 		result.append("❌ Database Error: ").append(e.getMessage());
	// 	}
		
	// 	return result.toString();
	//}

}