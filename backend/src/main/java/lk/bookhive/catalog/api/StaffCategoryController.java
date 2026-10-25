package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.bookhive.catalog.api.CategoryResponses.CategorySummary;
import lk.bookhive.catalog.domain.LibrarySection;
import lk.bookhive.catalog.service.CategoryService;
import lk.bookhive.catalog.service.CategoryService.Form;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Category tree management (librarians and admins). Reading the tree: GET /categories. */
@RestController
@RequestMapping("/staff/categories")
@Tag(name = "Staff: categories")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffCategoryController {

  private final CategoryService categories;

  public StaffCategoryController(CategoryService categories) {
    this.categories = categories;
  }

  /**
   * @param parentId null = top level
   * @param ddcCode Dewey number like 891.48; optional
   */
  public record CategoryRequest(
      UUID parentId,
      @NotBlank @Size(max = 120) String nameEn,
      @NotBlank @Size(max = 120) String nameSi,
      @NotBlank @Size(max = 120) String nameTa,
      @Pattern(regexp = "^$|^[0-9]{3}(\\.[0-9]+)?$", message = "a Dewey number like 891.48")
          String ddcCode,
      @NotNull LibrarySection section) {

    Form form() {
      return new Form(parentId, nameEn, nameSi, nameTa, ddcCode, section);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a category (optionally under a parent)")
  public CategorySummary create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest request) {
    return CategorySummary.from(categories.create(request.form(), staffId(jwt)));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Rename and/or move a category (400 CATEGORY_LOOP if it would loop)")
  public CategorySummary update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CategoryRequest request) {
    return CategorySummary.from(categories.update(id, request.form(), staffId(jwt)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete an empty category (no subcategories, no books)")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    categories.delete(id, staffId(jwt));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
