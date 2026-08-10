<p align="center">
  <img src="./docs/audittrove-banner.svg" alt="AuditTrove — Financial Document Intelligence" width="100%">
</p>

# AuditTrove

AuditTrove is a Spring Boot service for AI-assisted auditing of Turkish financial documents. It extracts text from uploaded PDFs, retrieves relevant financial regulations, and uses an LLM to produce a structured risk assessment with traceable legal references.

The project is being developed as the backend of a **ChatGPT app**. It exposes both a REST API and an MCP endpoint so the document-audit workflow can be connected to ChatGPT after deployment.

> AuditTrove is a decision-support tool, not a substitute for legal advice. Audit findings and regulatory references must be reviewed by a qualified professional before they are relied upon.

## Supported documents

- Consumer and commercial loan agreements
- Credit card agreements
- Bank account agreements
- Bank statements
- Other financial agreements

## Audit output

Each audit returns:

- A risk score from 0 to 100
- An executive summary
- Identified risks and supporting evidence
- Recommended actions
- Relevant regulatory references

Example response:

```json
{
  "riskScore": 81,
  "summary": "The agreement contains several clauses that require review.",
  "risks": [],
  "recommendations": [],
  "references": []
}
```

## How it works

1. A PDF is validated and its text is extracted with Apache PDFBox.
2. The RAG layer retrieves provisions relevant to the document.
3. The retrieved legal context and document text are sent to the configured OpenAI model.
4. The model returns a structured audit result through the REST API or MCP tool.

The legal corpus is designed for regulations from:

- Banking Regulation and Supervision Agency (BDDK)
- Central Bank of the Republic of Türkiye (TCMB)
- Law No. 6502 on Consumer Protection
- Banking Law No. 5411

## Technology

- Java 17
- Spring Boot 3
- Apache PDFBox
- PostgreSQL and pgvector-ready legal corpus schema
- Flyway database migrations
- OpenAI API
- OpenAPI / Swagger UI
- Model Context Protocol (MCP)
- Docker and Railway

## API

### Audit a document

```http
POST /api/v1/audit
Content-Type: multipart/form-data
```

```bash
curl -X POST http://localhost:8080/api/v1/audit \
  -F "file=@agreement.pdf"
```

Other endpoints:

| Endpoint | Purpose |
| --- | --- |
| `/swagger-ui.html` | Interactive REST API documentation |
| `/api-docs` | OpenAPI specification |
| `/actuator/health` | Service health check |
| `/mcp` | MCP endpoint for ChatGPT integration |

## Run locally

Requirements:

- Java 17 or later
- Maven 3.9 or later
- An OpenAI API key

```bash
export OPENAI_API_KEY="your-api-key"
mvn spring-boot:run
```

Run the test suite:

```bash
mvn clean test
```

## Configuration

| Environment variable | Required | Default | Description |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | Yes | — | OpenAI API key used for document analysis |
| `OPENAI_MODEL` | No | `gpt-4.1-mini` | Model used for the audit |
| `OPENAI_BASE_URL` | No | `https://api.openai.com` | OpenAI API base URL |
| `OPENAI_TIMEOUT_SECONDS` | No | `90` | LLM request timeout |
| `PORT` | No | `8080` | HTTP server port |
| `MAX_FILE_SIZE` | No | `15MB` | Multipart upload limit |
| `MAX_PDF_BYTES` | No | `15728640` | PDF validation limit in bytes |
| `RAG_RESULT_LIMIT` | No | `8` | Maximum number of retrieved provisions |
| `RAG_DATABASE_URL` | No | — | PostgreSQL JDBC URL |
| `RAG_DATABASE_USERNAME` | No | — | PostgreSQL username |
| `RAG_DATABASE_PASSWORD` | No | — | PostgreSQL password |
| `RAG_DATABASE_MIGRATE` | No | `true` | Run Flyway migrations on startup |

## Legal corpus and RAG

AuditTrove supports two corpus modes:

- Without `RAG_DATABASE_URL`, the application uses a small bundled development corpus.
- With PostgreSQL and pgvector, Flyway creates a schema for raw official documents, article- and paragraph-level provisions, version history, relationships, embeddings, and audit references.

The bundled records in `src/main/resources/regulations/tr/financial-regulations.json` are development seed data. They are not a complete or authoritative legal dataset. A production deployment must ingest official texts, preserve their effective dates and source URLs, calculate content hashes, and mark records as verified only after expert review.

PostgreSQL example:

```bash
export RAG_DATABASE_URL="jdbc:postgresql://localhost:5432/audittrove"
export RAG_DATABASE_USERNAME="audittrove"
export RAG_DATABASE_PASSWORD="secret"
```

The target PostgreSQL service must provide the `vector` extension.

## Docker

```bash
docker build -t audittrove .
docker run --rm -p 8080:8080 \
  -e OPENAI_API_KEY="your-api-key" \
  audittrove
```

## Deploy to Railway

The repository includes a multi-stage `Dockerfile` and `railway.toml`. Connect the GitHub repository to a Railway project, add `OPENAI_API_KEY`, and deploy. Railway uses `/actuator/health` for health checks.

For database-backed retrieval, add a PostgreSQL service with pgvector support and configure the `RAG_DATABASE_*` variables.

## ChatGPT app integration

AuditTrove includes an MCP tool named `audit_financial_document`. After deploying the service to a stable public HTTPS URL, the intended MCP connection URL is:

```text
https://<your-domain>/mcp
```

Before connecting or publishing the app, validate initialization, tool discovery, schemas, and representative tool calls with MCP Inspector. Then connect the public `/mcp` endpoint in ChatGPT developer mode and complete end-to-end testing.

The MCP integration is currently backend-only; it does not include a custom in-ChatGPT UI.

## Project status

AuditTrove is under active development. The core PDF audit flow, REST endpoint, legal retrieval infrastructure, Docker deployment, and initial MCP endpoint are implemented. The authoritative regulatory corpus, production security controls, and ChatGPT publication review remain pre-release work.
