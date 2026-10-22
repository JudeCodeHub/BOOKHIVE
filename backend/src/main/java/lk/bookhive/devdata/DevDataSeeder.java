package lk.bookhive.devdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fills the local database with sample data when the app starts in the {@code dev} profile. Never
 * runs in test or prod. Each seed method must be safe to run on every restart (skip if the data is
 * already there).
 *
 * <p>Planned: staff/admin users (Phase 4), membership types and members (Phase 5), 200 books (step
 * 229), loan history (step 293).
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

  @Override
  public void run(ApplicationArguments args) {
    log.info("Dev data seeder: nothing to seed yet");
  }
}
