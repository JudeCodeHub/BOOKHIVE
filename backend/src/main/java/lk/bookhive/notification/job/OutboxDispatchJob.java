package lk.bookhive.notification.job;

import lk.bookhive.notification.service.OutboxDispatcher;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every few seconds: send queued email and SMS (bookhive.outbox.poll-interval). */
@Component
public class OutboxDispatchJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchJob.class);

  private final OutboxDispatcher dispatcher;

  public OutboxDispatchJob(OutboxDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  // ShedLock: one instance sends at a time (SKIP LOCKED would also keep them apart)
  @Scheduled(fixedDelayString = "${bookhive.outbox.poll-interval}")
  @SchedulerLock(name = "dispatchOutbox", lockAtMostFor = "PT5M")
  public void run() {
    int sent = dispatcher.dispatchDue();
    if (sent > 0) {
      log.info("Outbox: {} message(s) sent", sent);
    }
  }
}
