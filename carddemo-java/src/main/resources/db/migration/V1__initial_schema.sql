-- CardDemo Java Migration: Initial Schema
-- Migrated from COBOL/VSAM data structures (copybooks) to relational tables
-- Source copybooks: CVACT01Y, CVACT02Y, CVACT03Y, CVCUS01Y, CVTRA05Y, CVTRA06Y,
--                   CSUSR01Y, CVTRA01Y, CVTRA02Y, CVTRA03Y, CVTRA04Y

-- User Security (from CSUSR01Y - SEC-USER-DATA, RECLN 80)
CREATE TABLE users (
    user_id       VARCHAR(8)   NOT NULL,
    first_name    VARCHAR(20)  NOT NULL,
    last_name     VARCHAR(20)  NOT NULL,
    password      VARCHAR(255) NOT NULL,
    user_type     VARCHAR(1)   NOT NULL DEFAULT 'U',
    PRIMARY KEY (user_id),
    CONSTRAINT chk_user_type CHECK (user_type IN ('A', 'U'))
);

-- Customers (from CVCUS01Y - CUSTOMER-RECORD, RECLN 500)
CREATE TABLE customers (
    cust_id              BIGINT       NOT NULL,
    first_name           VARCHAR(25)  NOT NULL,
    middle_name          VARCHAR(25),
    last_name            VARCHAR(25)  NOT NULL,
    addr_line_1          VARCHAR(50),
    addr_line_2          VARCHAR(50),
    addr_line_3          VARCHAR(50),
    addr_state_cd        VARCHAR(2),
    addr_country_cd      VARCHAR(3),
    addr_zip             VARCHAR(10),
    phone_num_1          VARCHAR(15),
    phone_num_2          VARCHAR(15),
    ssn                  BIGINT,
    govt_issued_id       VARCHAR(20),
    dob                  DATE,
    eft_account_id       VARCHAR(10),
    pri_card_holder_ind  VARCHAR(1),
    fico_credit_score    INT,
    PRIMARY KEY (cust_id)
);

-- Accounts (from CVACT01Y - ACCOUNT-RECORD, RECLN 300)
CREATE TABLE accounts (
    acct_id              BIGINT         NOT NULL,
    active_status        VARCHAR(1)     NOT NULL DEFAULT 'Y',
    curr_bal             DECIMAL(12,2)  NOT NULL DEFAULT 0,
    credit_limit         DECIMAL(12,2)  NOT NULL DEFAULT 0,
    cash_credit_limit    DECIMAL(12,2)  NOT NULL DEFAULT 0,
    open_date            DATE,
    expiration_date      DATE,
    reissue_date         DATE,
    curr_cyc_credit      DECIMAL(12,2)  NOT NULL DEFAULT 0,
    curr_cyc_debit       DECIMAL(12,2)  NOT NULL DEFAULT 0,
    addr_zip             VARCHAR(10),
    group_id             VARCHAR(10),
    PRIMARY KEY (acct_id)
);

-- Cards (from CVACT02Y - CARD-RECORD, RECLN 150)
CREATE TABLE cards (
    card_num             VARCHAR(16)  NOT NULL,
    acct_id              BIGINT       NOT NULL,
    cvv_cd               INT          NOT NULL,
    embossed_name        VARCHAR(50),
    expiration_date      DATE,
    active_status        VARCHAR(1)   NOT NULL DEFAULT 'Y',
    PRIMARY KEY (card_num),
    CONSTRAINT fk_card_account FOREIGN KEY (acct_id) REFERENCES accounts(acct_id)
);

-- Account-Card-Customer Cross Reference (from CVACT03Y - CARD-XREF-RECORD, RECLN 50)
CREATE TABLE account_card_xref (
    card_num   VARCHAR(16) NOT NULL,
    cust_id    BIGINT      NOT NULL,
    acct_id    BIGINT      NOT NULL,
    PRIMARY KEY (card_num),
    CONSTRAINT fk_xref_card     FOREIGN KEY (card_num) REFERENCES cards(card_num),
    CONSTRAINT fk_xref_customer FOREIGN KEY (cust_id)  REFERENCES customers(cust_id),
    CONSTRAINT fk_xref_account  FOREIGN KEY (acct_id)  REFERENCES accounts(acct_id)
);

