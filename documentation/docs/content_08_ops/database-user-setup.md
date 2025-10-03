# PostgreSQL ユーザー設定ガイド - idp-server初期構築

`idp-server`の初期構築で必要なPostgreSQLユーザー設定の完全ガイドです。Row Level Security (RLS) の理解に基づいた3段階のユーザー設計を提供します。

## 目次

1. [RLS (Row Level Security) 理解](#rls-row-level-security-理解)
2. [idp-serverで必要な3種類のユーザー](#idp-serverで必要な3種類のユーザー)
3. [各ユーザーの使い分け](#各ユーザーの使い分け)
4. [初期構築手順](#初期構築手順)
5. [セキュリティ考慮事項](#セキュリティ考慮事項)
6. [運用チェックリスト](#運用チェックリスト)

---

## RLS (Row Level Security) 理解

[PostgreSQL公式ドキュメント - 行セキュリティポリシー](https://www.postgresql.jp/docs/15/ddl-rowsecurity.html)

### RLSの動作原理

- **RLS有効**: テーブルごとにポリシーでアクセス制御
- **RLS回避**: `BYPASSRLS`権限またはスーパーユーザーがポリシーを無視
- **テーブル所有者**: デフォルトでRLSをバイパス

### PostgreSQLのRLS回避条件

以下のユーザーがRLSをバイパスできます：
- スーパーユーザー
- `BYPASSRLS`属性を持つロール
- テーブルの所有者（デフォルト）

---

## idp-serverで必要な3種類のユーザー

### 1. データベース所有者 (idpserver)

**用途**: DDL実行、テーブル作成、マイグレーション実行
**特徴**: テーブル所有者のためRLSを自動的にバイパス

```sql
-- =============================================================================
-- データベース所有者・スキーマ管理者
-- =============================================================================

CREATE USER idpserver WITH PASSWORD 'secure_owner_password';
CREATE DATABASE idpserver OWNER idpserver;

-- スキーマ所有権設定
ALTER SCHEMA public OWNER TO idpserver;

-- マイグレーション実行時はこのユーザーで接続
-- Flyway等のマイグレーションツールが使用
```

### 2. RLS超越管理者ユーザー (idp_admin_user)

**用途**: 全テナント横断操作、初期データ投入、システム管理API
**特徴**: `BYPASSRLS`権限でRLS制約を無視

**情報源**: `/libs/idp-server-database/postgresql/operation/admin_user.sql`

```bash
# スーパーユーザーで実行
sudo -u postgres psql -d idpserver -f libs/idp-server-database/postgresql/operation/admin_user.sql
```

### 3. 通常アプリケーションユーザー (idp_app_user)

**用途**: 通常のアプリケーション操作（テナント分離された環境）
**特徴**: RLS制約に従う、テナント境界を越えられない

**情報源**: `/libs/idp-server-database/postgresql/operation/app_user.sql`

```sql
-- =============================================================================
-- 通常アプリケーションユーザー（RLS制約下で動作）
-- =============================================================================

CREATE USER idp_app_user WITH PASSWORD 'secure_app_password';
-- 注意: BYPASSRLS権限は付与しない

-- 基本権限付与
GRANT CONNECT ON DATABASE idpserver TO idp_app_user;
GRANT USAGE ON SCHEMA public TO idp_app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO idp_app_user;

-- 将来のテーブル権限
ALTER DEFAULT PRIVILEGES FOR ROLE idpserver IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idp_app_user;
ALTER DEFAULT PRIVILEGES FOR ROLE idpserver IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO idp_app_user;
```

---

## 各ユーザーの使い分け

### データベース所有者 (idpserver)

```bash
# 使用タイミング
- Flyway migration実行時
- DDL変更時（テーブル作成・変更・削除）
- スキーマ更新時

# 使用例
java -jar flyway.jar -user=idpserver -password=xxx migrate
```

### RLS超越管理者 (idp_admin_user)

```bash
# 使用タイミング
- 初期システムテナント作成
- 全テナント横断データ操作
- システム管理API
- トラブル調査・データ修正

# 使用例（初期データ投入）
psql -U idp_admin_user -d idpserver -c "
-- 全テナントにアクセス可能（RLS無視）
INSERT INTO tenant (id, name, type, domain, authorization_provider, database_type)
VALUES ('admin-tenant-id', 'System Admin', 'ADMIN', 'admin.domain.com', 'internal', 'postgresql');

INSERT INTO idp_user (id, tenant_id, provider_id, name, email, status)
VALUES ('admin-user-id', 'admin-tenant-id', 'internal', 'System Admin', 'admin@domain.com', 'ACTIVE');
"
```

### 通常アプリケーションユーザー (idp_app_user)

```bash
# 使用タイミング
- 通常のアプリケーション稼働時
- エンドユーザーからのリクエスト処理
- テナント分離された操作

# 使用例（アプリケーション実行時）
psql -U idp_app_user -d idpserver -c "
-- テナントコンテキスト設定
SET app.tenant_id = 'target-tenant-id';

-- この後のクエリは自動的にテナント分離される
SELECT * FROM idp_user;  -- 指定テナントのユーザーのみ取得
SELECT * FROM client_configuration;  -- 指定テナントのクライアントのみ取得
"
```

---

## 初期構築手順

### Step 1: PostgreSQL基本設定

```sql
-- PostgreSQLスーパーユーザーで実行
-- 1. データベース作成
CREATE USER idpserver WITH PASSWORD 'secure_owner_password';
CREATE DATABASE idpserver OWNER idpserver;

-- 2. 基本設定
\c idpserver
ALTER SCHEMA public OWNER TO idpserver;
```

### Step 2: アプリケーションユーザー作成

```sql
-- スーパーユーザーで実行（BYPASSRLSのため）
CREATE USER idp_admin_user WITH
    PASSWORD 'secure_admin_password'
    BYPASSRLS;

CREATE USER idp_app_user WITH PASSWORD 'secure_app_password';

-- 権限付与（前述の詳細設定を実行）
```

### Step 3: スキーマ初期化

```bash
# データベース所有者でマイグレーション実行
SPRING_DATASOURCE_USERNAME=idpserver \
SPRING_DATASOURCE_PASSWORD=secure_owner_password \
java -jar idp-server.jar

# または Flyway直接実行
flyway -user=idpserver -password=secure_owner_password migrate
```

### Step 4: Docker Compose統合

#### 自動初期化用ファイル配置

Docker Composeでの自動初期化に必要なファイルが作成済みです：

```bash
# ファイル構成確認
ls -la libs/idp-server-database/postgresql/operation/
# init_admin_user.sh    - Docker用初期化スクリプト
# admin_user.sql        - RLS超越管理者ユーザー作成
# app_user.sql         - 通常アプリケーションユーザー作成
```

#### Docker Compose設定例

**情報源**: `/docker-compose.example.yml`, `.env.example`

```bash
# 環境変数設定
cp .env.example .env
# パスワードを安全な値に変更してください

# Docker Compose起動
docker-compose -f docker-compose.example.yml up -d

# 初期化確認
docker-compose logs postgresql
```

#### 環境変数設定

```bash
# 安全なパスワード生成
export POSTGRES_PASSWORD=$(openssl rand -base64 32)
export IDP_ADMIN_PASSWORD=$(openssl rand -base64 32)
export DB_OWNER_PASSWORD=$(openssl rand -base64 32)
export DB_APP_PASSWORD=$(openssl rand -base64 32)
```

---

## セキュリティ考慮事項

### RLS超越権限の厳格管理

```sql
-- BYPASSRLS権限の確認
SELECT rolname, rolbypassrls FROM pg_roles WHERE rolbypassrls = true;

-- 権限変更（必要に応じて）
ALTER USER idp_admin_user NOBYPASSRLS;  -- 権限剥奪
ALTER USER idp_admin_user BYPASSRLS;    -- 権限再付与（スーパーユーザーのみ可能）
```

### 接続制限・監査

```sql
-- 接続数制限
ALTER USER idp_admin_user CONNECTION LIMIT 5;  -- 管理者用は少なめ
ALTER USER idp_app_user CONNECTION LIMIT 50;   -- アプリケーション用

-- 監査ログ設定（postgresql.conf）
log_statement = 'mod'  -- DML操作をログ
log_connections = on
log_disconnections = on
```

### パスワードセキュリティ

```bash
# 安全なパスワード生成例
openssl rand -base64 32

# 環境変数での管理推奨
export DB_OWNER_PASSWORD=$(openssl rand -base64 32)
export DB_ADMIN_PASSWORD=$(openssl rand -base64 32)
export DB_APP_PASSWORD=$(openssl rand -base64 32)
```

---

## 運用チェックリスト

### 初期構築完了確認

- [ ] 3種類のユーザーが正しく作成されている
- [ ] RLS超越権限が適切に設定されている
- [ ] テーブル所有権が正しく設定されている
- [ ] RLSポリシーが有効化されている
- [ ] 各ユーザーで接続・操作確認済み

### セキュリティ確認

- [ ] パスワードが安全に設定されている
- [ ] 不要な権限が付与されていない
- [ ] 接続制限が適切に設定されている
- [ ] 監査ログが有効化されている

### 動作確認コマンド

```bash
# 1. 全ユーザーの接続確認
psql -U idpserver -d idpserver -c "SELECT current_user;"
psql -U idp_admin_user -d idpserver -c "SELECT current_user;"
psql -U idp_app_user -d idpserver -c "SELECT current_user;"

# 2. RLS動作確認
psql -U idp_app_user -d idpserver -c "
SET app.tenant_id = 'test-tenant-id';
SELECT count(*) FROM idp_user;
"

# 3. BYPASSRLS確認
psql -U idp_admin_user -d idpserver -c "
SELECT count(*) FROM idp_user;  -- 全テナントのユーザー数を表示
"

# 4. 権限確認
psql -U postgres -d idpserver -c "
SELECT
    r.rolname,
    r.rolsuper,
    r.rolbypassrls,
    r.rolconnlimit
FROM pg_roles r
WHERE r.rolname IN ('idpserver', 'idp_admin_user', 'idp_app_user')
ORDER BY r.rolname;
"
```

### トラブルシューティング

#### よくある問題と対処法

**1. BYPASSRLS権限が設定できない**
```bash
# 症状: "must be superuser to create BYPASSRLS users"
# 対処: スーパーユーザーで実行する必要がある
sudo -u postgres createuser idp_admin_user --bypassrls
```

**2. RLSポリシーが効かない**
```bash
# 症状: テナント分離されない
# 確認: テーブル所有者またはBYPASSRLS権限を持っている可能性
SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname = current_user;
```

**3. 権限不足エラー**
```bash
# 症状: "permission denied for table xxx"
# 対処: GRANT文の再実行
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO idp_app_user;
```

---

## まとめ

このガイドに従ってPostgreSQLユーザー設定を行うことで、idp-serverの安全で適切な初期構築が可能になります。

### 重要なポイント

1. **3段階のユーザー設計**: DDL用・管理用・アプリケーション用の明確な分離
2. **RLS理解に基づく設計**: PostgreSQLのセキュリティ機能を適切に活用
3. **実装確認済み**: 既存のスキーマと実装に基づいた正確な手順

**次のステップ**: [アプリケーション設定](./deployment.md#アプリケーション設定)でデータソース設定を行ってください。