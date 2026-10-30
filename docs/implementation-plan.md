# DevPilot — Implementation Plan

## Dependency Graph Between Parts

```
Part 0 (Blueprint)
  |
  v
Part 1 (Scaffolding + Health) <-- All subsequent parts depend on this
  |
  v
Part 2 (Database + Flyway + Entities)
  |
  v
Part 3 (GitHub OAuth + Security) <-- Required before any user-scoped resource
  |
  v
Part 4 (GitHub API + Repository CRUD)
  |
  v
Part 5 (File Discovery + Chunking)   <-- No AI dependency, can be unit-tested offline
  |
  v
Part 6 (Embeddings + pgvector + Indexing)
  |
  v
Part 7 (RAG Chat + SSE) <-- Core product feature
  |
  +-- Part 8 (Code Relationship Analysis) <-- Enhances Part 7 retrieval
  |
  +-- Part 9 (Bug Investigation Agent)
  |
  +-- Part 10 (PR Review)
  |
  +-- Part 11 (Safe Patch Generation)
  |
  v
Part 12 (Observability + Evaluation + Hardening)
```

## Part-by-Part Deliverable Summary

### Part 0 — Blueprint (COMPLETED)
- [x] Inspect reference repository
- [x] Document reference stack and architecture
- [x] Identify gaps and improvements
- [x] Create docs/architecture.md
- [x] Create docs/implementation-plan.md
- [x] Create docs/testing-strategy.md
- [x] Create docs/learning-notes.md

### Part 1 — Scaffolding and Local Development (COMPLETED)
**Backend:**
- [x] backend/ with Maven Wrapper (Spring Boot 3.4.5, Java 21)
- [x] pom.xml: Web, Actuator, Validation, Security, JPA, Test
- [x] BackendApplication.java
- [x] HealthController.java -> GET /api/v1/health
- [x] application.yml & application-test.yml
- [x] Automated tests passing: 7 tests run, 0 failures

**Frontend:**
- [x] frontend/ with Vite + React 19 + TypeScript
- [x] Tailwind CSS configured with dark theme tokens
- [x] TanStack React Query provider
- [x] React Router layout & shell
- [x] HealthStatus component polling /api/v1/health
- [x] Automated Vitest tests passing: 4 tests run, 0 failures
- [x] TypeScript production build verified

**Infrastructure:**
- [x] docker-compose.yml (PostgreSQL 16 + pgvector on port 5433)
- [x] .env.example
- [x] .gitignore
- [x] README.md

### Part 2 — Database Design and Persistence (COMPLETED)
**Flyway Migrations:**
- [x] `V1__create_users.sql` (users table, github_id unique index)
- [x] `V2__create_repositories.sql` (repositories table, user foreign key, unique constraint on user_id + github_repo_id)
- [x] `V3__create_chat_sessions.sql` (chat_sessions table, composite index on user_id + repository_id)
- [x] `V4__create_chat_messages.sql` (chat_messages table, session foreign key, session_id index)

**JPA Entities & Enums:**
- [x] `IndexStatus.java` & `MessageRole.java`
- [x] `User.java`
- [x] `ConnectedRepository.java` (mapped to `@Table(name = "repositories")`)
- [x] `ChatSession.java`
- [x] `ChatMessage.java`

**Repositories & Services:**
- [x] `UserRepository.java`
- [x] `ConnectedRepositoryRepository.java`
- [x] `ChatSessionRepository.java`
- [x] `ChatMessageRepository.java`
- [x] `ChatPersistenceService.java` (session & message CRUD with ownership enforcement)

**Verification & Tests:**
- [x] `FlywayMigrationAndRepositoryTest.java`: Flyway migrations executed with `ddl-auto: validate`, constraints and query methods verified (6 tests passed)
- [x] `ChatPersistenceServiceTest.java`: Service CRUD and security checks verified (8 tests passed)
- [x] `PostgresTestcontainersIntegrationTest.java`: Testcontainers real PostgreSQL container test configured

### Part 3 — Authentication and Security
**Creates:**
- SecurityConfig.java (filter chain, OAuth2, session cookie, CORS)
- CorsConfig.java
- CryptoConfig.java (token encryption)
- GitHubOAuth2UserService.java
- AppUserPrincipal.java, CurrentUser.java
- AuthController.java: GET /api/v1/auth/me, POST /api/v1/auth/logout
- Security tests with MockMvc and @WithMockUser

