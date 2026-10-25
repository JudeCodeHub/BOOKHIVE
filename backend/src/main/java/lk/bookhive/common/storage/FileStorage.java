package lk.bookhive.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Where uploaded files go (member documents now; book covers later). Code depends on this interface
 * only: S3-compatible storage in dev and production ({@link S3FileStorage}), a plain folder in
 * tests ({@link LocalFileStorage}).
 *
 * <p>Keys look like paths, e.g. {@code member-documents/<member id>/<random id>}. They are never
 * built from user input such as file names.
 */
public interface FileStorage {

  void put(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

  InputStream get(String key) throws IOException;

  void delete(String key) throws IOException;

  /**
   * A temporary link that lets a browser download one private file directly, without making the
   * bucket public. Anyone holding the link can open the file until it expires, so keep the time
   * short and only hand links to people allowed to see the file.
   */
  URI temporaryLink(String key, Duration validFor);
}
