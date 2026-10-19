package com.carddemo.online.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.record.UserSecurityRecord;
import com.carddemo.domain.entity.AppUser;
import com.carddemo.domain.repository.AppUserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The 10 USRSEC users of DUSRSECJ / V2 seed (padded PIC X fields, plaintext PASSWORD). */
public final class SeedUsers {

  private SeedUsers() {}

  public static AppUser user(String id, String fname, String lname, String pwd, String type) {
    return AppUser.fromRecord(
        new UserSecurityRecord(pad(id, 8), pad(fname, 20), pad(lname, 20), pwd, type, pad("", 23)));
  }

  static String pad(String s, int n) {
    return s + " ".repeat(n - s.length());
  }

  public static Map<String, AppUser> seed() {
    Map<String, AppUser> m = new LinkedHashMap<>();
    for (int i = 1; i <= 5; i++) {
      m.put("ADMIN00" + i, user("ADMIN00" + i, "ADMIN", "N" + i, "PASSWORD", "A"));
      m.put("USER000" + i, user("USER000" + i, "USER", "N" + i, "PASSWORD", "U"));
    }
    return m;
  }

  /** Stubs {@code findById} with the seed (keys are PIC X(08), so exact 8-char ids). */
  public static Map<String, AppUser> stub(AppUserRepository repo) {
    Map<String, AppUser> m = seed();
    when(repo.findById(anyString()))
        .thenAnswer(inv -> Optional.ofNullable(m.get(inv.<String>getArgument(0))));
    return m;
  }
}
