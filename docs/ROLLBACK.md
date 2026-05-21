# CardDemo Migration Rollback Procedures

## Overview

This document provides step-by-step rollback procedures for two CardDemo migrations:

1. **15-Digit Card Number Migration** - Rollback from 15-digit card numbers back to the original 16-digit format
2. **3/4-Digit CVV Migration** - Rollback from the expanded 4-digit CVV support back to the original 3-digit format

### Strategy

- Leverage existing GDG backup infrastructure for version control
- Use REPRO utilities for VSAM file restoration
- Utilize existing export/import programs (CBEXPORT/CBIMPORT)
- Follow existing JCL patterns from `TRANBKP.jcl` and `CARDFILE.jcl`
- Implement phased rollback with validation at each step

### Emergency Rollback JCL

| JCL | Purpose |
|-----|---------|
| `ROLLCARD.jcl` | Restore CARDDATA VSAM cluster from GDG backup |
| `ROLLXREF.jcl` | Restore CARDXREF VSAM cluster from GDG backup |
| `ROLLTRAN.jcl` | Restore TRANSACT VSAM cluster from GDG backup |
| `CBCVVRBK.jcl` | Run CVV data conversion utility (4-digit to 3-digit) |

---

## Rollback Procedure 1: 15-Digit Card Number Migration

### Phase 1: Pre-Rollback Preparation

1. **Take immediate backup** of current VSAM files using existing GDG pattern:
   - Backup `CARDDATA.VSAM.KSDS` to `CARDDATA.BKUP(+1)`
   - Backup `CARDXREF.VSAM.KSDS` to `CARDXREF.BKUP(+1)`
   - Backup `TRANSACT.VSAM.KSDS` to `TRANSACT.BKUP(+1)`

2. **Document current state**: record counts, file sizes, timestamps

3. **Close CICS files** using `CLOSEFIL.jcl` pattern:
   ```
   CEMT SET FIL(CARDDAT) CLO
   CEMT SET FIL(CARDAIX) CLO
   ```

4. **Notify users** of impending rollback

### Phase 2: Copybook Restoration

Restore the following copybooks to their original 16-digit card number format:

| Copybook | Field | Migrated Value | Restored Value | Notes |
|----------|-------|---------------|----------------|-------|
| `CVACT02Y.cpy` | CARD-NUM | PIC X(15) | PIC X(16) | Primary card record |
| `CVACT02Y.cpy` | FILLER | PIC X(60) | PIC X(59) | Adjust to maintain 150-byte record |
| `CVACT03Y.cpy` | XREF-CARD-NUM | PIC X(15) | PIC X(16) | Cross-reference record |
| `CVACT03Y.cpy` | FILLER | PIC X(15) | PIC X(14) | Adjust to maintain 50-byte record |
| `CVCRD01Y.cpy` | CC-CARD-NUM | PIC X(15) | PIC X(16) | Working storage |
| `CVCRD01Y.cpy` | CC-CARD-NUM-N | PIC 9(15) | PIC 9(16) | Numeric redefine |
| `CVTRA05Y.cpy` | TRAN-CARD-NUM | PIC X(15) | PIC X(16) | Transaction record |
| `COCOM01Y.cpy` | CDEMO-CARD-NUM | PIC 9(15) | PIC 9(16) | COMMAREA |
| `CVEXPORT.cpy` | EXP-TRAN-CARD-NUM | PIC X(15) | PIC X(16) | Export layout |
| `CVEXPORT.cpy` | EXP-XREF-CARD-NUM | PIC X(15) | PIC X(16) | Export XREF layout |
| `CVEXPORT.cpy` | EXP-CARD-NUM | PIC X(15) | PIC X(16) | Export card layout |

### Phase 3: Database Schema Restoration

1. **Restore DDL** (`app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl`):
   - Change `CARD_NUM` from `CHAR(15)` back to `CHAR(16)`

2. **Restore DCL** (`app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl`):
   - Change `CARD-NUM` from `PIC X(15)` back to `PIC X(16)` in both SQL DECLARE and COBOL DECLARATION sections

