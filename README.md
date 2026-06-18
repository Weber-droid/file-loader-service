# USSD File Loader Service

**Application 1** in the PAiCore USSD infrastructure. A Spring Boot service that watches a folder for pipe-delimited USSD Call Detail Record (CDR) files, ingests them in batch into PostgreSQL, and archives processed files.

## What it does

Every **60 seconds**, the service polls `./ussd-inputs` for new files. For each file:

1. Creates an audit row in `cdr_logs` (start time).
2. Reads the file line-by-line with a `BufferedReader` (low memory).
3. Parses each line into `call_detail_records` and saves in batches of **500**.
4. Counts malformed lines without stopping the file.
5. Updates `cdr_logs` with end time, success count, and failed count.
6. Moves the file to `./ussd-inputs/archive`.

```
  ussd-inputs/          every 60s       FileWatcherService
  (*.cdr files)  ─────────────────────►  CdrIngestionService
                                              │
                         ┌────────────────────┼────────────────────┐
                         ▼                    ▼                    ▼
                 call_detail_records      cdr_logs            archive/
```

## Tech stack

| Component | Choice |
|-----------|--------|
| Java | 17 |
| Spring Boot | 4.1.0 |
| Spring Data JPA | Hibernate batch inserts |
| Database | PostgreSQL 15 (`ussd`) |
| Build | Maven (wrapper included) |
| Quality gates | Checkstyle, SpotBugs, JUnit 5 |

## Prerequisites

- JDK 17
- Docker & Docker Compose
- Port **5434** free on your machine (avoids clashes with other local Postgres instances on 5432/5433)

## Quick start

### 1. Start PostgreSQL (creates DB + tables automatically)

```bash
docker compose up -d
```

On **first start**, Docker runs `scripts/docker-init/01-schema.sql` and creates:

- Database: `ussd`
- Tables: `call_detail_records`, `cdr_logs`

| Setting | Value |
|---------|-------|
| Host | `127.0.0.1` |
| Port | `5434` |
| Database | `ussd` |
| User | `postgres` |
| Password | `fileloader` |

To reset the database completely:

```bash
docker compose down -v
docker compose up -d
```

### 2. Prepare input folders

```bash
mkdir -p ./ussd-inputs/archive
```

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

Place a pipe-delimited file in `./ussd-inputs/`. Within one polling cycle (up to 60 seconds), the service ingests it and archives it.

**Example** (real USSD event line):

```
2023-08-18 10:00:00,113|15845|15|0|4|573103154359||6|0|4|573103602000|*611#|1|1|573104438064|1|6|732101643243482|1|1|573164454442|||||SUCCESS|PULL|2023-08-18 10:00:00.113|5948688|187195827|33354|1,1,2,1,3|fc352a22-61f4-43fe-b762-589ebcd0068c
```

### 5. Verify ingestion

```bash
docker exec -it file-loader-postgres psql -U postgres -d ussd -c \
  "SELECT file_name, success_count, failed_count FROM cdr_logs ORDER BY id DESC LIMIT 1;"

docker exec -it file-loader-postgres psql -U postgres -d ussd -c \
  "SELECT \"ID\", \"MSISDN\", \"STATUS\", \"SERVICE_CODE\" FROM call_detail_records LIMIT 10;"
```

## Configuration

Settings are in `src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://127.0.0.1:5434/ussd` | PostgreSQL JDBC URL |
| `spring.datasource.username` | `postgres` | Database user |
| `spring.datasource.password` | `fileloader` | Database password |
| `file-loader.input-directory` | `./ussd-inputs` | Folder to poll |
| `file-loader.archive-directory` | `./ussd-inputs/archive` | Processed files destination |
| `file-loader.batch-size` | `500` | Records per JDBC batch flush |

Optional reference copy for local overrides: `.env.example` (copy to `.env` — gitignored).

> **Note:** If you previously ran `export DB_PASSWORD=...` in your shell, open a fresh terminal or run `unset DB_PASSWORD SPRING_DATASOURCE_PASSWORD` before starting the app.

## CDR file format

Plain text, one record per line, fields separated by `|` (pipe).

- Splitting uses `line.split("\\|", -1)` so empty/missing trailing fields are preserved.
- Each line must have **33 columns**.
- **Column 1** (`RECORD_DATE`): `yyyy-MM-dd HH:mm:ss,SSS` (comma before milliseconds)
- **Column 28** (`TSTAMP`): `yyyy-MM-dd HH:mm:ss.SSS` (dot before milliseconds)

