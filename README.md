# Sourcerer App — Hexagonal Architecture

Refatoração do [sourcerer-app](https://github.com/sourcerer-io/sourcerer-app/) utilizando Arquitetura Hexagonal (Ports and Adapters).

## Disclaimer

Este projeto é baseado no [sourcerer-io/sourcerer-app](https://github.com/sourcerer-io/sourcerer-app/), uma aplicação CLI em Kotlin que analisa repositórios Git e gera perfis de desenvolvedores. O código original é de autoria da Sourcerer Inc. e está licenciado sob MIT.

A refatoração foi realizada com fins acadêmicos para comparação de métricas de qualidade (ex: SonarQube) entre diferentes padrões arquiteturais. O escopo funcional é idêntico ao projeto original — apenas a organização arquitetural foi alterada.

## Objetivo

Verificar como métricas de qualidade de software variam conforme diferentes padrões arquiteturais, mantendo exatamente o mesmo escopo funcional entre as versões.

## Arquitetura

```
Entrada CLI
    |
    v
Adaptadores de entrada (infrastructure/cli)
    |
    v
Casos de uso (application/usecases)
    |
    v
Domínio (domain)
    |
    v
Portas de saída (application/ports)
    |
    v
Adaptadores de saída (infrastructure)
```

## Estrutura do Projeto

```
src/main/kotlin/app/
├── Main.kt                              # Ponto de entrada e composição
│
├── domain/                              # Camada central — sem dependências externas
│   ├── entities/                        # Entidades do domínio
│   │   ├── Author.kt
│   │   ├── AuthorDistance.kt
│   │   ├── Commit.kt
│   │   ├── Fact.kt
│   │   ├── LocalRepo.kt
│   │   ├── Repo.kt
│   │   └── User.kt
│   ├── valueobjects/                    # Objetos de valor
│   │   ├── CommitStats.kt
│   │   ├── DiffContent.kt
│   │   ├── DiffFile.kt
│   │   ├── DiffRange.kt
│   │   ├── Process.kt
│   │   ├── ProcessEntry.kt
│   │   ├── RepoMeta.kt
│   │   └── UserEmail.kt
│   ├── errors/                          # Erros de domínio
│   │   ├── DomainError.kt
│   │   ├── EmptyRepoException.kt
│   │   ├── HashingException.kt
│   │   └── InvalidRepoException.kt
│   └── services/                        # Serviços de domínio (regras puras)
│       ├── CommitStatsExtractorService.kt
│       ├── FactCodes.kt
│       ├── LanguageDetectionService.kt
│       └── RehashCalculatorService.kt
│
├── application/                         # Casos de uso e portas
│   ├── ports/                           # Contratos (interfaces) com o mundo externo
│   │   ├── AnalyticsPort.kt
│   │   ├── ConfigurationPort.kt
│   │   ├── GitRepositoryPort.kt
│   │   ├── LoggerPort.kt
│   │   ├── Result.kt
│   │   └── ServerApiPort.kt
│   └── usecases/                        # Orquestração de funcionalidades
│       ├── AddRepositoryUseCase.kt
│       ├── AuthenticateUserUseCase.kt
│       ├── HashRepositoryUseCase.kt
│       ├── ListRepositoriesUseCase.kt
│       └── RemoveRepositoryUseCase.kt
│
└── infrastructure/                      # Adaptadores (bordas da aplicação)
    ├── config/                          # Composição de dependências
    │   └── Container.kt
    ├── cli/                             # Adaptador de entrada: CLI
    │   ├── CliAdapter.kt
    │   ├── ConsoleContext.kt
    │   ├── ConsoleState.kt
    │   ├── ConsoleUi.kt
    │   ├── OpenState.kt
    │   ├── AuthState.kt
    │   ├── ListRepoState.kt
    │   ├── AddRepoState.kt
    │   ├── EmailState.kt
    │   ├── UpdateRepoState.kt
    │   └── CloseState.kt
    ├── api/                             # Adaptador de saída: servidor HTTP
    │   └── HttpServerApiAdapter.kt
    ├── git/                             # Adaptador de saída: Git
    │   └── JGitRepositoryAdapter.kt
    ├── repositories/                    # Adaptador de saída: configuração
    │   └── FileConfigurationAdapter.kt
    ├── analytics/                       # Adaptador de saída: analytics
    │   └── GoogleAnalyticsAdapter.kt
    ├── logging/                         # Adaptador de saída: logging
    │   └── SentryLoggerAdapter.kt
    ├── extractors/                      # Adaptador: detecção de linguagem
    │   ├── Extractor.kt
    │   └── HeuristicsLanguageDetector.kt
    └── serialization/                   # Mappers protobuf
        └── ProtobufMapper.kt
```

## Preceitos da Arquitetura Hexagonal Seguidos

### 1. Portas e Adaptadores (Ports and Adapters)

- **Portas** são interfaces definidas na camada de aplicação (`application/ports/`) que representam os contratos de comunicação com o mundo externo.
- **Adaptadores** são implementações concretas dessas portas que vivem na camada de infraestrutura (`infrastructure/`).

### 2. Proteção do Domínio

O domínio não importa nenhum framework externo (JGit, Fuel, Protobuf, Jackson, Sentry, JCommander). Banco de dados, REST, gRPC, CLI — tudo é detalhe plugável. O negócio está preservado.

### 3. Direção de Dependência: De Fora para Dentro

```
infrastructure/ → application/ → domain/
     (fora)         (meio)        (dentro)
```

Os adaptadores dependem das portas. As portas dependem do domínio. O domínio não depende de nada externo.

### 4. Inversão de Dependência

Módulos de alto nível não dependem de módulos de baixo nível. Ambos dependem de abstrações (interfaces). Os use cases recebem portas no construtor, nunca classes concretas.

### 5. Casos de Uso como Porta de Entrada

Cada funcionalidade da aplicação tem uma classe de use case. O adaptador de entrada (CLI) chama o use case, que orquestra o domínio e acessa portas de saída.

### 6. Software Baseado em Lego

Para trocar JGit por outro client Git, basta criar um novo adaptador que implemente `GitRepositoryPort`. Para trocar Fuel por OkHttp, basta criar um novo `HttpServerApiAdapter`. Nenhuma alteração no domínio ou nos use cases.

### 7. Composição na Raiz

A injeção de dependência é feita manualmente no `Container.kt` — o único lugar onde classes concretas de infraestrutura são instanciadas e conectadas.

### 8. Separação de Complexidades

- **Complexidade de negócio**: `domain/services/`
- **Complexidade técnica**: `infrastructure/`

## Validação Arquitetural com ArchUnit

O projeto inclui 45 regras ArchUnit que validam automaticamente os preceitos hexagonais:

- Camadas respeitam a direção de dependência
- Domínio e aplicação não importam frameworks
- Portas são interfaces
- Adaptadores residem na infraestrutura
- Use cases não dependem de implementações concretas
- Serialização (Protobuf) é exclusiva da infraestrutura

```
src/test/kotlin/test/tests/architecture/
├── HexagonalArchitectureTest.kt    # 23 regras de camadas
└── PortsAndAdaptersTest.kt         # 22 regras de ports & adapters
```

## Stack Tecnológica

| Componente | Tecnologia |
|---|---|
| Linguagem | Kotlin 1.2.31 |
| Build | Gradle |
| Serialização | Protocol Buffers 3.5.1 |
| HTTP Client | Fuel 1.12.1 |
| Git | JGit 4.9.0 |
| Reactive | RxJava 2.1.12 |
| CLI | JCommander 1.72 |
| Testes | Spek 1.1.5 + ArchUnit 1.2.1 |
| Logging | Sentry 1.7.3 |

## Como Executar

```bash
# Build
./do.sh build_jar

# Executar diretamente
java -jar build/libs/sourcerer-app-hex.jar

# Adicionar repositório
java -jar build/libs/sourcerer-app-hex.jar add /path/to/repo

# Listar repositórios
java -jar build/libs/sourcerer-app-hex.jar list
```

## Licença

MIT — veja [LICENSE.md](LICENSE.md).
