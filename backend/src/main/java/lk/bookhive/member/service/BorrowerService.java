package lk.bookhive.member.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.domain.MembershipType;
import lk.bookhive.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What circulation needs to know about a member before lending (checklist step 220). */
@Service
public class BorrowerService {

  private final MemberRepository members;

  public BorrowerService(MemberRepository members) {
    this.members = members;
  }

  /**
   * A member as circulation sees them: status, expiry and their type's borrowing limits.
   *
   * @param allowedSections library sections they may borrow from; null = all
   */
  public record Borrower(
      UUID memberId,
      UUID userId,
      String memberNo,
      MemberStatus status,
      LocalDate expiresAt,
      int maxItems,
      int loanDays,
      int maxRenewals,
      int maxHolds,
      List<String> allowedSections) {

    public boolean mayBorrowFrom(String section) {
      return allowedSections == null || allowedSections.contains(section);
    }
  }

  @Transactional(readOnly = true)
  public Optional<Borrower> byId(UUID memberId) {
    return members.findById(memberId).map(BorrowerService::borrower);
  }

  /** The desk scans the library card: "BH-000123". */
  @Transactional(readOnly = true)
  public Optional<Borrower> byMemberNo(String memberNo) {
    return members
        .findByMemberNo(memberNo.strip().toUpperCase(Locale.ROOT))
        .map(BorrowerService::borrower);
  }

  /** The logged-in member's own record (member renewals). */
  @Transactional(readOnly = true)
  public Optional<Borrower> byUserId(UUID userId) {
    return members.findByUserId(userId).map(BorrowerService::borrower);
  }

  private static Borrower borrower(Member member) {
    MembershipType type = member.getMembershipType();
    return new Borrower(
        member.getId(),
        member.getUserId(),
        member.getMemberNo(),
        member.getStatus(),
        member.getExpiresAt(),
        type.getMaxItems(),
        type.getLoanDays(),
        type.getMaxRenewals(),
        type.getMaxHolds(),
        type.getAllowedSections());
  }
}
