package com.carddemo.online.useradmin.service;

import com.carddemo.online.useradmin.support.Fields;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@code PASSWDI NOT = SEC-USR-PWD} of COUSR02C on hashed storage: {@code {id}hash} values are
 * checked with the delegating encoder, legacy seed values with the COBOL PIC X(08) comparison.
 */
@Component
public class PasswordMatcher {

  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public boolean matches(String rawPassword, String stored) {
    if (stored == null) {
      return false;
    }
    String s = stored.trim();
    if (s.startsWith("{")) {
      try {
        return encoder.matches(Fields.rtrim(rawPassword), s);
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    return Fields.pad(rawPassword, 8).equals(Fields.pad(stored, 8));
  }
}
