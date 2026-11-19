import { describe, expect, it } from "vitest";
import { alertTargets, alertText, isDeadToken, preview } from "../src/chat";

const members = ["me", "keren", "david", "dana"];

describe("chat push targets", () => {
  it("alerts only tagged members and the quoted author", () => {
    const m = { itemId: "milk", authorId: "me", mentions: ["david"] };
    expect(alertTargets(m, "keren", members)).toEqual([
      { uid: "david", reason: "tag" },
      { uid: "keren", reason: "quote" },
    ]);
    expect(alertTargets({ itemId: "milk", authorId: "me" }, null, members)).toEqual([]);
  });

  it("never the writer, someone who left, or someone already alerted", () => {
    const m = { itemId: "milk", authorId: "me", mentions: ["me", "gone", "david", "dana"], pushedTo: ["dana"] };
    expect(alertTargets(m, "me", members)).toEqual([{ uid: "david", reason: "tag" }]);
  });

  it("a tagged quoted author is alerted once, as tagged", () => {
    expect(alertTargets({ itemId: "x", authorId: "me", mentions: ["keren"] }, "keren", members)).toEqual([{ uid: "keren", reason: "tag" }]);
  });
});

describe("chat alert text", () => {
  it("says who and why, with the product", () => {
    const m = { itemId: "milk", itemName: "חלב 3%", authorId: "me", authorName: "יוחאי", text: "@קרן לקנות?" };
    expect(alertText(m, "tag")).toEqual({ title: "יוחאי תייג/ה אותך · חלב 3%", body: "@קרן לקנות?" });
    expect(alertText(m, "quote").title).toBe("יוחאי הגיב/ה להודעה שלך · חלב 3%");
  });

  it("previews pictures and recordings", () => {
    expect(preview({ itemId: "x", authorId: "a", kind: "IMAGE", text: "זה?" })).toBe("📷 תמונה: זה?");
    expect(preview({ itemId: "x", authorId: "a", kind: "AUDIO", durationMs: 65_000 })).toBe("🎤 הקלטה 1:05");
    expect(preview({ itemId: "x", authorId: "a", text: "א".repeat(300) }).length).toBe(180);
  });

  it("drops dead tokens only", () => {
    expect(isDeadToken("messaging/registration-token-not-registered")).toBe(true);
    expect(isDeadToken("messaging/internal-error")).toBe(false);
    expect(isDeadToken(undefined)).toBe(false);
  });
});