| # | Column | Required |
|---|--------|----------|
| 1 | RECORD_DATE | Yes |
| 2–6 | L_SPC, L_SSN, L_RI, L_GT_I, L_GT_DIGITS | No |
| 7–11 | R_SPC, R_SSN, R_RI, R_GT_I, R_GT_DIGITS | No |
| 12 | SERVICE_CODE | No |
| 13–15 | OR_NATURE, OR_PLAN, OR_DIGITS | No |
| 16–18 | DE_NATURE, DE_PLAN, DE_DIGITS | No |
| 19–21 | ISDN_NATURE, ISDN_PLAN, MSISDN | No |
| 22–24 | VLR_NATURE, VLR_PLAN, VLR_DIGITS | No |
| 25 | IMSI | No |
| 26 | STATUS | Yes |
| 27 | TYPE | Yes |
| 28 | TSTAMP | Yes |
| 29–31 | LOCAL_DIALOG_ID, REMOTE_DIALOG_ID, DIALOG_DURATION | No |
| 32 | USSD_STRING | No |
| 33 | ID | Yes (primary key) |

Malformed lines increment `cdr_logs.failed_count` and are logged; the rest of the file continues processing.

## Database schema

DDL lives in `scripts/docker-init/01-schema.sql` and is applied automatically on first Docker start.

### `call_detail_records`

Stores ingested CDR rows. Primary key: `"ID"` (VARCHAR 150). Column names are uppercase quoted identifiers in PostgreSQL.

### `cdr_logs` (control / audit)

| Column | Description |
|--------|-------------|
| `id` | Auto-generated primary key |
| `file_name` | Source file name |
| `upload_start_time` | Processing start |
| `upload_end_time` | Processing end |
| `success_count` | Rows parsed and saved |
| `failed_count` | Rows that failed parsing |

## Project structure

```
src/main/java/com/paicore/file_loader_service/
├── FileLoaderServiceApplication.java   # @EnableScheduling
├── config/                             # FileLoaderProperties
├── entity/                             # CallDetailRecord, CdrLog
├── parser/                             # CdrLineParser
├── repository/                         # Spring Data JPA
└── service/
    ├── CdrIngestionService.java        # Batch ingestion + audit
    └── FileWatcherService.java         # Scheduled folder polling

scripts/
├── docker-init/01-schema.sql           # Postgres DDL (auto-run on first start)
└── seed-test-data.sql                  # Optional seed data (empty by default)

ussd-inputs/                            # Drop CDR files here (gitignored)
ussd-inputs/archive/                    # Processed files (gitignored)

checkstyle/                             # Checkstyle rules
.github/workflows/ci-cd.yml             # CI pipeline
docker-compose.yml                      # Local PostgreSQL
```

## Development

### Build

```bash
./mvnw clean package
```

### Tests

```bash
./mvnw test
```

Tests use in-memory H2 (PostgreSQL compatibility mode).

### Quality gates

```bash
./mvnw checkstyle:check
./mvnw spotbugs:check
```

### Full CI pipeline locally

```bash
./mvnw -B checkstyle:check
./mvnw -B test
./mvnw -B spotbugs:check
./mvnw -B package -DskipTests
```

## CI/CD

GitHub Actions (`.github/workflows/ci-cd.yml`) runs on pushes to `main` and `feature/**`, and on PRs to `main`:

1. Checkstyle
2. Unit tests
3. SpotBugs
4. Maven package

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `password authentication failed` | Stale shell `DB_PASSWORD` or wrong volume | Fresh terminal; `docker compose down -v && docker compose up -d` |
| Port already in use | Another Postgres on 5434 | Stop other containers or change port in `docker-compose.yml` + `application.yml` |
| Schema validation error | Tables missing | Reset volume: `docker compose down -v && docker compose up -d` |
| `column cdr1_0.id does not exist` | Uppercase quoted DDL vs Hibernate | `globally_quoted_identifiers: true` is set in `application.yml` |
| Files not picked up | Wrong path or polling delay | Confirm file is in `./ussd-inputs/`; wait up to 60 s |
| All rows in `failed_count` | Wrong delimiter or date format | Confirm pipe delimiter and timestamp patterns |
| Duplicate key errors | Same `ID` ingested twice | Use a new file or `TRUNCATE call_detail_records;` |

## License

Internal PAiCore USSD infrastructure component. License terms per organization policy.