3. **Execute DB2 ALTER TABLE** to restore column length:
   ```sql
   ALTER TABLE CARDDEMO.AUTHFRDS ALTER COLUMN CARD_NUM SET DATA TYPE CHAR(16);
   ```

4. **Run data restoration scripts** to convert 15-digit numbers back to 16-digit format

### Phase 4: VSAM File Restoration

Execute rollback JCL to restore original VSAM structures:

1. **Card Data** - Submit `ROLLCARD.jcl`:
   - Backs up current migrated data to GDG audit trail
   - Closes CICS files
   - Deletes and redefines VSAM cluster with `KEYS(16 0)`
   - Restores data from pre-migration GDG backup `CARDDATA.BKUP(-1)`
   - Rebuilds alternate indexes
   - Reopens CICS files
   - Validates record counts

2. **Card Cross-Reference** - Submit `ROLLXREF.jcl`:
   - Backs up current migrated data to GDG audit trail
   - Deletes and redefines VSAM cluster with `KEYS(16 0)`
   - Restores data from pre-migration GDG backup `CARDXREF.BKUP(-1)`
   - Rebuilds alternate indexes with original key offsets `KEYS(11,25)`
   - Validates record counts

3. **Transaction Data** - Submit `ROLLTRAN.jcl`:
   - Backs up current migrated data to GDG audit trail
   - Closes CICS files (`TRANSACT`, `CXACAIX`)
   - Deletes and redefines VSAM cluster with `KEYS(16 0)`
   - Restores data from pre-migration GDG backup `TRANSACT.BKUP(-1)`
   - Rebuilds alternate index on processed timestamp `KEYS(26 304)`
   - Defines path and builds alternate index
   - Reopens CICS files
   - Validates record counts

### Phase 5: Program Logic Restoration

1. **Restore validation messages** in COBOL programs:

   | Program | Migrated Message | Restored Message |
   |---------|-----------------|------------------|
   | `COCRDLIC.cbl` | `'CARD ID FILTER,IF SUPPLIED MUST BE A 15 DIGIT NUMBER'` | `'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER'` |
   | `COCRDSLC.cbl` | `'Card number if supplied must be a 15 digit number'` | `'Card number if supplied must be a 16 digit number'` |
   | `COCRDSLC.cbl` | `'CARD ID FILTER,IF SUPPLIED MUST BE A 15 DIGIT NUMBER'` | `'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER'` |
   | `COCRDUPC.cbl` | `'Card number if supplied must be a 15 digit number'` | `'Card number if supplied must be a 16 digit number'` |
   | `COCRDUPC.cbl` | `'CARD ID FILTER,IF SUPPLIED MUST BE A 15 DIGIT NUMBER'` | `'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER'` |

2. **Restore validation logic** to enforce 16-digit requirement

3. **Recompile** all affected COBOL programs

4. **Update load libraries** with restored modules

### Phase 6: Data Validation

1. Run record count comparisons against pre-migration backups
2. Validate data integrity using CBEXPORT to compare before/after states
3. Test card lookup operations with 16-digit numbers
4. Verify transaction processing with restored card numbers

---

## Rollback Procedure 2: 3/4-Digit CVV Migration

### Phase 1: Pre-Rollback Preparation

1. **Backup current CARDDATA.VSAM.KSDS** using GDG pattern
2. **Export current card data** using `CBEXPORT.jcl` for comparison
3. **Close CICS files** using `CLOSEFIL.jcl`
4. **Document current CVV distribution** (3-digit vs 4-digit counts)

### Phase 2: Copybook Restoration

Restore the following copybooks to their original 3-digit CVV format:

