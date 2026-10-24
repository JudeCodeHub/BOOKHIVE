import { describe, expect, it } from "vitest";
import { afterLoginPath, isSafeInternalPath } from "@/features/auth/redirects";
import type { SessionUser } from "@/features/auth/session";

const user = (roles: SessionUser["roles"]): SessionUser => ({
  id: "1",
  email: "a@b.lk",
  fullName: "A",
  roles,
  preferredLanguage: "en",
});

describe("after login", () => {
  it("goes back to the page the user wanted", () => {
    expect(afterLoginPath(user(["MEMBER"]), "/account/loans")).toBe("/account/loans");
  });

  it("otherwise goes to the user's own area", () => {
    expect(afterLoginPath(user(["MEMBER"]), null)).toBe("/account");
    expect(afterLoginPath(user(["LIBRARIAN"]), null)).toBe("/staff");
    expect(afterLoginPath(user(["ADMIN"]), null)).toBe("/admin");
  });

  it("never redirects to another website", () => {
    for (const evil of [
      "https://evil.example",
      "//evil.example",
      "/\\evil.example",
      "javascript:alert(1)",
    ]) {
      expect(isSafeInternalPath(evil)).toBe(false);
      expect(afterLoginPath(user(["MEMBER"]), evil)).toBe("/account");
    }
  });
});
