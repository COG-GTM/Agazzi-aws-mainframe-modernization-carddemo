      ******************************************************************        
      * Program     : COUSR03C.CBL
      * Application : CardDemo
      * Type        : CICS COBOL Program
      * Function    : Delete a user from USRSEC file
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
       PROGRAM-ID. COUSR03C.
       AUTHOR.     AWS.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.

       DATA DIVISION.
      *----------------------------------------------------------------*
      *                     WORKING STORAGE SECTION
      *----------------------------------------------------------------*
       WORKING-STORAGE SECTION.

       01 WS-VARIABLES.
         05 WS-PGMNAME                 PIC X(08) VALUE 'COUSR03C'.
         05 WS-TRANID                  PIC X(04) VALUE 'CU03'.
         05 WS-MESSAGE                 PIC X(80) VALUE SPACES.
         05 WS-USRSEC-FILE             PIC X(08) VALUE 'USRSEC  '.
         05 WS-ERR-FLG                 PIC X(01) VALUE 'N'.
           88 ERR-FLG-ON                         VALUE 'Y'.
           88 ERR-FLG-OFF                        VALUE 'N'.
         05 WS-RESP-CD                 PIC S9(09) COMP VALUE ZEROS.
         05 WS-REAS-CD                 PIC S9(09) COMP VALUE ZEROS.
         05 WS-USR-MODIFIED            PIC X(01) VALUE 'N'.
           88 USR-MODIFIED-YES                   VALUE 'Y'.
           88 USR-MODIFIED-NO                    VALUE 'N'.
         05 WS-AUTH-FLG                PIC X(01) VALUE 'N'.
           88 ADMIN-AUTHORIZED                   VALUE 'Y'.
           88 ADMIN-NOT-AUTHORIZED               VALUE 'N'.
         05 WS-USER-SEC-EOF            PIC X(01) VALUE 'N'.
           88 USER-SEC-EOF                       VALUE 'Y'.
           88 USER-SEC-NOT-EOF                   VALUE 'N'.
         05 WS-ADMIN-COUNT             PIC S9(04) COMP VALUE ZEROS.
         05 WS-TARGET-USR-ID           PIC X(08) VALUE SPACES.
         05 WS-TARGET-USR-TYPE         PIC X(01) VALUE SPACES.
           
       COPY COCOM01Y.
          05 CDEMO-CU03-INFO.
             10 CDEMO-CU03-USRID-FIRST     PIC X(08).
             10 CDEMO-CU03-USRID-LAST      PIC X(08).
             10 CDEMO-CU03-PAGE-NUM        PIC 9(08).
             10 CDEMO-CU03-NEXT-PAGE-FLG   PIC X(01) VALUE 'N'.
                88 NEXT-PAGE-YES                     VALUE 'Y'.
                88 NEXT-PAGE-NO                      VALUE 'N'.
             10 CDEMO-CU03-USR-SEL-FLG     PIC X(01).
             10 CDEMO-CU03-USR-SELECTED    PIC X(08).

       COPY COUSR03.

       COPY COTTL01Y.
       COPY CSDAT01Y.
       COPY CSMSG01Y.
       COPY CSUSR01Y.

       COPY DFHAID.
       COPY DFHBMSCA.

      *----------------------------------------------------------------*
      *                        LINKAGE SECTION
      *----------------------------------------------------------------*
       LINKAGE SECTION.
       01  DFHCOMMAREA.
         05  LK-COMMAREA                           PIC X(01)
             OCCURS 1 TO 32767 TIMES DEPENDING ON EIBCALEN.

      *----------------------------------------------------------------*
      *                       PROCEDURE DIVISION
      *----------------------------------------------------------------*
       PROCEDURE DIVISION.
       MAIN-PARA.

           SET ERR-FLG-OFF          TO TRUE
           SET USR-MODIFIED-NO      TO TRUE
           SET ADMIN-NOT-AUTHORIZED TO TRUE

           MOVE SPACES TO WS-MESSAGE
                          ERRMSGO OF COUSR3AO

           IF EIBCALEN = 0
               MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM
               PERFORM RETURN-TO-PREV-SCREEN
           ELSE
               MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA
               PERFORM VERIFY-ADMIN-AUTHORITY
               IF NOT CDEMO-PGM-REENTER
                   SET CDEMO-PGM-REENTER    TO TRUE
                   MOVE LOW-VALUES          TO COUSR3AO
                   MOVE -1       TO USRIDINL OF COUSR3AI
                   IF CDEMO-CU03-USR-SELECTED NOT =
                                              SPACES AND LOW-VALUES
                       MOVE CDEMO-CU03-USR-SELECTED TO
                            USRIDINI OF COUSR3AI
                       PERFORM PROCESS-ENTER-KEY
                   END-IF
                   PERFORM SEND-USRDEL-SCREEN
               ELSE
                   PERFORM RECEIVE-USRDEL-SCREEN
                   EVALUATE EIBAID
                       WHEN DFHENTER
                           PERFORM PROCESS-ENTER-KEY
                       WHEN DFHPF3
                           IF CDEMO-FROM-PROGRAM = SPACES OR LOW-VALUES
                               MOVE 'COADM01C' TO CDEMO-TO-PROGRAM
                           ELSE
                               MOVE CDEMO-FROM-PROGRAM TO
                               CDEMO-TO-PROGRAM
                           END-IF
                           PERFORM RETURN-TO-PREV-SCREEN
                       WHEN DFHPF4
                           PERFORM CLEAR-CURRENT-SCREEN
                       WHEN DFHPF5
                           PERFORM DELETE-USER-INFO
                       WHEN DFHPF12
                           MOVE 'COADM01C' TO CDEMO-TO-PROGRAM
                           PERFORM RETURN-TO-PREV-SCREEN
                       WHEN OTHER
                           MOVE 'Y'                       TO WS-ERR-FLG
                           MOVE CCDA-MSG-INVALID-KEY      TO WS-MESSAGE
                           PERFORM SEND-USRDEL-SCREEN
                   END-EVALUATE
               END-IF
           END-IF

           EXEC CICS RETURN
                     TRANSID (WS-TRANID)
                     COMMAREA (CARDDEMO-COMMAREA)
           END-EXEC.

      *----------------------------------------------------------------*
      *                      PROCESS-ENTER-KEY
      *----------------------------------------------------------------*
       PROCESS-ENTER-KEY.

           EVALUATE TRUE
               WHEN USRIDINI OF COUSR3AI = SPACES OR LOW-VALUES
                   MOVE 'Y'     TO WS-ERR-FLG
                   MOVE 'User ID can NOT be empty...' TO
                                   WS-MESSAGE
                   MOVE -1       TO USRIDINL OF COUSR3AI
                   PERFORM SEND-USRDEL-SCREEN
               WHEN OTHER
                   MOVE -1       TO USRIDINL OF COUSR3AI
                   CONTINUE
           END-EVALUATE

           IF NOT ERR-FLG-ON
               MOVE SPACES      TO FNAMEI   OF COUSR3AI
                                   LNAMEI   OF COUSR3AI
                                   USRTYPEI OF COUSR3AI
               MOVE USRIDINI  OF COUSR3AI TO SEC-USR-ID
               PERFORM READ-USER-SEC-FILE
           END-IF.

           IF NOT ERR-FLG-ON
               MOVE SEC-USR-FNAME      TO FNAMEI    OF COUSR3AI
               MOVE SEC-USR-LNAME      TO LNAMEI    OF COUSR3AI
               MOVE SEC-USR-TYPE       TO USRTYPEI  OF COUSR3AI
               PERFORM SEND-USRDEL-SCREEN
           END-IF.

      *----------------------------------------------------------------*
      *                      DELETE-USER-INFO
      *----------------------------------------------------------------*
       DELETE-USER-INFO.

           IF NOT ADMIN-AUTHORIZED
               MOVE 'Y'     TO WS-ERR-FLG
               MOVE 'You are not authorized to delete users...' TO
                               WS-MESSAGE
               MOVE -1       TO USRIDINL OF COUSR3AI
               PERFORM SEND-USRDEL-SCREEN
           ELSE
               EVALUATE TRUE
                   WHEN USRIDINI OF COUSR3AI = SPACES OR LOW-VALUES
                       MOVE 'Y'     TO WS-ERR-FLG
                       MOVE 'User ID can NOT be empty...' TO
                                       WS-MESSAGE
                       MOVE -1       TO USRIDINL OF COUSR3AI
                       PERFORM SEND-USRDEL-SCREEN
                   WHEN OTHER
                       MOVE -1       TO USRIDINL OF COUSR3AI
                       CONTINUE
               END-EVALUATE
           END-IF

           IF NOT ERR-FLG-ON
               PERFORM CHECK-LAST-ADMIN-USER
           END-IF

           IF NOT ERR-FLG-ON
               MOVE USRIDINI  OF COUSR3AI TO SEC-USR-ID
               PERFORM READ-USER-SEC-FILE
               PERFORM DELETE-USER-SEC-FILE
           END-IF.

      *----------------------------------------------------------------*
      *                      VERIFY-ADMIN-AUTHORITY
      * Re-reads the signed on user from USRSEC rather than trusting
      * the user type carried in the COMMAREA. Callers that are not
      * administrators never reach the USRSEC read/delete logic.
      *----------------------------------------------------------------*
       VERIFY-ADMIN-AUTHORITY.

           SET ADMIN-NOT-AUTHORIZED TO TRUE
           MOVE SPACES              TO CDEMO-USER-TYPE

           IF CDEMO-USER-ID NOT = SPACES AND LOW-VALUES
               MOVE CDEMO-USER-ID TO SEC-USR-ID

               EXEC CICS READ
                    DATASET   (WS-USRSEC-FILE)
                    INTO      (SEC-USER-DATA)
                    LENGTH    (LENGTH OF SEC-USER-DATA)
                    RIDFLD    (SEC-USR-ID)
                    KEYLENGTH (LENGTH OF SEC-USR-ID)
                    RESP      (WS-RESP-CD)
                    RESP2     (WS-REAS-CD)
               END-EXEC

               IF WS-RESP-CD = DFHRESP(NORMAL)
                   MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
                   IF CDEMO-USRTYP-ADMIN
                       SET ADMIN-AUTHORIZED TO TRUE
                   END-IF
               END-IF

               MOVE LOW-VALUES TO SEC-USER-DATA
           END-IF

           IF NOT ADMIN-AUTHORIZED
               IF CDEMO-USRTYP-USER
                   MOVE 'COMEN01C' TO CDEMO-TO-PROGRAM
               ELSE
                   MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM
               END-IF
               PERFORM RETURN-TO-PREV-SCREEN
           END-IF.

      *----------------------------------------------------------------*
      *                      CHECK-LAST-ADMIN-USER
      * Refuses the delete when the selected user is the only
      * remaining administrator, which would leave the application
      * without any user management access.
      *----------------------------------------------------------------*
       CHECK-LAST-ADMIN-USER.

           MOVE USRIDINI OF COUSR3AI TO WS-TARGET-USR-ID
           MOVE SPACES               TO WS-TARGET-USR-TYPE
           MOVE ZEROS                TO WS-ADMIN-COUNT
           SET USER-SEC-NOT-EOF      TO TRUE

           MOVE LOW-VALUES TO SEC-USR-ID

           EXEC CICS STARTBR
                DATASET   (WS-USRSEC-FILE)
                RIDFLD    (SEC-USR-ID)
                KEYLENGTH (LENGTH OF SEC-USR-ID)
                GTEQ
                RESP      (WS-RESP-CD)
                RESP2     (WS-REAS-CD)
           END-EXEC

           IF WS-RESP-CD NOT = DFHRESP(NORMAL)
               DISPLAY 'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD
               MOVE 'Y'     TO WS-ERR-FLG
               MOVE 'Unable to verify admin users...' TO
                               WS-MESSAGE
           ELSE
               PERFORM UNTIL USER-SEC-EOF OR
                       (WS-ADMIN-COUNT > 1 AND
                        WS-TARGET-USR-TYPE NOT = SPACES)
                   EXEC CICS READNEXT
                        DATASET   (WS-USRSEC-FILE)
                        INTO      (SEC-USER-DATA)
                        LENGTH    (LENGTH OF SEC-USER-DATA)
                        RIDFLD    (SEC-USR-ID)
                        KEYLENGTH (LENGTH OF SEC-USR-ID)
                        RESP      (WS-RESP-CD)
                        RESP2     (WS-REAS-CD)
                   END-EXEC

                   EVALUATE WS-RESP-CD
                       WHEN DFHRESP(NORMAL)
                           IF SEC-USR-TYPE = 'A'
                               ADD 1 TO WS-ADMIN-COUNT
                           END-IF
                           IF SEC-USR-ID = WS-TARGET-USR-ID
                               MOVE SEC-USR-TYPE TO WS-TARGET-USR-TYPE
                           END-IF
                       WHEN DFHRESP(ENDFILE)
                           SET USER-SEC-EOF TO TRUE
                       WHEN OTHER
                           DISPLAY 'RESP:' WS-RESP-CD
                                   'REAS:' WS-REAS-CD
                           SET USER-SEC-EOF TO TRUE
                           MOVE 'Y'     TO WS-ERR-FLG
                           MOVE 'Unable to verify admin users...' TO
                                           WS-MESSAGE
                   END-EVALUATE
               END-PERFORM

               EXEC CICS ENDBR
                    DATASET   (WS-USRSEC-FILE)
                    RESP      (WS-RESP-CD)
                    RESP2     (WS-REAS-CD)
               END-EXEC
           END-IF

           MOVE LOW-VALUES       TO SEC-USER-DATA
           MOVE WS-TARGET-USR-ID TO SEC-USR-ID

           IF NOT ERR-FLG-ON
               IF WS-TARGET-USR-TYPE = 'A' AND WS-ADMIN-COUNT < 2
                   MOVE 'Y'     TO WS-ERR-FLG
                   MOVE 'Cannot delete the last admin user...' TO
                                   WS-MESSAGE
               END-IF
           END-IF

           IF ERR-FLG-ON
               MOVE -1       TO USRIDINL OF COUSR3AI
               PERFORM SEND-USRDEL-SCREEN
           END-IF.

      *----------------------------------------------------------------*
      *                      RETURN-TO-PREV-SCREEN
      *----------------------------------------------------------------*
       RETURN-TO-PREV-SCREEN.

           IF CDEMO-TO-PROGRAM = LOW-VALUES OR SPACES
               MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM
           END-IF
           MOVE WS-TRANID    TO CDEMO-FROM-TRANID
           MOVE WS-PGMNAME   TO CDEMO-FROM-PROGRAM
           MOVE ZEROS        TO CDEMO-PGM-CONTEXT
           EXEC CICS
               XCTL PROGRAM(CDEMO-TO-PROGRAM)
               COMMAREA(CARDDEMO-COMMAREA)
           END-EXEC.

      *----------------------------------------------------------------*
      *                      SEND-USRDEL-SCREEN
      *----------------------------------------------------------------*
       SEND-USRDEL-SCREEN.

           PERFORM POPULATE-HEADER-INFO

           MOVE WS-MESSAGE TO ERRMSGO OF COUSR3AO

           EXEC CICS SEND
                     MAP('COUSR3A')
                     MAPSET('COUSR03')
                     FROM(COUSR3AO)
                     ERASE
                     CURSOR
           END-EXEC.

      *----------------------------------------------------------------*
      *                      RECEIVE-USRDEL-SCREEN
      *----------------------------------------------------------------*
       RECEIVE-USRDEL-SCREEN.

           EXEC CICS RECEIVE
                     MAP('COUSR3A')
                     MAPSET('COUSR03')
                     INTO(COUSR3AI)
                     RESP(WS-RESP-CD)
                     RESP2(WS-REAS-CD)
           END-EXEC.

      *----------------------------------------------------------------*
      *                      POPULATE-HEADER-INFO
      *----------------------------------------------------------------*
       POPULATE-HEADER-INFO.

           MOVE FUNCTION CURRENT-DATE  TO WS-CURDATE-DATA

           MOVE CCDA-TITLE01           TO TITLE01O OF COUSR3AO
           MOVE CCDA-TITLE02           TO TITLE02O OF COUSR3AO
           MOVE WS-TRANID              TO TRNNAMEO OF COUSR3AO
           MOVE WS-PGMNAME             TO PGMNAMEO OF COUSR3AO

           MOVE WS-CURDATE-MONTH       TO WS-CURDATE-MM
           MOVE WS-CURDATE-DAY         TO WS-CURDATE-DD
           MOVE WS-CURDATE-YEAR(3:2)   TO WS-CURDATE-YY

           MOVE WS-CURDATE-MM-DD-YY    TO CURDATEO OF COUSR3AO

           MOVE WS-CURTIME-HOURS       TO WS-CURTIME-HH
           MOVE WS-CURTIME-MINUTE      TO WS-CURTIME-MM
           MOVE WS-CURTIME-SECOND      TO WS-CURTIME-SS

           MOVE WS-CURTIME-HH-MM-SS    TO CURTIMEO OF COUSR3AO.

      *----------------------------------------------------------------*
      *                      READ-USER-SEC-FILE
      *----------------------------------------------------------------*
       READ-USER-SEC-FILE.

           EXEC CICS READ
                DATASET   (WS-USRSEC-FILE)
                INTO      (SEC-USER-DATA)
                LENGTH    (LENGTH OF SEC-USER-DATA)
                RIDFLD    (SEC-USR-ID)
                KEYLENGTH (LENGTH OF SEC-USR-ID)
                UPDATE
                RESP      (WS-RESP-CD)
                RESP2     (WS-REAS-CD)
           END-EXEC.

           EVALUATE WS-RESP-CD
               WHEN DFHRESP(NORMAL)
                   CONTINUE
                   MOVE 'Press PF5 key to delete this user ...' TO
                                   WS-MESSAGE
                   MOVE DFHNEUTR       TO ERRMSGC  OF COUSR3AO
                   PERFORM SEND-USRDEL-SCREEN
               WHEN DFHRESP(NOTFND)
                   MOVE 'Y'     TO WS-ERR-FLG
                   MOVE 'User ID NOT found...' TO
                                   WS-MESSAGE
                   MOVE -1       TO USRIDINL OF COUSR3AI
                   PERFORM SEND-USRDEL-SCREEN
               WHEN OTHER
                   DISPLAY 'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD
                   MOVE 'Y'     TO WS-ERR-FLG
                   MOVE 'Unable to lookup User...' TO
                                   WS-MESSAGE
                   MOVE -1       TO FNAMEL OF COUSR3AI
                   PERFORM SEND-USRDEL-SCREEN
           END-EVALUATE.

      *----------------------------------------------------------------*
      *                      DELETE-USER-SEC-FILE
      *----------------------------------------------------------------*
       DELETE-USER-SEC-FILE.

           EXEC CICS DELETE
                DATASET   (WS-USRSEC-FILE)
                RESP      (WS-RESP-CD)
                RESP2     (WS-REAS-CD)
           END-EXEC.

           EVALUATE WS-RESP-CD
               WHEN DFHRESP(NORMAL)
                   PERFORM INITIALIZE-ALL-FIELDS
                   MOVE SPACES             TO WS-MESSAGE
                   MOVE DFHGREEN           TO ERRMSGC  OF COUSR3AO
                   STRING 'User '     DELIMITED BY SIZE
                          SEC-USR-ID  DELIMITED BY SPACE
                          ' has been deleted ...' DELIMITED BY SIZE
                     INTO WS-MESSAGE
                   PERFORM SEND-USRDEL-SCREEN
               WHEN DFHRESP(NOTFND)
                   MOVE 'Y'     TO WS-ERR-FLG
                   MOVE 'User ID NOT found...' TO
                                   WS-MESSAGE
                   MOVE -1       TO USRIDINL OF COUSR3AI
                   PERFORM SEND-USRDEL-SCREEN
               WHEN OTHER
                   DISPLAY 'RESP:' WS-RESP-CD 'REAS:' WS-REAS-CD
                   MOVE 'Y'     TO WS-ERR-FLG
                   MOVE 'Unable to Update User...' TO
                                   WS-MESSAGE
                   MOVE -1       TO FNAMEL OF COUSR3AI
                   PERFORM SEND-USRDEL-SCREEN
           END-EVALUATE.

      *----------------------------------------------------------------*
      *                      CLEAR-CURRENT-SCREEN
      *----------------------------------------------------------------*
       CLEAR-CURRENT-SCREEN.

           PERFORM INITIALIZE-ALL-FIELDS.
           PERFORM SEND-USRDEL-SCREEN.

      *----------------------------------------------------------------*
      *                      INITIALIZE-ALL-FIELDS
      *----------------------------------------------------------------*
       INITIALIZE-ALL-FIELDS.

           MOVE -1              TO USRIDINL OF COUSR3AI
           MOVE SPACES          TO USRIDINI OF COUSR3AI
                                   FNAMEI   OF COUSR3AI
                                   LNAMEI   OF COUSR3AI
                                   USRTYPEI OF COUSR3AI
                                   WS-MESSAGE.
      *
      * Ver: CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19 23:12:35 CDT
      *
