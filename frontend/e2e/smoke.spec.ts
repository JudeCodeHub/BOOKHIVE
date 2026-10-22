import { expect, test } from "@playwright/test";

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

test("the member sidebar marks the current page", async ({ page }) => {
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

  await page.goto("/account");
  await expect(page).toHaveURL(/\/ta\/account$/);
});
