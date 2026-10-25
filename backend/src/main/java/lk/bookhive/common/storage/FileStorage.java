package lk.bookhive.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where uploaded files go (member documents now; book covers later). Code depends on this interface
 * only, so the files can live on disk in dev and in S3-compatible storage elsewhere (MinIO locally,
 * AWS S3 in production: checklist step 126).
 *
 * <p>Keys look like paths, e.g. {@code member-documents/<member id>/<random id>}. They are never
 * built from user input such as file names.
 */
public interface FileStorage {

  void put(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

  InputStream get(String key) throws IOException;

  void delete(String key) throws IOException;
}
