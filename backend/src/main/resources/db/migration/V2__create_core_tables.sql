-- Core tables: users, institutions, assets, trades, transfers, corporate actions and distributions.
-- Monetary values and quantities use numeric(20, 8), matching Money and Quantity in the domain.
-- Every monetary column has a sibling currency column.

CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE institution (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(10)  NOT NULL CHECK (type IN ('BROKER', 'BANK', 'EXCHANGE', 'WALLET')),
    cnpj VARCHAR(14) UNIQUE,
    CONSTRAINT ck_institution_cnpj CHECK (
        (type IN ('BROKER', 'BANK') AND cnpj IS NOT NULL)
        OR type = 'EXCHANGE'
        OR (type = 'WALLET' AND cnpj IS NULL))
);

-- Root of the asset hierarchy (JPA JOINED inheritance); subtypes get their own tables.
CREATE TABLE asset (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(150) NOT NULL,
    asset_class    VARCHAR(20)  NOT NULL CHECK (asset_class IN ('STOCK', 'FII', 'ETF', 'TREASURY', 'CRYPTO')),
    cnpj           VARCHAR(14),
    quote_currency VARCHAR(3)   NOT NULL CHECK (quote_currency IN ('BRL', 'USD')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE trade (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES app_user (id),
    asset_id       BIGINT         NOT NULL REFERENCES asset (id),
    institution_id BIGINT         NOT NULL REFERENCES institution (id),
    type           VARCHAR(4)     NOT NULL CHECK (type IN ('BUY', 'SELL')),
    trade_date     DATE           NOT NULL,
    quantity       NUMERIC(20, 8) NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(20, 8) NOT NULL CHECK (unit_price >= 0),
    price_currency VARCHAR(3)     NOT NULL CHECK (price_currency IN ('BRL', 'USD')),
    -- Fee paid in a currency (amount + currency) or in units of an asset (asset + quantity), never both.
    fee_amount     NUMERIC(20, 8) CHECK (fee_amount >= 0),
    fee_currency   VARCHAR(3) CHECK (fee_currency IN ('BRL', 'USD')),
    fee_asset_id   BIGINT REFERENCES asset (id),
    fee_quantity   NUMERIC(20, 8) CHECK (fee_quantity >= 0),
    source         VARCHAR(20)    NOT NULL CHECK (source IN ('MANUAL', 'B3_IMPORT', 'BINANCE_API', 'BINANCE_CSV')),
    external_id    VARCHAR(100),
    CONSTRAINT ck_trade_fee CHECK (
        (fee_amount IS NOT NULL AND fee_currency IS NOT NULL AND fee_asset_id IS NULL AND fee_quantity IS NULL)
        OR (fee_amount IS NULL AND fee_currency IS NULL AND fee_asset_id IS NOT NULL AND fee_quantity IS NOT NULL))
);

CREATE INDEX idx_trade_user_asset_date ON trade (user_id, asset_id, trade_date);
-- Idempotent sync: the same external id can only be recorded once per institution.
CREATE UNIQUE INDEX uk_trade_external_id ON trade (institution_id, external_id) WHERE external_id IS NOT NULL;

CREATE TABLE transfer (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT         NOT NULL REFERENCES app_user (id),
    asset_id            BIGINT         NOT NULL REFERENCES asset (id),
    from_institution_id BIGINT         NOT NULL REFERENCES institution (id),
    to_institution_id   BIGINT         NOT NULL REFERENCES institution (id),
    transfer_date       DATE           NOT NULL,
    quantity            NUMERIC(20, 8) NOT NULL CHECK (quantity > 0),
    fee                 NUMERIC(20, 8) NOT NULL CHECK (fee >= 0),
    source              VARCHAR(20)    NOT NULL
        CHECK (source IN ('MANUAL', 'B3_IMPORT', 'BINANCE_API', 'BINANCE_CSV')),
    external_id         VARCHAR(100),
    CONSTRAINT ck_transfer_institutions CHECK (from_institution_id <> to_institution_id),
    CONSTRAINT ck_transfer_fee CHECK (fee <= quantity)
);

CREATE INDEX idx_transfer_user_asset_date ON transfer (user_id, asset_id, transfer_date);
CREATE UNIQUE INDEX uk_transfer_external_id ON transfer (from_institution_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE TABLE corporate_action (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id      BIGINT         NOT NULL REFERENCES asset (id),
    type          VARCHAR(20)    NOT NULL
        CHECK (type IN ('STOCK_SPLIT', 'REVERSE_SPLIT', 'BONUS_SHARES', 'AMORTIZATION', 'MERGER')),
    record_date   DATE           NOT NULL,
    factor        NUMERIC(20, 8) NOT NULL CHECK (factor > 0),
    -- Always BRL: corporate actions come from B3-listed companies.
    assigned_cost NUMERIC(20, 8) CHECK (assigned_cost >= 0),
    CONSTRAINT uk_corporate_action UNIQUE (asset_id, type, record_date),
    CONSTRAINT ck_corporate_action_bonus_cost CHECK (type <> 'BONUS_SHARES' OR assigned_cost IS NOT NULL)
);

CREATE TABLE distribution (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT         NOT NULL REFERENCES app_user (id),
    asset_id         BIGINT         NOT NULL REFERENCES asset (id),
    institution_id   BIGINT         NOT NULL REFERENCES institution (id),
    type             VARCHAR(30)    NOT NULL
        CHECK (type IN ('DIVIDEND', 'JCP', 'FII_INCOME', 'FIXED_INCOME_INTEREST', 'STAKING_REWARD')),
    record_date      DATE           NOT NULL,
    payment_date     DATE           NOT NULL,
    base_quantity    NUMERIC(20, 8) NOT NULL CHECK (base_quantity >= 0),
    currency         VARCHAR(3)     NOT NULL CHECK (currency IN ('BRL', 'USD')),
    gross_amount     NUMERIC(20, 8) NOT NULL CHECK (gross_amount >= 0),
    withheld_tax     NUMERIC(20, 8) NOT NULL CHECK (withheld_tax >= 0),
    net_amount       NUMERIC(20, 8) NOT NULL,
    paid_in_asset_id BIGINT REFERENCES asset (id),
    paid_quantity    NUMERIC(20, 8) CHECK (paid_quantity > 0),
    CONSTRAINT ck_distribution_net CHECK (net_amount = gross_amount - withheld_tax),
    CONSTRAINT ck_distribution_dates CHECK (payment_date >= record_date),
    CONSTRAINT ck_distribution_paid_in_asset CHECK ((paid_in_asset_id IS NULL) = (paid_quantity IS NULL)),
    CONSTRAINT ck_distribution_staking CHECK (type <> 'STAKING_REWARD' OR paid_in_asset_id IS NOT NULL)
);

CREATE INDEX idx_distribution_user_payment_date ON distribution (user_id, payment_date);
