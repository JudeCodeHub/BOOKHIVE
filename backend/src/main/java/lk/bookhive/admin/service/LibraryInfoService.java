package lk.bookhive.admin.service;

import java.util.Map;
import java.util.UUID;
import lk.bookhive.admin.domain.LibraryInfo;
import lk.bookhive.admin.domain.LibraryInfo.About;
import lk.bookhive.admin.domain.LibraryInfo.Contact;
import lk.bookhive.admin.repository.LibraryInfoRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.text.RichText;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The library's own details (ADM-6, checklist step 377): read by the visit page and the contact
 * form, changed by admins. One row looked up by its key, so it's read straight from the database
 * rather than cached.
 */
@Service
public class LibraryInfoService {

  private final LibraryInfoRepository repository;
  private final AuditService audit;

  public LibraryInfoService(LibraryInfoRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public LibraryInfo current() {
    return repository
        .findById(LibraryInfo.ID)
        .orElseThrow(() -> new IllegalStateException("library_info row is missing"));
  }

  /**
   * Saves the details and the about texts (the HTML is cleaned first, step 336).
   *
   * @param expectedVersion the version the admin started editing from; a newer one means someone
   *     else saved in the meantime (LIBRARY_INFO_CHANGED), and nothing is written
   */
  @Transactional
  public LibraryInfo update(Contact contact, About about, long expectedVersion, UUID adminId) {
    LibraryInfo info = current();
    if (info.getVersion() != expectedVersion) {
      throw new ConflictException(
          "LIBRARY_INFO_CHANGED",
          "Someone else changed the library info in the meantime. Reload to see their change.");
    }
    info.change(
        contact,
        new About(
            RichText.clean(about.en()), RichText.clean(about.si()), RichText.clean(about.ta())),
        adminId);
    repository.flush();
    audit.record(
        "library.info_updated",
        adminId,
        "library_info",
        LibraryInfo.ID,
        Map.of("name", contact.name(), "email", contact.email()));
    return info;
  }

  /** First start: the row is created from the old bookhive.library.* settings. */
  @Transactional
  public void createIfMissing(Contact fromConfiguration) {
    if (repository.count() == 0) {
      repository.save(LibraryInfo.of(fromConfiguration));
    }
  }
}
