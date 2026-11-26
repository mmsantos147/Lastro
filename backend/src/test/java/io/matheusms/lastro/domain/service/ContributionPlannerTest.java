package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionItem;
import io.matheusms.lastro.domain.model.SuggestionReason;
import io.matheusms.lastro.domain.model.TargetAllocation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContributionPlannerTest {

    private static final Long PETR4 = 1L;
    private static final Long VALE3 = 2L;
    private static final Long HGLG11 = 3L;
    private static final Long KNRI11 = 4L;

    private final ContributionPlanner planner = new ContributionPlanner();

    @Test
    void shouldSendTheContributionToTheClassBelowTarget() {
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "600"), holding(HGLG11, AssetClass.FII, "400")),
                List.of(target(AssetClass.STOCK, "50"), target(AssetClass.FII, "50")),
                Money.brl("200"));

        assertThat(items).extracting(SuggestionItem::assetId, SuggestionItem::suggestedAmount, SuggestionItem::reason)
                .containsExactly(tuple(HGLG11, Money.brl("200"), SuggestionReason.BELOW_TARGET));
        assertThat(items.getFirst().currentPercentage()).isEqualByComparingTo("40");
        assertThat(items.getFirst().targetPercentage()).isEqualByComparingTo("50");
    }

    @Test
    void shouldSplitInProportionToTheGapsWhenTheAmountDoesNotCloseThem() {
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "500"), holding(HGLG11, AssetClass.FII, "100"),
                        holding(VALE3, AssetClass.ETF, "400")),
                List.of(target(AssetClass.STOCK, "40"), target(AssetClass.FII, "40"), target(AssetClass.ETF, "20")),
                Money.brl("100"));

        // after: 1100 → STOCK desires 440 (above), FII 440 (gap 340), ETF 220 (above): all to FII
        assertThat(items).extracting(SuggestionItem::assetClass, SuggestionItem::suggestedAmount)
                .containsExactly(tuple(AssetClass.FII, Money.brl("100")));
    }

    @Test
    void shouldCloseEveryGapWhenTheAmountIsEnough() {
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "600"), holding(HGLG11, AssetClass.FII, "400")),
                List.of(target(AssetClass.STOCK, "50"), target(AssetClass.FII, "50")),
                Money.brl("1000"));

        // after: 2000 → 1000 each
        assertThat(items).extracting(SuggestionItem::assetId, SuggestionItem::suggestedAmount)
                .containsExactly(tuple(HGLG11, Money.brl("600")), tuple(PETR4, Money.brl("400")));
    }

    @Test
    void shouldNeverSuggestClassesWithoutTarget() {
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "100"), holding(9L, AssetClass.CRYPTO, "100")),
                List.of(target(AssetClass.STOCK, "100")),
                Money.brl("50"));

        assertThat(items).extracting(SuggestionItem::assetClass).containsOnly(AssetClass.STOCK);
    }

    @Test
    void shouldAskForANewAssetWhenTheClassHasNoPosition() {
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "800")),
                List.of(target(AssetClass.STOCK, "80"), target(AssetClass.ETF, "20")),
                Money.brl("200"));

        assertThat(items).extracting(SuggestionItem::assetClass, SuggestionItem::assetId, SuggestionItem::reason,
                        SuggestionItem::suggestedAmount)
                .containsExactly(tuple(AssetClass.ETF, null, SuggestionReason.NEW_POSITION_IN_CLASS, Money.brl("200")));
    }

    @Test
    void shouldFillAssetsOfAClassTowardsEqualWeights() {
        var items = planner.plan(
                List.of(holding(HGLG11, AssetClass.FII, "300"), holding(KNRI11, AssetClass.FII, "100")),
                List.of(target(AssetClass.FII, "100")),
                Money.brl("300"));

        // class after: 700 → 350 each
        assertThat(items).extracting(SuggestionItem::assetId, SuggestionItem::suggestedAmount)
                .containsExactly(tuple(KNRI11, Money.brl("250")), tuple(HGLG11, Money.brl("50")));
    }

    @Test
    void shouldRespectTheCapPerAssetAndAskForANewAssetWithTheRest() {
        var fii = new TargetAllocation(AssetClass.FII, null, new BigDecimal("50"), BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("30"));
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "600"), holding(HGLG11, AssetClass.FII, "400")),
                List.of(target(AssetClass.STOCK, "50"), fii),
                Money.brl("400"));

        // after: 1400 → FII desires 700 (gets 300), STOCK 700 (gets 100); cap per asset: 30% × 1400 = 420
        assertThat(items).extracting(SuggestionItem::assetId, SuggestionItem::reason, SuggestionItem::suggestedAmount)
                .containsExactly(
                        tuple(null, SuggestionReason.ASSET_CAP_REACHED, Money.brl("280")),
                        tuple(PETR4, SuggestionReason.BELOW_TARGET, Money.brl("100")),
                        tuple(HGLG11, SuggestionReason.BELOW_TARGET, Money.brl("20")));
    }

    @Test
    void shouldTreatAnAssetWithItsOwnTargetAsASeparateBucket() {
        var items = planner.plan(
                List.of(holding(PETR4, AssetClass.STOCK, "500"), holding(VALE3, AssetClass.STOCK, "500")),
                List.of(new TargetAllocation(null, VALE3, new BigDecimal("60"), BigDecimal.ONE, BigDecimal.ONE, null),
                        target(AssetClass.STOCK, "40")),
                Money.brl("1000"));

        // after: 2000 → VALE3 desires 1200 (gap 700), the rest of the class 800 (gap 300)
        assertThat(items).extracting(SuggestionItem::assetId, SuggestionItem::suggestedAmount)
                .containsExactly(tuple(VALE3, Money.brl("700")), tuple(PETR4, Money.brl("300")));
    }

    @Test
    void shouldRoundToCentsKeepingTheExactTotal() {
        var items = planner.plan(
                List.of(holding(1L, AssetClass.FII, "100"), holding(2L, AssetClass.FII, "100"),
                        holding(3L, AssetClass.FII, "100")),
                List.of(target(AssetClass.FII, "100")),
                Money.brl("100"));

        assertThat(items).extracting(SuggestionItem::suggestedAmount)
                .containsExactlyInAnyOrder(Money.brl("33.34"), Money.brl("33.33"), Money.brl("33.33"));
    }

    @Test
    void shouldStartAPortfolioFromScratchFollowingTheTargets() {
        var items = planner.plan(List.of(),
                List.of(target(AssetClass.STOCK, "70"), target(AssetClass.FII, "30")), Money.brl("1000"));

        assertThat(items).extracting(SuggestionItem::assetClass, SuggestionItem::suggestedAmount)
                .containsExactly(tuple(AssetClass.STOCK, Money.brl("700")), tuple(AssetClass.FII, Money.brl("300")));
    }

    @Test
    void shouldRejectNonPositiveAmounts() {
        assertThatThrownBy(() -> planner.plan(List.of(), List.of(target(AssetClass.STOCK, "100")), Money.brl("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Holding holding(Long assetId, AssetClass assetClass, String value) {
        return new Holding(assetId, assetClass, Money.brl(value));
    }

    private static TargetAllocation target(AssetClass assetClass, String percentage) {
        return TargetAllocation.forClass(assetClass, percentage, "5");
    }
}
