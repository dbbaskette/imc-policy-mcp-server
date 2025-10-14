package com.insurancemegacorp.policymcpserver.vectorstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.GemFireAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authenticated version of Spring AI's GemFireVectorStore that supports Basic Authentication.
 *
 * This is a modified version of org.springframework.ai.vectorstore.gemfire.GemFireVectorStore
 * that adds username/password authentication support for GemFire VectorDB operations.
 */
public class AuthenticatedGemFireVectorStore extends AbstractObservationVectorStore implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticatedGemFireVectorStore.class);

    private static final String DEFAULT_URI = "http{ssl}://{host}:{port}/gemfire-vectordb/v1/indexes";
    private static final String EMBEDDINGS = "/embeddings";
    private static final String QUERY = "/query";
    private static final String DOCUMENT_FIELD = "document";

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_INDEX_NAME = "spring-ai-gemfire-index";
    public static final int DEFAULT_BEAM_WIDTH = 100;
    public static final int DEFAULT_MAX_CONNECTIONS = 16;
    public static final String DEFAULT_SIMILARITY_FUNCTION = "COSINE";
    public static final String[] DEFAULT_FIELDS = new String[] {};
    public static final int DEFAULT_BUCKETS = 0;
    public static final boolean DEFAULT_SSL_ENABLED = false;

    private final WebClient client;
    private final boolean initializeSchema;
    private final ObjectMapper objectMapper;
    private final String indexName;
    private final int beamWidth;
    private final int maxConnections;
    private final int buckets;
    private final String vectorSimilarityFunction;
    private final String[] fields;
    private final FilterExpressionConverter filterExpressionConverter;

    protected AuthenticatedGemFireVectorStore(Builder builder) {
        super(builder);

        this.initializeSchema = builder.initializeSchema;
        this.indexName = builder.indexName;
        this.beamWidth = builder.beamWidth;
        this.maxConnections = builder.maxConnections;
        this.buckets = builder.buckets;
        this.vectorSimilarityFunction = builder.vectorSimilarityFunction;
        this.fields = builder.fields;

        String base = UriComponentsBuilder.fromUriString(DEFAULT_URI)
                .build(builder.sslEnabled ? "s" : "", builder.host, builder.port)
                .toString();

        // Create WebClient with Basic Authentication if credentials provided
        WebClient.Builder webClientBuilder = WebClient.builder().baseUrl(base);

        if (builder.username != null && !builder.username.isEmpty()) {
            logger.info("Configuring authenticated GemFire vector store: {} (user: {})", base, builder.username);
            webClientBuilder.defaultHeaders(headers -> headers.setBasicAuth(builder.username, builder.password));
        } else {
            logger.warn("No authentication configured for GemFire vector store");
        }

        this.client = webClientBuilder.build();
        this.filterExpressionConverter = new GemFireAiSearchFilterExpressionConverter();
        this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
    }

    public static Builder builder(EmbeddingModel embeddingModel) {
        return new Builder(embeddingModel);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!this.initializeSchema) {
            return;
        }
        if (!indexExists()) {
            createIndex();
        }
    }

    public boolean indexExists() {
        String indexResponse = getIndex();
        return indexResponse != null && !indexResponse.isEmpty();
    }

    @Nullable
    public String getIndex() {
        try {
            return this.client.get()
                    .uri("/" + this.indexName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn("")
                    .block();
        } catch (Exception e) {
            logger.warn("Failed to check if index exists: {}", e.getMessage());
            return "";
        }
    }

    public void createIndex() throws JsonProcessingException {
        CreateRequest createRequest = new CreateRequest(this.indexName);
        createRequest.setBeamWidth(this.beamWidth);
        createRequest.setMaxConnections(this.maxConnections);
        createRequest.setBuckets(this.buckets);
        createRequest.setVectorSimilarityFunction(this.vectorSimilarityFunction);
        createRequest.setFields(this.fields);

        String index = this.objectMapper.writeValueAsString(createRequest);

        this.client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(index)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorMap(WebClientException.class, this::handleHttpClientException)
                .block();

        logger.info("Created GemFire index: {}", this.indexName);
    }

    @Override
    public void doAdd(List<Document> documents) {
        List<EmbeddingItem> items = documents.stream().map(document -> {
            Map<String, Object> metadata = new HashMap<>(document.getMetadata());
            metadata.put(DOCUMENT_FIELD, document.getText());

            float[] embedding = this.embeddingModel.embed(document.getText());

            return new EmbeddingItem(document.getId(), embedding, metadata);
        }).toList();

        this.client.post()
                .uri("/" + this.indexName + EMBEDDINGS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(items)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorMap(WebClientException.class, this::handleHttpClientException)
                .block();
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
        float[] floatVector = this.embeddingModel.embed(request.getQuery());

        // Convert filter expression to GemFire query format
        // NOTE: Filter support added in Spring AI 1.1.0-M3
        // The converter generates "field:value" which works with properly flattened metadata
        String filterQuery = null;
        if (request.hasFilterExpression()) {
            filterQuery = this.filterExpressionConverter.convertExpression(request.getFilterExpression());
            logger.debug("Applying metadata filter to GemFire query: {}", filterQuery);
        }

        QueryRequest queryRequest = new QueryRequest(floatVector, request.getTopK(), true, filterQuery);
        logger.debug("GemFire query request: topK={}, includeMetadata={}, filter={}, vectorLength={}",
                     queryRequest.topK(), queryRequest.includeMetadata(), queryRequest.filter(), floatVector.length);

        return this.client.post()
                .uri("/" + this.indexName + QUERY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryRequest)
                .retrieve()
                .bodyToFlux(QueryResponse.class)
                .doOnNext(r -> {
                    logger.info("✅ GemFire returned result: score={}, key={}, threshold={}, metadata keys={}",
                                r.score, r.key, request.getSimilarityThreshold(),
                                r.metadata != null ? r.metadata.keySet() : "null");
                })
                .doOnComplete(() -> {
                    logger.info("🏁 GemFire query complete for filter: {} (returned 0 documents if no doOnNext)", queryRequest.filter());
                })
                .doOnError(ex -> {
                    logger.error("❌ GemFire query error: {}", ex.getMessage());
                    if (queryRequest.filter() != null) {
                        logger.error("Failed query had filter: {}", queryRequest.filter());
                    }
                })
                .filter(r -> r.score >= request.getSimilarityThreshold())
                .map(r -> {
                    Map<String, Object> metadata = r.metadata;
                    if (r.metadata == null) {
                        metadata = new HashMap<>();
                        metadata.put(DOCUMENT_FIELD, "--Deleted--");
                    }

                    // Extract content - try "content" first (from DirectGemFireCacheWarmer), then "document" (legacy)
                    String content = (String) metadata.remove("content");
                    if (content == null) {
                        content = (String) metadata.remove(DOCUMENT_FIELD);
                    }
                    if (content == null) {
                        content = "--No Content--";
                    }

                    metadata.put(DocumentMetadata.DISTANCE.value(), 1 - r.score);
                    return Document.builder()
                            .id(r.key)
                            .text(content)
                            .metadata(metadata)
                            .score((double) r.score)
                            .build();
                })
                .collectList()
                .onErrorMap(WebClientException.class, this::handleHttpClientException)
                .block();
    }

    @Override
    public void doDelete(List<String> idList) {
        idList.forEach(id -> {
            this.client.delete()
                    .uri("/" + this.indexName + EMBEDDINGS + "/" + id)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .onErrorMap(WebClientException.class, this::handleHttpClientException)
                    .block();
        });
    }

    private RuntimeException handleHttpClientException(Throwable ex) {
        if (ex instanceof WebClientException) {
            logger.error("GemFire API error details: {}", ex.getMessage());
            if (ex.getCause() != null) {
                logger.error("Caused by: {}", ex.getCause().getMessage());
            }
            return new RuntimeException("Got an unexpected HTTP error: " + ex.getMessage(), ex);
        }
        return new RuntimeException(ex);
    }

    @Override
    public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
        return VectorStoreObservationContext.builder(VectorStoreProvider.GEMFIRE.value(), operationName)
                .collectionName(this.indexName)
                .dimensions(this.embeddingModel.dimensions());
    }

    // Inner classes for JSON serialization

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreateRequest(
            @JsonProperty("name") String name,
            @JsonProperty("beam-width") Integer beamWidth,
            @JsonProperty("max-connections") Integer maxConnections,
            @JsonProperty("vector-similarity-function") String vectorSimilarityFunction,
            @JsonProperty("fields") String[] fields,
            @JsonProperty("buckets") Integer buckets) {

        @JsonCreator
        CreateRequest(@JsonProperty("name") String name) {
            this(name, null, null, null, null, null);
        }

        public CreateRequest withBeamWidth(int beamWidth) {
            return new CreateRequest(name, beamWidth, maxConnections, vectorSimilarityFunction, fields, buckets);
        }

        public CreateRequest withMaxConnections(int maxConnections) {
            return new CreateRequest(name, beamWidth, maxConnections, vectorSimilarityFunction, fields, buckets);
        }

        public CreateRequest withVectorSimilarityFunction(String function) {
            return new CreateRequest(name, beamWidth, maxConnections, function, fields, buckets);
        }

        public CreateRequest withFields(String[] fields) {
            return new CreateRequest(name, beamWidth, maxConnections, vectorSimilarityFunction, fields, buckets);
        }

        public CreateRequest withBuckets(int buckets) {
            return new CreateRequest(name, beamWidth, maxConnections, vectorSimilarityFunction, fields, buckets);
        }

        public void setBeamWidth(int beamWidth) {
            // Immutable - use withBeamWidth
        }

        public void setMaxConnections(int maxConnections) {
            // Immutable - use withMaxConnections
        }

        public void setVectorSimilarityFunction(String function) {
            // Immutable - use withVectorSimilarityFunction
        }

        public void setFields(String[] fields) {
            // Immutable - use withFields
        }

        public void setBuckets(int buckets) {
            // Immutable - use withBuckets
        }
    }

    record EmbeddingItem(
            @JsonProperty("key") String key,
            @JsonProperty("vector") float[] vector,
            @JsonProperty("metadata") Map<String, Object> metadata) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QueryRequest(
            @JsonProperty("vector") float[] vector,
            @JsonProperty("top-k") int topK,
            @JsonProperty("include-metadata") boolean includeMetadata,
            @JsonProperty("filter-query") String filter) {
    }

    record QueryResponse(
            @JsonProperty("key") String key,
            @JsonProperty("score") float score,
            @JsonProperty("metadata") Map<String, Object> metadata) {
    }

    // Builder class

    public static class Builder extends AbstractVectorStoreBuilder<Builder> {

        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private String indexName = DEFAULT_INDEX_NAME;
        private int beamWidth = DEFAULT_BEAM_WIDTH;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private String vectorSimilarityFunction = DEFAULT_SIMILARITY_FUNCTION;
        private String[] fields = DEFAULT_FIELDS;
        private int buckets = DEFAULT_BUCKETS;
        private boolean sslEnabled = DEFAULT_SSL_ENABLED;
        private boolean initializeSchema = false;
        private String username;
        private String password;

        protected Builder(EmbeddingModel embeddingModel) {
            super(embeddingModel);
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder beamWidth(int beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder vectorSimilarityFunction(String function) {
            this.vectorSimilarityFunction = function;
            return this;
        }

        public Builder fields(String[] fields) {
            this.fields = fields;
            return this;
        }

        public Builder buckets(int buckets) {
            this.buckets = buckets;
            return this;
        }

        public Builder sslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
            return this;
        }

        public Builder initializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        @Override
        public AuthenticatedGemFireVectorStore build() {
            return new AuthenticatedGemFireVectorStore(this);
        }
    }
}
