import { normalizeStoreId } from "./filenames";
import { RawBranch, RawItem } from "./model";
import { extractRecords, num, pick } from "./xml";

/** Stores file → branches. Handles <Store>, <Branch> and <STORE> variants. */
export function parseStores(xml: string): RawBranch[] {
  const { records } = extractRecords(xml, ["store", "branch"]);
  const out: RawBranch[] = [];
  for (const r of records) {
    const id = pick(r, "storeid", "store_id", "branchid", "storenumber");
    if (!id) continue;
    out.push({
      storeId: normalizeStoreId(id),
      name: clean(pick(r, "storename", "branchname", "name")),
      address: clean(pick(r, "address", "storeaddress", "street")),
      city: clean(pick(r, "city", "cityname")),
      zip: pick(r, "zipcode", "zip"),
      subChain: clean(pick(r, "subchainname")),
      storeType: pick(r, "storetype"),
    });
  }
  return out;
}

/** Prices / PriceFull file → items, plus the store id from the file header. */
export function parsePrices(xml: string): { storeId: string | null; items: RawItem[] } {
  const { header, records } = extractRecords(xml, ["item", "product"]);
  const storeRaw = pick(header, "storeid", "store_id");
  const items: RawItem[] = [];
  for (const r of records) {
    const code = pick(r, "itemcode", "itemid", "barcode").replace(/^0+(?=\d{8,})/, "");
    const price = num(pick(r, "itemprice", "price"));
    if (!code || price === null || price <= 0) continue;
    const weighted = ["1", "true", "yes"].includes(pick(r, "bisweighted", "isweighted").toLowerCase());
    items.push({
      code,
      name: clean(pick(r, "itemname", "itemnm", "manufactureritemdescription")),
      brand: clean(pick(r, "manufacturername", "manufacturename")),
      qty: num(pick(r, "quantity")),
      unit: clean(pick(r, "unitqty", "unitofmeasure")),
      measure: clean(pick(r, "unitofmeasure")),
      weighted,
      price,
      unitPrice: num(pick(r, "unitofmeasureprice")),
      internal: pick(r, "itemtype") === "0",
      updated: pick(r, "priceupdatedate", "priceupdatetime"),
    });
  }
  return { storeId: storeRaw ? normalizeStoreId(storeRaw) : null, items };
}

function clean(s: string): string {
  return s.replace(/\s+/g, " ").trim();
}

/** Normalised Hebrew/Latin city key: no quotes, dashes or spaces. */
export function cityKey(s: string): string {
  return s
    .toLowerCase()
    .replace(/["'׳״`\-–\s.()]/g, "")
    .replace(/יי/g, "י");
}

const KRAYOT: string[] = [
  "קריית אתא", "קרית אתא", "kiryat ata", "6800",
  "קריית ביאליק", "קרית ביאליק", "kiryat bialik", "9500",
  "קריית מוצקין", "קרית מוצקין", "kiryat motzkin", "8200",
  "קריית ים", "קרית ים", "kiryat yam", "9600",
];

/** Spellings, English names and CBS codes per configured city. A key may also name a group of cities ("הקריות"). */
const ALIASES: Record<string, string[]> = {
  ירושלים: ["ירושלים", "ירושלם", "ים", "jerusalem", "yerushalayim", "3000"],
  // Haifa, including neighbourhoods that some Stores files list as the city
  חיפה: ["חיפה", "haifa", "hefa", "4000", "קריית חיים", "קרית חיים", "kiryat haim", "קריית שמואל", "קרית שמואל", "kiryat shmuel", "קריית אליעזר", "קרית אליעזר", "נווה שאנן", "הדר הכרמל"],
  הקריות: KRAYOT,
  "קריית אתא": KRAYOT.slice(0, 4),
  "קריית ביאליק": KRAYOT.slice(4, 8),
  "קריית מוצקין": KRAYOT.slice(8, 12),
  "קריית ים": KRAYOT.slice(12, 16),
};

/** True when a Stores-file city (name or CBS code) matches one of the configured cities. */
export function cityInScope(city: string, cities: string[]): boolean {
  const k = cityKey(city);
  if (!k) return false;
  return cities.some((c) => {
    const ck = cityKey(c);
    const keys = new Set([ck, ...(ALIASES[c] ?? []).map(cityKey)]);
    return keys.has(k) || [...keys].some((a) => a.length >= 4 && k.includes(a));
  });
}
