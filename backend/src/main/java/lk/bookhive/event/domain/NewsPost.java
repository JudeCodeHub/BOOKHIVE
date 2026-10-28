package lk.bookhive.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/** One news post or announcement (table {@code news}, EVT-4, checklist step 335). */
@Entity
@Table(name = "news")
public class NewsPost extends BaseEntity {

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 500)
  private String summary;

  @Column(nullable = false, columnDefinition = "text")
  private String bodyHtml;

  @Column(length = 300)
  private String imageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NewsStatus status;

  @Column(nullable = false)
  private boolean pinned;

  private Instant publishedAt;

  @Column(updatable = false)
  private UUID authorId;

  protected NewsPost() {
    // for JPA
  }

  /**
   * What staff fill in.
   *
   * @param bodyHtml already sanitised (step 336)
   */
  public record Details(String title, String summary, String bodyHtml, boolean pinned) {}

  public static NewsPost draft(Details details, UUID authorId) {
    NewsPost post = new NewsPost();
    post.status = NewsStatus.DRAFT;
    post.authorId = authorId;
    post.update(details);
    return post;
  }

  public void update(Details details) {
    this.title = details.title().strip();
    this.summary = details.summary() == null ? "" : details.summary().strip();
    this.bodyHtml = details.bodyHtml() == null ? "" : details.bodyHtml();
    this.pinned = details.pinned();
  }

  /** On the website. The first publish sets the date shown; publishing again keeps it. */
  public void publish(Instant now) {
    if (status == NewsStatus.PUBLISHED) {
      throw new ConflictException("NEWS_ALREADY_PUBLISHED", "This post is already published.");
    }
    this.status = NewsStatus.PUBLISHED;
    if (publishedAt == null) {
      this.publishedAt = now;
    }
  }

  /** Back to a draft (taken off the website, e.g. to fix a mistake). */
  public void unpublish() {
    if (status != NewsStatus.PUBLISHED) {
      throw new ConflictException("NEWS_NOT_PUBLISHED", "This post isn't published.");
    }
    this.status = NewsStatus.DRAFT;
  }

  public void setImageKey(String imageKey) {
    this.imageKey = imageKey;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getBodyHtml() {
    return bodyHtml;
  }

  public String getImageKey() {
    return imageKey;
  }

  public NewsStatus getStatus() {
    return status;
  }

  public boolean isPinned() {
    return pinned;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public UUID getAuthorId() {
    return authorId;
  }
}
