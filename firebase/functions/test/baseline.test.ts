import { describe, expect, it } from "vitest";
import type { GoogleGenAI } from "@google/genai";
import {
  MAX_AMOUNT,
  MAX_PEOPLE,
  MAX_SURVEY_ITEMS,
  SurveyQuestion,
  baselineCacheKey,
  baselineMessage,
  estimateWithGemini,
  parseAmounts,
  validateBaselineRequest,
} from "../src/baseline";

const milk: SurveyQuestion = { name: "חלב", unit: "liter", period: "week" };
const rice: SurveyQuestion = { name: "אורז", unit: "kilogram", period: "month" };

describe("first-list estimate: requests", () => {
  it("accepts ages and questions, ages as whole years", () => {
    expect(validateBaselineRequest({ householdId: "h1", ages: [40, 38.7, 0.5], items: [milk, { ...rice, name: "  אורז " }, milk] })).toEqual({
      householdId: "h1",
      ages: [40, 38, 0],
      items: [milk, rice],
    });
  });

  it("refuses what it can't use", () => {
    const ok = { householdId: "h1", ages: [30], items: [milk] };
    expect(typeof validateBaselineRequest({ ...ok, householdId: "a/b" })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, ages: [] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, ages: [-1] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, ages: ["30"] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, ages: Array(MAX_PEOPLE + 1).fill(30) })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, items: [] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, items: [{ ...milk, unit: "spoon" }] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, items: [{ ...milk, period: "day" }] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, items: [{ ...milk, name: "א".repeat(41) }] })).toBe("string");
    expect(typeof validateBaselineRequest({ ...ok, items: Array.from({ length: MAX_SURVEY_ITEMS + 1 }, (_, i) => ({ ...milk, name: `p${i}` })) })).toBe("string");
    expect(typeof validateBaselineRequest(null)).toBe("string");
  });

  it("only ages go to the model, and the products are fenced as data", () => {
    const text = baselineMessage([40, 9, 0], [milk, rice]);
    expect(text).toContain("Ages of the people at home: 40, 9, under 1.");
    expect(text).toContain("<products>\n- חלב | liter per week\n- אורז | kilogram per month\n</products>");
  });
});

describe("first-list estimate: cache", () => {
  it("the order of the people does not make a new question; the makeup and the questions do", () => {
    expect(baselineCacheKey([40, 9], [milk])).toBe(baselineCacheKey([9, 40], [milk]));
    expect(baselineCacheKey([40, 9], [milk])).not.toBe(baselineCacheKey([40, 10], [milk]));
    expect(baselineCacheKey([40], [milk])).not.toBe(baselineCacheKey([40], [milk, rice]));
  });
});

describe("first-list estimate: answers", () => {
  it("keeps only the products asked, once, with sane amounts", () => {
    const json = JSON.stringify({
      amounts: [
        { name: "חלב", amount: 3.456 },
        { name: "חלב", amount: 9 },
        { name: "אורז", amount: MAX_AMOUNT + 1 },
        { name: "לא נשאל", amount: 2 },
        { name: "אורז", amount: -1 },
        { name: 5, amount: 1 },
      ],
    });
    expect(parseAmounts(json, [milk, rice])).toEqual({ "חלב": 3.46 });
  });

  it("zero is an answer", () => {
    expect(parseAmounts(JSON.stringify({ amounts: [{ name: "אורז", amount: 0 }] }), [rice])).toEqual({ "אורז": 0 });
  });

  it("anything that is not the expected JSON reads as nothing", () => {
    expect(parseAmounts(undefined, [milk])).toEqual({});
    expect(parseAmounts("not json", [milk])).toEqual({});
    expect(parseAmounts('{"items": []}', [milk])).toEqual({});
  });

  const fakeAi = (text: string | undefined, seen: unknown[] = []) =>
    ({ models: { generateContent: async (req: unknown) => { seen.push(req); return { text }; } } }) as unknown as GoogleGenAI;

  it("asks for JSON in the schema", async () => {
    const seen: unknown[] = [];
    const json = JSON.stringify({ amounts: [{ name: "חלב", amount: 4 }] });
    expect(await estimateWithGemini(fakeAi(json, seen), "gemini-x", [40, 38], [milk])).toEqual({ "חלב": 4 });
    const req = seen[0] as { model: string; contents: string; config: { responseMimeType: string; responseJsonSchema: unknown; temperature: number } };
    expect(req.model).toBe("gemini-x");
    expect(req.contents).toContain("40, 38");
    expect(req.config.responseMimeType).toBe("application/json");
    expect(req.config.responseJsonSchema).toBeTruthy();
    expect(req.config.temperature).toBe(0);
  });

  it("an empty answer reads as nothing, not as an error", async () => {
    expect(await estimateWithGemini(fakeAi(undefined), "gemini-x", [30], [milk])).toEqual({});
  });
});
