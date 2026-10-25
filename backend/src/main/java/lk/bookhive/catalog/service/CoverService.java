package lk.bookhive.catalog.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.storage.FileStorage;
import lk.bookhive.common.storage.UploadCheck;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Book covers (CAT-4, checklist step 161). One upload becomes three JPEG sizes in storage:
 *
 * <pre>covers/&lt;book id&gt;/&lt;version&gt;/small.jpg | medium.jpg | large.jpg</pre>
 *
 * The book stores {@code covers/<book id>/<version>}. A new upload gets a new version, so the
 * public cover URL changes too and browsers can cache each one forever.
 */
@Service
public class CoverService {

  private static final Logger log = LoggerFactory.getLogger(CoverService.class);

  /** Cover sizes by width; the height follows the picture's own proportions. */
  public enum CoverSize {
    /** Search results and lists */
    SMALL(128),
    /** Book page */
    MEDIUM(320),
    /** Zoomed in */
    LARGE(800);

    private final int width;

    CoverSize(int width) {
      this.width = width;
    }

    String fileName() {
      return name().toLowerCase() + ".jpg";
    }
  }

  private final BookRepository books;
  private final FileStorage storage;
  private final AuditService audit;

  public CoverService(BookRepository books, FileStorage storage, AuditService audit) {
    this.books = books;
    this.storage = storage;
    this.audit = audit;
  }

  /** Checks the picture, makes the three sizes, stores them, then removes the old cover. */
  @Transactional
  public Book upload(UUID bookId, InputStream content, long sizeBytes, UUID staffId) {
    Book book = require(bookId);
    BufferedImage image = read(content, sizeBytes);
    String base = "covers/" + bookId + "/" + UUID.randomUUID();
    try {
      for (CoverSize size : CoverSize.values()) {
        byte[] jpeg = resize(image, size.width);
        storage.put(
            base + "/" + size.fileName(),
            new ByteArrayInputStream(jpeg),
            jpeg.length,
            "image/jpeg");
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not store the cover", e);
    }
    String old = book.getCoverKey();
    book.setCoverKey(base);
    deleteQuietly(old);
    audit.record("book.cover_changed", staffId, "book", bookId, Map.of());
    return book;
  }

  @Transactional
  public Book remove(UUID bookId, UUID staffId) {
    Book book = require(bookId);
    String old = book.getCoverKey();
    book.setCoverKey(null);
    deleteQuietly(old);
    audit.record("book.cover_removed", staffId, "book", bookId, Map.of());
    return book;
  }

  /** The JPEG bytes of one size, for the public cover URL. */
  @Transactional(readOnly = true)
  public byte[] read(UUID bookId, CoverSize size) {
    Book book = require(bookId);
    if (book.getCoverKey() == null) {
      throw new NotFoundException("COVER_NOT_FOUND", "This book has no cover yet.");
    }
    try (InputStream in = storage.get(book.getCoverKey() + "/" + size.fileName())) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new NotFoundException("COVER_NOT_FOUND", "This book has no cover yet.");
    }
  }

  private static BufferedImage read(InputStream content, long sizeBytes) {
    try (InputStream raw = content) {
      UploadCheck.CheckedUpload checked = UploadCheck.image("cover", raw, sizeBytes);
      BufferedImage image = ImageIO.read(checked.content());
      if (image == null) {
        throw new FieldValidationException(
            "cover", "FILE_TYPE_NOT_ALLOWED", "This picture can't be read. Try a JPG or PNG.");
      }
      return image;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** JPEG at most {@code width} wide. Never enlarges; transparent parts become white. */
  private static byte[] resize(BufferedImage image, int width) {
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = rgb.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Thumbnails.of(rgb)
          .scale(Math.min(1.0, (double) width / rgb.getWidth()))
          .outputFormat("jpg")
          .outputQuality(0.85)
          .toOutputStream(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Old files are only clutter; a failure here must not undo the new cover. */
  private void deleteQuietly(String base) {
    if (base == null) {
      return;
    }
    for (CoverSize size : CoverSize.values()) {
      try {
        storage.delete(base + "/" + size.fileName());
      } catch (IOException | RuntimeException e) {
        log.warn("Could not delete old cover file {}/{}", base, size.fileName(), e);
      }
    }
  }

  private Book require(UUID bookId) {
    return books
        .findById(bookId)
        .orElseThrow(() -> new NotFoundException("BOOK_NOT_FOUND", "Book not found."));
  }

  /** The changing part of the cover key, used as ?v= in public cover URLs; null if none. */
  public static String version(Book book) {
    String key = book.getCoverKey();
    return key == null ? null : key.substring(key.lastIndexOf('/') + 1);
  }
}
