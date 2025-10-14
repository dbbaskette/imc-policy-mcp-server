# GemFire Cache Miss Root Cause Analysis

## 🎯 **ROOT CAUSE IDENTIFIED**

**The metadata is NOT being flattened when stored in GemFire!**

### What's Wrong

**Postgres Structure** (correct):
```json
{
  "refnum1": 100002,
  "refnum2": 200002,
  "timestamp": 1758900318683,
  "sourcePath": "..."
}
```

**GemFire Structure** (WRONG - nested!):
```json
{
  "content": "INSURANCE MEGACORP...",
  "metadata": "{\"refnum1\": 100002, \"refnum2\": 200002, ...}"
}
```

**Expected GemFire Structure** (what we need):
```json
{
  "content": "INSURANCE MEGACORP...",
  "refnum1": 100002,
  "refnum2": 200002,
  "timestamp": 1758900318683,
  "sourcePath": "..."
}
```

### Why Filters Don't Work

When metadata is nested as a JSON string:
- `refnum1:100002` → Can't find `refnum1` field (it's inside a string!)
- `refnum1==100002` → Syntax error in GemFire

When metadata is properly flattened:
- `refnum1:100002` → Should work! ✅
- Field is at top level, GemFire can filter on it

### The Bug

`DirectGemFireCacheWarmer.java` has code to flatten metadata (lines 102-115):

```java
Map<String, Object> gemfireMetadata = new HashMap<>();
gemfireMetadata.put("content", content);

try {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> parsedMetadata = mapper.readValue(metadataJson, Map.class);
    // Add metadata fields at top level for filtering
    gemfireMetadata.putAll(parsedMetadata);  // This SHOULD flatten!
} catch (Exception e) {
    logger.warn("Failed to parse metadata JSON, storing as string: {}", e.getMessage());
    gemfireMetadata.put("metadata", metadataJson);  // Falls back to nested
}
```

**But GemFire still has nested metadata!**

Possible causes:
1. The exception is being caught and falling back to nested storage
2. There's another code path storing documents (not using `DirectGemFireCacheWarmer`)
3. GemFire is somehow re-wrapping the flattened metadata
4. The old code ran before the fix was deployed

### Test Results

**Filter syntax testing:**
- `refnum1:100002` with nested metadata → 0 results ❌
- `refnum1==100002` with nested metadata → 500 error ❌
- `refnum1:100002` with flattened metadata → Should work! (not tested yet)

### What Needs to Happen

1. **Fix the metadata flattening** in `DirectGemFireCacheWarmer`
   - Add debug logging to see if exception is being thrown
   - Verify `putAll()` is actually called
   - Check what's being sent to GemFire

2. **Clear the cache completely**
   ```bash
   curl -X DELETE -u "$CLUSTER_OP_CREDS" \
     "$GEMFIRE_URL/indexes/spring-ai-gemfire-index"

   curl -X POST -u "$CLUSTER_OP_CREDS" \
     -d '{"name": "spring-ai-gemfire-index", "fields": ["refnum1", "refnum2"...]}' \
     "$GEMFIRE_URL/indexes"
   ```

3. **Re-warm with fixed code**
   ```bash
   curl -X POST "$APP_URL/api/cache/warm/100002"
   ```

4. **Verify flattened structure**
   ```bash
   curl -u "$CLUSTER_OP_CREDS" \
     "$GEMFIRE_URL/indexes/spring-ai-gemfire-index/embeddings/{docId}" \
     | jq '.metadata | keys'
   ```
   Should show: `["content", "refnum1", "refnum2", "sourcePath", "timestamp"]`
   NOT: `["content", "metadata"]`

5. **Test filter query**
   ```bash
   # Should return results!
   curl -X POST -u "$DEV_CREDS" \
     -d '{"vector": [...], "top-k": 5, "filter-query": "refnum1:100002"}' \
     "$GEMFIRE_URL/indexes/spring-ai-gemfire-index/query"
   ```

### Current Status

- ✅ GemFire index has filterable fields configured
- ✅ Filter syntax identified: `refnum1:100002` (colon, not equals)
- ✅ Code to flatten metadata exists
- ❌ **Metadata is NOT actually flattened in GemFire**
- ❌ Cache queries return empty results
- ❌ No cache HITs occurring

###Next Debug Steps

1. Add extensive logging to `DirectGemFireCacheWarmer`:
   ```java
   logger.info("Metadata before flattening: {}", metadataJson);
   logger.info("Parsed metadata keys: {}", parsedMetadata.keySet());
   logger.info("GemFire metadata after putAll: {}", gemfireMetadata.keySet());
   logger.info("Sending to GemFire: {}", objectMapper.writeValueAsString(gemfireEmbedding));
   ```

2. Check GemFire response after POSTing embedding

3. Verify the actual HTTP request body being sent to GemFire

4. Test manually with curl to confirm GemFire accepts flattened metadata

---

**The cache infrastructure is 99% complete - we just need to fix the metadata flattening bug!** 🚀
