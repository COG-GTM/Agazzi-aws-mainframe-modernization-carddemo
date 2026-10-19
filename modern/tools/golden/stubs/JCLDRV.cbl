       IDENTIFICATION DIVISION.
       PROGRAM-ID. JCLDRV.
      * Golden-harness driver: emulates EXEC PGM=<pgm>,PARM='<parm>'.
      * Usage: jcldrv <PGM> [PARM]
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-PGM                  PIC X(8)   VALUE SPACES.
       01  WS-PARM-AREA.
           05  WS-PARM-LEN         PIC S9(4) COMP VALUE 0.
           05  WS-PARM-TEXT        PIC X(100) VALUE SPACES.
       PROCEDURE DIVISION.
           ACCEPT WS-PGM FROM ARGUMENT-VALUE
           ACCEPT WS-PARM-TEXT FROM ARGUMENT-VALUE
               ON EXCEPTION MOVE SPACES TO WS-PARM-TEXT
           END-ACCEPT
           IF WS-PARM-TEXT NOT = SPACES
               COMPUTE WS-PARM-LEN =
                   FUNCTION LENGTH(FUNCTION TRIM(WS-PARM-TEXT TRAILING))
           END-IF
           CALL WS-PGM USING WS-PARM-AREA
           STOP RUN.
