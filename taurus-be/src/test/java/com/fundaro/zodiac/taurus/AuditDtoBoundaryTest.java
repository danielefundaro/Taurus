package com.fundaro.zodiac.taurus;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packagesOf = TaurusApp.class, importOptions = DoNotIncludeTests.class)
class AuditDtoBoundaryTest {

    @ArchTest
    static final ArchRule dtoDoesNotExposeAuditFields = noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..service.dto..")
        .should()
        .haveNameMatching("deleted|insertBy|insertDate|editBy|editDate");
}
