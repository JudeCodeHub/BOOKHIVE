package lk.bookhive.member.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.repository.MemberRepository;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The member's digital library card: data, barcode/QR images and the printable PDF. */
@Service
public class LibraryCardService {

  // Bank-card size (ID-1, 85.6 x 54 mm) in PDF points (1 pt = 1/72 inch)
  private static final Rectangle CARD = new Rectangle(242.6f, 153f);
  // Honey and Deep Hive from PRD §11
  private static final Color HONEY = new Color(0xF5, 0x9E, 0x0B);
  private static final Color DEEP_HIVE = new Color(0x1F, 0x29, 0x37);
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final MemberRepository members;
  private final AuthService authService;
  private final CardCodes codes;

  public LibraryCardService(MemberRepository members, AuthService authService, CardCodes codes) {
    this.members = members;
    this.authService = authService;
    this.codes = codes;
  }

  /** What's printed on the card. */
  public record CardResponse(
      String memberNo, String fullName, String membershipType, LocalDate expiresAt) {}

  /** Only active members have a card: it's what the desk scans to lend books. */
  @Transactional(readOnly = true)
  public CardResponse card(UUID userId) {
    Member member =
        members
            .findByUserId(userId)
            .orElseThrow(
                () -> new NotFoundException("MEMBERSHIP_NOT_FOUND", "You are not a member yet."));
    if (member.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessRuleException(
          "CARD_NOT_AVAILABLE", "The library card is available while your membership is active.");
    }
    User user = authService.getProfile(userId);
    return new CardResponse(
        member.getMemberNo(),
        user.getFullName(),
        member.getMembershipType().getName(user.getPreferredLanguage()),
        member.getExpiresAt());
  }

  public byte[] barcode(UUID userId) {
    return codes.barcodePng(card(userId).memberNo());
  }

  public byte[] qr(UUID userId) {
    return codes.qrPng(card(userId).memberNo());
  }

  /**
   * One bank-card-sized page (checklist step 136): print it, or keep it on the phone.
   *
   * <p>Uses the built-in Helvetica font, which has Latin letters only: a name written in Sinhala or
   * Tamil script would not show. Embedding Noto fonts comes with the trilingual PDFs later.
   */
  public byte[] pdf(UUID userId) {
    CardResponse card = card(userId);
    Document document = new Document(CARD, 10, 10, 8, 8);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfWriter.getInstance(document, out);
      document.addTitle("BookHive library card " + card.memberNo());
      document.open();

      Paragraph title =
          new Paragraph("BookHive  Library Card", new Font(Font.HELVETICA, 10, Font.BOLD, HONEY));
      document.add(title);

      PdfPTable table = new PdfPTable(new float[] {3, 1.3f});
      table.setWidthPercentage(100);
      table.setSpacingBefore(4);

      PdfPCell details = borderless();
      details.addElement(line(card.fullName(), 9, Font.BOLD));
      details.addElement(line(card.memberNo(), 11, Font.BOLD));
      details.addElement(line(card.membershipType(), 7, Font.NORMAL));
      details.addElement(line("Valid until " + DATE.format(card.expiresAt()), 7, Font.NORMAL));
      table.addCell(details);

      Image qr = Image.getInstance(codes.qrPng(card.memberNo()));
      qr.scaleToFit(56, 56);
      PdfPCell qrCell = borderless();
      qrCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      qrCell.addElement(qr);
      table.addCell(qrCell);
      document.add(table);

      Image barcode = Image.getInstance(codes.barcodePng(card.memberNo()));
      barcode.scaleToFit(200, 34);
      barcode.setAlignment(Element.ALIGN_CENTER);
      document.add(barcode);

      document.close();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (DocumentException e) {
      throw new IllegalStateException("Could not create the card PDF", e);
    }
  }

  private static PdfPCell borderless() {
    PdfPCell cell = new PdfPCell();
    cell.setBorder(Rectangle.NO_BORDER);
    cell.setPadding(0);
    return cell;
  }

  private static Paragraph line(String text, float size, int style) {
    Paragraph paragraph = new Paragraph(text, new Font(Font.HELVETICA, size, style, DEEP_HIVE));
    paragraph.setLeading(size + 2);
    return paragraph;
  }
}
