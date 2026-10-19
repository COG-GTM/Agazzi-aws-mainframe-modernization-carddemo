-- 1B cutover constraints, applied by `migrate-data --job=applyConstraints` after a clean
-- vsamToRelational (history table dm_constraints_history). Only the logical FKs with
-- severity ERROR in the validator become real constraints; the ones the batch programs
-- tolerate (CBTRN02C rejects, CBACT04C DEFAULT group) stay logical.
ALTER TABLE transaction_category
    ADD CONSTRAINT fk_trancatg_trantype FOREIGN KEY (tran_type_cd)
        REFERENCES transaction_type (tran_type);
ALTER TABLE card
    ADD CONSTRAINT fk_card_account FOREIGN KEY (card_acct_id) REFERENCES account (acct_id);
ALTER TABLE card_xref
    ADD CONSTRAINT fk_xref_card FOREIGN KEY (xref_card_num) REFERENCES card (card_num);
ALTER TABLE card_xref
    ADD CONSTRAINT fk_xref_customer FOREIGN KEY (xref_cust_id) REFERENCES customer (cust_id);
ALTER TABLE card_xref
    ADD CONSTRAINT fk_xref_account FOREIGN KEY (xref_acct_id) REFERENCES account (acct_id);
ALTER TABLE tran_cat_balance
    ADD CONSTRAINT fk_tcatbal_account FOREIGN KEY (trancat_acct_id) REFERENCES account (acct_id);

ALTER TABLE account
    ADD CONSTRAINT ck_account_status CHECK (acct_active_status IN ('Y', 'N'));
ALTER TABLE card
    ADD CONSTRAINT ck_card_status CHECK (card_active_status IN ('Y', 'N'));
ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_type CHECK (sec_usr_type IN ('A', 'U'));
