package com.insurancemegacorp.policymcpserver.config;

import com.insurancemegacorp.policymcpserver.service.CachingVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vector Store configuration with GemFire caching layer.
 * 
 * This configuration sets up:
 * - GemFire as the fast in-memory cache
 * - Postgres pgvector as the primary authoritative store
 * - CachingVectorStore decorator that composes both with read-through logic
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Value("${vectorstore.cache.multiplier:5}")
    private int cacheMultiplier;

    @Value("${spring.ai.openai.embedding.options.dimensions:768}")
    private int embeddingDimensions;

    @Value("${app.vectorstore.table-name:vector_store}")
    private String tableName;

    /**
     * GemFire vector store for caching.
     * Uses auto-configuration from Cloud Foundry service binding (imc-cache).
     */
    @Bean
    @Qualifier("gemfireVectorStore")
    public VectorStore gemfireVectorStore(EmbeddingModel embeddingModel) {
        logger.info("Configuring GemFire vector store for caching (dimensions={})", embeddingDimensions);
        
        return GemFireVectorStore.builder(embeddingModel)
                .initializeSchema(true)  // Allow GemFire to create its own schema
                .build();
    }

    /**
     * Postgres pgvector store as primary authoritative store.
     * This is the source of truth for all vector data.
     */
    @Bean
    @Qualifier("postgresVectorStore")
    public VectorStore postgresVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        logger.info("Configuring Postgres pgvector store: table={}, dimensions={}", 
                   tableName, embeddingDimensions);
        
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(embeddingDimensions)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(false)  // Schema is managed by schema.sql
                .vectorTableName(tableName)
                .maxDocumentBatchSize(10000)
                .build();
    }

    /**
     * Primary vector store bean that combines GemFire cache with Postgres primary store.
     * This is the @Primary bean that will be injected into RagService and other consumers.
     */
    @Bean
    @Primary
    public VectorStore cachingVectorStore(
            @Qualifier("gemfireVectorStore") VectorStore gemfireVectorStore,
            @Qualifier("postgresVectorStore") VectorStore postgresVectorStore) {
        
        logger.info("Configuring caching vector store with multiplier={}", cacheMultiplier);
        
        return new CachingVectorStore(gemfireVectorStore, postgresVectorStore, cacheMultiplier);
    }
}

