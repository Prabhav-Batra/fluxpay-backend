package com.fluxpay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import jakarta.persistence.Entity;
import java.util.Map;
import java.util.Set;

/** Mirrors .engineering/config/architecture.yaml. Update both together. */
@AnalyzeClasses(packages = "com.fluxpay", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("common", Set.of()),
            Map.entry("identity", Set.of("common")),
            Map.entry("merchants", Set.of("common", "identity")),
            Map.entry("apikeys", Set.of("common", "merchants")),
            Map.entry("catalog", Set.of("common", "merchants")),
            Map.entry("payments", Set.of("common")),
            Map.entry("checkout", Set.of("common", "merchants", "catalog", "payments")),
            Map.entry("ledger", Set.of("common", "merchants")),
            Map.entry("events", Set.of("common", "merchants")),
            Map.entry("sales", Set.of("common", "checkout", "payments", "ledger", "events")),
            Map.entry("analytics", Set.of("common", "sales", "ledger")),
            Map.entry("admin", Set.of("common", "merchants", "ledger")));

    @ArchTest
    static void modules_depend_only_on_declared_modules(JavaClasses classes) {
        ALLOWED.forEach((module, allowed) -> {
            String[] forbidden = ALLOWED.keySet().stream()
                    .filter(other -> !other.equals(module) && !allowed.contains(other))
                    .map(other -> "com.fluxpay." + other + "..")
                    .toArray(String[]::new);
            noClasses()
                    .that()
                    .resideInAPackage("com.fluxpay." + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(forbidden)
                    .allowEmptyShould(true)
                    .because("module boundaries are declared in .engineering/config/architecture.yaml")
                    .check(classes);
        });
    }

    @ArchTest
    static void modules_never_depend_on_app_config(JavaClasses classes) {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.fluxpay.config..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.fluxpay.config..")
                .check(classes);
    }

    @ArchTest
    static void api_layer_never_touches_repositories_or_entities(JavaClasses classes) {
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..persistence..")
                .orShould()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .check(classes);
    }
}
