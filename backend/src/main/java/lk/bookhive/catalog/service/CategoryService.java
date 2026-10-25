package lk.bookhive.catalog.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.bookhive.catalog.domain.Category;
import lk.bookhive.catalog.domain.LibrarySection;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.CategoryRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The category tree (CAT-6, checklist step 156), based on the Dewey Decimal Classification. */
@Service
public class CategoryService {

  private final CategoryRepository categories;
  private final BookRepository books;
  private final AuditService audit;

  public CategoryService(CategoryRepository categories, BookRepository books, AuditService audit) {
    this.categories = categories;
    this.books = books;
    this.audit = audit;
  }

  /** One category with its children, sorted by DDC number then English name. */
  public record Node(Category category, List<Node> children) {}

  /** What staff fill in. {@code parentId} null = a top-level category. */
  public record Form(
      UUID parentId,
      String nameEn,
      String nameSi,
      String nameTa,
      String ddcCode,
      LibrarySection section) {}

  /** The whole tree, built from one query (a library has at most a few hundred categories). */
  @Transactional(readOnly = true)
  public List<Node> tree() {
    List<Category> all = categories.findAllByOrderByDdcCodeAscNameEnAsc();
    Map<UUID, List<Category>> byParent = new HashMap<>();
    for (Category category : all) {
      UUID parentId = category.getParent() == null ? null : category.getParent().getId();
      byParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(category);
    }
    return nodes(byParent, null);
  }

  private static List<Node> nodes(Map<UUID, List<Category>> byParent, UUID parentId) {
    return byParent.getOrDefault(parentId, List.of()).stream()
        .map(category -> new Node(category, nodes(byParent, category.getId())))
        .toList();
  }

  @Transactional
  public Category create(Form form, UUID staffId) {
    Category parent = form.parentId() == null ? null : require(form.parentId());
    checkSiblingName(form.parentId(), form.nameEn(), null);
    Category category =
        categories.save(
            new Category(
                parent,
                form.nameEn(),
                form.nameSi(),
                form.nameTa(),
                form.ddcCode(),
                form.section()));
    audit.record(
        "category.created", staffId, "category", category.getId(), Map.of("name", form.nameEn()));
    return category;
  }

  /** Rename and/or move. Moving under itself or one of its own children is refused. */
  @Transactional
  public Category update(UUID id, Form form, UUID staffId) {
    Category category = require(id);
    Category newParent = form.parentId() == null ? null : require(form.parentId());
    for (Category ancestor = newParent; ancestor != null; ancestor = ancestor.getParent()) {
      if (ancestor.getId().equals(id)) {
        throw new FieldValidationException(
            "parentId",
            "CATEGORY_LOOP",
            "A category can't be moved under itself or one of its own subcategories.");
      }
    }
    checkSiblingName(form.parentId(), form.nameEn(), id);
    category.moveTo(newParent);
    category.rename(form.nameEn(), form.nameSi(), form.nameTa(), form.ddcCode(), form.section());
    audit.record("category.updated", staffId, "category", id, Map.of("name", form.nameEn()));
    return category;
  }

  /** Only empty leaves: no subcategories and no books, so nothing is left without a home. */
  @Transactional
  public void delete(UUID id, UUID staffId) {
    Category category = require(id);
    boolean hasChildren =
        categories.findAllByOrderByDdcCodeAscNameEnAsc().stream()
            .anyMatch(c -> c.getParent() != null && c.getParent().getId().equals(id));
    if (hasChildren) {
      throw new ConflictException(
          "CATEGORY_HAS_CHILDREN", "Move or delete its subcategories first.");
    }
    if (books.existsByCategoriesId(id)) {
      throw new ConflictException(
          "CATEGORY_HAS_BOOKS", "Books are in this category, so it can't be deleted.");
    }
    categories.delete(category);
    audit.record(
        "category.deleted", staffId, "category", id, Map.of("name", category.getName(Language.EN)));
  }

  /** Same rule as the database's unique index, but with a message staff can act on. */
  private void checkSiblingName(UUID parentId, String nameEn, UUID exceptId) {
    String wanted = nameEn.strip().toLowerCase(Locale.ROOT);
    boolean taken =
        categories.findAllByOrderByDdcCodeAscNameEnAsc().stream()
            .filter(c -> !c.getId().equals(exceptId))
            .filter(
                c -> Objects.equals(c.getParent() == null ? null : c.getParent().getId(), parentId))
            .anyMatch(c -> c.getName(Language.EN).toLowerCase(Locale.ROOT).equals(wanted));
    if (taken) {
      throw new FieldValidationException(
          "nameEn", "CATEGORY_EXISTS", "There is already a category with this name here.");
    }
  }

  private Category require(UUID id) {
    return categories
        .findById(id)
        .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found."));
  }
}
