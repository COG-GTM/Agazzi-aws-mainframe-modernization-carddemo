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
      * Commarea used to call the password hashing service CSUTLPWC
      * and the sign on lockout policy applied by COSGN00C.
       01 WS-PWD-SERVICE-PARMS.
         05 PWD-FUNCTION               PIC X(08).
           88 PWD-FN-NEWSALT                     VALUE 'NEWSALT '.
           88 PWD-FN-HASH                        VALUE 'HASH    '.
           88 PWD-FN-VERIFY                      VALUE 'VERIFY  '.
         05 PWD-ALGO                   PIC X(08).
           88 PWD-ALGO-SHA256                    VALUE 'SHA256I '.
         05 PWD-CLEAR-PWD              PIC X(08).
         05 PWD-SALT                   PIC X(16).
         05 PWD-HASH                   PIC X(64).
         05 PWD-EXPECTED-HASH          PIC X(64).
         05 PWD-RETURN-CD              PIC 9(02).
           88 PWD-OK                             VALUE 00.
           88 PWD-MISMATCH                       VALUE 04.
           88 PWD-BAD-REQUEST                    VALUE 08.
           88 PWD-SERVICE-ERROR                  VALUE 12.
