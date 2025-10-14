# Cache Miss Debugging - Summary

## ✅ What We've Confirmed

### 1. **Data Structure is PERFECT**
- ✅ Postgres and GemFire have **IDENTICAL** vectors
- ✅ Metadata is properly flattened (`refnum1`, `refnum2` at top level in GemFire)
- ✅ Content matches exactly (2311 characters)
- ✅ Vector dimensions match (768)
- ✅ Filter fields are configured in the GemFire index

**Example Document Comparison (ID: `0594b53a-8bb1-4024-83f1-2e83e22924da`):**

| Field | Postgres | GemFire | Match? |
|-------|----------|---------|--------|
| Vector (first 10) | [0.018349074, -0.011368258, -0.1636163, ...] | [0.018349074, -0.011368258, -0.1636163, ...] | ✅ **IDENTICAL** |
| Content Length | 2311 | 2311 | ✅ YES |
| refnum1 | 100002 | 100002 | ✅ YES |
| refnum2 | 200002 | 200002 | ✅ YES |

### 2. **Cache Warming Works**
- ✅ API successfully warms cache: `/api/cache/warm/{customerId}`
- ✅ 7 documents cached for customer 100001
- ✅ 12 documents cached for customer 100002
- ✅ GemFire index shows correct number of embeddings
- ✅ Metrics are being recorded

### 3. **Filter Query is Correct**
- ✅ Filter format: `refnum1:100002`
- ✅ GemFire returns 200 OK for queries
- ✅ Filter parameter name: `filter-query` (correct)

### 4. **Logging is Enhanced**
Added detailed logging to see:
- GemFire query requests (topK, filter, vector length)
- GemFire response scores
- Cache HIT/MISS reasons

---

## ❓ What We Don't Know Yet

### **Why are queries resulting in cache MISSes?**

The vectors are identical, so the issue is likely one of these:

### Theory 1: **No Results Returned from GemFire**
- GemFire query returns empty array `[]`
- Filter might not be matching (syntax issue?)
- Index might not be using filterable fields correctly

**How to test:**
```bash
# Query GemFire directly with filter
curl -X POST \
  -u "developer_XXX:password" \
  -H "Content-Type: application/json" \
  -d '{
    "vector": [0.018349074, -0.011368258, -0.1636163, ...],
    "top-k": 5,
    "include-metadata": true,
    "filter-query": "refnum1:100002"
  }' \
  "https://gemfire-server-.../gemfire-vectordb/v1/indexes/spring-ai-gemfire-index/query"
```

### Theory 2: **Scores Below Threshold**
- GemFire returns results but similarity scores < 0.2
- Results get filtered out by `.filter(r -> r.score >= request.getSimilarityThreshold())`

**Expected behavior:**
- If you query with THE EXACT SAME text that was stored, score should be ~1.0
- If you query with similar text, score might be 0.3-0.8
- Score of < 0.2 would mean very different semantic meaning

**Note:** 0.2 threshold is actually quite permissive for cosine similarity!

### Theory 3: **Query Embedding Generation**
- Cache is warmed with embedding from Postgres (generated at ETL time)
- Query generates a NEW embedding at query time
- If embeddings are generated differently, they won't match

**Example scenario:**
- Cached: embedding of "INSURANCE MEGACORP\n\n123 Insurance Way..."  (full text, 2311 chars)
- Query: embedding of "What is my policy coverage?" (different text!)
- These would have different vectors → low similarity score

**This is actually expected behavior!** The cache should work like this:
1. User asks: "What is my policy coverage?"
2. System generates embedding for that question
3. Searches GemFire for similar document embeddings
4. Returns documents with similar semantic meaning

---

## 🧪 **How to Test Cache HITs**

### Test 1: Direct Query Simulation
Query GemFire with the exact same vector that's stored:

```bash
# 1. Get the stored vector for a document
DOC_ID="0594b53a-8bb1-4024-83f1-2e83e22924da"
curl -u "cluster_operator:password" \
  "https://gemfire-server-.../embeddings/$DOC_ID" \
  | jq '.vector' > /tmp/vector.json

# 2. Query with that exact vector and filter
curl -X POST \
  -u "developer:password" \
  -H "Content-Type: application/json" \
  -d "{
    \"vector\": $(cat /tmp/vector.json),
    \"top-k\": 5,
    \"include-metadata\": true,
    \"filter-query\": \"refnum1:100002\"
  }" \
  "https://gemfire-server-.../query"
```

