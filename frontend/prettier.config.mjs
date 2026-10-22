/**
 * Prettier formats the code; ESLint only checks for bugs (see eslint-config-prettier).
 * `pnpm format` fixes files, `pnpm format:check` fails if something isn't formatted.
 *
 * @type {import("prettier").Config & import("prettier-plugin-tailwindcss").PluginOptions}
 */
const config = {
  // Matches .editorconfig: 2 spaces, LF line endings
  tabWidth: 2,
  endOfLine: "lf",
  printWidth: 100,
  semi: true,
  singleQuote: false,
  trailingComma: "all",

  // Sorts Tailwind classes in a fixed order, e.g. "p-4 flex" -> "flex p-4"
  plugins: ["prettier-plugin-tailwindcss"],
  // Tailwind 4 has no tailwind.config.js; the plugin reads the theme from the CSS file
  tailwindStylesheet: "./src/app/globals.css",
};

export default config;
