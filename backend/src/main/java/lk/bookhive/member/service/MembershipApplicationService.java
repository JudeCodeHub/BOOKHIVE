package lk.bookhive.member.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.crypto.FieldEncryption;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.storage.FileStorage;
import lk.bookhive.common.storage.UploadCheck;
import lk.bookhive.common.storage.UploadCheck.CheckedUpload;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.domain.DocumentKind;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.Member.ApplicationDetails;
import lk.bookhive.member.domain.MemberDocument;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.domain.MembershipType;
import lk.bookhive.member.domain.RequiredDocument;
import lk.bookhive.member.repository.MemberDocumentRepository;
import lk.bookhive.member.repository.MemberRepository;
import lk.bookhive.member.repository.MembershipTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Online membership application (MEM-1, checklist step 125). */
@Service
public class MembershipApplicationService {

  private static final Logger log = LoggerFactory.getLogger(MembershipApplicationService.class);

  // Old NIC: 9 digits + V or X (e.g. 851234567V). New NIC (since 2016): 12 digits.
  private static final Pattern NIC = Pattern.compile("^(\\d{9}[VX]|\\d{12})$");

  private static final int ADULT_AGE = 18;

  private final MemberRepository members;
  private final MembershipTypeRepository types;
  private final MemberDocumentRepository documents;
  private final FileStorage storage;
  private final AuditService audit;
  private final LibraryTime time;
  private final FieldEncryption encryption;

  public MembershipApplicationService(
      MemberRepository members,
      MembershipTypeRepository types,
      MemberDocumentRepository documents,
      FileStorage storage,
      AuditService audit,
      LibraryTime time,
      FieldEncryption encryption) {
    this.members = members;
    this.types = types;
    this.documents = documents;
    this.storage = storage;
    this.audit = audit;
    this.time = time;
    this.encryption = encryption;
  }

  /** What the applicant sends, before checking. */
  public record ApplicationForm(
      String membershipType,
      String nic,
      LocalDate dateOfBirth,
      String addressLine,
      String city,
      String postalCode,
      String guardianMemberNo) {}

  /** An uploaded file, independent of the web layer. */
  public record Upload(InputStream content, long sizeBytes, String filename) {}

  /** Types someone can apply for now, for the application form. */
  @Transactional(readOnly = true)
  public List<MembershipType> activeTypes() {
    return types.findByActiveTrueOrderByCode();
  }

  /**
   * Checks the application against the chosen type's rules and saves it as PENDING, with the proof
   * document. One application per user; after a rejection they may apply again.
   *
   * @param document the NIC / student ID scan; may be null only if the type needs none
   */
  @Transactional
  public Member apply(UUID userId, ApplicationForm form, Upload document) {
    Member existing = members.findByUserId(userId).orElse(null);
    if (existing != null && existing.getStatus() != MemberStatus.REJECTED) {
      throw new ConflictException(
          "APPLICATION_EXISTS",
          existing.getStatus() == MemberStatus.PENDING
              ? "Your application is already waiting for review."
              : "You are already a member.");
    }

    MembershipType type =
        types
            .findByCode(form.membershipType().toUpperCase(Locale.ROOT))
            .filter(MembershipType::isActive)
            .orElseThrow(
                () ->
                    new FieldValidationException(
                        "membershipType",
                        "MEMBERSHIP_TYPE_UNKNOWN",
                        "Choose one of the membership types."));

    checkAge(type, form.dateOfBirth());
    String nic = checkNic(type, form.nic());
    Member guardian = checkGuardian(type, form.guardianMemberNo());
    if (type.getRequiredDocument() != RequiredDocument.NONE
        && (document == null || document.sizeBytes() == 0)) {
      throw new FieldValidationException(
          "document", "DOCUMENT_REQUIRED", "Please upload a photo or scan of your document.");
    }

    ApplicationDetails details =
        new ApplicationDetails(
            type,
            nic,
            encryption.lookupHash(nic),
            form.dateOfBirth(),
            form.addressLine().strip(),
            form.city().strip(),
            blankToNull(form.postalCode()),
            guardian);
    Member member;
    if (existing == null) {
      member = members.save(Member.apply(userId, details, time.now()));
    } else {
      existing.reapply(details, time.now());
      member = existing;
    }

    if (document != null && type.getRequiredDocument() != RequiredDocument.NONE) {
      saveDocument(member, DocumentKind.valueOf(type.getRequiredDocument().name()), document);
    }

    audit.record(
        "member.applied", userId, "member", member.getId(), Map.of("type", type.getCode()));
    log.info("Membership application {} ({})", member.getId(), type.getCode());
    return member;
  }

