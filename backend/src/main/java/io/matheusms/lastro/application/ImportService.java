package io.matheusms.lastro.application;

import io.matheusms.lastro.application.PortfolioLoader.UserPortfolio;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.ImportBatch;
import io.matheusms.lastro.domain.model.ImportStatus;
import io.matheusms.lastro.domain.model.InstitutionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.statement.AssetReference;
import io.matheusms.lastro.domain.model.statement.ImportIssue;
import io.matheusms.lastro.domain.model.statement.StatementEntry;
import io.matheusms.lastro.domain.model.statement.StatementEntry.CorporateActionEntry;
import io.matheusms.lastro.domain.model.statement.StatementEntry.DistributionEntry;
import io.matheusms.lastro.domain.model.statement.StatementEntry.TradeGroup;
import io.matheusms.lastro.domain.model.statement.TradeLeg;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.CorporateActionRepository;
import io.matheusms.lastro.domain.port.DistributionRepository;
import io.matheusms.lastro.domain.port.FixedIncomeTermsRepository;
import io.matheusms.lastro.domain.port.ImportBatchRepository;
import io.matheusms.lastro.domain.port.InstitutionRepository;
import io.matheusms.lastro.domain.port.StatementReader;
import io.matheusms.lastro.domain.port.TradeRepository;
import io.matheusms.lastro.domain.service.InsufficientQuantityException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports files exported by B3 and Binance (ADR 0009). Each row becomes the same trade, distribution or corporate
 * action a manual entry would, with the same consistency check; a row that would break the history is reported and
 * left out, without failing the rest. Imports are idempotent: rows already imported (same natural key) are skipped,
 * so the same file, or a newer export of the same history, can be imported again.
 */
@Service
public class ImportService {

    private static final int FACTOR_SCALE = 8;

    private final Map<EntrySource, StatementReader> readers;
    private final AssetRepository assets;
    private final InstitutionRepository institutions;
    private final TradeRepository trades;
    private final DistributionRepository distributions;
    private final CorporateActionRepository corporateActions;
    private final ImportBatchRepository batches;
    private final FixedIncomeTermsRepository fixedIncomeTerms;
    private final PortfolioLoader loader;
    private final PositionService positions;
    private final EntryValidator validator;
    private final Clock clock;

    ImportService(List<StatementReader> readers, AssetRepository assets, InstitutionRepository institutions,
            TradeRepository trades, DistributionRepository distributions, CorporateActionRepository corporateActions,
            ImportBatchRepository batches, FixedIncomeTermsRepository fixedIncomeTerms, PortfolioLoader loader,
            PositionService positions, EntryValidator validator, Clock clock) {
        this.fixedIncomeTerms = fixedIncomeTerms;
        this.readers = readers.stream().collect(Collectors.toMap(StatementReader::source, Function.identity()));
        this.assets = assets;
        this.institutions = institutions;
        this.trades = trades;
        this.distributions = distributions;
        this.corporateActions = corporateActions;
        this.batches = batches;
        this.loader = loader;
        this.positions = positions;
        this.validator = validator;
        this.clock = clock;
    }

    /**
     * @param institutionId where the imported operations happened: a broker or bank for B3, an exchange for Binance
     * @throws io.matheusms.lastro.domain.model.statement.UnreadableStatementException if the file is not in the
     *     format of the source
     */
    @Transactional
    public ImportResult importFile(Long userId, EntrySource source, Long institutionId, String fileName,
            byte[] content) {
        var reader = readers.get(source);
        if (reader == null) {
            throw new IllegalArgumentException("Files cannot be imported from " + source);
        }
        var institution = institutions.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution", institutionId));
        requireInstitutionType(source, institution.type());

        var statement = reader.read(fileName, content);
        var run = new Run(userId, institutionId, source);
        run.issues.addAll(statement.issues());
        statement.entries().stream()
                .sorted(Comparator.comparing(StatementEntry::date))
                .forEach(run::process);
        return run.save(fileName, sha256(content));
    }

