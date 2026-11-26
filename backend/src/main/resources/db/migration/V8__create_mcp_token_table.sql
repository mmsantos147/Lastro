-- OAuth tokens issued to MCP clients such as claude.ai (ADR 0011). Only hashes are stored.

CREATE TABLE mcp_token (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    kind       VARCHAR(10)  NOT NULL CHECK (kind IN ('ACCESS', 'REFRESH')),
    user_id    BIGINT       NOT NULL REFERENCES app_user (id),
    client_id  VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_mcp_token_expires_at ON mcp_token (expires_at);
