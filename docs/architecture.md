# DevPilot — Architecture

## 1. Reference Implementation Analysis

### What the Reference Builds
The reference project (`devPilot-master`) is a working AI-powered codebase assistant. It lets authenticated GitHub users connect their repositories, index the source files into a vector database, and then chat with the codebase using retrieval-augmented generation (RAG).

### Reference Technology Stack (Verified from Source)

| Layer | Technology | Version |
|---|---|---|
| Backend framework | Spring Boot | **4.1.0** |
| Java | Java | **21** |
| AI integration | Spring AI | **2.0.0** |
| Vector store | pgvector via spring-ai-starter-vector-store-pgvector | 2.0.0 |
| Database | PostgreSQL 16 with pgvector | pgvector/pgvector:pg16 |
| ORM | Spring Data JPA / Hibernate | managed by Boot |
| Security | Spring Security + OAuth2 Client | managed by Boot |
| Code generation | Lombok | latest |
| Frontend framework | Next.js | **16.2.12** |
| Frontend runtime | React | **19.2.4** |
| TypeScript | TypeScript | ^5 |
| Styling | Tailwind CSS | ^4 |
| UI components | shadcn/ui | ^4.15.0 |
| Data fetching | TanStack React Query | ^5.101.4 |
| Icons | Lucide React | ^1.27.0 |

> **Important:** The reference uses Spring Boot 4.1.0 (non-GA). We use **Spring Boot 3.4.x** (stable GA) and **Spring AI 1.0.0 GA**. The reference uses Next.js; we use **Vite + React** as specified.

---

### Reference Backend Package Structure

```
devPilot.backend
+-- config/
|   +-- AppConfig.java          - AsyncExecutor bean, RestClient.Builder bean
|   +-- CorsConfig.java         - CORS rules read from app.cors.allowed-origins
|   +-- CryptoConfig.java       - TextEncryptor bean for token encryption
|   +-- SecurityConfig.java     - Spring Security filter chain, OAuth2 login
+-- controllers/
|   +-- AuthController.java     - GET /api/auth/login-url, GET /api/auth/me
|   +-- ChatController.java     - POST/GET /api/chat/sessions, POST streaming
|   +-- RepoController.java     - GET/POST /api/repos, POST /api/repos/{id}/index
+-- dto/                        - Record types: request/response contracts
+-- entity/                     - JPA entities: User, Repository, ChatSession, ChatMessage
+-- exceptions/
|   +-- GlobalExceptionHandler  - @RestControllerAdvice, uniform error body
|   +-- BadRequestException
|   +-- NotFoundException
|   +-- UnauthorizedException
+-- repository/                 - Spring Data JPA interfaces
+-- security/
|   +-- AppUserPrincipal        - Wraps User entity as OAuth2User
|   +-- CurrentUser             - Helper to extract principal from SecurityContext
|   +-- GithubOAuth2UserService - Intercepts OAuth2 login, upserts User in DB
+-- services/
    +-- UserService             - upsertFromGitHub, token encrypt/decrypt
    +-- RepoService             - sync GitHub repos, ownership checks, DTO mapping
    +-- ChatService             - RAG pipeline entry point
    +-- ai/
    |   +-- ChatPromptBuilder   - Builds system + user prompts
    |   +-- ChatStreamHandler   - Streams tokens via SSE, persists assistant message
    |   +-- CitationMapper      - Document to CitationDto, JSON serialization
    |   +-- CodeContextRetriever - Vector similarity search with repo filter
    |   +-- RagSettings         - Constants: TOP_K_CHUNKS=8, STREAM_TIMEOUT=180s
    |   +-- RetrievedContext    - Value object: citations + contextText
    +-- github/
    |   +-- GithubApiClient     - RestClient calls: list repos, get tree, get file
    |   +-- GitHubRateLimiter   - Thread.sleep(50ms) between file fetches
    +-- indexing/
        +-- IndexingService     - Async indexing orchestrator
        +-- CodeChunker         - TokenTextSplitter wrapping, metadata attachment
        +-- CodeFileFilter      - Extension/path allowlist, lock file exclusion
```

---

### Reference Data Model

