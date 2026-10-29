package lk.bookhive.report.export;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lk.bookhive.report.export.ReportDocument.Column;
import lk.bookhive.report.export.ReportDocument.Table;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.FontSelector;
import org.openpdf.text.pdf.LayoutProcessor;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * A report as an A4 PDF (checklist step 366), in English, Sinhala or Tamil. The Noto fonts are
 * embedded (resources/fonts) and each piece of text gets the font for its script; Java's text
 * layout shapes Sinhala and Tamil (vowel signs, conjuncts), which plain PDF fonts can't do. Wide
 * tables turn the page sideways.
 */
@Component
public class PdfExporter {

  private static final Color HONEY = new Color(0xB4, 0x6A, 0x00);
  private static final Color HEADER_FILL = new Color(0xFD, 0xF3, 0xDC);
  private static final Color GREY = new Color(0x55, 0x55, 0x55);

  /** Tables with more columns than this are printed on landscape pages. */
  private static final int PORTRAIT_MAX_COLUMNS = 6;

  private static final Path FONT_DIR = fontDir();

  private final BaseFont[] regular;
  private final BaseFont[] bold;

  // LayoutProcessor is marked deprecated in OpenPDF 3 but is still its way to shape Sinhala and
  // Tamil (checked: vowel signs and conjuncts come out right)
  @SuppressWarnings("deprecation")
  public PdfExporter() {
    // Shaping only (no kerning/ligature changes), so other PDFs (cards, labels) look the same
    LayoutProcessor.enable();
    regular = fonts("NotoSans-Regular", "NotoSansSinhala-Regular", "NotoSansTamil-Regular");
    bold = fonts("NotoSans-Bold", "NotoSansSinhala-Bold", "NotoSansTamil-Bold");
  }

  public byte[] write(ReportDocument report) {
    boolean wide =
        report.tables().stream().anyMatch(t -> t.columns().size() > PORTRAIT_MAX_COLUMNS);
    Rectangle page = wide ? PageSize.A4.rotate() : PageSize.A4;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(page, 36, 36, 40, 44);
    PdfWriter writer = PdfWriter.getInstance(document, out);
    writer.setPageEvent(new PageNumbers());
    document.addTitle(report.title());
    document.addCreator("BookHive");
    document.open();

    document.add(paragraph(report.title(), bold, 16, HONEY, 6));
    for (String line : report.lines()) {
      document.add(paragraph(line, regular, 9, GREY, 2));
    }
    for (Table table : report.tables()) {
      if (table.title() != null) {
        document.add(paragraph(table.title(), bold, 12, Color.BLACK, 4, 14));
      }
      document.add(table(table));
    }
    document.close();
    return out.toByteArray();
  }

  private PdfPTable table(Table t) {
    List<Column> columns = t.columns();
    float[] widths = new float[columns.size()];
    for (int i = 0; i < widths.length; i++) {
      widths[i] = columns.get(i).width();
    }
    PdfPTable table = new PdfPTable(widths);
    table.setWidthPercentage(100);
    table.setSpacingBefore(8);
    table.setHeaderRows(1);
    for (Column c : columns) {
      PdfPCell cell = cell(c.header(), bold, c.numeric());
      cell.setBackgroundColor(HEADER_FILL);
      table.addCell(cell);
    }
    for (List<String> row : t.rows()) {
      for (int i = 0; i < columns.size(); i++) {
        table.addCell(cell(row.get(i), regular, columns.get(i).numeric()));
      }
    }
    if (t.totals() != null) {
      for (int i = 0; i < columns.size(); i++) {
        PdfPCell cell = cell(t.totals().get(i), bold, columns.get(i).numeric());
        cell.setBorderWidthTop(1.2f);
        table.addCell(cell);
      }
    }
    return table;
  }

  private PdfPCell cell(String text, BaseFont[] fonts, boolean numeric) {
    PdfPCell cell = new PdfPCell(phrase(text == null ? "" : text, fonts, 8.5f, Color.BLACK));
    cell.setPadding(4);
    cell.setBorderColor(new Color(0xDD, 0xDD, 0xDD));
    cell.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
    return cell;
  }

  private Paragraph paragraph(String text, BaseFont[] fonts, float size, Color color, float after) {
    return paragraph(text, fonts, size, color, after, 0);
  }

  private Paragraph paragraph(
      String text, BaseFont[] fonts, float size, Color color, float after, float before) {
    Paragraph p = new Paragraph(phrase(text, fonts, size, color));
    p.setSpacingAfter(after);
    p.setSpacingBefore(before);
    return p;
  }

  /** Each run of text in the font that has its letters (Latin, Sinhala, Tamil). */
  private static Phrase phrase(String text, BaseFont[] fonts, float size, Color color) {
    FontSelector selector = new FontSelector();
    for (BaseFont font : fonts) {
      selector.addFont(new Font(font, size, Font.NORMAL, color));
    }
    return selector.process(text);
  }

  /**
   * The fonts from the app's resources. Java's text layout (which shapes Sinhala and Tamil) opens
   * each font by its file path, and a font inside the jar has none, so they're copied to a
   * temporary folder once, when the app starts.
   */
  private static BaseFont[] fonts(String... names) {
    BaseFont[] fonts = new BaseFont[names.length];
    for (int i = 0; i < names.length; i++) {
      String resource = "/fonts/" + names[i] + ".ttf";
      try (InputStream in = PdfExporter.class.getResourceAsStream(resource)) {
        if (in == null) {
          throw new IllegalStateException("Font missing: " + resource);
        }
        Path file = FONT_DIR.resolve(names[i] + ".ttf");
        if (!Files.exists(file)) {
          Files.copy(in, file);
        }
        fonts[i] = BaseFont.createFont(file.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return fonts;
  }

  /** "BookHive · 3" at the bottom of every page. */
  private final class PageNumbers extends PdfPageEventHelper {
    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      PdfContentByte canvas = writer.getDirectContent();
      ColumnText.showTextAligned(
          canvas,
          Element.ALIGN_RIGHT,
          phrase("BookHive · " + writer.getPageNumber(), regular, 8, GREY),
          document.right(),
          document.bottom() - 20,
          0);
    }
  }

  private static Path fontDir() {
    try {
      Path dir = Files.createTempDirectory("bookhive-fonts");
      dir.toFile().deleteOnExit();
      return dir;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
