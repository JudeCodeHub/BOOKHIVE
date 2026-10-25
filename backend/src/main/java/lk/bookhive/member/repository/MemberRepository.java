package lk.bookhive.member.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByUserId(UUID userId);

  /** Next value of the member number sequence (V9 migration). */
  @Query(value = "SELECT nextval('member_no_seq')", nativeQuery = true)
  long nextMemberNumber();
}
