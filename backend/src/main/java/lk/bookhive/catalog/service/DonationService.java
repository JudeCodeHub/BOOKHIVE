package lk.bookhive.catalog.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.Donation;
import lk.bookhive.catalog.repository.CopyRepository;
import lk.bookhive.catalog.repository.DonationRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The donations log (CAT-10, checklist step 169). */
@Service
public class DonationService {

  private final DonationRepository donations;
  private final CopyRepository copies;
  private final AuditService audit;

  public DonationService(DonationRepository donations, CopyRepository copies, AuditService audit) {
    this.donations = donations;
    this.copies = copies;
    this.audit = audit;
  }

  /** A donation with how many copies came from it. */
  public record DonationView(Donation donation, long copyCount) {}

  /** Newest first; with {@code query}, only donors whose name contains it. */
  @Transactional(readOnly = true)
  public Page<DonationView> list(String query, int page, int size) {
    PageRequest request =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt", "createdAt"));
    Page<Donation> found =
        query == null || query.isBlank()
            ? donations.findAll(request)
            : donations.findByDonorNameContainingIgnoreCase(query.strip(), request);
    return found.map(d -> new DonationView(d, copies.countByDonationId(d.getId())));
  }

  @Transactional(readOnly = true)
  public Donation get(UUID id) {
    return require(id);
  }

  /** The copies that came with the donation, with their books. */
  @Transactional(readOnly = true)
  public List<Copy> copiesOf(UUID id) {
    require(id);
    return copies.findByDonationIdOrderByBarcode(id);
  }

  @Transactional
  public Donation create(
      String donorName, String donorContact, LocalDate receivedAt, String notes, UUID staffId) {
    Donation donation =
        donations.save(new Donation(donorName, donorContact, receivedAt, notes, staffId));
    audit.record(
        "donation.recorded",
        staffId,
        "donation",
        donation.getId(),
        Map.of("donor", donation.getDonorName()));
    return donation;
  }

  @Transactional
  public Donation update(
      UUID id,
      String donorName,
      String donorContact,
      LocalDate receivedAt,
      String notes,
      UUID staffId) {
    Donation donation = require(id);
    donation.update(donorName, donorContact, receivedAt, notes);
    audit.record("donation.updated", staffId, "donation", id, Map.of("donor", donorName.strip()));
    return donation;
  }

  Donation require(UUID id) {
    return donations
        .findById(id)
        .orElseThrow(() -> new NotFoundException("DONATION_NOT_FOUND", "Donation not found."));
  }
}
