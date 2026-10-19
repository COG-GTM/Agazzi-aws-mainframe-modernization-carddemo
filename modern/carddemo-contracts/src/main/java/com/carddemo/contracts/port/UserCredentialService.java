package com.carddemo.contracts.port;

import com.carddemo.contracts.screen.ConversationContext.UserType;
import java.util.Optional;

/**
 * Credential checks against USRSEC / app_user (COSGN00C, COUSR01C-03C). Fase 0 accepts the legacy
 * plaintext passwords of the seed; 1B migrates them to BCrypt; O1/O5 consume it.
 */
public interface UserCredentialService {

  record AuthenticatedUser(String userId, String firstName, String lastName, UserType userType) {}

  /** Outcome mirrors COSGN00C: unknown user vs wrong password produce different messages. */
  enum Outcome {
    OK,
    USER_NOT_FOUND,
    WRONG_PASSWORD
  }

  record AuthenticationResult(Outcome outcome, Optional<AuthenticatedUser> user) {}

  AuthenticationResult authenticate(String userId, String password);

  /** Encodes a new password for storage (O5 add/update user). */
  String encode(String rawPassword);
}
