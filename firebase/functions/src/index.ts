import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { gunzipSync, gzipSync } from "fflate";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { GoogleGenAI } from "@google/genai";
import { GoogleAuth } from "google-auth-library";
import { guardConfig, parseNotification, shouldCut } from "./budget";
import { AlertTarget, ChatMessage, alertTargets, alertText, isDeadToken } from "./chat";
import * as logger from "firebase-functions/logger";
import {
  BranchStatus,
  OFFERS_MINUTES,
  PriceConfig,
  PriceStore,
  PromoStatus,
  PromoStore,
  offersTick,
  refreshBranchPrices,
  refreshBranches,
  tick,
  withDefaults,
} from "./ingest";
import { Branch, Snapshot } from "./model";
import { downloadTzameret, snapshotStats } from "./nutrition";
import { DAILY_LIMIT, DEFAULT_MODEL, cacheKey, dayKey, readWithGemini, validateRequest } from "./interpret";
import { BASELINE_DAILY_LIMIT, baselineCacheKey, estimateWithGemini, validateBaselineRequest } from "./baseline";

// Israeli region: several retailer portals refuse requests from abroad.
setGlobalOptions({ region: "me-west1", maxInstances: 2 });

initializeApp();
const db = getFirestore();

const INDEX = db.doc("prices/branchIndex");
const statusDoc = (id: string) => db.doc(`priceBranches/${id}`);
const snapshotPath = (id: string) => `prices/${id}.json.gz`;

async function loadConfig(): Promise<PriceConfig & { admins: string[] }> {
  const raw = (await db.doc("config/prices").get()).data() as (Partial<PriceConfig> & { admins?: string[] }) | undefined;
  return { ...withDefaults(raw), admins: Array.isArray(raw?.admins) ? raw!.admins : [] };
}

const firebaseStore: PriceStore = {
  async readIndex() {
    const d = (await INDEX.get()).data();
    return { updatedAt: (d?.updatedAt as string) ?? null, branches: (d?.branches as Branch[]) ?? [] };
  },
  async writeIndex(branches, meta) {
    await INDEX.set({ ...meta, branches });
  },
  async readSnapshot(id) {
    const file = getStorage().bucket().file(snapshotPath(id));
    const [exists] = await file.exists();
    if (!exists) return null;
    const [buf] = await file.download();
    return JSON.parse(new TextDecoder().decode(gunzipSync(new Uint8Array(buf)))) as Snapshot;
  },
  async writeSnapshot(snapshot) {
    const body = gzipSync(new TextEncoder().encode(JSON.stringify(snapshot)), { level: 9 });
    await getStorage()
      .bucket()
      .file(snapshotPath(snapshot.branchId))
      .save(Buffer.from(body), {
        contentType: "application/gzip",
        metadata: { cacheControl: "private, max-age=600", metadata: { updatedAt: snapshot.updatedAt } },
      });
  },
  async readStatus(id) {
    return ((await statusDoc(id).get()).data() as BranchStatus | undefined) ?? null;
  },
  async writeStatus(id, status) {
    await statusDoc(id).set(status);
  },
  async trackedBranchIds() {
    const snap = await db.collectionGroup("settings").where("kind", "==", "priceBranches").get();
    const ids = new Set<string>();
    snap.forEach((d) => ((d.get("branchIds") as string[] | undefined) ?? []).forEach((id) => ids.add(id)));
    return [...ids];
  },
  geoCache: {
    async get(key) {
      const d = await db.doc(`geocodeCache/${key}`).get();
      if (!d.exists) return undefined;
      const v = d.data()!;
      return v.lat === null ? null : { lat: v.lat, lng: v.lng, approx: !!v.approx };
    },
    async put(key, value) {
      await db.doc(`geocodeCache/${key}`).set(value ?? { lat: null, lng: null, approx: true });
    },
  },
};

const deps = { store: firebaseStore, log: (m: string) => logger.info(m) };

const promoStatusDoc = (id: string) => db.doc(`promoBranches/${id}`);

/** Promotions per branch: Storage promos/{branchId}.json.gz, freshness in promoBranches/{branchId}. */
const promoStore: PromoStore = {
  async readPromoStatus(id) {
    return ((await promoStatusDoc(id).get()).data() as PromoStatus | undefined) ?? null;
  },
  async writePromoStatus(id, status) {
    await promoStatusDoc(id).set(status);
  },
  async writePromos(snapshot) {
    const body = gzipSync(new TextEncoder().encode(JSON.stringify(snapshot)), { level: 9 });
    await getStorage()
      .bucket()
      .file(`promos/${snapshot.branchId}.json.gz`)
      .save(Buffer.from(body), {
        contentType: "application/gzip",
        metadata: { cacheControl: "private, max-age=300", metadata: { updatedAt: snapshot.updatedAt } },
      });
  },
};

