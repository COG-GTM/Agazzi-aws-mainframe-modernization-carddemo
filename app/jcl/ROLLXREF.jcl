//ROLLXREF JOB 'Emergency Rollback Card XREF',CLASS=A,MSGCLASS=0,
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
//* EMERGENCY ROLLBACK JCL FOR CARD XREF DATA (CARDXREF.VSAM.KSDS)
//*
//* This job restores the CARDXREF VSAM cluster from a GDG backup.
//* Based on TRANBKP.jcl and XREFFILE.jcl patterns.
//*
//* Steps:
//*   STEP01 - Backup current (migrated) XREF data to GDG
//*   STEP02 - Delete current VSAM cluster
//*   STEP03 - Redefine VSAM cluster with original key length
//*   STEP04 - Restore data from pre-migration GDG backup
//*   STEP05 - Define alternate index on Account ID
//*   STEP06 - Define path for alternate index
//*   STEP07 - Build alternate index
//*   STEP08 - Validate restored record count
//*
//* IMPORTANT: Review GDG generation number (-1) before execution.
//*            Ensure the correct pre-migration backup is targeted.
//*********************************************************************
//*
//* *******************************************************************
//* STEP 1: BACKUP CURRENT (MIGRATED) XREF DATA FOR AUDIT TRAIL
//* *******************************************************************
//STEP01 EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  REPRO INDATASET(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS) -
        OUTDATASET(AWS.M2.CARDDEMO.CARDXREF.BKUP(+1))
  IF MAXCC LE 08 THEN SET MAXCC = 0
/*
//*
//* *******************************************************************
//* STEP 2: DELETE CURRENT CARD XREF VSAM CLUSTER
//* *******************************************************************
//STEP02 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   DELETE AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS -
          CLUSTER
   IF MAXCC LE 08 THEN SET MAXCC = 0
   DELETE AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX -
          ALTERNATEINDEX
   IF MAXCC LE 08 THEN SET MAXCC = 0
/*
//*
//* *******************************************************************
//* STEP 3: REDEFINE VSAM CLUSTER WITH ORIGINAL KEY LENGTH (16 BYTES)
//* *******************************************************************
//STEP03 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS) -
          CYLINDERS(1 5) -
          VOLUMES(AWSHJ1 -
          ) -
          KEYS(16 0) -
          RECORDSIZE(50 50) -
          SHAREOPTIONS(2 3) -
          ERASE -
          INDEXED -
          ) -
          DATA (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS.DATA) -
          ) -
          INDEX (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS.INDEX) -
          )
/*
//*
//* *******************************************************************
//* STEP 4: RESTORE DATA FROM PRE-MIGRATION GDG BACKUP
//* *******************************************************************
//STEP04 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD   SYSOUT=*
//BKUPDATA DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.CARDXREF.BKUP(-1)
//XREFVSAM DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS
//SYSIN    DD   *
   REPRO INFILE(BKUPDATA) OUTFILE(XREFVSAM)
/*
//*
//* *******************************************************************
//* STEP 5: DEFINE ALTERNATE INDEX ON ACCOUNT ID
//* *******************************************************************
//STEP05 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   DEFINE ALTERNATEINDEX (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX)-
   RELATE(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS)                    -
   KEYS(11,25)                                                   -
   NONUNIQUEKEY                                                  -
   UPGRADE                                                       -
   RECORDSIZE(50,50)                                             -
   FREESPACE(10,20)                                              -
   VOLUMES(AWSHJ1)                                               -
   CYLINDERS(5,1))                                               -
   DATA (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.DATA))           -
   INDEX (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.INDEX))
/*
//*
//* *******************************************************************
//* STEP 6: DEFINE PATH FOR ALTERNATE INDEX
//* *******************************************************************
//STEP06 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DEFINE PATH                                           -
   (NAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH)        -
    PATHENTRY(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX))
/*
//*
//* *******************************************************************
//* STEP 7: BUILD ALTERNATE INDEX
//* *******************************************************************
//STEP07 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   BLDINDEX                                                      -
   INDATASET(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS)                 -
   OUTDATASET(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX)
/*
//*
//* *******************************************************************
//* STEP 8: VALIDATE RESTORED RECORD COUNT
//* *******************************************************************
//STEP08 EXEC PGM=IDCAMS,COND=(4,LT)
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
   LISTCAT ENTRIES(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS) ALL
/*
//*
