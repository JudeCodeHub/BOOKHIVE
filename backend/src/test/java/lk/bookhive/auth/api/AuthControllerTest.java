package lk.bookhive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.bookhive.IntegrationTest;
import lk.bookhive.RecordingEmailSender;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import lk.bookhive.auth.domain.VerificationToken;
import lk.bookhive.auth.domain.VerificationTokenType;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.auth.repository.VerificationTokenRepository;
import lk.bookhive.auth.service.SecureTokens;
import lk.bookhive.common.domain.Language;
import lk.bookhive.notification.service.EmailSender.EmailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Register -> email -> verify, through the real HTTP endpoints and database. Not wrapped in a
 * rolled-back transaction: the email is only sent after a real commit, so each test cleans up.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AuthControllerTest {

  private static final Pattern TOKEN_IN_LINK = Pattern.compile("verify-email\\?token=([\\w-]+)");

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private VerificationTokenRepository verificationTokens;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RecordingEmailSender emails;

  @AfterEach
  void cleanUp() {
    users.deleteAll(); // tokens are deleted with their user (ON DELETE CASCADE)
    emails.clear();
  }

  @Test
  void registerCreatesUnverifiedAccountAndEmailsALink() throws Exception {
    register(
            """
            {"email":"Nimali@Mail.LK","password":"reading-is-fun","fullName":"Nimali Perera",
             "phone":"0771234567","preferredLanguage":"si"}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("nimali@mail.lk"))
        .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        // The password (or its hash) is never sent back
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    User saved = users.findByEmail("nimali@mail.lk").orElseThrow();
    assertThat(saved.getPreferredLanguage()).isEqualTo(Language.SI);
    assertThat(passwordEncoder.matches("reading-is-fun", saved.getPasswordHash())).isTrue();

    EmailMessage email = emails.lastTo("nimali@mail.lk").orElseThrow();
    assertThat(email.subject()).isEqualTo("Verify your BookHive email");
    // Link opens the website in the user's language
    assertThat(email.text()).contains("http://localhost:3000/si/verify-email?token=");
  }

  @Test
  void clickingTheEmailLinkActivatesTheAccountOnce() throws Exception {
    register(validBody("arjun@mail.lk")).andExpect(status().isCreated());
    String token = tokenFromEmail("arjun@mail.lk");

    verify(token).andExpect(status().isNoContent());

    User user = users.findByEmail("arjun@mail.lk").orElseThrow();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getEmailVerifiedAt()).isNotNull();

    // The same link a second time is refused
    verify(token)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));
  }

  @Test
  void expiredLinkIsRefused() throws Exception {
    register(validBody("late@mail.lk")).andExpect(status().isCreated());
    User user = users.findByEmail("late@mail.lk").orElseThrow();
    String oldToken = SecureTokens.generate();
    verificationTokens.save(
        VerificationToken.issue(
            user,
            VerificationTokenType.EMAIL,
            SecureTokens.hash(oldToken),
            Instant.now().minus(Duration.ofHours(1))));

    verify(oldToken)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_EXPIRED"));
    assertThat(users.findByEmail("late@mail.lk").orElseThrow().getStatus())
        .isEqualTo(UserStatus.PENDING_VERIFICATION);
  }

  @Test
  void unknownTokenIsRefused() throws Exception {
    verify("made-up-token")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));
  }

  @Test
  void emailCanOnlyBeRegisteredOnceIgnoringCase() throws Exception {
    register(validBody("kumari@mail.lk")).andExpect(status().isCreated());

    register(validBody("KUMARI@MAIL.LK"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void invalidInputListsEveryBadField() throws Exception {
    register(
            """
            {"email":"not-an-email","password":"short","fullName":"",
             "phone":"12345","preferredLanguage":"fr"}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(5));
    assertThat(users.count()).isZero();
  }

  @Test
  void passwordLongerThanBcryptCanHandleIsRefused() throws Exception {
    // 30 Sinhala letters = 90 bytes in UTF-8, over BCrypt's 72-byte limit
    String sinhala = "අ".repeat(30);
    register(
            """
            {"email":"long@mail.lk","password":"%s","fullName":"Long Password"}
            """
                .formatted(sinhala))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_LONG"));
  }

  @Test
  void commonPasswordIsShownUnderThePasswordField() throws Exception {
    register(
            """
            {"email":"weak@mail.lk","password":"password123","fullName":"Weak Password"}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_COMMON"));
    assertThat(users.existsByEmail("weak@mail.lk")).isFalse();
  }

  private ResultActions register(String json) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions verify(String token) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
  }

  private String tokenFromEmail(String to) {
    Matcher matcher = TOKEN_IN_LINK.matcher(emails.lastTo(to).orElseThrow().text());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static String validBody(String email) {
    return """
        {"email":"%s","password":"reading-is-fun","fullName":"Test Reader"}
        """
        .formatted(email);
  }
}
