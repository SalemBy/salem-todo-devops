package github.salemby.tododevops;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTests {
    @Test void enforcesWorkManagementBoundaries() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("github.salemby.tododevops");
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
    }
}
