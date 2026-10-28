package lk.bookhive.event.job;

import lk.bookhive.event.service.EventReminderService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning (library time): remind members about the events they're going to tomorrow. */
@Component
public class EventReminderJob {

  private static final Logger log = LoggerFactory.getLogger(EventReminderJob.class);

  private final EventReminderService reminders;

  public EventReminderJob(EventReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(cron = "${bookhive.jobs.event-reminder-cron}", zone = "Asia/Colombo")
  @SchedulerLock(name = "remindEvents", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void run() {
    int members = reminders.remindTomorrow();
    log.info("Event reminders: {} member(s)", members);
  }
}
