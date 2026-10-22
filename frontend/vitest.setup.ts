// Runs before every test file.

// Adds DOM matchers: expect(el).toBeInTheDocument(), toHaveAccessibleDescription()...
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Remove what each test rendered, so tests don't see each other's DOM
afterEach(() => {
  cleanup();
});
