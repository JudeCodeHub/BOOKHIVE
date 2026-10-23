package lk.bookhive.auth.config;

import lk.bookhive.auth.service.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin at startup if configured and no admin exists yet. Someone has to be able
 * to log in first and invite everyone else (checklist step 105).
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

  private final AuthService authService;
  private final BootstrapAdminProperties properties;

  public AdminBootstrap(AuthService authService, BootstrapAdminProperties properties) {
    this.authService = authService;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (properties.isConfigured()) {
      authService.bootstrapAdmin(properties.email(), properties.password(), properties.fullName());
    }
  }
}
