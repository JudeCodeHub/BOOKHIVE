package lk.bookhive.event.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.event.api.NewsResponses.NewsPostResponse;
import lk.bookhive.event.api.NewsResponses.NewsSummary;
import lk.bookhive.event.domain.NewsPost.Details;
import lk.bookhive.event.service.NewsService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** News for staff (EVT-4, step 335): write, publish, un-publish, pin, delete, picture. */
@RestController
@RequestMapping("/staff/news")
@Tag(name = "Staff: news")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffNewsController {

  private final NewsService news;

  public StaffNewsController(NewsService news) {
    this.news = news;
  }

  /**
   * @param summary one or two sentences for lists and the home page
   * @param bodyHtml the editor's HTML (cleaned on the server, step 336)
   * @param pinned keep it above the other posts
   */
  public record NewsRequest(
      @NotBlank @Size(max = 200) String title,
      @Size(max = 500) String summary,
      @Size(max = 100_000) String bodyHtml,
      boolean pinned) {

    Details details() {
      return new Details(title, summary, bodyHtml, pinned);
    }
  }

  @GetMapping
  @Operation(summary = "All posts (drafts too), most recently changed first")
  public PageResponse<NewsSummary> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(news.all(page, size), NewsSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One post in full (drafts too)")
  public NewsPostResponse get(@PathVariable UUID id) {
    return NewsPostResponse.from(news.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Write a post (as a draft)")
  public NewsPostResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewsRequest request) {
    return NewsPostResponse.from(news.create(request.details(), staffId(jwt)));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Change a post")
  public NewsPostResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody NewsRequest request) {
    return NewsPostResponse.from(news.update(id, request.details(), staffId(jwt)));
  }

  @PostMapping("/{id}/publish")
  @Operation(summary = "Put a post on the website")
  public NewsPostResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return NewsPostResponse.from(news.publish(id, staffId(jwt)));
  }

  @PostMapping("/{id}/unpublish")
  @Operation(summary = "Take a post off the website (back to a draft)")
  public NewsPostResponse unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return NewsPostResponse.from(news.unpublish(id, staffId(jwt)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a post for good")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    news.delete(id, staffId(jwt));
  }

  @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload the picture (JPG or PNG, max 5 MB)")
  public NewsPostResponse uploadImage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestPart("image") MultipartFile image)
      throws IOException {
    return NewsPostResponse.from(
        news.uploadImage(id, image.getInputStream(), image.getSize(), staffId(jwt)));
  }

  @DeleteMapping("/{id}/image")
  @Operation(summary = "Remove the picture")
  public NewsPostResponse removeImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return NewsPostResponse.from(news.removeImage(id, staffId(jwt)));
  }

  @GetMapping(path = "/{id}/image", produces = MediaType.IMAGE_JPEG_VALUE)
  @Operation(summary = "The picture (drafts too)")
  public ResponseEntity<byte[]> image(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .contentType(MediaType.IMAGE_JPEG)
        .body(news.image(id, false));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
