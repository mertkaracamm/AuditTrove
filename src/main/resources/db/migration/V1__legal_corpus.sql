CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE raw_legal_document (
    id BIGSERIAL PRIMARY KEY,
    source_title TEXT NOT NULL,
    authority VARCHAR(80) NOT NULL,
    official_url TEXT NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    content_hash CHAR(64) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    raw_content BYTEA NOT NULL,
    UNIQUE (official_url, content_hash)
);

CREATE TABLE legal_provision (
    id BIGSERIAL PRIMARY KEY,
    raw_document_id BIGINT REFERENCES raw_legal_document(id),
    provision_key VARCHAR(160) NOT NULL UNIQUE,
    source_title TEXT NOT NULL,
    law_number VARCHAR(32),
    article VARCHAR(40) NOT NULL,
    paragraph VARCHAR(40),
    title TEXT NOT NULL,
    provision_text TEXT NOT NULL,
    authority VARCHAR(80) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    topics JSONB NOT NULL DEFAULT '[]',
    applicable_documents JSONB NOT NULL DEFAULT '[]',
    keywords JSONB NOT NULL DEFAULT '[]',
    effective_from DATE,
    effective_to DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REPEALED', 'DRAFT')),
    official_url TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(source_title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(provision_text, '')), 'B')
    ) STORED,
    embedding VECTOR(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX legal_provision_search_idx ON legal_provision USING GIN (search_vector);
CREATE INDEX legal_provision_active_idx ON legal_provision (status, effective_from, effective_to);
CREATE INDEX legal_provision_embedding_idx ON legal_provision USING hnsw (embedding vector_cosine_ops);

CREATE TABLE legal_version (
    id BIGSERIAL PRIMARY KEY,
    provision_id BIGINT NOT NULL REFERENCES legal_provision(id) ON DELETE CASCADE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    provision_text TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    change_source_url TEXT,
    UNIQUE (provision_id, valid_from)
);

CREATE TABLE legal_relation (
    source_provision_id BIGINT NOT NULL REFERENCES legal_provision(id) ON DELETE CASCADE,
    target_provision_id BIGINT NOT NULL REFERENCES legal_provision(id) ON DELETE CASCADE,
    relation_type VARCHAR(30) NOT NULL,
    PRIMARY KEY (source_provision_id, target_provision_id, relation_type)
);

CREATE TABLE audit_reference (
    audit_id UUID NOT NULL,
    provision_id BIGINT NOT NULL REFERENCES legal_provision(id),
    retrieval_score DOUBLE PRECISION,
    cited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (audit_id, provision_id)
);
