# DevPilot — Testing Strategy

## Core Principle
Tests are mandatory for every feature. We never claim a feature is complete without running tests and observing the output. We never fabricate passing results.

---

## Backend Testing Layers

### Layer 1: Unit Tests
- **When:** Business logic that can be tested without Spring context or database
- **Tools:** JUnit 5, Mockito, AssertJ
- **Mocking:** @ExtendWith(MockitoExtension.class), @Mock, @InjectMocks
- **Examples:** CodeFileFilter, CodeChunker, ChatPromptBuilder, CitationMapper, GitHubRateLimiter

### Layer 2: Service Tests with Mocked Dependencies
- **When:** Service orchestration logic, where collaborators are mocked
- **Tools:** JUnit 5, Mockito
- **Examples:** ChatService (mock VectorStore + ChatModel), IndexingService (mock GitHubApiClient + VectorStore), RepositoryService (mock GitHubApiClient + Repository)

### Layer 3: Controller Tests (MockMvc)
- **When:** HTTP layer - request parsing, response serialization, status codes, validation, security
- **Tools:** @WebMvcTest, MockMvc, @MockBean, Spring Security Test
- **Examples:** AuthController, RepositoryController, ChatController
- **Security:** Use @WithMockUser or custom @WithMockAppUser for authenticated tests

### Layer 4: Repository Tests
- **When:** Custom JPQL/queries, pagination, constraint verification
- **Tools:** @DataJpaTest, Testcontainers (PostgreSQL)
- **Database:** Real PostgreSQL via Testcontainers (not H2)
- **Examples:** Custom finders like findByUserIdAndGithubRepoId, findByIdAndUserId

### Layer 5: Integration Tests
- **When:** Full application behavior end-to-end through HTTP
- **Tools:** @SpringBootTest(webEnvironment=RANDOM_PORT), Testcontainers
- **Examples:** OAuth flow, indexing lifecycle, chat session creation

---

## Frontend Testing Layers

### Layer 1: Component Tests
- **When:** Every meaningful React component
- **Tools:** Vitest, React Testing Library
- **Patterns:** render(), userEvent, screen.getBy*
- **Examples:** RepoCard (renders repo data), ChatInput (submit behavior), CitationChip

### Layer 2: Hook Tests
- **When:** Custom hooks with non-trivial state or side effects
- **Tools:** renderHook from React Testing Library
- **Examples:** useChat (SSE state management), useRepos (query state)

### Layer 3: API Interaction Tests
- **When:** Components that call the backend
- **Tools:** MSW (Mock Service Worker) for intercepting fetch/SSE
- **Examples:** Loading states, error states, successful data rendering

---

## What We Always Test

| Category | What |
|---|---|
| Auth | Authenticated vs unauthenticated access to every protected endpoint |
| Ownership | User A cannot access User B's repos, sessions, messages |
| Validation | Missing fields, too-long strings, invalid UUIDs |
| Error paths | 404 Not Found, 400 Bad Request, 401 Unauthorized |
| State machines | Indexing: PENDING -> INDEXING -> READY/FAILED transitions |
| Pagination | Correct page size, sorting, empty page |
| Streaming | SSE event order, cancellation, error events |
| Citations | Citation maps to actually-retrieved chunks, not fabricated |

---

## What We Never Do

- Write tests that only assert a mock was called without verifying meaningful behavior
- Weaken or delete tests to make a build pass
- Skip tests because a feature "looks like it works"
- Make unit tests depend on GitHub credentials, OpenAI API keys, or internet access
- Use H2 in-memory database for tests that involve PostgreSQL-specific features (pgvector, JSONB)
- Fabricate test output

---

## Test Environment Setup

### Backend
```
# Run all tests (requires Docker for Testcontainers)
cd backend
./mvnw test

# Run only unit tests (no Docker needed)
./mvnw test -Dgroups="unit"

# Run integration tests (Docker required)
./mvnw verify -Dgroups="integration"
```

### Frontend
```
cd frontend
npm run test        # vitest in watch mode
npm run test:ci     # vitest run (non-interactive, for CI)
npm run typecheck   # tsc --noEmit
npm run lint        # eslint
npm run build       # production build check
```

---

## Test File Naming Conventions

### Backend
- Unit tests: `SomeServiceTest.java`
- Controller tests: `SomeControllerTest.java`
- Repository tests: `SomeRepositoryTest.java`
- Integration tests: `SomeIntegrationTest.java`

### Frontend
- Component tests: `ComponentName.test.tsx`
- Hook tests: `useHookName.test.ts`
- Utility tests: `utilName.test.ts`

---

## Testcontainers Strategy

Testcontainers starts a real PostgreSQL container for integration tests.
We use a shared static container to avoid restarting it between tests.

```java
// Shared container pattern
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
```

This requires Docker Desktop to be running. If Docker is unavailable:
- Unit tests still pass
- Repository and integration tests are skipped with @EnabledIfSystemProperty
- CI environment must have Docker

---

## Mocking AI Components

For unit tests, we always mock:
- `EmbeddingModel` (returns fixed float arrays)
- `ChatModel` (returns fixed strings)
- `VectorStore` (returns predefined Document lists)

This ensures:
- Tests run without an OpenAI API key
- Tests are deterministic
- Tests are fast (no network calls)
- Tests focus on business logic, not model behavior

---

## Test Coverage Goals Per Part

| Part | Minimum Coverage Goals |
|---|---|
| 1 | Context load, health endpoint, frontend renders |
| 2 | All entity CRUD, custom queries, Flyway migrations |
| 3 | Auth flow, ownership checks, CSRF, session behavior |
| 4 | GitHub API mocks, repo sync, ownership, error mapping |
| 5 | File filter, chunker, line numbers, large files, encodings |
| 6 | Indexing state transitions, idempotency, mocked embeddings |
| 7 | RAG flow, SSE events, citations, cross-user access |
