# 🤔 RAG Tool Implementation Requirements - Issue 9

Please fill in your answers below and let me know when to start implementation.

---

## 1. 📊 **Vector Store Data Expectations**

**Question:** Since document population is handled externally, should I implement the RAG pipeline to gracefully handle an empty vector store for now?


**Your Answer:** I put a vector_store.csv file that has 1 entry.  Use it to create maybe 5 records for the Vectorstore to load it.

---

## 2. 👤 **Customer Filtering Strategy**

**Question:** How should customer filtering work in the vector store?

- A) Filter by customer ID in document metadata fields



---

## 3. 🔍 **RAG Pipeline Configuration**

### Query Expansion
**Question:** Should the LLM expand queries to include insurance-specific terminology?

- A) Yes, add insurance-specific terms and synonyms



### Similarity Threshold
**Question:** What similarity score threshold for relevant documents?

- A) 0.7 (more restrictive, higher quality)


### Result Limits
**Question:** How many documents should be retrieved for context?


- C) Configurable limit (default: 5 or less)


---

## 4. 🛡️ **Error Handling Scenarios**

**Question:** Which error cases should be handled explicitly? (Check all that apply)

- [x ] No documents found for customer
- [x ] Vector store connectivity issues
- [x] LLM/Embedding model failures
- [x ] Invalid customer ID
- [x ] Empty/null questions
- [ ] Query too long/complex


---

## 5. 📝 **Response Format**

**Question:** What should the tool return?

- D) Structured JSON with full metadata



## 6. 🧪 **Testing Strategy**

**Question:** What testing approach should I use?


- E) All of the above
