<p align="center">
  <img src="./docs/audittrove-banner.svg" alt="AuditTrove — Document Review Intelligence" width="100%">
</p>

# AuditTrove

AuditTrove is a Spring Boot service that powers **AI-assisted document review** for the AuditTrove mobile app (iOS and Android). It extracts text from uploaded documents, runs them through an OpenAI-based analysis pipeline, and returns a structured, page-referenced review: a score, an executive summary, attention points with supporting evidence, key metrics, recommended actions, and questions to ask a professional before acting.

The service is the backend for a native mobile client built with React Native / Expo ([AuditTrove-Mobile](https://github.com/mertkaracamm/AuditTrove-Mobile)). It handles device-based authentication, per-device usage quota, long-running analysis as background jobs, and push notifications when a review is ready.

> AuditTrove is a decision-support tool — not financial, accounting, investment, tax, or legal advice. For non-financial documents it reports only what the document itself says and never asserts whether a clause is legal, enforceable, or compliant. Findings must be reviewed by a qualified professional before they are relied upon.

## Supported document types

The analysis prompt adapts to the selected document type. Financial reports are the most deeply supported type; the others are reviewed as "attention points" without any legal/regulatory claims.

- Financial reports (annual/interim reports, financial statements)
- Rental / lease agreements
- Subscription / membership / service commitments
- Insurance policies
- Vehicle purchase / sale agreements
- Employment contracts
- General documents

## Review output

Each review returns:

- A score from 0 (needs line-by-line scrutiny) to 100 (generally clean) — computed **deterministically from the findings**, not taken from the model
- A one-sentence score rationale
- An executive summary
- Attention points (findings) with severity and page-referenced evidence
- Key metrics pulled from the document
- Recommended actions
- Advisor questions to ask before acting

```json
{
  "riskScore": 79,
  "scoreRationale": "…",
  "summary": "…",
  "risks": [{ "title": "…", "severity": "MEDIUM", "finding": "…", "evidence": "… (Sayfa 10)" }],
  "recommendations": ["…"],
  "keyMetrics": [{ "label": "…", "value": "…", "note": "…" }],
  "advisorQuestions": ["…"],
  "references": [{ "source": "Rapor Sayfa 10", "article": "", "title": "" }]
}
```

## How it works

1. A document (PDF, or a phone scan/photo turned into a PDF on the device) is validated and its text is extracted with Apache PDFBox. Owner-password PDFs are opened; only true open-password PDFs are rejected.
2. The text is split into `[REPORT PAGE n]` sections. Large documents are chunked so a long report is reviewed in full rather than truncated.
3. Each section is analyzed with the configured OpenAI model against a type-specific prompt.
4. A deterministic post-process grounds every finding's evidence to a real page marker, calibrates the score from the finding severities, and enforces a single-accounting-standard lock for financial reports (e.g. no mixing TMS and UFRS figures).
5. The result is returned via REST, either synchronously or through the async job API.

## API

Base path: `/api/v1`.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/audit` | Synchronous review (small documents) |
| `POST /api/v1/audit/async` | Start a review; returns `202` with a `jobId` |
| `GET /api/v1/audit/jobs/{id}` | Poll job status/result |
| `POST /api/v1/audit/jobs/{id}/cancel` | Cancel a running review |
| `POST /api/v1/devices` | Register a device, obtain an auth token |
| `POST /api/v1/devices/push-token` | Register the Expo push token for a device |
| `/swagger-ui.html` | Interactive REST API documentation |
| `/api-docs` | OpenAPI specification |
| `/actuator/health` | Service health check |

Long documents are processed in the background so the client never blocks on a timeout. Jobs run on a small thread pool, are held in memory with a 30-minute TTL, and usage quota is only recorded when a job completes successfully. When a job finishes, the service sends an Expo push notification to the device that started it.

## Security and quota

- **Device auth:** stateless HMAC-SHA256 device tokens (`MobileAuthFilter`, `DeviceTokenService`, `DeviceRegistrationController`).
- **Quota:** per-device monthly usage tracked in Postgres; a subscription (verified via RevenueCat) lifts the free-tier limit. Hourly rate limiting protects the API; polling, cancel, and push-token registration are exempt so they don't burn the limit.
- **Privacy:** documents are processed in memory for analysis and are **not** persisted to a database. Only anonymous per-device usage counters are stored.

## Technology

- Java 17 / Spring Boot 3
- Apache PDFBox (text extraction)
- PostgreSQL (device usage; optional pgvector-ready corpus schema)
- Flyway migrations
- OpenAI API
- Expo Push (server-side notifications)
- OpenAPI / Swagger UI
- Docker and Railway

## Run locally

Requirements: Java 17+, Maven 3.9+, an OpenAI API key.

```bash
export OPENAI_API_KEY="your-api-key"
export MOBILE_TOKEN_SECRET="a-long-random-secret"
mvn spring-boot:run
```

Run the tests:

```bash
mvn clean test
```

## Configuration

| Environment variable | Required | Default | Description |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | Yes | — | OpenAI API key used for document analysis |
| `MOBILE_TOKEN_SECRET` | Yes | — | HMAC secret for device auth tokens |
| `OPENAI_MODEL` | No | `gpt-4.1-mini` | Model used for the review |
| `OPENAI_BASE_URL` | No | `https://api.openai.com` | OpenAI API base URL |
| `OPENAI_TIMEOUT_SECONDS` | No | `90` | LLM request timeout |
| `PORT` | No | `8080` | HTTP server port |
| `MAX_FILE_SIZE` | No | `15MB` | Multipart upload limit |
| `MAX_PDF_BYTES` | No | `15728640` | PDF validation limit in bytes |
| `AUDIT_RATE_LIMIT_PER_HOUR` | No | `5` | Hourly request limit per device (production runs higher) |
| `REVENUECAT_API_KEY` | No | — | Verifies subscription entitlements; empty means everyone is free-tier |
| `RAG_DATABASE_URL` | No | — | PostgreSQL JDBC URL (device usage + optional corpus) |
| `RAG_DATABASE_USERNAME` | No | — | PostgreSQL username |
| `RAG_DATABASE_PASSWORD` | No | — | PostgreSQL password |
| `RAG_DATABASE_MIGRATE` | No | `true` | Run Flyway migrations on startup |

## Docker

```bash
docker build -t audittrove .
docker run --rm -p 8080:8080 \
  -e OPENAI_API_KEY="your-api-key" \
  -e MOBILE_TOKEN_SECRET="a-long-random-secret" \
  audittrove
```

## Deploy to Railway

The repository includes a multi-stage `Dockerfile` and `railway.toml`. Connect the GitHub repository to a Railway project, add the environment variables above (at minimum `OPENAI_API_KEY` and `MOBILE_TOKEN_SECRET`), attach a PostgreSQL service, and deploy. Railway uses `/actuator/health` for health checks.

## Project status

AuditTrove is in pre-release, preparing for App Store and Google Play launch. The document review pipeline, async job flow, device auth, quota, push notifications, and Railway deployment are implemented and running. Remaining pre-launch work is store submission (screenshots, listing copy, TestFlight/closed testing) and ongoing report-quality tuning.