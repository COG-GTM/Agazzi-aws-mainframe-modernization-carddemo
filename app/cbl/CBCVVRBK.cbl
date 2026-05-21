      ******************************************************************
      * Program     : CBCVVRBK.CBL
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : Rollback 4-digit CVV card records to the
      *               original 3-digit CVV layout.
      *
      * Input  DD : CVVRBKIN  - Sequential file of card records in the
      *                        migrated layout (CARD-CVV-CD = PIC 9(04),
      *                        trailing FILLER = PIC X(58); 150 bytes).
      * Output DD : CVVRBKOT  - Sequential file of card records in the
      *                        original layout (CARD-CVV-CD = PIC 9(03),
      *                        trailing FILLER = PIC X(59); 150 bytes).
      *
      * Rollback rule: 4-digit CVV values are truncated to 3 digits
      * by removing the leading digit. For values that were originally
      * 3 digits (leading zero from migration), this restores the
      * original value. The leading digit is validated and logged.
      *
      * Counters are printed at end of run for reconciliation.
      ******************************************************************
      * Copyright Amazon.com, Inc. or its affiliates.
      * All Rights Reserved.
      *
      * Licensed under the Apache License, Version 2.0 (the "License").
      * You may not use this file except in compliance with the License.
      * You may obtain a copy of the License at
      *
      *    http://www.apache.org/licenses/LICENSE-2.0
      *
      * Unless required by applicable law or agreed to in writing,
      * software distributed under the License is distributed on an
      * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
      * either express or implied. See the License for the specific
      * language governing permissions and limitations under the License
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBCVVRBK.
       AUTHOR.        CARDDEMO TEAM.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT CARD-INPUT  ASSIGN TO CVVRBKIN
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS  MODE IS SEQUENTIAL
                  FILE  STATUS IS WS-INPUT-STATUS.

           SELECT CARD-OUTPUT ASSIGN TO CVVRBKOT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS  MODE IS SEQUENTIAL
                  FILE  STATUS IS WS-OUTPUT-STATUS.

       DATA DIVISION.
       FILE SECTION.

       FD  CARD-INPUT
           RECORDING MODE IS F
           RECORD CONTAINS 150 CHARACTERS.
      *****************************************************************
      * Migrated card record layout (4-digit CVV).  Kept inline so the
      * program can read post-migration data without depending on the
      * rollback-target CVACT02Y copybook.
      *****************************************************************
       01  MIGRATED-CARD-RECORD.
           05  MIG-CARD-NUM                      PIC X(16).
           05  MIG-CARD-ACCT-ID                  PIC 9(11).
           05  MIG-CARD-CVV-CD                   PIC 9(04).
           05  MIG-CARD-EMBOSSED-NAME            PIC X(50).
           05  MIG-CARD-EXPIRAION-DATE           PIC X(10).
           05  MIG-CARD-ACTIVE-STATUS            PIC X(01).
           05  MIG-CARD-FILLER                   PIC X(58).

       FD  CARD-OUTPUT
           RECORDING MODE IS F
           RECORD CONTAINS 150 CHARACTERS.
      *****************************************************************
      * Original card record layout (3-digit CVV).  This is the
      * pre-migration shape that records are restored to.
      *****************************************************************
       01  ORIGINAL-CARD-RECORD.
           05  ORG-CARD-NUM                      PIC X(16).
           05  ORG-CARD-ACCT-ID                  PIC 9(11).
           05  ORG-CARD-CVV-CD                   PIC 9(03).
           05  ORG-CARD-EMBOSSED-NAME            PIC X(50).
           05  ORG-CARD-EXPIRAION-DATE           PIC X(10).
           05  ORG-CARD-ACTIVE-STATUS            PIC X(01).
           05  ORG-CARD-FILLER                   PIC X(59).

       WORKING-STORAGE SECTION.

       01  WS-FILE-STATUS-AREA.
           05  WS-INPUT-STATUS                   PIC X(02).
               88  WS-INPUT-OK                   VALUE '00'.
               88  WS-INPUT-EOF                  VALUE '10'.
           05  WS-OUTPUT-STATUS                  PIC X(02).
               88  WS-OUTPUT-OK                  VALUE '00'.

       01  WS-FLAGS.
           05  WS-END-OF-FILE                    PIC X(01) VALUE 'N'.
               88  WS-EOF-REACHED                VALUE 'Y'.

       01  WS-COUNTERS.
           05  WS-READ-COUNT                     PIC 9(09) VALUE 0.
           05  WS-WRITE-COUNT                    PIC 9(09) VALUE 0.
           05  WS-TRUNCATED-COUNT                PIC 9(09) VALUE 0.
           05  WS-NONZERO-LEAD-COUNT             PIC 9(09) VALUE 0.

       01  WS-CVV-WORK.
           05  WS-CVV-4-DIGIT                    PIC 9(04).
           05  WS-CVV-4-DIGIT-X REDEFINES WS-CVV-4-DIGIT.
               10  WS-CVV-LEADING-DIGIT          PIC X(01).
               10  WS-CVV-TRAILING-3             PIC X(03).
           05  WS-CVV-3-DIGIT                    PIC 9(03).

       01  WS-DISPLAY-NUM                        PIC ZZZ,ZZZ,ZZ9.

       PROCEDURE DIVISION.

       0000-MAIN.
           DISPLAY 'START OF EXECUTION OF PROGRAM CBCVVRBK'.

           PERFORM 1000-OPEN-FILES
           PERFORM 2000-PROCESS-FILE
              UNTIL WS-EOF-REACHED
           PERFORM 9000-CLOSE-FILES
           PERFORM 9100-DISPLAY-COUNTERS

           DISPLAY 'END OF EXECUTION OF PROGRAM CBCVVRBK'.
           GOBACK.

      *****************************************************************
       1000-OPEN-FILES.
           OPEN INPUT  CARD-INPUT.
           IF NOT WS-INPUT-OK
              DISPLAY 'ERROR OPENING CVVRBKIN, STATUS: '
                      WS-INPUT-STATUS
              PERFORM 9999-ABEND-PROGRAM
           END-IF.

           OPEN OUTPUT CARD-OUTPUT.
           IF NOT WS-OUTPUT-OK
              DISPLAY 'ERROR OPENING CVVRBKOT, STATUS: '
                      WS-OUTPUT-STATUS
              PERFORM 9999-ABEND-PROGRAM
           END-IF.

           EXIT.

      *****************************************************************
       2000-PROCESS-FILE.
           READ CARD-INPUT INTO MIGRATED-CARD-RECORD
             AT END
                SET WS-EOF-REACHED TO TRUE
             NOT AT END
                ADD 1 TO WS-READ-COUNT
                PERFORM 2100-ROLLBACK-RECORD
                PERFORM 2200-WRITE-RECORD
           END-READ.
           EXIT.

      *****************************************************************
      * Map every migrated field back into the original CARD-RECORD
      * layout.  The 4-digit CVV is truncated to 3 digits by removing
      * the leading digit.  If the leading digit is non-zero (meaning
      * a genuine 4-digit CVV was issued after migration), log a
      * warning for manual review.
      *****************************************************************
       2100-ROLLBACK-RECORD.
           INITIALIZE ORIGINAL-CARD-RECORD.

           MOVE MIG-CARD-NUM             TO ORG-CARD-NUM
           MOVE MIG-CARD-ACCT-ID         TO ORG-CARD-ACCT-ID
           MOVE MIG-CARD-EMBOSSED-NAME   TO ORG-CARD-EMBOSSED-NAME
           MOVE MIG-CARD-EXPIRAION-DATE  TO ORG-CARD-EXPIRAION-DATE
           MOVE MIG-CARD-ACTIVE-STATUS   TO ORG-CARD-ACTIVE-STATUS

           MOVE MIG-CARD-CVV-CD          TO WS-CVV-4-DIGIT

           IF WS-CVV-LEADING-DIGIT NOT = '0'
              ADD 1 TO WS-NONZERO-LEAD-COUNT
              DISPLAY 'WARNING: NON-ZERO LEADING DIGIT FOR CARD '
                      MIG-CARD-NUM
                      ' CVV=' WS-CVV-4-DIGIT
           END-IF

           MOVE WS-CVV-TRAILING-3        TO WS-CVV-3-DIGIT
           MOVE WS-CVV-3-DIGIT           TO ORG-CARD-CVV-CD

           ADD 1 TO WS-TRUNCATED-COUNT.

           EXIT.

      *****************************************************************
       2200-WRITE-RECORD.
           WRITE ORIGINAL-CARD-RECORD.
           IF NOT WS-OUTPUT-OK
              DISPLAY 'ERROR WRITING CVVRBKOT, STATUS: '
                      WS-OUTPUT-STATUS
              PERFORM 9999-ABEND-PROGRAM
           END-IF.
           ADD 1 TO WS-WRITE-COUNT.
           EXIT.

      *****************************************************************
       9000-CLOSE-FILES.
           CLOSE CARD-INPUT.
           CLOSE CARD-OUTPUT.
           EXIT.

      *****************************************************************
       9100-DISPLAY-COUNTERS.
           MOVE WS-READ-COUNT          TO WS-DISPLAY-NUM
           DISPLAY 'RECORDS READ          : ' WS-DISPLAY-NUM
           MOVE WS-WRITE-COUNT         TO WS-DISPLAY-NUM
           DISPLAY 'RECORDS WRITTEN       : ' WS-DISPLAY-NUM
           MOVE WS-TRUNCATED-COUNT     TO WS-DISPLAY-NUM
           DISPLAY 'CVVS TRUNCATED 4->3   : ' WS-DISPLAY-NUM
           MOVE WS-NONZERO-LEAD-COUNT  TO WS-DISPLAY-NUM
           DISPLAY 'NON-ZERO LEADING DIGIT: ' WS-DISPLAY-NUM
           IF WS-NONZERO-LEAD-COUNT > 0
              DISPLAY '*** WARNING: SOME RECORDS HAD GENUINE 4-DIGIT'
              DISPLAY '*** CVVS. REVIEW LOG FOR AFFECTED CARD NUMBERS.'
           END-IF.
           EXIT.

      *****************************************************************
       9999-ABEND-PROGRAM.
           DISPLAY 'CBCVVRBK ABEND - SEE PRECEDING MESSAGES'.
           MOVE 16 TO RETURN-CODE.
           STOP RUN.
