package lk.bookhive;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BookhiveApplicationTests {

  @Autowired private DataSource dataSource;

  // Fails if any bean, config value or migration is broken.
  @Test
  void contextLoads() {}

  @Test
  void flywayRanMigrationsOnTestDatabase() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    assertThat(jdbc.queryForObject("select current_setting('server_version')", String.class))
        .startsWith("16.");
    assertThat(jdbc.queryForList("select extname from pg_extension", String.class))
        .contains("pgcrypto", "citext", "pg_trgm");
  }
}
