package lk.bookhive.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.bookhive.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code @PreAuthorize} on service methods: enforced, honours "admin is also a librarian", and a
 * refusal reaches the client as our usual 403 ProblemDetail.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(MethodSecurityTest.TestBeans.class)
class MethodSecurityTest {

  @Autowired private LibrarianOnlyService service;
  @Autowired private MockMvc mvc;

  @Test
  @WithMockUser(roles = "LIBRARIAN")
  void librarianMayCallLibrarianMethod() {
    assertThat(service.waiveSmallFine()).isEqualTo("waived");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminMayToo() {
    assertThat(service.waiveSmallFine()).isEqualTo("waived");
  }

  @Test
  @WithMockUser(roles = "MEMBER")
  void memberIsRefused() {
    assertThatThrownBy(service::waiveSmallFine).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "MEMBER")
  void refusalBecomes403ProblemDetail() throws Exception {
    // A URL a member is allowed to reach, whose service method needs LIBRARIAN
    mvc.perform(get("/api/v1/me/test-waive"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  public static class LibrarianOnlyService {

    @PreAuthorize("hasRole('LIBRARIAN')")
    public String waiveSmallFine() {
      return "waived";
    }
  }

  @RestController
  static class TestController {

    private final LibrarianOnlyService service;

    TestController(LibrarianOnlyService service) {
      this.service = service;
    }

    @GetMapping("/me/test-waive")
    String waive() {
      return service.waiveSmallFine();
    }
  }

  @TestConfiguration
  static class TestBeans {

    @Bean
    LibrarianOnlyService librarianOnlyService() {
      return new LibrarianOnlyService();
    }

    @Bean
    TestController testController(LibrarianOnlyService service) {
      return new TestController(service);
    }
  }
}
