-- Phase 2: private fixed income (CDB, LCI, LCA) valued on the curve (ADR 0010).

ALTER TABLE asset DROP CONSTRAINT asset_asset_class_check;
ALTER TABLE asset ADD CONSTRAINT asset_asset_class_check
    CHECK (asset_class IN ('STOCK', 'FII', 'ETF', 'TREASURY', 'CRYPTO', 'FIXED_INCOME'));

ALTER TABLE target_allocation DROP CONSTRAINT target_allocation_asset_class_check;
ALTER TABLE target_allocation ADD CONSTRAINT target_allocation_asset_class_check
    CHECK (asset_class IN ('STOCK', 'FII', 'ETF', 'TREASURY', 'CRYPTO', 'FIXED_INCOME'));

ALTER TABLE suggestion_item DROP CONSTRAINT suggestion_item_asset_class_check;
ALTER TABLE suggestion_item ADD CONSTRAINT suggestion_item_asset_class_check
    CHECK (asset_class IN ('STOCK', 'FII', 'ETF', 'TREASURY', 'CRYPTO', 'FIXED_INCOME'));

-- Curve values are stored as quotes, so every valuation (positions, timeline, allocation) uses them unchanged.
ALTER TABLE quote DROP CONSTRAINT quote_source_check;
ALTER TABLE quote ADD CONSTRAINT quote_source_check
    CHECK (source IN ('BRAPI', 'BINANCE', 'MANUAL', 'CURVE'));

-- Subtype of asset (ADR 0002): the terms that give a private fixed income security its value on the curve.
CREATE TABLE fixed_income_security (
    asset_id           BIGINT PRIMARY KEY REFERENCES asset (id),
    issuer             VARCHAR(150),
    indexer            VARCHAR(10)   NOT NULL CHECK (indexer IN ('CDI', 'SELIC', 'IPCA', 'PREFIXED')),
    -- % of CDI/Selic (110 = 110% do CDI); only for those indexers
    indexer_percentage NUMERIC(7, 4) CHECK (indexer_percentage > 0),
    -- Annual %: fixed rate, real rate over IPCA, or spread over CDI/Selic
    rate               NUMERIC(7, 4) NOT NULL CHECK (rate >= 0),
    maturity_date      DATE,
    daily_liquidity    BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_fixed_income_percentage CHECK ((indexer IN ('CDI', 'SELIC')) = (indexer_percentage IS NOT NULL))
);