    @Transactional(readOnly = true)
    public List<ImportBatch> list(Long userId) {
        return batches.findByUser(userId);
    }

    /**
     * Undoes an import: deletes the trades and distributions it created. Corporate actions stay, because they are
     * shared and may have been created by another import.
     *
     * @throws InsufficientQuantityException if a later entry depends on what the import created
     */
    @Transactional
    public void delete(Long userId, Long batchId) {
        var batch = batches.findById(batchId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Import", batchId));
        var tradeIds = Set.copyOf(batches.findTradeIds(batch.id()));
        var distributionIds = Set.copyOf(batches.findDistributionIds(batch.id()));
        validator.requireConsistentPortfolio(userId, portfolio -> without(portfolio, tradeIds, distributionIds));
        tradeIds.forEach(trades::deleteById);
        distributionIds.forEach(distributions::deleteById);
        batches.deleteById(batch.id());
    }

    private static UserPortfolio without(UserPortfolio portfolio, Set<Long> tradeIds, Set<Long> distributionIds) {
        var histories = new HashMap<Long, AssetHistory>();
        portfolio.histories().forEach((assetId, history) -> histories.put(assetId, new AssetHistory(assetId,
                history.trades().stream().filter(trade -> !tradeIds.contains(trade.id())).toList(),
                history.transfers(), history.corporateActions())));
        var remaining = portfolio.distributions().stream()
                .filter(distribution -> !distributionIds.contains(distribution.id()))
                .toList();
        return new UserPortfolio(histories, remaining);
    }

    private static void requireInstitutionType(EntrySource source, InstitutionType type) {
        boolean matches = switch (source) {
            case B3_IMPORT -> type == InstitutionType.BROKER || type == InstitutionType.BANK;
            case BINANCE_CSV -> type == InstitutionType.EXCHANGE;
            default -> false;
        };
        if (!matches) {
            throw new IllegalArgumentException("A %s file cannot be imported into a %s".formatted(source, type));
        }
    }

    /**
     * State of one import: the portfolio as it would be with the rows accepted so far (every row is checked against
     * it), and what will be saved at the end.
     */
    private final class Run {

        private final Long userId;
        private final Long institutionId;
        private final EntrySource source;
        private final Set<String> knownKeys;
        private final Map<String, Asset> assetsByCode = new HashMap<>();
        private final List<ImportIssue> issues = new ArrayList<>();
        private final List<KeyedTrade> newTrades = new ArrayList<>();
        private final List<KeyedDistribution> newDistributions = new ArrayList<>();
        private final List<CorporateAction> newActions = new ArrayList<>();
        private final Set<String> withoutTermsReported = new HashSet<>();
        private UserPortfolio portfolio;
        private int duplicates;
        /** Rows (entries) that created records. */
        private int acceptedRows;

        Run(Long userId, Long institutionId, EntrySource source) {
            this.userId = userId;
            this.institutionId = institutionId;
            this.source = source;
            this.knownKeys = new HashSet<>(batches.findNaturalKeys(userId));
            this.portfolio = loader.load(userId);
        }

        void process(StatementEntry entry) {
            switch (entry) {
                case TradeGroup group -> trades(group);
                case DistributionEntry distribution -> distribution(distribution);
                case CorporateActionEntry action -> corporateAction(action);
            }
        }

        private void trades(TradeGroup group) {
            var keys = new ArrayList<String>();
            for (int leg = 0; leg < group.legs().size(); leg++) {
                keys.add(naturalKey("T", group.fingerprint(), leg));
            }
            if (keys.stream().anyMatch(knownKeys::contains)) {
                duplicates++;
                return;
            }
            var candidate = portfolio;
            var created = new ArrayList<KeyedTrade>();
            for (int leg = 0; leg < group.legs().size(); leg++) {
                var trade = trade(group.legs().get(leg), group);
                candidate = withTrade(candidate, trade);
                created.add(new KeyedTrade(trade, keys.get(leg)));
            }
            if (accept(candidate, group)) {
                newTrades.addAll(created);
                knownKeys.addAll(keys);
                acceptedRows++;
                requireFixedIncomeTerms(group);
            }
        }

        /** A fixed income security is valued at cost until its terms are typed: reported once per asset. */
        private void requireFixedIncomeTerms(TradeGroup group) {
            for (TradeLeg leg : group.legs()) {
                var asset = asset(leg.asset());
                if (asset.assetClass() == AssetClass.FIXED_INCOME && withoutTermsReported.add(asset.code())
                        && fixedIncomeTerms.findByAsset(asset.id()).isEmpty()) {
                    issues.add(new ImportIssue(group.line(), ImportIssue.Code.FIXED_INCOME_TERMS_REQUIRED,
                            group.description(), asset.code()));
                }
            }
        }

        private Trade trade(TradeLeg leg, TradeGroup group) {
            var fee = switch (leg.fee()) {
                case TradeLeg.LegFee.None _ -> TradeFee.none(leg.unitPrice().currency());
                case TradeLeg.LegFee.InCurrency(Money amount) -> new TradeFee.InCurrency(amount);
                case TradeLeg.LegFee.InAsset(AssetReference asset, Quantity quantity) ->
                        new TradeFee.InAsset(asset(asset).id(), quantity);
            };
            return new Trade(null, userId, asset(leg.asset()).id(), institutionId, leg.type(), group.date(),
                    leg.quantity(), leg.unitPrice(), fee, source, null);
        }

        private void distribution(DistributionEntry entry) {
            var key = naturalKey("D", entry.fingerprint(), 0);
            if (knownKeys.contains(key)) {
                duplicates++;
                return;
            }
            var none = Money.zero(entry.amount().currency());
            var distribution = new Distribution(null, userId, asset(entry.asset()).id(), institutionId, entry.type(),
                    entry.date(), entry.date(), entry.baseQuantity(), entry.amount(), none, entry.amount(), null, null);
            newDistributions.add(new KeyedDistribution(distribution, key));
            knownKeys.add(key);
            acceptedRows++;
        }

        /** The file reports quantities; the factor comes from the quantity held on the date. */
        private void corporateAction(CorporateActionEntry entry) {
            var assetId = asset(entry.asset()).id();
            var withAsset = withHistoryOf(portfolio, assetId);
            boolean recorded = withAsset.histories().get(assetId).corporateActions().stream()
                    .anyMatch(action -> action.type() == entry.type() && action.recordDate().equals(entry.date()));
            if (recorded) {
                duplicates++;
                return;
            }
            var held = positions.quantityOn(withAsset, assetId, entry.date());
            if (held.isZero()) {
                issues.add(new ImportIssue(entry.line(), ImportIssue.Code.NO_POSITION_FOR_EVENT, entry.description()));
                return;
            }
            var action = new CorporateAction(null, assetId, entry.type(), entry.date(),
                    factor(entry.type(), held.value(), entry.quantity().value()),
                    entry.type() == CorporateActionType.BONUS_SHARES ? Money.zero(Currency.BRL) : null);
            var candidate = withAsset.withHistory(assetId, history -> {
                var actions = new ArrayList<>(history.corporateActions());
                actions.add(action);
                return new AssetHistory(assetId, history.trades(), history.transfers(), actions);
            });
            if (accept(candidate, entry)) {
                newActions.add(action);
                acceptedRows++;
                if (entry.type() == CorporateActionType.BONUS_SHARES) {
                    issues.add(new ImportIssue(entry.line(), ImportIssue.Code.UNKNOWN_ASSIGNED_COST,
                            entry.description()));
                }
            }
        }

        private static BigDecimal factor(CorporateActionType type, BigDecimal held, BigDecimal quantity) {
            return switch (type) {
                case STOCK_SPLIT -> held.add(quantity).divide(held, FACTOR_SCALE, RoundingMode.HALF_EVEN);
                case REVERSE_SPLIT -> held.divide(quantity, FACTOR_SCALE, RoundingMode.HALF_EVEN);
                case BONUS_SHARES -> quantity.divide(held, FACTOR_SCALE, RoundingMode.HALF_EVEN);
                default -> throw new IllegalArgumentException("Not a quantity event: " + type);
            };
        }

        /** Keeps the candidate portfolio if it is consistent; reports the row otherwise. */
        private boolean accept(UserPortfolio candidate, StatementEntry entry) {
            try {
                positions.requireConsistent(candidate);
            } catch (InsufficientQuantityException exception) {
                issues.add(new ImportIssue(entry.line(), ImportIssue.Code.INCONSISTENT_HISTORY, entry.description(),
                        exception.getMessage()));
                return false;
            }
            portfolio = candidate;
            return true;
        }

        private UserPortfolio withTrade(UserPortfolio base, Trade trade) {
            return withHistoryOf(base, trade.assetId()).withHistory(trade.assetId(), history -> {
                var updated = new ArrayList<>(history.trades());
                updated.add(trade);
                return new AssetHistory(history.assetId(), updated, history.transfers(), history.corporateActions());
            });
        }

        /** Makes sure the asset's stored history (with its corporate actions) is part of the portfolio. */
        private UserPortfolio withHistoryOf(UserPortfolio base, Long assetId) {
            if (base.histories().containsKey(assetId)) {
                return base;
            }
            return base.withHistory(assetId, empty -> loader.history(userId, assetId));
        }

        /** Finds the asset by code, creating it when it does not exist yet. */
        private Asset asset(AssetReference reference) {
            return assetsByCode.computeIfAbsent(reference.code(), code -> assets.findByCode(code)
                    .orElseGet(() -> assets.save(new Asset(null, code, truncate(reference.name(), 150),
                            reference.assetClass(), null, reference.quoteCurrency(), true))));
        }

        private String naturalKey(String kind, String fingerprint, int leg) {
            return sha256(String.join("|", userId.toString(), institutionId.toString(), source.name(), kind,
                    fingerprint, Integer.toString(leg)).getBytes(StandardCharsets.UTF_8));
        }

        ImportResult save(String fileName, String fileHash) {
            issues.sort(Comparator.comparingInt(ImportIssue::line));
            boolean needsAttention = issues.stream().anyMatch(issue -> issue.code().needsAttention());
            var batch = batches.save(new ImportBatch(null, userId, institutionId, source, truncate(fileName, 255),
                    fileHash, Instant.now(clock),
                    needsAttention ? ImportStatus.COMPLETED_WITH_ISSUES : ImportStatus.COMPLETED,
                    acceptedRows, duplicates + issues.size()));
            for (KeyedTrade keyed : newTrades) {
                var saved = trades.save(keyed.trade());
                batches.linkTrade(userId, batch.id(), keyed.key(), saved.id());
            }
            for (KeyedDistribution keyed : newDistributions) {
                var saved = distributions.save(keyed.distribution());
                batches.linkDistribution(userId, batch.id(), keyed.key(), saved.id());
            }
            newActions.forEach(corporateActions::save);
            return new ImportResult(batch, newTrades.size(), newDistributions.size(), newActions.size(), duplicates,
                    List.copyOf(issues));
        }
    }

    private record KeyedTrade(Trade trade, String key) {
    }

    private record KeyedDistribution(Distribution distribution, String key) {
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }

    /**
     * @param trades trades created (a Binance conversion may create two)
     * @param duplicates rows already imported before
     * @param issues rows left out or recorded with a caveat, by line
     */
    public record ImportResult(
            ImportBatch batch, int trades, int distributions, int corporateActions, int duplicates,
            List<ImportIssue> issues) {
    }
}
