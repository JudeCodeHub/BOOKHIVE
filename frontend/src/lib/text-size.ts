/**
 * Reading size (A− / A / A+), for people who find the default text small (checklist step 383).
 * Everything on the site is sized in rem, so one font-size on <html> scales the whole page,
 * including spacing. Kept per device in localStorage, like the light/dark choice.
 */
export const TEXT_SIZES = ["small", "normal", "large", "larger"] as const;
export type TextSize = (typeof TEXT_SIZES)[number];

export const DEFAULT_TEXT_SIZE: TextSize = "normal";
export const TEXT_SIZE_KEY = "bookhive.text-size";

export function isTextSize(value: unknown): value is TextSize {
  return TEXT_SIZES.includes(value as TextSize);
}

/** Puts the size on <html>; "normal" removes the attribute so the plain rule applies. */
export function applyTextSize(size: TextSize) {
  const html = document.documentElement;
  if (size === DEFAULT_TEXT_SIZE) html.removeAttribute("data-text-size");
  else html.setAttribute("data-text-size", size);
}

export function readTextSize(): TextSize {
  try {
    const stored = window.localStorage.getItem(TEXT_SIZE_KEY);
    return isTextSize(stored) ? stored : DEFAULT_TEXT_SIZE;
  } catch {
    // Private windows can refuse storage; the default size is fine
    return DEFAULT_TEXT_SIZE;
  }
}

export function storeTextSize(size: TextSize) {
  try {
    if (size === DEFAULT_TEXT_SIZE) window.localStorage.removeItem(TEXT_SIZE_KEY);
    else window.localStorage.setItem(TEXT_SIZE_KEY, size);
  } catch {
    // Not remembered, but the page still changes size for this visit
  }
}

/**
 * Runs before the page is painted, so a reader who chose bigger text never sees it jump from the
 * default size. Written as one small string because it goes into an inline <script>.
 */
export const TEXT_SIZE_SCRIPT = `try{var s=localStorage.getItem(${JSON.stringify(TEXT_SIZE_KEY)});if(s&&s!=="normal")document.documentElement.setAttribute("data-text-size",s)}catch(e){}`;
