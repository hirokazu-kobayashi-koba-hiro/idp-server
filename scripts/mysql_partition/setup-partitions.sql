-- =====================================================
-- MySQL パーティショニング設定スクリプト
--
-- 対象テーブル:
--   - statistics_daily_users (日単位パーティション, 90日保持)
--   - statistics_monthly_users (月単位パーティション, 13ヶ月保持)
--   - statistics_yearly_users (月単位パーティション, 60ヶ月保持)
--
-- 前提条件:
--   - MySQL 8.0以上
--   - Event Schedulerが有効 (SET GLOBAL event_scheduler = ON)
-- =====================================================

-- =====================================================
-- Event Scheduler有効化
-- =====================================================
SET GLOBAL event_scheduler = ON;

-- =====================================================
-- パーティション管理用Stored Procedures
-- =====================================================

DELIMITER //

-- -----------------------------------------------------
-- statistics_daily_users のパーティション作成（日単位）
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_daily_users_partition//
CREATE PROCEDURE create_daily_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_end DATE;
    DECLARE partition_exists INT DEFAULT 0;

    -- 日単位のパーティション名 (例: p20251217)
    SET partition_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET partition_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    -- パーティションが存在するか確認
    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_daily_users'
      AND PARTITION_NAME = partition_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_daily_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SELECT CONCAT('Created partition: ', partition_name) AS result;
    ELSE
        SELECT CONCAT('Partition already exists: ', partition_name) AS result;
    END IF;
END//

-- -----------------------------------------------------
-- statistics_monthly_users のパーティション作成（月単位）
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_monthly_users_partition//
CREATE PROCEDURE create_monthly_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_start DATE;
    DECLARE partition_end DATE;
    DECLARE partition_exists INT DEFAULT 0;

    SET partition_start = DATE_FORMAT(target_date, '%Y-%m-01');
    SET partition_end = DATE_ADD(partition_start, INTERVAL 1 MONTH);
    SET partition_name = CONCAT('p', DATE_FORMAT(partition_start, '%Y%m'));

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_monthly_users'
      AND PARTITION_NAME = partition_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_monthly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SELECT CONCAT('Created partition: ', partition_name) AS result;
    ELSE
        SELECT CONCAT('Partition already exists: ', partition_name) AS result;
    END IF;
END//

-- -----------------------------------------------------
-- statistics_yearly_users のパーティション作成（月単位）
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_yearly_users_partition//
CREATE PROCEDURE create_yearly_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_start DATE;
    DECLARE partition_end DATE;
    DECLARE partition_exists INT DEFAULT 0;

    SET partition_start = DATE_FORMAT(target_date, '%Y-%m-01');
    SET partition_end = DATE_ADD(partition_start, INTERVAL 1 MONTH);
    SET partition_name = CONCAT('p', DATE_FORMAT(partition_start, '%Y%m'));

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_yearly_users'
      AND PARTITION_NAME = partition_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_yearly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SELECT CONCAT('Created partition: ', partition_name) AS result;
    ELSE
        SELECT CONCAT('Partition already exists: ', partition_name) AS result;
    END IF;
END//

-- -----------------------------------------------------
-- 古いパーティションの削除（日単位）
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS drop_old_daily_users_partitions//
CREATE PROCEDURE drop_old_daily_users_partitions(IN retention_days INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_daily_users'
          AND PARTITION_NAME != 'p_future'
          AND PARTITION_DESCRIPTION != 'MAXVALUE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET cutoff_date = DATE_SUB(CURDATE(), INTERVAL retention_days DAY);

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_name, p_description;
        IF done THEN
            LEAVE read_loop;
        END IF;

        -- パーティションの終了日がカットオフより前なら削除
        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d') <= cutoff_date THEN
            SET @sql = CONCAT('ALTER TABLE statistics_daily_users DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('Dropped partition: ', p_name) AS result;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- -----------------------------------------------------
-- 古いパーティションの削除（月単位）
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS drop_old_monthly_users_partitions//
CREATE PROCEDURE drop_old_monthly_users_partitions(IN retention_months INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_monthly_users'
          AND PARTITION_NAME != 'p_future'
          AND PARTITION_DESCRIPTION != 'MAXVALUE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET cutoff_date = DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL retention_months MONTH);

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_name, p_description;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d') <= cutoff_date THEN
            SET @sql = CONCAT('ALTER TABLE statistics_monthly_users DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('Dropped partition: ', p_name) AS result;
        END IF;
    END LOOP;
    CLOSE cur;
END//

DROP PROCEDURE IF EXISTS drop_old_yearly_users_partitions//
CREATE PROCEDURE drop_old_yearly_users_partitions(IN retention_months INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_yearly_users'
          AND PARTITION_NAME != 'p_future'
          AND PARTITION_DESCRIPTION != 'MAXVALUE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET cutoff_date = DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL retention_months MONTH);

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_name, p_description;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d') <= cutoff_date THEN
            SET @sql = CONCAT('ALTER TABLE statistics_yearly_users DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('Dropped partition: ', p_name) AS result;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- -----------------------------------------------------
-- パーティション自動メンテナンス
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS maintain_statistics_partitions//
CREATE PROCEDURE maintain_statistics_partitions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE target_date DATE;

    -- statistics_daily_users: 今後90日分のパーティションを事前作成
    SET i = 0;
    WHILE i < 90 DO
        SET target_date = DATE_ADD(CURDATE(), INTERVAL i DAY);
        CALL create_daily_users_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- statistics_monthly_users: 今後3ヶ月分のパーティションを事前作成
    SET i = 0;
    WHILE i < 3 DO
        SET target_date = DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);
        CALL create_monthly_users_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- statistics_yearly_users: 今後3ヶ月分のパーティションを事前作成
    SET i = 0;
    WHILE i < 3 DO
        SET target_date = DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);
        CALL create_yearly_users_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- 古いパーティションを削除
    CALL drop_old_daily_users_partitions(90);      -- 90日保持
    CALL drop_old_monthly_users_partitions(13);    -- 13ヶ月保持
    CALL drop_old_yearly_users_partitions(60);     -- 60ヶ月保持

    SELECT 'Partition maintenance completed' AS result;
END//

DELIMITER ;

-- =====================================================
-- Event Scheduler設定
-- 毎日AM 3:00にパーティションメンテナンスを実行
-- =====================================================

DROP EVENT IF EXISTS evt_maintain_statistics_partitions;

CREATE EVENT evt_maintain_statistics_partitions
ON SCHEDULE EVERY 1 DAY
STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 3 HOUR)
ON COMPLETION PRESERVE
ENABLE
COMMENT 'Daily maintenance of statistics table partitions'
DO
    CALL maintain_statistics_partitions();

-- =====================================================
-- 確認クエリ
-- =====================================================

-- Event Schedulerの状態確認
-- SHOW VARIABLES LIKE 'event_scheduler';

-- 登録されたイベント確認
-- SHOW EVENTS;

-- パーティション状態確認
-- SELECT TABLE_NAME, PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
-- FROM information_schema.PARTITIONS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME LIKE 'statistics%'
-- ORDER BY TABLE_NAME, PARTITION_NAME;
