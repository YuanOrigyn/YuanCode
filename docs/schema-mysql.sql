-- YuanCode MySQL schema
-- The plugin creates these objects automatically. Table names are fixed.
-- Recommended: MySQL 8.0+ with utf8mb4.

CREATE TABLE IF NOT EXISTS yuancode_meta (
  meta_key VARCHAR(64) PRIMARY KEY,
  meta_value VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yuancode_usage (
  code VARCHAR(64) NOT NULL,
  global_used INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yuancode_redemptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claim_key VARCHAR(191) NOT NULL,
  code VARCHAR(64) NOT NULL,
  player_uuid CHAR(36) NOT NULL,
  player_name VARCHAR(32) NOT NULL,
  player_ip VARCHAR(64) NULL,
  period_key VARCHAR(32) NOT NULL,
  redeemed_at BIGINT NOT NULL,
  server_id VARCHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_yc_claim (claim_key),
  KEY ix_yc_code_player (code, player_uuid, redeemed_at),
  KEY ix_yc_code_ip (code, player_ip, redeemed_at),
  KEY ix_yc_time (redeemed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yuancode_pending (
  id BIGINT NOT NULL AUTO_INCREMENT,
  player_uuid CHAR(36) NOT NULL,
  code VARCHAR(64) NOT NULL,
  reward_json LONGTEXT NOT NULL,
  execute_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_yc_pending_due (execute_at),
  KEY ix_yc_pending_player (player_uuid, execute_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO yuancode_meta(meta_key, meta_value)
VALUES ('schema_version', '1');
