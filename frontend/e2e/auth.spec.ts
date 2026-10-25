import { expect, test, type Page } from "@playwright/test";

// The whole sign-up journey against the real backend (checklist step 120):
// register -> read the email in Mailpit -> open the link -> log in -> log out.
//
// Needs the backend (./gradlew bootRun) and infra/docker-compose (Postgres, Redis, Mailpit).
// Without them these tests are skipped, so the smoke tests still run on their own.

const BACKEND = process.env.E2E_BACKEND_URL ?? "http://127.0.0.1:8080";
const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://127.0.0.1:8025";

type MailpitSummary = { ID: string };
type MailpitMessage = { ID: string; Subject: string; Text: string };

async function isUp(url: string) {
  try {
    return (await fetch(url, { signal: AbortSignal.timeout(2_000) })).ok;
  } catch {
    return false;
  }
}

/** Waits for the newest email sent to `email` and returns it (Mailpit's REST API). */
async function waitForEmail(email: string): Promise<MailpitMessage> {
  let found: MailpitSummary | undefined;
  await expect
    .poll(
      async () => {
        const query = encodeURIComponent(`to:"${email}"`);
        const response = await fetch(`${MAILPIT}/api/v1/search?query=${query}`);
        found = ((await response.json()) as { messages: MailpitSummary[] }).messages[0];
        return found;
      },
      { message: `an email to ${email}`, timeout: 15_000 },
    )
    .toBeDefined();
  const response = await fetch(`${MAILPIT}/api/v1/message/${found!.ID}`);
  return (await response.json()) as MailpitMessage;
}

async function deleteEmail(id: string) {
  await fetch(`${MAILPIT}/api/v1/messages`, {
    method: "DELETE",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ IDs: [id] }),
  });
}

async function fillIn(page: Page, fields: Record<string, string>) {
  for (const [label, value] of Object.entries(fields)) {
    await page.getByLabel(label, { exact: false }).fill(value);
  }
}

test.describe("sign-up journey", () => {
  test.beforeAll(async ({}, testInfo) => {
    // The backend allows 5 registrations per hour per IP: one browser is enough for this journey
    test.skip(testInfo.project.name !== "desktop", "runs once, on the desktop browser");
    const ready =
      (await isUp(`${BACKEND}/actuator/health`)) && (await isUp(`${MAILPIT}/api/v1/info`));
    test.skip(!ready, `needs the backend (${BACKEND}) and Mailpit (${MAILPIT}) running`);
  });

  test("register, confirm the email, log in and log out", async ({ page }) => {
    // A new address every run, so the test never trips over an earlier run's account
    const email = `e2e-${Date.now()}@bookhive.test`;
    const password = "a quiet library at dawn";

    // 1. Register
    await page.goto("/en/register");
    await fillIn(page, { "Full name": "Sunil Reader", Email: email, Password: password });
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page.getByRole("heading", { name: "Check your email" })).toBeVisible();

    // 2. The confirmation email arrives, in English, with a link back to the site
    const message = await waitForEmail(email);
    const link = message.Text.match(/https?:\/\/\S+\/en\/verify-email\?token=[\w-]+/)?.[0];
    expect(link, "verify link in the email").toBeDefined();

    // 3. Open the link (same path on the test server)
    const { pathname, search } = new URL(link!);
    await page.goto(pathname + search);
    await expect(page.getByRole("heading", { name: "Email confirmed" })).toBeVisible();
    await deleteEmail(message.ID);

    // 4. Log in: a member lands in My account, and the header shows their first name
    await page.getByRole("link", { name: "Log in" }).last().click();
    await expect(page).toHaveURL(/\/en\/login/);
    await fillIn(page, { Email: email, Password: password });
    await page.getByRole("main").getByRole("button", { name: "Log in" }).click();

    await expect(page).toHaveURL(/\/en\/account$/);
    await expect(page.getByText("Welcome, Sunil Reader!")).toBeVisible();
    await expect(page.getByRole("heading", { level: 1, name: "My account" })).toBeVisible();

    // A reload keeps the session (the refresh cookie gets a new access token)
    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: "My account" })).toBeVisible();

    // 5. Log out from the account menu: back on the home page, and My account is closed again
    await page.getByRole("button", { name: "Sunil" }).click();
    await page.getByRole("menuitem", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/en$/);
    await expect(page.getByText("You're logged out. See you soon!")).toBeVisible();

    await page.goto("/en/account");
    await expect(page).toHaveURL(/\/en\/login\?next=%2Faccount$/);
  });
});
