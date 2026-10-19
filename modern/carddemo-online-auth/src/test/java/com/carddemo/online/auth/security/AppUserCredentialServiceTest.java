package com.carddemo.online.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.carddemo.contracts.port.UserCredentialService.Outcome;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.domain.entity.AppUser;
import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.online.auth.SeedUsers;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppUserCredentialServiceTest {

  private AppUserRepository repo;
  private Map<String, AppUser> users;
  private AppUserCredentialService service;

  @BeforeEach
  void setUp() {
    repo = mock(AppUserRepository.class);
    users = SeedUsers.stub(repo);
    service = new AppUserCredentialService(repo);
  }

  @Test
  void legacySeedPlaintextIsCaseInsensitiveLikeUpperCase() {
    var r = service.authenticate("admin001", "password");
    assertThat(r.outcome()).isEqualTo(Outcome.OK);
    assertThat(r.user().orElseThrow().userId()).isEqualTo("ADMIN001");
    assertThat(r.user().orElseThrow().firstName()).isEqualTo("ADMIN");
    assertThat(r.user().orElseThrow().userType()).isEqualTo(UserType.ADMIN);
    assertThat(service.authenticate("USER0001", "PASSWORD").user().orElseThrow().userType())
        .isEqualTo(UserType.USER);
  }

  @Test
  void outcomesMirrorCosgn00c() {
    assertThat(service.authenticate("ADMIN001", "WRONGPWD").outcome())
        .isEqualTo(Outcome.WRONG_PASSWORD);
    assertThat(service.authenticate("NOBODY01", "PASSWORD").outcome())
        .isEqualTo(Outcome.USER_NOT_FOUND);
    assertThat(service.authenticate(null, null).outcome()).isEqualTo(Outcome.USER_NOT_FOUND);
    // the BMS field is PIC X(08): extra characters never reach the program
    assertThat(service.authenticate("ADMIN001XYZ", "PASSWORD").outcome()).isEqualTo(Outcome.OK);
  }

  @Test
  void bcryptHashesFromO5AndDataMigration() {
    String hash = service.encode("SECRET1");
    assertThat(hash).startsWith("{bcrypt}");
    users.get("USER0002").setSecUsrPwd(hash);
    assertThat(service.authenticate("user0002", "secret1").outcome()).isEqualTo(Outcome.OK);
    assertThat(service.authenticate("USER0002", "SECRET2").outcome())
        .isEqualTo(Outcome.WRONG_PASSWORD);
    // lower-case passwords never match, exactly like COSGN00C (input is upper-cased)
    users.get("USER0003").setSecUsrPwd(service.encode("lower"));
    assertThat(service.authenticate("USER0003", "lower").outcome())
        .isEqualTo(Outcome.WRONG_PASSWORD);
    users.get("USER0004").setSecUsrPwd(null);
    assertThat(service.authenticate("USER0004", "PASSWORD").outcome())
        .isEqualTo(Outcome.WRONG_PASSWORD);
  }

  @Test
  void userTypeOtherThanAIsRegularUser() {
    assertThat(AppUserCredentialService.userType("A")).isEqualTo(UserType.ADMIN);
    assertThat(AppUserCredentialService.userType("U")).isEqualTo(UserType.USER);
    assertThat(AppUserCredentialService.userType("X")).isEqualTo(UserType.USER);
    assertThat(AppUserCredentialService.userType(null)).isEqualTo(UserType.USER);
  }
}
