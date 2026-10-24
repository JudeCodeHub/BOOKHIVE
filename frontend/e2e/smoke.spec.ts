import { expect, test, type Page } from "@playwright/test";

// Smoke tests: the app shell works in a real browser. Feature flows (login, checkout...)
// get their own spec files as they're built.

test("the home page redirects to a language and shows the search box", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveURL(/\/en$/);
  await expect(
    page.getByRole("heading", { level: 1, name: "Every book, one search away" }),
  ).toBeVisible();
  await expect(
    page.getByRole("main").getByRole("searchbox", { name: "Search the catalog" }),
  ).toBeVisible();
});

test("each language has its own URL, text and html lang", async ({ page }) => {
  await page.goto("/si");
  await expect(page.locator("html")).toHaveAttribute("lang", "si");
  await expect(
    page.getByRole("heading", { level: 1, name: "සෑම පොතක්ම, එක් සෙවුමකින්" }),
  ).toBeVisible();

  await page.goto("/ta");
  await expect(page.locator("html")).toHaveAttribute("lang", "ta");
  await expect(
    page.getByRole("heading", { level: 1, name: "ஒவ்வொரு நூலும், ஒரு தேடலில்" }),
  ).toBeVisible();
});

test("searching goes to the search page in the current language", async ({ page }) => {
  await page.goto("/si");

  const search = page.getByRole("main").getByRole("searchbox");
  await search.fill("madol doova");
  await search.press("Enter");

  await expect(page).toHaveURL(/\/si\/search\?q=madol\+doova$/);
});

test("unknown pages show the translated 404 page", async ({ page }) => {
  const response = await page.goto("/en/this-page-does-not-exist");

  expect(response?.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "Page not found" })).toBeVisible();
  await page.getByRole("link", { name: "Go to the home page" }).click();
  await expect(page).toHaveURL(/\/en$/);
});

/** Pretends a member is logged in: the hint cookie for the proxy, a fake refresh for the page. */
async function loginAsMember(page: Page) {
  await page
    .context()
    .addCookies([{ name: "bookhive_session", value: "MEMBER", url: "http://localhost:3000" }]);
  await page.route("**/api/v1/auth/refresh", (route) =>
    route.fulfill({
      json: {
        accessToken: "e2e-token",
        tokenType: "Bearer",
        expiresAt: "2099-01-01T00:00:00Z",
        user: {
          id: "00000000-0000-0000-0000-000000000001",
          email: "nimali@mail.lk",
          fullName: "Nimali Perera",
          roles: ["MEMBER"],
          preferredLanguage: "en",
        },
      },
    }),
  );
}

test("visitors opening My account are sent to log in first", async ({ page }) => {
  await page.goto("/en/account/loans");

  await expect(page).toHaveURL(/\/en\/login\?next=%2Faccount%2Floans$/);
});

test("the member sidebar marks the current page", async ({ page }) => {
  await loginAsMember(page);
  await page.goto("/en/account");

  const nav = page.getByRole("navigation", { name: "My account" });
  await expect(nav.getByRole("link", { name: "Dashboard" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await expect(nav.getByRole("link", { name: "My loans" })).not.toHaveAttribute("aria-current");
});

test("dark mode can be toggled and is remembered", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "light" });
  await page.goto("/en");
  await expect(page.locator("html")).not.toHaveClass(/\bdark\b/);

  await page.getByRole("button", { name: "Toggle dark mode" }).click();
  await expect(page.locator("html")).toHaveClass(/\bdark\b/);

  await page.reload();
  await expect(page.locator("html")).toHaveClass(/\bdark\b/);
});

test("the language switcher keeps the page and the search query", async ({ page }) => {
  await loginAsMember(page);
  await page.goto("/en/account?tab=loans");

  await page.getByRole("link", { name: "සිංහල" }).click();
  await expect(page).toHaveURL(/\/si\/account\?tab=loans$/);

  await page.goto("/si/account");
  await page.getByRole("link", { name: "தமிழ்" }).click();
  await expect(page).toHaveURL(/\/ta\/account$/);
  await expect(page.getByRole("heading", { level: 1, name: "என் கணக்கு" })).toBeVisible();
});

test("the chosen language is remembered for links without a language", async ({ page }) => {
  await page.goto("/en");
  await page.getByRole("link", { name: "தமிழ்" }).click();
  await expect(page).toHaveURL(/\/ta$/);

  await page.goto("/register");
  await expect(page).toHaveURL(/\/ta\/register$/);
});
