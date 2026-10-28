package lk.bookhive.event.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.storage.FileStorage;
import lk.bookhive.common.storage.Pictures;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.NewsPost;
import lk.bookhive.event.domain.NewsPost.Details;
import lk.bookhive.event.domain.NewsStatus;
import lk.bookhive.event.repository.NewsPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Library news (EVT-4, checklist step 335): staff write, publish, un-publish, pin and delete posts;
 * the website shows the published ones. Every staff change is audited.
 */
@Service
public class NewsService {

  private static final Logger log = LoggerFactory.getLogger(NewsService.class);

  private static final int IMAGE_WIDTH = 1200;

  private final NewsPostRepository news;
  private final FileStorage storage;
  private final AuditService audit;
  private final LibraryTime time;

  public NewsService(
      NewsPostRepository news, FileStorage storage, AuditService audit, LibraryTime time) {
    this.news = news;
    this.storage = storage;
    this.audit = audit;
    this.time = time;
  }

  /** Published posts, pinned first then newest. */
  @Transactional(readOnly = true)
  public Page<NewsPost> published(int page, int size) {
    return news.findByStatusOrderByPinnedDescPublishedAtDesc(
        NewsStatus.PUBLISHED, PageRequest.of(page, size));
  }

  /** One post for the website: drafts don't exist for the public. */
  @Transactional(readOnly = true)
  public NewsPost publishedPost(UUID id) {
    return news.findById(id)
        .filter(post -> post.getStatus() == NewsStatus.PUBLISHED)
        .orElseThrow(() -> new NotFoundException("NEWS_NOT_FOUND", "News post not found."));
  }

  @Transactional(readOnly = true)
  public Page<NewsPost> all(int page, int size) {
    return news.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public NewsPost get(UUID id) {
    return require(id);
  }

  @Transactional
  public NewsPost create(Details details, UUID staffId) {
    NewsPost post = news.save(NewsPost.draft(details, staffId));
    audit.record("news.created", staffId, "news", post.getId(), Map.of("title", post.getTitle()));
    return post;
  }

  @Transactional
  public NewsPost update(UUID id, Details details, UUID staffId) {
    NewsPost post = require(id);
    post.update(details);
    audit.record("news.updated", staffId, "news", id, Map.of("title", post.getTitle()));
    return post;
  }

  @Transactional
  public NewsPost publish(UUID id, UUID staffId) {
    NewsPost post = require(id);
    post.publish(time.now());
    audit.record("news.published", staffId, "news", id, Map.of("title", post.getTitle()));
    return post;
  }

  @Transactional
  public NewsPost unpublish(UUID id, UUID staffId) {
    NewsPost post = require(id);
    post.unpublish();
    audit.record("news.unpublished", staffId, "news", id, Map.of("title", post.getTitle()));
    return post;
  }

  /** Gone for good, with its picture (the audit log keeps the title). */
  @Transactional
  public void delete(UUID id, UUID staffId) {
    NewsPost post = require(id);
    String image = post.getImageKey();
    news.delete(post);
    deleteQuietly(image);
    audit.record("news.deleted", staffId, "news", id, Map.of("title", post.getTitle()));
  }

  /** A picture (JPEG/PNG), stored as one JPEG; replaces the old one. */
  @Transactional
  public NewsPost uploadImage(UUID id, InputStream content, long sizeBytes, UUID staffId) {
    NewsPost post = require(id);
    byte[] jpeg = Pictures.jpeg(Pictures.read("image", content, sizeBytes), IMAGE_WIDTH);
    String key = "news/" + id + "/" + UUID.randomUUID() + ".jpg";
    try {
      storage.put(key, new ByteArrayInputStream(jpeg), jpeg.length, "image/jpeg");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not store the picture", e);
    }
    String old = post.getImageKey();
    post.setImageKey(key);
    deleteQuietly(old);
    audit.record("news.image_changed", staffId, "news", id, Map.of());
    return post;
  }

  @Transactional
  public NewsPost removeImage(UUID id, UUID staffId) {
    NewsPost post = require(id);
    deleteQuietly(post.getImageKey());
    post.setImageKey(null);
    audit.record("news.image_removed", staffId, "news", id, Map.of());
    return post;
  }

  /**
   * The picture's bytes; the public only gets published posts' pictures.
   *
   * @param publicOnly true for the website
   */
  @Transactional(readOnly = true)
  public byte[] image(UUID id, boolean publicOnly) {
    NewsPost post = publicOnly ? publishedPost(id) : require(id);
    if (post.getImageKey() == null) {
      throw new NotFoundException("NEWS_IMAGE_NOT_FOUND", "This post has no picture.");
    }
    try (InputStream in = storage.get(post.getImageKey())) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new NotFoundException("NEWS_IMAGE_NOT_FOUND", "This post has no picture.");
    }
  }

  private NewsPost require(UUID id) {
    return news.findById(id)
        .orElseThrow(() -> new NotFoundException("NEWS_NOT_FOUND", "News post not found."));
  }

  private void deleteQuietly(String key) {
    if (key == null) {
      return;
    }
    try {
      storage.delete(key);
    } catch (IOException | RuntimeException e) {
      log.warn("Could not delete old news picture {}", key, e);
    }
  }
}
