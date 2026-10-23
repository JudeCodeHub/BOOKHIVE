package lk.bookhive.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.bookhive.IntegrationTest;
import lk.bookhive.RecordingEmailSender;
import lk.bookhive.auth.api.AuthController;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.common.audit.AuditLog;
import lk.bookhive.common.audit.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole account journey in one go, through HTTP only, like a real user (checklist step 106).
 * Each step has its own detailed tests next to the code; this one proves they fit together.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AuthJourneyTest {

  private static final String EMAIL = "arjun.fernando@mail.lk";
  private static final String PASSWORD = "my-favourite-books";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private AuditLogRepository auditLogs;
  @Autowired private RecordingEmailSender emails;

  @BeforeEach
  @AfterEach
  void cleanUp() {
    users.deleteAll();
    auditLogs.deleteAll();
    emails.clear();
  }

  @Test
  void registerVerifyLoginUseRefreshLogout() throws Exception {
    // 1. Register: account created, but not usable yet
    json(
            post("/api/v1/auth/register"),
            """
            {"email":"%s","password":"%s","fullName":"Arjun Fernando","preferredLanguage":"ta"}
            """
                .formatted(EMAIL, PASSWORD))
        .andExpect(status().isCreated());
    login()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

    // 2. Click the link in the email
    String token = tokenIn(emails.lastTo(EMAIL).orElseThrow().text());
    json(post("/api/v1/auth/verify-email"), "{\"token\":\"" + token + "\"}")
        .andExpect(status().isNoContent());

    // 3. Log in: access token in the body, refresh token in the cookie
    MvcResult loggedIn = login().andExpect(status().isOk()).andReturn();
    String accessToken = accessToken(loggedIn);
    String refreshCookie = refreshCookie(loggedIn);

    // 4. Use the access token
    mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.preferredLanguage").value("ta"));

    // 5. Refresh: new access token and a rotated cookie
    MvcResult refreshed = refresh(refreshCookie).andExpect(status().isOk()).andReturn();
    String newCookie = refreshCookie(refreshed);
    assertThat(newCookie).isNotEqualTo(refreshCookie);
    mvc.perform(
            get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
        .andExpect(status().isOk());

    // 6. Log out: the session can't be refreshed any more
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie(AuthController.REFRESH_COOKIE, newCookie)))
        .andExpect(status().isNoContent());
    refresh(newCookie).andExpect(status().isUnauthorized());

    // 7. Everything important is in the audit log, in order
    List<String> actions =
        auditLogs.findAll().stream()
            .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
            .map(AuditLog::getAction)
            .toList();
    assertThat(actions)
        .containsSubsequence("auth.registered", "auth.email_verified", "auth.login", "auth.logout");
  }

  private ResultActions login() throws Exception {
    return json(
        post("/api/v1/auth/login"),
        """
        {"email":"%s","password":"%s"}
        """
            .formatted(EMAIL, PASSWORD));
  }

  private ResultActions refresh(String cookie) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh").cookie(new Cookie(AuthController.REFRESH_COOKIE, cookie)));
  }

  private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String tokenIn(String emailText) {
    Matcher matcher = Pattern.compile("token=([\\w-]+)").matcher(emailText);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static String accessToken(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  private static String refreshCookie(MvcResult result) {
    return result.getResponse().getCookie(AuthController.REFRESH_COOKIE).getValue();
  }
}
