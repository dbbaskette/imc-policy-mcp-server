-- Database Schema for IMC Policy MCP Server (PostgreSQL with PGVector + Spring AI)

-- Enable the vector extension (required for pgvector operations)
CREATE EXTENSION IF NOT EXISTS vector;

-- Create customers table (preserve existing customer functionality)
CREATE TABLE IF NOT EXISTS customers (
    customer_id INTEGER PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10)
);

-- Spring AI VectorStore table (explicit creation for reliable initialization)
-- This follows Spring AI's standard vector_store schema
-- Reference: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
CREATE TABLE IF NOT EXISTS vector_store (
    id TEXT PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(768)  -- Ollama nomic-embed-text uses 768 dimensions
);