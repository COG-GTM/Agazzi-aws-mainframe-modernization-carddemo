-- O4 (COTRN02C / COBIL00C): "last TRAN-ID + 1" allocation. The single row is locked with
-- SELECT ... FOR UPDATE before reading MAX(tran_id), so concurrent adds are serialized exactly like
-- the CICS ENQ held by STARTBR/READPREV/WRITE on the TRANSACT KSDS (no gaps, no duplicates).
CREATE TABLE o4_tran_id_lock (
    lock_id INTEGER NOT NULL,
    CONSTRAINT pk_o4_tran_id_lock PRIMARY KEY (lock_id)
);
INSERT INTO o4_tran_id_lock (lock_id) VALUES (1);
