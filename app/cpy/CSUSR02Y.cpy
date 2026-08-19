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
      * Password values that must never authenticate a USRSEC account.
      * Accounts are seeded disabled (blank password) by DUSRSECJ and
      * stay disabled until a unique password is loaded out of band by
      * the USRSECPW job. The published defaults of earlier releases are
      * rejected as well, so a security file seeded before that change
      * cannot be signed on with credentials taken from this repository.
      ******************************************************************
       01 SEC-PWD-POLICY.
         05 SEC-PWD-PLACEHOLDER        PIC X(08) VALUE '*LOCKED*'.
         05 SEC-PWD-SHIPPED-DEFAULT    PIC X(08) VALUE 'PASSWORD'.
