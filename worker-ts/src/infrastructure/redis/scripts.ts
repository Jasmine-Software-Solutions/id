import type Redis from "ioredis";

/**
 * Server-side Lua commands.
 *
 * Every counter mutation is a single script so concurrent Lambda invocations
 * cannot double-fire a detector: the call that inserts the record reaching the
 * threshold deletes the window and reports the count; the next caller starts a
 * fresh window. Redis TIME is used inside the scripts so skew between
 * execution environments cannot move a window.
 */

const SLIDE_WINDOW = `
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local window = tonumber(ARGV[2])
local cutoff = now - window
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. string.format('%d', cutoff))
redis.call('ZADD', KEYS[1], now, ARGV[1])
local count = redis.call('ZCARD', KEYS[1])
redis.call('PEXPIRE', KEYS[1], window)
if count >= tonumber(ARGV[3]) then
  redis.call('DEL', KEYS[1])
end
return count
`;

const CLEAR_INDEX = `
local members = redis.call('SMEMBERS', KEYS[1])
for i = 1, #members do
  redis.call('DEL', members[i])
end
redis.call('DEL', KEYS[1])
return #members
`;

const INCR_COUNTER = `
local value = redis.call('INCRBY', KEYS[1], tonumber(ARGV[1]))
if value < 0 then
  redis.call('SET', KEYS[1], 0)
  value = 0
end
redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]))
return value
`;

const SET_IF_NEWER = `
local current = redis.call('GET', KEYS[1])
if current then
  local ok, decoded = pcall(cjson.decode, current)
  if ok and type(decoded) == 'table' and decoded.timestamp then
    if tonumber(decoded.timestamp) > tonumber(ARGV[2]) then
      return 0
    end
  end
end
redis.call('SET', KEYS[1], ARGV[1], 'PX', tonumber(ARGV[3]))
return 1
`;

const RATE_LIMIT = `
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return count
`;

interface ScriptedRedis extends Redis {
  slideWindow(key: string, member: string, windowMs: string, threshold: string): Promise<number>;
  clearIndex(key: string): Promise<number>;
  incrCounter(key: string, delta: string, ttlMs: string): Promise<number>;
  setIfNewer(key: string, json: string, timestampMs: string, ttlMs: string): Promise<number>;
  rateLimit(key: string, ttlMs: string): Promise<number>;
}

export class RedisScripts {
  private readonly scripted: ScriptedRedis;
  private readonly redis: Redis;

  constructor(redis: Redis) {
    this.redis = redis;
    redis.defineCommand("slideWindow", { numberOfKeys: 1, lua: SLIDE_WINDOW });
    redis.defineCommand("clearIndex", { numberOfKeys: 1, lua: CLEAR_INDEX });
    redis.defineCommand("incrCounter", { numberOfKeys: 1, lua: INCR_COUNTER });
    redis.defineCommand("setIfNewer", { numberOfKeys: 1, lua: SET_IF_NEWER });
    redis.defineCommand("rateLimit", { numberOfKeys: 1, lua: RATE_LIMIT });
    this.scripted = redis as ScriptedRedis;
  }

  /** Prune-add-count-expire; deletes the key when the threshold is reached. */
  async slideWindow(key: string, member: string, windowMs: number, threshold: number): Promise<number> {
    return this.scripted.slideWindow(key, member, String(windowMs), String(threshold));
  }

  /** Deletes every window key recorded in an index, then the index itself. */
  async clearIndex(key: string): Promise<number> {
    return this.scripted.clearIndex(key);
  }

  async incrementCounter(key: string, delta: number, ttlMs: number): Promise<number> {
    return this.scripted.incrCounter(key, String(delta), String(ttlMs));
  }

  /** Deterministic last-writer-wins-by-timestamp CAS for last-login records. */
  async setIfNewer(key: string, json: string, timestampMs: number, ttlMs: number): Promise<boolean> {
    return (await this.scripted.setIfNewer(key, json, String(timestampMs), String(ttlMs))) === 1;
  }

  async rateLimit(key: string, ttlMs: number): Promise<number> {
    return this.scripted.rateLimit(key, String(ttlMs));
  }

  /** Escape hatch for plain commands (GET/SET/DEL/MGET). */
  get client(): Redis {
    return this.redis;
  }
}
