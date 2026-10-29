package lk.bookhive.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.Language;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The library's own details (table {@code library_info}): one row, changed by admins on the Library
 * info page (ADM-6, checklist step 377).
 */
@Entity
@Table(name = "library_info")
@EntityListeners(AuditingEntityListener.class)
public class LibraryInfo {

  /** There is one library. */
  public static final short ID = 1;

  @Id private short id = ID;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 200)
  private String addressLine;

  @Column(nullable = false, length = 80)
  private String city;

  @Column(length = 10)
  private String postalCode;

  @Column(nullable = false, length = 30)
  private String phone;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(name = "about_en", nullable = false, columnDefinition = "text")
  private String aboutEn = "";

  @Column(name = "about_si", nullable = false, columnDefinition = "text")
  private String aboutSi = "";

  @Column(name = "about_ta", nullable = false, columnDefinition = "text")
  private String aboutTa = "";

  private UUID updatedBy;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  // Two admins saving at once: the second gets a conflict instead of silently overwriting
  @Version
  @Column(nullable = false)
  private Long version;

  protected LibraryInfo() {
    // for JPA
  }

  /** The details, without the about texts (they start empty). */
  public record Contact(
      String name,
      String addressLine,
      String city,
      String postalCode,
      String phone,
      String email,
      double latitude,
      double longitude) {}

  /** The about text in each language; sanitised HTML (RichText), may be empty. */
  public record About(String en, String si, String ta) {}

  public static LibraryInfo of(Contact contact) {
    LibraryInfo info = new LibraryInfo();
    info.setContact(contact);
    return info;
  }

  public void change(Contact contact, About about, UUID adminId) {
    setContact(contact);
    this.aboutEn = about.en();
    this.aboutSi = about.si();
    this.aboutTa = about.ta();
    this.updatedBy = adminId;
  }

  private void setContact(Contact c) {
    this.name = c.name();
    this.addressLine = c.addressLine();
    this.city = c.city();
    this.postalCode = c.postalCode();
    this.phone = c.phone();
    this.email = c.email();
    this.latitude = c.latitude();
    this.longitude = c.longitude();
  }

  public Contact getContact() {
    return new Contact(name, addressLine, city, postalCode, phone, email, latitude, longitude);
  }

  public About getAbout() {
    return new About(aboutEn, aboutSi, aboutTa);
  }

  public String getAbout(Language language) {
    return switch (language) {
      case EN -> aboutEn;
      case SI -> aboutSi;
      case TA -> aboutTa;
    };
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }
}
