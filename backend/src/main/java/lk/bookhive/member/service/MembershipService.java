package lk.bookhive.member.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.event.MembershipFeeCharged;
import lk.bookhive.member.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Life of an existing membership: renewal (MEM-6), suspend and reactivate (MEM-7). */
@Service
public class MembershipService {

  private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

  /** Renewing opens this many days before the expiry date. */
  static final int RENEWAL_WINDOW_DAYS = 30;

  private final MemberRepository members;
  private final ApplicationEventPublisher events;
  private final AuditService audit;
  private final LibraryTime time;

  public MembershipService(
      MemberRepository members,
      ApplicationEventPublisher events,
      AuditService audit,
      LibraryTime time) {
    this.members = members;
    this.events = events;
    this.audit = audit;
    this.time = time;
  }

  /** The result of a renewal: the member and the fee now owed (zero for free types). */
  public record Renewal(Member member, Money fee) {}

  /** The logged-in user's own membership (null if they never applied). */
  @Transactional(readOnly = true)
  public Member findOwn(UUID userId) {
    return members.findByUserId(userId).orElse(null);
  }

  /** The member renews online (POST /me/membership/renew). */
  @Transactional
  public Renewal renewOwn(UUID userId) {
    Member member =
        members
            .findByUserId(userId)
            .orElseThrow(
                () -> new NotFoundException("MEMBERSHIP_NOT_FOUND", "You are not a member yet."));
    return renew(member, userId);
  }

  /** Staff renew at the desk, e.g. when the member pays in cash. */
  @Transactional
  public Renewal renewByStaff(UUID memberId, UUID staffId) {
    return renew(require(memberId), staffId);
  }

  private Renewal renew(Member member, UUID actorId) {
    LocalDate today = time.today();
    if (member.getStatus() == MemberStatus.SUSPENDED) {
      throw new BusinessRuleException(
          "MEMBERSHIP_SUSPENDED",
          "A suspended membership can't be renewed. Please contact the library.");
    }
    // Not too early: otherwise someone could stack up several years ahead
    if (member.getStatus() == MemberStatus.ACTIVE
        && !member.isExpiredOn(today)
        && member.getExpiresAt().isAfter(today.plusDays(RENEWAL_WINDOW_DAYS))) {
      throw new BusinessRuleException(
          "RENEWAL_TOO_EARLY",
          "You can renew from " + member.getExpiresAt().minusDays(RENEWAL_WINDOW_DAYS) + ".");
    }

    LocalDate newExpiry = member.renew(today);
    Money fee = member.getMembershipType().getAnnualFee();
    if (!fee.isZero()) {
      events.publishEvent(
          new MembershipFeeCharged(
              member.getId(), member.getUserId(), member.getMemberNo(), fee, newExpiry));
    }
    Map<String, Object> details = new HashMap<>();
    details.put("expiresAt", newExpiry.toString());
    details.put("fee", fee.amount().toPlainString());
    audit.record("member.renewed", actorId, "member", member.getId(), details);
    log.info("Membership {} renewed until {}", member.getMemberNo(), newExpiry);
    return new Renewal(member, fee);
  }

  /** Blocks borrowing until a librarian reactivates the membership. */
  @Transactional
  public Member suspend(UUID memberId, UUID staffId, String reason) {
    Member member = require(memberId);
    member.suspend(reason.strip());
    audit.record(
        "member.suspended", staffId, "member", member.getId(), Map.of("reason", reason.strip()));
    log.info("Membership {} suspended", member.getMemberNo());
    return member;
  }

  @Transactional
  public Member reactivate(UUID memberId, UUID staffId, String note) {
    Member member = require(memberId);
    member.reactivate(time.today());
    Map<String, Object> details = new HashMap<>();
    details.put("status", member.getStatus().name());
    if (note != null && !note.isBlank()) {
      details.put("note", note.strip());
    }
    audit.record("member.reactivated", staffId, "member", member.getId(), details);
    log.info("Membership {} reactivated as {}", member.getMemberNo(), member.getStatus());
    return member;
  }

  private Member require(UUID memberId) {
    return members
        .findById(memberId)
        .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found."));
  }
}
