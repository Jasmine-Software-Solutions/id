import type { Context } from "aws-lambda";

/**
 * Remaining invocation time, with the safety margin the batch processor uses
 * to stop starting new work.
 */
export const MIN_REMAINING_MS = 5_000;

export function remainingMs(context: Pick<Context, "getRemainingTimeInMillis">): number {
  try {
    return context.getRemainingTimeInMillis();
  } catch {
    return Number.MAX_SAFE_INTEGER;
  }
}

export function hasTimeForNext(context: Pick<Context, "getRemainingTimeInMillis">): boolean {
  return remainingMs(context) >= MIN_REMAINING_MS;
}
