package test.tests.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.BeforeClass
import org.junit.Test

/**
 * ArchUnit tests specifically for Ports and Adapters pattern validation.
 *
 * Validates the core hexagonal architecture concepts:
 * - Ports define contracts (interfaces) in the application layer
 * - Adapters implement those contracts in the infrastructure layer
 * - The domain is completely isolated from external concerns
 * - Dependency inversion is properly applied at boundaries
 *
 * Reference: "Módulos de alto nível não devem depender de módulos de baixo nível.
 * Devem depender de abstrações. Interfaces."
 */
class PortsAndAdaptersTest {

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
    // OUTPUT PORTS: Application defines what it needs from the outside world
    // "Portas: Representam as interfaces de comunicação do sistema com o mundo externo"
    // =========================================================================

    @Test
    fun `ServerApiPort is an interface in application ports`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("ServerApiPort")
            .should().beInterfaces()
            .andShould().resideInAPackage("app.application.ports..")

        rule.check(importedClasses)
    }

    @Test
    fun `GitRepositoryPort is an interface in application ports`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("GitRepositoryPort")
            .should().beInterfaces()
            .andShould().resideInAPackage("app.application.ports..")

        rule.check(importedClasses)
    }

    @Test
    fun `ConfigurationPort is an interface in application ports`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("ConfigurationPort")
            .should().beInterfaces()
            .andShould().resideInAPackage("app.application.ports..")

        rule.check(importedClasses)
    }

    @Test
    fun `LoggerPort is an interface in application ports`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("LoggerPort")
            .should().beInterfaces()
            .andShould().resideInAPackage("app.application.ports..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // OUTPUT ADAPTERS: Infrastructure implements the ports
    // "Adaptadores: Responsáveis por implementar as portas e fazer a ponte
    //  entre as operações do sistema e a infraestrutura externa"
    // =========================================================================

    @Test
    fun `HttpServerApiAdapter resides in infrastructure`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("HttpServerApiAdapter")
            .should().resideInAPackage("app.infrastructure.api..")

        rule.check(importedClasses)
    }

    @Test
    fun `JGitRepositoryAdapter resides in infrastructure`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("JGitRepositoryAdapter")
            .should().resideInAPackage("app.infrastructure.git..")

        rule.check(importedClasses)
    }

    @Test
    fun `FileConfigurationAdapter resides in infrastructure`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("FileConfigurationAdapter")
            .should().resideInAPackage("app.infrastructure.repositories..")

        rule.check(importedClasses)
    }

    @Test
    fun `SentryLoggerAdapter resides in infrastructure`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("SentryLoggerAdapter")
            .should().resideInAPackage("app.infrastructure.logging..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // INPUT ADAPTERS: CLI is an input adapter in infrastructure
    // "Controllers: Adapter de entrada — Chama o use case"
    // =========================================================================

    @Test
    fun `CliAdapter resides in infrastructure cli`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("CliAdapter")
            .should().resideInAPackage("app.infrastructure.cli..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // DEPENDENCY INVERSION: Use cases depend on port interfaces, not adapters
    // "Não passe classes concretas para um construtor. Passe interfaces."
    // =========================================================================

    @Test
    fun `use cases do not depend on concrete adapters`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application.usecases..")
            .should().dependOnClassesThat().resideInAPackage("app.infrastructure..")

        rule.check(importedClasses)
    }

    @Test
    fun `use cases do not depend on JGit directly`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application.usecases..")
            .should().dependOnClassesThat().resideInAPackage("org.eclipse.jgit..")

        rule.check(importedClasses)
    }

    @Test
    fun `use cases do not depend on Fuel directly`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application.usecases..")
            .should().dependOnClassesThat().resideInAPackage("com.github.kittinunf.fuel..")

        rule.check(importedClasses)
    }

    @Test
    fun `use cases do not depend on Protobuf directly`() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("app.application.usecases..")
            .should().dependOnClassesThat().resideInAPackage("com.google.protobuf..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // COMPOSITION ROOT: Only Container knows about concrete implementations
    // "Injeção de dependência manual no Main.kt"
    // =========================================================================

    @Test
    fun `Container resides in infrastructure config`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("Container")
            .should().resideInAPackage("app.infrastructure.config..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // DOMAIN SERVICES: Interface in domain, implementation in infrastructure
    // "LanguageDetectionService" is a domain interface implemented by infra
    // =========================================================================

    @Test
    fun `LanguageDetectionService is an interface in domain`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("LanguageDetectionService")
            .should().beInterfaces()
            .andShould().resideInAPackage("app.domain.services..")

        rule.check(importedClasses)
    }

    @Test
    fun `HeuristicsLanguageDetector resides in infrastructure`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("HeuristicsLanguageDetector")
            .should().resideInAPackage("app.infrastructure.extractors..")

        rule.check(importedClasses)
    }

    // =========================================================================
    // SERIALIZATION: Protobuf mapping lives in infrastructure only
    // "Banco de dados, REST, gRPC, RabbitMQ, CLI… tudo vira detalhe plugável"
    // =========================================================================

    @Test
    fun `ProtobufMapper resides in infrastructure serialization`() {
        val rule: ArchRule = classes()
            .that().haveSimpleName("ProtobufMapper")
            .should().resideInAPackage("app.infrastructure.serialization..")

        rule.check(importedClasses)
    }

    @Test
    fun `only infrastructure uses Protobuf`() {
        val rule: ArchRule = noClasses()
            .that().resideInAnyPackage("app.domain..", "app.application..")
            .should().dependOnClassesThat().resideInAPackage("com.google.protobuf..")

        rule.check(importedClasses)
    }

    @Test
    fun `only infrastructure uses JGit`() {
        val rule: ArchRule = noClasses()
            .that().resideInAnyPackage("app.domain..", "app.application..")
            .should().dependOnClassesThat().resideInAPackage("org.eclipse.jgit..")

        rule.check(importedClasses)
    }

    @Test
    fun `only infrastructure uses Fuel`() {
        val rule: ArchRule = noClasses()
            .that().resideInAnyPackage("app.domain..", "app.application..")
            .should().dependOnClassesThat().resideInAPackage("com.github.kittinunf.fuel..")

        rule.check(importedClasses)
    }
}
