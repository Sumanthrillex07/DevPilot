# DevPilot — Learning Notes

This document captures engineering concepts introduced in each part, with concrete connections to our source files.

---

## Part 0 — Blueprint

### Concept: Modular Monolith
A modular monolith is a single deployable application divided into clear internal modules. It avoids the operational complexity of microservices while keeping code organized. We organize by feature concern inside one Spring Boot application.

**Why not microservices?**  
For a team of one and a project of this scale, microservices would add: multiple deployment targets, inter-service HTTP latency, distributed tracing, service discovery, and split databases. The operational cost outweighs the benefit until scale demands it.

### Concept: RAG (Retrieval-Augmented Generation)
RAG solves a fundamental LLM limitation: models cannot know your private codebase. RAG bridges this gap:

```
1. INDEX TIME:  source code -> chunks -> embeddings -> vector store
2. QUERY TIME:  question -> embedding -> similarity search -> relevant chunks
3. PROMPT TIME: retrieved chunks + question -> LLM -> grounded answer
```

Without RAG, the model would hallucinate answers about code it has never seen.
With RAG, the model answers using actual retrieved code and can cite the source.

### Concept: Embeddings and Vector Search
An embedding is a list of floating-point numbers (e.g., 1536 numbers for text-embedding-ada-002) that represents the meaning of a piece of text in a high-dimensional space. Texts with similar meaning have embeddings that are geometrically close.

**Cosine similarity** is the most common distance metric: it measures the angle between two vectors. Similar texts have a small angle (cosine close to 1). pgvector stores these vectors in PostgreSQL and can efficiently find the K nearest vectors to a query embedding.

### Concept: SSE (Server-Sent Events)
SSE is a one-directional HTTP stream from server to client. Unlike WebSockets, it is unidirectional and works over standard HTTP. The client opens a long-lived GET (or POST) connection and the server sends events as they are produced.

In our chat feature:
- Backend produces: user_message -> token (repeated) -> assistant_message -> done
- Frontend reads the stream using ReadableStream/EventSource
- User sees text appearing word-by-word as the model generates it

SSE events look like:
```
event: token
data: "Hello"

event: token
data: " world"

event: done
data: [DONE]
```

### Concept: GitHub OAuth2 Flow
```
1. User clicks "Login with GitHub"
2. Browser redirects to: https://github.com/login/oauth/authorize?client_id=...
3. User grants permission on GitHub
4. GitHub redirects to: http://localhost:8080/login/oauth2/code/github?code=xxx
5. Spring Security exchanges code for access_token via GitHub API
6. GithubOAuth2UserService.loadUser() is called
7. We call GitHubApiClient to get user profile
8. We upsert User in our database (create or update)
9. Spring creates a server-side session and sets DEVPILOT_SESSION cookie
10. Browser is redirected to frontend /auth/callback
11. Frontend calls GET /api/v1/auth/me -> gets user data -> goes to dashboard
```

### Concept: Token Encryption
GitHub access tokens are OAuth credentials. If our database were breached, plaintext tokens would give attackers API access to all users repositories.

Solution: encrypt tokens at rest using AES encryption before storing them.
We use Spring Security's Encryptors.text(password, salt) which uses AES-256-CBC.

The encryption password and salt come from environment variables only:
- DEVPILOT_TOKEN_ENCRYPTOR_PASSWORD
- DEVPILOT_TOKEN_ENCRYPTOR_SALT

This is not perfect (the decryption key lives in the same process), but it is a meaningful improvement over plaintext and follows the principle of defense-in-depth.

### Concept: Flyway Database Migrations
Flyway manages schema evolution through versioned SQL scripts.
Each migration is a file named V{version}__{description}.sql.
Flyway runs outstanding migrations on application startup and records them in flyway_schema_history.

Benefits:
- Schema is version-controlled alongside code
- Migrations are ordered, reproducible, and irreversible (they do not roll back by default)
- Team members get the same schema by running the app
- Production deployments are safe and auditable

We never use spring.jpa.hibernate.ddl-auto=update in production. That approach can silently destroy data on schema changes.

### Concept: @Async and Thread Pool Executor
Repository indexing can take minutes for large repos. We cannot block an HTTP request thread for that long.

@Async tells Spring to execute a method on a different thread from the caller.
We configure a dedicated ThreadPoolTaskExecutor named "indexingExecutor" with:
- corePoolSize=2: always 2 threads ready
- maxPoolSize=4: burst up to 4 concurrent indexing jobs
- queueCapacity=50: queue up to 50 jobs before rejecting

The HTTP request returns 202 Accepted immediately, and indexing continues in the background.

### Concept: Dependency Injection (Constructor Injection)
Instead of services creating their own dependencies:
```java
// BAD: service creates its own dependencies
class ChatService {
    private final VectorStore store = new PgVectorStore(...);  // how do you test this?
}
```

