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
-- Issue #1550 follow-up: external_application_id を独立カラム化
--
-- 背景:
--   identity_verification_application.application_details (JSONB) 内の
--   外部システム発行 ID を ->> 演算子で検索していたが、GIN index は
--   ->> をサポートせず seq scan が走っていた。RLS + LEAKPROOF 制約で
--   @> 書き換えでも改善しなかった。
--
-- 対応:
--   外部システムが発行する申請 ID を独立カラム化する。検索キー名が
--   verification_type 設定 (callbackApplicationId) ごとに可変だが、
--   論理的には「外部申請 ID」一種類なので 1 カラムで表現できる。
--
-- index 追加は別ファイル (V0_10_0_3) に分離。本番運用では V0_10_0_3 を
-- 配置せず、CREATE INDEX CONCURRENTLY を runbook で手動実行する。
-- 既存データの backfill も本マイグレーション後に operation/ 配下で実施。
-- =====================================================

ALTER TABLE identity_verification_application
    ADD COLUMN external_application_id VARCHAR(255);
