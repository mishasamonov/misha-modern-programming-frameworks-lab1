package ua.edu.sumdu.persistence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;

class PersistenceArchitectureTest {
  @Test
  void persistenceNeverDependsOnWebOrServlets() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ua.edu.sumdu");
    noClasses()
        .that()
        .resideInAPackage("..persistence..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..web..", "jakarta.servlet..")
        .check(classes);
  }

  @Test
  void repositoryImplementationsStayInPersistence() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ua.edu.sumdu");
    classes()
        .that()
        .haveSimpleNameEndingWith("Repository")
        .and()
        .areNotInterfaces()
        .should()
        .resideInAPackage("..persistence..")
        .check(classes);
  }
}