```
users
  id           UUID PK
  github_id    BIGINT UNIQUE NOT NULL
  github_username VARCHAR(100) NOT NULL
  display_name VARCHAR(200) NOT NULL
  avatar_url   VARCHAR(500)
  access_token TEXT NOT NULL   <- AES encrypted at rest
  token_scopes VARCHAR(500)
  created_at   TIMESTAMPTZ NOT NULL

repositories
  id              UUID PK
  user_id         UUID NOT NULL   <- logical FK (no @ManyToOne in reference)
  github_repo_id  BIGINT NOT NULL
  owner           VARCHAR(100) NOT NULL
  name            VARCHAR(200) NOT NULL
  full_name       VARCHAR(300) NOT NULL
  is_private      BOOLEAN NOT NULL
  default_branch  VARCHAR(100) NOT NULL
  language        VARCHAR(100)
  html_url        VARCHAR(500)
  description     TEXT
  index_status    VARCHAR(20) NOT NULL  <- PENDING|INDEXING|READY|FAILED
  indexed_at      TIMESTAMPTZ
  chunk_count     INT NOT NULL DEFAULT 0
  files_total     INT NOT NULL DEFAULT 0
  files_processed INT NOT NULL DEFAULT 0
  error_message   TEXT
  created_at      TIMESTAMPTZ NOT NULL
  updated_at      TIMESTAMPTZ NOT NULL
  UNIQUE(user_id, github_repo_id)

chat_sessions
  id            UUID PK
  user_id       UUID NOT NULL
  repository_id UUID NOT NULL
  title         VARCHAR(200) NOT NULL
  created_at    TIMESTAMPTZ NOT NULL

chat_messages
  id         UUID PK
  session_id UUID NOT NULL
  role       VARCHAR(20) NOT NULL   <- USER | ASSISTANT
  content    TEXT NOT NULL
  citations  TEXT                   <- JSON array of CitationDto
  created_at TIMESTAMPTZ NOT NULL

vector_store (managed by Spring AI pgvector)
  id        UUID PK
  content   TEXT
  metadata  JSONB     <- {repoId, filePath, language, chunkIndex}
  embedding VECTOR(1536)
```

---

### Reference RAG Pipeline (Verified Flow)

```
User Question
     |
     v
ChatService.streamReply()
     | 1. requireSession()  - verify user owns session
     | 2. requireOwned()    - verify user owns repo, repo status=READY
     | 3. save UserMessage to chat_messages
     v
CodeContextRetriever.retrieve(repoId, question)
     | 4. VectorStore.similaritySearch(topK=8, filter=repoId)
     | 5. CitationMapper.fromDocument() -> List<CitationDto>
     v
ChatPromptBuilder
     | 6. systemPrompt(repoFullName)
     | 7. userPrompt(contextText, question)
     v
ChatStreamHandler.stream()
     | 8. ChatClient stream via Spring AI
     | 9. SSE events: user_message -> token* -> assistant_message -> done
     |10. persist completed assistant message + citations JSON
     v
SseEmitter returned to client
```

### Reference Indexing Pipeline (Verified Flow)

```
POST /api/repos/{id}/index
     |
     v
IndexingService.startIndexing()  - set status=INDEXING, save
IndexingService.indexAsync()     - @Async on "indexingExecutor" thread pool
     |
     v
doIndex():
  1. Delete existing vectors for repoId from pgvector
  2. GithubApiClient.getRepoTree() - fetch full file tree
  3. CodeFileFilter.isEligible()   - filter extensions, max size
  4. For each eligible file:
     a. GithubApiClient.getFileContent() - base64 decode
     b. CodeChunker.chunkFile()          - TokenTextSplitter + metadata
     c. Batch List<Document> (BATCH=32)
     d. VectorStore.add(batch)
     e. GitHubRateLimiter.pause()        - 50ms delay per file
  5. markReady()  - status=READY, record counts
  catch Exception => markFailed()
```

---

### Known Issues and Design Gaps in the Reference

| # | Issue | Severity | Our Decision |
|---|---|---|---|
| 1 | No Flyway migrations - schema via Hibernate auto-DDL | High | We use Flyway for all schema changes |
| 2 | No backend tests except empty context-load test | High | Tests mandatory for every feature |
| 3 | CSRF disabled without documentation | Medium | Keep disabled; document tradeoff (SPA+CORS) |
| 4 | Chunker does not track line numbers - citations are null | Medium | Improve chunker to track line ranges |
| 5 | UserService fields are public final (not private final) | Low | Fix to private final |
| 6 | Spring Boot 4.1.0 and Spring AI 2.0.0 (non-GA) | High | Use Spring Boot 3.4.x + Spring AI 1.0.0 GA |
| 7 | Frontend is Next.js, spec requires Vite+React | High | Use Vite + React + TypeScript |

---

## 2. Our Implementation Architecture

