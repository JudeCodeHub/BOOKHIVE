package lk.bookhive.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import lk.bookhive.IntegrationTest;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The URL rules with real tokens. None of these endpoints exist yet, so "allowed" shows up as 404
 * (security let it through, then no controller matched) and "blocked" as 401/403.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;

  @Test
  void healthCheckIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void publicCatalogNeedsNoLogin() throws Exception {
    mvc.perform(get("/api/v1/books/123")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/auth/anything")).andExpect(status().isNotFound());
  }

  @Test
  void protectedEndpointWithoutTokenIs401ProblemDetail() throws Exception {
    mvc.perform(get("/api/v1/me/loans"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void invalidTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/me/loans").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void anyLoggedInUserReachesMemberEndpoints() throws Exception {
    mvc.perform(as(get("/api/v1/me/loans"), Role.MEMBER)).andExpect(status().isNotFound());
  }

  @Test
  void memberCannotUseStaffEndpoints() throws Exception {
    mvc.perform(as(get("/api/v1/staff/loans"), Role.MEMBER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void librarianUsesStaffButNotAdminEndpoints() throws Exception {
    mvc.perform(as(get("/api/v1/staff/loans"), Role.LIBRARIAN)).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/admin/settings"), Role.LIBRARIAN))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminIsAlsoALibrarian() throws Exception {
    mvc.perform(as(get("/api/v1/staff/loans"), Role.ADMIN)).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/admin/settings"), Role.ADMIN)).andExpect(status().isNotFound());
  }

  @Test
  void apiDocsNeedLoginOutsideDev() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
  }

  @Test
  void corsPreflightFromFrontendIsAllowed() throws Exception {
    mvc.perform(
            options("/api/v1/me/loans")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  @Test
  void corsPreflightFromOtherSiteIsRejected() throws Exception {
    mvc.perform(
            options("/api/v1/me/loans")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Role role) {
    String token = jwtService.issueAccessToken(UUID.randomUUID(), List.of(role)).value();
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }
}
