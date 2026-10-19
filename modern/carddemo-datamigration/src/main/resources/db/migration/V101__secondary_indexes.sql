-- 1B final secondary indexes on the logical FK / alternate-key columns not covered by V1.
-- Pure access paths: no constraint, safe for every module that loads data in any order.
CREATE INDEX IF NOT EXISTS xref_cust_id ON card_xref (xref_cust_id);
CREATE INDEX IF NOT EXISTS acct_group_id ON account (acct_group_id);
CREATE INDEX IF NOT EXISTS tran_card_num ON transaction (tran_card_num);
CREATE INDEX IF NOT EXISTS dalytran_card_num ON daily_transaction (dalytran_card_num);
CREATE INDEX IF NOT EXISTS usrsec_esds_usr_id ON usrsec_esds (sec_usr_id);
CREATE INDEX IF NOT EXISTS usrsec_rrds_usr_id ON usrsec_rrds (sec_usr_id);
