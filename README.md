# REGU — EU AI Act Compliance Engine

An autonomous risk and compliance engine for the EU AI Act (effective 2 August 2026). REGU analyzes AI system documentation and produces structured legal compliance reports with citations to the official legal text.

**Current Status:** Phase 3.2 — EU AI Act legal chunk ingestion pipeline complete.

## 🚀 Project Overview

Target user: EU startup founder asking *"am I in trouble, and what do I need to do?"*

REGU accepts a natural-language description or uploaded document (PDF/DOCX) describing an AI system and produces a structured legal compliance report under the EU AI Act.

### Key Principles
1. **Citation mandatory** — every claim in the report traces to a chunk ID; no uncited sentences.
2. **Law-primary** — legal text is authoritative; use cases and guides serve it, never override it.
3. **Fail-safe not fail-silent** — low confidence results are flagged for manual review.
4. **Versioned corpus** — every chunk carries source, date, version, and status metadata.

## 🛠 Tech Stack

| Concern | Choice |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.0.5 |
| **Build** | Maven Wrapper (`./mvnw`) |
| **Database** | PostgreSQL 17 + pgvector 0.8.2 |
| **Migrations** | Flyway 11.14.1 (manually configured) |
| **ORM** | Spring Data JPA (for domain tables) |
| **Embeddings** | Voyage `voyage-3-large` (1024 dims) |
| **Classification LLM** | Gemini 2.5 Flash |
| **Reasoning LLM** | Claude Sonnet 4.6 |
| **LLM Framework** | Spring AI 2.0 |
| **Frontend** | React 19 + TypeScript + Vite (Phase 7) |
| **Dev Infra** | Docker Compose |

## 📐 RAG Architecture

Four distinct vector tables for precise retrieval:

| Table | Content | Search Method |
|---|---|---|
| `legal_chunks` | EU AI Act full text | Hybrid (Vector + Keyword) |
| `use_case_chunks` | Curated scenarios | Pure Vector |
| `guide_chunks` | Commission guidance | Vector + Metadata Filter |
| `decision_rule_chunks` | FLI Compliance logic | Vector + Metadata Filter |

## 📁 Repository Structure

```
regu/
├── backend/          Spring Boot 4 application
│   ├── src/main/java/com/regu/   Java source code
│   ├── src/main/resources/        Config & Flyway migrations (V1-V9)
│   └── compose.yaml               Docker Compose for local DB
├── corpus/           Ingestion source files (JSON)
│   ├── legal/raw_extraction/      EU AI Act batches
│   ├── decision_rules/            FLI flowchart logic
│   └── interview_questions/       Stage 1 question bank
├── frontend/         Placeholder (Phase 7)
└── docs/             Verification records
```

## 🚀 Getting Started

### Prerequisites

- **Java 21+** (e.g., OpenJDK 25.0.2)
- **Docker Desktop**
- **Voyage AI API Key** (for ingestion)

### 1. Start the Environment

```bash
cd regu/backend
docker compose up -d
```

### 2. Run Ingestion Pipeline

To populate the vector database with the EU AI Act text, decision rules, and interview questions:

```bash
export VOYAGE_API_KEY=your_key_here
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ingest
```

**Ingestion Steps:**
1. Legal chunks (885 chunks)
2. Decision rule chunks (40 rules)
3. Interview questions (15 questions)
4. Foreign key back-fills

### 3. Start the Backend

For normal development (without ingestion):

```bash
./mvnw spring-boot:run
```

Check health: `curl http://localhost:8080/api/v1/health`

## 🧠 Development Gotchas

- **Spring Boot 4 / Flyway:** Autoconfiguration was removed; we use a manual `FlywayConfig` and `JpaOrderingConfig` to ensure Migrations run before Hibernate validation.
- **Strict Typing:** PostgreSQL `SMALLINT` must map to Java `Short` (Hibernate 7 requirement).
- **JSONB Mapping:** JPA entities use `@JdbcTypeCode(SqlTypes.JSON)` for String-to-JSONB mapping.
- **Idempotency:** Ingestion is safe to re-run. Existing chunks are skipped based on `source_chunk_id` or `rule_id`.

## 🗺 Roadmap

- [x] **Phase 1** — Foundation & Skeleton
- [x] **Phase 2** — Data Model & Vector Tables (V1-V7)
- [x] **Phase 3.1** — Stage 1 Interview & FLI Ingestion
- [x] **Phase 3.2** — EU AI Act Legal Text Ingestion (V9)
- [ ] **Phase 4** — Retrieval Layer (Hybrid Search)
- [ ] **Phase 5** — LLM Orchestration
- [ ] **Phase 6** — Report Generation
- [ ] **Phase 7** — React Frontend

## 📄 License

MIT — see [LICENSE](LICENSE).

---

**Disclaimer:** REGU is a decision-support tool. It is not a substitute for qualified legal advice. The EU AI Act is a complex regulation; always consult a legal professional.
