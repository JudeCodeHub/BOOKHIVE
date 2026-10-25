package lk.bookhive.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Files in an S3 bucket: SeaweedFS locally (infra/docker-compose.yml), AWS S3 in production.
 *
 * <p>The bucket is private: nothing is uploaded with a public ACL, and browsers only ever get
 * short-lived presigned links ({@link #temporaryLink}).
 */
public class S3FileStorage implements FileStorage {

  private final S3Client s3;
  private final S3Presigner presigner;
  private final String bucket;

  public S3FileStorage(S3Client s3, S3Presigner presigner, String bucket) {
    this.s3 = s3;
    this.presigner = presigner;
    this.bucket = bucket;
  }

  @Override
  public void put(String key, InputStream content, long sizeBytes, String contentType) {
    s3.putObject(
        request -> request.bucket(bucket).key(key).contentType(contentType),
        RequestBody.fromInputStream(content, sizeBytes));
  }

  @Override
  public InputStream get(String key) throws IOException {
    try {
      return s3.getObject(request -> request.bucket(bucket).key(key));
    } catch (NoSuchKeyException e) {
      throw new IOException("No stored file under " + key, e);
    }
  }

  @Override
  public void delete(String key) {
    s3.deleteObject(request -> request.bucket(bucket).key(key));
  }

  @Override
  public URI temporaryLink(String key, Duration validFor) {
    return URI.create(
        presigner
            .presignGetObject(
                presign ->
                    presign
                        .signatureDuration(validFor)
                        .getObjectRequest(request -> request.bucket(bucket).key(key)))
            .url()
            .toString());
  }
}
