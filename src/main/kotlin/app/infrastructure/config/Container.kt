package app.infrastructure.config

import app.application.ports.*
import app.application.usecases.*
import app.domain.services.LanguageDetectionService
import app.infrastructure.analytics.GoogleAnalyticsAdapter
import app.infrastructure.api.HttpServerApiAdapter
import app.infrastructure.extractors.HeuristicsLanguageDetector
import app.infrastructure.git.JGitRepositoryAdapter
import app.infrastructure.logging.SentryLoggerAdapter
import app.infrastructure.repositories.FileConfigurationAdapter

/**
 * Dependency injection container.
 * Assembles the object graph by wiring adapters to ports and
 * injecting them into use cases.
 *
 * This is the composition root — the only place where concrete
 * implementations are instantiated and wired together.
 * Equivalent to container.js in the reference project.
 *
 * The container exposes:
 * - Ports (as interfaces) for use by the CLI adapter and UI states
 * - Use cases for executing application logic
 * - Domain services for business rules
 */
class Container {

    // --- Output Adapters (implement ports) ---
    val logger: LoggerPort = SentryLoggerAdapter()
    val configurator: ConfigurationPort = FileConfigurationAdapter()
    val serverApi: ServerApiPort = HttpServerApiAdapter(configurator)
    val gitRepository: GitRepositoryPort = JGitRepositoryAdapter(logger)
    val analytics: AnalyticsPort = GoogleAnalyticsAdapter()

    // --- Domain Services (implemented in infrastructure) ---
    val languageDetector: LanguageDetectionService = HeuristicsLanguageDetector()

    // --- Use Cases ---
    val hashRepositoryUseCase: HashRepositoryUseCase
        get() = HashRepositoryUseCase(serverApi, gitRepository, configurator,
            logger, languageDetector)

    val addRepositoryUseCase: AddRepositoryUseCase
        get() = AddRepositoryUseCase(gitRepository, configurator, logger)

    val removeRepositoryUseCase: RemoveRepositoryUseCase
        get() = RemoveRepositoryUseCase(configurator, logger)

    val listRepositoriesUseCase: ListRepositoriesUseCase
        get() = ListRepositoriesUseCase(configurator, logger)

    val authenticateUserUseCase: AuthenticateUserUseCase
        get() = AuthenticateUserUseCase(serverApi, logger)
}
