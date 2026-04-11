# REGU — EU AI Act Compliance Engine

An autonomous risk and compliance engine for the EU AI Act (effective 2 August 2026). REGU analyzes AI system documentation and produces structured legal compliance reports with citations to the official legal text.

**Status:** Phase 1 — Foundation complete. The backend runs and connects to PostgreSQL, but no business logic or AI features exist yet.

## What REGU will do (when complete)

- Accept a natural-language description or uploaded document (PDF or DOCX) describing an AI system
- Classify the system under the EU AI Act risk tiers: Unacceptable, High, Limited, or Minimal
- Cite the specific articles, annex points, and paragraphs that justify the classification
- List the concrete obligations that apply (risk management, data governance, human oversight, etc.)
- Produce a structured compliance report with full citations traceable to the source legal text

## What exists today (Phase 1)

- Spring Boot 4 backend running on Java 21
- PostgreSQL 17 with the pgvector extension, managed via Docker Compose
- Flyway database migrations (V1 enables pgvector)
- Environment-aware configuration (dev and prod profiles)
- Global exception handler with a stable JSON error response shape
- A health check endpoint at `/api/v1/health`

No AI, embedding, retrieval, or report logic exists yet. Those are added in Phases 3 through 6.

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.x |
| Build tool | Maven (via Maven Wrapper) |
| Database | PostgreSQL 17 with pgvector extension |
| Migrations | Flyway |
| Containerization | Docker Compose (development database only) |

The AI layer (Spring AI 2.0, Claude Sonnet 4.6, Gemini 2.5 Flash, Voyage embeddings) and the React frontend are planned for later phases and are not present in the current codebase.

## Project structure

```
regu/
├── backend/                       Spring Boot 4 application
│   ├── src/main/java/com/regu/   Java source code (packages scaffolded for all phases)
│   ├── src/main/resources/        Config files, Flyway migrations
│   ├── src/test/java/             Tests
│   ├── compose.yaml               Docker Compose for local PostgreSQL
│   ├── pom.xml                    Maven build configuration
│   └── .env.example               Environment variable template
├── frontend/                      Placeholder for the React frontend (Phase 7)
├── docs/                          Project documentation and verification records
└── README.md                      This file
```

## Getting started

### Prerequisites

- Java 21 or newer (verify with `java -version`)
- Docker Desktop or Docker Engine with the Compose plugin
- Git
- `curl` for health check verification (usually preinstalled)

You do NOT need a global Maven installation. The project uses the Maven Wrapper (`./mvnw`) which downloads the correct Maven version automatically.

### 1. Clone the repository

```bash
git clone <repository-url> regu
cd regu
```

### 2. Start the database

```bash
cd backend
docker compose up -d
```

Wait for the container to become healthy (usually 10–15 seconds). Verify with:

```bash
docker compose ps
```

The `regu-postgres` container should show status `Up` and health `healthy`.

### 3. Run the backend

From the `backend` directory:

```bash
./mvnw spring-boot:run
```

The application starts on port 8080. On the first run, Flyway applies the `V1__enable_pgvector.sql` migration. On subsequent runs, Flyway reports "Schema is up to date. No migration necessary."

### 4. Verify the health endpoint

In another terminal:

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "regu-backend",
  "version": "0.1.0",
  "timestamp": "2026-04-11T12:34:56.789Z"
}
```

If you see this response, Phase 1 is working correctly on your machine.

## Configuration profiles

The backend supports two Spring profiles:

- `dev` (default) — verbose logging, formatted SQL output, relaxed for local development
- `prod` — minimal logging, no SQL output, expects all sensitive values from environment variables

To run with the production profile locally (useful for verifying env-var-based config):

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

## Development commands

From the `backend` directory:

| Command | Purpose |
|---------|---------|
| `./mvnw clean compile` | Compile the backend |
| `./mvnw spring-boot:run` | Start the application (dev profile) |
| `./mvnw test` | Run the test suite |
| `./mvnw clean package` | Build an executable JAR in `target/` |
| `docker compose up -d` | Start the PostgreSQL container |
| `docker compose down` | Stop the PostgreSQL container (data persists in the volume) |
| `docker compose down -v` | Stop and DELETE all database data |

## Error response format

Every error produced by the backend follows this stable JSON shape:

```json
{
  "timestamp": "2026-04-11T12:34:56.789Z",
  "status": 404,
  "error": "Not Found",
  "message": "No endpoint found for GET /api/v1/nonexistent",
  "path": "/api/v1/nonexistent",
  "details": null
}
```

The `details` field is only present for validation errors and lists field-level problems. For all other errors it is omitted from the JSON response entirely.

## Roadmap

- [x] **Phase 1** — Foundation: Spring Boot 4, PostgreSQL, pgvector, Flyway, health endpoint, exception handler
- [ ] **Phase 2** — Data model and vector tables
- [ ] **Phase 3** — Ingestion pipeline (EU AI Act, use case examples, business guides)
- [ ] **Phase 4** — Retrieval layer (vector + keyword hybrid search)
- [ ] **Phase 5** — LLM orchestration (Gemini + Claude routing, cross-check validation)
- [ ] **Phase 6** — Report generation and REST API
- [ ] **Phase 7** — React frontend
- [ ] **Phase 8** — Evaluation harness (30 golden test scenarios)
- [ ] **Phase 9** — Polish and deployment

## License

MIT — see [LICENSE](LICENSE).

---

**Disclaimer:** REGU is a decision-support tool. It is not a substitute for qualified legal advice. The EU AI Act is a complex regulation; always consult a qualified legal professional for compliance decisions that carry regulatory or financial risk.
