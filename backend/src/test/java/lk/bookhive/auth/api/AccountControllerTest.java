package lk.bookhive.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import lk.bookhive.IntegrationTest;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** GET/PATCH /me: the logged-in user's own profile. */
@IntegrationTest
@AutoConfigureMockMvc
class AccountControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  private String bearer;

  @BeforeEach
  void logIn() throws Exception {
    User user =
        User.register(
            "nimali@mail.lk",
            passwordEncoder.encode("reading-is-fun"),
            "Nimali Perera",
            "0771234567",
            Language.SI);
    user.verifyEmail(Instant.now());
    users.save(user);
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"nimali@mail.lk\",\"password\":\"reading-is-fun\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    bearer = "Bearer " + JsonPath.read(body, "$.accessToken");
  }

  @AfterEach
  void cleanUp() {
    users.deleteAll();
  }

  @Test
  void showsMyProfile() throws Exception {
    mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("nimali@mail.lk"))
        .andExpect(jsonPath("$.fullName").value("Nimali Perera"))
        .andExpect(jsonPath("$.phone").value("0771234567"))
        .andExpect(jsonPath("$.preferredLanguage").value("si"))
        .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void needsLogin() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void changesOnlyTheFieldsSent() throws Exception {
    update("{\"preferredLanguage\":\"ta\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferredLanguage").value("ta"))
        .andExpect(jsonPath("$.fullName").value("Nimali Perera"))
        .andExpect(jsonPath("$.phone").value("0771234567"));

    update("{\"fullName\":\"  Nimali S. Perera \",\"phone\":\"\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Nimali S. Perera"))
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.preferredLanguage").value("ta"));
  }

  @Test
  void rejectsInvalidValuesAndCannotChangeEmail() throws Exception {
    update("{\"fullName\":\"   \",\"phone\":\"123\",\"preferredLanguage\":\"fr\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(3));

    // Unknown fields like email are ignored: the email stays the same
    update("{\"email\":\"hacker@mail.lk\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("nimali@mail.lk"));
  }

  private ResultActions update(String json) throws Exception {
    return mvc.perform(
        patch("/api/v1/me")
            .header(HttpHeaders.AUTHORIZATION, bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }
}
