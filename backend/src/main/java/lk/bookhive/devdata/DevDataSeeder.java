package lk.bookhive.devdata;

import lk.bookhive.fine.service.FineAccrualService;
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
 * <p>Seeds: 200 books with copies (DevCatalogSeeder), then 12 members with a year of loans
 * (DevLoanSeeder). Then overdue fines are brought up to today, so the fines screens have data
 * without waiting for the nightly job (it's safe to run any time: it only tops fines up).
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

  private final DevCatalogSeeder catalog;
  private final DevLoanSeeder loans;
  private final FineAccrualService fineAccrual;

  public DevDataSeeder(
      DevCatalogSeeder catalog, DevLoanSeeder loans, FineAccrualService fineAccrual) {
    this.catalog = catalog;
    this.loans = loans;
    this.fineAccrual = fineAccrual;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      catalog.seed();
      loans.seed();
      fineAccrual.accrue();
    } catch (RuntimeException e) {
      // Sample data is a convenience: never stop the app from starting because of it
      log.warn("Dev data seeding failed: {}", e.getMessage(), e);
    }
  }
}
