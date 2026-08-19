      ******************************************************************
      * Program     : CBUSRPWC.CBL
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : Set (or reset) sign on passwords in the USRSEC
      *               file.  Reads a control file holding user id and
      *               password pairs, stores a random salt and the
      *               hash of the password produced by CSUTLPWC, and
      *               clears any sign on lockout.  The clear password
      *               is never written to the USRSEC file nor to the
      *               report.
      *               The control file must be deleted by the job that
      *               runs this program - see USRSECPW.jcl.
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
       PROGRAM-ID.    CBUSRPWC.
       AUTHOR.        AWS.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT USRSEC-FILE ASSIGN TO USRSEC
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-USR-ID
                  FILE STATUS  IS USRSEC-STATUS.
      *
           SELECT PWDCTL-FILE ASSIGN TO PWDCTL
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS PWDCTL-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  USRSEC-FILE.
       01  FD-USRSEC-REC.
           05 FD-USR-ID                  PIC X(08).
           05 FD-USR-DATA                PIC X(152).
       FD  PWDCTL-FILE.
       01  FD-PWDCTL-REC.
           05 FD-CTL-USR-ID              PIC X(08).
           05 FD-CTL-PASSWORD            PIC X(08).
           05 FD-CTL-FILLER              PIC X(64).

       WORKING-STORAGE SECTION.

       01 WS-STATUS.
         05 USRSEC-STATUS                PIC X(02) VALUE '00'.
         05 PWDCTL-STATUS                PIC X(02) VALUE '00'.
         05 WS-END-OF-CTL                PIC X(01) VALUE 'N'.
           88 END-OF-CTL                           VALUE 'Y'.
         05 WS-ABEND-CD                  PIC 9(04) VALUE ZEROS.

       01 WS-COUNTERS.
         05 WS-READ-CNT                  PIC 9(05) VALUE ZEROS.
         05 WS-UPDATED-CNT               PIC 9(05) VALUE ZEROS.
         05 WS-ERROR-CNT                 PIC 9(05) VALUE ZEROS.

       COPY CSUSR01Y.
       COPY CSPWD01Y.
       COPY CSPWD02Y.

       PROCEDURE DIVISION.
       MAIN-PARA.

           OPEN I-O    USRSEC-FILE
           IF USRSEC-STATUS NOT = '00'
               DISPLAY 'CBUSRPWC: UNABLE TO OPEN USRSEC - STATUS '
                        USRSEC-STATUS
               MOVE 3001 TO WS-ABEND-CD
               PERFORM ABEND-PROGRAM
           END-IF

           OPEN INPUT  PWDCTL-FILE
           IF PWDCTL-STATUS NOT = '00'
               DISPLAY 'CBUSRPWC: UNABLE TO OPEN PWDCTL - STATUS '
                        PWDCTL-STATUS
               MOVE 3002 TO WS-ABEND-CD
               PERFORM ABEND-PROGRAM
           END-IF

           PERFORM UNTIL END-OF-CTL
               READ PWDCTL-FILE
                   AT END
                       SET END-OF-CTL TO TRUE
                   NOT AT END
                       ADD 1 TO WS-READ-CNT
                       PERFORM SET-ONE-PASSWORD
               END-READ
           END-PERFORM

           CLOSE PWDCTL-FILE
           CLOSE USRSEC-FILE

           DISPLAY 'CBUSRPWC: CONTROL RECORDS READ  : ' WS-READ-CNT
           DISPLAY 'CBUSRPWC: PASSWORDS SET         : ' WS-UPDATED-CNT
           DISPLAY 'CBUSRPWC: RECORDS IN ERROR      : ' WS-ERROR-CNT

           IF WS-ERROR-CNT NOT = ZEROS
               MOVE 3003 TO WS-ABEND-CD
               PERFORM ABEND-PROGRAM
           END-IF

           GOBACK.

      *----------------------------------------------------------------*
      *                      SET-ONE-PASSWORD
      *----------------------------------------------------------------*
       SET-ONE-PASSWORD.

           MOVE FUNCTION UPPER-CASE(FD-CTL-USR-ID) TO FD-USR-ID

           READ USRSEC-FILE
           IF USRSEC-STATUS NOT = '00'
               DISPLAY 'CBUSRPWC: USER NOT UPDATED ' FD-USR-ID
                       ' STATUS ' USRSEC-STATUS
               ADD 1 TO WS-ERROR-CNT
               GO TO SET-ONE-PASSWORD-EXIT
           END-IF

           IF FD-CTL-PASSWORD = SPACES OR LOW-VALUES
               DISPLAY 'CBUSRPWC: BLANK PASSWORD FOR ' FD-USR-ID
               ADD 1 TO WS-ERROR-CNT
               GO TO SET-ONE-PASSWORD-EXIT
           END-IF

           MOVE FD-USRSEC-REC TO SEC-USER-DATA

           INITIALIZE WS-PWD-SERVICE-PARMS
           SET PWD-FN-NEWSALT TO TRUE
           CALL 'CSUTLPWC' USING WS-PWD-SERVICE-PARMS

           IF PWD-OK
               SET PWD-FN-HASH TO TRUE
               MOVE FUNCTION UPPER-CASE(FD-CTL-PASSWORD)
                 TO PWD-CLEAR-PWD
               CALL 'CSUTLPWC' USING WS-PWD-SERVICE-PARMS
           END-IF

           IF NOT PWD-OK
               DISPLAY 'CBUSRPWC: HASH SERVICE FAILED FOR ' FD-USR-ID
                       ' RC ' PWD-RETURN-CD
               ADD 1 TO WS-ERROR-CNT
               MOVE SPACES TO PWD-CLEAR-PWD
               GO TO SET-ONE-PASSWORD-EXIT
           END-IF

           MOVE PWD-ALGO TO SEC-USR-PWD-ALGO
           MOVE PWD-SALT TO SEC-USR-PWD-SALT
           MOVE PWD-HASH TO SEC-USR-PWD-HASH
           MOVE ZEROS    TO SEC-USR-FAIL-CNT
           MOVE SPACES   TO SEC-USR-LOCK-TS

           MOVE SPACES   TO PWD-CLEAR-PWD
                            PWD-HASH

           MOVE SEC-USER-DATA TO FD-USRSEC-REC

           REWRITE FD-USRSEC-REC
           IF USRSEC-STATUS = '00'
               ADD 1 TO WS-UPDATED-CNT
               DISPLAY 'CBUSRPWC: PASSWORD SET FOR ' FD-USR-ID
           ELSE
               DISPLAY 'CBUSRPWC: REWRITE FAILED FOR ' FD-USR-ID
                       ' STATUS ' USRSEC-STATUS
               ADD 1 TO WS-ERROR-CNT
           END-IF.

       SET-ONE-PASSWORD-EXIT.
           EXIT.

      *----------------------------------------------------------------*
      *                      ABEND-PROGRAM
      *----------------------------------------------------------------*
       ABEND-PROGRAM.

           DISPLAY 'CBUSRPWC: ABENDING WITH CODE ' WS-ABEND-CD
           MOVE 16 TO RETURN-CODE
           GOBACK.