-- Transactions (from CVTRA05Y - TRAN-RECORD, RECLN 350)
CREATE TABLE transactions (
    tran_id           VARCHAR(32)    NOT NULL,
    tran_type_cd      VARCHAR(2),
    tran_cat_cd       INT,
    tran_source       VARCHAR(10),
    tran_desc         VARCHAR(100),
    tran_amt          DECIMAL(11,2),
    merchant_id       BIGINT,
    merchant_name     VARCHAR(50),
    merchant_city     VARCHAR(50),
    merchant_zip      VARCHAR(10),
    card_num          VARCHAR(16),
    orig_ts           TIMESTAMP,
    proc_ts           TIMESTAMP,
    PRIMARY KEY (tran_id)
);

-- Daily Transactions (from CVTRA06Y - DALYTRAN-RECORD, RECLN 350)
CREATE TABLE daily_transactions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    tran_id           VARCHAR(32)    NOT NULL,
    tran_type_cd      VARCHAR(2),
    tran_cat_cd       INT,
    tran_source       VARCHAR(10),
    tran_desc         VARCHAR(100),
    tran_amt          DECIMAL(11,2),
    merchant_id       BIGINT,
    merchant_name     VARCHAR(50),
    merchant_city     VARCHAR(50),
    merchant_zip      VARCHAR(10),
    card_num          VARCHAR(16),
    orig_ts           TIMESTAMP,
    proc_ts           TIMESTAMP,
    posted            BOOLEAN        NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
);

-- Transaction Types (from CVTRA03Y - TRAN-TYPE-RECORD, RECLN 60)
CREATE TABLE transaction_types (
    tran_type      VARCHAR(2)   NOT NULL,
    tran_type_desc VARCHAR(50)  NOT NULL,
    PRIMARY KEY (tran_type)
);

-- Transaction Categories (from CVTRA04Y - TRAN-CAT-RECORD, RECLN 60)
CREATE TABLE transaction_categories (
    tran_type_cd      VARCHAR(2)   NOT NULL,
    tran_cat_cd       INT          NOT NULL,
    tran_cat_type_desc VARCHAR(50) NOT NULL,
    PRIMARY KEY (tran_type_cd, tran_cat_cd)
);

-- Transaction Category Balance (from CVTRA01Y - TRAN-CAT-BAL-RECORD, RECLN 50)
CREATE TABLE transaction_category_balances (
    acct_id        BIGINT        NOT NULL,
    tran_type_cd   VARCHAR(2)    NOT NULL,
    tran_cat_cd    INT           NOT NULL,
    balance        DECIMAL(11,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (acct_id, tran_type_cd, tran_cat_cd),
    CONSTRAINT fk_tcb_account FOREIGN KEY (acct_id) REFERENCES accounts(acct_id)
);

-- Disclosure Groups (from CVTRA02Y - DIS-GROUP-RECORD, RECLN 50)
CREATE TABLE disclosure_groups (
    acct_group_id  VARCHAR(10)   NOT NULL,
    tran_type_cd   VARCHAR(2)    NOT NULL,
    tran_cat_cd    INT           NOT NULL,
    int_rate       DECIMAL(6,2)  NOT NULL DEFAULT 0,
    PRIMARY KEY (acct_group_id, tran_type_cd, tran_cat_cd)
);

-- Indexes for common query patterns
CREATE INDEX idx_cards_acct_id ON cards(acct_id);
CREATE INDEX idx_xref_cust_id ON account_card_xref(cust_id);
CREATE INDEX idx_xref_acct_id ON account_card_xref(acct_id);
CREATE INDEX idx_transactions_card_num ON transactions(card_num);
CREATE INDEX idx_transactions_orig_ts ON transactions(orig_ts);
CREATE INDEX idx_daily_transactions_posted ON daily_transactions(posted);
CREATE INDEX idx_daily_transactions_card_num ON daily_transactions(card_num);
