       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
      * Golden-harness stub for LE CEE3ABD: abend -> exit code 16.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-CODE                 PIC -(9)9.
       LINKAGE SECTION.
       01  ABCODE                  PIC S9(9) BINARY.
       01  TIMING                  PIC S9(9) BINARY.
       PROCEDURE DIVISION USING ABCODE TIMING.
           IF ADDRESS OF ABCODE NOT = NULL
               MOVE ABCODE TO WS-CODE
           ELSE
               MOVE 0 TO WS-CODE
           END-IF
           DISPLAY 'CEE3ABD ABEND U' FUNCTION TRIM(WS-CODE)
           MOVE 16 TO RETURN-CODE
           STOP RUN.
