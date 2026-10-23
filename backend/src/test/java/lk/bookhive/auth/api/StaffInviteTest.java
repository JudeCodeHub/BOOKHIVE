package lk.bookhive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.bookhive.IntegrationTest;
import lk.bookhive.RecordingEmailSender;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** An admin invites a librarian, who sets a password through the emailed link and logs in. */
@IntegrationTest
@AutoConfigureMockMvc
class StaffInviteTest {

  private static final String PASSWORD = "reading-is-fun";
  private static final Pattern INVITE_TOKEN = Pattern.compile("accept-invite\\?token=([\\w-]+)");

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RecordingEmailSender emails;

  @AfterEach
  void cleanUp() {
    users.deleteAll();
    emails.clear();
  }

  @Test
  void adminInvitesLibrarianWhoSetsAPasswordAndLogsIn() throws Exception {
    String admin = bearerFor(Role.ADMIN, "silva@library.lk");

    invite(admin, "kumari@library.lk", "LIBRARIAN")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("LIBRARIAN"))
        .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

    // Can't log in before accepting: nobody knows the placeholder password
    String email = emails.lastTo("kumari@library.lk").orElseThrow().text();
    assertThat(email).contains("as a librarian", "http://localhost:3000/si/accept-invite?token=");
    Matcher token = INVITE_TOKEN.matcher(email);
    assertThat(token.find()).isTrue();

    accept(token.group(1), "my-own-secret-pass").andExpect(status().isNoContent());

    String librarian = loginBearer("kumari@library.lk", "my-own-secret-pass");
    mvc.perform(get("/api/v1/staff/anything").header(HttpHeaders.AUTHORIZATION, librarian))
        .andExpect(status().isNotFound()); // past security: a librarian may use /staff
    accept(token.group(1), "another-new-pass")
        .andExpect(jsonPath("$.code").value("INVITE_TOKEN_INVALID"));
  }

  @Test
  void onlyAdminsCanInvite() throws Exception {
    invite(bearerFor(Role.LIBRARIAN, "kumari@library.lk"), "new@library.lk", "LIBRARIAN")
        .andExpect(status().isForbidden());
    invite(bearerFor(Role.MEMBER, "nimali@mail.lk"), "new@library.lk", "ADMIN")
        .andExpect(status().isForbidden());
    assertThat(users.existsByEmail("new@library.lk")).isFalse();
  }

  @Test
  void rejectsMemberRoleAndTakenEmails() throws Exception {
    String admin = bearerFor(Role.ADMIN, "silva@library.lk");

    invite(admin, "x@library.lk", "MEMBER")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("role"));
    invite(admin, "SILVA@library.lk", "LIBRARIAN")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  /** An active account with this role, logged in. */
  private String bearerFor(Role role, String email) throws Exception {
    String hash = passwordEncoder.encode(PASSWORD);
    User user =
        role == Role.MEMBER
            ? User.register(email, hash, "Test", null, Language.EN)
            : User.inviteStaff(email, hash, "Test", null, Language.EN, role);
    user.verifyEmail(Instant.now());
    users.save(user);
    return loginBearer(email, PASSWORD);
  }

  private String loginBearer(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"%s"}
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.accessToken");
  }

  private ResultActions invite(String bearer, String email, String role) throws Exception {
    return mvc.perform(
        post("/api/v1/admin/staff")
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","fullName":"Kumari Silva","role":"%s","preferredLanguage":"si"}
                """
                    .formatted(email, role)));
  }

  private ResultActions accept(String token, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/accept-invite")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"token":"%s","password":"%s"}
                """
                    .formatted(token, password)));
  }
}