/**
 * Every 30 minutes: for each tracked branch, a price file newer than the snapshot (stock and price changes) is merged
 * in, and the promotions are re-read when the chain has published a newer promotions file.
 */
export const promosTick = onSchedule(
  { schedule: `every ${OFFERS_MINUTES} minutes`, timeZone: "Asia/Jerusalem", timeoutSeconds: 1500, memory: "1GiB" },
  async () => {
    const config = await loadConfig();
    const report = await offersTick(config, deps, promoStore, 20 * 60_000);
    logger.info("offers tick", { report });
  },
);

/**
 * The app opens (or comes back after half an hour): promotions and stock of the household's branches are checked
 * now, for those not checked in the last 30 minutes.
 */
export const refreshOffers = onCall({ timeoutSeconds: 540, memory: "1GiB" }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const ids = ((req.data as { branchIds?: unknown })?.branchIds ?? []) as unknown[];
  const branchIds = ids.map(String).filter((id) => id && !id.includes("/")).slice(0, 10);
  if (!branchIds.length) return { report: [] };
  const config = await loadConfig();
  return { report: await offersTick(config, deps, promoStore, 8 * 60_000, branchIds) };
});

/**
 * Hourly tick. Whether anything is downloaded is decided by `config/prices`
 * (refreshHours, default 24 = once a day per tracked branch) so the admin can change the
 * resolution without a redeploy.
 */
export const pricesTick = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Asia/Jerusalem", timeoutSeconds: 1800, memory: "1GiB" },
  async () => {
    const config = await loadConfig();
    const report = await tick(config, deps, 25 * 60_000);
    logger.info("prices tick", { report });
  },
);

/** The app asks for fresh prices of a branch (e.g. on shopping day or right after selecting it). */
export const refreshBranch = onCall({ timeoutSeconds: 540, memory: "1GiB" }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const branchId = String((req.data as { branchId?: string })?.branchId ?? "");
  const config = await loadConfig();
  const { branches } = await firebaseStore.readIndex();
  const branch = branches.find((b) => b.id === branchId);
  if (!branch) throw new HttpsError("not-found", "unknown branch");
  const status = await firebaseStore.readStatus(branchId);
  if (status?.updatedAt && Date.now() - Date.parse(status.updatedAt) < config.manualRefreshMinutes * 60_000) {
    return { refreshed: false, status };
  }
  return { refreshed: true, status: await refreshBranchPrices(branch, config, deps) };
});

/** Admin: re-read the branch lists now (after changing cities or chains in config/prices). */
export const refreshBranchList = onCall({ timeoutSeconds: 1800, memory: "1GiB" }, async (req) => {
  const config = await loadConfig();
  if (!req.auth || !config.admins.includes(req.auth.uid)) throw new HttpsError("permission-denied", "admins only");
  return refreshBranches(config, deps);
});

/**
 * Budget kill switch: the billing budget publishes to the Pub/Sub topic `billing-budget`.
 * When the month's cost reaches the budget (config/billing: { enabled, cutAtRatio }), the project's
 * billing account is unlinked so nothing more can be charged. Re-link it by hand in the console.
 * The function's service account needs the role "Project Billing Manager" on the project.
 */
export const budgetGuard = onMessagePublished({ topic: "billing-budget", memory: "256MiB" }, async (event) => {
  let payload: unknown;
  try {
    payload = event.data.message.json;
  } catch {
    payload = undefined;
  }
  const n = parseNotification(payload);
  if (!n) {
    logger.warn("budget message without cost/budget", { data: event.data.message.data });
    return;
  }
  const config = guardConfig((await db.doc("config/billing").get()).data());
  logger.info("budget notification", { ...n, config });
  if (!shouldCut(n, config)) return;

  const projectId = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT ?? "";
  const client = await new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-billing"] }).getClient();
  const url = `https://cloudbilling.googleapis.com/v1/projects/${projectId}/billingInfo`;
  const info = (await client.request<{ billingEnabled?: boolean }>({ url })).data;
  if (!info.billingEnabled) {
    logger.info("billing already disabled");
    return;
  }
  await client.request({ url, method: "PUT", data: { billingAccountName: "" } });
  await db.doc("config/billing").set({ lastCutAt: new Date().toISOString(), lastCutCost: n.costAmount, lastCutBudget: n.budgetAmount }, { merge: true });
  logger.error(`billing disabled: cost ${n.costAmount} ${n.currencyCode ?? ""} reached budget ${n.budgetAmount}`);
});