### Stack Decisions (Finalized)

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| Backend | Spring Boot | 3.4.x stable | GA, compatible with Spring AI 1.0 |
| Java | Java | 21 | LTS, required by spec |
| Spring AI | spring-ai-bom | 1.0.0 GA | Stable API |
| Embedding | OpenAI text-embedding-ada-002 | via Spring AI | 1536 dims |
| Chat | OpenAI gpt-4o-mini | via Spring AI | Cost-efficient |
| Database | PostgreSQL 16 | pgvector/pgvector:pg16 | Matches reference |
| Migrations | Flyway | managed by Boot | Reference has none - we fix this |
| Frontend | Vite + React 18 | vite ^5, react ^18 | Required by spec |
| TypeScript | TypeScript | ^5.4 | Strict mode |
| Styling | Tailwind CSS | ^3.4 | Spec requirement |
| UI components | shadcn/ui | latest CLI | Spec requirement |
| Data fetching | TanStack React Query | ^5 | Spec requirement |
| Testing BE | JUnit 5, Mockito, AssertJ, Testcontainers | Boot-managed | Spec requirement |
| Testing FE | Vitest, React Testing Library | latest | Spec requirement |

### Package Structure (Our Implementation)

```
com.devpilot.backend
+-- config/
+-- controllers/
+-- dto/
|   +-- request/
|   +-- response/
+-- entities/
|   +-- User.java
|   +-- ConnectedRepository.java   <- renamed to avoid ambiguity with Spring repo interfaces
|   +-- ChatSession.java
|   +-- ChatMessage.java
+-- repositories/
+-- services/
|   +-- UserService.java
|   +-- RepositoryService.java
|   +-- ChatService.java
|   +-- github/
|   +-- indexing/
|   +-- ai/
+-- security/
+-- exceptions/
```

### Key Improvements Over Reference

1. Entity renamed `ConnectedRepository` - avoids naming collision with Spring Data `RepositoryRepository`
2. Proper JPA `@ManyToOne` relationships with explicit FK constraints
3. All schema changes via Flyway migrations (V1__, V2__, etc.)
4. `@PreUpdate` lifecycle hook manages `updatedAt` automatically
5. Chunker enhanced to track `startLine` and `endLine` for accurate citations
6. All secrets from environment variables - never hardcoded
7. Versioned API: `/api/v1/...` prefix

### API Contracts (Our Versioned API)

Base path: `/api/v1`

| Method | Path | Part | Description |
|---|---|---|---|
| GET | /api/v1/health | 1 | Health check |
| GET | /api/v1/auth/me | 3 | Current user |
| POST | /api/v1/auth/logout | 3 | Logout |
| GET | /api/v1/repositories | 4 | List connected repos |
| POST | /api/v1/repositories | 4 | Connect a repo |
| GET | /api/v1/repositories/{id} | 4 | Get repo details |
| DELETE | /api/v1/repositories/{id} | 4 | Disconnect repo |
| GET | /api/v1/github/repos | 4 | List available GitHub repos |
| POST | /api/v1/repositories/{id}/index | 6 | Start indexing |
| GET | /api/v1/repositories/{id}/index-status | 6 | Indexing progress |
| GET | /api/v1/repositories/{id}/sessions | 7 | List chat sessions |
| POST | /api/v1/repositories/{id}/sessions | 7 | Create session |
| GET | /api/v1/sessions/{id}/messages | 7 | Get messages |
| POST | /api/v1/sessions/{id}/messages/stream | 7 | Send message (SSE) |

### Security Architecture

```
Browser
  | HTTPS (local: HTTP)
  v
React App (Vite dev server, port 5173)
  | credentials: "include" (session cookie forwarded)
  v
Spring Boot (port 8080)
  |
  +- CORS: allow only http://localhost:5173, credentials=true
  |
  +- Spring Security Filter Chain
  |   +- OAuth2 login: /oauth2/authorization/github
  |   +- Callback: /login/oauth2/code/github
  |   +- Session cookie: DEVPILOT_SESSION (HttpOnly, SameSite=Lax)
  |   +- Permit: /api/v1/health, /oauth2/**, /login/oauth2/**, /error
  |   +- Require auth: all other /api/v1/**
  |
  +- Authorization (service layer)
      +- requireOwned(resourceId, userId) on every resource access
```

### Indexing Status Machine

```
         +----------+
         |  PENDING |  <- initial state on connection
         +----+-----+
              | POST /index
              v
         +----------+
         | INDEXING |  <- async background thread pool
         +--+----+--+
            |    |
    success |    | exception
            v    v
         +------+  +--------+
         | READY|  | FAILED |
         +------+  +---+----+
                       | POST /index (retry)
                       v
                  +----------+
                  | INDEXING |
                  +----------+
```
