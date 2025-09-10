-- Create required extensions for Spring AI 1.1 PGVector support
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create the vector_store table for insurance policy documents
-- Note: Spring AI 1.1 auto-configuration will create this table if initialize-schema=true
-- This schema is provided for reference and manual setup if needed
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content TEXT NOT NULL,
    metadata JSON NOT NULL,
    embedding VECTOR(768) NOT NULL  -- 768 dimensions for embedding model
);

-- Create index for efficient vector similarity search (HNSW)
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store 
USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);