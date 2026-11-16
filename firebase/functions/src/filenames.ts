import { FileEntry, FileKind } from "./model";

/**
 * Parses retailer file names such as
 *   PriceFull7290027600007-001-202611160300.gz
 *   Stores7290058140886-202611160500.xml
 *   PromoFull7290696200003-001-012-20261116-030000.xml.gz
 * Kind, store id and a sortable stamp (yyyymmddhhmm) are best-effort.
 */
export function parseFileName(name: string): Pick<FileEntry, "kind" | "storeId" | "stamp"> {
  const base = name.split("/").pop()!.split("?")[0];
  const lower = base.toLowerCase();
  let kind: FileKind | null = null;
  if (lower.includes("null")) kind = null;
  else if (lower.startsWith("pricefull")) kind = "pricefull";
  else if (lower.startsWith("promofull")) kind = "promofull";
  else if (lower.startsWith("price")) kind = "price";
  else if (lower.startsWith("promo")) kind = "promo";
  else if (lower.startsWith("store")) kind = "stores";

  const stem = lower.replace(/\.(xml|gz|zip)$/g, "").replace(/\.(xml|gz|zip)$/g, "");
  // groups of digits after the 13-digit chain id
  const m = stem.match(/^[a-z]+(\d{13})(.*)$/);
  const rest = m ? m[2] : stem;
  const groups = rest.split(/[-_]/).filter((g) => /^\d+$/.test(g));

  let stamp = "";
  const small: string[] = [];
  for (let i = 0; i < groups.length; i++) {
    const g = groups[i];
    if (g.length >= 8) {
      // yyyymmdd[hhmm[ss]] — or yyyymmdd followed by a separate hhmmss group
      let s = g;
      if (g.length === 8 && groups[i + 1] && groups[i + 1].length >= 4) s = g + groups[i + 1];
      stamp = (s + "0000").slice(0, 12);
      break;
    }
    small.push(g);
  }
  const storeId = kind === "stores" || small.length === 0 ? null : normalizeStoreId(small[small.length - 1]);
  return { kind, storeId, stamp };
}

export function normalizeStoreId(raw: string | number): string {
  const s = String(raw).trim();
  if (/^\d+$/.test(s)) return String(parseInt(s, 10));
  return s;
}

export function entry(name: string, url: string): FileEntry {
  return { name, url, ...parseFileName(name) };
}

/** The newest file of a kind (optionally for one store). Full files win over deltas of the same stamp. */
export function latest(files: FileEntry[], kind: FileKind, storeId?: string): FileEntry | null {
  const wanted = storeId === undefined ? null : normalizeStoreId(storeId);
  const matching = files.filter((f) => f.kind === kind && (wanted === null || f.storeId === wanted));
  matching.sort((a, b) => (a.stamp < b.stamp ? 1 : a.stamp > b.stamp ? -1 : a.name < b.name ? 1 : -1));
  return matching[0] ?? null;
}
