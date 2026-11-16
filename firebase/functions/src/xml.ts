import { gunzipSync, unzipSync } from "fflate";
import * as sax from "sax";

/** Undoes gzip / zip wrapping (detected by magic bytes) and decodes UTF-8 / UTF-16 text. */
export function decodePayload(data: Uint8Array): string {
  let bytes = data;
  for (let i = 0; i < 3; i++) {
    if (bytes[0] === 0x1f && bytes[1] === 0x8b) bytes = gunzipSync(bytes);
    else if (bytes[0] === 0x50 && bytes[1] === 0x4b) {
      const files = unzipSync(bytes);
      const first = Object.keys(files).find((n) => !n.endsWith("/"));
      if (!first) throw new Error("empty zip");
      bytes = files[first];
    } else break;
  }
  if (bytes[0] === 0xff && bytes[1] === 0xfe) return new TextDecoder("utf-16le").decode(bytes.subarray(2));
  if (bytes[0] === 0xfe && bytes[1] === 0xff) return new TextDecoder("utf-16be").decode(bytes.subarray(2));
  // UTF-16 without BOM: "<\0?\0"
  if (bytes[0] === 0x3c && bytes[1] === 0x00) return new TextDecoder("utf-16le").decode(bytes);
  if (bytes[0] === 0x00 && bytes[1] === 0x3c) return new TextDecoder("utf-16be").decode(bytes);
  const start = bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf ? 3 : 0;
  // Some chains declare windows-1255; their text is (almost) always ASCII/UTF-8 in practice.
  const head = new TextDecoder("latin1").decode(bytes.subarray(0, 200)).toLowerCase();
  if (head.includes("windows-1255")) {
    try {
      return new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(start));
    } catch {
      return new TextDecoder("windows-1255").decode(bytes.subarray(start));
    }
  }
  return new TextDecoder("utf-8").decode(bytes.subarray(start));
}

export interface Records {
  /** leaf fields seen outside any record (ChainId, SubChainId, StoreId …), lower-cased keys */
  header: Record<string, string>;
  records: Record<string, string>[];
}

/**
 * Streams through the XML and returns every element named one of [recordTags] as a flat map of its
 * descendant leaf values (lower-cased tag names, so StoreId/StoreID/STOREID all read the same).
 * Nested lists inside a record (e.g. PromotionItems/Item) are collected into `__items` as
 * comma-joined values of their first leaf field per child.
 * Malformed fragments are tolerated (non-strict parser); one bad item never aborts the file.
 */
export function extractRecords(xml: string, recordTags: string[]): Records {
  const wanted = new Set(recordTags.map((t) => t.toLowerCase()));
  const parser = sax.parser(false, { lowercase: true, trim: true });
  const header: Record<string, string> = {};
  const records: Record<string, string>[] = [];
  const stack: string[] = [];
  let current: Record<string, string> | null = null;
  let depthOfRecord = -1;
  let text = "";

  parser.onopentag = (node) => {
    const name = node.name;
    if (current === null && wanted.has(name) && !(name === "item" && stack.includes("promotion"))) {
      current = {};
      depthOfRecord = stack.length;
    }
    stack.push(name);
    text = "";
  };
  parser.ontext = (t) => {
    text += t;
  };
  parser.oncdata = (t) => {
    text += t;
  };
  parser.onclosetag = (name) => {
    stack.pop();
    const value = text.trim();
    text = "";
    if (current !== null) {
      if (stack.length === depthOfRecord) {
        records.push(current);
        current = null;
        depthOfRecord = -1;
      } else if (value !== "") {
        if (stack.length > depthOfRecord + 1 && name === "itemcode") {
          current.__items = current.__items ? `${current.__items},${value}` : value;
        } else if (!(name in current)) {
          current[name] = value;
        }
      }
    } else if (value !== "" && !(name in header)) {
      header[name] = value;
    }
  };
  parser.onerror = () => {
    // resume after malformed fragments (unescaped &, stray <)
    (parser as unknown as { error: Error | null }).error = null;
    parser.resume();
  };
  parser.write(xml).close();
  return { header, records };
}

export function num(v: string | undefined): number | null {
  if (v === undefined) return null;
  const n = Number(v.replace(/,/g, "").trim());
  return Number.isFinite(n) ? n : null;
}

export function pick(r: Record<string, string>, ...keys: string[]): string {
  for (const k of keys) {
    const v = r[k.toLowerCase()];
    if (v !== undefined && v !== "") return v;
  }
  return "";
}
