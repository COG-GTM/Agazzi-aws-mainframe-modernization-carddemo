//DUSRSECJ JOB 'DEF USRSEC FILE',REGION=8M,CLASS=A,
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
//* PRE DELETE STEP
//*-------------------------------------------------------------------*
//*
//PREDEL  EXEC PGM=IEFBR14
//*
//DD01     DD DSN=AWS.M2.CARDDEMO.USRSEC.PS,
//            DISP=(MOD,DELETE,DELETE)
//*
//*-------------------------------------------------------------------*
//* CREATE USER SECURITY FILE (PS) FROM IN-STREAM DATA
//*-------------------------------------------------------------------*
//*
//STEP01  EXEC PGM=IEBGENER
//*
//* The seeded users have NO password.  Sign on is refused until an
//* administrator sets one with USRSECPW.jcl.  Passwords are never
//* held in this file - only a salt and a hash (see CSUSR01Y).
//* Layout of the in-stream records:
//*   1-8 user id, 9-28 first name, 29-48 last name, 49 user type.
//* IEBGENER expands them to the 160 byte USRSEC record and sets the
//* failed sign on counter (position 138) to zero.
//*
//SYSUT1   DD *
ADMIN001MARGARET            GOLD                A
ADMIN002RUSSELL             RUSSELL             A
ADMIN003RAYMOND             WHITMORE            A
ADMIN004EMMANUEL            CASGRAIN            A
ADMIN005GRANVILLE           LACHAPELLE          A
USER0001LAWRENCE            THOMAS              U
USER0002AJITH               KUMAR               U
USER0003LAURITZ             ALME                U
USER0004AVERARDO            MAZZI               U
USER0005LEE                 TING                U
/*
//SYSUT2   DD DSN=AWS.M2.CARDDEMO.USRSEC.PS,
//            DISP=(NEW,CATLG,DELETE),
//            DCB=(LRECL=160,RECFM=FB,DSORG=PS,BLKSIZE=0),
//            UNIT=SYSAD,SPACE=(TRK,(10,5),RLSE)
//*
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  GENERATE MAXFLDS=2
  RECORD   FIELD=(49,1,,1),FIELD=(2,'00',,138)
/*
//*
//*-------------------------------------------------------------------*
//* DEFINE VSAM FILE FOR USER SECURITY
//*-------------------------------------------------------------------*
//*
//STEP02  EXEC PGM=IDCAMS
//*
//SYSPRINT DD  SYSOUT=*
//SYSIN    DD  *
 DELETE                  AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS
 SET       MAXCC = 0
 DEFINE    CLUSTER (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS)    -
                    KEYS(8,0)                                 -
                    RECORDSIZE(160,160)                       -
                    REUSE                                     -
                    INDEXED                                   -
                    TRACKS(45,15)                             -
                    FREESPACE(10,15)                          -
                    CISZ(8192))                               -
           DATA    (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS.DAT)) -
           INDEX   (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS.IDX))
/*
//*
//*-------------------------------------------------------------------*
//* COPY USER SECURITY DATA FROM PS TO VSAM FILE
//*-------------------------------------------------------------------*
//*
//STEP03  EXEC PGM=IDCAMS
//*
//IN       DD  DSN=AWS.M2.CARDDEMO.USRSEC.PS,DISP=SHR
//OUT      DD  DSN=AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS,DISP=SHR
//SYSOUT   DD  SYSOUT=*
//SYSPRINT DD  SYSOUT=*
//SYSIN    DD  *
  REPRO INFILE(IN) OUTFILE(OUT)
/*
//
//*
//* Ver: CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19 23:23:06 CDT
//*
