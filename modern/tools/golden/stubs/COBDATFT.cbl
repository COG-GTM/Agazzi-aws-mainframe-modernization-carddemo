       IDENTIFICATION DIVISION.
       PROGRAM-ID. COBDATFT.
      * Golden-harness COBOL port of app/asm/COBDATFT.asm.
       DATA DIVISION.
       LINKAGE SECTION.
       01  CO-REC.
           05  CO-INTYPE           PIC X.
           05  CO-INPDT            PIC X(20).
           05  CO-OUTTYPE          PIC X.
           05  CO-OUTDT            PIC X(20).
           05  CO-ERRMSG           PIC X(38).
       PROCEDURE DIVISION USING CO-REC.
           EVALUATE CO-INTYPE
             WHEN '1'
               IF CO-INPDT(5:1) = '-' OR CO-OUTTYPE = '2'
                   MOVE 'INVALID INPUT' TO CO-ERRMSG
               ELSE
                   MOVE CO-INPDT(1:4) TO CO-OUTDT(1:4)
                   MOVE '-'           TO CO-OUTDT(5:1)
                   MOVE CO-INPDT(5:2) TO CO-OUTDT(6:2)
                   MOVE '-'           TO CO-OUTDT(8:1)
                   MOVE CO-INPDT(7:2) TO CO-OUTDT(9:2)
               END-IF
             WHEN '2'
               IF CO-OUTTYPE = '1'
                   MOVE 'INVALID INPUT' TO CO-ERRMSG
               ELSE
                   MOVE CO-INPDT(1:4)  TO CO-OUTDT(1:4)
                   MOVE CO-INPDT(6:2)  TO CO-OUTDT(5:2)
                   MOVE CO-INPDT(9:2)  TO CO-OUTDT(7:2)
               END-IF
             WHEN OTHER
               MOVE 'INVALID INPUT' TO CO-ERRMSG
           END-EVALUATE
           MOVE 0 TO RETURN-CODE
           GOBACK.
