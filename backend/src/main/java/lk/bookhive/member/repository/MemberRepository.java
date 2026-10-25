package lk.bookhive.member.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  // The guardian is loaded in the same query: responses are built after the transaction ends
  // (open-in-view is off), when a lazy reference could no longer be loaded.
  @Override
  @EntityGraph(attributePaths = "guardian")
  Optional<Member> findById(UUID id);

  @EntityGraph(attributePaths = "guardian")
  Optional<Member> findByUserId(UUID userId);

  @EntityGraph(attributePaths = "guardian")
  Optional<Member> findByMemberNo(String memberNo);

  /** Staff queue. Loads type and guardian in the same query (no extra query per row). */
  @EntityGraph(attributePaths = {"membershipType", "guardian"})
  Page<Member> findByStatus(MemberStatus status, Pageable pageable);

  /**
   * Staff member search (MEM-8): exact member number, NIC (by its hash), phone or email, or part of
   * the name. Members only (not open or rejected applications). Joins the users table in SQL
   * because name, email and phone are account data.
   */
  @Query(
      value =
          """
          SELECT m.* FROM members m JOIN users u ON u.id = m.user_id
          WHERE m.status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED')
            AND (m.member_no = :exact
                 OR m.nic_hash = :nicHash
                 OR u.phone = :exact
                 OR u.email = lower(:exact)
                 OR u.full_name ILIKE '%' || :namePart || '%')
          ORDER BY u.full_name
          """,
      countQuery =
          """
          SELECT count(*) FROM members m JOIN users u ON u.id = m.user_id
          WHERE m.status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED')
            AND (m.member_no = :exact
                 OR m.nic_hash = :nicHash
                 OR u.phone = :exact
                 OR u.email = lower(:exact)
                 OR u.full_name ILIKE '%' || :namePart || '%')
          """,
      nativeQuery = true)
  Page<Member> search(
      @Param("exact") String exact,
      @Param("nicHash") String nicHash,
      @Param("namePart") String namePart,
      Pageable pageable);

  /** Junior members this member is guardian of. */
  List<Member> findByGuardianId(UUID guardianId);

  /** Next value of the member number sequence (V9 migration). */
  @Query(value = "SELECT nextval('member_no_seq')", nativeQuery = true)
  long nextMemberNumber();
}
