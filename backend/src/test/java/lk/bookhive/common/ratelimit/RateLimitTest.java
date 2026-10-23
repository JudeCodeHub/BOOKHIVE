package lk.bookhive.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import lk.bookhive.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Per-IP limits backed by a real Redis. Uses tiny limits (3 logins per minute); each test uses its
 * own fake IP address so buckets don't carry over between tests.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "bookhive.rate-limit.rules.login.capacity=3")
class RateLimitTest {

  @Autowired private MockMvc mvc;

  @Test
  void fourthLoginInAMinuteIsRefusedWithRetryAfter() throws Exception {
    String ip = randomIp();
    for (int i = 0; i < 3; i++) {
      login(ip).andExpect(status().isUnauthorized()); // allowed through (wrong password)
    }

    login(ip)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void otherClientsAreNotAffected() throws Exception {
    String busy = randomIp();
    for (int i = 0; i < 4; i++) {
      login(busy);
    }

    login(randomIp()).andExpect(status().isUnauthorized());
  }

  @Test
  void otherEndpointsAreNotLimited() throws Exception {
    String ip = randomIp();
    for (int i = 0; i < 4; i++) {
      login(ip);
    }

    mvc.perform(
            get("/actuator/health")
                .with(
                    request -> {
                      request.setRemoteAddr(ip);
                      return request;
                    }))
        .andExpect(status().isOk());
  }

  private ResultActions login(String ip) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .with(
                request -> {
                  request.setRemoteAddr(ip);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@mail.lk\",\"password\":\"whatever-it-is\"}"));
  }

  private static String randomIp() {
    return "10.0." + (UUID.randomUUID().hashCode() & 0xff) + "." + (System.nanoTime() & 0xff);
  }
}
