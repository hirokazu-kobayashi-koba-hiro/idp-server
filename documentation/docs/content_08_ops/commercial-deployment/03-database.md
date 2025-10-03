# PostgreSQL データベース設定

idp-server のマルチテナント環境で必要なPostgreSQL設定の手順と動作確認方法について説明します。

## 手順概要

1. **ユーザー作成**
   1. ControlPlane用のAdminユーザー作成（`idp_admin_user` - RLS BYPASS権限）
   2. アプリケーション用のアプリユーザー（`idp_app_user` - RLS適用）
2. **ユーザー権限確認**
3. **FlywayによるDDL適用**
4. **RLS動作確認**

---

## 🛡️ データベースユーザー設定

### 1. ユーザー作成

**作成するユーザー：**
- `idp_admin_user`: RLS BYPASS権限付き（テナント横断操作可能）
- `idp_app_user`: 通常権限（RLS適用、テナント分離）

**環境変数の設定**
※パスワードは変更してください。
```shell
export IDP_DB_ADMIN_USER=idp_admin_user
export IDP_DB_ADMIN_PASSWORD=idp_admin_user
export IDP_DB_APP_USER=idp_app_user
export IDP_DB_APP_PASSWORD=idp_app_user
export IDP_DB_HOST=localhost
export IDP_DB_PORT=5432
export IDP_DB_NAME=idpserver
```

**PostgreSQL認証設定（推奨）**
パスワード自動認証のため`.pgpass`ファイルを設定：
```shell
cat > ~/.pgpass << 'EOF'
localhost:5432:idpserver:idp_admin_user:idp_admin_user
localhost:5432:idpserver:idp_app_user:idp_app_user
EOF
chmod 600 ~/.pgpass
```

**フォーマット**: `hostname:port:database:username:password`

**スクリプトを使用してユーザーを作成します：**

```bash
./01-init-users.sh
```

### 2. ユーザー権限確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME -f ./libs/idp-server-database/postgresql/operation/select-user-role.sql
```

※ `.pgpass`設定済みの場合、パスワード入力は不要

**期待結果**:
```
    rolname     | rolsuper | rolbypassrls | rolconnlimit
----------------+----------+--------------+--------------
 idp_admin_user | f        | t            |           25
 idp_app_user   | f        | f            |           50
(2 rows)
```

- `idp_admin_user`: `rolbypassrls=t` でRLS BYPASS権限あり
- `idp_app_user`: `rolbypassrls=f` でRLS適用対象

### 3. 接続テスト

#### 管理用ユーザー接続確認

```shell 
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME -c "SELECT current_user, session_user;"
```

#### アプリケーション用ユーザー接続確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_APP_USER -d $IDP_DB_NAME -c "SELECT current_user, session_user;"
```
※ `.pgpass`設定済みの場合、パスワード入力は不要

**期待結果**:
```
# 管理用ユーザー
 current_user   | session_user
----------------+---------------
 idp_admin_user | idp_admin_user

# アプリケーション用ユーザー
 current_user | session_user
--------------+--------------
 idp_app_user | idp_app_user
```

---

## 🛠️ FlywayによるDDL適用

### スキーマ初期化・マイグレーション

```shell
DB_TYPE=postgresql ./gradlew flywayClean flywayMigrate
```

### マイグレーション状態確認

マイグレーション履歴確認

```bash
./gradlew flywayInfo
```

全てのStateが "Success" であることを確認

---

## 🔍 RLS設定確認

### 1. RLS有効テーブル確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME -f ./libs/idp-server-database/postgresql/operation/select-rls-table.sql
```

**期待結果**: 以下のテーブルが `rls_enabled=true` で表示される

### 2. RLSポリシー確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME -f ./libs/idp-server-database/postgresql/operation/select-rls-policy.sql
```

**期待結果**: テナント分離のポリシーが設定されていることを確認
```
 schemaname |       tablename        |      policyname       |        policy_condition
------------+------------------------+-----------------------+------------------------------
 public     | client_configuration   | tenant_isolation_policy | (tenant_id = current_setting('app.tenant_id'::text))
(29 rows)
```

