package lk.bookhive;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Infrastructure for integration tests.
 *
 * <ul>
 *   <li>A real PostgreSQL 16 in Docker (same version as infra/docker-compose.yml).
 *       {@code @ServiceConnection} points the datasource at it automatically. Spring caches the
 *       test context, so the container is started once and shared by all integration tests in a
 *       run.
 *   <li>Emails are recorded instead of sent over SMTP.
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer("postgres:16-alpine");
  }

  @Bean
  @Primary
  RecordingEmailSender recordingEmailSender() {
    return new RecordingEmailSender();
  }
}
