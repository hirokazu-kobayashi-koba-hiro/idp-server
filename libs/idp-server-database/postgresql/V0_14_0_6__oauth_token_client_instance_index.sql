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
-- oauth_token に (tenant_id, client_instance_id) の部分 index を追加 (Issue #1521)
--
-- 背景:
--   Client Instance を失効・削除したとき、そのインスタンスに発行したトークンを
--   client_instance_id で引いて削除する。index が無いとテナントのトークンを
--   全件走査する。client_instance_id を持つのは registered_instance_key で
--   発行したトークンだけなので、部分 index にして大きさを抑える。
--
-- 開発 / ステージング環境では Flyway 適用でそのまま作成して問題ない。
--
-- 本番運用 (oauth_token に大量のレコードがある) では、通常の CREATE INDEX が
-- 書き込みブロックを引き起こすため、Flyway 適用前に
-- libs/idp-server-database/postgresql/operation/
--   oauth-token-client-instance-index/create_index.sql
-- で CONCURRENTLY を先に実行しておくこと。`CREATE INDEX IF NOT EXISTS`
-- なので本ファイルは no-op となり安全。
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_oauth_token_client_instance
    ON oauth_token (tenant_id, client_instance_id)
    WHERE client_instance_id IS NOT NULL;
