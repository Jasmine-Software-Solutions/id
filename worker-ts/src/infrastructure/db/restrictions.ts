import type { Pool } from "pg";

export interface NewRestriction {
  id: string;
  createdAt: number;
  type: string;
  targetType: string;
  targetId: string;
  eventId: string;
  durationMinutes: number;
  expiresAt: number;
}

export interface RestrictionRecord extends NewRestriction {}

/**
 * Direct insert into the id database's `restrictions` table.
 *
 * Column names and bigint-millisecond timestamps mirror Exposed's
 * `ExposedRestrictionRepository.Table` (verified with `\d restrictions`).
 */
export class RestrictionRepository {
  constructor(private readonly pool: Pool) {}

  async create(restriction: NewRestriction): Promise<RestrictionRecord> {
    await this.pool.query(
      `INSERT INTO restrictions
         (id, created_at, type, target_type, target_id, event_id, duration_minutes, expires_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [
        restriction.id,
        restriction.createdAt,
        restriction.type,
        restriction.targetType,
        restriction.targetId,
        restriction.eventId,
        restriction.durationMinutes,
        restriction.expiresAt,
      ],
    );
    return restriction;
  }
}
