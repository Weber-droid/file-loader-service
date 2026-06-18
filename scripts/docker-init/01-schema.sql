-- Schema for USSD File Loader Service (runs on first container start)

CREATE TABLE IF NOT EXISTS call_detail_records (
    "RECORD_DATE"       TIMESTAMP NOT NULL,
    "L_SPC"             INT,
    "L_SSN"             INT,
    "L_RI"              INT,
    "L_GT_I"            INT,
    "L_GT_DIGITS"       VARCHAR(18),
    "R_SPC"             INT,
    "R_SSN"             INT,
    "R_RI"              INT,
    "R_GT_I"            INT,
    "R_GT_DIGITS"       VARCHAR(18),
    "SERVICE_CODE"      VARCHAR(50),
    "OR_NATURE"         INT,
    "OR_PLAN"           INT,
    "OR_DIGITS"         VARCHAR(18),
    "DE_NATURE"         INT,
    "DE_PLAN"           INT,
    "DE_DIGITS"         VARCHAR(18),
    "ISDN_NATURE"       INT,
    "ISDN_PLAN"         INT,
    "MSISDN"            VARCHAR(18),
    "VLR_NATURE"        INT,
    "VLR_PLAN"          INT,
    "VLR_DIGITS"        VARCHAR(18),
    "IMSI"              VARCHAR(100),
    "STATUS"            VARCHAR(30) NOT NULL,
    "TYPE"              VARCHAR(30) NOT NULL,
    "TSTAMP"            TIMESTAMP NOT NULL,
    "LOCAL_DIALOG_ID"   BIGINT,
    "REMOTE_DIALOG_ID"  BIGINT,
    "DIALOG_DURATION"   BIGINT,
    "USSD_STRING"       VARCHAR(255),
    "ID"                VARCHAR(150) PRIMARY KEY NOT NULL
);

CREATE TABLE IF NOT EXISTS cdr_logs (
    id                  BIGSERIAL PRIMARY KEY,
    file_name           VARCHAR,
    upload_start_time   TIMESTAMP,
    upload_end_time     TIMESTAMP,
    success_count       INT,
    failed_count        INT
);
