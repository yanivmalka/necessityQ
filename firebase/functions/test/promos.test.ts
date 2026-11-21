import { describe, expect, it } from "vitest";
import { Chain } from "../src/chains";
import { entry } from "../src/filenames";
import { BranchStatus, PromoStatus, PromoStore, offersDue, refreshBranchPromos, refreshBranchStock, DEFAULT_CONFIG } from "../src/ingest";
import { Branch, FileKind, Snapshot } from "../src/model";
import { PromoSnapshot, israelLocal, offerable, parsePromotions, promoTime } from "../src/promos";
import { Source } from "../src/sources";

const PROMO_XML = `<?xml version="1.0" encoding="utf-8"?>
<root><ChainId>7290058140886</ChainId><StoreId>039</StoreId>
<Promotions Count="4">
 <Promotion>
  <PromotionId>101</PromotionId>
  <PromotionDescription>קוטג' 5% 2 ב-10</PromotionDescription>
  <PromotionStartDate>2026-11-01</PromotionStartDate><PromotionStartHour>00:00</PromotionStartHour>
  <PromotionEndDate>2026-11-30</PromotionEndDate><PromotionEndHour>23:59</PromotionEndHour>
  <MinQty>2</MinQty><DiscountType>1</DiscountType><DiscountedPrice>10</DiscountedPrice>
  <PromotionItems Count="2"><Item><ItemCode>7290000042015</ItemCode><IsGiftItem>0</IsGiftItem></Item><Item><ItemCode>7290000000001</ItemCode></Item></PromotionItems>
  <AdditionalRestrictions><AdditionalIsCoupon>0</AdditionalIsCoupon><AdditionalIsTotal>0</AdditionalIsTotal></AdditionalRestrictions>
  <Clubs><ClubId>0 - כלל הלקוחות</ClubId></Clubs>
 </Promotion>
 <Promotion>
  <PromotionId>102</PromotionId>
  <PromotionDescription>במבה 20% הנחה עד גמר המלאי</PromotionDescription>
  <PromotionEndDate>2026-12-31</PromotionEndDate>
  <DiscountType>2</DiscountType><DiscountRate>2000</DiscountRate><MinQty>1</MinQty>
  <PromotionItems><Item><ItemCode>7290000042015</ItemCode></Item></PromotionItems>
  <Clubs><ClubId>1</ClubId></Clubs>
 </Promotion>
 <Promotion>
  <PromotionId>103</PromotionId>
  <PromotionDescription>ישן</PromotionDescription>
  <PromotionEndDate>2026-10-01</PromotionEndDate><DiscountedPrice>5</DiscountedPrice>
  <PromotionItems><Item><ItemCode>7290000042015</ItemCode></Item></PromotionItems>
 </Promotion>
 <Promotion>
  <PromotionId>104</PromotionId>
  <PromotionDescription>10 ש"ח הנחה בקנייה מעל 300</PromotionDescription>
  <PromotionEndDate>2026-12-31</PromotionEndDate><DiscountedPrice>10</DiscountedPrice>
  <AdditionalRestrictions><AdditionalIsCoupon>1</AdditionalIsCoupon><AdditionalIsTotal>1</AdditionalIsTotal><AdditionalMinBasketAmount>300</AdditionalMinBasketAmount></AdditionalRestrictions>
 </Promotion>
</Promotions></root>`;

const SALES_XML = `<Root><StoreId>5</StoreId><Sales>
 <Sale><PromotionID>7</PromotionID><ItemCode>111111111</ItemCode><PromotionDescription>3 ב-20</PromotionDescription><MinQty>3</MinQty><DiscountedPrice>20</DiscountedPrice><PromotionEndDate>2026-12-01</PromotionEndDate></Sale>
 <Sale><PromotionID>7</PromotionID><ItemCode>222222222</ItemCode><PromotionDescription>3 ב-20</PromotionDescription><MinQty>3</MinQty><DiscountedPrice>20</DiscountedPrice><PromotionEndDate>2026-12-01</PromotionEndDate></Sale>
</Sales></Root>`;

describe("promotions", () => {
  it("parses nested promotions with their items, club, coupon and stock flags", () => {
    const { storeId, promos } = parsePromotions(PROMO_XML);
    expect(storeId).toBe("39");
    expect(promos).toHaveLength(4);
    expect(promos[0]).toMatchObject({ id: "101", c: ["7290000042015", "7290000000001"], mq: 2, dp: 10, dr: null, cl: false, cp: false, sl: false, s: "2026-11-01T00:00:00", e: "2026-11-30T23:59:00" });
    expect(promos[1]).toMatchObject({ id: "102", dr: 0.2, dp: null, cl: true, sl: true, e: "2026-12-31T23:59:59" });
    expect(promos[3]).toMatchObject({ cp: true, t: true, mb: 300 });
  });

  it("groups one-row-per-item sales by promotion id", () => {
    const { storeId, promos } = parsePromotions(SALES_XML);
    expect(storeId).toBe("5");
    expect(promos).toEqual([expect.objectContaining({ id: "7", c: ["111111111", "222222222"], mq: 3, dp: 20 })]);
  });

  it("counts as offerable only running, stock-independent promotions on products the branch sells", () => {
    const { promos } = parsePromotions(PROMO_XML);
    // 102 depends on stock, 103 ended, 104 is on the whole basket
    expect(offerable(promos, new Set(["7290000042015"]), "2026-11-21T12:00:00").map((p) => p.id)).toEqual(["101"]);
    // out of stock: the product is not in the price file
    expect(offerable(promos, new Set(["999"]), "2026-11-21T12:00:00")).toEqual([]);
    expect(offerable(promos, new Set(["7290000042015"]), "2026-12-05T12:00:00")).toEqual([]);
  });

  it("reads the common date forms", () => {
    expect(promoTime("2026-11-30 23:59", "", true)).toBe("2026-11-30T23:59:00");
    expect(promoTime("30/11/2026", "", true)).toBe("2026-11-30T23:59:59");
    expect(promoTime("", "", false)).toBe("");
    expect(israelLocal(new Date("2026-11-21T10:00:00Z"))).toBe("2026-11-21T12:00:00");
  });

  it("checks every 30 minutes", () => {
    const now = new Date("2026-11-21T10:00:00Z");
    expect(offersDue(null, now)).toBe(true);
    const st = { lastAttemptAt: "2026-11-21T09:45:00Z" } as PromoStatus;
    expect(offersDue(st, now)).toBe(false);
    expect(offersDue({ ...st, lastAttemptAt: "2026-11-21T09:29:00Z" }, now)).toBe(true);
  });
});

