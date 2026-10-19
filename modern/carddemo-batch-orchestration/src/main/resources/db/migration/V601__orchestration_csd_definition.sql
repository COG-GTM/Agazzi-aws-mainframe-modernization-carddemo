-- b5-orchestration: CICS CSD catalog loaded by CBADMCDJ (DFHCSDUP DEFINE statements).
CREATE TABLE cics_csd_definition (
    resource_type  VARCHAR(12)   NOT NULL,
    resource_name  VARCHAR(8)    NOT NULL,
    csd_group      VARCHAR(8)    NOT NULL,
    attributes     VARCHAR(2000) NOT NULL,
    defined_at     TIMESTAMP     NOT NULL,
    PRIMARY KEY (resource_type, resource_name, csd_group)
);
