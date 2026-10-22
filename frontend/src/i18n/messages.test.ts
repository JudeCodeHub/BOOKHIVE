import { describe, expect, it } from "vitest";
import en from "../../messages/en.json";
import si from "../../messages/si.json";
import ta from "../../messages/ta.json";

/** Flattens {"Header": {"login": "..."}} to {"Header.login": "..."}. */
function flatten(messages: object, prefix = ""): Record<string, string> {
  return Object.entries(messages).reduce<Record<string, string>>((all, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === "object"
      ? { ...all, ...flatten(value, path) }
      : { ...all, [path]: String(value) };
  }, {});
}

const english = flatten(en);

// A missing translation would show the raw key (e.g. "Header.login") to Sinhala/Tamil users
describe.each([
  ["si", flatten(si)],
  ["ta", flatten(ta)],
])("%s translations", (_locale, translated) => {
  it("have exactly the same keys as English", () => {
    expect(Object.keys(translated).sort()).toEqual(Object.keys(english).sort());
  });

  it("have no empty texts", () => {
    expect(Object.entries(translated).filter(([, text]) => !text.trim())).toEqual([]);
  });

  it("keep the same {placeholders} as English", () => {
    const placeholders = (text: string) => (text.match(/\{\w+\}/g) ?? []).sort();
    for (const [key, text] of Object.entries(english)) {
      expect(placeholders(translated[key] ?? ""), key).toEqual(placeholders(text));
    }
  });
});
