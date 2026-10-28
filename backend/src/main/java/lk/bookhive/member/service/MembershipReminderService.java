package lk.bookhive.member.service;

import java.time.LocalDate;
import java.util.List;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.event.MembershipExpiring;
import lk.bookhive.member.repository.MemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership expiry reminders (NOTIF-4, step 317): active members whose membership ends in N days
 * (settings, 30 by default) get a {@link MembershipExpiring} event, which the notification module
 * turns into a "renew now" message. Renewing opens a few days before expiry anyway (settings).
 */
@Service
public class MembershipReminderService {

  private final MemberRepository members;
  private final SettingsService settings;
  private final ApplicationEventPublisher events;
  private final LibraryTime time;

  public MembershipReminderService(
      MemberRepository members,
      SettingsService settings,
      ApplicationEventPublisher events,
      LibraryTime time) {
    this.members = members;
    this.settings = settings;
    this.events = events;
    this.time = time;
  }

  /**
   * @return how many members were reminded
   */
  @Transactional(readOnly = true)
  public int remindExpiring() {
    LocalDate expiresAt =
        time.today().plusDays(settings.notifications().membershipExpiringDaysBefore());
    List<Member> expiring = members.findByStatusAndExpiresAt(MemberStatus.ACTIVE, expiresAt);
    expiring.forEach(
        member ->
            events.publishEvent(
                new MembershipExpiring(
                    member.getId(), member.getUserId(), member.getMemberNo(), expiresAt)));
    return expiring.size();
  }
}
