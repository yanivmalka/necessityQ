/**
 * צ'אט פוד push: who is alerted about a message, and what the alert says.
 *
 * Not every member is alerted about every message. Only members tagged with "@name" (`mentions`) and
 * the author of the quoted message (`replyTo`) — a quote counts as a tag. Never the writer, never
 * someone who left the household, and never twice for the same message (`pushedTo`, so a tag added
 * by an edit alerts only the new member).
 */

export interface ChatMessage {
  itemId: string;
  itemName?: string;
  authorId: string;
  authorName?: string;
  text?: string;
  kind?: string;
  durationMs?: number;
  mentions?: string[];
  pushedTo?: string[];
}

export type AlertReason = "tag" | "quote";

export interface AlertTarget {
  uid: string;
  reason: AlertReason;
}

export function alertTargets(m: ChatMessage, quotedAuthor: string | null, members: string[]): AlertTarget[] {
  const inHousehold = new Set(members);
  const done = new Set(m.pushedTo ?? []);
  const out = new Map<string, AlertReason>();
  for (const uid of m.mentions ?? []) out.set(uid, "tag");
  if (quotedAuthor && !out.has(quotedAuthor)) out.set(quotedAuthor, "quote");
  return [...out.entries()]
    .filter(([uid]) => uid !== m.authorId && inHousehold.has(uid) && !done.has(uid))
    .map(([uid, reason]) => ({ uid, reason }));
}

function duration(ms: number): string {
  const sec = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
}

/** One line for the notification, as the app shows it in lists. */
export function preview(m: ChatMessage): string {
  const text = (m.text ?? "").trim();
  if (m.kind === "IMAGE") return "📷 תמונה" + (text ? `: ${text}` : "");
  if (m.kind === "AUDIO") return "🎤 הקלטה" + (m.durationMs ? ` ${duration(m.durationMs)}` : "");
  return text.length > 180 ? text.slice(0, 179) + "…" : text;
}

export function alertText(m: ChatMessage, reason: AlertReason): { title: string; body: string } {
  const who = m.authorName?.trim() || "מישהו";
  const action = reason === "tag" ? "תייג/ה אותך" : "הגיב/ה להודעה שלך";
  const item = m.itemName?.trim();
  return { title: `${who} ${action}` + (item ? ` · ${item}` : ""), body: preview(m) };
}

/** FCM error codes after which a token is dropped from the user's document. */
export function isDeadToken(code: string | undefined): boolean {
  return code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token" ||
    code === "messaging/invalid-argument";
}
