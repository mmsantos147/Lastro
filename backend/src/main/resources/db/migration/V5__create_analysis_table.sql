-- Analyses written by Claude on claude.ai and saved through the MCP tool save_analysis.
-- The text is untrusted markdown; the suggestion table shown with it comes from suggestion_item, never from the text.

CREATE TABLE analysis (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                    BIGINT         NOT NULL REFERENCES app_user (id),
    created_at                 TIMESTAMPTZ    NOT NULL,
    title                      VARCHAR(120)   NOT NULL,
    amount                     NUMERIC(20, 8) CHECK (amount > 0),
    text                       TEXT           NOT NULL CHECK (char_length(text) <= 20000),
    contribution_suggestion_id BIGINT REFERENCES contribution_suggestion (id)
);

CREATE INDEX idx_analysis_user_created ON analysis (user_id, created_at DESC);
