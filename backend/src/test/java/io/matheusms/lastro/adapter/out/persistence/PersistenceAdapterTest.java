package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * JPA slice with every persistence adapter and a PostgreSQL container (never an embedded database). All persistence tests share this
 * exact configuration, so Spring caches a single context (and container) for them. Each test runs in a
 * transaction that is rolled back at the end.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        TestcontainersConfiguration.class,
        UserPersistenceAdapter.class,
        InstitutionPersistenceAdapter.class,
        AssetPersistenceAdapter.class,
        TradePersistenceAdapter.class,
        TransferPersistenceAdapter.class,
        CorporateActionPersistenceAdapter.class,
        DistributionPersistenceAdapter.class,
        QuotePersistenceAdapter.class,
        EconomicIndicatorPersistenceAdapter.class,
        ImportBatchPersistenceAdapter.class,
        FixedIncomeTermsPersistenceAdapter.class
})
@interface PersistenceAdapterTest {
}
