# データベースマイグレーション戦略

idp-server のデータベーススキーマ変更を安全に実施するためのマイグレーション戦略と運用ガイドライン。

---

## 📌 基本方針

### バージョン管理の原則

**アプリケーションバージョンとマイグレーションバージョンを一致させる**

| アプリバージョン | マイグレーションバージョン | 説明 |
|----------------|-------------------------|------|
| v0.9.0 | V0_9_0__init_lib.sql | 初期DDL |
| v1.0.1 | V1_0_1__webauthn.sql | WebAuthn機能追加 |
| v1.1.0 | V1_1_0__*.sql | 次期メジャー機能 |

**デプロイ順序**:
```
1. マイグレーション実行（Flyway）
   ↓
2. アプリケーションデプロイ
```

**重要**: マイグレーション完了前にアプリケーションを起動しない

---

## 🔧 マイグレーション作成ガイド

### ファイル命名規則

```
V{major}_{minor}_{patch}__{description}.sql
```

**例**:
- `V1_0_2__add_user_email_verified.sql`
- `V1_1_0__add_identity_verification_tables.sql`
- `V1_1_1__add_index_user_email.sql`

**ルール**:
- バージョン番号は昇順（Flywayが自動検出）
- 説明は英語スネークケース
- PostgreSQL用: `libs/idp-server-database/postgresql/`
- MySQL用: `libs/idp-server-database/mysql/`

---

## ✅ 互換性ルール

### 安全な変更（後方互換あり）

#### カラム追加（DEFAULT値付き）
```sql
-- ✅ 旧バージョンのアプリも動作可能
ALTER TABLE idp_user ADD COLUMN email_verified BOOLEAN DEFAULT false;
```

#### テーブル追加
```sql
-- ✅ 旧バージョンは新テーブルを使わないので問題なし
CREATE TABLE new_feature (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ...
);
```

#### インデックス追加
```sql
-- ✅ パフォーマンス改善のみ、アプリケーションに影響なし
CREATE INDEX idx_user_email ON idp_user(email);
```

---

### ⚠️ 注意が必要な変更

#### NOT NULL制約追加
```sql
-- ⚠️ 2段階で実施（DEFAULT値を先に設定）
-- Phase 1: カラム追加（NULL許容 + DEFAULT）
ALTER TABLE idp_user ADD COLUMN required_field VARCHAR(255) DEFAULT 'default_value';

-- Phase 2: 既存データ補填後、NOT NULL制約追加
UPDATE idp_user SET required_field = 'default_value' WHERE required_field IS NULL;
ALTER TABLE idp_user ALTER COLUMN required_field SET NOT NULL;
```

#### 大量データ更新
```sql
-- ⚠️ 1万件以上ならバッチ処理
-- 悪い例: 全件を1トランザクションで更新（ロック競合）
-- UPDATE idp_user SET email_verified = (attributes->>'email_verified')::boolean;

-- 良い例: バッチ処理（1000件ずつ）
DO $$
DECLARE
  updated_count INT;
BEGIN
  LOOP
    UPDATE idp_user
    SET email_verified = (attributes->>'email_verified')::boolean
    WHERE id IN (
      SELECT id FROM idp_user
      WHERE email_verified IS NULL
        AND attributes->>'email_verified' IS NOT NULL
      LIMIT 1000
    );

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    EXIT WHEN updated_count = 0;

    RAISE NOTICE 'Updated % rows', updated_count;
    COMMIT;
  END LOOP;
END $$;
```

---

### ❌ 危険な変更（段階的実施必須）

#### カラム削除
```sql
-- ❌ 即座に削除すると旧バージョンのアプリが壊れる
-- ALTER TABLE idp_user DROP COLUMN old_column;

-- ✅ Expand-Contractパターンで3段階実施（後述）
```

#### 型変更
```sql
-- ❌ 即座に変更すると旧バージョンで型エラー
-- ALTER TABLE idp_user ALTER COLUMN age TYPE VARCHAR;

-- ✅ Expand-Contractパターンで実施
```

