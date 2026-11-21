import { normalizeStoreId } from "./filenames";
import { extractRecords, num, pick } from "./xml";

/**
 * Promotions of one branch, from its PromoFull (or Promo) transparency file, as the app downloads them: all of them,
 * as published (compact keys keep the file small). The app leaves out what it must not offer.
 */
export interface PromoItem {
  id: string;
  /** description as published ("קוטג' 5% 2 ב-10") */
  d: string;
  /** barcodes it applies to (gift items left out) */
  c: string[];
  /** minimum quantity for the reward (1 when not set) */
  mq: number;
  /** discounted price for [mq] units (as published), null when the reward is a rate */
  dp: number | null;
  /** discount rate as a fraction (0.2 = 20%), null when not a rate */
  dr: number | null;
  /** start / end as ISO times (Israel time written as published, no zone), "" when not published */
  s: string;
  e: string;
  /** only for a customer club / a credit card (not every customer) */
  cl: boolean;
  /** needs a coupon */
  cp: boolean;
  /** depends on stock ("עד גמר המלאי"): not offered by the app */
  sl: boolean;
  /** minimum basket amount, null when none */
  mb: number | null;
  /** a discount on the whole basket rather than on items */
  t: boolean;
}

export interface PromoSnapshot {
  v: 1;
  branchId: string;
  updatedAt: string;
  sourceFile: string;
  sourceStamp: string;
  promos: PromoItem[];
}

const TRUE = new Set(["1", "true", "yes", "כן"]);
const STOCK = /(גמר|סיום)\s*(ה)?מלאי|עד\s*(ל)?גמר|while\s+stock|stock\s+lasts|כמות\s+מוגבלת|במלאי\s+מוגבל/i;

/** "2026-11-30" + "23:59" → "2026-11-30T23:59:00"; tolerates "2026-11-30 23:59", "30/11/2026". */
export function promoTime(date: string, hour: string, endOfDay: boolean): string {
  const d = date.trim();
  let day = "";
  let time = "";
  const iso = d.match(/^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/);
  const il = d.match(/^(\d{1,2})[/.](\d{1,2})[/.](\d{4})/);
  if (iso) {
    day = `${iso[1]}-${iso[2].padStart(2, "0")}-${iso[3].padStart(2, "0")}`;
    if (iso[4]) time = `${iso[4].padStart(2, "0")}:${iso[5]}:${iso[6] ?? "00"}`;
  } else if (il) {
    day = `${il[3]}-${il[2].padStart(2, "0")}-${il[1].padStart(2, "0")}`;
  } else {
    return "";
  }
  const h = hour.trim().match(/(\d{1,2}):(\d{2})(?::(\d{2}))?/);
  if (h) time = `${h[1].padStart(2, "0")}:${h[2]}:${h[3] ?? "00"}`;
  if (!time) time = endOfDay ? "23:59:59" : "00:00:00";
  return `${day}T${time}`;
}

/** A club id of "0" (or "0 - כלל הלקוחות") is every customer; anything else is a club or a card. */
function clubOnly(raw: string): boolean {
  const v = raw.trim();
  if (!v) return false;
  return !/^0(\D|$)/.test(v);
}

/** A rate as a fraction: 20 → 0.2; 2000 (hundredths of a percent, as some chains publish) → 0.2. */
function rate(raw: number | null): number | null {
  if (raw === null || raw <= 0) return null;
  const pct = raw > 100 ? raw / 100 : raw;
  return pct > 0 && pct < 100 ? pct / 100 : null;
}

/**
 * PromoFull / Promo file → promotions. Two dialects: `<Promotion>` with nested `PromotionItems/Item/ItemCode`
 * (most chains), and `<Sale>` rows, one per item and promotion id (grouped here).
 */
export function parsePromotions(xml: string): { storeId: string | null; promos: PromoItem[] } {
  const { header, records } = extractRecords(xml, ["promotion", "sale"]);
  const byId = new Map<string, PromoItem>();
  for (const r of records) {
    const id = pick(r, "promotionid", "promotion_id", "promoid");
    if (!id) continue;
    const codes = [
      ...(r.__items ? r.__items.split(",") : []),
      ...[pick(r, "itemcode")].filter((c) => c !== ""),
    ]
      .map((c) => c.trim().replace(/^0+(?=\d{8,})/, ""))
      .filter((c) => c !== "");
    const existing = byId.get(id);
    if (existing) {
      for (const c of codes) if (!existing.c.includes(c)) existing.c.push(c);
      continue;
    }
    const description = pick(r, "promotiondescription", "promodescription", "description").replace(/\s+/g, " ").trim();
    const remarks = pick(r, "remarks", "remark");
    const discountType = pick(r, "discounttype");
    const discountRate = rate(num(pick(r, "discountrate")));
    const discounted = num(pick(r, "discountedprice", "promoprice"));
    const minQty = num(pick(r, "minqty", "minnoofitemofered", "minnoofitemsoffered")) ?? 1;
    const isRate = discountType === "2" || (discounted === null && discountRate !== null);
    byId.set(id, {
      id,
      d: description,
      c: codes,
      mq: minQty > 0 ? minQty : 1,
      dp: !isRate && discounted !== null && discounted > 0 ? discounted : null,
      dr: isRate ? discountRate : null,
      s: promoTime(pick(r, "promotionstartdate", "startdate"), pick(r, "promotionstarthour", "starthour"), false),
      e: promoTime(pick(r, "promotionenddate", "enddate"), pick(r, "promotionendhour", "endhour"), true),
      cl: clubOnly(pick(r, "clubid", "clubs")),
      cp: TRUE.has(pick(r, "additionaliscoupon", "iscoupon").toLowerCase()),
      sl: STOCK.test(`${description} ${remarks}`),
      mb: (() => {
        const v = num(pick(r, "additionalminbasketamount", "minpurchaseamnt", "minpurchaseamount"));
        return v !== null && v > 0 ? v : null;
      })(),
      t: TRUE.has(pick(r, "additionalistotal").toLowerCase()),
    });
  }
  const storeRaw = pick(header, "storeid", "store_id");
  return { storeId: storeRaw ? normalizeStoreId(storeRaw) : null, promos: [...byId.values()] };
}

/**
 * The promotions the app may show now (for the status count; the app applies the same rules itself): running at
 * [nowLocal] (Israel time as "yyyy-mm-ddThh:mm:ss"), with a reward that can be priced, and either on the whole basket
 * or on at least one barcode the branch sells now ([sold]; missing from the price file = out of stock). Those that
 * depend on stock are shown too, with a warning.
 */
export function offerable(promos: PromoItem[], sold: Set<string>, nowLocal: string): PromoItem[] {
  return promos
    .filter((p) => (!p.s || p.s <= nowLocal) && (!p.e || p.e >= nowLocal))
    .filter((p) => p.dp !== null || p.dr !== null)
    .filter((p) => p.t || p.c.some((c) => sold.has(c)));
}

/** "now" in Israel as "yyyy-mm-ddThh:mm:ss", to compare with the files' local times. */
export function israelLocal(now: Date): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Jerusalem",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "00";
  return `${get("year")}-${get("month")}-${get("day")}T${get("hour")}:${get("minute")}:${get("second")}`;
}
