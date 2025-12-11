# PostgreSQL データベース設定

idp-server のマルチテナント環境で必要なPostgreSQL設定の手順と動作確認方法について説明します。

---

## 1. 構築手順概要

データベースを構築するには、以下の順序で設定を行います。

```
┌─────────────────────────────────────────────────────────────────┐
│  1.1 PostgreSQL設定                                             │
│  ─────────────────────────────────────────────────────────────  │
│  • shared_preload_libraries に pg_cron を追加                   │
│  • cron.database_name = 'postgres' を設定                       │
│  • PostgreSQL再起動が必要                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1.2 DB_OWNER ユーザー作成                                       │
│  ─────────────────────────────────────────────────────────────  │
│  • idp (DB_OWNER) - Flyway実行、pg_cronジョブ実行                │
│  ※ 拡張への権限付与に必要なため先に作成                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1.3 拡張インストール・権限設定                                   │
│  ─────────────────────────────────────────────────────────────  │
│  • pg_cron 拡張（postgres DB、クロスデータベースモード）          │
│  • pg_partman 拡張（idpserver DB、partmanスキーマ）               │
│  • idp ユーザーへの権限付与（cron, partman スキーマ）             │
│  ※ Flywayが partman.create_parent() を使うため必須               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1.4 アプリケーションユーザー作成                                 │
│  ─────────────────────────────────────────────────────────────  │
│  • idp_admin_user - 管理API用（BYPASSRLS）                       │
│  • idp_app_user - アプリケーション用（RLS適用）                   │
│  ※ Flyway前でも後でもOK（デフォルト権限で自動付与）               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1.5 Flywayマイグレーション                                      │
│  ─────────────────────────────────────────────────────────────  │
│  • テーブル作成、partman.create_parent() でパーティション設定     │
│  • archive スキーマ・関数作成                                    │
│  • RLSポリシー設定                                               │
│  ※ partman 拡張が必要                                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1.6 pg_cronジョブ登録                                           │
│  ─────────────────────────────────────────────────────────────  │
│  • partman-maintenance（partman.run_maintenance_proc()）        │
│  • archive-processing（archive.process_archive()）              │
│  ※ Flywayでテーブル・関数が作成された後に登録                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1.7 動作確認                                                    │
│  ─────────────────────────────────────────────────────────────  │
│  • ユーザー権限、RLS、パーティション、ジョブの確認                │
└─────────────────────────────────────────────────────────────────┘
```

**重要**: 1.2（idp作成）→ 1.3（拡張・権限付与）→ 1.5（Flyway）→ 1.6（pg_cronジョブ）の順序は厳守してください。
- idp への権限付与のため、拡張インストール前に idp ユーザーが必要
- Flyway DDL が `partman.create_parent()` を呼び出すため、pg_partman 拡張が先に必要
- pg_cron ジョブが `archive.*` 関数を参照するため、Flyway 実行後にジョブ登録
- 1.4（アプリケーションユーザー）は 1.5（Flyway）の前後どちらでも可（ローカルDocker環境と同じ順序）

---

## 2. 設定手順

### 2.1 PostgreSQL設定

#### postgresql.conf の設定

パーティショニング機能には以下の設定が必要です：

```
shared_preload_libraries = 'pg_cron'
cron.database_name = 'postgres'
```

**Note**: `pg_partman_bgw`（pg_partman のバックグラウンドワーカー）は使用せず、pg_cron でパーティション管理をスケジュール実行しています。

**設定後、PostgreSQLの再起動が必要です。**

#### 設定確認

```sql
SHOW shared_preload_libraries;
SHOW cron.database_name;
```

**期待結果**:
```
 shared_preload_libraries
--------------------------
 pg_stat_statements,pg_cron

 cron.database_name
--------------------
 postgres
```

---

### 2.2 DB_OWNER ユーザー作成

#### 2.2.1 環境変数の設定

※パスワードは変更してください。

```shell
export IDP_DB_HOST=localhost
export IDP_DB_PORT=5432
export IDP_DB_NAME=idpserver
export DB_OWNER_USER=idp
export DB_OWNER_PASSWORD=<password>
```

#### 2.2.2 idp ユーザー作成

**重要**: BYPASSRLS権限を持つユーザーを作成するには、スーパーユーザー権限が必要です。

- **オンプレミス環境**: PostgreSQLスーパーユーザー（例: `postgres`）で実行
- **AWS RDS環境**: マスターユーザー（`rds_superuser`ロールを持つユーザー）で実行

