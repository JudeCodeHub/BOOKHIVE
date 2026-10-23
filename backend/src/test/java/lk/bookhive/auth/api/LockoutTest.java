package lk.bookhive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.bookhive.IntegrationTest;
import lk.bookhive.RecordingEmailSender;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** AUTH-6: 5 wrong passwords in a row lock the account for 15 minutes. */
@IntegrationTest
@AutoConfigureMockMvc
class LockoutTest {

  private static final String PASSWORD = "reading-is-fun";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RecordingEmailSender emails;

  @BeforeEach
  void createUser() {
    User user =
        User.register(
            "kumari@mail.lk", passwordEncoder.encode(PASSWORD), "Kumari", null, Language.EN);
    user.verifyEmail(Instant.now());
    users.save(user);
  }

  @AfterEach
  void cleanUp() {
    users.deleteAll();
    emails.clear();
  }

  @Test
  void fiveWrongPasswordsLockTheAccount() throws Exception {
    for (int i = 0; i < 5; i++) {
      login("wrong-" + i).andExpect(status().isUnauthorized());
    }

    // Even the right password is refused now, and the user is told why
    login(PASSWORD)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    assertThat(users.findByEmail("kumari@mail.lk").orElseThrow().getLockedUntil())
        .isAfter(Instant.now().plusSeconds(14 * 60));
  }

  @Test
  void wrongPasswordWhileLockedLooksLikeAnyOtherWrongPassword() throws Exception {
    for (int i = 0; i < 5; i++) {
      login("wrong-" + i);
    }

    login("still-wrong")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void correctPasswordResetsTheCount() throws Exception {
    for (int i = 0; i < 4; i++) {
      login("wrong-" + i);
    }
    login(PASSWORD).andExpect(status().isOk());
    for (int i = 0; i < 4; i++) {
      login("wrong-again-" + i);
    }

    login(PASSWORD).andExpect(status().isOk()); // 4 + 4 failures, but never 5 in a row
  }

  @Test
  void resettingThePasswordUnlocksTheAccount() throws Exception {
    for (int i = 0; i < 5; i++) {
      login("wrong-" + i);
    }

    mvc.perform(
        post("/api/v1/auth/forgot-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"kumari@mail.lk\"}"));
    Matcher token =
        Pattern.compile("token=([\\w-]+)")
            .matcher(emails.lastTo("kumari@mail.lk").orElseThrow().text());
    assertThat(token.find()).isTrue();
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"" + token.group(1) + "\",\"newPassword\":\"brand-new-pass\"}"))
        .andExpect(status().isNoContent());

    login("brand-new-pass").andExpect(status().isOk());
  }

  private ResultActions login(String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"kumari@mail.lk","password":"%s"}
                """
                    .formatted(password)));
  }
}
