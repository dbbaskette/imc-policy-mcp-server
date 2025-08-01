package com.baskettecase.mcpserver;

import com.baskettecase.mcpserver.ToolsService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 🚀 Insurance MegaCorp MCP Server Application
 * 
 * <p>This is the main Spring Boot application class for the Insurance MegaCorp Model Context Protocol (MCP) server.
 * It provides a comprehensive RAG-enabled server that integrates with Claude Desktop and other MCP clients.</p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>🔄 <strong>Dual Transport Support</strong>: STDIO (Claude Desktop) and SSE (Web clients)</li>
 *   <li>🤖 <strong>AI Integration</strong>: Ollama models for chat and embedding operations</li>
 *   <li>🗄️ <strong>Database Layer</strong>: PostgreSQL with PGVector for customer and policy data</li>
 *   <li>📊 <strong>Vector RAG</strong>: Semantic search capabilities for policy documents</li>
 *   <li>🛠️ <strong>MCP Tools</strong>: Insurance-specific tools for customer and policy operations</li>
 * </ul>
 * 
 * <h3>Architecture:</h3>
 * <p>Built on Spring AI 1.0.0 with auto-configuration for MCP server capabilities.
 * The application automatically discovers and registers {@code @Tool} annotated methods
 * as MCP tools available to connected clients.</p>
 * 
 * <h3>Configuration Profiles:</h3>
 * <ul>
 *   <li>🖥️ <strong>local-stdio</strong>: For Claude Desktop integration with TestContainers PostgreSQL</li>
 *   <li>🌐 <strong>local-sse</strong>: For web client development with H2 in-memory database</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <pre>
 * # Claude Desktop (STDIO)
 * java -Dspring.profiles.active=local-stdio -jar imc-policy-mcp-server.jar
 * 
 * # Web clients (SSE)
 * java -Dspring.profiles.active=local-sse -jar imc-policy-mcp-server.jar
 * </pre>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 * @see org.springframework.ai.tool.annotation.Tool
 * @see com.baskettecase.mcpserver.ToolsService
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.baskettecase")
public class McpServerApplication {

	/**
	 * Main entry point for the IMC Policy MCP Server.
	 * 
	 * <p>Starts the Spring Boot application with MCP server auto-configuration.
	 * The server will listen for MCP client connections based on the active profile:</p>
	 * <ul>
	 *   <li>STDIO profile: Communicates via standard input/output</li>
	 *   <li>SSE profile: Serves HTTP SSE endpoints on port 8080</li>
	 * </ul>
	 * 
	 * @param args command line arguments (typically empty for MCP servers)
	 */
	public static void main(String[] args) {
		SpringApplication.run(McpServerApplication.class, args);
	}

	/**
	 * Configures the MCP tool callback provider for automatic tool registration.
	 * 
	 * <p>This bean automatically discovers all {@code @Tool} annotated methods in the
	 * {@link ToolsService} and registers them as available MCP tools. Tools registered
	 * this way become immediately available to connected MCP clients.</p>
	 * 
	 * @param toolsService the service containing insurance-specific MCP tools
	 * @return configured tool callback provider for method-based tools
	 * @see org.springframework.ai.tool.method.MethodToolCallbackProvider
	 * @see com.baskettecase.mcpserver.ToolsService
	 */
	@Bean
	public ToolCallbackProvider toolsProvider(ToolsService toolsService) {
		return MethodToolCallbackProvider.builder().toolObjects(toolsService).build();
	}

}