  private void checkAge(MembershipType type, LocalDate dateOfBirth) {
    LocalDate today = time.today();
    if (!dateOfBirth.isBefore(today)) {
      throw new FieldValidationException(
          "dateOfBirth", "DATE_OF_BIRTH_INVALID", "Enter your date of birth.");
    }
    int age = Period.between(dateOfBirth, today).getYears();
    if (!type.acceptsAge(age)) {
      throw new FieldValidationException(
          "dateOfBirth",
          "AGE_NOT_ALLOWED",
          "Your age doesn't match this membership type. Please choose another type.");
    }
  }

  /**
   * Junior members need an active adult member as guardian (MEM-2), given by member number. Staff
   * still check the relationship when they review the application.
   */
  private Member checkGuardian(MembershipType type, String guardianMemberNo) {
    if (!type.isRequiresGuardian()) {
      return null;
    }
    if (guardianMemberNo == null || guardianMemberNo.isBlank()) {
      throw new FieldValidationException(
          "guardianMemberNo",
          "GUARDIAN_REQUIRED",
          "A junior member needs a parent or guardian who is a library member.");
    }
    Member guardian =
        members
            .findByMemberNo(guardianMemberNo.strip().toUpperCase(Locale.ROOT))
            .orElseThrow(
                () ->
                    new FieldValidationException(
                        "guardianMemberNo",
                        "GUARDIAN_NOT_FOUND",
                        "No member has this number. Check the guardian's library card."));
    if (guardian.getStatus() != MemberStatus.ACTIVE) {
      throw new FieldValidationException(
          "guardianMemberNo", "GUARDIAN_NOT_ACTIVE", "The guardian's membership is not active.");
    }
    int guardianAge = Period.between(guardian.getDateOfBirth(), time.today()).getYears();
    if (guardian.getMembershipType().isRequiresGuardian() || guardianAge < ADULT_AGE) {
      throw new FieldValidationException(
          "guardianMemberNo", "GUARDIAN_NOT_ADULT", "The guardian must be an adult member.");
    }
    return guardian;
  }

  /** Returns the NIC in standard form (upper case, no spaces), or null if the type needs none. */
  private static String checkNic(MembershipType type, String nic) {
    if (type.getRequiredDocument() != RequiredDocument.NIC) {
      return null;
    }
    String normalised = nic == null ? "" : nic.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    if (!NIC.matcher(normalised).matches()) {
      throw new FieldValidationException(
          "nic", "NIC_INVALID", "Enter your NIC number, e.g. 851234567V or 198512345678.");
    }
    return normalised;
  }

  private void saveDocument(Member member, DocumentKind kind, Upload upload) {
    // Random key: never derived from the user's file name
    String key = "member-documents/" + member.getId() + "/" + UUID.randomUUID();
    String contentType;
    try (InputStream raw = upload.content()) {
      // Size and real file type (from the first bytes), not what the browser claims
      CheckedUpload checked = UploadCheck.document("document", raw, upload.sizeBytes());
      contentType = checked.type().contentType();
      storage.put(key, checked.content(), upload.sizeBytes(), contentType);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not store the uploaded document", e);
    }
    documents.save(
        new MemberDocument(
            member, kind, key, contentType, upload.sizeBytes(), shorten(upload.filename())));
  }

  /** Kept only to show staff; the column holds 255 characters. */
  private static String shorten(String filename) {
    return filename == null || filename.length() <= 255 ? filename : filename.substring(0, 255);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
