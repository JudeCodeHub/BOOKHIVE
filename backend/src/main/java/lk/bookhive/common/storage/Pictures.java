package lk.bookhive.common.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import lk.bookhive.common.exception.FieldValidationException;
import net.coobird.thumbnailator.Thumbnails;

/**
 * Uploaded pictures (book covers, event posters): checked like any upload (real JPEG/PNG, size
 * limit), then re-encoded as JPEG. Re-encoding also strips anything hidden in the original file
 * (camera location data, embedded scripts).
 */
public final class Pictures {

  private Pictures() {}

  /**
   * Reads an uploaded picture.
   *
   * @param field the form field, for the error message
   * @throws FieldValidationException when it isn't a readable JPEG or PNG
   */
  public static BufferedImage read(String field, InputStream content, long sizeBytes) {
    try (InputStream raw = content) {
      UploadCheck.CheckedUpload checked = UploadCheck.image(field, raw, sizeBytes);
      BufferedImage image = ImageIO.read(checked.content());
      if (image == null) {
        throw new FieldValidationException(
            field, "FILE_TYPE_NOT_ALLOWED", "This picture can't be read. Try a JPG or PNG.");
      }
      return image;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** JPEG at most {@code width} wide. Never enlarges; transparent parts become white. */
  public static byte[] jpeg(BufferedImage image, int width) {
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

  /**
   * The changing part of a picture's storage key ("events/<id>/<random>.jpg" -> "<random>"), used
   * as ?v= in public picture URLs so a new picture gets a new URL; null when there's none.
   */
  public static String version(String key) {
    if (key == null) {
      return null;
    }
    String file = key.substring(key.lastIndexOf('/') + 1);
    return file.endsWith(".jpg") ? file.substring(0, file.length() - 4) : file;
  }
}
