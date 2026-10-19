//USRSECPW JOB 'SET USRSEC PWD',REGION=8M,CLASS=A,
//      MSGCLASS=H,NOTIFY=&SYSUID
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
//*-------------------------------------------------------------------*
//* ENABLE CARDDEMO SIGN-ON ACCOUNTS
//*
//* DUSRSECJ SEEDS EVERY ACCOUNT WITH A BLANK PASSWORD, WHICH COSGN00C
//* TREATS AS DISABLED. THIS JOB LOADS THE PASSWORDS THAT THE OPERATOR
//* SUPPLIES OUT OF BAND IN AWS.M2.CARDDEMO.USRSEC.PWD, A RECFM=FB
//* LRECL=80 DATASET IN THE CSUSR01Y RECORD LAYOUT:
//*
//*   COL  1-8   USER ID
//*   COL  9-28  FIRST NAME
//*   COL 29-48  LAST NAME
//*   COL 49-56  PASSWORD, UNIQUE PER ACCOUNT. COSGN00C REFUSES THE
//*              PUBLISHED DEFAULTS ('PASSWORD', '*LOCKED*') AND BLANKS
//*   COL 57     USER TYPE, A FOR ADMIN OR U FOR REGULAR USER
//*
//* CREATE THAT DATASET ON THE TARGET SYSTEM ONLY, PROTECT IT WITH THE
//* EXTERNAL SECURITY MANAGER AND NEVER COMMIT IT TO SOURCE CONTROL.
//* STEP02 DELETES IT ONCE THE RECORDS HAVE BEEN LOADED.
//*
//* CLOSE THE USRSEC FILE IN CICS (JOB CLOSEFIL) BEFORE RUNNING THIS
//* JOB AND REOPEN IT AFTERWARDS (JOB OPENFIL).
//*-------------------------------------------------------------------*
//*
//*-------------------------------------------------------------------*
//* REPLACE THE SEEDED RECORDS WITH THE OPERATOR SUPPLIED ONES
//*-------------------------------------------------------------------*
//*
//STEP01  EXEC PGM=IDCAMS
//*
//IN       DD  DSN=AWS.M2.CARDDEMO.USRSEC.PWD,DISP=SHR
//OUT      DD  DSN=AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS,DISP=SHR
//SYSOUT   DD  SYSOUT=*
//SYSPRINT DD  SYSOUT=*
//SYSIN    DD  *
  REPRO INFILE(IN) OUTFILE(OUT) REPLACE
/*
//*
//*-------------------------------------------------------------------*
//* DELETE THE CLEARTEXT INPUT ONCE IT HAS BEEN LOADED
//*-------------------------------------------------------------------*
//*
//STEP02  EXEC PGM=IEFBR14,COND=(0,LT)
//*
//DD01     DD  DSN=AWS.M2.CARDDEMO.USRSEC.PWD,
//             DISP=(OLD,DELETE,DELETE)
//*
