-- X1 authorization extension: DB2 table CARDDEMO.AUTHFRDS (ddl/AUTHFRDS.ddl, XAUTHFRD.ddl).
CREATE TABLE auth_fraud (
    card_num               VARCHAR(16)   NOT NULL,
    auth_ts                TIMESTAMP     NOT NULL,
    auth_type              VARCHAR(4),
    card_expiry_date       VARCHAR(4),
    message_type           VARCHAR(6),
    message_source         VARCHAR(6),
    auth_id_code           VARCHAR(6),
    auth_resp_code         VARCHAR(2),
    auth_resp_reason       VARCHAR(4),
    processing_code        VARCHAR(6),
    transaction_amt        NUMERIC(12,2),
    approved_amt           NUMERIC(12,2),
    merchant_category_code VARCHAR(4),
    acqr_country_code      VARCHAR(3),
    pos_entry_mode         SMALLINT,
    merchant_id            VARCHAR(15),
    merchant_name          VARCHAR(22),
    merchant_city          VARCHAR(13),
    merchant_state         VARCHAR(2),
    merchant_zip           VARCHAR(9),
    transaction_id         VARCHAR(15),
    match_status           VARCHAR(1),
    auth_fraud             VARCHAR(1),
    fraud_rpt_date         DATE,
    acct_id                NUMERIC(11),
    cust_id                NUMERIC(9),
    CONSTRAINT pk_auth_fraud PRIMARY KEY (card_num, auth_ts)
);

-- XAUTHFRD: CARD_NUM ASC, AUTH_TS DESC.
CREATE INDEX xauthfrd ON auth_fraud (card_num ASC, auth_ts DESC);