### Part 4 — GitHub Repository Integration
**Creates:**
- GitHubApiClient.java (RestClient-based, paginated)
- GitHubRateLimiter.java
- RepositoryController.java (CRUD endpoints)
- RepositoryService.java (sync, ownership, DTO mapping)
- Frontend: RepoList, RepoCard, RepoDetail components
- WireMock/MockServer tests for GitHub API responses

### Part 5 — Code Discovery and Chunking (COMPLETED)
**Creates:**
- [x] CodeFileFilter.java (noise reduction, sensitive credential exclusion, size bounds, language detection)
- [x] CodeChunker.java (line-aware sliding window, contextual header, SHA-256 hash)
- [x] CodeChunk.java & RagSettings.java (metadata model with 1-indexed startLine/endLine and Spring AI Document conversion)
- [x] CodeFileFilterTest (52 parameterized unit tests passing)
- [x] CodeChunkerTest (5 unit tests verifying bounds, overlap, hash determinism, and Document mapping)

### Part 6 — Embeddings, pgvector, and Indexing (COMPLETED)
**Creates:**
- [x] VectorStoreConfig.java (PgVectorStore with HNSW cosine distance; SimpleVectorStore / StubEmbeddingModel fallback for offline/test)
- [x] AsyncConfig.java (@EnableAsync with dedicated indexingExecutor ThreadPoolTaskExecutor)
- [x] Flyway migration V5: vector_store table with VECTOR(1536), HNSW index, GIN metadata index
- [x] IndexingService.java (@Async pipeline: cleanup, discovery, filtering, batch chunking, rate limiting, progress tracking)
- [x] RepoController.java: POST /api/v1/repos/{id}/index endpoint (202 Accepted)
- [x] IndexingServiceTest & RepoControllerTest (7 new unit/mockMvc tests passing)

### Part 7 — RAG Chat and SSE (COMPLETED)
**Creates:**
- [x] `CitationDto.java` & `RetrievedContext.java`: Structured citation data transport objects with start/end lines and language.
- [x] `CitationMapper.java`: Maps Spring AI `Document` metadata to `CitationDto` and JSON persistence.
- [x] `CodeContextRetriever.java`: Vector similarity search filtered by `repoId` with top-K limit and similarity threshold.
- [x] `ChatPromptBuilder.java`: Grounded system prompts with Markdown citation instructions (`[file.ext:L1-L20]`) and context formatting.
- [x] `ChatModelConfig.java`: Spring AI `ChatClient` and `OpenAiChatModel` configuration with fallback `StubChatModel` for testing/offline environments.
- [x] `ChatStreamHandler.java`: Reactive SSE token streaming with structured events: `user_message`, `token`, `assistant_message`, `done`, and error handling.
- [x] `ChatService.java`: Chat session creation, history retrieval, prompt grounding, message persistence, and SSE stream orchestration.
- [x] `ChatController.java`: Endpoints:
  - `POST /api/v1/chat/sessions` (Create session)
  - `GET /api/v1/chat/sessions?repositoryId={id}` (List sessions)
  - `GET /api/v1/chat/sessions/{id}` (Get messages with citations)
  - `POST /api/v1/chat/sessions/{id}/messages` (SSE token stream)
- [x] Unit & MockMvc Tests:
  - `CitationMapperTest.java`: 3 tests passed.
  - `ChatPromptBuilderTest.java`: 2 tests passed.
  - `CodeContextRetrieverTest.java`: 2 tests passed.
  - `ChatServiceTest.java`: 5 tests passed.
  - `ChatControllerTest.java`: 8 WebMvc tests passed.
  - Full suite: 128 tests passing, 0 failures, 1 skipped (Postgres container).

