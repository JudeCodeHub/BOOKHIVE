package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.member.service.LibraryCardService;
import lk.bookhive.member.service.LibraryCardService.Card;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's digital library card (MEM-5). Active members only. */
@RestController
@RequestMapping("/me")
@Tag(name = "Membership")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class LibraryCardController {

  private final LibraryCardService cards;

  public LibraryCardController(LibraryCardService cards) {
    this.cards = cards;
  }

  @GetMapping("/card")
  @Operation(summary = "What's on my library card")
  public Card card(@AuthenticationPrincipal Jwt jwt) {
    return cards.card(userId(jwt));
  }

  @GetMapping(path = "/card/barcode.png", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "Card barcode (Code 128)")
  public ResponseEntity<byte[]> barcode(@AuthenticationPrincipal Jwt jwt) {
    return image(cards.barcode(userId(jwt)));
  }

  @GetMapping(path = "/card/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "Card QR code")
  public ResponseEntity<byte[]> qr(@AuthenticationPrincipal Jwt jwt) {
    return image(cards.qr(userId(jwt)));
  }

  @GetMapping(path = "/card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(summary = "Printable card (PDF, bank-card size)")
  public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = userId(jwt);
    String memberNo = cards.card(userId).memberNo();
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("bookhive-card-" + memberNo + ".pdf")
                .build()
                .toString())
        // Personal data: browsers and proxies must not keep a copy
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PDF)
        .body(cards.pdf(userId));
  }

  private static ResponseEntity<byte[]> image(byte[] png) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG)
        .body(png);
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
