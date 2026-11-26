package io.matheusms.lastro.domain.model.statement;

import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Something an imported file says happened, already translated from the file's vocabulary into the domain's.
 * Every entry keeps the line it came from and a description of it (for the import report), and a fingerprint: the
 * same row always has the same fingerprint, even in another export of the same history, which makes re-imports
 * idempotent.
 */
public sealed interface StatementEntry {

    int line();

    LocalDate date();

    String fingerprint();

    /** Short text identifying the source row, e.g. {@code "Compra · PETR4 · 100"}. */
    String description();

    /**
     * Trades that happened together and are recorded all or nothing, e.g. buying BTC with USDT is a BTC buy plus a
     * USDT sell.
     */
    record TradeGroup(int line, LocalDate date, String fingerprint, String description, List<TradeLeg> legs)
            implements StatementEntry {

        public TradeGroup {
            Objects.requireNonNull(date, "date must not be null");
            Objects.requireNonNull(fingerprint, "fingerprint must not be null");
            legs = List.copyOf(legs);
            if (legs.isEmpty()) {
                throw new IllegalArgumentException("A trade group needs at least one trade");
            }
        }
    }

    /** Income paid in cash: the file gives the payment date only, used as record date too. */
    record DistributionEntry(
            int line, LocalDate date, String fingerprint, String description, AssetReference asset,
            DistributionType type, Quantity baseQuantity, Money amount) implements StatementEntry {

        public DistributionEntry {
            Objects.requireNonNull(date, "date must not be null");
            Objects.requireNonNull(fingerprint, "fingerprint must not be null");
            Objects.requireNonNull(asset, "asset must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(baseQuantity, "baseQuantity must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }

    /**
     * Split, reverse split or bonus shares reported as quantities, not factors. The import derives the factor from
     * the quantity held on the date.
     *
     * @param quantity for {@link CorporateActionType#STOCK_SPLIT} and {@link CorporateActionType#BONUS_SHARES}, the
     *     shares received; for {@link CorporateActionType#REVERSE_SPLIT}, the shares held after the event
     */
    record CorporateActionEntry(
            int line, LocalDate date, String fingerprint, String description, AssetReference asset,
            CorporateActionType type, Quantity quantity) implements StatementEntry {

        public CorporateActionEntry {
            Objects.requireNonNull(date, "date must not be null");
            Objects.requireNonNull(asset, "asset must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(quantity, "quantity must not be null");
            if (type != CorporateActionType.STOCK_SPLIT && type != CorporateActionType.REVERSE_SPLIT
                    && type != CorporateActionType.BONUS_SHARES) {
                throw new IllegalArgumentException("Unsupported corporate action entry: " + type);
            }
        }
    }
}
