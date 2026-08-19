      ******************************************************************
      * Copyright Amazon.com, Inc. or its affiliates.
      * All Rights Reserved.
      *
      * Licensed under the Apache License, Version 2.0 (the "License").
      * You may not use this file except in compliance with the License.
      * You may obtain a copy of the License at
      *
      *    http://www.apache.org/licenses/LICENSE-2.0
      *
      * Unless required by applicable law or agreed to in writing,
      * software distributed under the License is distributed on an
      * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
      * either express or implied. See the License for the specific
      * language governing permissions and limitations under the License
      ******************************************************************
      * Sign on password / lockout policy used by COSGN00C, COUSR01C
      * and COUSR02C.
       01 WS-PWD-POLICY.
         05 PWD-MAX-FAILED-ATTEMPTS    PIC 9(02) VALUE 03.
         05 PWD-LOCKOUT-SECONDS        PIC 9(05) VALUE 00900.
         05 PWD-FAILED-DELAY-SECONDS   PIC 9(02) VALUE 02.
      *  Values that are never accepted as a password.
         05 PWD-REJECT-COUNT           PIC 9(02) VALUE 05.
         05 PWD-REJECT-TABLE.
           10 FILLER                   PIC X(08) VALUE 'PASSWORD'.
           10 FILLER                   PIC X(08) VALUE 'PASSWD  '.
           10 FILLER                   PIC X(08) VALUE 'CARDDEMO'.
           10 FILLER                   PIC X(08) VALUE 'ADMIN   '.
           10 FILLER                   PIC X(08) VALUE '12345678'.
         05 FILLER REDEFINES PWD-REJECT-TABLE.
           10 PWD-REJECT-VALUE         PIC X(08) OCCURS 5 TIMES.
