package io.matheusms.lastro.domain.model.statement;

import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.TradeType;
import java.util.Objects;

/**
 * One trade of a {@link StatementEntry.TradeGroup}.
 *
 * @param unitPrice excluding fees
 */
public record TradeLeg(AssetReference asset, TradeType type, Quantity quantity, Money unitPrice, LegFee fee) {

    public TradeLeg {
        Objects.requireNonNull(asset, "asset must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        Objects.requireNonNull(fee, "fee must not be null");
    }

    /** Fee of an imported trade; the asset of a fee paid in an asset is resolved by the import. */
    public sealed interface LegFee {

        LegFee NONE = new None();

        record None() implements LegFee {
        }

        record InCurrency(Money amount) implements LegFee {

            public InCurrency {
                Objects.requireNonNull(amount, "amount must not be null");
            }
        }

        record InAsset(AssetReference asset, Quantity quantity) implements LegFee {

            public InAsset {
                Objects.requireNonNull(asset, "asset must not be null");
                Objects.requireNonNull(quantity, "quantity must not be null");
            }
        }
    }
}
