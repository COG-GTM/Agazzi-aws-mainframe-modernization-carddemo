//USRSECPW JOB 'USRSECPW',CLASS=A,MSGCLASS=0,
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
//* *******************************************************************
//* Set or reset CardDemo sign on passwords.
//*
//* CBUSRPWC stores a random salt and the hash of each password in
//* the USRSEC file - the password itself is never stored.
//*
//* The PWDCTL control records hold the clear passwords:
//*    1-8  user id
//*    9-16 password
//* Supply them from a protected data set (RACF discrete profile,
//* UACC(NONE)) and delete it once the job completes.  Do NOT code
//* passwords in-stream in this member and do NOT commit them.
//* *******************************************************************
//STEP10  EXEC PGM=CBUSRPWC
//STEPLIB  DD DISP=SHR,
//            DSN=AWS.M2.CARDDEMO.LOADLIB
//SYSPRINT DD SYSOUT=*
//SYSOUT   DD SYSOUT=*
//USRSEC   DD DISP=SHR,
//            DSN=AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS
//PWDCTL   DD DISP=SHR,
//            DSN=&SYSUID..CARDDEMO.PWDCTL
//*
//* *******************************************************************
//* Scratch the control data set so the clear passwords do not linger.
//* *******************************************************************
//STEP20  EXEC PGM=IDCAMS,COND=EVEN
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
 DELETE &SYSUID..CARDDEMO.PWDCTL
 SET    MAXCC = 0
/*
