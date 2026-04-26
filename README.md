# REGU - EU AI Act Compliance Engine

> Structured first-pass risk classification for your AI system, grounded in the EU AI Act. Every claim cited to a specific paragraph. Delivered in under five minutes.

![Status](https://img.shields.io/badge/status-MVP-orange)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-green)
![React](https://img.shields.io/badge/React-19-61dafb)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

---

## What is REGU?

REGU is a compliance analysis engine for the EU AI Act (enforcement begins **2 August 2026**). It takes a natural-language description or uploaded document about an AI system and produces a structured legal report: risk tier classification, applicable articles, Annex IV documentation gaps - all cited to the exact paragraph in the regulation.

**Target user:** An EU startup founder who needs to know if they're affected by the Act, and what they need to do about it - without hiring a compliance team.

> REGU is currently in MVP stage and not open for public testing.
> Contact us at [alpgiray.dev@gmail.com](mailto:alpgiray.dev@gmail.com) or [cnumanberk@gmail.com](mailto:cnumanberk@gmail.com) for early access.

---

## Core Principles

| Principle | Description |
|---|---|
| **Citation mandatory** | Every claim traces to a chunk ID. No uncited sentences. |
| **Law-primary** | Legal text is authoritative. Use cases and guides serve it, never override it. |
| **Fail-safe** | Low-confidence results are flagged for manual review, never passed silently. |
| **Versioned corpus** | Every chunk carries source, date, version, and status metadata. |

---

## Tech Stack

### Backend

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Build | Maven Wrapper (`./mvnw`) |
| Database | PostgreSQL 17 + pgvector 0.8.2 |
| Migrations | Flyway 11.14.1 |
| ORM | Spring Data JPA |
| Embeddings | Voyage `voyage-3-large` (1024 dims) |
| LLM | Claude Sonnet |
| LLM Framework | Spring AI 2.0 |
| Dev Infra | Docker Compose |

### Frontend

| Concern | Choice |
|---|---|
| Framework | React 19 + TypeScript |
| Build | Vite |
| Styling | Tailwind CSS v4 |
| Animation | Framer Motion |
| Routing | React Router v7 |

---

## RAG Architecture

Four distinct vector tables for precise, scoped retrieval:

| Table | Content | Search Method |
|---|---|---|
| `legal_chunks` | EU AI Act full text (885 chunks) | Hybrid (Vector + Keyword) |
| `use_case_chunks` | Curated real-world scenarios | Pure Vector |
| `guide_chunks` | Commission guidance documents | Vector + Metadata Filter |
| `decision_rule_chunks` | FLI Compliance Checker logic | Vector + Metadata Filter |

The pipeline: user input -> embedding (Voyage) -> retrieval across all four tables -> LLM generation (Claude Sonnet) -> citation validator -> structured JSON report.

---

## Repository Structure

```
regu/
├── backend/
│   ├── src/main/java/com/regu/     Java source
│   ├── src/main/resources/          Config & Flyway migrations (V1-V9)
│   └── compose.yaml                 Docker Compose for local DB
├── corpus/
│   ├── legal/raw_extraction/        EU AI Act batches
│   ├── decision_rules/              FLI flowchart logic
│   └── interview_questions/         Stage 1 question bank
├── frontend/
│   ├── src/components/              React components
│   ├── src/pages/                   Route-level pages
│   └── src/lib/                     API client & utilities
└── docs/                            Verification records
```

---

## Getting Started

### Prerequisites

- Java 21+
- Docker Desktop
- Voyage AI API key (for ingestion)
- Anthropic API key (for LLM calls)

### 1. Start the database

```bash
cd backend
docker compose up -d
```

### 2. Run ingestion (one-time)

Populates the vector database with the EU AI Act text, decision rules, and interview questions:

```bash
export VOYAGE_API_KEY=your_key_here
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ingest
```

Ingestion steps:
1. Legal chunks (885 chunks, EU AI Act full text)
2. Decision rule chunks (40 rules from FLI Compliance Checker)
3. Interview questions (15 Stage 1 questions)
4. Foreign key back-fills

Ingestion is idempotent - safe to re-run. Existing chunks are skipped by `source_chunk_id` / `rule_id`.

### 3. Start the backend

```bash
./mvnw spring-boot:run
```

Health check: `curl http://localhost:8080/api/v1/health`

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Opens at `http://localhost:5173`.

---

## Known Gotchas

- **Spring Boot 4 / Flyway:** Autoconfiguration was removed in Boot 4. We use a manual `FlywayConfig` and `JpaOrderingConfig` to ensure migrations run before Hibernate schema validation.
- **Strict typing:** PostgreSQL `SMALLINT` must map to Java `Short` (Hibernate 7 requirement).
- **JSONB mapping:** JPA entities use `@JdbcTypeCode(SqlTypes.JSON)` for `String`-to-JSONB mapping.

---

## Roadmap

- [x] Phase 1 - Foundation & skeleton
- [x] Phase 2 - Data model & vector tables (V1-V7)
- [x] Phase 3.1 - Stage 1 interview & FLI ingestion
- [x] Phase 3.2 - EU AI Act legal text ingestion (V9)
- [x] Phase 4 - Retrieval layer (hybrid search)
- [x] Phase 5 - LLM orchestration
- [x] Phase 6 - Report generation
- [x] Phase 7 - React frontend
- [ ] Phase 8 - Open beta

---

## Contact

Built in the EU by [Alpgiray Celik](mailto:alpgiray.dev@gmail.com) and [Caner Uman Berk](mailto:cnumanberk@gmail.com).

---

## License

MIT - see [LICENSE](LICENSE).

**Disclaimer:** REGU is a decision-support tool, not a substitute for qualified legal advice. Always consult a legal professional for compliance decisions.
