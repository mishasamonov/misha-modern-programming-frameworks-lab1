package sumdu.edu.ua.web;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.importer.*;
import org.junit.jupiter.api.Test;

class WebArchitectureTest {
  private final com.tngtech.archunit.core.domain.JavaClasses imported =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("ua.edu.sumdu", "sumdu.edu.ua.web");

  @Test
  void webDoesNotDependOnPersistenceOrJdbc() {
    noClasses()
        .that()
        .resideInAPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..persistence..", "java.sql..", "javax.sql..", "org.h2..")
        .check(imported);
  }

  @Test
  void coreHasNoServletOrJdbcDependencies() {
    noClasses()
        .that()
        .resideInAPackage("..core..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "jakarta.servlet..", "java.sql..", "javax.sql..", "..web..", "..persistence..")
        .check(imported);
  }

  @Test
  void webUsesJavalinWithoutServletClasses() {
    noClasses()
        .that()
        .resideInAPackage("sumdu.edu.ua.web..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("jakarta.servlet..")
        .check(imported);
  }

  @Test
  void daoAndRepositoriesAreOnlyInPersistence() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Repository")
        .or()
        .haveSimpleNameEndingWith("Dao")
        .or()
        .haveSimpleNameEndingWith("DAO")
        .should()
        .resideInAPackage("..persistence..")
        .check(imported);
    classes()
        .that()
        .areNotInterfaces()
        .and()
        .areAssignableTo(ua.edu.sumdu.core.port.CatalogRepositoryPort.class)
        .should()
        .resideInAPackage("..persistence..")
        .check(imported);
    classes()
        .that()
        .areNotInterfaces()
        .and()
        .areAssignableTo(ua.edu.sumdu.core.port.CommentRepositoryPort.class)
        .should()
        .resideInAPackage("..persistence..")
        .check(imported);
  }

  @Test
  void layersAreFreeOfCycles() {
    com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
        .matching("ua.edu.sumdu.(*)..")
        .should()
        .beFreeOfCycles()
        .check(imported);
  }
}
