package lk.bookhive.hold.job;

import lk.bookhive.hold.service.HoldExpiryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every night (library time): expire uncollected holds, then remind about last pickup days. */
@Component
public class HoldExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

  private final HoldExpiryService expiry;

  public HoldExpiryJob(HoldExpiryService expiry) {
    this.expiry = expiry;
  }

  @Scheduled(cron = "${bookhive.jobs.hold-expiry-cron}", zone = "Asia/Colombo")
  @SchedulerLock(name = "expireHolds", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void run() {
    int expired = expiry.expireOverdue();
    int reminded = expiry.remindExpiring();
    log.info("Hold job: {} expired, {} pickup reminder(s)", expired, reminded);
  }
}
