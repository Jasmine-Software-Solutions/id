import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";
import { RestrictionRepository } from "../../src/infrastructure/db/restrictions";
import { deleteActiveSessions } from "../../src/infrastructure/db/sessions";
import { deletePasswords } from "../../src/infrastructure/db/passwords";

/**
 * Runs against the dev-stack Postgres (database `id`). Set SKIP_INTEGRATION=1
 * to skip when no dev stack is up.
 */
const skip = process.env.SKIP_INTEGRATION === "1";
const runId = randomUUID().slice(0, 8);

describe.skipIf(skip)("id database actions", () => {
  let pool: Pool;
  const accountIds: string[] = [];
  const restrictionIds: string[] = [];

  beforeAll(() => {
    pool = new Pool({
      host: process.env.PGHOST ?? "localhost",
      port: Number(process.env.PGPORT ?? 5432),
      database: process.env.PGDATABASE ?? "id",
      user: process.env.PGUSER ?? "user",
      password: process.env.PGPASSWORD ?? "password",
      max: 2,
    });
  });

  afterAll(async () => {
    for (const id of restrictionIds) {
      await pool.query("DELETE FROM restrictions WHERE id = $1", [id]);
    }
    for (const id of accountIds) {
      await pool.query("DELETE FROM accounts WHERE id = $1", [id]);
    }
    await pool.end();
  });

  async function createAccount(): Promise<string> {
    const id = randomUUID();
    accountIds.push(id);
    await pool.query(
      `INSERT INTO accounts (id, created_at, email, first_name, last_name, email_verified)
       VALUES ($1, $2, $3, '', '', false)`,
      [id, Date.now(), `worker-ts-${runId}-${id}@example.com`],
    );
    return id;
  }

  it("inserts a restriction row with millisecond timestamps", async () => {
    const repository = new RestrictionRepository(pool);
    const row = {
      id: randomUUID(),
      createdAt: Date.now(),
      type: "ACCOUNT_LOCK",
      targetType: "ACCOUNT",
      targetId: randomUUID(),
      eventId: randomUUID(),
      durationMinutes: 30,
      expiresAt: 0,
    };
    row.expiresAt = row.createdAt + 30 * 60_000;
    restrictionIds.push(row.id);

    await repository.create(row);

    const result = await pool.query("SELECT * FROM restrictions WHERE id = $1", [row.id]);
    expect(result.rows).toHaveLength(1);
    expect(result.rows[0]).toMatchObject({
      type: "ACCOUNT_LOCK",
      target_type: "ACCOUNT",
      target_id: row.targetId,
      event_id: row.eventId,
      duration_minutes: 30,
    });
    // postgres bigint comes back as a string
    expect(Number(result.rows[0].created_at)).toBe(row.createdAt);
    expect(Number(result.rows[0].expires_at)).toBe(row.expiresAt);
  });

  it("deletes only unexpired sessions", async () => {
    const accountId = await createAccount();
    const now = Date.now();
    const activeId = randomUUID();
    const boundaryId = randomUUID();
    const expiredId = randomUUID();

    for (const [id, expiresAt] of [
      [activeId, now + 60_000],
      [boundaryId, now],
      [expiredId, now - 1],
    ] as const) {
      await pool.query(
        `INSERT INTO sessions (id, created_at, expires_at, account, argon2_token_hash)
         VALUES ($1, $2, $3, $4, 'hash')`,
        [id, now, expiresAt, accountId],
      );
    }

    const deleted = await deleteActiveSessions(pool, accountId, now);
    expect(deleted.sort()).toEqual([activeId, boundaryId].sort());

    const remaining = await pool.query("SELECT id FROM sessions WHERE account = $1", [accountId]);
    expect(remaining.rows.map((row) => row.id)).toEqual([expiredId]);
  });

  it("deletes every password row for an account", async () => {
    const accountId = await createAccount();
    const now = Date.now();

    for (const id of [randomUUID(), randomUUID()]) {
      await pool.query(
        `INSERT INTO passwords (id, created_at, account_id, argon2_password_hash)
         VALUES ($1, $2, $3, 'hash')`,
        [id, now, accountId],
      );
    }

    const deleted = await deletePasswords(pool, accountId);
    expect(deleted).toHaveLength(2);

    const remaining = await pool.query("SELECT id FROM passwords WHERE account_id = $1", [accountId]);
    expect(remaining.rows).toHaveLength(0);
  });

  it("leaves other accounts untouched", async () => {
    const accountId = await createAccount();
    const otherId = await createAccount();
    const now = Date.now();

    await pool.query(
      `INSERT INTO sessions (id, created_at, expires_at, account, argon2_token_hash)
       VALUES ($1, $2, $3, $4, 'hash')`,
      [randomUUID(), now, now + 60_000, otherId],
    );

    await deleteActiveSessions(pool, accountId, now);

    const remaining = await pool.query("SELECT id FROM sessions WHERE account = $1", [otherId]);
    expect(remaining.rows).toHaveLength(1);
  });
});
