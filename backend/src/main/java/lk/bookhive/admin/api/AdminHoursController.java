package lk.bookhive.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lk.bookhive.admin.api.LibraryController.DaySummary;
import lk.bookhive.admin.domain.Holiday;
import lk.bookhive.admin.service.LibraryHoursAdminService;
import lk.bookhive.admin.service.LibraryHoursAdminService.DayHours;
import lk.bookhive.admin.service.LibraryHoursAdminService.HolidayDetails;
import lk.bookhive.admin.service.LibraryHoursService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening hours and holidays for admins (ADM-3, checklist step 357). Times are Sri Lankan local
 * time. When days become closed, books due and reserved books waiting on those days move to the
 * next open day.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin: opening hours")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminHoursController {

  /** One weekday; both times null = closed all day. */
  public record DayHoursRequest(
      @NotNull DayOfWeek day,
      @Schema(nullable = true, example = "08:00") LocalTime opensAt,
      @Schema(nullable = true, example = "17:00") LocalTime closesAt) {}

  public record WeekRequest(@NotNull @Size(min = 7, max = 7) List<@Valid DayHoursRequest> week) {}

  public record WeekResponse(List<DaySummary> week) {}

  public record HolidayRequest(
      @NotNull LocalDate date,
      @NotBlank @Size(max = 120) String nameEn,
      @NotBlank @Size(max = 120) String nameSi,
      @NotBlank @Size(max = 120) String nameTa) {

    HolidayDetails details() {
      return new HolidayDetails(date, nameEn.strip(), nameSi.strip(), nameTa.strip());
    }
  }

  public record AdminHolidaySummary(
      UUID id, LocalDate date, String nameEn, String nameSi, String nameTa) {

    static AdminHolidaySummary from(Holiday h) {
      return new AdminHolidaySummary(
          h.getId(),
          h.getDate(),
          h.getName(Language.EN),
          h.getName(Language.SI),
          h.getName(Language.TA));
    }
  }

  private final LibraryHoursService hours;
  private final LibraryHoursAdminService admin;

  public AdminHoursController(LibraryHoursService hours, LibraryHoursAdminService admin) {
    this.hours = hours;
    this.admin = admin;
  }

  @GetMapping("/hours")
  @Operation(summary = "The weekly opening hours, Monday first")
  public WeekResponse week() {
    return new WeekResponse(hours.week().stream().map(DaySummary::from).toList());
  }

  @PutMapping("/hours")
  @Operation(
      summary =
          "Replace the weekly hours (all 7 days; loans due on days that become closed move to the"
              + " next open day)")
  public WeekResponse updateWeek(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WeekRequest request) {
    List<DayHours> week =
        request.week().stream().map(d -> new DayHours(d.day(), d.opensAt(), d.closesAt())).toList();
    return new WeekResponse(
        admin.updateWeek(week, adminId(jwt)).stream().map(DaySummary::from).toList());
  }

  @GetMapping("/holidays")
  @Operation(summary = "One year's holidays, in date order")
  public List<AdminHolidaySummary> holidays(@RequestParam @Min(2000) @Max(2100) int year) {
    return admin.holidays(year).stream().map(AdminHolidaySummary::from).toList();
  }

  @PostMapping("/holidays")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a closed day (today or later; one per date)")
  public AdminHolidaySummary addHoliday(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HolidayRequest request) {
    return AdminHolidaySummary.from(admin.addHoliday(request.details(), adminId(jwt)));
  }

  @PutMapping("/holidays/{id}")
  @Operation(summary = "Change a holiday's date or names")
  public AdminHolidaySummary updateHoliday(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody HolidayRequest request) {
    return AdminHolidaySummary.from(admin.updateHoliday(id, request.details(), adminId(jwt)));
  }

  @DeleteMapping("/holidays/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a holiday (the library opens that day after all)")
  public void deleteHoliday(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    admin.deleteHoliday(id, adminId(jwt));
  }

  private static UUID adminId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
