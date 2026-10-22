package lk.bookhive.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/register}. Password strength rules (common-password list) come
 * in checklist step 101.
 *
 * @param phone optional; a Sri Lankan number, e.g. 0771234567 or +94771234567
 * @param preferredLanguage optional "en", "si" or "ta" (default "en")
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 150) String fullName,
    @Pattern(regexp = "^(\\+94|0)[0-9]{9}$", message = "must be a Sri Lankan phone number")
        String phone,
    @Pattern(regexp = "^(en|si|ta)$", message = "must be en, si or ta") String preferredLanguage) {}
