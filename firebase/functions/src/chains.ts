/**
 * Chains the pipeline can read, grouped by publishing platform (one adapter per platform).
 * Endpoints and Cerberus user names follow the gov.il index and the open-source
 * OpenIsraeliSupermarkets scrapers (MIT). Cerberus passwords are empty unless the chain publishes one.
 */
export type Platform = "shufersal" | "cerberus" | "publishprice" | "laibcatalog";

export interface Chain {
  key: string;
  name: string;
  chainIds: string[];
  platform: Platform;
  /** cerberus: FTP user / password */
  user?: string;
  password?: string;
  /** publishprice: https://prices.<site>.co.il/ */
  site?: string;
}

export const CHAINS: Chain[] = [
  { key: "shufersal", name: "שופרסל", chainIds: ["7290027600007"], platform: "shufersal" },
  { key: "ramilevy", name: "רמי לוי", chainIds: ["7290058140886"], platform: "cerberus", user: "RamiLevi" },
  { key: "osherad", name: "אושר עד", chainIds: ["7290103152017"], platform: "cerberus", user: "osherad" },
  { key: "yohananof", name: "יוחננוף", chainIds: ["7290803800003"], platform: "cerberus", user: "yohananof" },
  { key: "tivtaam", name: "טיב טעם", chainIds: ["7290873255550"], platform: "cerberus", user: "TivTaam" },
  { key: "superyuda", name: "סופר יודה", chainIds: ["7290058198450", "7290058177776"], platform: "cerberus", user: "yuda_ho", password: "Yud@147" },
  { key: "keshet", name: "קשת טעמים", chainIds: ["7290785400000"], platform: "cerberus", user: "Keshet" },
  { key: "stopmarket", name: "סטופ מרקט", chainIds: ["7290639000004"], platform: "cerberus", user: "Stop_Market" },
  { key: "freshmarket", name: "פרשמרקט", chainIds: ["7290876100000"], platform: "cerberus", user: "freshmarket" },
  { key: "carrefour", name: "קרפור / יינות ביתן", chainIds: ["7290055700007"], platform: "publishprice", site: "carrefour" },
  { key: "victory", name: "ויקטורי", chainIds: ["7290696200003", "7290058103393"], platform: "laibcatalog" },
  { key: "mahsaneihashuk", name: "מחסני השוק", chainIds: ["7290661400001", "7290633800006"], platform: "laibcatalog" },
];

/** Chains enabled when the admin has not configured a list (strong presence in Jerusalem). */
export const DEFAULT_CHAINS = ["shufersal", "ramilevy", "osherad", "yohananof", "victory", "carrefour", "superyuda", "tivtaam"];

export function chainByKey(key: string): Chain | undefined {
  return CHAINS.find((c) => c.key === key);
}
