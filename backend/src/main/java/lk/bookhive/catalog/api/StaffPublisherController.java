package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.bookhive.catalog.domain.Publisher;
import lk.bookhive.catalog.service.PublisherService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Publisher management for cataloguing (librarians and admins). */
@RestController
@RequestMapping("/staff/publishers")
@Tag(name = "Staff: publishers")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffPublisherController {

  private final PublisherService publishers;

  public StaffPublisherController(PublisherService publishers) {
    this.publishers = publishers;
  }

  public record PublisherRequest(@NotBlank @Size(max = 200) String name) {}

  public record PublisherResponse(UUID id, String name) {

    static PublisherResponse from(Publisher publisher) {
      return new PublisherResponse(publisher.getId(), publisher.getName());
    }
  }

  @GetMapping
  @Operation(summary = "Publishers A–Z, optionally filtered by part of the name")
  public PageResponse<PublisherResponse> list(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(publishers.list(q, page, size), PublisherResponse::from);
  }

  @GetMapping("/{id}")
  public PublisherResponse get(@PathVariable UUID id) {
    return PublisherResponse.from(publishers.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a publisher (409 PUBLISHER_EXISTS if the name is taken)")
  public PublisherResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PublisherRequest request) {
    return PublisherResponse.from(publishers.create(request.name(), staffId(jwt)));
  }

  @PutMapping("/{id}")
  public PublisherResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody PublisherRequest request) {
    return PublisherResponse.from(publishers.update(id, request.name(), staffId(jwt)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a publisher with no books (409 PUBLISHER_HAS_BOOKS otherwise)")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    publishers.delete(id, staffId(jwt));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
