package lk.bookhive.catalog.api;

import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.Category;
import lk.bookhive.catalog.domain.LibrarySection;
import lk.bookhive.catalog.service.CategoryService.Node;
import lk.bookhive.common.domain.Language;

/** Category JSON shared by the public and the staff endpoints. */
final class CategoryResponses {

  private CategoryResponses() {}

  /** A category with its subcategories (the tree). */
  public record CategoryTreeResponse(
      UUID id,
      String nameEn,
      String nameSi,
      String nameTa,
      String ddcCode,
      LibrarySection section,
      List<CategoryTreeResponse> children) {

    static CategoryTreeResponse from(Node node) {
      Category category = node.category();
      return new CategoryTreeResponse(
          category.getId(),
          category.getName(Language.EN),
          category.getName(Language.SI),
          category.getName(Language.TA),
          category.getDdcCode(),
          category.getSection(),
          node.children().stream().map(CategoryTreeResponse::from).toList());
    }
  }

  /** One category without children (e.g. on a book). */
  public record CategorySummary(
      UUID id,
      UUID parentId,
      String nameEn,
      String nameSi,
      String nameTa,
      String ddcCode,
      LibrarySection section) {

    static CategorySummary from(Category category) {
      return new CategorySummary(
          category.getId(),
          category.getParent() == null ? null : category.getParent().getId(),
          category.getName(Language.EN),
          category.getName(Language.SI),
          category.getName(Language.TA),
          category.getDdcCode(),
          category.getSection());
    }
  }
}
