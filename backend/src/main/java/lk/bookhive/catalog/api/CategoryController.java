package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lk.bookhive.catalog.api.CategoryResponses.CategoryTreeResponse;
import lk.bookhive.catalog.service.CatalogCacheKeys;
import lk.bookhive.catalog.service.CategoryService;
import lk.bookhive.common.cache.PublicCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

/** The category tree for browsing and search filters (public). */
@RestController
@RequestMapping("/categories")
@Tag(name = "Catalog")
public class CategoryController {

  private final CategoryService categories;
  private final PublicCache cache;

  public CategoryController(CategoryService categories, PublicCache cache) {
    this.categories = categories;
    this.cache = cache;
  }

  @GetMapping
  @Operation(summary = "All categories as a tree, names in en/si/ta")
  public List<CategoryTreeResponse> tree() {
    // Shown on every search and browse page; changes rarely (evicted on any category change)
    return cache.get(
        CatalogCacheKeys.CATEGORY_TREE,
        CatalogCacheKeys.CATEGORY_TREE_TTL,
        new TypeReference<>() {},
        () -> categories.tree().stream().map(CategoryTreeResponse::from).toList());
  }
}
