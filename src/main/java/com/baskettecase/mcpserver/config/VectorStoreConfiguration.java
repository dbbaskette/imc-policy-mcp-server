package com.baskettecase.mcpserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 🗄️ Spring AI Vector Store Configuration
 * 
 * <p>Configures PostgreSQL with PGVector extension as the vector database for RAG operations.
 * Uses Spring AI's built-in PGVectorStore implementation with auto-configuration.</p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>📊 768-dimensional vectors (compatible with nomic-embed-text)</li>
 *   <li>🔍 Customer-specific document filtering via metadata</li>
 *   <li>⚡ Optimized for semantic similarity search</li>
 *   <li>🐳 Testcontainers integration for local development</li>
 * </ul>
 * 
 * <p><strong>Reference:</strong> 
 * <a href="https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html">
 * Spring AI PGVector Documentation
 * </a></p>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 */
@Configuration
@Profile({"local-sse", "local-stdio", "sse", "stdio"}) // All profiles use PGVector
public class VectorStoreConfiguration {

    /**
     * 🗄️ Vector Store Configuration Properties
     * 
     * <p>Spring AI auto-configures PGVectorStore when the spring-ai-starter-vector-store-pgvector
     * dependency is present. This configuration class provides documentation and any custom
     * configuration needed.</p>
     * 
     * <h3>Auto-Configuration Details:</h3>
     * <ul>
     *   <li><strong>Table Name:</strong> vector_store (configured via properties)</li>
     *   <li><strong>Schema:</strong> public</li>
     *   <li><strong>Dimensions:</strong> 768 (configured via properties)</li>
     *   <li><strong>Distance Metric:</strong> Cosine similarity</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> VectorStore is auto-configured by Spring AI when the
     * pgvector starter dependency is present and database connection is configured.</p>
     * 
     * <p><strong>Configuration:</strong> See application-*.properties files for
     * spring.ai.vectordatabase.pgvector.* properties.</p>
     */
    // VectorStore is auto-configured by Spring AI pgvector starter
    // No explicit @Bean needed - Spring AI handles this automatically
}