-- =====================================================
-- MySQL パーティションテーブルへのマイグレーション
--
-- 既存のテーブルをパーティションテーブルに変換する
-- 注意: 本番環境では十分なテストを行ってから実行すること
--
-- パーティション単位:
--   - statistics_daily_users: 日単位 (90日保持)
--   - statistics_monthly_users: 月単位 (13ヶ月保持)
--   - statistics_yearly_users: 月単位 (60ヶ月保持)
-- =====================================================

-- =====================================================
-- Step 1: 既存データのバックアップ
-- =====================================================

-- バックアップテーブル作成
CREATE TABLE IF NOT EXISTS statistics_daily_users_backup AS
SELECT * FROM statistics_daily_users;

CREATE TABLE IF NOT EXISTS statistics_monthly_users_backup AS
SELECT * FROM statistics_monthly_users;

CREATE TABLE IF NOT EXISTS statistics_yearly_users_backup AS
SELECT * FROM statistics_yearly_users;

-- =====================================================
-- Step 2: 既存テーブルの削除と再作成（パーティション付き）
-- =====================================================

-- statistics_daily_users (日単位パーティション)
DROP TABLE IF EXISTS statistics_daily_users;

CREATE TABLE statistics_daily_users (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, user_id),
    KEY idx_statistics_daily_users_tenant_date (tenant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily active users tracking with daily partitioning (90-day retention)'
PARTITION BY RANGE COLUMNS(stat_date) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- statistics_monthly_users (月単位パーティション)
DROP TABLE IF EXISTS statistics_monthly_users;

CREATE TABLE statistics_monthly_users (
    tenant_id CHAR(36) NOT NULL,
    stat_month DATE NOT NULL COMMENT 'First day of month (e.g., 2025-01-01)',
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_month, user_id),
    KEY idx_statistics_monthly_users_tenant_month (tenant_id, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly active users tracking with monthly partitioning (13-month retention)'
PARTITION BY RANGE COLUMNS(stat_month) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- statistics_yearly_users (月単位パーティション)
DROP TABLE IF EXISTS statistics_yearly_users;

CREATE TABLE statistics_yearly_users (
    tenant_id CHAR(36) NOT NULL,
    stat_year DATE NOT NULL COMMENT 'Fiscal year start date (e.g., 2025-04-01 for April fiscal year)',
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_year, user_id),
    KEY idx_statistics_yearly_users_tenant_year (tenant_id, stat_year),
    KEY idx_statistics_yearly_users_last_used (tenant_id, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Yearly active users tracking with monthly partitioning for fiscal year support (60-month retention)'
PARTITION BY RANGE COLUMNS(stat_year) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- =====================================================
-- Step 3: 初期パーティション作成
-- =====================================================

DELIMITER //

DROP PROCEDURE IF EXISTS create_initial_partitions//
CREATE PROCEDURE create_initial_partitions()
BEGIN
    DECLARE i INT;
    DECLARE target_date DATE;
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_end DATE;

    -- statistics_daily_users: 過去90日 + 今後90日分の日単位パーティション
    SET i = -90;
    WHILE i < 90 DO
        SET target_date = DATE_ADD(CURDATE(), INTERVAL i DAY);
        SET partition_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
        SET partition_end = DATE_ADD(target_date, INTERVAL 1 DAY);

        SET @sql = CONCAT(
            'ALTER TABLE statistics_daily_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET i = i + 1;
    END WHILE;

    -- statistics_monthly_users: 過去13ヶ月 + 今後3ヶ月分の月単位パーティション
    SET i = -13;
    WHILE i < 3 DO
        SET target_date = DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);
        SET partition_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m'));
        SET partition_end = DATE_ADD(target_date, INTERVAL 1 MONTH);

        SET @sql = CONCAT(
            'ALTER TABLE statistics_monthly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET i = i + 1;
    END WHILE;

    -- statistics_yearly_users: 過去60ヶ月 + 今後3ヶ月分の月単位パーティション
    SET i = -60;
    WHILE i < 3 DO
        SET target_date = DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);
        SET partition_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m'));
        SET partition_end = DATE_ADD(target_date, INTERVAL 1 MONTH);

        SET @sql = CONCAT(
            'ALTER TABLE statistics_yearly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET i = i + 1;
    END WHILE;

    SELECT 'Initial partitions created' AS result;
END//

DELIMITER ;

CALL create_initial_partitions();
DROP PROCEDURE IF EXISTS create_initial_partitions;

-- =====================================================
-- Step 4: バックアップからデータ復元
-- =====================================================

INSERT INTO statistics_daily_users
SELECT * FROM statistics_daily_users_backup;

INSERT INTO statistics_monthly_users
SELECT * FROM statistics_monthly_users_backup;

INSERT INTO statistics_yearly_users
SELECT * FROM statistics_yearly_users_backup;

-- =====================================================
-- Step 5: 確認
-- =====================================================

SELECT 'statistics_daily_users partitions (showing first 10):' AS info;
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'statistics_daily_users'
ORDER BY PARTITION_NAME
LIMIT 10;

SELECT 'statistics_monthly_users partitions:' AS info;
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'statistics_monthly_users'
ORDER BY PARTITION_NAME;

SELECT 'statistics_yearly_users partitions (showing first 10):' AS info;
SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'statistics_yearly_users'
ORDER BY PARTITION_NAME
LIMIT 10;

-- パーティション数の確認
SELECT TABLE_NAME, COUNT(*) AS partition_count
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'statistics%_users'
GROUP BY TABLE_NAME;

-- =====================================================
-- Step 6: バックアップテーブルの削除（確認後に実行）
-- =====================================================

-- 確認が完了したら以下のコメントを外して実行
-- DROP TABLE IF EXISTS statistics_daily_users_backup;
-- DROP TABLE IF EXISTS statistics_monthly_users_backup;
-- DROP TABLE IF EXISTS statistics_yearly_users_backup;

SELECT 'Migration completed. Verify data and then drop backup tables.' AS result;
