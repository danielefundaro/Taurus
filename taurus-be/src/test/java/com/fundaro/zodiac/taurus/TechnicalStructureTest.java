package com.fundaro.zodiac.taurus;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packagesOf = TaurusApp.class, importOptions = DoNotIncludeTests.class)
class TechnicalStructureTest {

    // prettier-ignore
    @ArchTest
    static final ArchRule respectsTechnicalArchitectureLayers = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Config").definedBy("..config..")
        .layer("Web").definedBy("..web..")
        .optionalLayer("Service").definedBy("..service..")
        .layer("Security").definedBy("..security..")
        .optionalLayer("Multitenancy").definedBy("..multitenancy..")
        .optionalLayer("Persistence").definedBy("..repository..")
        .layer("Domain").definedBy("..domain..")
        .optionalLayer("Aop").definedBy("..aop..")
        .optionalLayer("Rabbitmq").definedBy("..rabbitmq..")
        .optionalLayer("Utils").definedBy("..utils..")

        .whereLayer("Config").mayNotBeAccessedByAnyLayer()
        .whereLayer("Web").mayOnlyBeAccessedByLayers("Config", "Service")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config", "Aop", "Rabbitmq")
        .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Service", "Web", "Aop", "Multitenancy", "Rabbitmq")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Security", "Web", "Config", "Rabbitmq")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Persistence", "Service", "Security", "Web", "Config", "Aop", "Rabbitmq", "Utils")

        .ignoreDependency(belongToAnyOf(TaurusApp.class), alwaysTrue())
        .ignoreDependency(alwaysTrue(), belongToAnyOf(
            com.fundaro.zodiac.taurus.config.Constants.class,
            com.fundaro.zodiac.taurus.config.ApplicationProperties.class
        ))
        .ignoreDependency(
            resideInAPackage("..service.."),
            resideInAPackage("..config.changelog..")
        )
        // Spring Data uses this service record as a read-only interface projection.
        .ignoreDependency(
            com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository.class,
            com.fundaro.zodiac.taurus.service.notification.NotificationPendingSummary.class
        );
}
