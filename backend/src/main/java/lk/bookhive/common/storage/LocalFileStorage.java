package lk.bookhive.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Stores files in a folder on this machine. Used by tests, which have no S3 server. */
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  @Override
  public void put(String key, InputStream content, long sizeBytes, String contentType)
      throws IOException {
    Path target = resolve(key);
    Files.createDirectories(target.getParent());
    Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public InputStream get(String key) throws IOException {
    return Files.newInputStream(resolve(key));
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(resolve(key));
  }

  /** A file: link; only meaningful on this machine. */
  @Override
  public URI temporaryLink(String key, Duration validFor) {
    return resolve(key).toUri();
  }

  /** Keeps every key inside the root folder, so "../../etc/passwd" can't escape it. */
  private Path resolve(String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("Storage key escapes the storage folder: " + key);
    }
    return path;
  }
}