/** Tzameret (national nutrition database): one gzipped snapshot in Storage, refreshed monthly. */
async function refreshNutritionSnapshot() {
  const snapshot = await downloadTzameret(undefined, (m) => logger.info(m));
  const body = gzipSync(new TextEncoder().encode(JSON.stringify(snapshot)), { level: 9 });
  await getStorage()
    .bucket()
    .file("nutrition/tzameret.json.gz")
    .save(Buffer.from(body), { contentType: "application/gzip", metadata: { cacheControl: "private, max-age=86400" } });
  const stats = { updatedAt: snapshot.updatedAt, bytes: body.byteLength, ...snapshotStats(snapshot) };
  await db.doc("nutrition/meta").set(stats);
  return stats;
}

export const nutritionMonthly = onSchedule(
  { schedule: "17 4 3 * *", timeZone: "Asia/Jerusalem", timeoutSeconds: 540, memory: "512MiB" },
  async () => {
    logger.info("tzameret refreshed", await refreshNutritionSnapshot());
  },
);

/** Admin: download Tzameret now (first time, or after the Ministry publishes an update). */
export const refreshNutrition = onCall({ timeoutSeconds: 540, memory: "512MiB" }, async (req) => {
  const config = await loadConfig();
  if (!req.auth || !config.admins.includes(req.auth.uid)) throw new HttpsError("permission-denied", "admins only");
  return refreshNutritionSnapshot();
});

/**
 * צ'אט פוד: push one message to the members it is aimed at — those tagged with "@" and the author of
 * the message it quotes — and to nobody else. Called by the writer's app right after sending; while
 * the message has not reached the server yet (sent offline) it answers not-found and the app retries.
 * The pushes are data messages with high priority, so the app can sound the alert even on silent.
 */
export const chatPush = onCall({ memory: "256MiB", timeoutSeconds: 30, invoker: "public" }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const uid = req.auth.uid;
  const { householdId, messageId } = (req.data ?? {}) as { householdId?: string; messageId?: string };
  if (!householdId || !messageId || householdId.includes("/") || messageId.includes("/")) {
    throw new HttpsError("invalid-argument", "householdId and messageId are required");
  }
  const household = db.doc(`households/${householdId}`);
  const members = ((await household.get()).get("members") as string[] | undefined) ?? [];
  if (!members.includes(uid)) throw new HttpsError("permission-denied", "not a member");

  const ref = household.collection("messages").doc(messageId);
  // claim the targets in a transaction, so a retry that overlaps never pushes twice
  const claimed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "message not synced yet");
    const m = snap.data() as ChatMessage & { replyTo?: string };
    if (m.authorId !== uid) throw new HttpsError("permission-denied", "only the writer");
    let quotedAuthor: string | null = null;
    if (m.replyTo) {
      const quoted = await tx.get(household.collection("messages").doc(m.replyTo));
      quotedAuthor = (quoted.get("authorId") as string | undefined) ?? null;
    }
    const targets = alertTargets(m, quotedAuthor, members);
    if (targets.length) tx.update(ref, { pushedTo: FieldValue.arrayUnion(...targets.map((t) => t.uid)) });
    return { m, targets };
  });
  if (!claimed.targets.length) return { sent: 0 };
  return { sent: await sendAlerts(householdId, messageId, claimed.m, claimed.targets) };
});

async function sendAlerts(householdId: string, messageId: string, m: ChatMessage, targets: AlertTarget[]): Promise<number> {
  const jobs: { uid: string; token: string; reason: AlertTarget["reason"] }[] = [];
  for (const t of targets) {
    const tokens = ((await db.doc(`users/${t.uid}`).get()).get("pushTokens") as string[] | undefined) ?? [];
    for (const token of tokens) jobs.push({ uid: t.uid, token, reason: t.reason });
  }
  if (!jobs.length) return 0;
  const result = await getMessaging().sendEach(
    jobs.map((j) => {
      const { title, body } = alertText(m, j.reason);
      return {
        token: j.token,
        data: {
          type: "chat",
          to: j.uid,
          householdId,
          messageId,
          itemId: m.itemId,
          itemName: m.itemName ?? "",
          reason: j.reason,
          title,
          body,
        },
        android: { priority: "high" as const, ttl: 6 * 3600 * 1000 },
      };
    }),
  );
  // tokens of uninstalled apps / signed-out phones are removed
  const dead = new Map<string, string[]>();
  result.responses.forEach((r, i) => {
    if (!r.success && isDeadToken(r.error?.code)) dead.set(jobs[i].uid, [...(dead.get(jobs[i].uid) ?? []), jobs[i].token]);
  });
  await Promise.all([...dead.entries()].map(([u, tokens]) => db.doc(`users/${u}`).update({ pushTokens: FieldValue.arrayRemove(...tokens) })));
  logger.info("chat push", { householdId, messageId, targets: targets.length, sent: result.successCount });
  return result.successCount;
}

/**
 * The Gemini API key (Google AI Studio) and model, read from the environment (functions/.env, which the deploy
 * workflow writes from the GitHub secret GEMINI_API_KEY). Plain environment, not deploy parameters: a parameter
 * without a value stops a non-interactive deploy of every function. No key = not set up: the app adds text as typed.
 */
