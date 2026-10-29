package lk.bookhive.community.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.community.service.WishlistService;
import lk.bookhive.community.service.WishlistService.WishlistEntry;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's wishlist (checklist step 338). */
@RestController
@RequestMapping("/me/wishlist")
@Tag(name = "My wishlist")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class WishlistController {

  private final WishlistService wishlist;
  private final BorrowerService borrowers;

  public WishlistController(WishlistService wishlist, BorrowerService borrowers) {
    this.wishlist = wishlist;
    this.borrowers = borrowers;
  }

  public record AddToWishlistRequest(@NotNull UUID bookId) {}

  /**
   * A saved book.
   *
   * @param title null if the book is no longer in the catalog
   * @param availableNow copies on the shelf that can be borrowed right now
   * @param lendableCopies copies that can go home at all (0 = reference only)
   */
  public record WishlistItemSummary(
      UUID bookId,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String coverVersion,
      long availableNow,
      long lendableCopies,
      Instant addedAt) {

    static WishlistItemSummary from(WishlistEntry entry) {
      var t = entry.title();
      return new WishlistItemSummary(
          entry.item().getBookId(),
          t == null ? null : t.title(),
          t == null ? null : t.coverVersion(),
          t == null ? 0 : t.availableToBorrow(),
          t == null ? 0 : t.lendableCopies(),
          entry.item().getAddedAt());
    }
  }

  public record WishlistStatusResponse(boolean saved) {}

  @GetMapping
  @Operation(summary = "My wishlist, newest first, with what's on the shelf now")
  public PageResponse<WishlistItemSummary> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
    return PageResponse.from(wishlist.list(memberId(jwt), page, size), WishlistItemSummary::from);
  }

  @GetMapping("/{bookId}")
  @Operation(summary = "Is this book on my wishlist? (for the heart button)")
  public WishlistStatusResponse status(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId) {
    return new WishlistStatusResponse(wishlist.contains(memberId(jwt), bookId));
  }

  @PostMapping
  @Operation(summary = "Save a book (saving it again changes nothing)")
  public WishlistItemSummary add(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddToWishlistRequest request) {
    return WishlistItemSummary.from(wishlist.add(memberId(jwt), request.bookId()));
  }

  @DeleteMapping("/{bookId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Take a book off my wishlist")
  public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId) {
    wishlist.remove(memberId(jwt), bookId);
  }

  private UUID memberId(Jwt jwt) {
    return borrowers.memberIdOfUser(UUID.fromString(jwt.getSubject()));
  }
}
