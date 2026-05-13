//CBCVVMIG JOB 'Migrate 3-digit CVVs to 4 digits',CLASS=A,MSGCLASS=0,
// NOTIFY=&SYSUID
//******************************************************************
//* Copyright Amazon.com, Inc. or its affiliates.
//* All Rights Reserved.
//*
//* Licensed under the Apache License, Version 2.0 (the "License").
//* You may not use this file except in compliance with the License.
//* You may obtain a copy of the License at
//*
//*    http://www.apache.org/licenses/LICENSE-2.0
//*
//* Unless required by applicable law or agreed to in writing,
//* software distributed under the License is distributed on an
//* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
//* either express or implied. See the License for the specific
//* language governing permissions and limitations under the License
//* *******************************************************************
//* ONE-TIME MIGRATION OF CARD DATA FROM 3-DIGIT CVV LAYOUT TO THE
//* NEW 4-DIGIT CVV LAYOUT.
//*
//* This job is intended to be run once, during a maintenance window,
//* against a sequential copy of the production CARDDATA file:
//*
//*   STEP01 - REPRO the live VSAM KSDS to a sequential backup
//*            (legacy 3-digit CVV layout, 150-byte fixed records).
//*   STEP02 - Execute CBCVVMIG which reads the backup and writes a
//*            new sequential dataset with 4-digit CVVs.
//*   STEP03 - Operators then REPRO the migrated sequential file
//*            back into the (recreated) VSAM cluster.
//*            STEP03 is intentionally left as a manual step so the
//*            production cutover happens under change control.
//* *******************************************************************
//* STEP 1: UNLOAD CARDDATA VSAM TO A SEQUENTIAL FILE (LEGACY LAYOUT)
//* *******************************************************************
//STEP01 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  REPRO INDATASET(AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS) -
        OUTDATASET(AWS.M2.CARDDEMO.CARDDATA.BACKUP.SEQ)
/*
//* *******************************************************************
//* STEP 2: RUN MIGRATION PROGRAM
//* *******************************************************************
//STEP02 EXEC PGM=CBCVVMIG
//STEPLIB  DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.LOADLIB
//*
//* INPUT - LEGACY 3-DIGIT CVV LAYOUT (150-BYTE FIXED RECORDS)
//*
//CVVMIGIN DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.CARDDATA.BACKUP.SEQ
//*
//* OUTPUT - NEW 4-DIGIT CVV LAYOUT (150-BYTE FIXED RECORDS)
//*
//CVVMIGOT DD DSN=AWS.M2.CARDDEMO.CARDDATA.MIGRATED.SEQ,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,
//            SPACE=(CYL,(10,5),RLSE),
//            DCB=(RECFM=FB,LRECL=150,BLKSIZE=0)
//*
//SYSOUT   DD SYSOUT=*
//SYSPRINT DD SYSOUT=*
//*
