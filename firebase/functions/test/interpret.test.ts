import { describe, expect, it } from "vitest";
import type { GoogleGenAI } from "@google/genai";
import { MAX_ITEMS, MAX_TEXT, cacheKey, cleanItems, dayKey, normalizeText, parseReading, readWithGemini, userMessage, validateRequest } from "../src/interpret";

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

  it("the model's no-null answer becomes items with nulls", () => {
    const json = JSON.stringify({
      items: [
        { name: "עגבניות", variant: "", amount: 0, unit: "none", category: "PRODUCE" },
        { name: "מים", variant: " ", amount: 6, unit: "liter", category: "DRINKS" },
        { name: "משהו", variant: "", amount: 0, unit: "spoon", category: "NOT_A_SECTION" },
        { variant: "חסר שם" },
      ],
    });
    expect(parseReading(json)).toEqual([
      item("עגבניות"),
      item("מים", { amount: 6, unit: "liter", category: "DRINKS" }),
      item("משהו", { category: "OTHER" }),
    ]);
  });

  it("anything that is not the expected JSON reads as nothing", () => {
    expect(parseReading(undefined)).toEqual([]);
    expect(parseReading("not json")).toEqual([]);
    expect(parseReading('{"things": []}')).toEqual([]);
  });

  const fakeAi = (text: string | undefined, seen: unknown[] = []) =>
    ({ models: { generateContent: async (req: unknown) => { seen.push(req); return { text }; } } }) as unknown as GoogleGenAI;

  it("asks for JSON in the schema, with the text fenced as data", async () => {
    const seen: unknown[] = [];
    const json = JSON.stringify({ items: [{ name: "פלפל", variant: "אדום", amount: 0, unit: "none", category: "PRODUCE" }, { name: "פלפל", variant: "אדום", amount: 0, unit: "none", category: "PRODUCE" }] });
    expect(await readWithGemini(fakeAi(json, seen), "gemini-x", "פלפלים בכל מיני צבעים", null)).toEqual([item("פלפל", { variant: "אדום" })]);
    const req = seen[0] as { model: string; contents: string; config: { responseMimeType: string; responseJsonSchema: unknown } };
    expect(req.model).toBe("gemini-x");
    expect(req.contents).toContain("<typed>פלפלים בכל מיני צבעים</typed>");
    expect(req.config.responseMimeType).toBe("application/json");
    expect(req.config.responseJsonSchema).toBeTruthy();
  });

  it("an empty answer (a declined request) reads as nothing, not as an error", async () => {
    expect(await readWithGemini(fakeAi(undefined), "gemini-x", "x", null)).toEqual([]);
  });
});
