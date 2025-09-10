# Project Instructions: imc-policy-mcp-server

## 1. Project Overview & Goal

- **What is the primary goal of this project?**
    - The primary goal is to create a high-performance **MCP Tool Server**. This server will not be a standalone chat application, but will instead provide a set of "tools" to an external **MCP Client**. The main function of these tools is to answer questions about insurance policy documents by retrieving relevant information from a vector database.
- **Who are the end-users?**
    - The direct consumer is the **MCP Client** application. The end-users are customers of the insurance company who will interact with the MCP Client (a chat interface) to ask questions about their auto insurance policies.

## 2. Tech Stack

- **Language(s) & Version(s)**: Java 21
- **Framework(s)**: Spring Boot 3.5.5, Spring AI **1.1.0-M1**. (Note: This is a milestone release of Spring AI, which is required for its advanced MCP and RAG features. We will stick to this version).
- **Database(s)**: PostgreSQL with the PGVector extension.
- **Build/Package Manager**: Maven (with wrapper), Git.
- **Remote Git Repository**: `https://github.com/dbbaskette/imc-policy-mcp-server`

## 3. Architecture & Design

- **High-Level Architecture**: The application is an **MCP Tool Server**, built with Spring AI. It acts as a specialized backend service that exposes its capabilities (e.g., querying documents) as tools that can be called by an MCP Client. This server is a **retriever only**; it is responsible for accessing a pre-populated vector store.
    - [Spring AI MCP Overview](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-overview.html)
    - [Spring AI MCP Annotations & Examples](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-annotations-examples.html#_complete_application_examples)

- **RAG (Retrieval-Augmented Generation) Pipeline**: To simplify the implementation and leverage high-level Spring AI abstractions, the RAG pipeline will be implemented using a `QuestionAnswerAdvisor`. This approach encapsulates the logic for retrieval and response generation.
    1. A `QuestionAnswerAdvisor` bean will be configured for the application.
    2. The advisor will be wired with a `VectorStoreRetriever` that is configured to use a dynamic filter expression.
    3. The `answerQuery` tool will pass the user's query and `customerId` to the advisor.
    4. The advisor will use the filter expression to ensure the vector store search is scoped to the specific customer's documents (e.g., `metadata.customerId == [customerId]`).
    5. The retrieved documents will be automatically combined and returned as context to the MCP Client. This approach replaces the manual chain of transformers and joiners.
    - [Dynamic Filter Expressions Documentation](https://docs.spring.io/spring-ai/reference/1.1/api/retrieval-augmented-generation.html#_dynamic_filter_expressions)

- **API Design**: The server will use Spring AI's MCP framework to expose tools via `@McpTool` annotations.
    - **Primary Tool**: `answerQuery(query: String, customerId: Integer)` - This is the main function of the server. It will execute the RAG pipeline.
    - **Scaffolded Tool**: A `queryCustomer(customerId: Integer)` tool will be added to the code but commented out. It will serve as a placeholder for future functionality to retrieve customer data from a relational database.
    - Standard Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/info`) will be enabled for monitoring.

- **Database Schema**: The PostgreSQL database will contain a `vector_store` table with the following schema, which matches the `vector_store.csv` export.
    - `id`: `UUID` (Primary Key, Default: `uuid_generate_v4()`)
    - `content`: `TEXT`
    - `metadata`: `JSON`
    - `embedding`: `VECTOR(768)`

- **Directory Structure**:
    - `src/main/java/com/insurancemegacorp/policymcpserver/`: Main application source code.
    - `src/main/resources/`: Configuration files (`application.properties`, `schema.sql`, etc.).
    - `docs/`: Project documentation.
    - `local_data/`: Contains sample data for local development.

## 4. Coding Standards & Conventions

- **Code Style**: Google Java Style Guide.
- **Naming Conventions**:
    - Use `camelCase` for variables and methods.
    - Services should be suffixed with `Service`.
- **Error Handling**: The MCP server will report errors back to the client according to the MCP specification.

## 5. Local Development & Deployment

- **Local Setup**:
    - A `docker-compose.yml` file will be provided to run a local PostgreSQL instance with the PGVector extension enabled.
    - On application startup in the `local` profile, the server will automatically:
        1. Connect to the local database.
        2. Create the `vector_store` table if it doesn't exist, as defined in `src/main/resources/schema.sql`.
        3. Ingest and create embeddings for the data from `local_data/vector_store.csv` into the `vector_store` table.
    - A `.env.example` file will be provided to manage local environment variables (e.g., `OPENAI_API_KEY`, database credentials). You will need to copy this to a `.env` file (which is git-ignored) and fill in your details.

- **Cloud Foundry (CF) Deployment**:
    - The application will use a `cloud` Spring profile for CF deployment.
    - In the CF environment, the application will connect to a **pre-populated** PostgreSQL database service. No data ingestion will occur on startup.
    - The application will be configured to bind to cloud services for the embedding model and the chat model.

- **IMPORTANT DOCUMENTATION**
    - https://docs.spring.io/spring-ai/reference/1.1/index.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/chatmodel.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/embeddings.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/tools.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-overview.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-client-boot-starter-docs.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-server-boot-starter-docs.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-streamable-http-server-boot-starter-docs.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-annotations-overview.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-annotations-server.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-annotations-special-params.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-annotations-examples.html
    - https://docs.spring.io/spring-ai/reference/1.1/api/retrieval-augmented-generation.html


<!-- devplan:start -->
## Development Plan

### Phase 1: Project Setup & Configuration ✅ COMPLETE
- [x] Update `pom.xml` with Spring Boot 3.3.6, Java 21, and Spring AI 1.1.0-SNAPSHOT dependencies.
- [x] Create the base package structure: `com.insurancemegacorp.policymcpserver`.
- [x] Updated `pom.xml` with correct Spring AI artifact names and added OpenCSV dependency.
- [x] Create `application-local.properties` and `application-cloud.properties` for environment-specific configurations.
- [x] Create `docker-compose.yml` for a PostgreSQL container with the PGVector extension.
- [x] Create the `.env.example` file with placeholders for local development secrets.
- [x] Initialize a local Git repository and add a `.gitignore` file.

### Phase 2: Database and Data Ingestion ✅ COMPLETE
- [x] Create `src/main/resources/schema.sql` to define the `vector_store` table.
- [x] Implement a robust `DataLoaderService` with comprehensive CSV parsing, validation, and error handling.
- [x] Added dimension detection and mismatch handling for vector embeddings.
- [x] Configure the `VectorStore` bean to connect to the PGVector database.
- [x] Resolve embedding dimension mismatch (fixed schema.sql to use correct dimensions).

### Phase 3: Core RAG Service Implementation
#### 3.1 Configuration & Infrastructure ✅ COMPLETE
- [x] Create `RagConfiguration` class with `@Configuration` annotation
- [x] Configure `VectorStore` bean for PGVector connection
- [x] Configure `EmbeddingModel` bean with Cloud Foundry compatibility (`@ConditionalOnMissingBean`)
- [x] Create `VectorStoreRetriever` bean with metadata filtering support
- [x] Configure `QuestionAnswerAdvisor` bean with retriever and embedding model
- [x] Externalize all configuration values to properties/environment variables
- [x] Add Cloud Foundry bound service support for AI models

#### 3.2 RAG Service Implementation ✅ COMPLETE
- [x] Create `RagService` class with core query processing logic
- [x] Implement `processQuery(String query, Integer customerId)` method
- [x] Add dynamic filter expression for customerId: `metadata.refnum1 == [customerId]`
- [x] Implement error handling and logging for RAG operations
- [x] Add configuration properties for RAG parameters (top-k results, etc.)

#### 3.3 Integration & Testing ✅ COMPLETE
- [x] Create `RagTestController` to verify RAG functionality (local/dev profiles only)
- [x] Test query processing with sample customer IDs from CSV data
- [x] Validate that filtering works correctly (only returns docs for specific customer)
- [x] Test edge cases: invalid customer ID, no matching documents, null inputs
- [x] Performance testing: measure query response times with unit and performance tests

### Phase 4: MCP Tool Server Implementation
#### 4.1 MCP Tool Service Setup
- [ ] Create `McpToolService` class with `@Component` annotation
- [ ] Inject `RagService` dependency for query processing
- [ ] Configure MCP server properties in application.properties
- [ ] Add proper error handling and validation for MCP tool calls

#### 4.2 Core MCP Tools Implementation
- [ ] Implement `@McpTool` annotated `answerQuery(String query, Integer customerId)` method
  - [ ] Validate input parameters (non-empty query, valid customerId)
  - [ ] Call `RagService.processQuery()` to get document context
  - [ ] Return formatted response with retrieved context
  - [ ] Handle and log any errors appropriately
- [ ] Add comprehensive JavaDoc documentation for the tool

#### 4.3 Future Tools (Scaffolded)
- [ ] Create commented-out `@McpTool` for `queryCustomer(Integer customerId)` placeholder
- [ ] Add TODO comments explaining future functionality
- [ ] Document expected input/output format for future implementation

#### 4.4 MCP Server Testing
- [ ] Test MCP tool registration and discovery
- [ ] Verify tool can be called with correct parameters
- [ ] Test error handling with invalid inputs
- [ ] Validate response format matches MCP specification

### Phase 5: Finalization & Documentation
- [ ] Add unit tests for the `McpToolService` to verify the interaction with the `QuestionAnswerAdvisor`.
- [ ] Create a `README.md` file with detailed instructions for setting up the local environment and running the application.

<!-- devplan:end -->

## Current Status & Issues

### ✅ Completed (Phases 1 & 2)
- ✅ **Complete project setup**: Maven structure, dependencies, configurations
- ✅ **Database infrastructure**: Docker Compose, schema, data loading
- ✅ **Robust data ingestion**: CSV parsing with validation and error handling
- ✅ **Environment configuration**: Local and cloud application properties
- ✅ **Embedding dimension mismatch resolved**: Schema now matches CSV data

### 🔧 Current Focus: Phase 3 - RAG Service Implementation
**Next tasks to implement the core RAG functionality:**

### 🎯 Immediate Next Steps
1. **Create RAG Configuration class** with QuestionAnswerAdvisor setup
2. **Configure VectorStore retriever** with customerId filtering capability  
3. **Implement RAG service** to handle query processing pipeline
4. **Test RAG functionality** with sample queries