全テーブルで `app.tenant_id` 設定値によるテナント分離が実装されていることを確認

---

## ✅ テナント分離動作確認

### 1. 管理者ユーザーでの確認

#### 管理用ユーザー接続確認

```shell 
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME -c "SELECT COUNT(*) FROM client_configuration;"
```

#### アプリケーション用ユーザー接続確認


```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_APP_USER -d $IDP_DB_NAME -c "SELECT COUNT(*) FROM client_configuration"
```

**期待結果**: アプリケーションユーザーはRLS適用のため、テナント設定に応じたデータのみアクセス可能

```sql
-- テナント設定なし
SELECT COUNT(*) FROM tenant_invitation;
-- 結果: count = 0 または ERROR: app.tenant_id is not set

-- テナント設定あり (例: テナントAのUUID設定)
SET app.tenant_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
SELECT COUNT(*) FROM tenant_invitation;
-- 結果: count = 3 (テナントAのデータのみ)

-- 異なるテナント設定 (例: テナントBのUUID設定)
SET app.tenant_id = 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff';
SELECT COUNT(*) FROM tenant_invitation;
-- 結果: count = 5 (テナントBのデータのみ)
```

### 3. 動作確認チェックポイント

- [ ] **管理者ユーザー**: テナント設定に関係なく全データにアクセス可能
- [ ] **アプリケーションユーザー**: テナントIDを変更すると、見えるデータが変わる
- [ ] **アプリケーションユーザー**: テナントID未設定時はエラーまたは0件
- [ ] **アプリケーションユーザー**: 他テナントのデータにアクセスできない

---

## 🔧 設定チェックリスト

### 必須設定項目

- [ ] **ユーザー作成完了**: `idp_admin_user`, `idp_app_user` 作成済み
- [ ] **権限設定完了**: 管理用(DDL) vs アプリケーション用(DML) 権限分離
- [ ] **接続テスト成功**: 各ユーザーで正常接続確認
- [ ] **Flyway実行完了**: `flywayClean` → `flywayMigrate` 正常完了
- [ ] **RLS確認完了**: 主要テーブルでRLS有効化確認
- [ ] **テナント分離確認**: 異なるテナントIDでデータ分離確認

### 接続状況確認

```sql
-- 現在の接続状況
SELECT
  datname,
  usename,
  client_addr,
  state,
  query_start
FROM pg_stat_activity
WHERE datname = 'idpserver'
ORDER BY query_start DESC;
```

### よくある問題の確認

```sql
-- ユーザー存在確認
SELECT usename, usesuper, usecreatedb
FROM pg_user
WHERE usename IN ('idp_admin_user', 'idp_app_user');

-- ユーザー権限確認
SELECT grantee, table_name, privilege_type
FROM information_schema.table_privileges
WHERE grantee IN ('idp_admin_user', 'idp_app_user')
ORDER BY grantee, table_name;
```

**期待結果**:

**ユーザー存在確認**:
```
   usename     | usesuper | usecreatedb
---------------+----------+-------------
 idp_admin_user| f        | f
 idp_app_user  | f        | f
(2 rows)
```

**ユーザー権限確認**: 両ユーザーが全テーブルに対してSELECT, INSERT, UPDATE, DELETE権限を持っていること
```
    grantee     |       table_name       | privilege_type
----------------+------------------------+----------------
 idp_admin_user | client_configuration   | SELECT
 idp_admin_user | client_configuration   | INSERT
 idp_admin_user | client_configuration   | UPDATE
 idp_admin_user | client_configuration   | DELETE
 idp_app_user   | client_configuration   | SELECT
 idp_app_user   | client_configuration   | INSERT
 (続く...)
```

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [初期設定・ユーザー・ロール](./04-initial-configuration.md)
- [検証・テストチェックリスト](./05-verification-checklist.md)
- [運用ガイダンス](./06-operational-guidance.md)