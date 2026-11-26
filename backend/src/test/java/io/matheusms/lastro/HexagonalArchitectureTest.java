package io.matheusms.lastro;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Dependency rules of the hexagonal architecture.
 * <p>
 * Rules allow empty packages because most layers are still empty; they start biting
 * as soon as classes are added.
 */
@AnalyzeClasses(packages = "io.matheusms.lastro", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    /**
     * Dependencies point inwards: adapters → application → domain services/ports → domain model.
     * Adapters never depend on each other. The config package (wiring) is deliberately left out.
     */
    @ArchTest
    static final ArchRule layersShouldDependInwards = onionArchitecture()
            .domainModels("..domain.model..")
            .domainServices("..domain.service..", "..domain.port..")
            .applicationServices("..application..", "..tools..")
            .adapter("rest", "..adapter.in.rest..")
            .adapter("mcp", "..adapter.in.mcp..")
            .adapter("scheduler", "..adapter.in.scheduler..")
            .adapter("persistence", "..adapter.out.persistence..")
            .adapter("quote", "..adapter.out.quote..")
            .adapter("indicator", "..adapter.out.indicator..")
            .adapter("b3import", "..adapter.out.b3import..")
            .adapter("binance", "..adapter.out.binance..")
            .withOptionalLayers(true)
            // config is the wiring layer and may depend on every other layer (ADR 0001)
            .ignoreDependency(resideInAPackage("..config.."), alwaysTrue());

    @ArchTest
    static final ArchRule domainShouldNotDependOnFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.transaction..",
                    "org.hibernate..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainShouldNotDependOnConfig = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..config..")
            .allowEmptyShould(true);
}
