package lk.bookhive.circulation.job;

import lk.bookhive.circulation.service.LoanReminderService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning (library time): remind members whose books are due in a few days. */
@Component
public class LoanReminderJob {

  private static final Logger log = LoggerFactory.getLogger(LoanReminderJob.class);

  private final LoanReminderService reminders;

  public LoanReminderJob(LoanReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(cron = "${bookhive.jobs.due-soon-cron}", zone = "Asia/Colombo")
  @SchedulerLock(name = "remindDueSoon", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void dueSoon() {
    int members = reminders.remindDueSoon();
    log.info("Due-soon reminders: {} member(s)", members);
  }
}
