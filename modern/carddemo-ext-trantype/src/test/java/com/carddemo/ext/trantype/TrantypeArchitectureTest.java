package com.carddemo.ext.trantype;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.carddemo.ext.trantype",
    importOptions = ImportOption.DoNotIncludeTests.class)
class TrantypeArchitectureTest {

  @ArchTest
  static final ArchRule onlySharedModules =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.carddemo.batch..",
              "com.carddemo.online.auth..",
              "com.carddemo.online.account..",
              "com.carddemo.online.card..",
              "com.carddemo.online.transaction..",
              "com.carddemo.online.useradmin..",
              "com.carddemo.ext.authorization..",
              "com.carddemo.ext.mqinquiry..",
              "com.carddemo.app..",
              "com.carddemo.datamigration..",
              "com.carddemo.parity..");

  @ArchTest
  static final ArchRule timeOnlyThroughBusinessClock =
      noClasses()
          .should()
          .callMethod(java.time.LocalDateTime.class, "now")
          .orShould()
          .callMethod(java.time.LocalDate.class, "now")
          .orShould()
          .callMethod(System.class, "currentTimeMillis");

  @ArchTest
  static final ArchRule noFloatingPoint =
      noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Double");
}
