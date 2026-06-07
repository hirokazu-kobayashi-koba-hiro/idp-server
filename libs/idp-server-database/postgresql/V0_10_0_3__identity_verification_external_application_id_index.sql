/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- =====================================================
-- external_application_id index 追加
--
-- 開発 / ステージング環境では Flyway 適用でそのまま作成して問題ない。
--
-- 本番運用 (大量レコードがあるテーブル) では、通常の CREATE INDEX が
-- 書き込みブロックを引き起こすため、Flyway 適用前に
-- libs/idp-server-database/postgresql/operation/
--   identity-verification-external-application-id-backfill/create_index.sql
-- で CONCURRENTLY を先に実行しておくこと。`CREATE INDEX IF NOT EXISTS`
-- なので本ファイルは no-op となり安全。
-- =====================================================

-- UNIQUE: 外部 vendor 発行 ID は tenant 内で一意な前提。NULL は UNIQUE 制約の
-- 対象外 (PostgreSQL の標準挙動) なので、backfill 未完了の NULL 行は許容される。
CREATE UNIQUE INDEX IF NOT EXISTS idx_verification_external_application_id
    ON identity_verification_application (tenant_id, external_application_id);
