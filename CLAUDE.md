# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# Project Context

Refer to the user-provided 'PROJECT.md' file for specific project goals, tech stack, and architecture. That file is the primary source of truth for project-specific context.

---

# Core Development Guidelines

## Prime Directive: Safety and Accuracy
- **Your #1 rule is to not hallucinate.**
- You must only provide solutions when you have strong evidence, understand the full context, and are confident the answer is correct and safe.
- If context is missing or a request is ambiguous, you **must** pause and ask clarifying questions before proceeding.

## Interaction Workflow
1.  **Clarify:** Ask questions to resolve ambiguity before generating code.
2.  **Reason & Plan (Chain-of-Thought):** For any non-trivial request, you must use Chain-of-Thought (CoT) reasoning. Before producing code, outline a detailed, structured plan. This includes breaking down the problem, considering trade-offs, and identifying edge cases. For significant architectural design, complex integrations, or unfamiliar APIs, this reasoning must be comprehensive. **Wait for approval on the plan before generating code.**
3.  **Generate:** Create minimal, idiomatic code. Add comments only for complex logic.
4.  **Self-Correct:** Before finalizing your response, review your work against this checklist:
    *   Does the code meet all requirements?
    *   Is it idiomatic for the language and framework?
    *   Is it secure? (See Security section below).
    *   Does it include necessary tests?
    *   Are file paths and explanations clear?

## Output Formatting
- Use Markdown.
- Use language-tagged code blocks.
- When creating a new file, **always** state its full intended path.
- When modifying an existing file, present the changes in a `diff` format.
- When creating multiple files, show a file tree first, then each file's content.

## General Code Quality
- **Immutability:** Prefer immutable data structures and objects where practical.
- **Constants:** Avoid "magic strings" and "magic numbers." Use named constants.

## Security & Compliance
- Do not include secrets in examples.
- Redact creds and tokens.
- Flag insecure patterns and propose safe alternatives.

## Testing & Validation
- Provide unit or integration test examples for all new business logic.
- Include a simple command to run the tests (e.g., `mvn test`, `npm test`).

## Token & Output Efficiency
- **Always be mindful of token consumption and cost.** This is especially critical for: logs, responses from other LLMs, network requests, and CLI printouts.
- Minimize unnecessary output and avoid repetition. Keep responses, especially JSON or config files, tight and concise.
- Use logging (`log.debug`, `System.out.println`) judiciously and only where it adds significant value for debugging.
- When asked to summarize or explain, offer a "concise" and an "expanded" version if appropriate.
- If the request involves prompt engineering for another LLM, include advice on token budgeting.

---

# Spring Boot & MCP Server Guidelines

## Spring Boot Rules
1. Use annotations such as `@RestController`, `@Service`, and `@Repository` appropriately.
2. Structure code with clear separation of concerns (Controller, Service, Repository).
3. Use constructor injection (no field injection).
4. Prefer `ResponseEntity<?>` for REST responses and include relevant HTTP status codes.
5. Externalize configuration in `application.yml`. Show example snippets when adding new props.
6. Provide tests (JUnit + Testcontainers when DBs are involved).

## MCP Server Rules
1. Follow the client/tool semantics you are targeting; define clear tool schemas.
2. Keep handlers stateless and fast; enforce auth and rate limits.
3. Return structured JSON results and clear error objects.
4. Provide a local/dev runner and example client calls.
5. Add basic observability (request logs + latency counters).

---

# Project Overview

This is an Insurance MegaCorp (IMC) MCP (Model Context Protocol) Server for policy operations. The project provides utility tools and supports both STDIO and SSE (Server-Sent Events) transport modes.

## Tech Stack

- **Java 21** - Primary language
- **Spring Boot 3.5.3** - Application framework  
- **Spring AI 1.0.0** - AI integration framework with MCP support
- **Spring Data JPA** - Database access layer
- **H2 Database** - In-memory database for testing
- **PostgreSQL** - Production database support
- **Maven** - Build and dependency management
- **Base Package**: `com.baskettecase.mcpserver`

## Build and Development Commands

### Building the Project
```bash
mvn clean install
```

### Running Tests
```bash
mvn test
```

### Running the Server

**Using the Unified Test Script (Recommended)**:
```bash
# SSE mode with test database (default)
./test-mcp.sh --sse

# STDIO mode with test database (Claude Desktop compatible)
./test-mcp.sh --stdio

# Build and run
./test-mcp.sh --build --sse

# Test insurance query tools
./test-mcp.sh --stdio --test-tools

# Show all options
./test-mcp.sh --help
```

**Manual Execution**:
```bash
# SSE Mode with PostgreSQL test database
java -Dspring.profiles.active=local-sse -jar target/imc-policy-mcp-server-0.0.1-SNAPSHOT.jar

# STDIO Mode with PostgreSQL test database
java -Dspring.profiles.active=local-stdio -jar target/imc-policy-mcp-server-0.0.1-SNAPSHOT.jar

# Production modes (requires PostgreSQL setup)
java -Dspring.profiles.active=sse -jar target/imc-accident-mcp-server-0.0.1-SNAPSHOT.jar
java -Dspring.profiles.active=stdio -jar target/imc-accident-mcp-server-0.0.1-SNAPSHOT.jar
```

