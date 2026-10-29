package lk.bookhive.admin.config;

import lk.bookhive.admin.domain.LibraryInfo.Contact;
import lk.bookhive.admin.service.LibraryInfoService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fills the library_info row on the first start after the Library info page arrived (step 377),
 * from the {@code bookhive.library.*} settings that held these details before. Does nothing once
 * the row exists, so admin changes are never overwritten by configuration.
 */
@Component
@EnableConfigurationProperties(LibraryProperties.class)
public class LibraryInfoBootstrap implements ApplicationRunner {

  private final LibraryInfoService libraryInfo;
  private final LibraryProperties properties;

  public LibraryInfoBootstrap(LibraryInfoService libraryInfo, LibraryProperties properties) {
    this.libraryInfo = libraryInfo;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    libraryInfo.createIfMissing(
        new Contact(
            properties.name(),
            properties.addressLine(),
            properties.city(),
            properties.postalCode(),
            properties.phone(),
            properties.email(),
            properties.latitude(),
            properties.longitude()));
  }
}
