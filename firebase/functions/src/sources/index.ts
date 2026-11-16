import { Client } from "basic-ftp";
import { Writable } from "node:stream";
import { Chain } from "../chains";
import { entry } from "../filenames";
import { FileEntry, FileKind } from "../model";
import { decodeEntities, httpGet, httpText } from "./http";

/** Lists and fetches a chain's transparency files, whatever platform publishes them. */
export interface Source {
  list(chain: Chain, kind: FileKind, storeId?: string): Promise<FileEntry[]>;
  fetch(chain: Chain, file: FileEntry): Promise<Uint8Array>;
}

// ---------------------------------------------------------------- Shufersal

const SHUFERSAL = "https://prices.shufersal.co.il";
const SHUFERSAL_CAT: Record<FileKind, string> = { price: "1", pricefull: "2", promo: "3", promofull: "4", stores: "5" };

/** File links on a prices.shufersal.co.il listing page (they point at signed blob-storage URLs). */
export function parseShufersalPage(html: string): FileEntry[] {
  const out: FileEntry[] = [];
  const seen = new Set<string>();
  for (const m of html.matchAll(/href="([^"]+)"/gi)) {
    let url = decodeEntities(m[1]);
    const nameMatch = url.match(/([^/?]+\.(?:gz|xml|zip))(?:\?|$)/i);
    if (!nameMatch) continue;
    if (url.startsWith("/")) url = SHUFERSAL + url;
    if (seen.has(url)) continue;
    seen.add(url);
    out.push(entry(nameMatch[1], url));
  }
  return out;
}

const shufersal: Source = {
  async list(_chain, kind, storeId) {
    const params = new URLSearchParams({ catID: SHUFERSAL_CAT[kind] });
    if (storeId) params.set("storeId", storeId);
    return parseShufersalPage(await httpText(`${SHUFERSAL}/FileObject/UpdateCategory?${params}`));
  },
  fetch: (_c, f) => httpGet(f.url),
};

// ---------------------------------------------------------------- PublishPrice (prices.<site>.co.il)

/** The page hard-codes `const path = '…'` and `const files = [{name,…}]` in a script. */
export function parsePublishPricePage(html: string, baseUrl: string): FileEntry[] {
  const filesMatch = html.match(/const\s+files\s*=\s*(\[[\s\S]*?\])\s*;?\s*\n/);
  if (!filesMatch) return [];
  const pathMatch = html.match(/const\s+path\s*=\s*['"]([^'"]*)['"]/);
  const path = (pathMatch?.[1] ?? "").replace(/^\.\//, "").replace(/^\/+|\/+$/g, "");
  let files: { name: string }[];
  try {
    files = JSON.parse(filesMatch[1]);
  } catch {
    return [];
  }
  const base = baseUrl.replace(/\/$/, "");
  return files.filter((f) => f && f.name).map((f) => entry(f.name, path ? `${base}/${path}/${f.name}` : `${base}/${f.name}`));
}

const publishPrice: Source = {
  async list(chain) {
    const base = `https://prices.${chain.site}.co.il/`;
    return parsePublishPricePage(await httpText(base), base);
  },
  fetch: (_c, f) => httpGet(f.url),
};

// ---------------------------------------------------------------- Laibcatalog (Victory, Mahsanei HaShuk …)

const LAIB = "https://laibcatalog.co.il";

export function parseLaibFiles(json: unknown, chainId: string): FileEntry[] {
  if (!Array.isArray(json)) return [];
  return json
    .map((e) => (e && typeof e === "object" ? (e as Record<string, unknown>) : {}))
    .map((e) => String(e.fileName ?? e.filename ?? e.name ?? ""))
    .filter((n) => n !== "")
    .map((n) => entry(n, `${LAIB}/webapi/${chainId}/${n}`));
}

const laibcatalog: Source = {
  async list(chain, _kind, storeId) {
    const all: FileEntry[] = [];
    for (const id of chain.chainIds) {
      const params = new URLSearchParams({ edi: id });
      if (storeId) params.set("branchNumber", storeId);
      try {
        const body = await httpText(`${LAIB}/webapi/api/getfiles?${params}`);
        all.push(...parseLaibFiles(JSON.parse(body), chain.chainIds[0]));
      } catch {
        // a chain id without branches answers 400; the other id may still work
      }
    }
    return all;
  },
  fetch: (_c, f) => httpGet(f.url),
};

// ---------------------------------------------------------------- Cerberus (url.retail.publishedprices.co.il, FTP)

const CERBERUS_HOST = "url.retail.publishedprices.co.il";

async function withFtp<T>(chain: Chain, run: (c: Client) => Promise<T>): Promise<T> {
  const client = new Client(120_000);
  try {
    await client.access({
      host: CERBERUS_HOST,
      user: chain.user,
      password: chain.password ?? "",
      secure: true,
      // the portal's certificate does not always validate; the data is public anyway
      secureOptions: { rejectUnauthorized: false },
    });
    return await run(client);
  } finally {
    client.close();
  }
}

const listingCache = new Map<string, { at: number; files: FileEntry[] }>();

const cerberus: Source = {
  async list(chain) {
    const cached = listingCache.get(chain.key);
    if (cached && Date.now() - cached.at < 10 * 60_000) return cached.files;
    const files = await withFtp(chain, async (c) =>
      (await c.list()).filter((f) => f.isFile && /\.(gz|xml|zip)$/i.test(f.name)).map((f) => entry(f.name, `ftp:${f.name}`)),
    );
    listingCache.set(chain.key, { at: Date.now(), files });
    return files;
  },
  async fetch(chain, file) {
    const chunks: Buffer[] = [];
    const sink = new Writable({
      write(chunk, _enc, cb) {
        chunks.push(chunk as Buffer);
        cb();
      },
    });
    await withFtp(chain, (c) => c.downloadTo(sink, file.url.replace(/^ftp:/, "")));
    return new Uint8Array(Buffer.concat(chunks));
  },
};

export function sourceFor(chain: Chain): Source {
  switch (chain.platform) {
    case "shufersal":
      return shufersal;
    case "publishprice":
      return publishPrice;
    case "laibcatalog":
      return laibcatalog;
    case "cerberus":
      return cerberus;
  }
}
