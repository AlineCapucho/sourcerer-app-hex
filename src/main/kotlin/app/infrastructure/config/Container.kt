package app.infrastructure.config

import app.application.ports.*
import app.application.usecases.*
import app.domain.services.LanguageDetectionService
import app.infrastructure.api.HttpServerApiAdapter
import app.infrastructure.extractors.HeuristicsLanguageDetector
import app.infrastructure.git.JGitRepositoryAdapter
import app.infrastructure.hashers.AuthorDistanceAdapter
import app.infrastructure.hashers.CodeLongevityAdapter
import app.infrastructure.hashers.FactHasherAdapter
import app.infrastructure.hashers.MetaHasherAdapter
import app.infrastructure.logging.SentryLoggerAdapter
import app.infrastructure.repositories.FileConfigurationAdapter

/**
 * Dependency injection container.
 * Assembles the object graph by wiring adapters to ports and
 * injecting them into use cases.
 *
 * This is the composition root — the only place where concrete
 * implementations are instantiated and wired together.
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

    // --- Domain Services (implemented in infrastructure) ---
    val languageDetector: LanguageDetectionService = HeuristicsLanguageDetector()

    // --- Use Cases ---
    val hashRepositoryUseCase: HashRepositoryUseCase
        get() = HashRepositoryUseCase(
            serverApi = serverApi,
            gitRepository = gitRepository,
            configurator = configurator,
            logger = logger,
            languageDetector = languageDetector,
            factHashingService = FactHasherAdapter(),
            metaHashingService = MetaHasherAdapter(),
            codeLongevityService = CodeLongevityAdapter(logger),
            authorDistanceService = AuthorDistanceAdapter(),
            commitHasherEnabled = app.BuildConfig.COMMIT_HASHER_ENABLED,
            factHasherEnabled = app.BuildConfig.FACT_HASHER_ENABLED,
            longevityEnabled = app.BuildConfig.LONGEVITY_ENABLED,
            metaHasherEnabled = app.BuildConfig.META_HASHER_ENABLED,
            distancesEnabled = app.BuildConfig.DISTANCES_ENABLED
        )

    val addRepositoryUseCase: AddRepositoryUseCase
        get() = AddRepositoryUseCase(gitRepository, configurator, logger)

    val removeRepositoryUseCase: RemoveRepositoryUseCase
        get() = RemoveRepositoryUseCase(configurator, logger)

    val listRepositoriesUseCase: ListRepositoriesUseCase
        get() = ListRepositoriesUseCase(configurator, logger)

    val authenticateUserUseCase: AuthenticateUserUseCase
        get() = AuthenticateUserUseCase(serverApi, configurator, logger)
}
