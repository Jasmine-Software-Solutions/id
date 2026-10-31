import type { Pool } from "pg";

/**
 * Deletes every unexpired session for an account, returning the deleted ids.
 *
 * Equivalent to `ExposedSessionRepository.findByAccount` (expires_at >= now)
 * followed by deleting each row — one statement instead of a read-then-delete.
 */
export async function deleteActiveSessions(
  pool: Pool,
  accountId: string,
  nowMs: number,
): Promise<string[]> {
  const result = await pool.query<{ id: string }>(
    "DELETE FROM sessions WHERE account = $1 AND expires_at >= $2 RETURNING id",
    [accountId, nowMs],
  );
  return result.rows.map((row) => row.id);
}