| Copybook | Field | Migrated Value | Restored Value | Notes |
|----------|-------|---------------|----------------|-------|
| `CVACT02Y.cpy` | CARD-CVV-CD | PIC X(04) | PIC 9(03) | Card record layout |
| `CVACT02Y.cpy` | FILLER | PIC X(58) | PIC X(59) | Adjust to maintain 150 bytes |
| `CVEXPORT.cpy` | EXP-CARD-CVV-CD | PIC 9(04) COMP | PIC 9(03) COMP | Export layout |
| `CVEXPORT.cpy` | FILLER (card) | PIC X(372) | PIC X(373) | Adjust export record |

### Phase 3: Program Working Storage Restoration

Restore working storage fields in COBOL programs:

| Program | Field | Migrated Value | Restored Value |
|---------|-------|---------------|----------------|
| `COCRDUPC.cbl` | CARD-CVV-CD-X | PIC X(04) | PIC X(03) |
| `COCRDUPC.cbl` | CARD-CVV-CD-N | PIC 9(04) | PIC 9(03) |
| `COCRDUPC.cbl` | CCUP-OLD-CVV-CD | PIC X(4) | PIC X(3) |
| `COCRDUPC.cbl` | CCUP-NEW-CVV-CD | PIC X(4) | PIC X(3) |
| `COCRDUPC.cbl` | CARD-UPDATE-CVV-CD | PIC 9(04) | PIC 9(03) |
| `COCRDUPC.cbl` | FILLER (update rec) | PIC X(58) | PIC X(59) |
| `COCRDSLC.cbl` | CARD-CVV-CD-X | PIC X(04) | PIC X(03) |
| `COCRDSLC.cbl` | CARD-CVV-CD-N | PIC 9(04) | PIC 9(03) |
| `COCRDLIC.cbl` | CARD-CVV-CD-X | PIC X(04) | PIC X(03) |
| `COCRDLIC.cbl` | CARD-CVV-CD-N | PIC 9(04) | PIC 9(03) |

### Phase 4: Validation Logic Restoration

1. **Remove CVV length validation logic** that accepts both 3 and 4 digits
   - Remove the `1270-EDIT-CVV` paragraph and its EXIT from `COCRDUPC.cbl`
   - Remove `WS-EDIT-CVV-FLAG` and related 88-level items
   - Remove `CARD-CVV-LENGTH-NOT-VALID` error message
   - Remove `PERFORM 1270-EDIT-CVV THRU 1270-EDIT-CVV-EXIT`

2. **Restore strict 3-digit validation** in all card processing programs

3. **Update error messages** from `'CVV must be a 3 or 4 digit number'` back to
   specific 3-digit validation

4. **Remove any conditional logic** for CVV length handling

### Phase 5: Data Conversion

Use the **CBCVVRBK** conversion utility to truncate 4-digit CVVs back to 3 digits:

1. **Submit `CBCVVRBK.jcl`**:
   - Step 1: Unloads migrated VSAM to sequential file
   - Step 2: Runs CBCVVRBK program to convert 4-digit CVVs to 3-digit
   - Removes leading digit from 4-digit CVVs
   - Validates resulting 3-digit CVVs
   - Logs warnings for records with genuine 4-digit CVVs (non-zero leading digit)

2. **Review program output** for records with non-zero leading digits:
   - These represent genuine 4-digit CVVs issued after migration
   - Decide on case-by-case handling (manual re-issuance may be needed)

3. **Execute in test environment first** before production

### Phase 6: VSAM File Restoration

1. Submit `ROLLCARD.jcl` to restore original VSAM record structure
2. Use REPRO to load the rolled-back data from `CBCVVRBK` output:
   ```
   REPRO INDATASET(AWS.M2.CARDDEMO.CARDDATA.ROLLBACK.SEQ) -
         OUTDATASET(AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS)
   ```
3. Rebuild alternate indexes
4. Validate record counts and data integrity

### Phase 7: Program Recompilation

1. Recompile all COBOL programs referencing updated copybooks
2. Update load libraries
3. Perform unit testing for CVV validation
4. Test card creation and update operations

---

## Common Rollback Procedures

### Emergency Rollback JCL

Emergency rollback JCL files are provided based on the existing `TRANBKP.jcl` pattern:

