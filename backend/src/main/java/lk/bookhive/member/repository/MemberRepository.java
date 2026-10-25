package lk.bookhive.member.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByUserId(UUID userId);

  Optional<Member> findByMemberNo(String memberNo);

  /** Staff queue. Loads type and guardian in the same query (no extra query per row). */
  @EntityGraph(attributePaths = {"membershipType", "guardian"})
  Page<Member> findByStatus(MemberStatus status, Pageable pageable);

  /** Next value of the member number sequence (V9 migration). */
  @Query(value = "SELECT nextval('member_no_seq')", nativeQuery = true)
  long nextMemberNumber();
}