### Testing
- **Unit Tests**: 6 comprehensive tests for the insurance query tools using Mockito
- **Test Script**: `./test-mcp.sh` provides unified testing interface with database testing
- **Test Coverage**: Customer queries, accident queries, error handling, edge cases
- **Test Database**: H2 in-memory database with sample insurance data

## Architecture Overview

### Transport Layer Architecture
The application supports dual transport modes:
- **SSE Transport**: Uses Spring WebFlux with reactive endpoints for web-based MCP clients
- **STDIO Transport**: Uses standard input/output for command-line MCP clients

### Core Components

1. **McpServerApplication.java** - Main Spring Boot application class
   - Registers tool callbacks using `MethodToolCallbackProvider`
   - Entry point: `src/main/java/com/baskettecase/mcpserver/McpServerApplication.java:17`

2. **ToolsService.java** - Service implementing insurance query MCP tools
   - Uses `@Tool` annotation for automatic tool registration
   - Implements customer and accident database queries
   - Location: `src/main/java/com/baskettecase/mcpserver/ToolsService.java:15`

3. **Database Layer**
   - `Customer.java` - JPA entity for customer data
   - `Accident.java` - JPA entity for accident data with G-force measurements
   - `CustomerRepository.java` - Spring Data JPA repository
   - `AccidentRepository.java` - Spring Data JPA repository with custom queries

4. **Test Infrastructure**
   - `ToolsServiceTest.java` - Comprehensive unit tests (6 test cases) using Mockito
   - `test-mcp.sh` - Unified testing script with database initialization
   - `schema.sql` - H2 database schema for testing
   - `data.sql` - Sample insurance data for testing

### Configuration Strategy
- **Profile-Based**: Separate configuration files for each transport mode and environment
  - `application.properties` - Base configuration with default SSE profile
  - `application-sse.properties` - SSE transport configuration (production)
  - `application-stdio.properties` - STDIO transport configuration (production)
  - `application-local-sse.properties` - Local development with SSE transport and PostgreSQL
  - `application-local-stdio.properties` - Local development with STDIO transport and PostgreSQL
- **Key Properties**:
  - `spring.profiles.active` - Profile selection (sse/stdio/local-sse/local-stdio)
  - `spring.ai.mcp.server.stdio` - Transport mode toggle
  - Database configuration - PostgreSQL with Testcontainers for local development, PostgreSQL for production
  - JPA settings - Schema initialization and SQL logging for test profiles
  - Logging configuration - Critical for STDIO transport functionality

## Project Transformation Goals

**Completed Transformation Goals** (from PROJECT.md):

✅ **IMC Accident MCP Server**: Fully transformed to insurance-specific MCP server
✅ **Database Integration**: Complete Spring Data JPA with H2/PostgreSQL support
✅ **Insurance Tools**: Implemented customer and accident query tools
✅ **Test Database**: H2 in-memory database with sample insurance data
✅ **Profile Separation**: Test and production profile configurations
✅ **Testing Infrastructure**: Comprehensive unit tests with Mockito and database testing
✅ **Package Structure**: Maintained `com.baskettecase.mcpserver` structure

**Available Tools**:
- `queryCustomer` - Query customer information by customer ID (returns contact details and address)
- `queryAccidents` - Query accident information by customer ID (returns all accidents where customer was driver)

## Development Patterns

### Tool Registration
Tools are registered using the method-based pattern:
1. **Method-based**: Using `@Tool` annotation on service methods in `ToolsService`
2. **Database Integration**: Tools use Spring Data JPA repositories for data access
3. **Error Handling**: Comprehensive null checking and user-friendly error messages

### Spring AI Integration
- Uses `spring-ai-starter-mcp-server-webflux` starter dependency
- Automatic tool discovery through Spring's component scanning
- Integration with Spring AI BOM for version management

## Usage and Testing

### Quick Start
1. **Build**: `mvn clean install`
2. **Test**: `./test-mcp.sh --sse` or `./test-mcp.sh --stdio` (uses H2 test database)
3. **Test Tools**: `./test-mcp.sh --stdio --test-tools` (tests insurance query tools)
4. **Connect MCP Client**: 
   - SSE: `http://localhost:8080/mcp/message` (with H2 sample data)
   - STDIO: Use the running process input/output (compatible with Claude Desktop)
5. **Sample Queries**:
   - Query customer: `queryCustomer(100001)` - Returns John Doe's information
   - Query accidents: `queryAccidents(100001)` - Returns John Doe's accident history

### Important Configuration Notes
- **Test Profiles**: Default test script uses `-test` profiles with H2 database and sample data
- **Production Profiles**: Use `sse` or `stdio` profiles for PostgreSQL production database
- **STDIO Mode**: Banner and console logging automatically disabled for Claude Desktop compatibility
- **Database Initialization**: Test profiles automatically create schema and load sample insurance data
- **Profile Selection**: Use `--sse` or `--stdio` with test script, or set `spring.profiles.active`
- **Repository Configuration**: Uses Spring milestone repositories for Spring AI
- **Sample Data**: 5 customers and 10 accidents with realistic insurance data including G-force measurements