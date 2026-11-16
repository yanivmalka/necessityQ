import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { gunzipSync, gzipSync } from "fflate";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { GoogleAuth } from "google-auth-library";
import { guardConfig, parseNotification, shouldCut } from "./budget";
import * as logger from "firebase-functions/logger";
import { BranchStatus, PriceConfig, PriceStore, refreshBranchPrices, refreshBranches, tick, withDefaults } from "./ingest";
import { Branch, Snapshot } from "./model";

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
