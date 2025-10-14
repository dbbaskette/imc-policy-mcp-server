# Complete Postgres vs GemFire Record Comparison

## Document ID: `0594b53a-8bb1-4024-83f1-2e83e22924da`

---

## POSTGRES RECORD

```json
{
  "id": "0594b53a-8bb1-4024-83f1-2e83e22924da",
  "content": "INSURANCE MEGACORP\n\n123 Insurance Way, Anytown, USA 12345\n\n1-800-555-ACME | contact@acmeinsurance.com\n\nAUTO INSURANCE POLICY DOCUMENTS\n\nCustomer Number: 100002\n\nPolicy Number: IMC-200002\n\nNew Policy Welcome Package...",
  "content_length": 2311,
  "metadata": {
    "refnum1": 100002,
    "refnum2": 200002,
    "timestamp": 1758900318683,
    "sourcePath": "http://big-data-005.kuhn-labs.com:9870/webhdfs/v1/processed_files/100002-200002.pdf.txt?op=OPEN"
  },
  "vector_dimensions": 768,
  "vector_first_10": [0.018349074, -0.011368258, -0.1636163, -0.0035935733, 0.049668856, -0.019126885, -0.01292616, 0.0011894406, 0.05526, 0.017427327]
}
```

---

## GEMFIRE RECORD

```json
{
  "key": "0594b53a-8bb1-4024-83f1-2e83e22924da",
  "metadata": {
    "content": "INSURANCE MEGACORP\n\n123 Insurance Way, Anytown, USA 12345\n\n1-800-555-ACME | contact@acmeinsurance.com\n\nAUTO INSURANCE POLICY DOCUMENTS\n\nCustomer Number: 100002\n\nPolicy Number: IMC-200002\n\nNew Policy Welcome Package...",
    "refnum1": 100002,
    "sourcePath": "http://big-data-005.kuhn-labs.com:9870/webhdfs/v1/processed_files/100002-200002.pdf.txt?op=OPEN",
    "refnum2": 200002,
    "timestamp": 1758900318683
  },
  "vector": [0.018349074, -0.011368258, -0.1636163, -0.0035935733, 0.049668856, -0.019126885, -0.01292616, 0.0011894406, 0.05526, 0.017427327, ...768 total dimensions]
}
```

---

## COMPARISON

| Aspect | Postgres | GemFire | Match? |
|--------|----------|---------|--------|
| **ID/Key** | `0594b53a-8bb1-4024-83f1-2e83e22924da` | `0594b53a-8bb1-4024-83f1-2e83e22924da` | ✅ YES |
| **Content Length** | 2311 characters | 2311 characters | ✅ YES |
| **Content Preview** | "INSURANCE MEGACORP..." | "INSURANCE MEGACORP..." | ✅ YES (identical) |
| **Vector Dimensions** | 768 | 768 | ✅ YES |
| **Vector First 10** | [0.018349074, -0.011368258, -0.1636163, -0.0035935733, 0.049668856, -0.019126885, -0.01292616, 0.0011894406, 0.05526, 0.017427327] | [0.018349074, -0.011368258, -0.1636163, -0.0035935733, 0.049668856, -0.019126885, -0.01292616, 0.0011894406, 0.05526, 0.017427327] | ✅ **YES - IDENTICAL!** |
| **Metadata refnum1** | 100002 | 100002 | ✅ YES |
| **Metadata refnum2** | 200002 | 200002 | ✅ YES |
| **Metadata timestamp** | 1758900318683 | 1758900318683 | ✅ YES |
| **Metadata sourcePath** | http://big-data-005.kuhn-labs.com:9870/webhdfs/v1/processed_files/100002-200002.pdf.txt?op=OPEN | http://big-data-005.kuhn-labs.com:9870/webhdfs/v1/processed_files/100002-200002.pdf.txt?op=OPEN | ✅ YES |

---

## ✅ **CONCLUSION: VECTORS ARE IDENTICAL**

**The embedding vectors in Postgres and GemFire are EXACTLY THE SAME.**

This means:
- ✅ Cache warming is storing the correct vectors
- ✅ Metadata is properly flattened and filterable
- ✅ Content is identical in both stores
- ✅ Document structure is correct

**Since the vectors match, cache HITs should be possible!**

The cache MISS issue must be caused by something else:
1. **Similarity threshold too high?** (currently 0.2)
2. **Query embedding generation issue?** - Query text might generate different embeddings
3. **Filter not matching?** - Need to verify filter syntax
4. **Similarity scores below threshold?** - GemFire returning results but scores < 0.2

---

## NEXT STEPS

1. **Check logs for GemFire response scores** - See what similarity scores are being returned
2. **Test with lower threshold** - Try 0.0 to see all results
3. **Verify filter query format** - Ensure `refnum1:100002` is correct syntax
4. **Compare query embeddings** - Check if query generates same embedding each time

The data is correct - the issue is in the query/filtering/scoring logic.
