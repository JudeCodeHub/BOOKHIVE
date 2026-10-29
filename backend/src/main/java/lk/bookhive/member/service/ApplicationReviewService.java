package lk.bookhive.member.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.storage.FileStorage;
import lk.bookhive.common.storage.StorageProperties;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberDocument;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.event.MembershipApproved;
import lk.bookhive.member.event.MembershipFeeCharged;
import lk.bookhive.member.event.MembershipRejected;
import lk.bookhive.member.repository.MemberDocumentRepository;
import lk.bookhive.member.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff review of membership applications (MEM-3, checklist steps 130-131). */
@Service
public class ApplicationReviewService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationReviewService.class);

  private final MemberRepository members;
  private final MemberDocumentRepository documents;
  private final MemberNumberGenerator memberNumbers;
  private final AuthService authService;
  private final FileStorage storage;
  private final StorageProperties storageProperties;
  private final ApplicationEventPublisher events;
  private final AuditService audit;
  private final LibraryTime time;

  /** Applications waiting for a decision (staff dashboard, step 360). */
  @Transactional(readOnly = true)
  public long pendingCount() {
    return members.countByStatus(MemberStatus.PENDING);
  }

  public ApplicationReviewService(
      MemberRepository members,
      MemberDocumentRepository documents,
      MemberNumberGenerator memberNumbers,
      AuthService authService,
      FileStorage storage,
      StorageProperties storageProperties,
      ApplicationEventPublisher events,
      AuditService audit,
      LibraryTime time) {
    this.members = members;
    this.documents = documents;
    this.memberNumbers = memberNumbers;
    this.authService = authService;
    this.storage = storage;
    this.storageProperties = storageProperties;
    this.events = events;
    this.audit = audit;
    this.time = time;
  }

  /** An application with the applicant's account details (from the auth module). */
  public record ApplicationView(Member member, User applicant) {}

  /** A document plus a short-lived link to view it (the bucket itself is private). */
  public record DocumentView(MemberDocument document, URI link) {}

  /** Pending applications, oldest first, so nobody waits longer than necessary. */
  @Transactional(readOnly = true)
  public Page<ApplicationView> pending(int page, int size) {
    Page<Member> applications =
        members.findByStatus(
            MemberStatus.PENDING, PageRequest.of(page, size, Sort.by("appliedAt").ascending()));
    Set<UUID> userIds = applications.stream().map(Member::getUserId).collect(Collectors.toSet());
    Map<UUID, User> users = authService.findUsers(userIds);
    return applications.map(member -> new ApplicationView(member, users.get(member.getUserId())));
  }

  @Transactional(readOnly = true)
  public ApplicationView get(UUID applicationId) {
    Member member = require(applicationId);
    return new ApplicationView(member, authService.getProfile(member.getUserId()));
  }

  /** The uploaded proof, with links that stop working after a few minutes. */
  @Transactional(readOnly = true)
  public List<DocumentView> documents(UUID applicationId) {
    return documents.findByMemberId(applicationId).stream()
        .map(
            document ->
                new DocumentView(
                    document,
                    storage.temporaryLink(
                        document.getStorageKey(), storageProperties.linkValidity())))
        .toList();
  }

  /** Accept: gives the member number and starts the one-year membership; emails the member. */
  @Transactional
  public Member approve(UUID applicationId, UUID staffId) {
    Member member = requirePending(applicationId);
    member.approve(staffId, memberNumbers.next(), time.today(), time.now());
    User user = authService.getProfile(member.getUserId());
    events.publishEvent(
        new MembershipApproved(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPreferredLanguage(),
            member.getMemberNo(),
            member.getMembershipType().getName(user.getPreferredLanguage()),
            member.getExpiresAt()));
    // A paid type: the first year's fee is owed from today (FIN-2), like on every renewal
    Money fee = member.getMembershipType().getAnnualFee();
    if (!fee.isZero()) {
      events.publishEvent(
          new MembershipFeeCharged(
              member.getId(),
              member.getUserId(),
              member.getMemberNo(),
              fee,
              member.getExpiresAt()));
    }
    audit.record(
        "member.approved",
        staffId,
        "member",
        member.getId(),
        Map.of("memberNo", member.getMemberNo()));
    log.info("Application {} approved as {}", member.getId(), member.getMemberNo());
    return member;
  }

  /** Decline with a reason the applicant can act on; they may apply again afterwards. */
  @Transactional
  public Member reject(UUID applicationId, UUID staffId, String reason) {
    Member member = requirePending(applicationId);
    member.reject(reason.strip());
    User user = authService.getProfile(member.getUserId());
    events.publishEvent(
        new MembershipRejected(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPreferredLanguage(),
            member.getStatusReason()));
    audit.record("member.rejected", staffId, "member", member.getId(), Map.of());
    log.info("Application {} rejected", member.getId());
    return member;
  }

  private Member requirePending(UUID applicationId) {
    Member member = require(applicationId);
    if (!member.isPending()) {
      throw new ConflictException(
          "APPLICATION_NOT_PENDING", "This application has already been decided.");
    }
    return member;
  }

  private Member require(UUID applicationId) {
    return members
        .findById(applicationId)
        .orElseThrow(
            () -> new NotFoundException("APPLICATION_NOT_FOUND", "Application not found."));
  }
}
