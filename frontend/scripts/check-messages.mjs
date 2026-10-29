// Checks the translation files (messages/en.json, si.json, ta.json) against each other.
//
//   pnpm i18n:check
//
// Fails (exit code 1) when Sinhala or Tamil is missing a key or has one English doesn't, when a
// message isn't valid ICU (a broken {count, plural, ...}), or when a translation uses different
// {placeholders} than English (the value would never be filled in). Used keys are already
// checked by TypeScript against en.json (src/i18n/app-config.ts).
//
// Also lists texts that are exactly the same as English, as a hint: most are fine (BookHive,
// ISBN, SMS, "{count} / {capacity}"), but a new one may be a sentence nobody translated yet.

import { readFileSync } from "node:fs";
import { parse, TYPE } from "@formatjs/icu-messageformat-parser";

const BASE = "en";
const OTHERS = ["si", "ta"];

/** { "Events": { "title": "..." } } -> { "Events.title": "..." } */
function flatten(object, prefix = "", out = {}) {
  for (const [key, value] of Object.entries(object)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === "object") flatten(value, path, out);
    else out[path] = value;
  }
  return out;
}

/** The names of all {placeholders} and <tags> in a parsed message, nested ones included. */
function argumentNames(elements, names = new Set()) {
  for (const element of elements) {
    if (element.type !== TYPE.literal && element.type !== TYPE.pound) names.add(element.value);
    for (const option of Object.values(element.options ?? {})) argumentNames(option.value, names);
    if (element.children) argumentNames(element.children, names);
  }
  return names;
}

const messages = Object.fromEntries(
  [BASE, ...OTHERS].map((locale) => [
    locale,
    flatten(JSON.parse(readFileSync(new URL(`../messages/${locale}.json`, import.meta.url)))),
  ]),
);

const problems = [];
const arguments_ = {};
for (const [locale, entries] of Object.entries(messages)) {
  for (const [key, text] of Object.entries(entries)) {
    try {
      arguments_[`${locale}:${key}`] = [...argumentNames(parse(text))].sort().join(", ");
    } catch (error) {
      problems.push(`${locale} ${key}: not valid ICU (${error.message})`);
    }
  }
}

/** Each language's own letters: a translation of real words should use them. */
const SCRIPTS = { si: /[\u0d80-\u0dff]/, ta: /[\u0b80-\u0bff]/ };

const sameAsEnglish = [];
const notTranslated = [];
for (const locale of OTHERS) {
  for (const key of Object.keys(messages[BASE])) {
    if (!(key in messages[locale])) {
      problems.push(`${locale} ${key}: missing`);
      continue;
    }
    const expected = arguments_[`${BASE}:${key}`];
    const actual = arguments_[`${locale}:${key}`];
    if (expected !== undefined && actual !== undefined && expected !== actual) {
      problems.push(`${locale} ${key}: uses {${actual}}, English uses {${expected}}`);
    }
    if (messages[locale][key] === messages[BASE][key] && /[A-Za-z]{3,}/.test(messages[BASE][key])) {
      sameAsEnglish.push(`${locale} ${key} = ${JSON.stringify(messages[BASE][key])}`);
    }
    // English has real words here, so the translation should be in that language's own letters
    // (names, abbreviations like ISBN and "{count} / {capacity}" patterns are the exceptions)
    const script = SCRIPTS[locale];
    if (script && /[A-Za-z]{4,}/.test(messages[BASE][key]) && !script.test(messages[locale][key])) {
      notTranslated.push(`${locale} ${key} = ${JSON.stringify(messages[locale][key])}`);
    }
  }
  for (const key of Object.keys(messages[locale])) {
    if (!(key in messages[BASE])) problems.push(`${locale} ${key}: not in English (left over?)`);
  }
}

console.log(`${Object.keys(messages[BASE]).length} messages in ${[BASE, ...OTHERS].join(", ")}`);
if (sameAsEnglish.length > 0) {
  console.log(`\nSame as English (usually fine, check new ones):\n  ${sameAsEnglish.join("\n  ")}`);
}
if (notTranslated.length > 0) {
  console.log(`\nNo ${OTHERS.join("/")} letters (fine for names and patterns, check new ones):`);
  console.log(`  ${notTranslated.join("\n  ")}`);
}
if (problems.length > 0) {
  console.error(`\n${problems.length} problem(s):\n  ${problems.join("\n  ")}`);
  process.exit(1);
}
console.log("\nNo problems found.");