**Expected:** Should return the same document with score = 1.0 (perfect match)

### Test 2: MCP Query with Logging
Make an MCP query and check logs for:
```
GemFire query request: topK=5, filter=refnum1:100002, vectorLength=768
GemFire returned result: score=0.85, key=abc-123, threshold=0.2
Cache HIT: Found 3 documents in cache
```

### Test 3: Check What GemFire Actually Returns
Add temporary logging to see raw GemFire response:

```java
.bodyToFlux(QueryResponse.class)
.doOnNext(r -> logger.info("RAW GemFire response: key={}, score={}, metadata keys={}",
    r.key, r.score, r.metadata != null ? r.metadata.keySet() : "null"))
```

---

## 📊 **Current Status**

**Diagnostics Added:**
- ✅ `/api/diagnostic/compare/{customerId}` - Compare Postgres vs GemFire data
- ✅ `/api/diagnostic/postgres-full/{docId}` - Get full Postgres record
- ✅ `/api/diagnostic/full-compare/{docId}` - Complete comparison with vectors
- ✅ Enhanced logging in `AuthenticatedGemFireVectorStore`

**What's Working:**
- ✅ Cache warming
- ✅ Data storage (vectors identical)
- ✅ Filter configuration
- ✅ Metrics collection

**What to Test:**
- 🔄 Actual MCP query to see if cache is checked
- 🔄 GemFire response scores
- 🔄 Filter query matching

---

## 🎯 **Next Steps**

1. **Trigger an MCP query** with customer 100002:
   ```json
   {
     "name": "answerQuery",
     "arguments": {
       "query": "What vehicles are covered?",
       "customerId": 100002
     }
   }
   ```

2. **Check logs immediately** for:
   - "🔍 Attempting cache lookup"
   - "GemFire query request: topK=X, filter=refnum1:100002"
   - "GemFire returned result: score=X.XX"
   - "✅ Cache HIT" or "❌ Cache MISS"

3. **If MISS, check:**
   - Were any results returned? (count)
   - What were the scores?
   - Was the filter applied correctly?

4. **If scores < 0.2:**
   - This means the query embedding is very different from stored embeddings
   - **This is expected** if querying with different text than what's stored
   - Cache is working correctly, just no semantic match

5. **If no results returned:**
   - Filter might not be matching
   - Test direct GemFire query with curl
   - Check if `refnum1:100002` syntax is correct for GemFire

---

## 💡 **Important Insight**

**The cache doesn't store "questions and answers"** - it stores **document embeddings**.

When you query "What is my policy coverage?", the system:
1. Generates an embedding for that question
2. Searches for documents with **similar semantic meaning**
3. Returns the most relevant documents

**A cache HIT means:** "Found documents in cache that are semantically similar to your query"

**A cache MISS might mean:**
- No documents in cache match the query semantically (score < 0.2)
- Filter eliminated all results (wrong customer)
- GemFire returned empty results (filter syntax issue)

The threshold of 0.2 is actually quite low - it should match even loosely related content.

---

## 🔍 **Diagnostic Endpoints**

### Compare Data Structures
```bash
curl "https://imc-policy-mcp-server.../api/diagnostic/compare/100002"
```

### Get Complete Record Comparison
```bash
curl "https://imc-policy-mcp-server.../api/diagnostic/full-compare/0594b53a-8bb1-4024-83f1-2e83e22924da"
```

### Check Cache Stats
```bash
curl "https://imc-policy-mcp-server.../api/cache/stats"
curl "https://imc-policy-mcp-server.../actuator/metrics/vectorstore.cache.hits"
curl "https://imc-policy-mcp-server.../actuator/metrics/vectorstore.cache.misses"
```

### Monitor Logs
```bash
cf logs imc-policy-mcp-server | grep -E "(Cache HIT|Cache MISS|GemFire returned)"
```
