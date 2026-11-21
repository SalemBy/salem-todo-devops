package github.salemby.tododevops;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTests {
    @Test void enforcesWorkManagementBoundaries() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("github.salemby.tododevops");
        // Work management may use identity's application services and DTOs, never its internals.
        noClasses().that().resideInAPackage("..workmanagement..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.api..", "..identity.domain..",
                "..identity.infrastructure..").check(classes);
        noClasses().that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage("..workmanagement..", "..identity..").check(classes);
        noClasses().that().resideInAPackage("..workmanagement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..", "org.springframework..").check(classes);
        noClasses().that().resideInAPackage("..workmanagement.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..infrastructure..").check(classes);
        noClasses().that().resideInAPackage("..workmanagement.application..")
            .should().dependOnClassesThat().resideInAPackage("..workmanagement.api..").check(classes);
        noClasses().that().resideInAPackage("..identity.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..", "org.springframework..").check(classes);
        noClasses().that().resideInAPackage("..identity.application..")
            .should().dependOnClassesThat().resideInAPackage("..identity.api..").check(classes);
        noClasses().that().resideInAPackage("..identity.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.domain..", "..identity.infrastructure..").check(classes);
        // Administration coordinates both modules through their application services only, and nothing depends on it (#67).
        noClasses().that().resideInAPackage("..administration..")
            .should().dependOnClassesThat().resideInAnyPackage("..identity.api..", "..identity.domain..", "..identity.infrastructure..",
                "..workmanagement.api..", "..workmanagement.domain..", "..workmanagement.infrastructure..").check(classes);
        noClasses().that().resideOutsideOfPackage("..administration..")
            .should().dependOnClassesThat().resideInAPackage("..administration..").check(classes);
    }
}
