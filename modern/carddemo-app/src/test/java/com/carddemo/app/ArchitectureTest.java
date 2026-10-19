package com.carddemo.app;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Module boundaries of plan §3.2 (active from Fase 0, run on every build). */
@AnalyzeClasses(packages = "com.carddemo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** batch-*, online-* and ext-* never depend on each other (online-common is shared). */
  @ArchTest
  static final ArchRule featureModulesAreIndependent =
      slices()
          .matching("com.carddemo.(batch|online|ext).(*)..")
          .should()
          .notDependOnEachOther()
          .ignoreDependency(alwaysTrue(), resideInAPackage("com.carddemo.online.common.."))
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule featureModulesUseOnlySharedModules =
      noClasses()
          .that()
          .resideInAnyPackage("com.carddemo.batch..", "com.carddemo.online..", "com.carddemo.ext..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.carddemo.app..", "com.carddemo.datamigration..", "com.carddemo.parity..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule sharedModulesDoNotDependOnFeatureModules =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.carddemo.contracts..",
              "com.carddemo.domain..",
              "com.carddemo.copybook..",
              "com.carddemo.platform..",
              "com.carddemo.online.common..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.carddemo.batch..",
              "com.carddemo.online.auth..",
              "com.carddemo.online.account..",
              "com.carddemo.online.card..",
              "com.carddemo.online.transaction..",
              "com.carddemo.online.useradmin..",
              "com.carddemo.ext..",
              "com.carddemo.app..");

  @ArchTest
  static final ArchRule contractsAreFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("com.carddemo.contracts..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "com.carddemo.domain..");

  /** Plan §3.4 rule 5: time only through BusinessClock. */
  @ArchTest
  static final ArchRule noDirectSystemTime =
      noClasses()
          .that()
          .resideOutsideOfPackages("com.carddemo.platform.time..", "com.carddemo.contracts.port..")
          .should()
          .callMethod(java.time.LocalDateTime.class, "now")
          .orShould()
          .callMethod(java.time.LocalDate.class, "now")
          .orShould()
          .callMethod(System.class, "currentTimeMillis");
}
