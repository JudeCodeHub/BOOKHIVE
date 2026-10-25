package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lk.bookhive.catalog.api.CategoryResponses.CategoryTreeResponse;
import lk.bookhive.catalog.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The category tree for browsing and search filters (public). */
@RestController
@RequestMapping("/categories")
@Tag(name = "Catalog")
public class CategoryController {

  private final CategoryService categories;

  public CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  @GetMapping
  @Operation(summary = "All categories as a tree, names in en/si/ta")
  public List<CategoryTreeResponse> tree() {
    return categories.tree().stream().map(CategoryTreeResponse::from).toList();
  }
}
