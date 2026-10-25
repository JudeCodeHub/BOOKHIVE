package lk.bookhive.member.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.member.domain.MembershipType;
import lk.bookhive.member.domain.MembershipType.Rules;
import lk.bookhive.member.repository.MembershipTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admins manage membership types and their borrowing rules (PRD §4.1, checklist step 145). */
@Service
public class MembershipTypeAdminService {

  private final MembershipTypeRepository types;
  private final AuditService audit;

  public MembershipTypeAdminService(MembershipTypeRepository types, AuditService audit) {
    this.types = types;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<MembershipType> all() {
    return types.findAllByOrderByCode();
  }

  @Transactional
  public MembershipType create(String code, Rules rules, UUID adminId) {
    String normalised = code.strip().toUpperCase(Locale.ROOT);
    if (types.existsByCode(normalised)) {
      throw new ConflictException(
          "MEMBERSHIP_TYPE_EXISTS", "A membership type with this code already exists.");
    }
    check(rules);
    MembershipType type = types.save(MembershipType.create(normalised, rules));
    audit.record(
        "membership_type.created", adminId, "membership_type", type.getId(), summary(type));
    return type;
  }

  @Transactional
  public MembershipType update(UUID id, Rules rules, UUID adminId) {
    MembershipType type =
        types
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "MEMBERSHIP_TYPE_NOT_FOUND", "Membership type not found."));
    check(rules);
    type.update(rules);
    audit.record(
        "membership_type.updated", adminId, "membership_type", type.getId(), summary(type));
    return type;
  }

  /** Rules that span several fields (single fields are checked by the request annotations). */
  private static void check(Rules rules) {
    if (rules.minAge() != null && rules.maxAge() != null && rules.minAge() > rules.maxAge()) {
      throw new FieldValidationException(
          "maxAge", "AGE_RANGE_INVALID", "The maximum age must be at least the minimum age.");
    }
  }

  /** What changed, for the audit log: the rules that matter to members. */
  private static Map<String, Object> summary(MembershipType type) {
    Map<String, Object> details = new HashMap<>();
    details.put("code", type.getCode());
    details.put("maxItems", type.getMaxItems());
    details.put("loanDays", type.getLoanDays());
    details.put("maxRenewals", type.getMaxRenewals());
    details.put("maxHolds", type.getMaxHolds());
    details.put("annualFee", type.getAnnualFee().amount().toPlainString());
    details.put("active", type.isActive());
    return details;
  }
}
