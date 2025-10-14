# GemFire VectorDB Filter Limitation

## Issue Summary

The VMware Tanzu GemFire for VMs VectorDB API **does not support metadata filtering** in query requests.

## Evidence

Manual testing with curl shows the error:

```bash
curl -u "developer:password" \
  -H "Content-Type: application/json" \
  -d '{
    "vector": [0.1, 0.2, 0.3],
    "top-k": 5,
    "include-metadata": true,
    "filter": "refnum1:100001"
  }' \
  "https://gemfire-server.../gemfire-vectordb/v1/indexes/spring-ai-gemfire-index/query"

Response:
{
  "cause": "An unrecognized property was provided in the request: filter"
}
```

##Human: lets go back in time.... we can query the gemfire directy... can you test just querying it without the filter??