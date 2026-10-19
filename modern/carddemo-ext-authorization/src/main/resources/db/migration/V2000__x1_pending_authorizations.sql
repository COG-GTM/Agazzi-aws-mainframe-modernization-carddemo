-- X1 authorization extension: IMS HIDAM DBPAUTP0 (PSBPAUTB) as relational tables.
-- Root segment PAUTSUM0 (CIPAUSMY, 100 bytes) keyed by ACCNTID.
CREATE TABLE pending_auth_summary (
    acct_id              BIGINT        NOT NULL,
    cust_id              INTEGER       NOT NULL,
    auth_status          VARCHAR(1)    NOT NULL,
    account_status       VARCHAR(10)   NOT NULL,
    credit_limit         NUMERIC(11,2) NOT NULL,
    cash_limit           NUMERIC(11,2) NOT NULL,
    credit_balance       NUMERIC(11,2) NOT NULL,
    cash_balance         NUMERIC(11,2) NOT NULL,
    approved_auth_cnt    SMALLINT      NOT NULL,
    declined_auth_cnt    SMALLINT      NOT NULL,
    approved_auth_amt    NUMERIC(11,2) NOT NULL,
    declined_auth_amt    NUMERIC(11,2) NOT NULL,
    filler               VARCHAR(68)   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_pending_auth_summary PRIMARY KEY (acct_id)
);

-- Child segment PAUTDTL1 (CIPAUDTY, 200 bytes) keyed by PAUT9CTS = inverted date/time
-- (99999 - YYDDD, 999999999 - HHMMSSmmm): ascending key order = newest first.
CREATE TABLE pending_auth_detail (
    acct_id                BIGINT        NOT NULL,
    auth_date_9c           INTEGER       NOT NULL,
    auth_time_9c           BIGINT        NOT NULL,
    auth_orig_date         VARCHAR(6)    NOT NULL,
    auth_orig_time         VARCHAR(6)    NOT NULL,
    card_num               VARCHAR(16)   NOT NULL,
    auth_type              VARCHAR(4)    NOT NULL,
    card_expiry_date       VARCHAR(4)    NOT NULL,
    message_type           VARCHAR(6)    NOT NULL,
    message_source         VARCHAR(6)    NOT NULL,
    auth_id_code           VARCHAR(6)    NOT NULL,
    auth_resp_code         VARCHAR(2)    NOT NULL,
    auth_resp_reason       VARCHAR(4)    NOT NULL,
    processing_code        INTEGER       NOT NULL,
    transaction_amt        NUMERIC(12,2) NOT NULL,
    approved_amt           NUMERIC(12,2) NOT NULL,
    merchant_category_code VARCHAR(4)    NOT NULL,
    acqr_country_code      VARCHAR(3)    NOT NULL,
    pos_entry_mode         SMALLINT      NOT NULL,
    merchant_id            VARCHAR(15)   NOT NULL,
    merchant_name          VARCHAR(22)   NOT NULL,
    merchant_city          VARCHAR(13)   NOT NULL,
    merchant_state         VARCHAR(2)    NOT NULL,
    merchant_zip           VARCHAR(9)    NOT NULL,
    transaction_id         VARCHAR(15)   NOT NULL,
    match_status           VARCHAR(1)    NOT NULL,
    auth_fraud             VARCHAR(1)    NOT NULL,
    fraud_rpt_date         VARCHAR(8)    NOT NULL,
    filler                 VARCHAR(34)   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_pending_auth_detail PRIMARY KEY (acct_id, auth_date_9c, auth_time_9c),
    CONSTRAINT fk_pending_auth_detail_summary FOREIGN KEY (acct_id)
        REFERENCES pending_auth_summary (acct_id)
);

-- Secondary access by card on the inverted timestamp key (newest first).
CREATE INDEX ix_pending_auth_detail_card_inv_ts
    ON pending_auth_detail (card_num, auth_date_9c, auth_time_9c);
