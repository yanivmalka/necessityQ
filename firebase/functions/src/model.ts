/** Canonical shapes shared by the ingestion pipeline (independent of any retailer dialect). */

export type FileKind = "stores" | "pricefull" | "price" | "promofull" | "promo";

/** One file a retailer offers for download. */
export interface FileEntry {
  name: string;
  /** HTTP(S) URL, or `ftp:<name>` for Cerberus. */
  url: string;
  kind: FileKind | null;
  storeId: string | null;
  /** yyyymmddhhmm (sortable), "" when unknown. */
  stamp: string;
}

/** A branch as published in a Stores file. */
export interface RawBranch {
  storeId: string;
  name: string;
  address: string;
  city: string;
  zip: string;
  subChain: string;
  storeType: string;
}

/** A branch inside the data scope, as the app sees it. */
export interface Branch extends RawBranch {
  /** `${chainKey}_${storeId}` */
  id: string;
  chainKey: string;
  chainName: string;
  lat: number | null;
  lng: number | null;
  /** true when only the city centre could be geocoded */
  approx: boolean;
}

/** One product line of a Prices / PriceFull file. */
export interface RawItem {
  code: string;
  name: string;
  brand: string;
  /** package size, e.g. 500 */
  qty: number | null;
  /** unit of the package size, e.g. "גרם" */
  unit: string;
  weighted: boolean;
  price: number;
  unitPrice: number | null;
  /** chain-internal code (not comparable across chains) */
  internal: boolean;
  updated: string;
}

/** A product in the per-branch snapshot the app downloads (compact keys keep the file small). */
export interface SnapshotItem {
  c: string; // code (barcode)
  n: string; // name
  b: string; // brand / manufacturer
  q: number | null; // package size
  u: string; // unit
  w: boolean; // weighted
  i: boolean; // internal code
  p: number; // current price
  up: number | null; // unit price as published
  s: string; // since (yyyy-mm-dd) — the current price holds since this day
  lo: number; // lowest price in the range window
  hi: number; // highest price in the range window
  h: [string, number][]; // history of changes [day, price], oldest first
}

export interface Snapshot {
  v: 1;
  branchId: string;
  updatedAt: string; // ISO
  sourceFile: string;
  sourceStamp: string;
  rangeDays: number;
  items: SnapshotItem[];
}
