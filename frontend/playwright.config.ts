import { defineConfig, devices } from "@playwright/test";

const PORT = 3000;
const isCI = !!process.env.CI;

/**
 * End-to-end tests (pnpm test:e2e): a real browser clicks through the real app.
 * Playwright starts the frontend itself; locally it reuses `pnpm dev` if it's already running.
 * Report after a run: pnpm exec playwright show-report
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  // A test.only left in by mistake fails CI instead of silently skipping everything else
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  reporter: isCI
    ? [["github"], ["html", { open: "never" }]]
    : [["list"], ["html", { open: "never" }]],

  use: {
    baseURL: `http://localhost:${PORT}`,
    // Recorded on a retry: step-by-step DOM snapshots, network and console for debugging
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  // Public and member pages are mobile-first, so every test also runs on a phone screen
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["Pixel 7"] } },
  ],

  webServer: {
    // CI tests the production build; locally the dev server is faster to start
    command: isCI ? "pnpm build && pnpm start" : "pnpm dev",
    url: `http://localhost:${PORT}/en`,
    reuseExistingServer: !isCI,
    timeout: 180_000,
  },
});