### Part 8 — Code Relationship Analysis and Dependency Graph (COMPLETED)
**Creates:**
- [x] `SymbolType.java` & `RelationType.java`: Enums modeling code symbols (CLASS, INTERFACE, FUNCTION, IMPORT, PACKAGE) and relationships (IMPORTS, EXTENDS, IMPLEMENTS, DEFINES, CALLS).
- [x] `CodeSymbol.java` & `CodeRelation.java`: Typed records capturing source, target, relation type, file path, and line numbers.
- [x] `CodeRelationshipExtractor.java`: Multi-language AST-lite parser extracting symbols and dependency edges across Java, TypeScript/JavaScript, Python, and Go.
- [x] `DependencyGraph.java` & `DependencyGraphService.java`: Graph data structure and service managing node/edge storage, 1-hop neighbor lookup, and repository ownership checks.
- [x] `DependencyGraphController.java`: `GET /api/v1/repos/{id}/graph` endpoint returning graph nodes and edges for visualization.
- [x] Graph-Augmented RAG: Updated `CodeContextRetriever.java` and `IndexingService.java` to extract relationships during file indexing and inject 1-hop symbol references into the LLM context.
- [x] Unit & WebMvc Tests:
  - `CodeRelationshipExtractorTest.java`: 5 unit tests verifying all 4 languages.
  - `DependencyGraphServiceTest.java`: 3 tests verifying traversal, ownership checks, and 404s.
  - `DependencyGraphControllerTest.java`: 2 WebMvc tests verifying authentication and response structure.
  - Full suite: 139 tests run, 0 failures, 1 skipped.

### Part 9 — Autonomous Bug Investigation Agent (COMPLETED)
**Creates:**
- [x] `StackTraceParser.java`: Multi-language stack trace and error log parser (Java, Python, TypeScript/JavaScript) extracting exception types, error messages, and identifying the top application stack frame while filtering out framework frames.
- [x] `InvestigateBugRequest.java`: DTO modeling bug report submissions with description, raw error logs, failing test snippets, and environment details.
- [x] `BugReportResponse.java`: Structured diagnostic response containing summary, root cause, suspected files, numbered reproduction steps, suggested fixes, and code citations.
- [x] `BugInvestigationService.java`: Autonomous agent combining stack trace analysis, semantic vector retrieval via `CodeContextRetriever`, dependency graph queries, and grounded LLM reasoning.
- [x] `BugInvestigationController.java`: `POST /api/v1/repos/{id}/investigate` endpoint.
- [x] `ChatModelConfig.java`: Exposes configured `ChatClient` bean for direct injection into agent services.
- [x] Unit & WebMvc Tests:
  - `StackTraceParserTest.java`: 4 unit tests verifying Java, Python, TS/JS stack trace parsing and empty log handling.
  - `BugInvestigationServiceTest.java`: 3 unit tests verifying repository ownership, 404s, and end-to-end diagnosis pipeline.
  - `BugInvestigationControllerTest.java`: 2 WebMvc tests verifying authentication and JSON response serialization.
  - Full suite: 148 tests run, 0 failures, 1 skipped.

### Part 10 — Pull Request Review & Code Quality Agent (COMPLETED)
**Creates:**
- [x] `GithubApiClient.java`: Added PR metadata, raw diff, and changed files endpoints (`getPullRequest`, `getPullRequestDiff`, `getPullRequestFiles`).
- [x] `UnifiedDiffParser.java`: Git unified diff parser decomposing diffs into `FileDiff`, `DiffHunk`, and `DiffLine` models with precise line tracking.
- [x] `ReviewDiffRequest.java`: DTO modeling raw diff review requests.
- [x] `PrReviewCommentDto.java`: Structured inline review comments featuring `CommentSeverity` (CRITICAL, WARNING, INFO, SUGGESTION) and `CommentCategory` (SECURITY, BUG_RISK, PERFORMANCE, MAINTAINABILITY, CODE_STYLE).
- [x] `PrReviewResponse.java`: High-level review response containing overall `ReviewVerdict` (APPROVED, COMMENT, REQUEST_CHANGES), risk score, file count, comments, and citations.
- [x] `PrReviewService.java`: Autonomous review orchestration combining GitHub PR diff fetching, unified diff parsing, vector search for context via `CodeContextRetriever`, LLM review prompting, and structured comment extraction.
- [x] `PrReviewController.java`: REST endpoints `POST /api/v1/repos/{id}/reviews/github/{prNumber}` and `POST /api/v1/repos/{id}/reviews/diff`.
- [x] Unit & WebMvc Tests:
  - `UnifiedDiffParserTest.java`: 3 unit tests verifying multi-file diff parsing, hunk line tracking, and empty diff handling.
  - `PrReviewServiceTest.java`: 4 unit tests verifying repository ownership checks, 404s, raw diff reviews, and GitHub PR reviews.
  - `PrReviewControllerTest.java`: 3 WebMvc tests verifying authentication, request validation, and JSON response serialization.
  - Full suite: 158 tests run, 0 failures, 1 skipped.

