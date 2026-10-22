package lk.bookhive.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Runs the handler against a fake controller; no Spring context or database needed. */
class GlobalExceptionHandlerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void notFoundBecomes404WithCode() throws Exception {
    mvc.perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Book 42 was not found"));
  }

  @Test
  void businessRuleBecomes422WithCode() throws Exception {
    mvc.perform(get("/rule"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("LOAN_LIMIT_REACHED"));
  }

  @Test
  void conflictBecomes409WithCode() throws Exception {
    mvc.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void unexpectedErrorBecomes500WithoutLeakingDetails() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail", not(containsString("secret"))));
  }

  @Test
  void springErrorGetsCodeFromStatus() throws Exception {
    mvc.perform(post("/not-found"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void invalidBodyListsEveryBadField() throws Exception {
    mvc.perform(
            post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"fullName\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("Email"))
        .andExpect(jsonPath("$.errors[?(@.field == 'fullName')].code").value("NotBlank"));
  }

  @Test
  void invalidQueryParamIsReportedByName() throws Exception {
    mvc.perform(get("/books").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].code").value("Min"));
  }

  record RegisterRequest(@NotBlank @Email String email, @NotBlank String fullName) {}

  @RestController
  static class ThrowingController {

    @PostMapping("/register")
    void register(@Valid @RequestBody RegisterRequest request) {}

    @GetMapping("/books")
    void books(@RequestParam @Min(0) int page) {}

    @GetMapping("/not-found")
    void notFound() {
      throw new NotFoundException("BOOK_NOT_FOUND", "Book 42 was not found");
    }

    @GetMapping("/rule")
    void rule() {
      throw new BusinessRuleException("LOAN_LIMIT_REACHED", "Member already has 4 items");
    }

    @GetMapping("/conflict")
    void conflict() {
      throw new ConflictException("EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("secret database password in stack trace");
    }
  }
}
