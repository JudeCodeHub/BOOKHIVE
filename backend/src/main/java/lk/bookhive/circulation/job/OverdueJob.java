package lk.bookhive.circulation.job;

import lk.bookhive.circulation.service.OverdueService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every night just after midnight (library time): mark loans past their due date overdue. */
@Component
public class OverdueJob {

  private static final Logger log = LoggerFactory.getLogger(OverdueJob.class);

  private final OverdueService overdue;

  public OverdueJob(OverdueService overdue) {
    this.overdue = overdue;
  }

  // lockAtLeastFor: an instance whose clock is a little behind can't run it again right after
  @Scheduled(cron = "${bookhive.jobs.overdue-cron}", zone = "Asia/Colombo")
  @SchedulerLock(name = "markOverdueLoans", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void run() {
    int marked = overdue.markOverdue();
    log.info("Overdue job: {} loan(s) marked overdue", marked);
  }
}
