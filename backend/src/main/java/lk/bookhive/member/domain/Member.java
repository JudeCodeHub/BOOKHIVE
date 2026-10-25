package lk.bookhive.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/**
 * A library member (table {@code members}): a user account plus library data. Starts as an
 * application ({@link MemberStatus#PENDING}) and becomes a member when staff approve it.
 *
 * <p>The user is referenced by id only: accounts belong to the auth module.
 */
@Entity
@Table(name = "members")
public class Member extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Column(length = 12)
  private String memberNo;

  // Eager: almost every use needs the type's rules, and there are only a handful of types
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "membership_type_id", nullable = false)
  private MembershipType membershipType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MemberStatus status;

  // Encrypted in the database (AES-GCM, NicConverter); plain text only in memory
  @Convert(converter = NicConverter.class)
  @Column(name = "nic_encrypted")
  private String nic;

  // HMAC of the NIC, so staff can still find a member by NIC (MEM-8)
  @Column(length = 64)
  private String nicHash;

  @Column(nullable = false)
  private LocalDate dateOfBirth;

  @Column(nullable = false, length = 200)
  private String addressLine;

  @Column(nullable = false, length = 80)
  private String city;

  @Column(length = 10)
  private String postalCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "guardian_member_id")
  private Member guardian;

  @Column(nullable = false)
  private Instant appliedAt;

  private UUID approvedBy;

  private Instant approvedAt;

  private LocalDate expiresAt;

  @Column(length = 500)
  private String statusReason;

  @Column(nullable = false)
  private boolean readingHistoryEnabled = true;

  protected Member() {
    // for JPA
  }

  /** A new membership application, waiting for staff review. */
  public static Member apply(UUID userId, ApplicationDetails details, Instant now) {
    Member member = new Member();
    member.userId = userId;
    member.fill(details, now);
    return member;
  }

  /** After a rejection the user may apply again: same row, fresh application. */
  public void reapply(ApplicationDetails details, Instant now) {
    moveTo(MemberStatus.PENDING);
    fill(details, now);
  }

  private void fill(ApplicationDetails details, Instant now) {
    this.membershipType = details.membershipType();
    this.nic = details.nic();
    this.nicHash = details.nicHash();
    this.dateOfBirth = details.dateOfBirth();
    this.addressLine = details.addressLine();
    this.city = details.city();
    this.postalCode = details.postalCode();
    this.guardian = details.guardian();
    if (status == null) {
      moveTo(MemberStatus.PENDING);
    }
    this.statusReason = null;
    this.appliedAt = now;
  }

  /**
   * Staff accept the application: the person becomes an active member for one year (PRD §4.1).
   *
   * @param memberNo a fresh number from MemberNumberGenerator
   */
  public void approve(UUID staffId, String memberNo, LocalDate today, Instant now) {
    moveTo(MemberStatus.ACTIVE);
    this.memberNo = memberNo;
    this.approvedBy = staffId;
    this.approvedAt = now;
    this.expiresAt = today.plusYears(1);
    this.statusReason = null;
  }

  /** Staff turn the application down; the reason is shown to the applicant. */
  public void reject(String reason) {
    moveTo(MemberStatus.REJECTED);
    this.statusReason = reason;
  }

  /**
   * Another year (MEM-6). Renewing before the end adds the year to the current expiry date, so
   * early renewers lose no days; after expiry the new year starts today.
   *
   * @return the new expiry date
   */
  public LocalDate renew(LocalDate today) {
    moveTo(MemberStatus.ACTIVE);
    LocalDate base = isExpiredOn(today) ? today : expiresAt;
    this.expiresAt = base.plusYears(1);
    this.statusReason = null;
    return expiresAt;
  }

  /** Staff block the member from borrowing (MEM-7), e.g. after damaging books. */
  public void suspend(String reason) {
    moveTo(MemberStatus.SUSPENDED);
    this.statusReason = reason;
  }

  /** Lifts a suspension. If the year ran out meanwhile, the member is expired instead. */
  public void reactivate(LocalDate today) {
    moveTo(isExpiredOn(today) ? MemberStatus.EXPIRED : MemberStatus.ACTIVE);
    this.statusReason = null;
  }

  /** The membership year is over (the nightly expiry job uses this too). */
  public void expire() {
    moveTo(MemberStatus.EXPIRED);
  }

  public boolean isPending() {
    return status == MemberStatus.PENDING;
  }

  /** Past the last valid day, whatever the status says (the expiry job may not have run yet). */
  public boolean isExpiredOn(LocalDate today) {
    return expiresAt != null && expiresAt.isBefore(today);
  }

  /**
   * The one place where the status changes (checklist step 132), so no code path can make an
   * illegal jump such as EXPIRED -> PENDING.
   */
  private void moveTo(MemberStatus next) {
    if (status != null && !status.canMoveTo(next)) {
      throw new ConflictException(
          "STATUS_CHANGE_NOT_ALLOWED", "A " + status + " membership can't become " + next + ".");
    }
    this.status = next;
  }

  /** What the applicant fills in; already validated by the service. */
  public record ApplicationDetails(
      MembershipType membershipType,
      String nic,
      String nicHash,
      LocalDate dateOfBirth,
      String addressLine,
      String city,
      String postalCode,
      Member guardian) {}

  public UUID getUserId() {
    return userId;
  }

  public String getMemberNo() {
    return memberNo;
  }

  public MembershipType getMembershipType() {
    return membershipType;
  }

  public MemberStatus getStatus() {
    return status;
  }

  public String getNic() {
    return nic;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getAddressLine() {
    return addressLine;
  }

  public String getCity() {
    return city;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public Member getGuardian() {
    return guardian;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public LocalDate getExpiresAt() {
    return expiresAt;
  }

  public String getStatusReason() {
    return statusReason;
  }

  public boolean isReadingHistoryEnabled() {
    return readingHistoryEnabled;
  }
}
