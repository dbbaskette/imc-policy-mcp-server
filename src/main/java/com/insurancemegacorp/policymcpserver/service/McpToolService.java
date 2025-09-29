package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MCP Tool Service exposing insurance policy document retrieval via RAG.
 */
@Component
public class McpToolService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolService.class);

    @Autowired
    private RagService ragService;

    /**
     * Answer questions about customer's insurance policy documents using RAG.
     */
    @McpTool(name = "answerQuery", 
             description = "Answer questions about insurance policy documents using customer-specific context retrieval",
             annotations = @McpTool.McpAnnotations(
                 title = "Insurance Policy Query Tool",
                 readOnlyHint = true,
                 destructiveHint = false,
                 idempotentHint = true
             ))
    public PolicyQueryResult answerQuery(
            @McpToolParam(description = "Customer's question about their insurance policy", required = true) 
            String query,
            @McpToolParam(description = "Customer ID to filter policy documents", required = true) 
            Integer customerId) {
        
        logger.info("MCP Tool: answerQuery called with customerId={}, query='{}'", customerId, query);
        
        try {
            if (!StringUtils.hasText(query)) return PolicyQueryResult.error("Query cannot be empty");
            if (customerId == null) return PolicyQueryResult.error("Customer ID is required");
            
            long startTime = System.currentTimeMillis();
            String context = ragService.processQuery(query, customerId);
            long processingTime = System.currentTimeMillis() - startTime;
            
            logger.info("MCP Tool: Query processed successfully for customer {} in {}ms", customerId, processingTime);
            
            return PolicyQueryResult.success(query, customerId, context, processingTime);
            
        } catch (Exception e) {
            logger.error("MCP Tool: Query failed for customer {}: {}", customerId, e.getMessage(), e);
            return PolicyQueryResult.error("Failed to process query: " + e.getMessage());
        }
    }

    /**
     * Debug tool to test vector search with different similarity thresholds.
     */
    @McpTool(name = "debugSearch", 
             description = "Debug vector search with adjustable similarity threshold for troubleshooting",
             annotations = @McpTool.McpAnnotations(
                 title = "Vector Search Debug Tool",
                 readOnlyHint = true,
                 destructiveHint = false,
                 idempotentHint = true
             ))
    public DebugSearchResult debugSearch(
            @McpToolParam(description = "Search query", required = true) 
            String query,
            @McpToolParam(description = "Customer ID to filter policy documents", required = true) 
            Integer customerId,
            @McpToolParam(description = "Similarity threshold (0.0-1.0)", required = false) 
            Double threshold) {
        
        logger.info("MCP Tool: debugSearch called with customerId={}, query='{}', threshold={}", 
                   customerId, query, threshold);
        
        try {
            if (!StringUtils.hasText(query)) return DebugSearchResult.error("Query cannot be empty");
            if (customerId == null) return DebugSearchResult.error("Customer ID is required");
            
            double searchThreshold = threshold != null ? threshold : 0.1; // Lower default for debugging
            
            String result = ragService.debugSearch(query, customerId, searchThreshold);
            
            return DebugSearchResult.success(query, customerId, searchThreshold, result);
            
        } catch (Exception e) {
            logger.error("MCP Tool: Debug search failed for customer {}: {}", customerId, e.getMessage(), e);
            return DebugSearchResult.error("Failed to process debug search: " + e.getMessage());
        }
    }

    // TODO: Future customer data retrieval tool
    /*
    @McpTool(name = "queryCustomer", 
             description = "Retrieve customer contact and account information (non-policy data)",
             annotations = @McpTool.McpAnnotations(
                 title = "Customer Information Tool",
                 readOnlyHint = true,
                 destructiveHint = false,
                 idempotentHint = true
             ))
    public CustomerResult queryCustomer(
            @McpToolParam(description = "Customer ID to retrieve information for", required = true) 
            Integer customerId) {
        
        // TODO: Implement customer database queries for contact info, account status, etc.
        
        return CustomerResult.notImplemented("Customer queries not yet implemented");
    }
    */

    /**
     * Policy query result.
     */
    public static class PolicyQueryResult {
        private final boolean success;
        private final String query;
        private final Integer customerId;
        private final String context;
        private final Long processingTimeMs;
        private final String error;
        private final long timestamp;

        private PolicyQueryResult(boolean success, String query, Integer customerId, 
                                String context, Long processingTimeMs, String error) {
            this.success = success;
            this.query = query;
            this.customerId = customerId;
            this.context = context;
            this.processingTimeMs = processingTimeMs;
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }

        public static PolicyQueryResult success(String query, Integer customerId, String context, long processingTimeMs) {
            return new PolicyQueryResult(true, query, customerId, context, processingTimeMs, "");
        }

        public static PolicyQueryResult error(String error) {
            return new PolicyQueryResult(false, null, null, null, null, error != null ? error : "Unknown error");
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getQuery() { return query; }
        public Integer getCustomerId() { return customerId; }
        public String getContext() { return context; }
        public Long getProcessingTimeMs() { return processingTimeMs; }
        public String getError() { return error; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Debug search result.
     */
    public static class DebugSearchResult {
        private final boolean success;
        private final String query;
        private final Integer customerId;
        private final Double threshold;
        private final String result;
        private final String error;
        private final long timestamp;

        private DebugSearchResult(boolean success, String query, Integer customerId, 
                                Double threshold, String result, String error) {
            this.success = success;
            this.query = query;
            this.customerId = customerId;
            this.threshold = threshold;
            this.result = result;
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }

        public static DebugSearchResult success(String query, Integer customerId, Double threshold, String result) {
            return new DebugSearchResult(true, query, customerId, threshold, result, null);
        }

        public static DebugSearchResult error(String error) {
            return new DebugSearchResult(false, null, null, null, null, error);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getQuery() { return query; }
        public Integer getCustomerId() { return customerId; }
        public Double getThreshold() { return threshold; }
        public String getResult() { return result; }
        public String getError() { return error; }
        public long getTimestamp() { return timestamp; }
    }
}