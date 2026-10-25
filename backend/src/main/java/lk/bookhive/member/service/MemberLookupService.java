package lk.bookhive.member.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.crypto.FieldEncryption;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finding members at the desk (MEM-8) and their profile for staff (checklist step 144). */
@Service
public class MemberLookupService {

  private final MemberRepository members;
  private final AuthService authService;
  private final FieldEncryption encryption;

  public MemberLookupService(
      MemberRepository members, AuthService authService, FieldEncryption encryption) {
    this.members = members;
    this.authService = authService;
    this.encryption = encryption;
  }

  /** A member with their account details (name, email, phone live in the auth module). */
  public record MemberView(Member member, User user) {}

  /** A member's full profile: also their guardian and the juniors they look after. */
  public record MemberProfile(Member member, User user, List<MemberView> juniors) {}

  /**
   * One search box for everything staff type or scan: "BH-000123", "851234567V", "0771234567",
   * "nimali@mail.lk" or "nimali".
   */
  @Transactional(readOnly = true)
  public Page<MemberView> search(String query, int page, int size) {
    String q = query.strip();
    String exact = q.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    // Phones are stored as typed at registration (07... or +94...); try the exact text too
    Page<Member> found =
        members.search(
            exact.startsWith("BH-") ? exact : q.replaceAll("\\s", ""),
            encryption.lookupHash(exact),
            q,
            PageRequest.of(page, size));
    Set<UUID> userIds = found.stream().map(Member::getUserId).collect(Collectors.toSet());
    Map<UUID, User> users = authService.findUsers(userIds);
    return found.map(member -> new MemberView(member, users.get(member.getUserId())));
  }

  /** A member already loaded (e.g. a guardian) with their account details. */
  @Transactional(readOnly = true)
  public MemberView profileView(Member member) {
    return new MemberView(member, authService.getProfile(member.getUserId()));
  }

  @Transactional(readOnly = true)
  public MemberProfile profile(UUID memberId) {
    Member member =
        members
            .findById(memberId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found."));
    List<Member> juniors = members.findByGuardianId(memberId);
    Map<UUID, User> users =
        authService.findUsers(juniors.stream().map(Member::getUserId).collect(Collectors.toSet()));
    return new MemberProfile(
        member,
        authService.getProfile(member.getUserId()),
        juniors.stream()
            .map(junior -> new MemberView(junior, users.get(junior.getUserId())))
            .toList());
  }
}
