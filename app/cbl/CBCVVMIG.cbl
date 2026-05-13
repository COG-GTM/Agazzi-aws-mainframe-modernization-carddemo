      ******************************************************************
      * Program     : CBCVVMIG.CBL
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : Migrate legacy 3-digit CVV card records to the
      *               new 4-digit CVV layout.
      *
      * Input  DD : CVVMIGIN  - Sequential file of card records in the
      *                        legacy layout (CARD-CVV-CD = PIC 9(03),
      *                        trailing FILLER = PIC X(59); 150 bytes).
      * Output DD : CVVMIGOT  - Sequential file of card records in the
      *                        new layout (CARD-CVV-CD = PIC 9(04),
      *                        trailing FILLER = PIC X(58); 150 bytes).
      *
      * Migration rule: legacy 3-digit CVV values are widened to 4
      * digits with a leading zero (e.g. 123 -> 0123) by relying on the
      * COBOL MOVE semantics from PIC 9(03) to PIC 9(04).
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
       PROGRAM-ID.    CBCVVMIG.
       AUTHOR.        CARDDEMO TEAM.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT CARD-INPUT  ASSIGN TO CVVMIGIN
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS  MODE IS SEQUENTIAL
                  FILE  STATUS IS WS-INPUT-STATUS.

           SELECT CARD-OUTPUT ASSIGN TO CVVMIGOT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS  MODE IS SEQUENTIAL
                  FILE  STATUS IS WS-OUTPUT-STATUS.

       DATA DIVISION.
       FILE SECTION.

       FD  CARD-INPUT
           RECORDING MODE IS F
           RECORD CONTAINS 150 CHARACTERS.
      *****************************************************************
      * Legacy card record layout (3-digit CVV).  Kept inline so the
      * program can read pre-migration data without depending on the
      * updated CVACT02Y copybook.
      *****************************************************************
       01  LEGACY-CARD-RECORD.
           05  LEG-CARD-NUM                      PIC X(16).
           05  LEG-CARD-ACCT-ID                  PIC 9(11).
           05  LEG-CARD-CVV-CD                   PIC 9(03).
           05  LEG-CARD-EMBOSSED-NAME            PIC X(50).
           05  LEG-CARD-EXPIRAION-DATE           PIC X(10).
           05  LEG-CARD-ACTIVE-STATUS            PIC X(01).
           05  LEG-CARD-FILLER                   PIC X(59).

       FD  CARD-OUTPUT
           RECORDING MODE IS F
           RECORD CONTAINS 150 CHARACTERS.
      *****************************************************************
      * New card record layout (4-digit CVV) supplied by the project
      * copybook.  This is the post-migration shape of every record.
      *****************************************************************
       COPY CVACT02Y.

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
           05  WS-PADDED-COUNT                   PIC 9(09) VALUE 0.

       01  WS-DISPLAY-NUM                        PIC ZZZ,ZZZ,ZZ9.

       PROCEDURE DIVISION.

       0000-MAIN.
           DISPLAY 'START OF EXECUTION OF PROGRAM CBCVVMIG'.

           PERFORM 1000-OPEN-FILES
           PERFORM 2000-PROCESS-FILE
              UNTIL WS-EOF-REACHED
           PERFORM 9000-CLOSE-FILES
           PERFORM 9100-DISPLAY-COUNTERS

           DISPLAY 'END OF EXECUTION OF PROGRAM CBCVVMIG'.
           GOBACK.

      *****************************************************************
       1000-OPEN-FILES.
           OPEN INPUT  CARD-INPUT.
           IF NOT WS-INPUT-OK
              DISPLAY 'ERROR OPENING CVVMIGIN, STATUS: ' WS-INPUT-STATUS
              PERFORM 9999-ABEND-PROGRAM
           END-IF.

           OPEN OUTPUT CARD-OUTPUT.
           IF NOT WS-OUTPUT-OK
              DISPLAY 'ERROR OPENING CVVMIGOT, STATUS: '
                      WS-OUTPUT-STATUS
              PERFORM 9999-ABEND-PROGRAM
           END-IF.

           EXIT.

      *****************************************************************
       2000-PROCESS-FILE.
           READ CARD-INPUT INTO LEGACY-CARD-RECORD
             AT END
                SET WS-EOF-REACHED TO TRUE
             NOT AT END
                ADD 1 TO WS-READ-COUNT
                PERFORM 2100-MIGRATE-RECORD
                PERFORM 2200-WRITE-RECORD
           END-READ.
           EXIT.

      *****************************************************************
      * Map every legacy field into the new CARD-RECORD layout.
      * MOVE from PIC 9(03) to PIC 9(04) left-pads with a leading zero,
      * which is exactly the migration rule (e.g. 123 -> 0123).
      *****************************************************************
       2100-MIGRATE-RECORD.
           INITIALIZE CARD-RECORD.

           MOVE LEG-CARD-NUM             TO CARD-NUM
           MOVE LEG-CARD-ACCT-ID         TO CARD-ACCT-ID
           MOVE LEG-CARD-CVV-CD          TO CARD-CVV-CD
           MOVE LEG-CARD-EMBOSSED-NAME   TO CARD-EMBOSSED-NAME
           MOVE LEG-CARD-EXPIRAION-DATE  TO CARD-EXPIRAION-DATE
           MOVE LEG-CARD-ACTIVE-STATUS   TO CARD-ACTIVE-STATUS

      *    Every legacy record is widened from 3 to 4 digit CVV.
           ADD 1 TO WS-PADDED-COUNT.

           EXIT.

      *****************************************************************
       2200-WRITE-RECORD.
           WRITE CARD-RECORD.
           IF NOT WS-OUTPUT-OK
              DISPLAY 'ERROR WRITING CVVMIGOT, STATUS: '
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
           MOVE WS-READ-COUNT    TO WS-DISPLAY-NUM
           DISPLAY 'RECORDS READ      : ' WS-DISPLAY-NUM
           MOVE WS-WRITE-COUNT   TO WS-DISPLAY-NUM
           DISPLAY 'RECORDS WRITTEN   : ' WS-DISPLAY-NUM
           MOVE WS-PADDED-COUNT  TO WS-DISPLAY-NUM
           DISPLAY 'LEGACY 3-DIG CVVS : ' WS-DISPLAY-NUM
           EXIT.

      *****************************************************************
       9999-ABEND-PROGRAM.
           DISPLAY 'CBCVVMIG ABEND - SEE PRECEDING MESSAGES'.
           MOVE 16 TO RETURN-CODE.
           STOP RUN.
