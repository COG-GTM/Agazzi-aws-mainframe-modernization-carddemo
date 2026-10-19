/**
 * Fase 5C security hardening (plan "Fase 5", ASVS L2 checklist in {@code docs/security/}).
 *
 * <p>Everything here is additive to the {@code SecurityFilterChain} owned by O1: response security
 * headers, structured audit log of sign-on/authorization/admin/update operations and the AWS
 * Secrets Manager config import. Nothing replaces a workstream bean.
 */
package com.carddemo.security;
