      *================================================================*
      * DTCDRV - golden-harness driver for CSUTLDTC (1c-platform).      *
      * Reads CASES (date X(10) + mask X(10)), CALLs the original      *
      * app/cbl/CSUTLDTC and writes date, mask, LS-RESULT, RETURN-CODE. *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. DTCDRV.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT CASES-FILE ASSIGN TO CASES
                  ORGANIZATION IS SEQUENTIAL.
           SELECT RESULT-FILE ASSIGN TO RESULTS
                  ORGANIZATION IS SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD  CASES-FILE.
       01  CASE-REC.
           05 CASE-DATE              PIC X(10).
           05 CASE-FMT               PIC X(10).
       FD  RESULT-FILE.
       01  RESULT-REC.
           05 OUT-DATE               PIC X(10).
           05 OUT-FMT                PIC X(10).
           05 OUT-RESULT             PIC X(80).
           05 OUT-RC                 PIC 9(04).
       WORKING-STORAGE SECTION.
       01  WS-EOF                    PIC X(01) VALUE 'N'.
       01  WS-COUNT                  PIC 9(04) VALUE 0.
       01  WS-DATE                   PIC X(10).
       01  WS-FMT                    PIC X(10).
       01  WS-RESULT                 PIC X(80).
       PROCEDURE DIVISION.
           OPEN INPUT CASES-FILE
                OUTPUT RESULT-FILE
           PERFORM UNTIL WS-EOF = 'Y'
              READ CASES-FILE
                 AT END MOVE 'Y' TO WS-EOF
                 NOT AT END PERFORM CHECK-CASE
              END-READ
           END-PERFORM
           CLOSE CASES-FILE RESULT-FILE
           DISPLAY 'DTCDRV CASES CHECKED: ' WS-COUNT
           MOVE 0 TO RETURN-CODE
           GOBACK.
       CHECK-CASE.
           MOVE CASE-DATE TO WS-DATE
           MOVE CASE-FMT  TO WS-FMT
           MOVE SPACES    TO WS-RESULT
           CALL 'CSUTLDTC' USING WS-DATE WS-FMT WS-RESULT
           MOVE CASE-DATE   TO OUT-DATE
           MOVE CASE-FMT    TO OUT-FMT
           MOVE WS-RESULT   TO OUT-RESULT
           MOVE RETURN-CODE TO OUT-RC
           WRITE RESULT-REC
           ADD 1 TO WS-COUNT.
