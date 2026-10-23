package lk.bookhive.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.List;
import lk.bookhive.IntegrationTest;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Security-relevant actions leave an audit entry; entries can't be changed afterwards. */
@IntegrationTest
@AutoConfigureMockMvc
class AuditTrailTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private AuditLogRepository auditLogs;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbc;

  private User user;

  @BeforeEach
  void setUp() {
    auditLogs.deleteAll(); // entries from other test classes
    User registered =
        User.register(
            "silva@mail.lk", passwordEncoder.encode("reading-is-fun"), "Silva", null, Language.EN);
    registered.verifyEmail(Instant.now());
    user = users.save(registered);
  }

  @AfterEach
  void cleanUp() {
    users.deleteAll();
    auditLogs.deleteAll();
  }

  @Test
  void successfulLoginIsRecordedWithIpAndRequestId() throws Exception {
    login("silva@mail.lk", "reading-is-fun", "203.0.113.7");

    List<AuditLog> entries = auditLogs.findByActionOrderByOccurredAtDesc("auth.login");
    assertThat(entries).hasSize(1);
    AuditLog entry = entries.getFirst();
    assertThat(entry.getActorId()).isEqualTo(user.getId());
    assertThat(entry.getIp()).isEqualTo("203.0.113.7");
    assertThat(entry.getRequestId()).isNotBlank();
  }

  @Test
  void failedLoginsAreRecordedEvenThoughTheRequestFails() throws Exception {
    login("silva@mail.lk", "wrong-password", "203.0.113.8");
    login("ghost@mail.lk", "wrong-password", "203.0.113.8");

    List<AuditLog> entries = auditLogs.findByActionOrderByOccurredAtDesc("auth.login_failed");
    assertThat(entries).hasSize(2);
    assertThat(entries)
        .extracting(e -> e.getDetails().get("accountExists"))
        .containsExactlyInAnyOrder(true, false);
    // Never anything secret in the details
    assertThat(entries.toString()).doesNotContain("wrong-password");
  }

  @Test
  void entriesCannotBeChanged() throws Exception {
    login("silva@mail.lk", "reading-is-fun", "203.0.113.9");

    assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'nothing.happened'"))
        .hasMessageContaining("audit_logs rows cannot be changed");
  }

  private void login(String email, String password, String ip) throws Exception {
    mvc.perform(
        post("/api/v1/auth/login")
            .with(
                request -> {
                  request.setRemoteAddr(ip);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(email, password)));
  }
}
