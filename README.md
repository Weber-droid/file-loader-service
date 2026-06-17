# USSD File Loader Service

A Spring Boot service that ingests pipe-delimited USSD Call Detail Record (CDR) files from a watched input directory, persists them in batch to PostgreSQL, and archives processed files. It is designed for high-throughput, low-memory ingestion as part of the PAiCore USSD infrastructure.

## Overview

The service polls a configured input folder every 60 seconds. When a new file is detected, it:

1. Creates an audit row in `cdr_logs` with the upload start time.
2. Reads the file line-by-line using a `BufferedReader` to keep memory usage low.
3. Parses each row into a `call_detail_records` entity and persists records in configurable batches (default: 500).
4. Counts malformed rows without aborting the entire file.
5. Updates `cdr_logs` with end time, success count, and failed count.
6. Moves the file to the archive directory on successful completion.

```
  ┌─────────────┐     every 60s      ┌──────────────────┐
  │ input/      │ ─────────────────► │ FileWatcherService│
  │  *.cdr      │                    └────────┬─────────┘
  └─────────────┘                             │
                                              ▼
                                   ┌─────────────────────┐
                                   │ CdrIngestionService │
                                   │  parse → batch save │
                                   └────────┬────────────┘
                                            │
                         ┌──────────────────┼──────────────────┐
                         ▼                  ▼                  ▼
                 call_detail_records    cdr_logs         archive/
```

## Tech stack

| Component | Version / choice |
|-----------|------------------|
| Java | 17 |
| Spring Boot | 4.1.0 |
| Spring Data JPA | Hibernate batch inserts |
| Database | PostgreSQL (`ussd` schema/database) |
| Build | Maven (wrapper included) |
| Quality gates | Checkstyle, SpotBugs, JUnit 5 |

## Prerequisites

- **JDK 17**
- **Docker & Docker Compose** (for local PostgreSQL)
- **PostgreSQL tables** — `call_detail_records` and `cdr_logs` must exist before startup (`spring.jpa.hibernate.ddl-auto: validate`)

## Quick start

### 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

This starts a PostgreSQL 15 instance with:

| Setting | Value |
|---------|-------|
| Database | `ussd` |
| User / password | `postgres` / `postgres` |
| Port | `5432` |
| Data volume | `file-loader-postgres-data` (persists across restarts) |

To apply DDL on first boot, place SQL scripts in `docker/postgres/init/` and uncomment the init volume in `docker-compose.yml`.

### 2. Configure input directories

```bash
mkdir -p ./ussd-inputs/archive

export FILE_LOADER_INPUT_DIR=./ussd-inputs
export FILE_LOADER_ARCHIVE_DIR=./ussd-inputs/archive
```

If these variables are not set, the service defaults to `./data/input` and `./data/input/archive`.

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Or build and run the JAR:

```bash
./mvnw package -DskipTests
java -jar target/file-loader-service-0.0.1-SNAPSHOT.jar
```

### 4. Drop a CDR file

Place a pipe-delimited file in the input directory. Within one polling cycle (up to 60 seconds), the service ingests it and moves it to the archive folder.

## Configuration

All settings live in `src/main/resources/application.yml`. Override via environment variables or a local profile file (`application-local.yml` is gitignored).

| Property | Environment variable | Default | Description |
|----------|---------------------|---------|-------------|
| `spring.datasource.url` | — | `jdbc:postgresql://localhost:5432/ussd` | PostgreSQL JDBC URL |
| `spring.datasource.username` | `DB_USERNAME` | `postgres` | Database user |
| `spring.datasource.password` | `DB_PASSWORD` | `postgres` | Database password |
| `file-loader.input-directory` | `FILE_LOADER_INPUT_DIR` | `./data/input` | Directory to poll for new files |
| `file-loader.archive-directory` | `FILE_LOADER_ARCHIVE_DIR` | `./data/input/archive` | Destination for processed files |
| `file-loader.batch-size` | — | `500` | Records per JDBC batch flush |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | — | `500` | Hibernate batch insert size |
| `spring.jpa.properties.hibernate.order_inserts` | — | `true` | Groups inserts for batching |

### Local overrides

Create `src/main/resources/application-local.yml` (not committed) for machine-specific settings:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ussd
    username: postgres
    password: postgres

file-loader:
  input-directory: ./ussd-inputs
  archive-directory: ./ussd-inputs/archive
