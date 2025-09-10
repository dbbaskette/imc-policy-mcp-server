package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG service for processing customer-scoped policy document queries.
 */
@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private QuestionAnswerAdvisor questionAnswerAdvisor;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Value("${app.rag.top-k:5}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.rag.query-rewrite:true}")
    private boolean enableQueryRewrite;

    @Value("${app.rag.multi-query:false}")
    private boolean enableMultiQuery;

    @Value("${app.rag.multi-query-count:3}")
    private int multiQueryCount;

    /**
     * Process query with customer-scoped document retrieval.
     */
    public String processQuery(String query, Integer customerId) {
        if (!StringUtils.hasText(query)) throw new IllegalArgumentException("Query cannot be null or empty");
        if (customerId == null) throw new IllegalArgumentException("Customer ID cannot be null");

        logger.info("Processing RAG query for customer {}: '{}'", customerId, query);
        
        try {
            // Apply query transformations if enabled
            String processedQuery = processQueryTransformations(query);
            
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(processedQuery)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(new FilterExpressionBuilder().eq("refnum1", customerId).build())
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            logger.info("Retrieved {} documents for customer {} with query: '{}' (topK={}, threshold={})", 
                       documents.size(), customerId, query, topK, similarityThreshold);

            if (documents.isEmpty()) {
                return "No relevant policy information found for this customer and query.";
            }

            StringBuilder context = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                context.append("Document ").append(i + 1).append(":\n")
                       .append(doc.getFormattedContent());
                if (i < documents.size() - 1) context.append("\n\n---\n\n");
            }
            return context.toString();

        } catch (Exception e) {
            logger.error("Error processing RAG query for customer {}: {}", customerId, e.getMessage(), e);
            throw new RuntimeException("Failed to process query: " + e.getMessage(), e);
        }
    }

    /**
     * Apply query transformations to improve retrieval quality.
     */
    private String processQueryTransformations(String originalQuery) {
        String processedQuery = originalQuery;
        
        try {
            if (enableQueryRewrite) {
                // Rewrite query for better semantic matching
                RewriteQueryTransformer rewriter = RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .build();
                
                Query query = new Query(originalQuery);
                Query rewrittenQuery = rewriter.transform(query);
                processedQuery = rewrittenQuery.text();
                
                logger.debug("Query rewritten: '{}' -> '{}'", originalQuery, processedQuery);
            }
            
            if (enableMultiQuery) {
                // Expand to multiple queries and use the first one
                // (Full multi-query search would require aggregating results)
                MultiQueryExpander expander = MultiQueryExpander.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .numberOfQueries(multiQueryCount)
                        .build();
                
                List<Query> expandedQueries = expander.expand(new Query(processedQuery));
                if (!expandedQueries.isEmpty()) {
                    processedQuery = expandedQueries.get(0).text();
                    logger.debug("Multi-query expansion: generated {} variations", expandedQueries.size());
                }
            }
            
        } catch (Exception e) {
            logger.warn("Query transformation failed, using original: {}", e.getMessage());
            processedQuery = originalQuery;
        }
        
        return processedQuery;
    }

    /**
     * Get RAG service configuration info.
     */
    public RagServiceInfo getServiceInfo() {
        return new RagServiceInfo(
                topK,
                similarityThreshold,
                vectorStore.getClass().getSimpleName(),
                questionAnswerAdvisor.getClass().getSimpleName(),
                enableQueryRewrite,
                enableMultiQuery,
                multiQueryCount
        );
    }

    /**
     * RAG service configuration data.
     */
    public static class RagServiceInfo {
        private final int topK;
        private final double similarityThreshold;
        private final String retrieverType;
        private final String advisorType;
        private final boolean queryRewriteEnabled;
        private final boolean multiQueryEnabled;
        private final int multiQueryCount;

        public RagServiceInfo(int topK, double similarityThreshold, String retrieverType, String advisorType,
                            boolean queryRewriteEnabled, boolean multiQueryEnabled, int multiQueryCount) {
            this.topK = topK;
            this.similarityThreshold = similarityThreshold;
            this.retrieverType = retrieverType;
            this.advisorType = advisorType;
            this.queryRewriteEnabled = queryRewriteEnabled;
            this.multiQueryEnabled = multiQueryEnabled;
            this.multiQueryCount = multiQueryCount;
        }

        // Getters
        public int getTopK() { return topK; }
        public double getSimilarityThreshold() { return similarityThreshold; }
        public String getRetrieverType() { return retrieverType; }
        public String getAdvisorType() { return advisorType; }
        public boolean isQueryRewriteEnabled() { return queryRewriteEnabled; }
        public boolean isMultiQueryEnabled() { return multiQueryEnabled; }
        public int getMultiQueryCount() { return multiQueryCount; }

        @Override
        public String toString() {
            return String.format("RagServiceInfo{topK=%d, threshold=%.2f, retriever='%s', advisor='%s', rewrite=%s, multiQuery=%s}", 
                               topK, similarityThreshold, retrieverType, advisorType, queryRewriteEnabled, multiQueryEnabled);
        }
    }
}
