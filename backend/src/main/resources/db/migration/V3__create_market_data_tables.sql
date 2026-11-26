-- Market data: daily closing quotes per asset and economic indicators (CDI, Selic, IPCA, Ibovespa, USD/BRL).
-- Both are refreshed by scheduled jobs and are keyed by date, so refreshes are idempotent upserts.

CREATE TABLE quote (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id    BIGINT         NOT NULL REFERENCES asset (id),
    quote_date  DATE           NOT NULL,
    close_price NUMERIC(20, 8) NOT NULL CHECK (close_price >= 0),
    currency    VARCHAR(3)     NOT NULL CHECK (currency IN ('BRL', 'USD')),
    source      VARCHAR(10)    NOT NULL CHECK (source IN ('BRAPI', 'BINANCE', 'MANUAL')),
    CONSTRAINT uk_quote UNIQUE (asset_id, quote_date, currency)
);

CREATE TABLE economic_indicator (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type           VARCHAR(10)    NOT NULL CHECK (type IN ('CDI', 'SELIC', 'IPCA', 'IBOV', 'USD_BRL')),
    indicator_date DATE           NOT NULL,
    value          NUMERIC(20, 8) NOT NULL,
    CONSTRAINT uk_economic_indicator UNIQUE (type, indicator_date)
);
