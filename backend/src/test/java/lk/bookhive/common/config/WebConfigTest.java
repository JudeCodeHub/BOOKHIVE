package lk.bookhive.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WebConfigTest.PingController.class)
@WithMockUser
class WebConfigTest {

  @Autowired private MockMvc mvc;

  @Test
  void bookHiveControllersGetApiPrefix() throws Exception {
    mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
    mvc.perform(get("/ping")).andExpect(status().isNotFound());
  }

  @Test
  void libraryControllersKeepTheirPaths() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @RestController
  static class PingController {

    @GetMapping("/ping")
    String ping() {
      return "pong";
    }
  }
}
