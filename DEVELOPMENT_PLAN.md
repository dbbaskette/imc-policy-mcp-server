# IMC Policy MCP Server - Development Plan
## RAG Implementation with Spring AI

### Overview
This project implements a Model Context Protocol (MCP) server for insurance policy operations using **Spring AI's built-in RAG capabilities** rather than custom implementations.

### Architecture (Revised)
```
MCP Client → ToolsService → ChatClient → [RAG Advisor] → VectorStore (PGVector)
                                     ↓
                                  ChatModel (Ollama/OpenAI)
```

### Key Spring AI Components Used

#### 1. VectorStore Integration
- **PGVectorStore** - Spring AI's PostgreSQL vector database integration
- **Document** - Spring AI's document abstraction with metadata
- **FilterExpressionBuilder** - Dynamic customer filtering

#### 2. RAG Pipeline
- **RetrievalAugmentationAdvisor** - Handles entire RAG workflow
- **VectorStoreDocumentRetriever** - Semantic search with filtering
- **ChatClient** - Unified chat interface with advisor integration

#### 3. Configuration
- **Auto-configuration** for PGVector, Ollama, and OpenAI
- **Profile-based** model selection (local vs cloud)

---

## Implementation Phases

### ✅ Phase 1: Base MCP Server (COMPLETED)
- [x] MCP server with STDIO/SSE transport
- [x] Customer query tool
- [x] Database connectivity (H2 for customers)
- [x] Profile-based configuration

### ✅ Phase 2: Spring AI VectorStore Setup (COMPLETED)
**References**: 
- [PGVector Documentation](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [RAG Documentation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)

#### Tasks:
- [x] **Configure PGVectorStore** - uses Spring AI auto-configuration
- [x] **Remove custom VectorDocument entity** - replaced with Spring AI Document
- [x] **Update data loading** - PolicyDocumentLoader uses `VectorStore.add(List<Document>)`
- [x] **Configure customer metadata** - FilterExpressionBuilder for customer filtering

#### Technical Details:
```java
// Spring AI approach - no custom entities needed
@Bean
VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
        .withSchemaName("public")
        .withVectorTableName("vector_store")
        .withDimensions(768)
        .build();
}
```

### ✅ Phase 3: Spring AI RAG Pipeline (COMPLETED)
**References**: 
- [Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [ChatClient Documentation](https://docs.spring.ai/spring-ai/reference/api/chat/chatclient.html)

#### Tasks:
- [x] **Remove custom RAGService** - replaced with SpringAIRAGService
- [x] **Implement proper RAG pipeline** using VectorStore + ChatClient
- [x] **Setup ChatClient** with proper configuration
- [x] **Implement customer filtering** using FilterExpressionBuilder

#### Technical Details:
```java
// Spring AI RAG - no manual embedding generation
@Bean
RetrievalAugmentationAdvisor retrievalAdvisor(VectorStore vectorStore) {
    return new RetrievalAugmentationAdvisor(
        VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(0.7)
            .topK(5)
            .build());
}

@Bean
ChatClient chatClient(ChatModel chatModel, RetrievalAugmentationAdvisor advisor) {
    return ChatClient.builder(chatModel)
        .defaultAdvisors(advisor)
        .build();
}
```

### 🔄 Phase 4: MCP Tool Implementation (PLANNED)
**References**: 
- [MCP Tools](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [ChatClient Usage](https://docs.spring.ai/spring-ai/reference/api/chat/chatclient.html)

#### Tasks:
- [ ] **Simplify answerQuestion tool** to use ChatClient
- [ ] **Implement customer filtering** via advisor context
- [ ] **Add query expansion** using ChatClient prompting
- [ ] **Return structured responses** with source metadata

#### Technical Details:
```java
@Tool(description = "Answer insurance questions with customer-specific context")
public String answerQuestion(String question, Integer customerId) {
    // Let Spring AI handle the entire RAG pipeline
    return chatClient.prompt()
        .user(question)
        .advisors(spec -> spec.param("customerId", customerId))
        .call()
        .content();
}
```

---

## Data Flow (Spring AI Approach)

### Current Flow (Custom - ❌ Wrong)
```
Question → Manual Embedding → Custom Query → Manual Context → LLM → Response
```

### Correct Flow (Spring AI - ✅ Implemented)
```
Question + Customer ID → VectorStore Search → Retrieved Docs → Context → ChatClient → Answer
                           ↓                      ↓               ↓
                    [Auto Embedding + Filter] [Content Extraction] [Prompt Engineering]
```

---

## Configuration Strategy

### Local Profile (`local-sse`, `local-stdio`)
- **Vector Store**: Testcontainers PostgreSQL with PGVector
- **Chat Model**: Ollama (llama3.2:3b)
- **Embedding Model**: Ollama (nomic-embed-text:latest)
- **Data**: Test data from vector_store_testdata.csv

### Cloud Profile (`sse`, `stdio`) 
- **Vector Store**: Cloud Foundry bound PostgreSQL
- **Chat Model**: OpenAI GPT model
- **Embedding Model**: OpenAI embedding model
- **Data**: Production policy documents

---

## Key Benefits of Spring AI Approach

1. **Simplified Code**: No manual embedding generation or vector operations
2. **Built-in Optimization**: Automatic query expansion and context management
3. **Consistent API**: Standard ChatClient interface for all LLM interactions
4. **Flexible Filtering**: Dynamic customer filtering via FilterExpressionBuilder
5. **Maintainable**: Uses Spring AI's tested and optimized RAG pipeline

---

## Current Status: ✅ REFACTORING COMPLETED

**Problem Solved**: ✅ Replaced custom RAG implementation with Spring AI native components
**Implementation**: Spring AI VectorStore + ChatClient + Document abstraction
**Priority**: COMPLETED - Now follows Spring AI best practices

### ✅ Completed Refactoring:
1. ✅ Removed custom RAG components (RAGService, VectorDocument, custom repositories)
2. ✅ Configured Spring AI PGVectorStore with auto-configuration
3. ✅ Implemented proper RAG pipeline with VectorStore + ChatClient
4. ✅ Updated MCP tools to use SpringAIRAGService with customer filtering

---

## 🏁 Implementation Summary

### ✅ Key Components Built:
1. **VectorStoreConfiguration** - Spring AI auto-configuration setup
2. **PolicyDocumentLoader** - Loads CSV data as Spring AI Document objects
3. **SpringAIRAGService** - Complete RAG pipeline using Spring AI components
4. **RAGConfiguration** - ChatClient configuration for RAG operations
5. **Updated ToolsService** - Simplified MCP tool using SpringAIRAGService

### 🔧 Technical Architecture:
- **VectorStore**: Auto-configured PGVectorStore with customer metadata filtering
- **Documents**: Spring AI Document objects with customer metadata (refnum1, refnum2)
- **Search**: FilterExpressionBuilder for customer-specific document retrieval
- **Generation**: ChatClient with context-enhanced prompts
- **Integration**: MCP tool directly calls SpringAIRAGService.answerQuestion()

---

*Last Updated: 2025-08-01*  
*Status: ✅ SPRING AI REFACTORING COMPLETED*  
*Reference Documentation: Spring AI 1.0.0 GA*