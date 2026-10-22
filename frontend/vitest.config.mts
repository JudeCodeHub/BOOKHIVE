import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

/**
 * Unit + component tests (pnpm test). Files: src/**\/*.test.ts(x), next to the code they test.
 * End-to-end browser tests live in e2e/ and run with Playwright instead.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    // Understands the "@/..." import alias from tsconfig.json
    tsconfigPaths: true,
  },
  test: {
    // A fake browser DOM so components can render
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    // Tests must not depend on order or leftovers from other tests
    restoreMocks: true,
    unstubGlobals: true,
  },
});
