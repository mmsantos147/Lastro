-- Target allocation (strategy) and the deterministic contribution suggestions computed from it.
-- Percentages are stored as percent values (25.00 = 25%); tolerances are percentage points.

CREATE TABLE strategy (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES app_user (id),
    name    VARCHAR(100) NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT FALSE
);

-- At most one active strategy per user.
CREATE UNIQUE INDEX uk_strategy_active ON strategy (user_id) WHERE active;

-- A target is either an asset class or a specific asset (which then leaves its class bucket).
CREATE TABLE target_allocation (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_id       BIGINT        NOT NULL REFERENCES strategy (id) ON DELETE CASCADE,
    asset_class       VARCHAR(20) CHECK (asset_class IN ('STOCK', 'FII', 'ETF', 'TREASURY', 'CRYPTO')),
    asset_id          BIGINT REFERENCES asset (id),
    target_percentage NUMERIC(7, 4) NOT NULL CHECK (target_percentage >= 0 AND target_percentage <= 100),
    min_tolerance     NUMERIC(7, 4) NOT NULL CHECK (min_tolerance >= 0),
    max_tolerance     NUMERIC(7, 4) NOT NULL CHECK (max_tolerance >= 0),
    max_per_asset     NUMERIC(7, 4) CHECK (max_per_asset > 0 AND max_per_asset <= 100),
    CONSTRAINT ck_target_allocation_bucket CHECK ((asset_class IS NULL) <> (asset_id IS NULL))
);

CREATE TABLE contribution_suggestion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT         NOT NULL REFERENCES app_user (id),
    strategy_id BIGINT         NOT NULL REFERENCES strategy (id),
    created_at  TIMESTAMPTZ    NOT NULL,
    amount      NUMERIC(20, 8) NOT NULL CHECK (amount > 0),
    source      VARCHAR(3)     NOT NULL CHECK (source IN ('WEB', 'MCP'))
);

CREATE INDEX idx_contribution_suggestion_user_created ON contribution_suggestion (user_id, created_at);

CREATE TABLE suggestion_item (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    suggestion_id      BIGINT         NOT NULL REFERENCES contribution_suggestion (id) ON DELETE CASCADE,
    position           INTEGER        NOT NULL,
    asset_class        VARCHAR(20)    NOT NULL CHECK (asset_class IN ('STOCK', 'FII', 'ETF', 'TREASURY', 'CRYPTO')),
    asset_id           BIGINT REFERENCES asset (id),
    suggested_amount   NUMERIC(20, 8) NOT NULL CHECK (suggested_amount >= 0),
    reason             VARCHAR(30)    NOT NULL,
    current_percentage NUMERIC(7, 4)  NOT NULL,
    target_percentage  NUMERIC(7, 4)  NOT NULL
);