#### テーブル削除
```sql
-- ❌ 旧バージョンがテーブルを参照している場合エラー
-- DROP TABLE old_table;

-- ✅ 次バージョンで誰も使わないことを確認してから削除
```

---

## 🔄 Expand-Contractパターン

**破壊的変更（カラム削除・型変更）は3段階で実施**

### 例: カラム名変更（email → user_email）

#### Phase 1: Expand（v1.0 → v1.1）
```sql
-- V1_1_0__expand_rename_email.sql
-- 新カラム追加、既存データをコピー
ALTER TABLE idp_user ADD COLUMN user_email VARCHAR(255);
UPDATE idp_user SET user_email = email WHERE user_email IS NULL;
CREATE INDEX idx_user_email ON idp_user(user_email);
```

**アプリv1.1デプロイ**: 新カラム（user_email）を使用するように修正

#### Phase 2: Migration Period（v1.1運用）
- 旧カラム（email）は残したまま
- アプリv1.1は新カラム（user_email）のみ使用
- 旧バージョンが完全に停止するまで待機

#### Phase 3: Contract（v1.2）
```sql
-- V1_2_0__contract_remove_old_email.sql
-- 旧カラム削除
ALTER TABLE idp_user DROP COLUMN email;
```

---

## 🚨 マイグレーション失敗時の対処

### 失敗検出
```bash
cd libs/idp-server-database
./gradlew flywayInfo
```

**出力例**（失敗時）:
```
+------------+---------+---------------------+------+---------------------+---------+
| Category   | Version | Description         | Type | Installed On        | State   |
+------------+---------+---------------------+------+---------------------+---------+
| Versioned  | 1.0.1   | webauthn            | SQL  | 2024-01-15 10:00:00 | Success |
| Versioned  | 1.0.2   | add email verified  | SQL  | 2024-01-15 10:05:00 | Failed  |
+------------+---------+---------------------+------+---------------------+---------+
```

### 修復手順

#### 1. 失敗原因を特定
```bash
# Flywayログ確認
./gradlew flywayInfo

# PostgreSQLログ確認
psql -h $IDP_DB_HOST -U idp_admin_user -d idpserver
```

#### 2. 手動でDDL修正（必要な場合）
```bash
# 失敗したDDLを手動実行・修正
psql -h $IDP_DB_HOST -U idp_admin_user -d idpserver

# 例: カラムが既に存在していた場合
# ALTER TABLE idp_user DROP COLUMN email_verified;  -- 手動削除
```

#### 3. Flyway履歴を修復
```bash
# 失敗状態をクリア（flyway_schema_historyテーブルを修正）
./gradlew flywayRepair
```

#### 4. マイグレーション再実行
```bash
./gradlew flywayMigrate
```

---

## 📋 デプロイ前チェックリスト

### マイグレーション作成時
- [ ] ファイル命名規則に従っている（`V{major}_{minor}_{patch}__{description}.sql`）
- [ ] PostgreSQL/MySQL両方のSQLファイルを作成
- [ ] 後方互換性を確認（カラム追加にDEFAULT値を設定）
- [ ] 大量データ更新はバッチ処理で実装
- [ ] ステージング環境で実行時間を計測

### デプロイ前
- [ ] リリース資材にマイグレーションファイルが含まれている
- [ ] アプリバージョンとマイグレーションバージョンが一致
- [ ] ステージング環境で事前検証済み
- [ ] ロールバック手順を確認（手動DDLによる復旧）

### デプロイ後
- [ ] `./gradlew flywayInfo` で全て Success
- [ ] アプリケーションの起動成功
- [ ] 主要機能の動作確認（OAuth/OIDC フロー）

---

## 🔗 関連ドキュメント

- [データベース設定](./03-database.md) - RLS設定、ユーザー作成
- [デプロイ概要](./00-overview.md) - デプロイ手順全体
- [運用ガイダンス](./05-operational-guidance.md) - 監視・トラブルシューティング