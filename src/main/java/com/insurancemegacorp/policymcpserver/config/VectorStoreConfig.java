package com.insurancemegacorp.policymcpserver.config;

import com.insurancemegacorp.policymcpserver.service.CachingVectorStore;
import com.insurancemegacorp.policymcpserver.service.VectorCacheMetrics;
import com.insurancemegacorp.policymcpserver.vectorstore.AuthenticatedGemFireVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Value("${spring.ai.vectorstore.gemfire.host:localhost}")
    private String gemfireHost;

    @Value("${spring.ai.vectorstore.gemfire.port:8080}")
    private int gemfirePort;

    @Value("${spring.ai.vectorstore.gemfire.index:spring-ai-gemfire-index}")
    private String gemfireIndex;

    @Value("${spring.ai.vectorstore.gemfire.username:}")
    private String gemfireUsername;

    @Value("${spring.ai.vectorstore.gemfire.password:}")
    private String gemfirePassword;

    @Value("${spring.ai.vectorstore.gemfire.initialize-schema:false}")
    private boolean gemfireInitializeSchema;

    /**
     * Custom RestClient builder for GemFire with Basic Authentication.
     * Spring AI GemFireVectorStore may use RestClient instead of WebClient.
     */
    @Bean
    @Primary
    public org.springframework.web.client.RestClient.Builder restClientBuilder() {
        var builder = org.springframework.web.client.RestClient.builder();

        // Add Basic Auth if GemFire credentials are available
        if (gemfireUsername != null && !gemfireUsername.isEmpty()) {
            logger.info("Configuring RestClient with Basic Auth for GemFire (user: {})", gemfireUsername);
            builder.defaultHeaders(headers -> headers.setBasicAuth(gemfireUsername, gemfirePassword));
        } else {
            logger.warn("No GemFire credentials found - cache will not authenticate");
        }

        return builder;
    }

    /**
     * Custom WebClient builder for GemFire with Basic Authentication.
     * Keeping this in case GemFireVectorStore uses WebClient.
     */
    @Bean
    public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
        var builder = org.springframework.web.reactive.function.client.WebClient.builder();

        // Add Basic Auth if GemFire credentials are available
        if (gemfireUsername != null && !gemfireUsername.isEmpty()) {
            logger.info("Configuring WebClient with Basic Auth for GemFire (user: {})", gemfireUsername);
            builder.defaultHeaders(headers -> headers.setBasicAuth(gemfireUsername, gemfirePassword));
        }

        return builder;
    }

    /**
     * GemFire vector store for caching.
     * Uses Cloud Foundry service binding (imc-cache) via VCAP_SERVICES.
     * The host and port are configured through spring.ai.vectorstore.gemfire properties
     * which should be extracted from the service credentials.
     *
     * IMPORTANT: VectorDB must be enabled on the GemFire service for caching to work.
     * To enable: cf update-service imc-cache -c '{"enable_vector_db": true}'
     *
     * With schema initialization disabled, the cache will gracefully fall back to
     * no-op mode if the index doesn't exist, allowing the app to run with just Postgres.
     */
    @Bean
    @Qualifier("gemfireVectorStore")
    public VectorStore gemfireVectorStore(EmbeddingModel embeddingModel) {
        logger.info("Configuring GemFire vector store for caching: {}:{} index={} (dimensions={})",
                   gemfireHost, gemfirePort, gemfireIndex, embeddingDimensions);

        logger.warn("NOTE: GemFire caching requires VectorDB to be enabled on the service.");
        logger.warn("If not enabled, queries will fall back to Postgres only (no caching).");
        logger.warn("To enable: cf update-service imc-cache -c '{{\"enable_vector_db\": true}}'");

        // Use our custom AuthenticatedGemFireVectorStore with Basic Authentication support
        return AuthenticatedGemFireVectorStore.builder(embeddingModel)
                .host(gemfireHost)
                .port(gemfirePort)
                .indexName(gemfireIndex)
                .sslEnabled(gemfirePort == 443)  // Enable SSL for HTTPS (port 443)
                .initializeSchema(gemfireInitializeSchema)  // Read from property spring.ai.vectorstore.gemfire.initialize-schema
                .username(gemfireUsername)
                .password(gemfirePassword)
                .fields(new String[] {"refnum1", "refnum2", "sourcePath", "timestamp"})  // Declare filterable metadata fields
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
            @Qualifier("postgresVectorStore") VectorStore postgresVectorStore,
            VectorCacheMetrics metrics,
            com.insurancemegacorp.policymcpserver.service.DirectGemFireCacheWarmer directWarmer) {

        logger.info("Configuring caching vector store with multiplier={}", cacheMultiplier);

        return new CachingVectorStore(gemfireVectorStore, postgresVectorStore, cacheMultiplier, metrics, directWarmer);
    }
}

