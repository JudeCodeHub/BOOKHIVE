package lk.bookhive.catalog.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.common.barcode.BarcodeImages;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

/**
 * Printable barcode labels for copies (CAT-5, checklist step 160): A4 sheets of 3 x 8 labels (70 x
 * 37 mm, the common "24 per sheet" sticker paper). Each label: the title, the shelf label (call
 * number) and the Code 128 barcode with its number underneath.
 *
 * <p>Uses the built-in Helvetica font: titles in Sinhala or Tamil script won't show yet (same as
 * the library card); the barcode, which is what matters for scanning, always does.
 */
@Service
public class LabelSheetService {

  private static final int COLUMNS = 3;
  // 37 mm in points (1 mm = 72 / 25.4 pt)
  private static final float LABEL_HEIGHT = 37 * 72 / 25.4f;
  private static final int TITLE_MAX = 38;

  private final BarcodeImages barcodes;

  public LabelSheetService(BarcodeImages barcodes) {
    this.barcodes = barcodes;
  }

  public byte[] pdf(List<Copy> copies) {
    // A4 with the margins of standard 24-label sheets (about 13 mm top/bottom, 7 mm sides)
    Document document = new Document(PageSize.A4, 20, 20, 37, 37);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfWriter.getInstance(document, out);
      document.addTitle("BookHive copy labels");
      document.open();

      PdfPTable table = new PdfPTable(COLUMNS);
      table.setWidthPercentage(100);
      for (Copy copy : copies) {
        table.addCell(label(copy));
      }
      // Fill the last row so the table is drawn completely
      int remainder = copies.size() % COLUMNS;
      for (int i = 0; remainder != 0 && i < COLUMNS - remainder; i++) {
        table.addCell(empty());
      }
      document.add(table);
      document.close();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (DocumentException e) {
      throw new IllegalStateException("Could not create the label sheet", e);
    }
  }

  private PdfPCell label(Copy copy) throws IOException {
    PdfPCell cell = empty();
    cell.setPadding(6);

    String title = copy.getBook().getTitle();
    if (title.length() > TITLE_MAX) {
      title = title.substring(0, TITLE_MAX - 1) + "…";
    }
    cell.addElement(centered(title, 8, Font.BOLD));
    if (copy.getBook().getCallNumber() != null) {
      cell.addElement(centered(copy.getBook().getCallNumber(), 7, Font.NORMAL));
    }

    Image barcode = Image.getInstance(barcodes.barcodePng(copy.getBarcode()));
    barcode.scaleToFit(150, 36);
    barcode.setAlignment(Element.ALIGN_CENTER);
    cell.addElement(barcode);
    cell.addElement(centered(copy.getBarcode(), 8, Font.NORMAL));
    return cell;
  }

  private static PdfPCell empty() {
    PdfPCell cell = new PdfPCell();
    cell.setFixedHeight(LABEL_HEIGHT);
    cell.setBorder(Rectangle.NO_BORDER);
    return cell;
  }

  private static Paragraph centered(String text, float size, int style) {
    Paragraph paragraph = new Paragraph(text, new Font(Font.HELVETICA, size, style));
    paragraph.setAlignment(Element.ALIGN_CENTER);
    paragraph.setLeading(size + 1);
    return paragraph;
  }
}
