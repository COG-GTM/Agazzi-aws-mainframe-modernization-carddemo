      ******************************************************************
      * Program     : CSUTLPWC.CBL
      * Application : CardDemo
      * Type        : Callable subprogram (CICS and batch)
      * Function    : Password hashing service for the USRSEC file.
      *               Passwords are never stored; only a random salt
      *               and an iterated SHA-256 hash of salt+password
      *               are kept in the user security record.
      *               Hashing uses the ICSF one way hash service
      *               CSNBOWH and the ICSF random number generator
      *               CSNBRNG.  Any ICSF failure is reported to the
      *               caller as a service error so that callers can
      *               fail closed.
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
       PROGRAM-ID. CSUTLPWC.
       AUTHOR.     AWS.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.

       DATA DIVISION.
       WORKING-STORAGE SECTION.

       01 WS-WORK-FIELDS.
         05 WS-ITERATIONS              PIC 9(05) VALUE 04096.
         05 WS-ITER-IDX                PIC 9(05) VALUE ZEROS.
         05 WS-IDX                     PIC 9(04) VALUE ZEROS.
         05 WS-OUT-IDX                 PIC 9(04) VALUE ZEROS.
         05 WS-BYTE-VAL                PIC 9(03) VALUE ZEROS.
         05 WS-HI-NIBBLE               PIC 9(02) VALUE ZEROS.
         05 WS-LO-NIBBLE               PIC 9(02) VALUE ZEROS.
         05 WS-DIFF-ACC                PIC 9(05) VALUE ZEROS.
         05 WS-HEX-DIGITS              PIC X(16)
                                       VALUE '0123456789ABCDEF'.
         05 WS-HEX-TABLE REDEFINES WS-HEX-DIGITS.
           10 WS-HEX-DIGIT             PIC X(01) OCCURS 16 TIMES.

       01 WS-HASH-INPUT.
         05 WS-HI-SALT                 PIC X(16).
         05 WS-HI-DATA                 PIC X(32).
       01 WS-HASH-INPUT-BYTES REDEFINES WS-HASH-INPUT.
         05 WS-HI-BYTE                 PIC X(01) OCCURS 48 TIMES.

       01 WS-DIGEST                    PIC X(32).
       01 WS-DIGEST-BYTES REDEFINES WS-DIGEST.
         05 WS-DIGEST-BYTE             PIC X(01) OCCURS 32 TIMES.

       01 WS-HEX-OUT                   PIC X(64).
       01 WS-HEX-OUT-CHARS REDEFINES WS-HEX-OUT.
         05 WS-HEX-CHAR                PIC X(01) OCCURS 64 TIMES.

       01 WS-RANDOM-BYTES              PIC X(08).
       01 WS-RANDOM-BYTES-T REDEFINES WS-RANDOM-BYTES.
         05 WS-RANDOM-BYTE             PIC X(01) OCCURS 8 TIMES.

      * ICSF one way hash (CSNBOWH) parameter list
       01 WS-OWH-PARMS.
         05 OWH-RETURN-CODE            PIC S9(09) COMP VALUE ZEROS.
         05 OWH-REASON-CODE            PIC S9(09) COMP VALUE ZEROS.
         05 OWH-EXIT-DATA-LEN          PIC S9(09) COMP VALUE ZEROS.
         05 OWH-EXIT-DATA              PIC X(04)       VALUE SPACES.
         05 OWH-RULE-COUNT             PIC S9(09) COMP VALUE 2.
         05 OWH-RULE-ARRAY.
           10 FILLER                   PIC X(08) VALUE 'SHA-256 '.
           10 FILLER                   PIC X(08) VALUE 'ONLY    '.
         05 OWH-TEXT-LENGTH            PIC S9(09) COMP VALUE ZEROS.
         05 OWH-CHAIN-LENGTH           PIC S9(09) COMP VALUE 128.
         05 OWH-CHAIN-VECTOR           PIC X(128)      VALUE LOW-VALUES.
         05 OWH-HASH-LENGTH            PIC S9(09) COMP VALUE 32.

      * ICSF random number generate (CSNBRNG) parameter list
       01 WS-RNG-PARMS.
         05 RNG-RETURN-CODE            PIC S9(09) COMP VALUE ZEROS.
         05 RNG-REASON-CODE            PIC S9(09) COMP VALUE ZEROS.
         05 RNG-EXIT-DATA-LEN          PIC S9(09) COMP VALUE ZEROS.
         05 RNG-EXIT-DATA              PIC X(04)       VALUE SPACES.
         05 RNG-FORM                   PIC X(08) VALUE 'RANDOM  '.

       LINKAGE SECTION.
       COPY CSPWD01Y.

       PROCEDURE DIVISION USING WS-PWD-SERVICE-PARMS.
       MAIN-PARA.

           MOVE 00 TO PWD-RETURN-CD

           EVALUATE TRUE
               WHEN PWD-FN-NEWSALT
                   PERFORM GENERATE-SALT
               WHEN PWD-FN-HASH
                   PERFORM COMPUTE-HASH
               WHEN PWD-FN-VERIFY
                   PERFORM VERIFY-HASH
               WHEN OTHER
                   MOVE 08 TO PWD-RETURN-CD
           END-EVALUATE

           GOBACK.

      *----------------------------------------------------------------*
      *   Build a 16 character (8 byte) random salt using ICSF.
      *----------------------------------------------------------------*
       GENERATE-SALT.

           MOVE SPACES TO PWD-SALT

           CALL 'CSNBRNG' USING RNG-RETURN-CODE
                                RNG-REASON-CODE
                                RNG-EXIT-DATA-LEN
                                RNG-EXIT-DATA
                                RNG-FORM
                                WS-RANDOM-BYTES

           IF RNG-RETURN-CODE NOT = 0
               MOVE 12 TO PWD-RETURN-CD
           ELSE
               MOVE ZEROS TO WS-OUT-IDX
               PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 8
                   COMPUTE WS-BYTE-VAL =
                       FUNCTION ORD(WS-RANDOM-BYTE(WS-IDX)) - 1
                   DIVIDE WS-BYTE-VAL BY 16 GIVING WS-HI-NIBBLE
                          REMAINDER WS-LO-NIBBLE
                   ADD 1 TO WS-OUT-IDX
                   MOVE WS-HEX-DIGIT(WS-HI-NIBBLE + 1)
                     TO WS-HEX-CHAR(WS-OUT-IDX)
                   ADD 1 TO WS-OUT-IDX
                   MOVE WS-HEX-DIGIT(WS-LO-NIBBLE + 1)
                     TO WS-HEX-CHAR(WS-OUT-IDX)
               END-PERFORM
               MOVE WS-HEX-OUT(1:16) TO PWD-SALT
               MOVE 'SHA256I '       TO PWD-ALGO
           END-IF.

      *----------------------------------------------------------------*
      *   Hash salt + password, WS-ITERATIONS times, and return the
      *   result as 64 hexadecimal characters.
      *----------------------------------------------------------------*
       COMPUTE-HASH.

           MOVE SPACES TO PWD-HASH

           EVALUATE TRUE
               WHEN PWD-SALT = SPACES OR LOW-VALUES
                   MOVE 08 TO PWD-RETURN-CD
               WHEN PWD-CLEAR-PWD = SPACES OR LOW-VALUES
                   MOVE 08 TO PWD-RETURN-CD
               WHEN PWD-ALGO NOT = 'SHA256I '
                   MOVE 08 TO PWD-RETURN-CD
               WHEN OTHER
                   CONTINUE
           END-EVALUATE

           IF PWD-RETURN-CD NOT = 00
               GO TO COMPUTE-HASH-EXIT
           END-IF

           MOVE LOW-VALUES     TO WS-HASH-INPUT
           MOVE PWD-SALT       TO WS-HI-SALT
           MOVE PWD-CLEAR-PWD  TO WS-HI-DATA(1:8)
           MOVE 24             TO OWH-TEXT-LENGTH

           PERFORM VARYING WS-ITER-IDX FROM 1 BY 1
                   UNTIL WS-ITER-IDX > WS-ITERATIONS
                      OR PWD-RETURN-CD NOT = 00
               PERFORM CALL-ONE-WAY-HASH
               IF PWD-RETURN-CD = 00
                   MOVE PWD-SALT  TO WS-HI-SALT
                   MOVE WS-DIGEST TO WS-HI-DATA
                   MOVE 48        TO OWH-TEXT-LENGTH
               END-IF
           END-PERFORM

           IF PWD-RETURN-CD = 00
               PERFORM HEX-ENCODE-DIGEST
               MOVE WS-HEX-OUT TO PWD-HASH
           END-IF.

       COMPUTE-HASH-EXIT.
           EXIT.

      *----------------------------------------------------------------*
      *   Recompute the hash and compare it with the stored one.
      *   The comparison scans every character so that the time taken
      *   does not depend on where the first difference is.
      *----------------------------------------------------------------*
       VERIFY-HASH.

           PERFORM COMPUTE-HASH

           IF PWD-RETURN-CD NOT = 00
               GO TO VERIFY-HASH-EXIT
           END-IF

           MOVE ZEROS TO WS-DIFF-ACC
           PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 64
               IF PWD-HASH(WS-IDX:1) NOT = PWD-EXPECTED-HASH(WS-IDX:1)
                   ADD 1 TO WS-DIFF-ACC
               END-IF
           END-PERFORM

           IF WS-DIFF-ACC NOT = ZEROS
               MOVE 04 TO PWD-RETURN-CD
           END-IF.

       VERIFY-HASH-EXIT.
           EXIT.

      *----------------------------------------------------------------*
      *                      CALL-ONE-WAY-HASH
      *----------------------------------------------------------------*
       CALL-ONE-WAY-HASH.

           MOVE LOW-VALUES TO OWH-CHAIN-VECTOR
           MOVE 32         TO OWH-HASH-LENGTH

           CALL 'CSNBOWH' USING OWH-RETURN-CODE
                                OWH-REASON-CODE
                                OWH-EXIT-DATA-LEN
                                OWH-EXIT-DATA
                                OWH-RULE-COUNT
                                OWH-RULE-ARRAY
                                OWH-TEXT-LENGTH
                                WS-HASH-INPUT
                                OWH-CHAIN-LENGTH
                                OWH-CHAIN-VECTOR
                                OWH-HASH-LENGTH
                                WS-DIGEST

           IF OWH-RETURN-CODE NOT = 0
               MOVE 12 TO PWD-RETURN-CD
           END-IF.

      *----------------------------------------------------------------*
      *                      HEX-ENCODE-DIGEST
      *----------------------------------------------------------------*
       HEX-ENCODE-DIGEST.

           MOVE SPACES TO WS-HEX-OUT
           MOVE ZEROS  TO WS-OUT-IDX

           PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 32
               COMPUTE WS-BYTE-VAL =
                   FUNCTION ORD(WS-DIGEST-BYTE(WS-IDX)) - 1
               DIVIDE WS-BYTE-VAL BY 16 GIVING WS-HI-NIBBLE
                      REMAINDER WS-LO-NIBBLE
               ADD 1 TO WS-OUT-IDX
               MOVE WS-HEX-DIGIT(WS-HI-NIBBLE + 1)
                 TO WS-HEX-CHAR(WS-OUT-IDX)
               ADD 1 TO WS-OUT-IDX
               MOVE WS-HEX-DIGIT(WS-LO-NIBBLE + 1)
                 TO WS-HEX-CHAR(WS-OUT-IDX)
           END-PERFORM.
