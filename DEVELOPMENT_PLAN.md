# Development Plan: IMC Policy MCP Server

## Current State Analysis

**✅ Already Completed:**
- Basic Spring Boot 3.5.3 + Spring AI 1.0.0 MCP server infrastructure  
- Dual transport modes (SSE/STDIO) with profile-based configuration
- Database layer with JPA, H2 test database, PostgreSQL production support
- Customer query tool with database integration
- Comprehensive testing infrastructure with unit tests and test script

**🔄 Transformation Required:**
The current codebase is an insurance accident MCP server that needs to be transformed into a RAG-based policy MCP server with:
- **Keep existing Customer query tool** ✅
- **Remove Accident-related functionality** 
- **Add sophisticated RAG pipeline** with vector database, LLM integration, and natural language processing

---

## Development Plan: Incremental Steps

*Based on Spring AI 1.0.0 documentation references from PROJECT.md*

### Phase 1: Project Cleanup & Renaming  
**Goal:** Clean up accident-specific code while preserving Customer functionality

#### Issue 1: ✅ Project Artifact Renaming (COMPLETED)
- ✅ Update `pom.xml` - change from `imc-accident-mcp-server` to `imc-policy-mcp-server`

#### Issue 2: Remove Accident-Specific Code (Keep Customer) ✅
- ✅ Remove `Accident.java` entity class
- ✅ Remove `AccidentRepository.java` repository  
- ✅ Remove accident-related queries from `ToolsService.java`
- ✅ **PRESERVE** `Customer.java` entity and `CustomerRepository.java`
- ✅ **PRESERVE** `queryCustomer` tool method
- ✅ Update `schema.sql` - remove accident tables, keep customer table
- ✅ Update `data.sql` - remove accident data, keep customer data

#### Issue 3: Clean Up Test Infrastructure ✅
- ✅ Remove accident-specific tests from `ToolsServiceTest.java`
- ✅ **PRESERVE** customer query tests  
- ✅ Update `test-mcp.sh` to remove accident testing options
- ✅ **PRESERVE** customer database testing functionality

### Phase 2: RAG Infrastructure Setup ✅
**Goal:** Add vector database and AI model integration  
*Reference: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html*

#### Issue 4: Add Vector Database Dependencies ✅
- ✅ Add `spring-ai-starter-pgvector` dependency to `pom.xml`
- ✅ Add `spring-ai-starter-openai` dependency for cloud profile
- ✅ Add `spring-ai-starter-ollama` dependency for local profile  
- ✅ Add Testcontainers for local PostgreSQL with PGVector

#### Issue 5: Configure Database Profiles ✅
- ✅ **Cloud Profile**: Add Cloud Foundry PostgreSQL binding configuration
- ✅ **Local Profile**: Add Testcontainers PostgreSQL with PGVector setup
- ✅ Update `application-*.properties` files with vector database config
- ✅ Add vector store schema initialization

#### Issue 6: Create Vector Store Integration ✅
- ✅ Create `VectorStoreService.java` using PGVectorStore  
- ✅ Add customer-filtered vector search methods (read-only)
- ✅ **NOTE**: Document population/ingestion handled externally
- ✅ **Reference**: Spring AI PGVector documentation

### Phase 3: LLM & Embedding Model Integration  
**Goal:** Add AI model processing capabilities
*Reference: https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html, https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html*

#### Issue 7: Configure Chat Models  
- ✅ **Cloud Profile**: Configure OpenAI ChatModel bean
- ✅ **Local Profile**: Configure Ollama ChatModel bean
- ✅ Add model configuration properties for each profile
- ✅ Create `LLMService.java` for query expansion and answer generation

#### Issue 8: Configure Embedding Models
*Reference: https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html, https://docs.spring.io/spring-ai/reference/api/embeddings/nomic-embed.html*
- ✅ **Cloud Profile**: Configure OpenAI EmbeddingModel bean  
- ✅ **Local Profile**: Configure nomic-embed EmbeddingModel via Ollama
- ✅ Add embedding configuration properties
- ✅ Create `EmbeddingService.java` for text-to-vector conversion

### Phase 4: RAG Pipeline Implementation
**Goal:** Implement complete RAG workflow  
*Reference: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html*

#### Issue 9: Create RAG Tool  
- [ ] Add new `@Tool` method: `answerQuestion(String question, Integer customerId)`
- [ ] Implement RAG pipeline:
  1. Query expansion using LLM
  2. Question embedding using EmbeddingModel  
  3. Vector search with customer filtering
  4. Context-based answer generation using LLM
- [ ] Add comprehensive error handling and logging
- [ ] **Reference**: Spring AI RAG documentation

#### Issue 10: Vector Store Query Interface
- [ ] Create read-only vector store query interface
- [ ] Add customer-filtered vector search methods
- [ ] **NOTE**: Document population handled by external systems
- [ ] Focus on retrieval and customer filtering capabilities

### Phase 5: Testing & Documentation  
**Goal:** Comprehensive testing and documentation updates

#### Issue 11: Create RAG Tests
- [ ] Unit tests for `VectorStoreService`
- [ ] Unit tests for `LLMService` and `EmbeddingService`  
- [ ] Integration tests for complete RAG pipeline
- [ ] Mock-based testing for AI model interactions
- [ ] **PRESERVE** existing customer query tests

#### Issue 12: Update Test Script & Configuration
- [ ] Add RAG pipeline testing to `test-mcp.sh`
- [ ] Add colorful output for RAG testing phases
- [ ] Test both local (Ollama) and cloud (OpenAI) configurations
- [ ] Add Docker Compose for local testing environment

#### Issue 13: Documentation Updates
- [ ] Update `README.md` with RAG architecture overview and icons ✨
- [ ] Document new `answerQuestion` tool usage
- [ ] Add setup instructions for local vs cloud profiles  
- [ ] Update `CLAUDE.md` with RAG-specific context
- [ ] Add architecture diagrams showing RAG pipeline flow

---

## Technical Implementation Notes

### New RAG Tool Specification  
```java
@Tool(description = "Answer natural language questions using RAG with customer-specific context")
public String answerQuestion(String question, Integer customerId)
```

### Key Dependencies (Spring AI 1.0.0)
- `spring-ai-starter-pgvector` - Vector database integration
- `spring-ai-starter-openai` - OpenAI models (cloud profile)  
- `spring-ai-starter-ollama` - Local Ollama models
- `testcontainers-postgresql` - Local PostgreSQL with PGVector

### Profile Architecture
- **Local Profile**: Ollama + nomic-embed + Testcontainers PostgreSQL
- **Cloud Profile**: OpenAI API + Cloud Foundry PostgreSQL binding

### RAG Pipeline Flow
1. **Input**: Natural language question + customerId
2. **Query Expansion**: LLM enhances the question  
3. **Embedding**: Convert expanded query to vector
4. **Vector Search**: Find relevant documents filtered by customerId
5. **Answer Generation**: LLM creates answer from retrieved context
6. **Output**: Contextual answer with source references

---

## Success Criteria

✅ **Functional Requirements:**
- Customer query tool preserved and working
- RAG pipeline processes natural language questions  
- Customer-filtered document retrieval
- LLM-generated contextual answers
- Both local (Ollama) and cloud (OpenAI) profiles functional

✅ **Technical Requirements:**
- PostgreSQL with PGVector integration
- Comprehensive test coverage with colored output
- Docker-based local development environment  
- Cloud Foundry deployment compatibility
- MCP protocol compliance maintained

✅ **Documentation Requirements:**
- Updated README with icons and RAG architecture
- Comprehensive setup instructions
- Tool usage examples and API documentation