```sql
-- superuser で実行
-- psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U <superuser> -d $IDP_DB_NAME

CREATE USER idp WITH PASSWORD '<password>';
GRANT CONNECT ON DATABASE idpserver TO idp;
GRANT USAGE ON SCHEMA public TO idp;
GRANT CREATE ON SCHEMA public TO idp;
GRANT ALL PRIVILEGES ON DATABASE idpserver TO idp;

-- BYPASSRLS 権限付与（Flyway実行とpg_cronジョブ実行に必要）
ALTER USER idp BYPASSRLS;
```

#### 2.2.3 確認

```sql
SELECT rolname, rolsuper, rolbypassrls
FROM pg_roles
WHERE rolname = 'idp';
```

**期待結果**:
```
 rolname | rolsuper | rolbypassrls
---------+----------+--------------
 idp     | f        | t
```

---

### 2.3 拡張インストール・権限設定

#### 2.3.1 pg_cron 拡張（postgres DB）

pg_cron はクロスデータベースモードで `postgres` データベースにインストールします。

```sql
-- postgres データベースに接続（superuserで実行）
-- psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U <superuser> -d postgres

CREATE EXTENSION IF NOT EXISTS pg_cron;

-- idp ユーザーへの権限付与
GRANT USAGE ON SCHEMA cron TO idp;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cron TO idp;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA cron TO idp;
```

**確認**:
```sql
SELECT * FROM pg_extension WHERE extname = 'pg_cron';
```

#### 2.3.2 pg_partman 拡張（idpserver DB）

```sql
-- idpserver データベースに接続（superuserで実行）
-- psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U <superuser> -d idpserver

CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;

-- idp ユーザーへの権限付与
GRANT USAGE, CREATE ON SCHEMA partman TO idp;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO idp;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA partman TO idp;
GRANT ALL ON ALL SEQUENCES IN SCHEMA partman TO idp;
```

**確認**:
```sql
SELECT * FROM pg_extension WHERE extname = 'pg_partman';
```

#### 2.3.3 アーカイブエクスポート権限（オプション）

ローカルファイルエクスポートを使用する場合のみ必要です。本番環境では AWS S3 等の外部ストレージを使用することを推奨します。

```sql
-- idpserver データベースに接続
GRANT pg_write_server_files TO idp;
```

#### 2.3.4 権限確認

**スキーマ権限確認**:
```sql
SELECT
    nspname AS schema_name,
    pg_get_userbyid(nspowner) AS owner,
    nspacl AS access_privileges
FROM pg_namespace
WHERE nspname IN ('partman', 'cron');
```

**期待結果**:
```
 schema_name |   owner   |                  access_privileges
-------------+-----------+-------------------------------------------
 partman     | <superuser> | {...,idp=UC/<superuser>}
 cron        | postgres  | {postgres=UC/postgres,idp=U/postgres}
```

---

### 2.4 アプリケーションユーザー作成

Flywayマイグレーション前にアプリケーションユーザーを作成します（ローカルDocker環境と同じ順序）。

#### 2.4.1 作成するユーザー

| ユーザー | 役割 | BYPASSRLS | 用途 |
|---------|------|-----------|------|
| `idp_admin_user` | 管理API用 | Yes | Control Plane API（テナント横断操作） |
| `idp_app_user` | アプリケーション用 | No | 通常のAPI（RLS適用） |

#### 2.4.2 環境変数の設定

```shell
export IDP_DB_ADMIN_USER=idp_admin_user
export IDP_DB_ADMIN_PASSWORD=<admin_password>
export IDP_DB_APP_USER=idp_app_user
export IDP_DB_APP_PASSWORD=<app_password>
```

#### 2.4.3 ユーザー作成スクリプトの実行

```bash
# スーパーユーザーで実行
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U <superuser> -d $IDP_DB_NAME \
  -f ./libs/idp-server-database/postgresql/user/admin_user.sql

psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U <superuser> -d $IDP_DB_NAME \
  -f ./libs/idp-server-database/postgresql/user/app_user.sql
```

#### 2.4.4 PostgreSQL認証設定（推奨）

パスワード自動認証のため`.pgpass`ファイルを設定：

```shell
cat > ~/.pgpass << 'EOF'
localhost:5432:idpserver:idp:<password>
localhost:5432:idpserver:idp_admin_user:<admin_password>
localhost:5432:idpserver:idp_app_user:<app_password>
localhost:5432:postgres:idp:<password>
EOF
chmod 600 ~/.pgpass
```

**フォーマット**: `hostname:port:database:username:password`

