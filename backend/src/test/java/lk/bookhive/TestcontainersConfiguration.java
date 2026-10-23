package lk.bookhive;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Infrastructure for integration tests, the same versions as infra/docker-compose.yml:
 *
 * <ul>
 *   <li>A real PostgreSQL 16 and Redis 8 in Docker. {@code @ServiceConnection} points Spring at
 *       them automatically. Spring caches the test context, so the containers start once and are
 *       shared by all integration tests in a run.
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
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
  }

  @Bean
  @Primary
  RecordingEmailSender recordingEmailSender() {
    return new RecordingEmailSender();
  }
}
