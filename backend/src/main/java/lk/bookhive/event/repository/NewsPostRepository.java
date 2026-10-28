package lk.bookhive.event.repository;

import java.util.UUID;
import lk.bookhive.event.domain.NewsPost;
import lk.bookhive.event.domain.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsPostRepository extends JpaRepository<NewsPost, UUID> {

  /** The public list: pinned first, then newest. */
  Page<NewsPost> findByStatusOrderByPinnedDescPublishedAtDesc(NewsStatus status, Pageable pageable);

  /** Staff list: everything, most recently changed first. */
  Page<NewsPost> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
