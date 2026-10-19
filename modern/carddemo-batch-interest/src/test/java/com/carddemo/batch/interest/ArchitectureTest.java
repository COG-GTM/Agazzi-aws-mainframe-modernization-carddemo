package com.carddemo.batch.interest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Plan §3.2/§3.4 rules applied to the B2 module. */
@AnalyzeClasses(
    packages = "com.carddemo.batch.interest",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule onlySharedModules =
      noClasses()
          .that()
          .resideInAPackage("com.carddemo.batch.interest..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.carddemo.app..",
              "com.carddemo.parity..",
              "com.carddemo.datamigration..",
              "com.carddemo.online..",
              "com.carddemo.ext..",
              "com.carddemo.batch.posting..",
              "com.carddemo.batch.reports..",
              "com.carddemo.batch.datalifecycle..",
              "com.carddemo.batch.orchestration..");

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
  static final ArchRule noBinaryFloatingPoint =
      noFields().should().haveRawType(double.class).orShould().haveRawType(float.class);
}
