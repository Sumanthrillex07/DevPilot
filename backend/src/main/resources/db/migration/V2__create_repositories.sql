CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_repo_id BIGINT NOT NULL,
    owner VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    full_name VARCHAR(300) NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT false,
    default_branch VARCHAR(100) NOT NULL,
    language VARCHAR(100),
    html_url VARCHAR(500),
    description TEXT,
    index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    indexed_at TIMESTAMP WITH TIME ZONE,
    chunk_count INT NOT NULL DEFAULT 0,
    files_total INT NOT NULL DEFAULT 0,
    files_processed INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_repositories_user_github_repo UNIQUE (user_id, github_repo_id)
);

CREATE INDEX idx_repositories_user_id ON repositories(user_id);
CREATE INDEX idx_repositories_full_name ON repositories(full_name);
