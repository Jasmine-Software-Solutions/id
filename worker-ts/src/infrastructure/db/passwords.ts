import type { Pool } from "pg";

/**
 * Deletes every password row for an account, returning the deleted ids.
 *
 * Mirrors `ExposedPasswordRepository.findByAccount` + delete (the Java worker
 * left no password behind after a credential-stuffing hit).
 */
export async function deletePasswords(pool: Pool, accountId: string): Promise<string[]> {
  const result = await pool.query<{ id: string }>(
    "DELETE FROM passwords WHERE account_id = $1 RETURNING id",
    [accountId],
  );
  return result.rows.map((row) => row.id);
}