| JCL File | Target VSAM | GDG Backup Pattern |
|----------|------------|-------------------|
| `ROLLCARD.jcl` | `CARDDATA.VSAM.KSDS` | `CARDDATA.BKUP(-1)` |
| `ROLLXREF.jcl` | `CARDXREF.VSAM.KSDS` | `CARDXREF.BKUP(-1)` |
| `ROLLTRAN.jcl` | `TRANSACT.VSAM.KSDS` | `TRANSACT.BKUP(-1)` |

Key features:
- GDG versioning to restore previous generation
- Conditional execution (`COND=(4,LT)`) to prevent accidental data loss
- Audit trail backup before rollback (`BKUP(+1)`)
- CICS file close/open management
- Alternate index rebuilding
- Record count validation via LISTCAT

### Validation and Testing

1. Use existing `CBEXPORT`/`CBIMPORT` programs for data validation
2. Run comparison reports between current and backup data:
   ```
   REPRO INDATASET(AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS) -
         OUTDATASET(AWS.M2.CARDDEMO.CARDDATA.VERIFY.SEQ)
   ```
3. Execute smoke tests for critical business functions
4. Perform end-to-end testing of card and transaction processing
5. Validate CICS file operations using CEMT commands:
   ```
   CEMT INQ FIL(CARDDAT)
   CEMT INQ FIL(CARDAIX)
   ```

### Rollback Verification Checklist

- [ ] Compare record counts before and after rollback
- [ ] Validate data integrity using checksums
- [ ] Test all card-related transactions
- [ ] Verify batch processing jobs complete successfully
- [ ] Confirm reporting functions return expected results
- [ ] Document rollback completion and lessons learned

### Post-Rollback Activities

1. Update operational documentation
2. Communicate rollback status to stakeholders
3. Analyze root cause of migration failure
4. Implement additional safeguards for future migrations
5. Schedule review of migration approach
6. Update rollback procedures based on lessons learned

---

## File Inventory

### New Files Added for Rollback Support

| File | Type | Description |
|------|------|-------------|
| `app/jcl/ROLLCARD.jcl` | JCL | Emergency rollback for CARDDATA VSAM |
| `app/jcl/ROLLXREF.jcl` | JCL | Emergency rollback for CARDXREF VSAM |
| `app/jcl/ROLLTRAN.jcl` | JCL | Emergency rollback for TRANSACT VSAM |
| `app/cbl/CBCVVRBK.cbl` | COBOL | CVV conversion utility (4-digit to 3-digit) |
| `app/jcl/CBCVVRBK.jcl` | JCL | JCL to execute CVV rollback conversion |
| `docs/ROLLBACK.md` | Documentation | This rollback procedure document |

### Files to Restore During Rollback

| File | Changes Required |
|------|-----------------|
| `app/cpy/CVACT02Y.cpy` | Card number PIC X(16), CVV PIC 9(03), FILLER X(59) |
| `app/cpy/CVACT03Y.cpy` | XREF card number PIC X(16), FILLER X(14) |
| `app/cpy/CVCRD01Y.cpy` | CC-CARD-NUM PIC X(16), CC-CARD-NUM-N PIC 9(16) |
| `app/cpy/CVTRA05Y.cpy` | TRAN-CARD-NUM PIC X(16) |
| `app/cpy/COCOM01Y.cpy` | CDEMO-CARD-NUM PIC 9(16) |
| `app/cpy/CVEXPORT.cpy` | All card number fields PIC X(16), CVV PIC 9(03) COMP |
| `app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl` | CARD_NUM CHAR(16) |
| `app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl` | CARD-NUM PIC X(16) |
| `app/cbl/COCRDLIC.cbl` | CVV fields PIC X(03)/9(03), 16-digit messages |
| `app/cbl/COCRDSLC.cbl` | CVV fields PIC X(03)/9(03), 16-digit messages |
| `app/cbl/COCRDUPC.cbl` | CVV fields, validation logic, 16-digit messages |
