package com.zendo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.zendo", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureEnforcementTest {

    // 1. Vendor Encapsulation
    @ArchTest
    static final ArchRule vendorEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.vendor..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.vendor.domain..", "com.zendo.vendor.infrastructure..", "com.zendo.vendor.application..")
                    .because("Modules must not access Vendor's internal layers directly.");

    // 2. Catalog Encapsulation
    @ArchTest
    static final ArchRule catalogEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.catalog..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.catalog.domain..", "com.zendo.catalog.infrastructure..", "com.zendo.catalog.application..")
                    .because("Modules must not access Catalog's internal layers directly.");

    // 2a. Inventory Encapsulation
    @ArchTest
    static final ArchRule inventoryEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.inventory..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.inventory.domain..", "com.zendo.inventory.infrastructure..")
                    .because("Modules must not access Inventory's internal layers directly.");

    // 2b. Cart Encapsulation
    @ArchTest
    static final ArchRule cartEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.cart..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.cart.domain..", "com.zendo.cart.infrastructure..")
                    .because("Modules must not access Cart's internal layers directly.");

    // 2c. Payment Encapsulation
    @ArchTest
    static final ArchRule paymentEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.payment..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.payment.domain..", "com.zendo.payment.infrastructure..", "com.zendo.payment.application..")
                    .because("Modules must not access Payment's internal layers directly.");

    // 2d. Promotion Encapsulation
    @ArchTest
    static final ArchRule promotionEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.promotion..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.promotion.domain..", "com.zendo.promotion.infrastructure..", "com.zendo.promotion.application..")
                    .because("Modules must not access Promotion's internal layers directly.");

    // 2e. Pricing Encapsulation
    @ArchTest
    static final ArchRule pricingEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.pricing..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.pricing.domain..", "com.zendo.pricing.infrastructure..", "com.zendo.pricing.application..")
                    .because("Modules must not access Pricing's internal layers directly.");

    // 2f. Notification Encapsulation
    @ArchTest
    static final ArchRule notificationEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.notification..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.notification.domain..", "com.zendo.notification.infrastructure..", "com.zendo.notification.application..")
                    .because("Modules must not access Notification's internal layers directly.");

    // 2g. Review Encapsulation
    @ArchTest
    static final ArchRule reviewEncapsulation =
            noClasses()
                    .that().resideOutsideOfPackage("com.zendo.review..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.zendo.review.domain..", "com.zendo.review.infrastructure..", "com.zendo.review.application..")
                    .because("Modules must not access Review's internal layers directly.");

    // 3. Catalog may only depend on the explicitly approved Vendor API.
    @ArchTest
    static final ArchRule catalogOnlyDependsOnVendorApi =
            noClasses()
                    .that().resideInAPackage("com.zendo.catalog..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.zendo.vendor.domain..",
                            "com.zendo.vendor.infrastructure..",
                            "com.zendo.vendor.application.."
                    )
                    .because("Catalog may only depend on the explicitly approved Vendor API.");

    // 4. Domain packages must not depend on Spring, JPA, infrastructure, or API/controller packages.
    @ArchTest
    static final ArchRule domainIsIndependent =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "..infrastructure..",
                            "..api..",
                            "io.micrometer.."
                    )
                    .because("Domain must be completely decoupled from framework and outer layers.");

    // 5. Controllers must not depend directly on repositories.
    @ArchTest
    static final ArchRule controllersMustNotDependOnRepositories =
            noClasses()
                    .that().areAnnotatedWith(RestController.class)
                    .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
                    .orShould().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class)
                    .because("Controllers must not bypass the application layer.");

    // 6. Repositories must remain inside their owning module.
    // (Implicitly enforced by Java package access and rule 1/2)
    @ArchTest
    static final ArchRule repositoriesResideInCorrectLayer =
            classes()
                    .that().areAnnotatedWith(Repository.class)
                    .or().areAssignableTo(org.springframework.data.repository.Repository.class)
                    .should().resideInAnyPackage("..domain..", "..infrastructure..")
                    .because("Repositories belong in domain (interfaces) or infrastructure (implementations).");

    // 7. JPA entities must remain inside their owning module.
    @ArchTest
    static final ArchRule entitiesResideInInfrastructure =
            classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..infrastructure.persistence..")
                    .because("JPA entities are infrastructure concerns.");

    // 8. No cyclic dependencies between bounded contexts.
    @ArchTest
    static final ArchRule noCyclicDependencies =
            slices()
                    .matching("com.zendo.(*)..")
                    .should().beFreeOfCycles()
                    .because("Cyclic dependencies between bounded contexts create tight coupling.");

    // 9. Shared must not depend on business modules.
    @ArchTest
    static final ArchRule sharedMustNotDependOnBusinessModules =
            noClasses()
                    .that().resideInAPackage("com.zendo.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.zendo.identity..", "com.zendo.vendor..", "com.zendo.catalog..", "com.zendo.inventory..", "com.zendo.cart..", "com.zendo.order..", "com.zendo.payment..", "com.zendo.promotion..", "com.zendo.pricing..", "com.zendo.notification..", "com.zendo.review.."
                    )
                    .because("Shared module must be foundational only.");

    // 10. Business modules must not use shared as a substitute for direct domain coupling.
    // (This is a design principle. ArchUnit test ensures shared domain doesn't exist).
    @ArchTest
    static final ArchRule sharedMustNotContainDomainLogic =
            noClasses()
                    .that().resideInAPackage("com.zendo.shared..")
                    .should().haveSimpleNameContaining("Service")
                    .orShould().haveSimpleNameContaining("Repository")
                    .orShould().haveSimpleNameContaining("Entity")
                    .because("Shared module must not contain business logic.");

    // 11. Infrastructure may depend on application/domain abstractions according to dependency inversion.
    @ArchTest
    static final ArchRule infrastructureDependsOnInnerLayers =
            classes()
                    .that().resideInAPackage("..infrastructure..")
                    .should().onlyHaveDependentClassesThat().resideInAnyPackage(
                            "..infrastructure..", // self
                            "java..", "javax..", "jakarta..", "org.springframework..", "com.zendo.shared.."
                            // Inner layers don't depend on infrastructure (checked by Rule 4)
                    )
                    .because("Dependency inversion: Infrastructure must depend on abstractions, but nothing depends on infrastructure.");

    // 12. Order -> Inventory synchronous transaction boundary.
    @ArchTest
    static final ArchRule orderToInventoryOnlyViaApi =
            noClasses()
                    .that().resideInAPackage("com.zendo.order..")
                    .should().dependOnClassesThat().resideInAPackage("com.zendo.inventory.infrastructure..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.zendo.inventory.domain..")
                    .because("Order can only access Inventory through its approved API/UseCases.");

    // 13a. Order must not depend on Payment
    @ArchTest
    static final ArchRule orderDoesNotDependOnPayment =
            noClasses()
                    .that().resideInAPackage("com.zendo.order..")
                    .should().dependOnClassesThat().resideInAPackage("com.zendo.payment..")
                    .because("Order must not depend on Payment.");

    // 13b. Payment must not depend on Order
    @ArchTest
    static final ArchRule paymentDoesNotDependOnOrder =
            noClasses()
                    .that().resideInAPackage("com.zendo.payment..")
                    .should().dependOnClassesThat().resideInAPackage("com.zendo.order..")
                    .because("Payment must not depend on Order.");

    // 14. Domain packages cannot depend on Spring Security or JWT
    @ArchTest
    static final ArchRule domainDoesNotDependOnSecurity =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.security..",
                            "com.auth0.jwt..",
                            "jakarta.servlet.."
                    )
                    .because("Domain packages must not depend on security infrastructure or servlet APIs.");

    // 15. Business modules cannot depend on SecurityContext directly
    @ArchTest
    static final ArchRule businessModulesMustNotDependOnSecurityContext =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.zendo.identity..", "com.zendo.vendor..", "com.zendo.catalog..", 
                            "com.zendo.inventory..", "com.zendo.cart..", "com.zendo.order..", 
                            "com.zendo.payment..", "com.zendo.promotion..", "com.zendo.pricing..", 
                            "com.zendo.notification..", "com.zendo.review.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
                    .because("Business modules must use AuthenticatedUser abstraction, not SecurityContextHolder.");

    // 16. Review cannot access Order/Catalog/Identity infrastructure
    @ArchTest
    static final ArchRule reviewDoesNotAccessOtherInfrastructure =
            noClasses()
                    .that().resideInAPackage("com.zendo.review..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.zendo.order.infrastructure..", "com.zendo.order.domain..",
                            "com.zendo.catalog.infrastructure..", "com.zendo.catalog.domain..",
                            "com.zendo.identity.infrastructure..", "com.zendo.identity.domain.."
                    )
                    .because("Review must only use approved API contracts, not internal domain or infrastructure.");
}
