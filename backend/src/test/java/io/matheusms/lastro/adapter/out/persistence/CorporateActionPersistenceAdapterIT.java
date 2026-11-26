package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@PersistenceAdapterTest
class CorporateActionPersistenceAdapterIT {

    @Autowired
    private CorporateActionPersistenceAdapter corporateActions;
    @Autowired
    private AssetPersistenceAdapter assets;

    private Long assetId;

    @BeforeEach
    void setUp() {
        assetId = assets.save(TestData.stock("ITSA4")).id();
    }

    @Test
    void shouldReturnActionsOrderedByRecordDateWithAssignedCost() {
        var bonus = corporateActions.save(new CorporateAction(null, assetId, CorporateActionType.BONUS_SHARES,
                LocalDate.of(2026, 6, 1), new BigDecimal("0.1"), Money.brl("18.75")));
        var split = corporateActions.save(new CorporateAction(null, assetId, CorporateActionType.STOCK_SPLIT,
                LocalDate.of(2026, 2, 1), new BigDecimal("2"), null));

        assertThat(corporateActions.findByAsset(assetId)).containsExactly(split, bonus);
        assertThat(bonus.factor()).isEqualByComparingTo("0.1");
    }

    @Test
    void shouldRejectDuplicateActionForSameAssetTypeAndDate() {
        var split = new CorporateAction(null, assetId, CorporateActionType.STOCK_SPLIT,
                LocalDate.of(2026, 2, 1), new BigDecimal("2"), null);
        corporateActions.save(split);

        assertThatThrownBy(() -> corporateActions.save(split))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
