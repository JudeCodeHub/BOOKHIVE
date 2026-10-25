package lk.bookhive.common.storage;

import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Picks the {@link FileStorage}: {@code bookhive.storage.type} = s3 or local. */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

  @Bean
  @ConditionalOnProperty(name = "bookhive.storage.type", havingValue = "local")
  FileStorage localFileStorage(StorageProperties properties) {
    return new LocalFileStorage(Path.of(properties.localDir()));
  }

  @Bean
  @ConditionalOnProperty(name = "bookhive.storage.type", havingValue = "s3", matchIfMissing = true)
  FileStorage s3FileStorage(StorageProperties properties) {
    StorageProperties.S3 config = properties.s3();
    Region region = Region.of(config.region());
    AwsCredentialsProvider credentials =
        config.accessKey() == null || config.accessKey().isBlank()
            ? DefaultCredentialsProvider.builder().build()
            : StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
    S3Configuration s3Settings =
        S3Configuration.builder().pathStyleAccessEnabled(config.pathStyle()).build();

    var client =
        S3Client.builder()
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Settings);
    var presigner =
        S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Settings);
    if (config.endpoint() != null && !config.endpoint().isBlank()) {
      client.endpointOverride(URI.create(config.endpoint()));
      presigner.endpointOverride(URI.create(config.endpoint()));
    }

    S3Client s3 = client.build();
    if (config.createBucket()) {
      createBucketIfMissing(s3, config.bucket());
    }
    return new S3FileStorage(s3, presigner.build(), config.bucket());
  }

  /** Dev convenience: a fresh SeaweedFS has no buckets. Production buckets are made by infra. */
  private static void createBucketIfMissing(S3Client s3, String bucket) {
    try {
      s3.headBucket(request -> request.bucket(bucket));
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      s3.createBucket(request -> request.bucket(bucket));
      log.info("Created storage bucket {}", bucket);
    } catch (RuntimeException e) {
      // Storage not running: the app still starts; uploads fail until it's up
      log.warn("Storage bucket {} could not be checked: {}", bucket, e.getMessage());
    }
  }
}