```

Run with:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## CDR file format

Files are plain text, one record per line, fields separated by `|` (pipe).

- Splitting uses `line.split("\\|", -1)` so missing trailing fields are preserved as empty strings.
- Each line must contain **33 columns** in the order below.
- **Column 1** (`RECORD_DATE`): `yyyy-MM-dd HH:mm:ss,SSS` (comma before milliseconds)
- **Column 28** (`TSTAMP`): `yyyy-MM-dd HH:mm:ss.SSS` (dot before milliseconds)

| # | Column | Type | Required |
|---|--------|------|----------|
| 1 | RECORD_DATE | TIMESTAMP | Yes |
| 2–6 | L_SPC, L_SSN, L_RI, L_GT_I, L_GT_DIGITS | INT / VARCHAR(18) | No |
| 7–11 | R_SPC, R_SSN, R_RI, R_GT_I, R_GT_DIGITS | INT / VARCHAR(18) | No |
| 12 | SERVICE_CODE | VARCHAR(50) | No |
| 13–15 | OR_NATURE, OR_PLAN, OR_DIGITS | INT / VARCHAR(18) | No |
| 16–18 | DE_NATURE, DE_PLAN, DE_DIGITS | INT / VARCHAR(18) | No |
| 19–21 | ISDN_NATURE, ISDN_PLAN, MSISDN | INT / VARCHAR(18) | No |
| 22–24 | VLR_NATURE, VLR_PLAN, VLR_DIGITS | INT / VARCHAR(18) | No |
| 25 | IMSI | VARCHAR(100) | No |
| 26 | STATUS | VARCHAR(30) | Yes |
| 27 | TYPE | VARCHAR(30) | Yes |
| 28 | TSTAMP | TIMESTAMP | Yes |
| 29–31 | LOCAL_DIALOG_ID, REMOTE_DIALOG_ID, DIALOG_DURATION | BIGINT | No |
| 32 | USSD_STRING | VARCHAR(255) | No |
| 33 | ID | VARCHAR(150) | Yes (primary key) |

**Example line** (fields abbreviated):

```
2026-06-17 10:15:30,123|1|2|3|||||...|ACTIVE|USSD|2026-06-17 10:15:31.456||||*123#|cdr-id-0001
```

Malformed lines increment `cdr_logs.failed_count` and are logged; the rest of the file continues processing.

## Database tables

### `call_detail_records`

Stores ingested CDR rows. Primary key: `ID` (VARCHAR 150).

### `cdr_logs` (control / audit)

Tracks each file processing run:

| Column | Description |
|--------|-------------|
| `id` | Auto-generated BIGINT primary key |
| `file_name` | Source file name |
| `upload_start_time` | Processing start timestamp |
| `upload_end_time` | Processing end timestamp |
| `success_count` | Successfully parsed and persisted rows |
| `failed_count` | Rows that failed parsing |

## Project structure

```
src/main/java/com/paicore/file_loader_service/
├── FileLoaderServiceApplication.java   # @EnableScheduling entry point
├── config/                             # @ConfigurationProperties binding
├── entity/                             # JPA entities (CDR + audit log)
├── parser/                             # Pipe-delimited line parser
├── repository/                         # Spring Data JPA repositories
└── service/
    ├── CdrIngestionService.java        # Batch ingestion + audit logging
    └── FileWatcherService.java         # Scheduled directory polling

checkstyle/                             # Checkstyle rules
.github/workflows/ci-cd.yml             # GitHub Actions pipeline
docker-compose.yml                      # Local PostgreSQL stack
```

## Development

### Build

```bash
./mvnw clean package
```

### Run tests

```bash
./mvnw test
```

Tests use an in-memory H2 database configured in `src/test/resources/application.yml` (PostgreSQL compatibility mode).

### Quality gates

```bash
./mvnw checkstyle:check    # Code style
./mvnw spotbugs:check      # Static analysis
```

### Full CI pipeline locally

```bash
./mvnw -B checkstyle:check
./mvnw -B test
./mvnw -B spotbugs:check
./mvnw -B package -DskipTests
```

## CI/CD

GitHub Actions (`.github/workflows/ci-cd.yml`) runs on pushes to `main` and `feature/**`, and on pull requests to `main`:

1. Checkstyle
2. Unit tests
3. SpotBugs
4. Maven package

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Startup fails with schema validation error | Tables missing in `ussd` DB | Apply DDL; ensure `ddl-auto: validate` matches your schema |
| Files not picked up | Wrong input path or polling delay | Verify `FILE_LOADER_INPUT_DIR`; wait up to 60 s |
| All rows in `failed_count` | Wrong delimiter or date format | Confirm pipe delimiter and timestamp patterns |
| Duplicate key errors | Re-processing same `ID` | Ensure files are archived after success; clear duplicates in DB |
| Cannot connect to PostgreSQL | Docker not running | `docker compose up -d postgres` and check port `5432` |

## License

Internal PAiCore USSD infrastructure component. License terms per organization policy.
