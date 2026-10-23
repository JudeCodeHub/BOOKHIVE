package lk.bookhive.auth.config;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import jakarta.servlet.DispatcherType;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.service.JwtService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Who may call what (ADR 0001, PRD §3).
 *
 * <ul>
 *   <li>Stateless: no server session; every request proves itself with "Authorization: Bearer".
 *   <li>Deny by default: a URL not listed as public needs a valid token.
 *   <li>/staff/** needs LIBRARIAN, /admin/** needs ADMIN (an ADMIN is also a LIBRARIAN).
 * </ul>
 *
 * <p>Fine-grained rules live in the services: {@code @PreAuthorize} on a method (enabled here) for
 * role checks, and plain code for "only the owner may see this loan".
 */
@Configuration
// Second line of defence: @PreAuthorize("hasRole('ADMIN')") on a service method holds even if a
// URL rule above is wrong or the method is called from somewhere new
@EnableMethodSecurity
public class SecurityConfig {

  private static final String API = "/api/v1";

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      AuthenticationEntryPoint problemEntryPoint,
      AccessDeniedHandler problemAccessDeniedHandler,
      @Value("${bookhive.security.public-api-docs:false}") boolean publicApiDocs)
      throws Exception {
    http
        // Bearer tokens are not sent automatically by browsers, so CSRF doesn't apply to them.
        // The two cookie endpoints (refresh, logout) get CSRF protection in checklist step 393.
        .csrf(AbstractHttpConfigurer::disable)
        // Uses the CorsConfigurationSource bean (common/config/CorsConfig)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              // Spring's internal error forwarding must not be blocked
              auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
              auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
              if (publicApiDocs) {
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              // Register, login, refresh, logout, verify email, forgot/reset password
              auth.requestMatchers(API + "/auth/**").permitAll();
              // Public website data (PRD §10): anyone can browse the catalog and library info
              auth.requestMatchers(
                      GET,
                      API + "/books/**",
                      API + "/search/**",
                      API + "/categories/**",
                      API + "/authors/**",
                      API + "/events/**",
                      API + "/news/**",
                      API + "/library/**")
                  .permitAll();
              auth.requestMatchers(POST, API + "/contact").permitAll();
              // PayHere calls this server-to-server; its signature is checked in code (PAY-2)
              auth.requestMatchers(POST, API + "/payments/payhere/notify").permitAll();

              auth.requestMatchers(API + "/admin/**").hasRole(Role.ADMIN.name());
              auth.requestMatchers(API + "/staff/**").hasRole(Role.LIBRARIAN.name());
              auth.anyRequest().authenticated();
            })
        // Reads and checks "Authorization: Bearer <jwt>" using the JwtDecoder bean
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(problemEntryPoint))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(problemEntryPoint)
                    .accessDeniedHandler(problemAccessDeniedHandler));
    return http.build();
  }

  /**
   * Token claim "roles": ["LIBRARIAN"] becomes the authority ROLE_LIBRARIAN; sub is the user id.
   */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtService.ROLES_CLAIM);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  /** An ADMIN can do everything a LIBRARIAN can (PRD §3), without needing both roles. */
  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role(Role.ADMIN.name())
        .implies(Role.LIBRARIAN.name())
        .build();
  }

  // 401/403 raised inside the security filters are handed to GlobalExceptionHandler, so they
  // get the same ProblemDetail JSON (with "code") as every other error.

  @Bean
  AuthenticationEntryPoint problemEntryPoint(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return (request, response, ex) -> {
      response.setHeader("WWW-Authenticate", "Bearer");
      resolver.resolveException(request, response, null, ex);
    };
  }

  @Bean
  AccessDeniedHandler problemAccessDeniedHandler(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
  }
}
