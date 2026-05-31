package test.tests.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.BeforeClass
import org.junit.Test

/**
 * ArchUnit tests to validate Hexagonal Architecture rules.
 *
 * These tests enforce the architectural constraints defined in the
 * hexagonal architecture document:
 *
 * 1. Domain layer has no external dependencies
 * 2. Application layer depends only on domain
 * 3. Infrastructure depends on application and domain (never the reverse)
 * 4. Dependency direction is always from outside to inside
 * 5. Ports are interfaces in the application layer
 * 6. Adapters implement ports and live in infrastructure
 */
class HexagonalArchitectureTest {

    companion object {
        private lateinit var importedClasses: JavaClasses

        @BeforeClass
        @JvmStatic
        fun setup() {
            importedClasses = ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("app")
        }
    }

    // =========================================================================
    // RULE 1: Layered Architecture (Dependency direction: outside → inside)
    // =========================================================================

    @Test
    fun `layered architecture is respected`() {
        val rule = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy("app.domain..")
            .layer("Application").definedBy("app.application..")
            .layer("Infrastructure").definedBy("app.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 2: Domain does not depend on Application or Infrastructure
    // "Proteger o core" - "O negócio está preservado"
    // =========================================================================

    @Test
    fun `domain does not depend on application layer`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("app.application..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain does not depend on infrastructure layer`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("app.infrastructure..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 3: Domain does not depend on frameworks
    // "Não deve ser modelado/baseado em framework"
    // "Banco de dados, REST, gRPC, RabbitMQ, CLI… tudo vira detalhe plugável"
    // =========================================================================

    @Test
    fun `domain does not depend on JGit`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.eclipse.jgit..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain does not depend on Fuel HTTP client`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.github.kittinunf.fuel..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain does not depend on Protobuf`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.google.protobuf..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain does not depend on Jackson`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain does not depend on Sentry`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("io.sentry..")

        rule.check(importedClasses)
    }

    @Test
    fun `domain does not depend on JCommander`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.beust.jcommander..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 4: Application does not depend on Infrastructure
    // "Dependência de fora para dentro"
    // =========================================================================

    @Test
    fun `application does not depend on infrastructure layer`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("app.infrastructure..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 5: Application does not depend on frameworks
    // "Não deve ser modelado/baseado em framework"
    // =========================================================================

    @Test
    fun `application does not depend on JGit`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("org.eclipse.jgit..")

        rule.check(importedClasses)
    }

    @Test
    fun `application does not depend on Fuel HTTP client`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("com.github.kittinunf.fuel..")

        rule.check(importedClasses)
    }

    @Test
    fun `application does not depend on Protobuf`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("com.google.protobuf..")

        rule.check(importedClasses)
    }

    @Test
    fun `application does not depend on Jackson`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")

        rule.check(importedClasses)
    }

    @Test
    fun `application does not depend on Sentry`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("io.sentry..")

        rule.check(importedClasses)
    }

    @Test
    fun `application does not depend on JCommander`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application..")
            .should().dependOnClassesThat().resideInAPackage("com.beust.jcommander..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 6: Ports are interfaces
    // "Normaliza entradas e saídas (portas)"
    // "Representam as interfaces de comunicação do sistema com o mundo externo"
    // =========================================================================

    @Test
    fun `ports are interfaces`() {
        val rule: ArchRule = classes()
            .that().resideInAPackage("app.application.ports..")
            .and().haveSimpleNameEndingWith("Port")
            .should().beInterfaces()

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 7: Adapters naming convention
    // "Adaptadores concretos para o mundo externo"
    // =========================================================================

    @Test
    fun `adapter classes have Adapter suffix`() {
        val rule: ArchRule = classes()
            .that().resideInAnyPackage(
                "app.infrastructure.api..",
                "app.infrastructure.git..",
                "app.infrastructure.repositories..",
                "app.infrastructure.analytics..",
                "app.infrastructure.logging.."
            )
            .and().haveSimpleNameNotEndingWith("State")
            .and().haveSimpleNameNotEndingWith("Ui")
            .and().haveSimpleNameNotEndingWith("Context")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Adapter")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 8: Use cases reside in application layer
    // "Cada funcionalidade da aplicação tem uma classe"
    // =========================================================================

    @Test
    fun `use cases reside in application usecases package`() {
        val rule: ArchRule = classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage("app.application.usecases..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 9: Domain entities reside in domain layer
    // =========================================================================

    @Test
    fun `entities reside in domain entities package`() {
        val rule: ArchRule = classes()
            .that().resideInAPackage("app.domain.entities..")
            .should().resideInAPackage("app.domain..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 10: Infrastructure classes should not be accessed by domain
    // "Sem acesso direto" - "Modificar apenas o adaptador"
    // =========================================================================

    @Test
    fun `infrastructure is not accessed by domain or application`() {
        val rule: ArchRule = noClasses()
            .that().resideInAnyPackage("app.domain..", "app.application..")
            .should().dependOnClassesThat().resideInAPackage("app.infrastructure..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 11: No cyclic dependencies between layers
    // "Acoplamento não deve existir"
    // =========================================================================

    @Test
    fun `no cyclic dependencies between domain and application`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.domain..")
            .should().dependOnClassesThat().resideInAPackage("app.application..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // RULE 12: Domain errors extend DomainError
    // =========================================================================

    @Test
    fun `domain errors reside in domain errors package`() {
        val rule: ArchRule = classes()
            .that().resideInAPackage("app.domain.errors..")
            .should().resideInAPackage("app.domain..")

        rule.check(importedClasses)
    }
}
