import { describe, expect, it } from "vitest";
import { guardRedirect } from "@/features/auth/route-guard";

describe("route guard (proxy)", () => {
  it("lets public pages through without a session", () => {
    expect(guardRedirect("/en", "", undefined)).toBeNull();
    expect(guardRedirect("/si/login", "", undefined)).toBeNull();
    expect(guardRedirect("/en/accountant", "", undefined)).toBeNull();
  });

  it("sends visitors to the login page, remembering where they were going", () => {
    expect(guardRedirect("/si/account/loans", "?tab=due", undefined)).toBe(
      "/si/login?next=%2Faccount%2Floans%3Ftab%3Ddue",
    );
    expect(guardRedirect("/en/admin", "", "nonsense")).toBe("/en/login?next=%2Fadmin");
  });

  it("lets each role into its own areas only", () => {
    expect(guardRedirect("/en/account", "", "MEMBER")).toBeNull();
    expect(guardRedirect("/en/staff/desk", "", "MEMBER")).toBe("/en/account");
    expect(guardRedirect("/ta/admin", "", "LIBRARIAN")).toBe("/ta/staff");
    expect(guardRedirect("/en/staff", "", "LIBRARIAN")).toBeNull();
    expect(guardRedirect("/en/admin/staff", "", "LIBRARIAN.ADMIN")).toBeNull();
  });
});
