package lk.bookhive.member.domain;

import jakarta.persistence.Column;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "membership_type_id", nullable = false)
  private MembershipType membershipType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MemberStatus status;

  // Encrypted with AES-GCM by a JPA converter in checklist step 128
  @Column(name = "nic_encrypted")
  private String nic;

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
    if (status != MemberStatus.REJECTED) {
      throw new IllegalStateException("Only a rejected application can be submitted again");
    }
    fill(details, now);
  }

  private void fill(ApplicationDetails details, Instant now) {
    this.membershipType = details.membershipType();
    this.nic = details.nic();
    this.dateOfBirth = details.dateOfBirth();
    this.addressLine = details.addressLine();
    this.city = details.city();
    this.postalCode = details.postalCode();
    this.status = MemberStatus.PENDING;
    this.statusReason = null;
    this.appliedAt = now;
  }

  /** What the applicant fills in; already validated by the service. */
  public record ApplicationDetails(
      MembershipType membershipType,
      String nic,
      LocalDate dateOfBirth,
      String addressLine,
      String city,
      String postalCode) {}

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
