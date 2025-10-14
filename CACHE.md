
# Implementing a Proactive Read-Through Vector Store Cache in Spring AI

This document is the implementation plan for adding a proactive, read-through caching layer to the `imc-policy-mcp-server` Spring AI RAG application. GemFire will serve as the in-memory cache while Postgres with pgvector remains the primary store. The two vector stores are composed with a decorator so retrievals prefer GemFire but fall back to Postgres and proactively warm the cache.

## Goal

Refactor the existing RAG configuration so that:

- Similarity searches consult a GemFire-backed cache first.
- Cache misses query the existing Postgres pgvector store.
- Miss handling over-fetches from Postgres to seed the cache for future calls.
- Responses return only the number of documents requested by the caller.
- Cache usage is read-through only; writes continue to land in Postgres via existing ETL flows.

The GemFire cache instance is provisioned in Cloud Foundry and will be bound to the app as `imc-cache`.

## Scope and Assumptions

- All changes live in the current module (root `pom.xml`, `src/main/java/com/insurancemegacorp/policymcpserver`).
- GemFire credentials and connection details come from the Cloud Foundry service binding; local development can rely on placeholder properties or disabled cache behavior.
- Write-through is **not** required; GemFire maintains only cached results populated on demand.
- Existing RAG services (e.g., `RagService`, `RagEtlService`) should keep working without code changes beyond the new decorator integration.

## Implementation Plan

1. **Dependencies**
   - Add the GemFire vector store starter to the root `pom.xml`.
   - Keep the existing Postgres driver and ensure the pgvector starter (or equivalent) remains available for the primary store.

2. **Configuration Properties**
   - Introduce cache configuration properties (e.g., `vectorstore.cache.multiplier`) in `src/main/resources/application.properties`.
   - Document how these properties are supplied in Cloud Foundry vs. local development.

3. **Caching Decorator**
   - Create `src/main/java/com/insurancemegacorp/policymcpserver/service/CachingVectorStore.java` implementing `VectorStore`.
   - Implement read-through logic: try GemFire, fetch from Postgres on miss, prefetch additional results, return only the requested `topK`, and skip write-through.

4. **Bean Wiring**
   - Add `VectorStoreConfig` to `src/main/java/com/insurancemegacorp/policymcpserver/config`.
   - Define beans for the GemFire cache (using GemFire starter configuration), the Postgres pgvector store, and the `@Primary` caching decorator.
   - Ensure the existing `RagConfiguration` injects the decorator rather than the raw pgvector store.

5. **Deployment Notes**
   - Update `manifest.yml` (or deployment docs) to bind the `imc-cache` service.
   - Provide operator notes on property management and expected environment variables.

## Step-by-Step Instructions

### Step 1: Add Dependencies

Modify the root `pom.xml` to include the GemFire starter alongside the existing Spring AI dependencies.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-gemfire-store-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### Step 2: Configure Data Source and Cache Properties

Add the following properties to `src/main/resources/application.properties`. Adjust the datasource values to match each environment or rely on Cloud Foundry bindings.

```properties
# ===============================================
# VECTOR STORE AND CACHE CONFIGURATION
# ===============================================

# PostgreSQL Datasource Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/your-db
spring.datasource.username=your-user
spring.datasource.password=your-password
spring.datasource.driver-class-name=org.postgresql.Driver

# Proactive Caching Configuration
# Determines how many more documents to fetch on a cache miss.
# Example: if original request is for 4 docs, 4 * 5 = 20 docs will be cached.
vectorstore.cache.multiplier=5
```

### Step 3: Create the Caching Decorator Service

Create `src/main/java/com/insurancemegacorp/policymcpserver/service/CachingVectorStore.java` with the following logic.

```java
package com.insurancemegacorp.policymcpserver.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;

/**
 * A read-through caching decorator for a VectorStore.
 */
public class CachingVectorStore implements VectorStore {

    private final VectorStore cache;      // GemFire
    private final VectorStore primary;    // Postgres pgvector
    private final int cacheMultiplier;

    public CachingVectorStore(VectorStore cache, VectorStore primary, int cacheMultiplier) {
        this.cache = cache;
        this.primary = primary;
        this.cacheMultiplier = cacheMultiplier;
    }

    @Override
    public void add(List<Document> documents) {
        primary.add(documents);  // existing write path
    }

    @Override
    public Optional<Boolean> delete(List<String> idList) {
        primary.delete(idList);
        return cache.delete(idList);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        List<Document> cachedResult = cache.similaritySearch(request);
        if (!cachedResult.isEmpty()) {
            return cachedResult;
        }

        int originalTopK = request.getTopK();
        int newTopK = originalTopK * cacheMultiplier;

        SearchRequest largerRequest = SearchRequest.defaults()
                .withQuery(request.getQuery())
                .withTopK(newTopK)
                .withSimilarityThreshold(request.getSimilarityThreshold())
                .withFilterExpression(request.getFilterExpression());

        List<Document> primaryResult = primary.similaritySearch(largerRequest);

        if (!primaryResult.isEmpty()) {
            cache.add(primaryResult);
        }

        if (primaryResult.size() > originalTopK) {
            return primaryResult.subList(0, originalTopK);
        }

        return primaryResult;
    }
}
```

### Step 4: Configure the Vector Store Beans

Add `src/main/java/com/insurancemegacorp/policymcpserver/config/VectorStoreConfig.java` to wire the cache and decorator.

```java
package com.insurancemegacorp.policymcpserver.config;

import com.insurancemegacorp.policymcpserver.service.CachingVectorStore;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.gemfire.store.GemfireVectorStore;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Value("${vectorstore.cache.multiplier:5}")
    private int cacheMultiplier;

    @Bean
    public VectorStore gemfireVectorStore(EmbeddingClient embeddingClient) {
        return new GemfireVectorStore(embeddingClient);
    }

    @Bean
    public VectorStore postgresVectorStore(JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient) {
        return new PgVectorStore(jdbcTemplate, embeddingClient);
    }

    @Bean
    @Primary
    public VectorStore cachingVectorStore(VectorStore gemfireVectorStore, VectorStore postgresVectorStore) {
        return new CachingVectorStore(gemfireVectorStore, postgresVectorStore, cacheMultiplier);
    }
}
```

### Step 5: Deployment Updates

- Bind the Cloud Foundry service `imc-cache` in `manifest.yml`:

  ```yaml
  services:
    - imc-cache
  ```

- Ensure Postgres and GemFire credentials are available either through CF bindings or environment variables.
- Document how to disable GemFire locally (e.g., profile-based configuration) if needed for developers without GemFire access.

## Summary of Expected Changes

- Update `pom.xml` with GemFire/pgvector starters.
- Add cache-related properties to `application.properties`.
- Introduce `CachingVectorStore` and supporting `VectorStoreConfig`.
- Adjust deployment descriptors to bind the `imc-cache` service.

After completing these steps, the RAG services will automatically leverage the GemFire cache while continuing to rely on Postgres as the authoritative data source.