### Part 11 — Safe Patch Generation & Validation Agent (COMPLETED)
**Creates:**
- [x] `UnifiedDiffParser.java`: Annotated with `@Component` and upgraded with fallback parsing for direct `--- a/...` and `+++ b/...` diff headers.
- [x] `PatchApplier.java`: In-memory patch application engine applying hunk insertions, deletions, and modifications to original file strings with line mismatch detection without filesystem access.
- [x] `PatchSafetyValidator.java`: Multi-stage security and syntax validator enforcing path bounds (preventing path traversal `..` and edits to sensitive files like `.env`, `.git`), detecting hazardous code constructs (`Runtime.exec`, hardcoded secrets, tokens), checking bracket balance, and dry-run verifying applicability.
- [x] DTOs:
  - `GeneratePatchRequest.java`: Input model with instruction, target file, and error context.
  - `ValidatePatchRequest.java`: Input model for validating arbitrary diffs.
  - `PatchValidationResult.java`: Result structure with error/warning lists, change metrics, and dry-run status.
  - `DryRunPatchRequest.java` & `DryRunPatchResponse.java`: In-memory dry-run execution payloads.
  - `GeneratedPatchResponse.java`: Full AI patch output with explanation, unified diff, affected files, validation summary, and citations.
- [x] `PatchGenerationService.java`: Autonomous agent combining repository ownership verification, vector retrieval via `CodeContextRetriever`, grounded LLM prompting (`ChatClient`), diff sanitization, and safety validation.
- [x] `PatchController.java`: REST endpoints `POST /api/v1/repos/{id}/patches/generate`, `POST /api/v1/repos/{id}/patches/validate`, `POST /api/v1/repos/{id}/patches/dry-run`.
- [x] Automated Tests:
  - `PatchApplierTest.java`: 3 tests verifying single hunk, multi-hunk, and line mismatch detection.
  - `PatchSafetyValidatorTest.java`: 5 tests verifying path traversal prevention, protected file protection, malicious code flags, and dry run validation.
  - `PatchGenerationServiceTest.java`: 5 tests verifying ownership checks, 404s, LLM patch generation, validation, and dry-run delegation.
  - `PatchControllerTest.java`: 4 WebMvc tests verifying authentication, request handling, and JSON serialization.
  - Full suite: 175 tests run, 0 failures, 1 skipped.

### Part 12 — Observability, Evaluation & Hardening
- Observability and metrics pipeline
- Evaluation benchmarks for RAG retrieval and code quality



---

## Version Compatibility Matrix

| Component | Version | Compatibility Note |
|---|---|---|
| Java | 21 | Required by spec, LTS |
| Spring Boot | 3.4.x | GA, latest stable 3.x |
| Spring AI BOM | 1.0.0 | GA release, stable API surface |
| spring-ai-openai | 1.0.0 | Via BOM |
| spring-ai-pgvector | 1.0.0 | Via BOM |
| PostgreSQL JDBC | managed by Boot | Postgres 16 compatible |
| Flyway | managed by Boot | Auto-applies migrations on startup |
| Testcontainers | 1.19.x | Compatible with JUnit 5 |
| React | 18.x | Stable LTS release |
| Vite | 5.x | Latest stable |
| TypeScript | 5.4+ | Strict mode |
| Tailwind CSS | 3.4.x | Stable v3 (v4 still in beta) |
| TanStack Query | 5.x | Latest stable |
| Vitest | 1.x | Compatible with Vite 5 |

---

## What We Retain from Reference

- Overall RAG architecture (retrieve -> prompt -> stream)
- Indexing async pattern (@Async + ThreadPoolTaskExecutor)
- GitHub API pagination approach
- Token encryption via Spring Security Encryptors
- SSE event protocol (user_message, token, assistant_message, done)
- File filter extension allowlist
- CORS + session cookie security model
- Global exception handler structure

## What We Redesign

- Schema management: reference uses auto-DDL; we use Flyway
- Entity naming: `ConnectedRepository` not `Repository`
- API versioning: `/api/v1/` prefix
- Chunker: add real line-number tracking
- Test coverage: comprehensive vs nearly zero
- Frontend: Vite+React vs Next.js
- Spring Boot version: 3.4.x vs 4.1.0
- Spring AI version: 1.0.0 GA vs 2.0.0

## What Is New (Not in Reference)

- Parts 8-12: code relationships, bug investigation, PR review, patch generation, evaluation
- Flyway-managed vector schema
- Testcontainers integration tests
- Full frontend test suite
- Observability and metrics
- Docker isolation for patch execution
