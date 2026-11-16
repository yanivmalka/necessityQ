/**
 * Budget kill switch. A Cloud Billing budget publishes notifications to the Pub/Sub topic
 * `billing-budget`; when the cost of the month reaches the budget, the project's billing account is
 * unlinked. The project then falls back to the no-cost quotas: paid services (functions, scheduler,
 * storage above the free tier) stop until billing is linked again by hand in the console.
 *
 * Budget notification payload:
 * https://cloud.google.com/billing/docs/how-to/budgets-programmatic-notifications#notification_format
 */
export interface BudgetNotification {
  budgetDisplayName?: string;
  costAmount?: number;
  budgetAmount?: number;
  currencyCode?: string;
  alertThresholdExceeded?: number;
  costIntervalStart?: string;
}

export interface GuardConfig {
  /** off = only log */
  enabled: boolean;
  /** cut when cost >= budget × this factor (1 = 100%) */
  cutAtRatio: number;
}

export const DEFAULT_GUARD: GuardConfig = { enabled: true, cutAtRatio: 1 };

export function guardConfig(raw: Partial<GuardConfig> | undefined): GuardConfig {
  return {
    enabled: typeof raw?.enabled === "boolean" ? raw.enabled : DEFAULT_GUARD.enabled,
    cutAtRatio: typeof raw?.cutAtRatio === "number" && raw.cutAtRatio > 0 ? raw.cutAtRatio : DEFAULT_GUARD.cutAtRatio,
  };
}

export function parseNotification(json: unknown): BudgetNotification | null {
  if (!json || typeof json !== "object") return null;
  const n = json as BudgetNotification;
  if (typeof n.costAmount !== "number" || typeof n.budgetAmount !== "number") return null;
  return n;
}

/** True when billing should be cut now. */
export function shouldCut(n: BudgetNotification, config: GuardConfig): boolean {
  if (!config.enabled) return false;
  if (!(n.budgetAmount! > 0)) return false;
  return n.costAmount! >= n.budgetAmount! * config.cutAtRatio;
}
