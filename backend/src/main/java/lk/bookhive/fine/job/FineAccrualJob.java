package lk.bookhive.fine.job;

import lk.bookhive.fine.service.FineAccrualService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every night (library time), after loans are marked overdue: add the day's overdue fines. */
@Component
public class FineAccrualJob {

  private static final Logger log = LoggerFactory.getLogger(FineAccrualJob.class);

  private final FineAccrualService accrual;

  public FineAccrualJob(FineAccrualService accrual) {
    this.accrual = accrual;
  }

  @Scheduled(cron = "${bookhive.jobs.fine-accrual-cron}", zone = "Asia/Colombo")
  @SchedulerLock(name = "accrueFines", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
  public void run() {
    int loans = accrual.accrue();
    log.info("Fine accrual job: {} overdue loan(s) checked", loans);
  }
}
