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
            .should().dependOnClassesThat().resideInAPackage("..workmanagement..").check(classes);
        noClasses().that().resideInAPackage("..workmanagement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..", "org.springframework..").check(classes);
        noClasses().that().resideInAPackage("..workmanagement.api..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..infrastructure..").check(classes);
        noClasses().that().resideInAPackage("..workmanagement.application..")
            .should().dependOnClassesThat().resideInAPackage("..workmanagement.api..").check(classes);
    }
}
