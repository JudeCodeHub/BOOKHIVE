package lk.bookhive.event.api;

import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.storage.Pictures;
import lk.bookhive.event.domain.NewsPost;
import lk.bookhive.event.domain.NewsStatus;

/** What the news endpoints return. */
public final class NewsResponses {

  private NewsResponses() {}

  /** A post in a list (no body: lists only need the summary). */
  public record NewsSummary(
      UUID id,
      String title,
      String summary,
      boolean pinned,
      NewsStatus status,
      Instant publishedAt,
      String imageVersion,
      Instant updatedAt) {

    static NewsSummary from(NewsPost post) {
      return new NewsSummary(
          post.getId(),
          post.getTitle(),
          post.getSummary(),
          post.isPinned(),
          post.getStatus(),
          post.getPublishedAt(),
          version(post),
          post.getUpdatedAt());
    }
  }

  /**
   * One post in full.
   *
   * @param bodyHtml sanitised on the server (step 336)
   */
  public record NewsPostResponse(
      UUID id,
      String title,
      String summary,
      String bodyHtml,
      boolean pinned,
      NewsStatus status,
      Instant publishedAt,
      String imageVersion,
      Instant updatedAt) {

    static NewsPostResponse from(NewsPost post) {
      return new NewsPostResponse(
          post.getId(),
          post.getTitle(),
          post.getSummary(),
          post.getBodyHtml(),
          post.isPinned(),
          post.getStatus(),
          post.getPublishedAt(),
          version(post),
          post.getUpdatedAt());
    }
  }

  /** Changes with every new picture; null when there's none. */
  static String version(NewsPost post) {
    return Pictures.version(post.getImageKey());
  }
}
