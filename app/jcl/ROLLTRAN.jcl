//ROLLTRAN JOB 'Emergency Rollback Transaction Data',CLASS=A,MSGCLASS=0,
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
//* EMERGENCY ROLLBACK JCL FOR TRANSACTION DATA (TRANSACT.VSAM.KSDS)
//*
//* This job restores the TRANSACT VSAM cluster from a GDG backup.
//* Based on TRANBKP.jcl pattern.
//*
//* Steps:
//*   STEP01 - Backup current (migrated) transaction data to GDG
//*   STEP02 - Delete current VSAM cluster
//*   STEP03 - Redefine VSAM cluster with original parameters
//*   STEP04 - Restore data from pre-migration GDG backup
//*   STEP05 - Validate restored record count
//*
//* IMPORTANT: Review GDG generation number (-1) before execution.
//*            Ensure the correct pre-migration backup is targeted.
//*********************************************************************
//*
//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')
//*
//* *******************************************************************
//* STEP 1: BACKUP CURRENT (MIGRATED) TRANSACTION DATA FOR AUDIT TRAIL
//* *******************************************************************
//STEP01 EXEC PROC=REPROC,
// CNTLLIB=AWS.M2.CARDDEMO.CNTL
//*
//PRC001.FILEIN  DD DISP=SHR,
//        DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS
//*
//PRC001.FILEOUT DD DISP=(NEW,CATLG,DELETE),
//        UNIT=SYSDA,
//        DCB=(LRECL=350,RECFM=FB,BLKSIZE=0),
//        SPACE=(CYL,(1,1),RLSE),
//        DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)
//*
//* *******************************************************************
//* STEP 2: DELETE CURRENT TRANSACTION VSAM CLUSTER
//* *******************************************************************
//STEP02 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD   SYSOUT=*
//SYSIN    DD   *
   DELETE AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS -
          CLUSTER
   IF MAXCC LE 08 THEN SET MAXCC = 0
   DELETE AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX -
          ALTERNATEINDEX
   IF MAXCC LE 08 THEN SET MAXCC = 0
/*
//*
//* *******************************************************************
//* STEP 3: REDEFINE TRANSACTION VSAM CLUSTER
//* *******************************************************************
//STEP03 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD   SYSOUT=*
//SYSIN    DD   *
   DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) -
          CYLINDERS(1 5) -
          VOLUMES(AWSHJ1 -
          ) -
          KEYS(16 0) -
          RECORDSIZE(350 350) -
          SHAREOPTIONS(2 3) -
          ERASE -
          INDEXED -
          ) -
          DATA (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS.DATA) -
          ) -
          INDEX (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS.INDEX) -
          )
/*
//*
//* *******************************************************************
//* STEP 4: RESTORE DATA FROM PRE-MIGRATION GDG BACKUP
//* *******************************************************************
//STEP04 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD   SYSOUT=*
//BKUPDATA DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(-1)
//TRANVSAM DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS
//SYSIN    DD   *
   REPRO INFILE(BKUPDATA) OUTFILE(TRANVSAM)
/*
//*
//* *******************************************************************
//* STEP 5: VALIDATE RESTORED RECORD COUNT
//* *******************************************************************
//STEP05 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD   SYSOUT=*
//SYSIN    DD   *
   LISTCAT ENTRIES(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) ALL
/*
//*