const branch = { id: "ramilevy_39", storeId: "39", chainKey: "ramilevy" } as Branch;
const enc = (s: string) => new TextEncoder().encode(s);

function stores(snapshot: Snapshot | null, status: BranchStatus | null) {
  const mem = {
    snapshot,
    status,
    promoStatus: null as PromoStatus | null,
    promos: null as PromoSnapshot | null,
  };
  const store = {
    async readIndex() {
      return { updatedAt: null, branches: [branch] };
    },
    async writeIndex() {},
    async readSnapshot() {
      return mem.snapshot;
    },
    async writeSnapshot(s: Snapshot) {
      mem.snapshot = s;
    },
    async readStatus() {
      return mem.status;
    },
    async writeStatus(_id: string, s: BranchStatus) {
      mem.status = s;
    },
    async trackedBranchIds() {
      return [branch.id];
    },
    geoCache: { async get() { return undefined; }, async put() {} },
  };
  const promoStore: PromoStore = {
    async readPromoStatus() {
      return mem.promoStatus;
    },
    async writePromoStatus(_id, s) {
      mem.promoStatus = s;
    },
    async writePromos(p) {
      mem.promos = p;
    },
  };
  return { mem, store, promoStore };
}

const snap = (codes: string[], stamp: string): Snapshot => ({
  v: 1, branchId: branch.id, updatedAt: "2026-11-21T01:00:00Z", sourceFile: "PriceFull7290058140886-039-202611210300.gz", sourceStamp: stamp, rangeDays: 90,
  items: codes.map((c) => ({ c, n: c, b: "", q: null, u: "", w: false, i: false, p: 6, up: null, s: "2026-11-01", lo: 6, hi: 6, h: [["2026-11-01", 6]] })),
});

describe("promotions and stock every half hour", () => {
  it("writes every promotion of the file as published, and skips an unchanged file", async () => {
    const { mem, store, promoStore } = stores(snap(["7290000042015"], "202611210300"), null);
    let fetches = 0;
    const src: Source = {
      async list(_c: Chain, kind: FileKind) {
        return kind === "promofull" ? [entry("PromoFull7290058140886-039-202611211000.gz", "p")] : [];
      },
      async fetch() {
        fetches++;
        return enc(PROMO_XML);
      },
    };
    const deps = { store, sourceFor: () => src, now: () => new Date("2026-11-21T10:30:00Z") };
    const st = await refreshBranchPromos(branch, deps, promoStore);
    expect(st).toMatchObject({ error: null, promos: 4, active: 1 });
    expect(mem.promos?.promos.map((p) => p.id)).toEqual(["101", "102", "103", "104"]);
    // nothing is cut from a promotion: both barcodes stay, also the one the branch does not sell now
    expect(mem.promos?.promos[0].c).toEqual(["7290000042015", "7290000000001"]);
    await refreshBranchPromos(branch, deps, promoStore);
    expect(fetches).toBe(1);
  });

  it("merges a Price delta newer than the snapshot, keeping products it does not list", async () => {
    const status: BranchStatus = { updatedAt: "2026-11-21T01:00:00Z", items: 2, sourceFile: "PriceFull7290058140886-039-202611210300.gz", sourceStamp: "202611210300", lastAttemptAt: null, error: null };
    const { mem, store } = stores(snap(["7290000042015", "7290000000001"], "202611210300"), status);
    const delta = `<root><StoreId>39</StoreId><Items><Item><ItemCode>7290000042015</ItemCode><ItemName>חלב</ItemName><ItemPrice>5.5</ItemPrice></Item></Items></root>`;
    const src: Source = {
      async list(_c: Chain, kind: FileKind) {
        return kind === "pricefull"
          ? [entry("PriceFull7290058140886-039-202611210300.gz", "f")]
          : [entry("Price7290058140886-039-202611211100.gz", "d")];
      },
      async fetch() {
        return enc(delta);
      },
    };
    const deps = { store, sourceFor: () => src, now: () => new Date("2026-11-21T09:05:00Z") };
    const st = await refreshBranchStock(branch, DEFAULT_CONFIG, deps);
    expect(st).toMatchObject({ error: null, items: 2, sourceStamp: "202611211100" });
    expect(mem.snapshot?.items.find((i) => i.c === "7290000042015")?.p).toBe(5.5);
    // nothing newer: no download
    expect(await refreshBranchStock(branch, DEFAULT_CONFIG, deps)).toBeNull();
  });
});
