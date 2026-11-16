export const USER_AGENT = "necessityQ-price-ingestion/0.1 (+https://github.com/yanivmalka/necessityQ)";

/** GET with timeout, polite User-Agent and exponential backoff on 429/5xx/network errors. */
export async function httpGet(url: string, opts: { timeoutMs?: number; tries?: number; maxBytes?: number } = {}): Promise<Uint8Array> {
  const tries = opts.tries ?? 4;
  const maxBytes = opts.maxBytes ?? 300 * 1024 * 1024;
  let delay = 2000;
  let lastError: unknown;
  for (let attempt = 1; attempt <= tries; attempt++) {
    try {
      const res = await fetch(url, {
        headers: { "User-Agent": USER_AGENT, Accept: "*/*" },
        signal: AbortSignal.timeout(opts.timeoutMs ?? 120_000),
        redirect: "follow",
      });
      if (res.status === 429 || res.status >= 500) throw new Error(`HTTP ${res.status} for ${url}`);
      if (!res.ok) throw Object.assign(new Error(`HTTP ${res.status} for ${url}`), { permanent: true });
      const buf = new Uint8Array(await res.arrayBuffer());
      if (buf.byteLength > maxBytes) throw Object.assign(new Error(`file too large (${buf.byteLength})`), { permanent: true });
      return buf;
    } catch (e) {
      lastError = e;
      if ((e as { permanent?: boolean }).permanent || attempt === tries) break;
      await sleep(delay);
      delay *= 2;
    }
  }
  throw lastError;
}

export async function httpText(url: string, timeoutMs = 60_000): Promise<string> {
  return new TextDecoder("utf-8").decode(await httpGet(url, { timeoutMs }));
}

export function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

export function decodeEntities(s: string): string {
  return s
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}