We declare dependencies as constructor parameters and let Spring inject them:
```java
// GOOD: dependencies are injected, easy to mock in tests
@Service
@RequiredArgsConstructor
class ChatService {
    private final VectorStore vectorStore;      // Spring injects this
    private final ChatModel chatModel;          // Spring injects this
}
```

In tests, we pass mock implementations via @MockBean or @Mock + constructor call.

### Concept: DTOs vs Entities
JPA entities are persistence objects - they are tightly coupled to the database schema.
Exposing entities directly in API responses is problematic because:
1. Lazy-loaded collections throw LazyInitializationException outside a session
2. You accidentally expose internal fields (like the encrypted access_token)
3. Adding a field to the entity changes the API contract unexpectedly

DTOs (Data Transfer Objects) are separate classes that define the API contract.
They let entities evolve independently of the API.

We use Java Records for DTOs since they are immutable and concise:
```java
public record RepositoryResponse(UUID id, String fullName, String indexStatus, ...) {}
```

---

## Part 1 — Scaffolding and Local Development

### Concept: Spring Boot 3.4.x & Hibernate 6 Dialect Resolution
In Spring Boot 2.x / Hibernate 5, developers often manually configured `spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.PostgreSQL95Dialect` or `H2Dialect`.
In Hibernate 6 (used by Spring Boot 3.x):
1. **Dialect Auto-Detection**: Hibernate detects the dialect automatically from JDBC metadata (`DatabaseMetaData`).
2. **Explicit Dialect vs Auto-Dialect**: Setting an outdated dialect can trigger warnings (`HHH90000025: Dialect does not need to be specified explicitly`).
3. **H2 in PostgreSQL Mode**: For lightweight context tests (`application-test.yml`), we use `jdbc:h2:mem:devpilot_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` and set `ddl-auto: create-drop`. This allows Spring context tests to load in sub-seconds without needing a running Postgres daemon. In Part 2, real schema and database logic tests run against Testcontainers with Flyway enabled.

### Concept: Separation of Test Configurations
- `application.yml` (Production & Local Dev):
  - `ddl-auto: validate`: Hibernate refuses to run if schema does not match entities.
  - `flyway.enabled: true`: Flyway owns all schema creation and migrations.
- `application-test.yml` (Fast Test Profile):
  - `flyway.enabled: false`: Disabled for fast in-memory H2 tests that only verify Spring context and REST controller routing.
  - `ddl-auto: create-drop`: H2 generates tables automatically for context tests.

### Concept: Vite Reverse Proxy for Development
In production, reverse proxies (like Nginx or Cloudflare) or a unified domain route requests to backend and frontend. In local development:
- Frontend runs on `http://localhost:5173`
- Backend runs on `http://localhost:8080`

Direct browser `fetch('http://localhost:8080/api/...')` triggers CORS preflight checks (`OPTIONS`) and requires credentials configuration.
By adding a proxy in `vite.config.ts`:
```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```
The browser requests `/api/v1/health` on port 5173, and Vite proxies it to port 8080 server-side, eliminating CORS issues during development while maintaining production-identical endpoint paths.

### Concept: TanStack React Query for Asynchronous Server State
React components should not directly manage async loading flags, caching, deduplication, and retry logic in manual `useEffect` hooks.
Using `useQuery`:
- **Declarative Fetching**: Automatically manages `data`, `isLoading`, `isFetching`, and `error`.
- **Automatic Polling**: `refetchInterval: 30000` polls the backend heartbeat every 30 seconds.
- **Cache Management**: Deduplicates identical calls across multiple components.

### Concept: Vitest with React Testing Library in Strict TypeScript
Unlike Jest, Vitest shares Vite's build pipeline and plugin tree (`vite.config.ts`), eliminating duplicate Babel/TS configuration.
Key testing patterns used:
1. `setupFiles: './src/test/setup.ts'` imports `@testing-library/jest-dom/vitest` to extend Vitest's `expect` with DOM matchers like `toBeInTheDocument()`.
2. Mocking API modules using `vi.spyOn(client, 'fetchHealthStatus')` lets us test loading, healthy, and network-failure states deterministically without running the backend server.
3. Wrapping test renders in a fresh `QueryClientProvider` with `retry: false` ensures test isolation and prevents slow retries from blocking test runs.

## Part 2 — Database (To be written after Part 2 is built)

## Part 3 — Security (To be written after Part 3 is built)

## Part 4 — GitHub Integration (To be written after Part 4 is built)

## Part 5 — Chunking (To be written after Part 5 is built)

## Part 6 — Embeddings (To be written after Part 6 is built)

## Part 7 — RAG Chat (To be written after Part 7 is built)
