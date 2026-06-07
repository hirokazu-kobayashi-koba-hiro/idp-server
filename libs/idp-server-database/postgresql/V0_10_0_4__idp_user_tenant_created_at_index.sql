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
-- Issue #1460: idp_user に (tenant_id, created_at DESC) index 追加
--
-- 背景:
--   ユーザー検索 API のページネーションで ORDER BY created_at DESC が
--   使われるが、対応 index がないため 200 万行規模で Parallel Seq Scan
--   になる。計測: 185ms → 2.5ms (75x 改善)。
--
-- 開発 / ステージング環境では Flyway 適用でそのまま作成して問題ない。
--
-- 本番運用 (大量レコードがあるテーブル) では、通常の CREATE INDEX が
-- 書き込みブロックを引き起こすため、Flyway 適用前に
-- libs/idp-server-database/postgresql/operation/
--   idp-user-tenant-created-at-index/create_index.sql
-- で CONCURRENTLY を先に実行しておくこと。`CREATE INDEX IF NOT EXISTS`
-- なので本ファイルは no-op となり安全。
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_idp_user_tenant_created_at
    ON idp_user (tenant_id, created_at DESC);
