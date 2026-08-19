      ******************************************************************        
      * Program     : COSGN00C.CBL
      * Application : CardDemo
      * Type        : CICS COBOL Program
      * Function    : Signon Screen for the CardDemo Application
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
       PROGRAM-ID. COSGN00C.
       AUTHOR.     AWS.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.

       DATA DIVISION.
      *----------------------------------------------------------------*
      *                     WORKING STORAGE SECTION
      *----------------------------------------------------------------*
       WORKING-STORAGE SECTION.

       01 WS-VARIABLES.
         05 WS-PGMNAME                 PIC X(08) VALUE 'COSGN00C'.
         05 WS-TRANID                  PIC X(04) VALUE 'CC00'.
         05 WS-MESSAGE                 PIC X(80) VALUE SPACES.
         05 WS-USRSEC-FILE             PIC X(08) VALUE 'USRSEC  '.
         05 WS-ERR-FLG                 PIC X(01) VALUE 'N'.
           88 ERR-FLG-ON                         VALUE 'Y'.
           88 ERR-FLG-OFF                        VALUE 'N'.
         05 WS-RESP-CD                 PIC S9(09) COMP VALUE ZEROS.
         05 WS-REAS-CD                 PIC S9(09) COMP VALUE ZEROS.
         05 WS-USER-ID                 PIC X(08).
         05 WS-USER-PWD                PIC X(08).
         05 WS-DELAY-SECS              PIC S9(08) COMP VALUE ZEROS.
         05 WS-LOCK-FLG                PIC X(01) VALUE 'N'.
           88 USER-LOCKED                        VALUE 'Y'.
           88 USER-NOT-LOCKED                    VALUE 'N'.
         05 WS-NOW-SECONDS             PIC 9(12) VALUE ZEROS.
         05 WS-LOCK-SECONDS            PIC 9(12) VALUE ZEROS.
         05 WS-BAD-SIGNON-MSG          PIC X(80) VALUE
            'Invalid user ID or password. Try again ...'.

       01 WS-SIGNON-TS.
         05 WS-SIGNON-DATE             PIC 9(08) VALUE ZEROS.
         05 WS-SIGNON-HH               PIC 9(02) VALUE ZEROS.
         05 WS-SIGNON-MI               PIC 9(02) VALUE ZEROS.
         05 WS-SIGNON-SS               PIC 9(02) VALUE ZEROS.

       01 WS-STORED-LOCK-TS.
         05 WS-LOCK-DATE               PIC 9(08) VALUE ZEROS.
         05 WS-LOCK-HH                 PIC 9(02) VALUE ZEROS.
         05 WS-LOCK-MI                 PIC 9(02) VALUE ZEROS.
         05 WS-LOCK-SS                 PIC 9(02) VALUE ZEROS.

       COPY COCOM01Y.

       COPY COSGN00.

       COPY COTTL01Y.
       COPY CSDAT01Y.
       COPY CSMSG01Y.
       COPY CSUSR01Y.
       COPY CSPWD01Y.
       COPY CSPWD02Y.

       COPY DFHAID.
       COPY DFHBMSCA.
      *COPY DFHATTR.

      *----------------------------------------------------------------*
      *                        LINKAGE SECTION
      *----------------------------------------------------------------*
       LINKAGE SECTION.
       01  DFHCOMMAREA.
         05  LK-COMMAREA                           PIC X(01)
             OCCURS 1 TO 32767 TIMES DEPENDING ON EIBCALEN.

      *----------------------------------------------------------------*
      *                      PROCEDURE DIVISION
      *----------------------------------------------------------------*
       PROCEDURE DIVISION.
       MAIN-PARA.

           SET ERR-FLG-OFF TO TRUE

           MOVE SPACES TO WS-MESSAGE
                          ERRMSGO OF COSGN0AO

           IF EIBCALEN = 0
               MOVE LOW-VALUES TO COSGN0AO
               MOVE -1       TO USERIDL OF COSGN0AI
               PERFORM SEND-SIGNON-SCREEN
           ELSE
               EVALUATE EIBAID
                   WHEN DFHENTER
                       PERFORM PROCESS-ENTER-KEY
                   WHEN DFHPF3
                       MOVE CCDA-MSG-THANK-YOU        TO WS-MESSAGE
                       PERFORM SEND-PLAIN-TEXT
                   WHEN OTHER
                       MOVE 'Y'                       TO WS-ERR-FLG
                       MOVE CCDA-MSG-INVALID-KEY      TO WS-MESSAGE
                       PERFORM SEND-SIGNON-SCREEN
               END-EVALUATE
           END-IF.

           EXEC CICS RETURN
                     TRANSID (WS-TRANID)
                     COMMAREA (CARDDEMO-COMMAREA)
                     LENGTH(LENGTH OF CARDDEMO-COMMAREA)
           END-EXEC.


      *----------------------------------------------------------------*
      *                      PROCESS-ENTER-KEY
      *----------------------------------------------------------------*
       PROCESS-ENTER-KEY.

           EXEC CICS RECEIVE
                     MAP('COSGN0A')
                     MAPSET('COSGN00')
                     RESP(WS-RESP-CD)
                     RESP2(WS-REAS-CD)
           END-EXEC.

           EVALUATE TRUE
               WHEN USERIDI OF COSGN0AI = SPACES OR LOW-VALUES
                   MOVE 'Y'      TO WS-ERR-FLG
                   MOVE 'Please enter User ID ...' TO WS-MESSAGE
                   MOVE -1       TO USERIDL OF COSGN0AI
                   PERFORM SEND-SIGNON-SCREEN
               WHEN PASSWDI OF COSGN0AI = SPACES OR LOW-VALUES
                   MOVE 'Y'      TO WS-ERR-FLG
                   MOVE 'Please enter Password ...' TO WS-MESSAGE
                   MOVE -1       TO PASSWDL OF COSGN0AI
                   PERFORM SEND-SIGNON-SCREEN
               WHEN OTHER
                   CONTINUE
           END-EVALUATE.

           MOVE FUNCTION UPPER-CASE(USERIDI OF COSGN0AI) TO
                           WS-USER-ID
                           CDEMO-USER-ID
           MOVE FUNCTION UPPER-CASE(PASSWDI OF COSGN0AI) TO
                           WS-USER-PWD

           IF NOT ERR-FLG-ON
               PERFORM READ-USER-SEC-FILE
           END-IF.

      *----------------------------------------------------------------*
      *                      SEND-SIGNON-SCREEN
      *----------------------------------------------------------------*
       SEND-SIGNON-SCREEN.

           PERFORM POPULATE-HEADER-INFO

           MOVE WS-MESSAGE TO ERRMSGO OF COSGN0AO

           EXEC CICS SEND
                     MAP('COSGN0A')
                     MAPSET('COSGN00')
                     FROM(COSGN0AO)
                     ERASE
                     CURSOR
           END-EXEC.

      *----------------------------------------------------------------*
      *                      SEND-PLAIN-TEXT
      *----------------------------------------------------------------*
       SEND-PLAIN-TEXT.

           EXEC CICS SEND TEXT
                     FROM(WS-MESSAGE)
                     LENGTH(LENGTH OF WS-MESSAGE)
                     ERASE
                     FREEKB
           END-EXEC.

           EXEC CICS RETURN
           END-EXEC.

      *----------------------------------------------------------------*
      *                      POPULATE-HEADER-INFO
      *----------------------------------------------------------------*
       POPULATE-HEADER-INFO.

           MOVE FUNCTION CURRENT-DATE  TO WS-CURDATE-DATA

           MOVE CCDA-TITLE01           TO TITLE01O OF COSGN0AO
           MOVE CCDA-TITLE02           TO TITLE02O OF COSGN0AO
           MOVE WS-TRANID              TO TRNNAMEO OF COSGN0AO
           MOVE WS-PGMNAME             TO PGMNAMEO OF COSGN0AO

           MOVE WS-CURDATE-MONTH       TO WS-CURDATE-MM
           MOVE WS-CURDATE-DAY         TO WS-CURDATE-DD
           MOVE WS-CURDATE-YEAR(3:2)   TO WS-CURDATE-YY

           MOVE WS-CURDATE-MM-DD-YY    TO CURDATEO OF COSGN0AO

           MOVE WS-CURTIME-HOURS       TO WS-CURTIME-HH
           MOVE WS-CURTIME-MINUTE      TO WS-CURTIME-MM
           MOVE WS-CURTIME-SECOND      TO WS-CURTIME-SS

           MOVE WS-CURTIME-HH-MM-SS    TO CURTIMEO OF COSGN0AO

           EXEC CICS ASSIGN
               APPLID(APPLIDO OF COSGN0AO)
           END-EXEC

           EXEC CICS ASSIGN
               SYSID(SYSIDO OF COSGN0AO)
           END-EXEC.

      *----------------------------------------------------------------*
      *                      READ-USER-SEC-FILE
      *----------------------------------------------------------------*
       READ-USER-SEC-FILE.

           EXEC CICS READ
                DATASET   (WS-USRSEC-FILE)
                INTO      (SEC-USER-DATA)
                LENGTH    (LENGTH OF SEC-USER-DATA)
                RIDFLD    (WS-USER-ID)
                KEYLENGTH (LENGTH OF WS-USER-ID)
                UPDATE
                RESP      (WS-RESP-CD)
                RESP2     (WS-REAS-CD)
           END-EXEC.

           EVALUATE WS-RESP-CD
               WHEN 0
                   PERFORM VALIDATE-SIGNON
               WHEN 13
                   PERFORM REJECT-SIGNON
               WHEN OTHER
                   PERFORM SIGNON-NOT-AVAILABLE
           END-EVALUATE.

      *----------------------------------------------------------------*
      *                      VALIDATE-SIGNON
      *  The stored password hash is never compared with the entered
      *  password directly.  The entered password is re-hashed with
      *  the salt held in the user record and the hashes are compared.
      *----------------------------------------------------------------*
       VALIDATE-SIGNON.

           PERFORM CHECK-ACCOUNT-LOCK

           EVALUATE TRUE
               WHEN USER-LOCKED
                   PERFORM UNLOCK-USER-SEC-FILE
                   MOVE 'Y'      TO WS-ERR-FLG
                   MOVE 'Account is locked. Contact administrator.'
                                 TO WS-MESSAGE
                   MOVE -1       TO USERIDL OF COSGN0AI
                   PERFORM SEND-SIGNON-SCREEN
               WHEN SEC-USR-PWD-NOT-SET
                   PERFORM UNLOCK-USER-SEC-FILE
                   PERFORM REJECT-SIGNON
               WHEN NOT SEC-USR-PWD-SHA256
                   PERFORM UNLOCK-USER-SEC-FILE
                   PERFORM SIGNON-NOT-AVAILABLE
               WHEN OTHER
                   PERFORM VERIFY-PASSWORD
           END-EVALUATE.

      *----------------------------------------------------------------*
      *                      VERIFY-PASSWORD
      *----------------------------------------------------------------*
       VERIFY-PASSWORD.

           INITIALIZE WS-PWD-SERVICE-PARMS
           SET PWD-FN-VERIFY     TO TRUE
           MOVE SEC-USR-PWD-ALGO TO PWD-ALGO
           MOVE SEC-USR-PWD-SALT TO PWD-SALT
           MOVE WS-USER-PWD      TO PWD-CLEAR-PWD
           MOVE SEC-USR-PWD-HASH TO PWD-EXPECTED-HASH

           CALL 'CSUTLPWC' USING WS-PWD-SERVICE-PARMS

           MOVE SPACES TO PWD-CLEAR-PWD
                          PWD-HASH
                          WS-USER-PWD

           EVALUATE TRUE
               WHEN PWD-OK
                   PERFORM SIGNON-SUCCESS
               WHEN PWD-MISMATCH
                   PERFORM RECORD-FAILED-ATTEMPT
               WHEN OTHER
                   PERFORM UNLOCK-USER-SEC-FILE
                   PERFORM SIGNON-NOT-AVAILABLE
           END-EVALUATE.

      *----------------------------------------------------------------*
      *                      SIGNON-SUCCESS
      *----------------------------------------------------------------*
       SIGNON-SUCCESS.

           IF SEC-USR-FAIL-CNT NOT = ZEROS
           OR NOT SEC-USR-NOT-LOCKED
               MOVE ZEROS  TO SEC-USR-FAIL-CNT
               MOVE SPACES TO SEC-USR-LOCK-TS
               PERFORM REWRITE-USER-SEC-FILE
           ELSE
               PERFORM UNLOCK-USER-SEC-FILE
           END-IF

           MOVE WS-TRANID    TO CDEMO-FROM-TRANID
           MOVE WS-PGMNAME   TO CDEMO-FROM-PROGRAM
           MOVE WS-USER-ID   TO CDEMO-USER-ID
           MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
           MOVE ZEROS        TO CDEMO-PGM-CONTEXT

           IF CDEMO-USRTYP-ADMIN
                EXEC CICS XCTL
                  PROGRAM ('COADM01C')
                  COMMAREA(CARDDEMO-COMMAREA)
                END-EXEC
           ELSE
                EXEC CICS XCTL
                  PROGRAM ('COMEN01C')
                  COMMAREA(CARDDEMO-COMMAREA)
                END-EXEC
           END-IF.

      *----------------------------------------------------------------*
      *                      RECORD-FAILED-ATTEMPT
      *  Counts the failure in the user record and locks the account
      *  once the policy limit is reached.
      *----------------------------------------------------------------*
       RECORD-FAILED-ATTEMPT.

           IF SEC-USR-FAIL-CNT < 99
               ADD 1 TO SEC-USR-FAIL-CNT
           END-IF

           IF SEC-USR-FAIL-CNT >= PWD-MAX-FAILED-ATTEMPTS
               PERFORM GET-CURRENT-TIMESTAMP
               MOVE WS-SIGNON-TS TO SEC-USR-LOCK-TS
           END-IF

           PERFORM REWRITE-USER-SEC-FILE
           PERFORM REJECT-SIGNON.

      *----------------------------------------------------------------*
      *                      CHECK-ACCOUNT-LOCK
      *----------------------------------------------------------------*
       CHECK-ACCOUNT-LOCK.

           SET USER-NOT-LOCKED TO TRUE

           IF SEC-USR-NOT-LOCKED
               CONTINUE
           ELSE
               IF SEC-USR-LOCK-TS IS NUMERIC
                   MOVE SEC-USR-LOCK-TS TO WS-STORED-LOCK-TS
                   PERFORM GET-CURRENT-TIMESTAMP
                   COMPUTE WS-NOW-SECONDS =
                       FUNCTION INTEGER-OF-DATE(WS-SIGNON-DATE)
                       * 86400
                       + WS-SIGNON-HH * 3600
                       + WS-SIGNON-MI * 60
                       + WS-SIGNON-SS
                   COMPUTE WS-LOCK-SECONDS =
                       FUNCTION INTEGER-OF-DATE(WS-LOCK-DATE)
                       * 86400
                       + WS-LOCK-HH * 3600
                       + WS-LOCK-MI * 60
                       + WS-LOCK-SS
                   IF WS-NOW-SECONDS < WS-LOCK-SECONDS
                   OR WS-NOW-SECONDS - WS-LOCK-SECONDS <
                                                 PWD-LOCKOUT-SECONDS
                       SET USER-LOCKED TO TRUE
                   ELSE
                       MOVE ZEROS  TO SEC-USR-FAIL-CNT
                       MOVE SPACES TO SEC-USR-LOCK-TS
                   END-IF
               ELSE
      *            An unreadable lock stamp keeps the account locked.
                   SET USER-LOCKED TO TRUE
               END-IF
           END-IF.

      *----------------------------------------------------------------*
      *                      GET-CURRENT-TIMESTAMP
      *----------------------------------------------------------------*
       GET-CURRENT-TIMESTAMP.

           MOVE FUNCTION CURRENT-DATE TO WS-CURDATE-DATA

           MOVE WS-CURDATE-N       TO WS-SIGNON-DATE
           MOVE WS-CURTIME-HOURS   TO WS-SIGNON-HH
           MOVE WS-CURTIME-MINUTE  TO WS-SIGNON-MI
           MOVE WS-CURTIME-SECOND  TO WS-SIGNON-SS.

      *----------------------------------------------------------------*
      *                      REJECT-SIGNON
      *  One message for every failure - unknown user, disabled or
      *  wrong password - so the screen never says which one it was.
      *  The delay slows down automated guessing.
      *----------------------------------------------------------------*
       REJECT-SIGNON.

           MOVE PWD-FAILED-DELAY-SECONDS TO WS-DELAY-SECS

           EXEC CICS DELAY
                     FOR SECONDS(WS-DELAY-SECS)
                     RESP(WS-RESP-CD)
                     RESP2(WS-REAS-CD)
           END-EXEC

           MOVE 'Y'                TO WS-ERR-FLG
           MOVE WS-BAD-SIGNON-MSG  TO WS-MESSAGE
           MOVE -1                 TO USERIDL OF COSGN0AI
           PERFORM SEND-SIGNON-SCREEN.

      *----------------------------------------------------------------*
      *                      SIGNON-NOT-AVAILABLE
      *----------------------------------------------------------------*
       SIGNON-NOT-AVAILABLE.

           MOVE 'Y'      TO WS-ERR-FLG
           MOVE 'Unable to verify the User ...' TO WS-MESSAGE
           MOVE -1       TO USERIDL OF COSGN0AI
           PERFORM SEND-SIGNON-SCREEN.

      *----------------------------------------------------------------*
      *                      REWRITE-USER-SEC-FILE
      *----------------------------------------------------------------*
       REWRITE-USER-SEC-FILE.

           EXEC CICS REWRITE
                DATASET   (WS-USRSEC-FILE)
                FROM      (SEC-USER-DATA)
                LENGTH    (LENGTH OF SEC-USER-DATA)
                RESP      (WS-RESP-CD)
                RESP2     (WS-REAS-CD)
           END-EXEC

           EXEC CICS SYNCPOINT
           END-EXEC.

      *----------------------------------------------------------------*
      *                      UNLOCK-USER-SEC-FILE
      *----------------------------------------------------------------*
       UNLOCK-USER-SEC-FILE.

           EXEC CICS UNLOCK
                DATASET   (WS-USRSEC-FILE)
                RESP      (WS-RESP-CD)
                RESP2     (WS-REAS-CD)
           END-EXEC.
      *
      * Ver: CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19 23:12:33 CDT
      *
