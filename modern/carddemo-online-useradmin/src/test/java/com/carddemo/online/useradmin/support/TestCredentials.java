package com.carddemo.online.useradmin.support;

import com.carddemo.contracts.port.UserCredentialService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** UserCredentialService stub: BCrypt encoding (delegating encoder), no authentication. */
public class TestCredentials implements UserCredentialService {

  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  @Override
  public AuthenticationResult authenticate(String userId, String password) {
    throw new UnsupportedOperationException("not used by CU00-CU03");
  }

  @Override
  public String encode(String rawPassword) {
    return encoder.encode(rawPassword);
  }
}
