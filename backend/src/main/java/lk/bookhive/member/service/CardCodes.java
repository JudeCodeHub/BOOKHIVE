package lk.bookhive.member.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * The scannable parts of the library card (MEM-5, checklist step 135), made with ZXing. Both codes
 * contain just the member number (e.g. BH-000123), so a desk scanner "types" it straight into the
 * member search box.
 *
 * <ul>
 *   <li><b>Code 128</b>: the classic barcode; every library desk scanner reads it.
 *   <li><b>QR code</b>: for phone cameras and screens; still readable when partly scratched.
 * </ul>
 */
@Component
public class CardCodes {

  private static final int BLACK = 0xFF000000;
  private static final int WHITE = 0xFFFFFFFF;

  /** PNG barcode, sized for the card (wide and short). */
  public byte[] barcodePng(String memberNo) {
    return png(
        new Code128Writer()
            .encode(memberNo, BarcodeFormat.CODE_128, 600, 120, Map.of(EncodeHintType.MARGIN, 10)));
  }

  /** PNG QR code. Level M: up to ~15% of it can be damaged and it still scans. */
  public byte[] qrPng(String memberNo) {
    try {
      return png(
          new QRCodeWriter()
              .encode(
                  memberNo,
                  BarcodeFormat.QR_CODE,
                  300,
                  300,
                  Map.of(
                      EncodeHintType.MARGIN,
                      1,
                      EncodeHintType.ERROR_CORRECTION,
                      ErrorCorrectionLevel.M)));
    } catch (WriterException e) {
      throw new IllegalStateException("Could not make a QR code", e);
    }
  }

  private static byte[] png(BitMatrix matrix) {
    BufferedImage image =
        new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < matrix.getWidth(); x++) {
      for (int y = 0; y < matrix.getHeight(); y++) {
        image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
      }
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
