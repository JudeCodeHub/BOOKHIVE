"use client";

import { useSyncExternalStore } from "react";

/**
 * Desk sounds (checklist step 241), so staff hear the result without looking up from the books.
 * Made with the Web Audio API, so there are no files to load:
 *
 * - success: a short rising "ding" (lent, returned to shelf, member OK)
 * - hold: three rising notes, louder (this book goes to the hold shelf: don't shelve it!)
 * - warning: two flat beeps (needs attention, e.g. amber alert or already in the list)
 * - error: a low buzz (refused, unknown barcode)
 */
export type DeskSound = "success" | "hold" | "warning" | "error";

const STORAGE_KEY = "bookhive.desk.muted";

let audio: AudioContext | null = null;
let muted = readMuted();
const listeners = new Set<() => void>();

function readMuted(): boolean {
  try {
    return typeof window !== "undefined" && window.localStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

/** One note. `at` is seconds from now. */
function tone(
  context: AudioContext,
  frequency: number,
  at: number,
  duration: number,
  type: OscillatorType = "sine",
  volume = 0.15,
) {
  const start = context.currentTime + at;
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  oscillator.type = type;
  oscillator.frequency.value = frequency;
  // Fade in and out quickly: no clicks
  gain.gain.setValueAtTime(0, start);
  gain.gain.linearRampToValueAtTime(volume, start + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
  oscillator.connect(gain).connect(context.destination);
  oscillator.start(start);
  oscillator.stop(start + duration + 0.02);
}

export function playSound(sound: DeskSound) {
  if (muted || typeof window === "undefined" || !("AudioContext" in window)) return;
  try {
    // Browsers allow sound after a key press or click; a scanner's keystrokes count
    audio ??= new AudioContext();
    if (audio.state === "suspended") void audio.resume();
    switch (sound) {
      case "success":
        tone(audio, 880, 0, 0.09);
        tone(audio, 1320, 0.08, 0.12);
        break;
      case "hold":
        tone(audio, 660, 0, 0.1, "triangle", 0.25);
        tone(audio, 880, 0.1, 0.1, "triangle", 0.25);
        tone(audio, 1175, 0.2, 0.2, "triangle", 0.25);
        break;
      case "warning":
        tone(audio, 520, 0, 0.12, "triangle");
        tone(audio, 520, 0.18, 0.12, "triangle");
        break;
      case "error":
        tone(audio, 196, 0, 0.35, "square", 0.08);
        break;
    }
  } catch {
    // No sound is never a reason to break the desk
  }
}

export function setMuted(value: boolean) {
  muted = value;
  try {
    window.localStorage.setItem(STORAGE_KEY, value ? "1" : "0");
  } catch {
    // Private mode: muted for this page only
  }
  listeners.forEach((listener) => listener());
}

/** Whether desk sounds are off on this computer (remembered between visits). */
export function useMuted() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => muted,
    () => false,
  );
}

/** Problems -> sound: none = success, only overridable ones = warning, anything else = error. */
export function soundForProblems(problems: { overridable: boolean }[]): DeskSound {
  if (problems.length === 0) return "success";
  return problems.every((problem) => problem.overridable) ? "warning" : "error";
}
