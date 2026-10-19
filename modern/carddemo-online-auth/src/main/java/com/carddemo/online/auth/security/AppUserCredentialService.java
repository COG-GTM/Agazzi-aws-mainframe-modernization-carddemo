package com.carddemo.online.auth.security;

import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.domain.entity.AppUser;
import com.carddemo.domain.repository.AppUserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O1 credential check on {@code app_user} (replaces the Fase 0 fallback). Mirrors COSGN00C
 * READ-USER-SEC-FILE: the user id and the typed password are upper-cased and moved into PIC X(08)
 * ({@code FUNCTION UPPER-CASE}), then compared with SEC-USR-PWD. Stored passwords are either
 * DelegatingPasswordEncoder hashes ({@code {bcrypt}...}, PE-001) or the legacy 8-byte plaintext of
 * the immutable seed.
 */
@Service
public class AppUserCredentialService implements UserCredentialService {

  static final int PIC_X8 = 8;

  private final AppUserRepository users;
  private final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public AppUserCredentialService(AppUserRepository users) {
    this.users = users;
  }

  /** MOVE to PIC X(08): truncate on the right, pad with spaces. */
  static String picX8(String s) {
    String v = s == null ? "" : s;
    return v.length() >= PIC_X8 ? v.substring(0, PIC_X8) : v + " ".repeat(PIC_X8 - v.length());
  }

  static String upper(String s) {
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthenticationResult authenticate(String userId, String password) {
    // COSGN00C READ-USER-SEC-FILE: EXEC CICS READ DATASET(USRSEC) RIDFLD(WS-USER-ID)
    Optional<AppUser> found = users.findById(picX8(upper(userId)));
    if (found.isEmpty()) {
      return new AuthenticationResult(Outcome.USER_NOT_FOUND, Optional.empty());
    }
    AppUser u = found.get();
    if (!passwordMatches(picX8(upper(password)), u.getSecUsrPwd())) {
      return new AuthenticationResult(Outcome.WRONG_PASSWORD, Optional.empty());
    }
    return new AuthenticationResult(
        Outcome.OK,
        Optional.of(
            new AuthenticatedUser(
                u.getSecUsrId().stripTrailing(),
                u.getSecUsrFname().stripTrailing(),
                u.getSecUsrLname().stripTrailing(),
                userType(u.getSecUsrType()))));
  }

  /** IF CDEMO-USRTYP-ADMIN (SEC-USR-TYPE = 'A') -> admin, every other value -> regular user. */
  static UserType userType(String secUsrType) {
    return secUsrType != null && secUsrType.startsWith("A") ? UserType.ADMIN : UserType.USER;
  }

  private boolean passwordMatches(String typedX8, String stored) {
    if (stored == null) {
      return false;
    }
    String s = stored.strip();
    if (s.startsWith("{")) {
      return encoder.matches(typedX8.stripTrailing(), s);
    }
    // IF SEC-USR-PWD = WS-USER-PWD (legacy plaintext seed, PIC X(08) comparison)
    return picX8(stored).equals(typedX8);
  }

  /**
   * Hash for a new SEC-USR-PWD (O5). The value is first moved into PIC X(08) like the BMS field;
   * case is kept, so (as in COBOL) only upper-case passwords can sign on.
   */
  @Override
  public String encode(String rawPassword) {
    return encoder.encode(picX8(rawPassword).stripTrailing());
  }
}
