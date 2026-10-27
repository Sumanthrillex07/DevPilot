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

### Part 2 — Database Design and Persistence
**Creates:**
- Flyway migrations:
  - V1__create_users.sql
  - V2__create_repositories.sql
  - V3__create_chat_sessions.sql
  - V4__create_chat_messages.sql
- Entity classes: User, ConnectedRepository, ChatSession, ChatMessage
- Repository interfaces: UserRepository, ConnectedRepositoryRepository, etc.
- Basic service methods for sessions and messages
- Testcontainers integration tests for schema and CRUD

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

### Part 5 — Code Discovery and Chunking
**Creates:**
- CodeFileFilter.java (enhanced: sensitive file exclusion)
- CodeChunker.java (enhanced: line-number tracking)
- Chunk metadata model: repoId, branch, commitSha, filePath, language, startLine, endLine, contentHash, chunkText
- Unit tests with fixture files

### Part 6 — Embeddings, pgvector, and Indexing
**Creates:**
- IndexingService.java (@Async orchestration)
- Flyway migration V5: vector_store table with VECTOR(1536)
- IndexController.java: POST /index, GET /index-status
- Idempotency, retry, status transitions
- Tests with mocked EmbeddingClient

### Part 7 — RAG Chat and SSE
**Creates:**
- CodeContextRetriever.java
- ChatPromptBuilder.java
- ChatStreamHandler.java
- CitationMapper.java (with real line ranges)
- ChatController.java: sessions + streaming
- Frontend: ChatView, MessageList, ChatInput, CitationChip
- Tests with mocked ChatModel and VectorStore

### Parts 8–12 — Advanced Features
Planned in order after Part 7 is approved and working.

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
