package lk.bookhive.auth.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** How passwords are hashed before they are stored in {@code users.password_hash}. */
@Configuration
public class PasswordConfig {

  /**
   * Cost factor 12 = 2^12 rounds, about a quarter of a second per hash. Slow on purpose: a login
   * barely notices, but an attacker with a stolen database can only test a few guesses per second
   * per password instead of billions.
   */
  static final int BCRYPT_STRENGTH = 12;

  private static final String BCRYPT = "bcrypt";

  /**
   * BCrypt, with the algorithm written in front of every hash: {@code {bcrypt}$2a$12$...}. If we
   * ever move to a stronger algorithm, old hashes still verify and get upgraded on next login.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        BCRYPT, Map.of(BCRYPT, new BCryptPasswordEncoder(BCRYPT_STRENGTH)));
  }
}
