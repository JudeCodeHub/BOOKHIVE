package lk.bookhive.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stores files in a folder on this machine. Simple stand-in until MinIO/S3 (checklist step 126).
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(StorageProperties properties) {
    this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
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

  /** Keeps every key inside the root folder, so "../../etc/passwd" can't escape it. */
  private Path resolve(String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("Storage key escapes the storage folder: " + key);
    }
    return path;
  }
}
