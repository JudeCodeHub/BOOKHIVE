package lk.bookhive.common.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.storage.*} in application.yml.
 *
 * @param type {@code s3} (dev, prod) or {@code local} (tests)
 * @param localDir folder for {@link LocalFileStorage}
 * @param linkValidity how long a {@link FileStorage#temporaryLink} works
 */
@ConfigurationProperties("bookhive.storage")
public record StorageProperties(String type, String localDir, Duration linkValidity, S3 s3) {

  /**
   * @param endpoint S3 API address; empty = AWS S3 itself (production)
   * @param pathStyle true for SeaweedFS/MinIO (http://host/bucket/key), false for AWS
   * @param createBucket create the bucket at startup if it doesn't exist (dev only)
   * @param accessKey empty = AWS default credentials (IAM role in production)
   */
  public record S3(
      String endpoint,
      String region,
      String bucket,
      String accessKey,
      String secretKey,
      boolean pathStyle,
      boolean createBucket) {}
}
