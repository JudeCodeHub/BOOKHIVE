package lk.bookhive.event.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.UUID;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.event.api.NewsResponses.NewsPostResponse;
import lk.bookhive.event.api.NewsResponses.NewsSummary;
import lk.bookhive.event.service.NewsService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Library news on the public website (EVT-4, step 335); no login needed. */
@RestController
@RequestMapping("/news")
@Tag(name = "News")
public class NewsController {

  private final NewsService news;

  public NewsController(NewsService news) {
    this.news = news;
  }

  @GetMapping
  @Operation(summary = "Published news, pinned first then newest")
  public PageResponse<NewsSummary> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    return PageResponse.from(news.published(page, size), NewsSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One published post")
  public NewsPostResponse get(@PathVariable UUID id) {
    return NewsPostResponse.from(news.publishedPost(id));
  }

  @GetMapping(path = "/{id}/image", produces = MediaType.IMAGE_JPEG_VALUE)
  @Operation(summary = "The post's picture; cache it (the URL changes with ?v= when it changes)")
  public ResponseEntity<byte[]> image(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
        .contentType(MediaType.IMAGE_JPEG)
        .body(news.image(id, true));
  }
}
