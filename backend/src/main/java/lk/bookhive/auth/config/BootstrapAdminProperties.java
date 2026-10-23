package lk.bookhive.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.bootstrap-admin.*}: the very first admin, created at startup only while no admin
 * exists (env BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD). Every later admin is invited by an
 * existing one.
 *
 * @param email leave empty to skip
 * @param password must pass the normal password rules; remove it from the environment afterwards
 */
@ConfigurationProperties("bookhive.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String fullName) {

  boolean isConfigured() {
    return email != null && !email.isBlank() && password != null && !password.isBlank();
  }

  // Never print the password into logs
  @Override
  public String toString() {
    return "BootstrapAdminProperties[email=" + email + "]";
  }
}
