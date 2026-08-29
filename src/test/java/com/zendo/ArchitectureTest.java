package com.zendo;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture fitness tests enforcing Zendo structural rules.
 *
 * <p>These tests run without Spring context — they analyze compiled bytecode
 * using ArchUnit. They enforce rules from:</p>
 * <ul>
 *   <li>rules/00-CONSTITUTION.md §3 — domain independence</li>
 *   <li>rules/01-ARCHITECTURE.md §5 — domain purity</li>
 *   <li>rules/03-MODULE-BOUNDARIES.md §5 — package boundary enforcement</li>
 *   <li>rules/10-TESTING.md §6 — architecture tests</li>
 * </ul>
 *
 * <p>{@code allowEmptyShould(true)} is used because during Phase 0
 * no domain/application/infrastructure packages exist yet. Once the
 * first bounded context is created, these rules will actively enforce
 * constraints on real classes.</p>
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.zendo";

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    // -----------------------------------------------------------------------
    // Domain Purity — rules/01-ARCHITECTURE.md §5
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Domain Purity")
    class DomainPurity {

        @Test
        @DisplayName("Domain classes must not depend on Spring framework")
        void domainMustNotDependOnSpring() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.transaction.."
                    )
                    .because("Domain must remain framework-independent (rule 01 §5, rule 00 §3)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }

        @Test
        @DisplayName("Domain classes must not depend on RabbitMQ")
        void domainMustNotDependOnRabbitMQ() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.apache.RabbitMQ..", "org.springframework.RabbitMQ..")
                    .because("Domain must not depend on messaging infrastructure (rule 01 §5)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }

        @Test
        @DisplayName("Domain classes must not depend on Redis")
        void domainMustNotDependOnRedis() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.data.redis..",
                            "io.lettuce..",
                            "redis.clients.."
                    )
                    .because("Domain must not depend on Redis infrastructure (rule 01 §5)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }

        @Test
        @DisplayName("Domain classes must not depend on web/HTTP")
        void domainMustNotDependOnWeb() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "jakarta.servlet..",
                            "org.springframework.http.."
                    )
                    .because("Domain must not depend on HTTP/web infrastructure (rule 01 §5)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }
    }

    // -----------------------------------------------------------------------
    // Dependency Direction — rules/01-ARCHITECTURE.md §4
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Dependency Direction")
    class DependencyDirection {

        @Test
        @DisplayName("Domain must not depend on infrastructure")
        void domainMustNotDependOnInfrastructure() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..")
                    .because("Dependency direction: infrastructure -> application -> domain (rule 01 §4)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }

        @Test
        @DisplayName("Domain must not depend on application layer")
        void domainMustNotDependOnApplication() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..")
                    .because("Domain is the innermost layer and must not depend on application (rule 01 §4)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }

        @Test
        @DisplayName("Application must not depend on infrastructure")
        void applicationMustNotDependOnInfrastructure() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..")
                    .because("Application may depend on domain only, not infrastructure (rule 01 §4)")
                    .allowEmptyShould(true);

            rule.check(allClasses);
        }
    }
}
