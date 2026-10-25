package lk.bookhive.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.storage.*} in application.yml.
 *
 * @param localDir folder for {@link LocalFileStorage} (git-ignored)
 */
@ConfigurationProperties("bookhive.storage")
public record StorageProperties(String localDir) {}
