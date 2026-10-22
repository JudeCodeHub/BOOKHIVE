package lk.bookhive;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Enforces the modular-monolith rules from PRD §7.2 on every build. If someone breaks a rule, this
 * test fails and names the class that did it.
 */
class ArchitectureTest {

  static final String ROOT = "lk.bookhive";

  static final List<String> MODULES =
      List.of(
          "auth",
          "member",
          "catalog",
          "search",
          "circulation",
          "hold",
          "fine",
          "payment",
          "notification",
          "event",
          "community",
          "report",
          "admin");

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
  }

  // One test per module: e.g. only catalog code may use catalog.repository.
  // Other modules must go through catalog's service instead.
  @TestFactory
  List<DynamicTest> repositoriesAreUsedOnlyInsideTheirModule() {
    return MODULES.stream()
        .map(
            module ->
                DynamicTest.dynamicTest(
                    module,
                    () ->
                        noClasses()
                            .that()
                            .resideOutsideOfPackage(ROOT + "." + module + "..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAPackage(ROOT + "." + module + ".repository..")
                            .because("other modules must call " + module + "'s service instead")
                            .allowEmptyShould(true)
                            .check(classes)))
        .toList();
  }

  // common is shared by everyone, so it must not depend on any feature module (no cycles).
  @Test
  void commonDoesNotDependOnFeatureModules() {
    noClasses()
        .that()
        .resideInAPackage(ROOT + ".common..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(MODULES.stream().map(m -> ROOT + "." + m + "..").toArray(String[]::new))
        .check(classes);
  }
}
