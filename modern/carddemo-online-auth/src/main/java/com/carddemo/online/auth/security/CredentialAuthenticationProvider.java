package com.carddemo.online.auth.security;

import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.port.UserCredentialService.AuthenticatedUser;
import com.carddemo.contracts.port.UserCredentialService.AuthenticationResult;
import com.carddemo.contracts.port.UserCredentialService.Outcome;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security bridge to {@link UserCredentialService}: used by HTTP Basic (machine clients,
 * golden scenarios) and by the CC00 sign-on screen. Principal = SEC-USR-ID, role from SEC-USR-TYPE.
 */
public class CredentialAuthenticationProvider implements AuthenticationProvider {

  public static final String ROLE_ADMIN = "ROLE_ADMIN";
  public static final String ROLE_USER = "ROLE_USER";

  private final UserCredentialService credentials;

  public CredentialAuthenticationProvider(UserCredentialService credentials) {
    this.credentials = credentials;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    String user = authentication.getName();
    Object pwd = authentication.getCredentials();
    AuthenticationResult r = credentials.authenticate(user, pwd == null ? "" : pwd.toString());
    if (r.outcome() != Outcome.OK || r.user().isEmpty()) {
      throw new BadCredentialsException("Bad credentials");
    }
    return token(r.user().get());
  }

  /** Authenticated token for a signed-on user (no credentials kept). */
  public static UsernamePasswordAuthenticationToken token(AuthenticatedUser user) {
    String role = user.userType() == UserType.ADMIN ? ROLE_ADMIN : ROLE_USER;
    return UsernamePasswordAuthenticationToken.authenticated(
        user.userId(), null, List.of(new SimpleGrantedAuthority(role)));
  }

  /** User type of an authenticated principal (CDEMO-USER-TYPE). */
  public static UserType userType(Authentication auth) {
    return auth.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()))
        ? UserType.ADMIN
        : UserType.USER;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
