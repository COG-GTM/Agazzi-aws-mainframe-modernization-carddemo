       IDENTIFICATION DIVISION.
       PROGRAM-ID. MVSWAIT.
      * Golden-harness stub for app/asm/MVSWAIT.asm (STIMER in 1/100 s).
      * GOLDEN_NOWAIT=Y skips the real sleep to keep golden runs fast.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-NOWAIT               PIC X VALUE SPACE.
       01  WS-SECONDS              PIC 9(8) COMP.
       LINKAGE SECTION.
       01  MVSWAIT-TIME            PIC 9(8) COMP.
       PROCEDURE DIVISION USING MVSWAIT-TIME.
           ACCEPT WS-NOWAIT FROM ENVIRONMENT 'GOLDEN_NOWAIT'
           DISPLAY 'MVSWAIT CENTISECONDS ' MVSWAIT-TIME
           IF WS-NOWAIT NOT = 'Y'
               COMPUTE WS-SECONDS = MVSWAIT-TIME / 100
               CALL 'C$SLEEP' USING WS-SECONDS
           END-IF
           GOBACK.
