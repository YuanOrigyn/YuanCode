-- YuanCode SQLite schema (default storage)
-- The plugin creates these objects automatically. This file documents the layout.

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS yuancode_meta (
  meta_key TEXT PRIMARY KEY,
  meta_value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS yuancode_usage (
  code TEXT PRIMARY KEY,
  global_used INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS yuancode_redemptions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  claim_key TEXT NOT NULL,
  code TEXT NOT NULL,
  player_uuid TEXT NOT NULL,
  player_name TEXT NOT NULL,
  player_ip TEXT,
  period_key TEXT NOT NULL,
  redeemed_at INTEGER NOT NULL,
  server_id TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_yc_claim
  ON yuancode_redemptions(claim_key);
CREATE INDEX IF NOT EXISTS ix_yc_code_player
  ON yuancode_redemptions(code, player_uuid, redeemed_at);
CREATE INDEX IF NOT EXISTS ix_yc_code_ip
  ON yuancode_redemptions(code, player_ip, redeemed_at);
CREATE INDEX IF NOT EXISTS ix_yc_time
  ON yuancode_redemptions(redeemed_at);

CREATE TABLE IF NOT EXISTS yuancode_pending (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  player_uuid TEXT NOT NULL,
  code TEXT NOT NULL,
  reward_json TEXT NOT NULL,
  execute_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_yc_pending_due
  ON yuancode_pending(execute_at);
CREATE INDEX IF NOT EXISTS ix_yc_pending_player
  ON yuancode_pending(player_uuid, execute_at);

INSERT OR IGNORE INTO yuancode_meta(meta_key, meta_value)
VALUES ('schema_version', '1');
