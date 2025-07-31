package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.model.Customer;
import com.baskettecase.mcpserver.model.Accident;
import com.baskettecase.mcpserver.repository.CustomerRepository;
import com.baskettecase.mcpserver.repository.AccidentRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ToolsService {

	private final CustomerRepository customerRepository;
	private final AccidentRepository accidentRepository;

	@Autowired
	public ToolsService(CustomerRepository customerRepository, AccidentRepository accidentRepository) {
		this.customerRepository = customerRepository;
		this.accidentRepository = accidentRepository;
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
	 * Query accident information by customer ID (driver ID)
	 * @param customerId The customer ID (driver ID) to search accidents for
	 * @return List of accidents associated with the customer
	 */
	@Tool(description = "Query accident information by customer ID. Returns all accidents where the customer was the driver.")
	public String queryAccidents(Integer customerId) {
		if (customerId == null) {
			return "Error: Customer ID cannot be null";
		}

		List<Accident> accidents = accidentRepository.findAccidentsByCustomerId(customerId);
		
		if (accidents.isEmpty()) {
			return "No accidents found for customer ID: " + customerId;
		}

		StringBuilder result = new StringBuilder();
		result.append(String.format("Found %d accident(s) for customer ID %d:\n\n", accidents.size(), customerId));
		
		for (int i = 0; i < accidents.size(); i++) {
			Accident accident = accidents.get(i);
			result.append(String.format("Accident #%d:\n", i + 1));
			result.append(String.format("  Accident ID: %d\n", accident.getAccidentId()));
			result.append(String.format("  Policy ID: %d\n", accident.getPolicyId()));
			result.append(String.format("  Vehicle ID: %d\n", accident.getVehicleId()));
			result.append(String.format("  Timestamp: %s\n", accident.getAccidentTimestamp()));
			result.append(String.format("  Location: %s, %s\n", accident.getLatitude(), accident.getLongitude()));
			result.append(String.format("  G-Force: %s\n", accident.getGForce()));
			result.append(String.format("  Description: %s\n", accident.getDescription()));
			
			if (i < accidents.size() - 1) {
				result.append("\n");
			}
		}
		
		return result.toString();
	}
}