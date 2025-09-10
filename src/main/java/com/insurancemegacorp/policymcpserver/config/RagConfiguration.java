package com.insurancemegacorp.policymcpserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG configuration for PGVector store and QuestionAnswerAdvisor.
 * Supports both local (OpenAI API) and Cloud Foundry (bound services) environments.
 */
@Configuration
public class RagConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RagConfiguration.class);

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}")
    private String embeddingModelName;

    @Value("${spring.ai.openai.embedding.options.dimensions:768}")
    private int embeddingDimensions;

    @Value("${app.rag.top-k:5}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.vectorstore.table-name:vector_store}")
    private String tableName;

    // Column names use defaults from PgVectorStore

    // EmbeddingModel is auto-configured by Spring AI starter
    // In local development: Uses spring.ai.openai.api-key from application properties
    // In Cloud Foundry: Auto-configured via bound AI services (VCAP_SERVICES)

    /**
     * Configure PGVector store for document retrieval.
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        logger.info("Configuring PGVector: table={}, dimensions={}, model={}", 
                   tableName, embeddingDimensions, embeddingModel.getClass().getSimpleName());
        
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(embeddingDimensions)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(false) // Schema is managed by schema.sql
                .vectorTableName(tableName)
                .maxDocumentBatchSize(10000)
                .build();
    }

    // Note: VectorStoreRetriever is not available in Spring AI 1.1.0-SNAPSHOT
    // RagService will use VectorStore.similaritySearch() directly

    /**
     * Configure ChatClient builder for query transformations.
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        logger.info("Configuring ChatClient.Builder for RAG transformations");
        return ChatClient.builder(chatModel);
    }

    /**
     * Configure QuestionAnswerAdvisor for RAG functionality.
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        logger.info("Configuring QuestionAnswerAdvisor with vector store");
        
        return QuestionAnswerAdvisor.builder(vectorStore).build();
    }
}
