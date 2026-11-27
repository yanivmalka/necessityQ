import { describe, expect, it } from "vitest";
import { isBusy, withBusyRetry } from "../src/gemini";

const busy = new Error('{"error":{"code":503,"message":"This model is currently experiencing high demand.","status":"UNAVAILABLE"}}');
const notFound = new Error('{"error":{"code":404,"message":"models/x is not found","status":"NOT_FOUND"}}');
const noSleep = async () => {};

describe("Gemini busy retry", () => {
  it("tells busy from a real failure", () => {
    expect(isBusy(busy)).toBe(true);
    expect(isBusy(Object.assign(new Error("x"), { status: 429 }))).toBe(true);
    expect(isBusy(notFound)).toBe(false);
  });

  it("tries again while busy, then answers", async () => {
    const asked: string[] = [];
    let n = 0;
    const r = await withBusyRetry("main", async (m) => { asked.push(m); if (n++ < 2) throw busy; return "ok"; }, [1, 1], "lite", noSleep);
    expect(r).toEqual({ result: "ok", model: "main" });
    expect(asked).toEqual(["main", "main", "main"]);
  });

  it("falls back to the second model when the first stays busy", async () => {
    const asked: string[] = [];
    const r = await withBusyRetry("main", async (m) => { asked.push(m); if (m === "main") throw busy; return "ok"; }, [1, 1], "lite", noSleep);
    expect(r).toEqual({ result: "ok", model: "lite" });
    expect(asked).toEqual(["main", "main", "main", "lite"]);
  });

  it("does not retry a real failure", async () => {
    let n = 0;
    await expect(withBusyRetry("main", async () => { n++; throw notFound; }, [1, 1], "lite", noSleep)).rejects.toBe(notFound);
    expect(n).toBe(1);
  });
});