const geminiKey = (): string => process.env.GEMINI_API_KEY ?? "";
const geminiModel = (): string => process.env.GEMINI_MODEL || DEFAULT_MODEL;

/**
 * הבנת כוונה בהוספה לרשימה: reads free text the app did not understand ("משהו לכריכים לילדים") as grocery
 * items (see interpret.ts). Members of the household only; cached by text; a daily limit per household.
 */
export const interpretItems = onCall(
  { memory: "256MiB", timeoutSeconds: 60, invoker: "public" },
  async (req) => {
    if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
    if (!geminiKey()) throw new HttpsError("failed-precondition", "GEMINI_API_KEY is not set");
    const request = validateRequest(req.data);
    if (typeof request === "string") throw new HttpsError("invalid-argument", request);
    const { text, people, householdId } = request;
    const household = db.doc(`households/${householdId}`);
    const members = ((await household.get()).get("members") as string[] | undefined) ?? [];
    if (!members.includes(req.auth.uid)) throw new HttpsError("permission-denied", "not a member");

    const cached = db.doc(`interpretCache/${cacheKey(text, people)}`);
    const hit = await cached.get();
    if (hit.exists) return { items: hit.get("items") ?? [], cached: true };

    // the limit counts only questions that reach the model
    const usage = household.collection("usage").doc(`interpret-${dayKey(new Date())}`);
    const allowed = await db.runTransaction(async (tx) => {
      const count = ((await tx.get(usage)).get("count") as number | undefined) ?? 0;
      if (count >= DAILY_LIMIT) return false;
      tx.set(usage, { count: count + 1 }, { merge: true });
      return true;
    });
    if (!allowed) throw new HttpsError("resource-exhausted", "daily limit reached");

    const ai = new GoogleGenAI({ apiKey: geminiKey() });
    const model = geminiModel();
    let items;
    try {
      items = await readWithGemini(ai, model, text, people);
    } catch (e) {
      logger.error("interpret failed", { householdId, error: e instanceof Error ? e.message : String(e) });
      throw new HttpsError("unavailable", "could not read the text right now");
    }
    // no household or user in the cache: only the words typed and the reading
    await cached.set({ text, people: people ?? null, items, model, at: FieldValue.serverTimestamp() });
    logger.info("interpret", { householdId, items: items.length });
    return { items, cached: false };
  },
);

/**
 * רשימה ראשונה למשפחה: the amounts the first-list survey proposes, estimated from the ages of the people at home
 * (see baseline.ts). Members of the household only; cached by makeup and questions; a daily limit per household.
 */
export const estimateBaseline = onCall(
  { memory: "256MiB", timeoutSeconds: 60, invoker: "public" },
  async (req) => {
    if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
    if (!geminiKey()) throw new HttpsError("failed-precondition", "GEMINI_API_KEY is not set");
    const request = validateBaselineRequest(req.data);
    if (typeof request === "string") throw new HttpsError("invalid-argument", request);
    const { ages, items, householdId } = request;
    const household = db.doc(`households/${householdId}`);
    const members = ((await household.get()).get("members") as string[] | undefined) ?? [];
    if (!members.includes(req.auth.uid)) throw new HttpsError("permission-denied", "not a member");

    const cached = db.doc(`baselineCache/${baselineCacheKey(ages, items)}`);
    const hit = await cached.get();
    if (hit.exists) return { amounts: hit.get("amounts") ?? {}, cached: true };

    // the limit counts only questions that reach the model
    const usage = household.collection("usage").doc(`baseline-${dayKey(new Date())}`);
    const allowed = await db.runTransaction(async (tx) => {
      const count = ((await tx.get(usage)).get("count") as number | undefined) ?? 0;
      if (count >= BASELINE_DAILY_LIMIT) return false;
      tx.set(usage, { count: count + 1 }, { merge: true });
      return true;
    });
    if (!allowed) throw new HttpsError("resource-exhausted", "daily limit reached");

    const ai = new GoogleGenAI({ apiKey: geminiKey() });
    const model = geminiModel();
    let amounts;
    try {
      amounts = await estimateWithGemini(ai, model, ages, items);
    } catch (e) {
      logger.error("baseline failed", { householdId, error: e instanceof Error ? e.message : String(e) });
      throw new HttpsError("unavailable", "could not estimate right now");
    }
    // no household or user in the cache: only the ages and the estimate
    if (Object.keys(amounts).length > 0) {
      await cached.set({ ages: [...ages].sort((a, b) => a - b), amounts, model, at: FieldValue.serverTimestamp() });
    }
    logger.info("baseline", { householdId, items: Object.keys(amounts).length });
    return { amounts, cached: false };
  },
);
