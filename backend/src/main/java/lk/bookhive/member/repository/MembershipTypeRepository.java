package lk.bookhive.member.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.member.domain.MembershipType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipTypeRepository extends JpaRepository<MembershipType, UUID> {

  Optional<MembershipType> findByCode(String code);

  /** The types someone can apply for now (retired ones are left out). */
  List<MembershipType> findByActiveTrueOrderByCode();
}
