package lk.bookhive.member.job;

import lk.bookhive.member.service.MembershipReminderService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning (library time): remind members whose membership ends in a few weeks. */
@Component
public class MembershipReminderJob {

  private static final Logger log = LoggerFactory.getLogger(MembershipReminderJob.class);

  private final MembershipReminderService reminders;

  public MembershipReminderJob(MembershipReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(cron = "${bookhive.jobs.membership-expiring-cron}", zone = "Asia/Colombo")
  @SchedulerLock(
      name = "remindMembershipExpiring",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT1M")
  public void run() {
    int members = reminders.remindExpiring();
    log.info("Membership expiry reminders: {} member(s)", members);
  }
}
