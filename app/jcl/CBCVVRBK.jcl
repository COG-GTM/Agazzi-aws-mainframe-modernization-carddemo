//CBCVVRBK JOB 'Rollback 4-digit CVVs to 3 digits',CLASS=A,MSGCLASS=0,
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
//******************************************************************
//*********************************************************************
//* CVV DATA ROLLBACK - CONVERT 4-DIGIT CVV RECORDS BACK TO 3-DIGIT
//*
//* This job reverses the CVV migration performed by CBCVVMIG.
//* It reads the migrated CARDDATA sequential backup and produces
//* a sequential file with the original 3-digit CVV layout.
//*
//* Steps:
//*   STEP01 - REPRO migrated VSAM KSDS to sequential backup
//*   STEP02 - Run CBCVVRBK to convert 4-digit CVVs back to 3-digit
//*   STEP03 - (Manual) REPRO the rolled-back sequential file
//*            back into the VSAM cluster after redefining it
//*            with the original layout via ROLLCARD.jcl
//*
//* WARNING: Records with genuine 4-digit CVVs (non-zero leading
//*          digit) will have data truncated. Review the program
//*          output for affected card numbers before proceeding.
//*********************************************************************
//*
//* *******************************************************************
//* STEP 1: UNLOAD MIGRATED CARDDATA VSAM TO SEQUENTIAL FILE
//* *******************************************************************
//STEP01 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  REPRO INDATASET(AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS) -
        OUTDATASET(AWS.M2.CARDDEMO.CARDDATA.MIGRATED.SEQ)
/*
//*
//* *******************************************************************
//* STEP 2: RUN CVV ROLLBACK PROGRAM
//* *******************************************************************
//STEP02 EXEC PGM=CBCVVRBK,COND=(4,LT)
//STEPLIB  DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.LOADLIB
//*
//* INPUT - MIGRATED 4-DIGIT CVV LAYOUT (150-BYTE FIXED RECORDS)
//*
//CVVRBKIN DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.CARDDATA.MIGRATED.SEQ
//*
//* OUTPUT - ORIGINAL 3-DIGIT CVV LAYOUT (150-BYTE FIXED RECORDS)
//*
//CVVRBKOT DD DSN=AWS.M2.CARDDEMO.CARDDATA.ROLLBACK.SEQ,
//            DISP=(NEW,CATLG,DELETE),
//            UNIT=SYSDA,
//            SPACE=(CYL,(10,5),RLSE),
//            DCB=(RECFM=FB,LRECL=150,BLKSIZE=0)
//*
//SYSOUT   DD SYSOUT=*
//SYSPRINT DD SYSOUT=*
//*
