# DevPilot — AI-Powered Codebase Assistant

DevPilot is an AI-powered developer assistant designed to help software engineers navigate, understand, query, investigate bugs in, and review code across their GitHub repositories using state-of-the-art Retrieval-Augmented Generation (RAG) and Agentic Workflows.

---

## 🛠 Tech Stack

- **Backend**: Java 21, Spring Boot 3.4.x, Spring Data JPA, Spring Security (OAuth2), Flyway, Testcontainers, Spring AI (GA 1.0.0)
- **Database**: PostgreSQL 16 + pgvector extension
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, TanStack React Query, React Router, Lucide Icons
- **Testing**: JUnit 5, MockMvc, Testcontainers, Vitest, React Testing Library

---

## 🚀 Getting Started

### 1. Prerequisites
- **Java 21** or later
- **Node.js 20+** and npm
- **Docker** and Docker Compose

### 2. Environment Setup
Copy the environment template:
```bash
cp .env.example .env
```

### 3. Start Database
```bash
docker compose up -d
```
PostgreSQL with `pgvector` will start on `localhost:5433`.

### 4. Run Backend
```bash
cd backend
./mvnw spring-boot:run
```
Backend health check is accessible at:
- `http://localhost:8080/api/v1/health`
- `http://localhost:8080/actuator/health`

To run backend tests:
```bash
./mvnw test "-Dspring.profiles.active=test"
```

### 5. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

To run frontend tests:
```bash
npm run test
```

---

## 📁 Repository Structure
```
DevPilot/
├── backend/            # Spring Boot 3 backend application
│   ├── src/main/       # Production source code
│   └── src/test/       # Unit and integration test suites
├── frontend/           # Vite + React + TypeScript frontend
│   ├── src/            # Components, hooks, routes, API clients
│   └── public/         # Static assets
├── docs/               # Architecture, design decisions, implementation plans
├── docker-compose.yml  # Local services (PostgreSQL + pgvector)
└── .env.example        # Environment variable definitions
```
