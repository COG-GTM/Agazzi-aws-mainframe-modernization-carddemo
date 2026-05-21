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
//* Based on TRANBKP.jcl and TRANFILE.jcl patterns.
//*
//* Steps:
//*   STEP01 - Backup current (migrated) transaction data to GDG
//*   STEP02 - Close CICS files
//*   STEP03 - Delete current VSAM cluster
//*   STEP04 - Redefine VSAM cluster with original parameters
//*   STEP05 - Restore data from pre-migration GDG backup
//*   STEP06 - Define alternate index on processed timestamp
//*   STEP07 - Define path for alternate index
//*   STEP08 - Build alternate index
//*   STEP09 - Open CICS files
//*   STEP10 - Validate restored record count
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
//* STEP 2: CLOSE CICS FILES BEFORE ROLLBACK
//* *******************************************************************
//STEP02 EXEC PGM=SDSF
//ISFOUT DD SYSOUT=*
//CMDOUT DD SYSOUT=*
//ISFIN  DD *
 /F CICSAWSA,'CEMT SET FIL(TRANSACT) CLO'
 /F CICSAWSA,'CEMT SET FIL(CXACAIX ) CLO'
/*
//*
//* *******************************************************************
//* STEP 3: DELETE CURRENT TRANSACTION VSAM CLUSTER
//* *******************************************************************
//STEP03 EXEC PGM=IDCAMS,COND=(4,LT)
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
//* STEP 4: REDEFINE TRANSACTION VSAM CLUSTER
//* *******************************************************************
//STEP04 EXEC PGM=IDCAMS,COND=(4,LT)
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
//* STEP 5: RESTORE DATA FROM PRE-MIGRATION GDG BACKUP
//* *******************************************************************
//STEP05 EXEC PGM=IDCAMS,COND=(4,LT)
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
//* STEP 6: DEFINE ALTERNATE INDEX ON PROCESSED TIMESTAMP
//* *******************************************************************
//STEP06 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   DEFINE ALTERNATEINDEX (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX)-
   RELATE(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS)                    -
   KEYS(26 304)                                                  -
   NONUNIQUEKEY                                                  -
   UPGRADE                                                       -
   RECORDSIZE(350,350)                                           -
   VOLUMES(AWSHJ1)                                               -
   CYLINDERS(5,1))                                               -
   DATA (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.DATA))           -
   INDEX (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.INDEX))
/*
//*
//* *******************************************************************
//* STEP 7: DEFINE PATH FOR ALTERNATE INDEX
//* *******************************************************************
//STEP07 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DEFINE PATH                                           -
   (NAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH)        -
    PATHENTRY(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX))
/*
//*
//* *******************************************************************
//* STEP 8: BUILD ALTERNATE INDEX
//* *******************************************************************
//STEP08 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   BLDINDEX                                                      -
   INDATASET(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS)                 -
   OUTDATASET(AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX)
/*
//*
//* *******************************************************************
//* STEP 9: REOPEN CICS FILES AFTER ROLLBACK
//* *******************************************************************
//STEP09 EXEC PGM=SDSF
//ISFOUT DD SYSOUT=*
//CMDOUT DD SYSOUT=*
//ISFIN  DD *
 /F CICSAWSA,'CEMT SET FIL(TRANSACT) OPE'
 /F CICSAWSA,'CEMT SET FIL(CXACAIX ) OPE'
/*
//*
//* *******************************************************************
//* STEP 10: VALIDATE RESTORED RECORD COUNT
//* *******************************************************************
//STEP10 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD   SYSOUT=*
//SYSIN    DD   *
   LISTCAT ENTRIES(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) ALL
/*
//*
