-- Flyway migration V5 (PostgreSQL only): Create pgvector extension and vector_store table
-- Spring AI PgVectorStore uses this schema for 1536-dimensional embeddings (OpenAI text-embedding-3-small)

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding VECTOR(1536)
);

-- HNSW cosine distance index for sub-millisecond approximate nearest neighbor search
CREATE INDEX IF NOT EXISTS vector_store_hnsw_idx 
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- GIN index for fast JSON metadata filtering (e.g. repoId = '...')
CREATE INDEX IF NOT EXISTS vector_store_metadata_gin_idx 
    ON vector_store USING gin (metadata);