#### 2.4.5 ユーザー権限確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U <superuser> -d $IDP_DB_NAME \
  -f ./libs/idp-server-database/postgresql/operation/select-user-role.sql
```

**期待結果**:
```
    rolname     | rolsuper | rolbypassrls | rolconnlimit
----------------+----------+--------------+--------------
 idp            | f        | t            |           -1
 idp_admin_user | f        | t            |           -1
 idp_app_user   | f        | f            |           -1
```

**Note**: `rolconnlimit = -1` は無制限を意味します。接続数制限はアプリケーション側のコネクションプール（HikariCP等）で管理します。

---

### 2.5 Flywayマイグレーション

#### 2.5.1 マイグレーション実行

```bash
# idp ユーザー（DB_OWNER）で実行
flyway -url=jdbc:postgresql://$IDP_DB_HOST:$IDP_DB_PORT/idpserver \
       -user=idp \
       -password=<password> \
       migrate
```

または Gradle を使用：

```bash
DB_TYPE=postgresql ./gradlew flywayMigrate
```

#### 2.5.2 マイグレーション確認

```bash
flyway -url=jdbc:postgresql://$IDP_DB_HOST:$IDP_DB_PORT/idpserver \
       -user=idp \
       -password=<password> \
       info
```

または：

```bash
./gradlew flywayInfo
```

全てのマイグレーションが "Success" であることを確認してください。

#### 2.5.3 パーティショニング関連のマイグレーション

以下のマイグレーションでパーティショニングが設定されます：

| マイグレーション | 内容 |
|----------------|------|
| `V0_9_21_1__add_event_partitioning.sql` | security_event, security_event_hook_results のパーティション設定 |
| `V0_9_21_2__statistics.sql` | statistics_daily_users, statistics_monthly_users, statistics_yearly_users のパーティション設定 |
| `V0_9_21_3__archive_support.sql` | archive スキーマ・関数作成、retention_schema 設定 |

---

### 2.6 pg_cronジョブ登録

#### 2.6.1 ジョブ登録

```bash
# idp ユーザーで postgres データベースに接続して実行
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U idp -d postgres \
  -f ./libs/idp-server-database/postgresql/operation/setup-pg-cron-jobs.sql
```

**重要**: pg_cron は postgres データベースにインストールされているため、postgres データベースに接続してジョブを登録します。ジョブは `cron.schedule_in_database()` を使用して idpserver データベースで実行されます。

#### 2.6.2 登録されるジョブ

| ジョブ名 | スケジュール | 実行DB | 説明 |
|---------|-------------|--------|------|
| `partman-maintenance` | 毎日 02:00 UTC | idpserver | パーティション作成・削除 |
| `archive-processing` | 毎日 03:00 UTC | idpserver | アーカイブエクスポート・削除 |

#### 2.6.3 ジョブ確認

```sql
-- postgres データベースに接続
SELECT
    jobid,
    jobname,
    schedule,
    database,
    username,
    active
FROM cron.job
WHERE jobname IN ('partman-maintenance', 'archive-processing');
```

**期待結果**:
```
 jobid |       jobname       | schedule  | database  | username | active
-------+---------------------+-----------+-----------+----------+--------
     1 | partman-maintenance | 0 2 * * * | idpserver | idp      | t
     2 | archive-processing  | 0 3 * * * | idpserver | idp      | t
```

---

## 3. 動作確認

### 3.1 RLS設定確認

#### RLS有効テーブル確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME \
  -f ./libs/idp-server-database/postgresql/operation/select-rls-table.sql
```

#### RLSポリシー確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME \
  -f ./libs/idp-server-database/postgresql/operation/select-rls-policy.sql
```

**期待結果**:
```
 schemaname |       tablename        |      policyname       |        policy_condition
------------+------------------------+-----------------------+------------------------------
 public     | tenant                 | tenant_isolation_policy | (id = current_setting('app.tenant_id'::text)::uuid)
(29 rows)
```

### 3.2 テナント分離動作確認

#### 管理者ユーザーでの確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_ADMIN_USER -d $IDP_DB_NAME \
  -c "SELECT COUNT(*) FROM tenant;"
```

管理者ユーザー（BYPASSRLS）は全データにアクセス可能です。

#### アプリケーションユーザーでの確認

```shell
psql -h $IDP_DB_HOST -p $IDP_DB_PORT -U $IDP_DB_APP_USER -d $IDP_DB_NAME \
  -c "SELECT COUNT(*) FROM tenant"
```

