package lk.bookhive.community.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.community.domain.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

  Page<WishlistItem> findByMemberIdOrderByAddedAtDesc(UUID memberId, Pageable pageable);

  Optional<WishlistItem> findByMemberIdAndBookId(UUID memberId, UUID bookId);

  long countByMemberId(UUID memberId);
}
