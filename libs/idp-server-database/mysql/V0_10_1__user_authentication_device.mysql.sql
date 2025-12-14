-- ============================================================================
-- V0_10_1__idp_user_authentication_devices.mysql.sql
-- Issue #964: Authentication device search query performance improvement
--
-- Summary:
--   Extract idp_user.authentication_devices (JSON) to a separate table
--   Enable fast search using PK (BTree index)
-- ============================================================================

-- ============================================================================
-- 1. Create idp_user_authentication_devices table
-- ============================================================================
-- id = device identifier (PK)
CREATE TABLE idp_user_authentication_devices (
    id                      CHAR(36)                           NOT NULL,
    tenant_id               CHAR(36)                           NOT NULL,
    user_id                 CHAR(36)                           NOT NULL,
    os                      VARCHAR(100),
    model                   VARCHAR(255),
    platform                VARCHAR(50),
    locale                  VARCHAR(50),
    app_name                VARCHAR(255),
    priority                INT DEFAULT 1,
    available_methods       JSON,
    notification_token      TEXT,
    notification_channel    VARCHAR(50),
    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for tenant + user lookup
CREATE INDEX idx_user_auth_device_tenant_user
    ON idp_user_authentication_devices (tenant_id, user_id);

-- Index for user_id lookup (list devices for a user)
CREATE INDEX idx_user_auth_device_user_id
    ON idp_user_authentication_devices (user_id);

-- ============================================================================
-- 2. Migrate existing data
-- ============================================================================
-- MySQL does not have jsonb_array_elements, so use stored procedure
DELIMITER //

CREATE PROCEDURE migrate_authentication_devices()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_tenant_id CHAR(36);
    DECLARE v_user_id CHAR(36);
    DECLARE v_auth_devices JSON;
    DECLARE v_created_at DATETIME;
    DECLARE v_updated_at DATETIME;
    DECLARE v_device JSON;
    DECLARE v_device_count INT;
    DECLARE v_idx INT;

    DECLARE cur CURSOR FOR
        SELECT tenant_id, id, authentication_devices, created_at, updated_at
        FROM idp_user
        WHERE authentication_devices IS NOT NULL
        AND JSON_LENGTH(authentication_devices) > 0;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_tenant_id, v_user_id, v_auth_devices, v_created_at, v_updated_at;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET v_device_count = JSON_LENGTH(v_auth_devices);
        SET v_idx = 0;

        WHILE v_idx < v_device_count DO
            SET v_device = JSON_EXTRACT(v_auth_devices, CONCAT('$[', v_idx, ']'));

            INSERT INTO idp_user_authentication_devices (
                id,
                tenant_id,
                user_id,
                os,
                model,
                platform,
                locale,
                app_name,
                priority,
                available_methods,
                notification_token,
                notification_channel,
                created_at,
                updated_at
            ) VALUES (
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.id')),
                v_tenant_id,
                v_user_id,
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.os')),
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.model')),
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.platform')),
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.locale')),
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.app_name')),
                COALESCE(JSON_EXTRACT(v_device, '$.priority'), 1),
                COALESCE(JSON_EXTRACT(v_device, '$.available_methods'), JSON_ARRAY()),
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.notification_token')),
                JSON_UNQUOTE(JSON_EXTRACT(v_device, '$.notification_channel')),
                v_created_at,
                v_updated_at
            );

            SET v_idx = v_idx + 1;
        END WHILE;
    END LOOP;

    CLOSE cur;
END //

DELIMITER ;

-- Execute migration
CALL migrate_authentication_devices();

-- Drop procedure after migration
DROP PROCEDURE IF EXISTS migrate_authentication_devices;
