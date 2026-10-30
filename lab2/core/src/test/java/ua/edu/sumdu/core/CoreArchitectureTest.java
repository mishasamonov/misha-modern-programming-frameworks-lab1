package ua.edu.sumdu.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;

class CoreArchitectureTest {
  @Test
  void coreHasNoInfrastructureDependencies() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ua.edu.sumdu.core");
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "jakarta.servlet..",
            "javax.servlet..",
            "java.sql..",
            "javax.sql..",
            "org.h2..",
            "..persistence..",
            "..web..")
        .check(classes);
  }

  @Test
  void allCoreConstructorsArePublic() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ua.edu.sumdu.core");
    constructors()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..core..")
        .should()
        .bePublic()
        .check(classes);
  }
}
