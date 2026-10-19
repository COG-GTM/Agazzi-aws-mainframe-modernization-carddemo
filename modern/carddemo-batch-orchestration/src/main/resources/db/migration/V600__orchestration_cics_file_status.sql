-- b5-orchestration: shared CEMT file state (CLOSEFIL/OPENFIL -> MaintenanceModePort).
CREATE TABLE cics_file_status (
    file_name   VARCHAR(8)  NOT NULL PRIMARY KEY,
    status      VARCHAR(6)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    changed_at  TIMESTAMP   NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0
);
