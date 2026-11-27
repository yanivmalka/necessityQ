import { describe, expect, it } from "vitest";
import Anthropic from "@anthropic-ai/sdk";
import { MAX_ITEMS, MAX_TEXT, cacheKey, cleanItems, dayKey, normalizeText, readWithClaude, userMessage, validateRequest } from "../src/interpret";

describe("quick-add reading: requests", () => {
  it("accepts a member's text and a sane household size", () => {
    expect(validateRequest({ householdId: "h1", text: "  משהו  לכריכים ", people: 4 })).toEqual({ householdId: "h1", text: "משהו לכריכים", people: 4 });
    expect(validateRequest({ householdId: "h1", text: "חלב", people: 99 })).toEqual({ householdId: "h1", text: "חלב", people: null });
  });

  it("refuses what it can't read", () => {
    expect(typeof validateRequest({ text: "חלב" })).toBe("string");
    expect(typeof validateRequest({ householdId: "a/b", text: "חלב" })).toBe("string");
    expect(typeof validateRequest({ householdId: "h1", text: "   " })).toBe("string");
    expect(typeof validateRequest({ householdId: "h1", text: "א".repeat(MAX_TEXT + 1) })).toBe("string");
    expect(typeof validateRequest(null)).toBe("string");
  });

  it("the typed text is fenced as data", () => {
    expect(userMessage("לשקשוקה", 3)).toBe("The household is 3 people.\nWhat they typed:\n<typed>לשקשוקה</typed>");
    expect(userMessage("לשקשוקה", null)).toBe("What they typed:\n<typed>לשקשוקה</typed>");
  });
});

describe("quick-add reading: cache", () => {
  it("spacing, quotes, niqqud and case do not make a new question", () => {
    expect(normalizeText("  קוטג׳   5% ")).toBe(normalizeText("קוטג' 5%"));
    expect(cacheKey("מה שצריך  לשקשוקה", 4)).toBe(cacheKey("מה שצריך לשקשוקה", 4));
    expect(normalizeText("חָלָב")).toBe("חלב");
  });

  it("household size is part of the question (it changes amounts)", () => {
    expect(cacheKey("מים לשבוע", 2)).not.toBe(cacheKey("מים לשבוע", 6));
    expect(cacheKey("מים לשבוע", null)).toBe(cacheKey("מים לשבוע", 0));
  });

  it("day key", () => {
    expect(dayKey(new Date("2026-11-27T22:10:00Z"))).toBe("20261127");
  });
});

describe("quick-add reading: answers", () => {
  const item = (name: string, extra: Partial<Parameters<typeof cleanItems>[0][number]> = {}) => ({
    name, variant: null, amount: null, unit: null, category: "PRODUCE" as const, ...extra,
  });

  it("trims, drops empty names and duplicates, and keeps the order", () => {
    const out = cleanItems([item(" עגבניות "), item(""), item("ביצים", { category: "DAIRY" }), item("עגבניות")]);
    expect(out.map((i) => i.name)).toEqual(["עגבניות", "ביצים"]);
  });

  it("an impossible amount is dropped with its unit", () => {
    expect(cleanItems([item("מים", { amount: -2, unit: "liter" })])[0]).toMatchObject({ amount: null, unit: null });
    expect(cleanItems([item("מים", { amount: 9, unit: "liter" })])[0]).toMatchObject({ amount: 9, unit: "liter" });
  });

  it("at most a dozen items", () => {
    expect(cleanItems(Array.from({ length: 30 }, (_, i) => item(`פריט ${i}`)))).toHaveLength(MAX_ITEMS);
  });

  it("a declined request reads as nothing, not as an error", async () => {
    const fake = { beta: { messages: { parse: async () => ({ stop_reason: "refusal", parsed_output: null }) } } } as unknown as Anthropic;
    expect(await readWithClaude(fake, "x", null)).toEqual([]);
  });

  it("the parsed reading is cleaned", async () => {
    const fake = {
      beta: { messages: { parse: async () => ({ stop_reason: "end_turn", parsed_output: { items: [item("פלפל", { variant: "אדום" }), item("פלפל", { variant: "אדום" })] } }) } },
    } as unknown as Anthropic;
    expect(await readWithClaude(fake, "פלפלים בכל מיני צבעים", null)).toEqual([item("פלפל", { variant: "אדום" })]);
  });
});
