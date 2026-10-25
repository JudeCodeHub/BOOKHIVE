package lk.bookhive.member.repository;

import java.util.List;
import java.util.UUID;
import lk.bookhive.member.domain.MemberDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDocumentRepository extends JpaRepository<MemberDocument, UUID> {

  List<MemberDocument> findByMemberId(UUID memberId);
}
