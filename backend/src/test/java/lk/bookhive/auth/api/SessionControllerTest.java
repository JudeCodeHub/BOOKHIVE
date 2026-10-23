package lk.bookhive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.IntegrationTest;
import lk.bookhive.auth.domain.RefreshToken;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.repository.RefreshTokenRepository;
import lk.bookhive.auth.repository.UserRepository;
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

/** Login, refresh (rotation + reuse detection) and logout through HTTP, with cookies. */
@IntegrationTest
@AutoConfigureMockMvc
class SessionControllerTest {

  private static final String PASSWORD = "reading-is-fun";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private RefreshTokenRepository refreshTokens;
  @Autowired private PasswordEncoder passwordEncoder;

  @AfterEach
  void cleanUp() {
    users.deleteAll(); // refresh tokens are deleted with their user
  }

  @Test
  void loginReturnsAccessTokenAndSetsSecureRefreshCookie() throws Exception {
    activeUser("nimali@mail.lk");

    MvcResult result =
        login("Nimali@Mail.lk", PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("nimali@mail.lk"))
            .andExpect(jsonPath("$.user.roles[0]").value("MEMBER"))
            .andExpect(jsonPath("$.user.preferredLanguage").value("si"))
            // The refresh token travels only in the cookie
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andReturn();

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .startsWith(AuthController.REFRESH_COOKIE + "=")
        .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800");

    // The access token works on a protected URL (404 = got past security; no such page yet)
    mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, bearer(result)))
        .andExpect(status().isNotFound());
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
    activeUser("arjun@mail.lk");

    login("arjun@mail.lk", "wrong-password")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("The email or password is incorrect."));
    login("nobody@mail.lk", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("The email or password is incorrect."));
  }

  @Test
  void unverifiedAccountCannotLogIn() throws Exception {
    users.save(
        User.register(
            "new@mail.lk", passwordEncoder.encode(PASSWORD), "New User", null, Language.EN));

    login("new@mail.lk", PASSWORD)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
  }

  @Test
  void refreshRotatesTheCookieAndReturnsANewAccessToken() throws Exception {
    activeUser("kumari@mail.lk");
    String firstCookie = refreshCookieOf(login("kumari@mail.lk", PASSWORD).andReturn());

    MvcResult refreshed = refresh(firstCookie).andExpect(status().isOk()).andReturn();

    String secondCookie = refreshCookieOf(refreshed);
    assertThat(secondCookie).isNotEqualTo(firstCookie);
    assertThat(bearer(refreshed)).startsWith("Bearer ey");
  }

  @Test
  void reusingAnOldRefreshTokenEndsTheWholeSession() throws Exception {
    activeUser("silva@mail.lk");
    String stolen = refreshCookieOf(login("silva@mail.lk", PASSWORD).andReturn());
    String legit = refreshCookieOf(refresh(stolen).andReturn()); // the real user refreshed

    // The attacker replays the old token: detected
    refresh(stolen)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // ...and the real user's newer token was revoked too, so both must log in again
    refresh(legit).andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithoutCookieOrWithExpiredTokenFails() throws Exception {
    User user = activeUser("old@mail.lk");
    String expired = SecureTokens.generate();
    refreshTokens.save(
        RefreshToken.issue(
            user,
            SecureTokens.hash(expired),
            UUID.randomUUID(),
            Instant.now().minus(Duration.ofMinutes(1))));

    mvc.perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    refresh(expired).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutClearsTheCookieAndEndsTheSession() throws Exception {
    activeUser("fernando@mail.lk");
    String cookie = refreshCookieOf(login("fernando@mail.lk", PASSWORD).andReturn());

    MvcResult loggedOut =
        mvc.perform(
                post("/api/v1/auth/logout")
                    .cookie(new Cookie(AuthController.REFRESH_COOKIE, cookie)))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(loggedOut.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    refresh(cookie).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithoutCookieIsHarmless() throws Exception {
    mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
  }

  private User activeUser(String email) {
    User user =
        User.register(email, passwordEncoder.encode(PASSWORD), "Test Reader", null, Language.SI);
    user.verifyEmail(Instant.now());
    return users.save(user);
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(email, password)));
  }

  private ResultActions refresh(String cookieValue) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh")
            .cookie(new Cookie(AuthController.REFRESH_COOKIE, cookieValue)));
  }

  private static String refreshCookieOf(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
    assertThat(cookie).isNotNull();
    return cookie.getValue();
  }

  private static String bearer(MvcResult result) throws Exception {
    return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }
}
