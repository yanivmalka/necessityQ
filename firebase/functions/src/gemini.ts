/**
 * Calls to Gemini that survive a busy model: a 503 ("high demand") or 429 is tried again after a short wait, and
 * then on a second, lighter model. Any other error (a bad key, a bad request) is thrown at once.
 */

/** Used when the main model stays busy: a stable Flash-Lite model (Google's models page). */
export const FALLBACK_MODEL = "gemini-3.5-flash-lite";
export const RETRY_DELAYS_MS = [1_000, 3_000];

/** Is this the model being busy or rate-limited (worth another try), rather than a real failure? */
export function isBusy(e: unknown): boolean {
  const text = e instanceof Error ? e.message : String(e);
  const status = (e as { status?: unknown })?.status;
  return status === 503 || status === 429 || /"code":\s*(503|429)|UNAVAILABLE|RESOURCE_EXHAUSTED|high demand|overloaded/i.test(text);
}

/**
 * [ask] with [model]; while busy, again after each of [delays]; still busy, once with [fallback] (when it differs).
 * Returns the answer and the model that gave it.
 */
export async function withBusyRetry<T>(
  model: string,
  ask: (model: string) => Promise<T>,
  delays: number[] = RETRY_DELAYS_MS,
  fallback: string = FALLBACK_MODEL,
  sleep: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
): Promise<{ result: T; model: string }> {
  let last: unknown;
  for (let attempt = 0; attempt <= delays.length; attempt++) {
    try {
      return { result: await ask(model), model };
    } catch (e) {
      if (!isBusy(e)) throw e;
      last = e;
      if (attempt < delays.length) await sleep(delays[attempt]);
    }
  }
  if (fallback && fallback !== model) return { result: await ask(fallback), model: fallback };
  throw last;
}
