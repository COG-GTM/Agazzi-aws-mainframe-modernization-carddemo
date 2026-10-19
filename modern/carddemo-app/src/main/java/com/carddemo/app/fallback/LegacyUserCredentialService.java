package com.carddemo.app.fallback;

import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.domain.entity.AppUser;
import com.carddemo.domain.repository.AppUserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Fase 0 credential check on app_user. Accepts DelegatingPasswordEncoder hashes ({bcrypt}...) and,
 * for the immutable seed, the legacy 8-byte plaintext (PARITY_EXCEPTIONS PE-001). Like COSGN00C the
 * user id and password are upper-cased and compared padded to PIC X(8).
 */
final class LegacyUserCredentialService implements UserCredentialService {

  private final AppUserRepository users;
  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  LegacyUserCredentialService(AppUserRepository users) {
    this.users = users;
  }

  private static String pad8(String s) {
    String u = s == null ? "" : s.toUpperCase(Locale.ROOT);
    return u.length() >= 8 ? u.substring(0, 8) : u + " ".repeat(8 - u.length());
  }

  @Override
  public AuthenticationResult authenticate(String userId, String password) {
    Optional<AppUser> found = users.findById(pad8(userId));
    if (found.isEmpty()) {
      return new AuthenticationResult(Outcome.USER_NOT_FOUND, Optional.empty());
    }
    AppUser u = found.get();
    String stored = u.getSecUsrPwd();
    boolean ok =
        stored.startsWith("{")
            ? encoder.matches(password, stored.trim())
            : pad8(password).equals(pad8(stored));
    if (!ok) {
      return new AuthenticationResult(Outcome.WRONG_PASSWORD, Optional.empty());
    }
    return new AuthenticationResult(
        Outcome.OK,
        Optional.of(
            new AuthenticatedUser(
                u.getSecUsrId().trim(),
                u.getSecUsrFname().trim(),
                u.getSecUsrLname().trim(),
                UserType.fromCode(u.getSecUsrType().charAt(0)))));
  }

  @Override
  public String encode(String rawPassword) {
    return encoder.encode(rawPassword);
  }
}