アプリケーションユーザーはRLS適用のため、テナント設定に応じたデータのみアクセス可能です：

```sql
-- テナント設定なし
SELECT COUNT(*) FROM tenant;
-- 結果: count = 0 または ERROR: app.tenant_id is not set

-- テナント設定あり
SET app.tenant_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
SELECT COUNT(*) FROM tenant;
-- 結果: count = 1 (テナント自身のデータのみ)
```

### 3.3 pg_partman 設定確認

```sql
-- idpserver データベースに接続
SELECT
    parent_table,
    partition_interval,
    retention,
    retention_keep_table,
    retention_schema,
    premake
FROM partman.part_config;
```

**期待結果**:
```
           parent_table            | partition_interval | retention | retention_keep_table | retention_schema | premake
-----------------------------------+--------------------+-----------+----------------------+------------------+---------
 public.security_event             | 1 day              | 90 days   | t                    | archive          |       7
 public.security_event_hook_results| 1 day              | 90 days   | t                    | archive          |       7
 public.statistics_daily_users     | 1 day              | 90 days   | f                    |                  |       7
 public.statistics_monthly_users   | 1 mon              | 13 months | f                    |                  |      13
 public.statistics_yearly_users    | 1 year             | 5 years   | f                    |                  |       5
```

### 3.4 パーティション一覧確認

```sql
-- idpserver データベースに接続
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size('public.' || tablename)) as size
FROM pg_tables
WHERE tablename LIKE 'security_event_p%'
ORDER BY tablename DESC
LIMIT 10;
```

### 3.5 pg_cron 実行履歴確認

```sql
-- postgres データベースに接続
SELECT
    jobid,
    jobname,
    start_time,
    end_time,
    status,
    return_message
FROM cron.job_run_details
WHERE jobname IN ('partman-maintenance', 'archive-processing')
ORDER BY start_time DESC
LIMIT 10;
```

---

## 4. 設定チェックリスト

### 必須設定項目

- [ ] **2.1**: `shared_preload_libraries` に `pg_cron` 追加
- [ ] **2.1**: `cron.database_name = 'postgres'` 設定
- [ ] **2.1**: PostgreSQL 再起動
- [ ] **2.2**: idp (DB_OWNER) ユーザー作成
- [ ] **2.3**: pg_cron 拡張インストール（postgres DB）
- [ ] **2.3**: pg_partman 拡張インストール（idpserver DB）
- [ ] **2.3**: idp ユーザーへの権限付与
- [ ] **2.4**: アプリケーションユーザー作成（idp_admin_user, idp_app_user）
- [ ] **2.5**: Flyway マイグレーション実行
- [ ] **2.6**: pg_cron ジョブ登録
- [ ] **3.1**: RLS 設定確認
- [ ] **3.2**: テナント分離動作確認
- [ ] **3.3**: pg_partman 設定確認

---

## 5. トラブルシューティング

### 5.1 pg_cron ジョブが実行されない

```sql
-- postgres データベースに接続
-- ジョブが active か確認
SELECT jobname, active FROM cron.job;

-- 実行履歴でエラーを確認
SELECT jobname, status, return_message
FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 5;
```

### 5.2 permission denied エラー

```sql
-- 権限確認
SELECT
    nspname,
    nspacl
FROM pg_namespace
WHERE nspname IN ('cron', 'partman');

-- 必要に応じて権限付与
GRANT USAGE ON SCHEMA cron TO idp;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA cron TO idp;
```

### 5.3 パーティションが作成されない

```sql
-- idpserver データベースに接続
-- partman 設定確認
SELECT parent_table, automatic_maintenance, premake
FROM partman.part_config;

-- 手動でメンテナンス実行
CALL partman.run_maintenance_proc();
```

### 5.4 Flyway マイグレーションが失敗する

pg_partman 拡張がインストールされているか確認してください：

```sql
-- idpserver データベースに接続
SELECT * FROM pg_extension WHERE extname = 'pg_partman';

-- partman スキーマが存在するか確認
SELECT nspname FROM pg_namespace WHERE nspname = 'partman';
```

### 5.5 RLS関連のエラー

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

### 5.6 接続状況確認

```sql
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

---

## 6. 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [初期設定・ユーザー・ロール](./04-initial-configuration.md)
- [運用ガイダンス](./05-operational-guidance.md)
- [パーティショニングガイド](../../content_06_developer-guide/08-reference/database-partitioning-guide.md)
- [セキュリティイベントアーカイブガイド](../../content_06_developer-guide/08-reference/security-event-archive-guide.md)
