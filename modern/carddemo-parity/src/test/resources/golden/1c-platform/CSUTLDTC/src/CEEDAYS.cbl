      *================================================================*
      * CEEDAYS - golden-harness emulation of the LE callable service  *
      * (1c-platform). Same check order as                             *
      * com.carddemo.platform.time.CeeDays: picture, length,           *
      * digits/separators, month, day, Gregorian range.                *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEEDAYS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-D                      PIC X(256).
       01  WS-P                      PIC X(256).
       01  WS-DL                     PIC 9(04) COMP.
       01  WS-PL                     PIC 9(04) COMP.
       01  WS-I                      PIC 9(04) COMP.
       01  WS-K                      PIC 9(04) COMP.
       01  WS-NT                     PIC 9(04) COMP.
       01  WS-OFF                    PIC 9(04) COMP.
       01  WS-LEN                    PIC 9(04) COMP.
       01  WS-MSG                    PIC 9(04) VALUE 0.
       01  WS-TOKS.
           05 WS-TOK OCCURS 256 TIMES.
              10 WS-T-KIND           PIC X(01).
              10 WS-T-OFF            PIC 9(04) COMP.
              10 WS-T-LEN            PIC 9(04) COMP.
       01  WS-KIND                   PIC X(01).
       01  WS-HAS-Y                  PIC X(01).
       01  WS-HAS-M                  PIC X(01).
       01  WS-HAS-D                  PIC X(01).
       01  WS-HAS-J                  PIC X(01).
       01  WS-YY2                    PIC X(01).
       01  WS-Y                      PIC S9(05).
       01  WS-M                      PIC S9(05).
       01  WS-DD                     PIC S9(05).
       01  WS-J                      PIC S9(05).
       01  WS-NUM                    PIC 9(04).
       01  WS-MAXD                   PIC 9(03).
       01  WS-TODAY                  PIC 9(08).
       01  WS-START                  PIC 9(04).
       01  WS-CENT                   PIC 9(02).
       01  WS-LEAP                   PIC X(01).
       LINKAGE SECTION.
       01  LS-DATE.
           02 LS-DATE-LEN            PIC S9(04) BINARY.
           02 LS-DATE-TXT            PIC X(256).
       01  LS-PIC.
           02 LS-PIC-LEN             PIC S9(04) BINARY.
           02 LS-PIC-TXT             PIC X(256).
       01  LS-LILLIAN                PIC S9(09) BINARY.
       01  LS-FC.
           02 LS-SEV                 PIC S9(04) BINARY.
           02 LS-MSGNO               PIC S9(04) BINARY.
           02 LS-CTL-FAC             PIC X(04).
           02 LS-ISINFO              PIC S9(09) BINARY.
       PROCEDURE DIVISION USING LS-DATE LS-PIC LS-LILLIAN LS-FC.
           MOVE 0 TO LS-LILLIAN LS-SEV LS-MSGNO LS-ISINFO WS-MSG WS-NT
           MOVE LOW-VALUES TO LS-CTL-FAC
           MOVE SPACES TO WS-D WS-P
           MOVE 'N' TO WS-HAS-Y WS-HAS-M WS-HAS-D WS-HAS-J WS-YY2
           MOVE -1 TO WS-Y WS-M WS-DD WS-J
           IF LS-DATE-LEN > 0
              MOVE LS-DATE-TXT(1:LS-DATE-LEN) TO WS-D
           END-IF
           IF LS-PIC-LEN > 0
              MOVE LS-PIC-TXT(1:LS-PIC-LEN) TO WS-P
           END-IF
           INSPECT WS-D REPLACING ALL LOW-VALUE BY SPACE
           INSPECT WS-P REPLACING ALL LOW-VALUE BY SPACE
           PERFORM VARYING WS-DL FROM 256 BY -1
                   UNTIL WS-DL = 0 OR WS-D(WS-DL:1) NOT = SPACE
           END-PERFORM
           PERFORM VARYING WS-PL FROM 256 BY -1
                   UNTIL WS-PL = 0 OR WS-P(WS-PL:1) NOT = SPACE
           END-PERFORM
           PERFORM PARSE-PICTURE
           IF WS-MSG = 0 AND WS-DL < WS-PL
              MOVE 2507 TO WS-MSG
           END-IF
           IF WS-MSG = 0
              PERFORM EXTRACT-FIELDS
           END-IF
           IF WS-MSG = 0
              PERFORM CHECK-CALENDAR
           END-IF
           IF WS-MSG NOT = 0
              MOVE 3        TO LS-SEV
              MOVE WS-MSG   TO LS-MSGNO
              MOVE X'59C3C5C5' TO LS-CTL-FAC
              MOVE 0        TO LS-LILLIAN
           END-IF
           GOBACK.

       PARSE-PICTURE.
           IF WS-PL = 0
              MOVE 2518 TO WS-MSG
           END-IF
           MOVE 1 TO WS-I
           PERFORM UNTIL WS-I > WS-PL OR WS-MSG NOT = 0
              EVALUATE TRUE
                 WHEN WS-P(WS-I:4) = 'YYYY'
                    MOVE 'Y' TO WS-KIND
                    MOVE 4 TO WS-LEN
                 WHEN WS-P(WS-I:2) = 'YY'
                    MOVE 'y' TO WS-KIND
                    MOVE 2 TO WS-LEN
                 WHEN WS-P(WS-I:2) = 'MM'
                    MOVE 'M' TO WS-KIND
                    MOVE 2 TO WS-LEN
                 WHEN WS-P(WS-I:3) = 'DDD'
                    MOVE 'J' TO WS-KIND
                    MOVE 3 TO WS-LEN
                 WHEN WS-P(WS-I:2) = 'DD'
                    MOVE 'D' TO WS-KIND
                    MOVE 2 TO WS-LEN
                 WHEN WS-P(WS-I:1) IS ALPHABETIC
                      AND WS-P(WS-I:1) NOT = SPACE
                    MOVE 2518 TO WS-MSG
                 WHEN OTHER
                    MOVE 'L' TO WS-KIND
                    MOVE 1 TO WS-LEN
              END-EVALUATE
              IF WS-MSG = 0
                 EVALUATE WS-KIND
                    WHEN 'Y'
                    WHEN 'y'
                       IF WS-HAS-Y = 'Y' MOVE 2518 TO WS-MSG END-IF
                       MOVE 'Y' TO WS-HAS-Y
                    WHEN 'M'
                       IF WS-HAS-M = 'Y' MOVE 2518 TO WS-MSG END-IF
                       MOVE 'Y' TO WS-HAS-M
                    WHEN 'D'
                       IF WS-HAS-D = 'Y' MOVE 2518 TO WS-MSG END-IF
                       MOVE 'Y' TO WS-HAS-D
                    WHEN 'J'
                       IF WS-HAS-J = 'Y' MOVE 2518 TO WS-MSG END-IF
                       MOVE 'Y' TO WS-HAS-J
                 END-EVALUATE
                 ADD 1 TO WS-NT
                 MOVE WS-KIND TO WS-T-KIND(WS-NT)
                 MOVE WS-I    TO WS-T-OFF(WS-NT)
                 MOVE WS-LEN  TO WS-T-LEN(WS-NT)
                 ADD WS-LEN TO WS-I
              END-IF
           END-PERFORM
           IF WS-MSG = 0
              IF WS-HAS-Y NOT = 'Y'
                 MOVE 2518 TO WS-MSG
              ELSE
                 IF WS-HAS-J = 'Y'
                    IF WS-HAS-M = 'Y' OR WS-HAS-D = 'Y'
                       MOVE 2518 TO WS-MSG
                    END-IF
                 ELSE
                    IF WS-HAS-M NOT = 'Y' OR WS-HAS-D NOT = 'Y'
                       MOVE 2518 TO WS-MSG
                    END-IF
                 END-IF
              END-IF
           END-IF.

       EXTRACT-FIELDS.
           PERFORM VARYING WS-K FROM 1 BY 1
                   UNTIL WS-K > WS-NT OR WS-MSG NOT = 0
              MOVE WS-T-OFF(WS-K) TO WS-OFF
              MOVE WS-T-LEN(WS-K) TO WS-LEN
              IF WS-T-KIND(WS-K) = 'L'
                 IF WS-D(WS-OFF:1) NOT = WS-P(WS-OFF:1)
                    MOVE 2520 TO WS-MSG
                 END-IF
              ELSE
                 IF WS-D(WS-OFF:WS-LEN) IS NOT NUMERIC
                    MOVE 2520 TO WS-MSG
                 ELSE
                    COMPUTE WS-NUM =
                       FUNCTION NUMVAL(WS-D(WS-OFF:WS-LEN))
                    EVALUATE WS-T-KIND(WS-K)
                       WHEN 'Y' MOVE WS-NUM TO WS-Y
                       WHEN 'y' MOVE WS-NUM TO WS-Y
                                MOVE 'Y' TO WS-YY2
                       WHEN 'M' MOVE WS-NUM TO WS-M
                       WHEN 'D' MOVE WS-NUM TO WS-DD
                       WHEN 'J' MOVE WS-NUM TO WS-J
                    END-EVALUATE
                 END-IF
              END-IF
           END-PERFORM.

       CHECK-CALENDAR.
           IF WS-YY2 = 'Y'
              ACCEPT WS-TODAY FROM DATE YYYYMMDD
              COMPUTE WS-START = WS-TODAY / 10000 - 80
              COMPUTE WS-CENT = WS-START / 100
              COMPUTE WS-Y = WS-CENT * 100 + WS-Y
              IF WS-Y < WS-START
                 ADD 100 TO WS-Y
              END-IF
           END-IF
           MOVE 'N' TO WS-LEAP
           IF (FUNCTION MOD(WS-Y, 4) = 0 AND
               FUNCTION MOD(WS-Y, 100) NOT = 0)
              OR FUNCTION MOD(WS-Y, 400) = 0
              MOVE 'Y' TO WS-LEAP
           END-IF
           IF WS-J < 0 AND (WS-M < 1 OR WS-M > 12)
              MOVE 2517 TO WS-MSG
           END-IF
           IF WS-MSG = 0
              IF WS-J >= 0
                 MOVE 365 TO WS-MAXD
                 IF WS-LEAP = 'Y' MOVE 366 TO WS-MAXD END-IF
                 IF WS-J < 1 OR WS-J > WS-MAXD
                    MOVE 2508 TO WS-MSG
                 END-IF
              ELSE
                 EVALUATE WS-M
                    WHEN 2
                       MOVE 28 TO WS-MAXD
                       IF WS-LEAP = 'Y' MOVE 29 TO WS-MAXD END-IF
                    WHEN 4 WHEN 6 WHEN 9 WHEN 11
                       MOVE 30 TO WS-MAXD
                    WHEN OTHER
                       MOVE 31 TO WS-MAXD
                 END-EVALUATE
                 IF WS-DD < 1 OR WS-DD > WS-MAXD
                    MOVE 2508 TO WS-MSG
                 END-IF
              END-IF
           END-IF
           IF WS-MSG = 0
              IF WS-Y < 1582
                 MOVE 2513 TO WS-MSG
              END-IF
              IF WS-Y = 1582
                 IF (WS-J >= 0 AND WS-J < 288) OR
                    (WS-J < 0 AND (WS-M < 10 OR
                                   (WS-M = 10 AND WS-DD < 15)))
                    MOVE 2513 TO WS-MSG
                 END-IF
              END-IF
           END-IF
      *    Lillian = INTEGER-OF-DATE + 6653 (from 1601); not part of
      *    CSUTLDTC LS-RESULT, so 1582-1600 is left to the unit tests.
           IF WS-MSG = 0 AND WS-Y >= 1601
              IF WS-J >= 0
                 COMPUTE LS-LILLIAN =
                    FUNCTION INTEGER-OF-DAY(WS-Y * 1000 + WS-J) + 6653
              ELSE
                 COMPUTE LS-LILLIAN = FUNCTION INTEGER-OF-DATE(
                    WS-Y * 10000 + WS-M * 100 + WS-DD) + 6653
              END-IF
           END-IF.
