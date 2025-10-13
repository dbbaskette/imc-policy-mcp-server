<!-- 746b40b1-c5ae-4038-acba-3645596073ba a5d667d3-81d5-45e4-9d34-5a4a89721c97 -->
# GemFire Vector Store Cache Implementation

## Overview

Add a proactive read-through caching layer using GemFire VectorStore that wraps the existing Postgres pgvector store. The cache will over-fetch on misses to warm the cache for future queries.

## Key Changes

### 1. Add GemFire Dependency

Update `pom.xml` to add the GemFire vector store starter:

- Add `spring-ai-starter-vector-store-gemfire` dependency
- This provides `GemFireVectorStore` as documented at https://docs.spring.io/spring-ai/reference/1.1-SNAPSHOT/api/vectordbs/gemfire.html

### 2. Create CachingVectorStore Decorator

Create `src/main/java/com/insurancemegacorp/policymcpserver/service/CachingVectorStore.java`:

- Implements `VectorStore` interface
- Constructor accepts: `VectorStore cache` (GemFire), `VectorStore primary` (Postgres), `int cacheMultiplier`
- **add()**: Write directly to primary store only (cache is read-only)
- **delete()**: Delete from both primary and cache
- **similaritySearch()**: 
- Try cache first with original `SearchRequest`
- On cache miss (empty results), query primary with `topK * cacheMultiplier`
- Add over-fetched results to cache
- Return only the originally requested `topK` documents
- Cache key strategy: Uses query string + filters + topK (embedded in `SearchRequest`)

### 3. Create VectorStoreConfig

Create `src/main/java/com/insurancemegacorp/policymcpserver/config/VectorStoreConfig.java`:

- Define `@Bean` for GemFire vector store (will use CF service binding via auto-configuration)
- Define `@Bean` for Postgres vector store (existing, extracted from `RagConfiguration`)
- Define `@Primary @Bean` for `CachingVectorStore` that composes both
- Add `@Value("${vectorstore.cache.multiplier:5}")` for configurable multiplier

### 4. Update RagConfiguration

Modify `src/main/java/com/insurancemegacorp/policymcpserver/config/RagConfiguration.java`:

- Remove the `vectorStore()` bean definition (moved to `VectorStoreConfig`)
- Keep `chatClientBuilder()` and `questionAnswerAdvisor()` beans
- The `@Primary` `CachingVectorStore` will be auto-injected into services

### 5. Add Cache Configuration Properties

Update `src/main/resources/application-cloud.properties`:

- Add GemFire connection properties (will be auto-configured from CF service binding)
- Add `vectorstore.cache.multiplier=5` property
- Document that `imc-cache` service binding provides GemFire connection details

### 6. Update Cloud Foundry Manifest

Modify `manifest.yml`:

- Add `imc-cache` to the services list
- This binds the GemFire VectorDB service to the application

## Implementation Notes

### Cache Consistency Strategy

- **Write path**: All writes go directly to Postgres (cache remains read-only)
- **Cache invalidation**: External batch processes handle embeddings; cache can be cleared externally (app restart or manual GemFire clear) when new embeddings are loaded
- **Cache warming**: Over-fetching on miss (multiplier=5x) proactively populates cache
- **No write-through**: Cache is populated only on read misses
- **Local ETL services**: `RagEtlService` and `ReEmbeddingService` are for local testing only and don't require cache invalidation

### Cloud Foundry Integration

- GemFire service binding (`imc-cache`) provides connection details via VCAP_SERVICES
- Spring Boot auto-configuration will detect the service and configure `GemFireVectorStore`
- No manual connection configuration needed for CF environments

### Cache Key Matching

- GemFire's `similaritySearch()` will match on exact query string + filters
- This is simpler than vector similarity matching but may miss semantically similar queries
- Trade-off accepted: exact match is performant and predictable

## Files Created

1. `src/main/java/com/insurancemegacorp/policymcpserver/service/CachingVectorStore.java`
2. `src/main/java/com/insurancemegacorp/policymcpserver/config/VectorStoreConfig.java`

## Files Modified

1. `pom.xml` - Add GemFire dependency
2. `src/main/java/com/insurancemegacorp/policymcpserver/config/RagConfiguration.java` - Remove vectorStore bean
3. `src/main/resources/application-cloud.properties` - Add cache configuration
4. `manifest.yml` - Add imc-cache service binding

## Implementation Status

- ✅ Add spring-ai-starter-vector-store-gemfire dependency to pom.xml
- ✅ Create CachingVectorStore decorator implementing read-through cache logic with over-fetching
- ✅ Create VectorStoreConfig with beans for GemFire, Postgres, and caching decorator
- ✅ Update RagConfiguration to remove vectorStore bean (moved to VectorStoreConfig)
- ✅ Add cache configuration properties to application-cloud.properties
- ✅ Add imc-cache service binding to manifest.yml

