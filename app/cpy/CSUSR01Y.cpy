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
      * User security record - 160 bytes.
      * Passwords are never stored in this record.  Only a salted,
      * iterated one way hash of the password is kept (see CSUTLPWC).
       01 SEC-USER-DATA.
         05 SEC-USR-ID                 PIC X(08).
         05 SEC-USR-FNAME              PIC X(20).
         05 SEC-USR-LNAME              PIC X(20).
         05 SEC-USR-TYPE               PIC X(01).
           88 SEC-USR-TYPE-ADMIN                 VALUE 'A'.
           88 SEC-USR-TYPE-USER                  VALUE 'U'.
         05 SEC-USR-PWD-ALGO           PIC X(08).
           88 SEC-USR-PWD-NOT-SET                VALUE SPACES
                                                       LOW-VALUES.
           88 SEC-USR-PWD-SHA256                 VALUE 'SHA256I '.
         05 SEC-USR-PWD-SALT           PIC X(16).
         05 SEC-USR-PWD-HASH           PIC X(64).
         05 SEC-USR-FAIL-CNT           PIC 9(02).
         05 SEC-USR-LOCK-TS            PIC X(14).
           88 SEC-USR-NOT-LOCKED                 VALUE SPACES
                                                       LOW-VALUES.
         05 SEC-USR-FILLER             PIC X(07).
      *
      * Ver: CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19 23:15:59 CDT
      *
