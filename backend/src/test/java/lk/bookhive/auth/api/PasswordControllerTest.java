package lk.bookhive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Forgot password, reset password and change password, through HTTP with real emails. */
@IntegrationTest
@AutoConfigureMockMvc
class PasswordControllerTest {

  private static final String OLD_PASSWORD = "reading-is-fun";
  private static final String NEW_PASSWORD = "even-more-books";
  private static final Pattern RESET_TOKEN = Pattern.compile("reset-password\\?token=([\\w-]+)");

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private VerificationTokenRepository verificationTokens;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RecordingEmailSender emails;

  @AfterEach
  void cleanUp() {
    users.deleteAll();
    emails.clear();
  }

  @Test
  void forgotPasswordEmailsAResetLinkInTheUsersLanguage() throws Exception {
    activeUser("nimali@mail.lk", Language.SI);

    forgot("Nimali@Mail.lk").andExpect(status().isNoContent());

    String text = emails.lastTo("nimali@mail.lk").orElseThrow().text();
    assertThat(text).contains("http://localhost:3000/si/reset-password?token=");
  }

  @Test
  void forgotPasswordGivesTheSameAnswerForUnknownEmails() throws Exception {
    forgot("nobody@mail.lk").andExpect(status().isNoContent());

    assertThat(emails.sent()).isEmpty();
  }

  @Test
  void resetLinkSetsNewPasswordLogsOutEverywhereAndWorksOnce() throws Exception {
    activeUser("arjun@mail.lk", Language.EN);
    String oldSession = refreshCookieOf(login("arjun@mail.lk", OLD_PASSWORD));
    forgot("arjun@mail.lk");
    String token = resetTokenFromEmail("arjun@mail.lk");

    reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

    loginRequest("arjun@mail.lk", OLD_PASSWORD).andExpect(status().isUnauthorized());
    loginRequest("arjun@mail.lk", NEW_PASSWORD).andExpect(status().isOk());
    refresh(oldSession).andExpect(status().isUnauthorized()); // logged out everywhere
    assertThat(emails.lastTo("arjun@mail.lk").orElseThrow().subject())
        .isEqualTo("Your BookHive password was changed");

    reset(token, "yet-another-one")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
  }

  @Test
  void onlyTheNewestResetLinkWorks() throws Exception {
    activeUser("kumari@mail.lk", Language.TA);
    forgot("kumari@mail.lk");
    String first = resetTokenFromEmail("kumari@mail.lk");
    forgot("kumari@mail.lk");
    String second = resetTokenFromEmail("kumari@mail.lk");

    reset(first, NEW_PASSWORD).andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void expiredResetLinkIsRefused() throws Exception {
    User user = activeUser("late@mail.lk", Language.EN);
    String token = SecureTokens.generate();
    verificationTokens.save(
        VerificationToken.issue(
            user,
            VerificationTokenType.RESET,
            SecureTokens.hash(token),
            Instant.now().minus(Duration.ofMinutes(1))));

    reset(token, NEW_PASSWORD)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
  }

  @Test
  void resettingAlsoVerifiesAnUnverifiedEmail() throws Exception {
    users.save(
        User.register(
            "new@mail.lk", passwordEncoder.encode(OLD_PASSWORD), "New", null, Language.EN));
    forgot("new@mail.lk");

    reset(resetTokenFromEmail("new@mail.lk"), NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(users.findByEmail("new@mail.lk").orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void changePasswordNeedsLoginAndTheCurrentPassword() throws Exception {
    activeUser("silva@mail.lk", Language.EN);
    MvcResult session = login("silva@mail.lk", OLD_PASSWORD);

    mvc.perform(changePassword(null, OLD_PASSWORD, NEW_PASSWORD))
        .andExpect(status().isUnauthorized());
    mvc.perform(changePassword(bearer(session), "wrong-password", NEW_PASSWORD))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
  }

  @Test
  void changePasswordKeepsThisDeviceLoggedInAndLogsOutOthers() throws Exception {
    activeUser("fernando@mail.lk", Language.EN);
    MvcResult laptop = login("fernando@mail.lk", OLD_PASSWORD);
    MvcResult phone = login("fernando@mail.lk", OLD_PASSWORD);

    MvcResult changed =
        mvc.perform(changePassword(bearer(laptop), OLD_PASSWORD, NEW_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    refresh(refreshCookieOf(phone)).andExpect(status().isUnauthorized());
    refresh(refreshCookieOf(changed)).andExpect(status().isOk());
    loginRequest("fernando@mail.lk", NEW_PASSWORD).andExpect(status().isOk());
  }

  private User activeUser(String email, Language language) {
    User user =
        User.register(email, passwordEncoder.encode(OLD_PASSWORD), "Test Reader", null, language);
    user.verifyEmail(Instant.now());
    return users.save(user);
  }

  private ResultActions forgot(String email) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/forgot-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}"));
  }

  private ResultActions reset(String token, String newPassword) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"token":"%s","newPassword":"%s"}
                """
                    .formatted(token, newPassword)));
  }

  private ResultActions loginRequest(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(email, password)));
  }

  private MvcResult login(String email, String password) throws Exception {
    return loginRequest(email, password).andExpect(status().isOk()).andReturn();
  }

  private ResultActions refresh(String cookie) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh").cookie(new Cookie(AuthController.REFRESH_COOKIE, cookie)));
  }

  private static MockHttpServletRequestBuilder changePassword(
      String bearer, String current, String next) {
    var request =
        post("/api/v1/me/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"currentPassword":"%s","newPassword":"%s"}
                """
                    .formatted(current, next));
    return bearer == null ? request : request.header(HttpHeaders.AUTHORIZATION, bearer);
  }

  private String resetTokenFromEmail(String to) {
    Matcher matcher = RESET_TOKEN.matcher(emails.lastTo(to).orElseThrow().text());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static String refreshCookieOf(MvcResult result) {
    return result.getResponse().getCookie(AuthController.REFRESH_COOKIE).getValue();
  }

  private static String bearer(MvcResult result) throws Exception {
    return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }
}
