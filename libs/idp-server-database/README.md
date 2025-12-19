# idp-server-database

## 商用環境向けドキュメント

商用環境でのデータベース構築手順は以下のドキュメントを参照してください：

- [PostgreSQL データベース設定](../../documentation/docs/content_08_ops/commercial-deployment/03-database.md)
  - ユーザー作成、拡張インストール、Flyway、pg_cronジョブ、RLS設定

---

## ローカル環境（Docker Compose）

### 構築フロー

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  postgres-primary コンテナ起動                                               │
│  ─────────────────────────────────────────────────────────────────────────  │
│  command: postgres -c shared_preload_libraries=pg_stat_statements,pg_cron   │
│                    -c cron.database_name=postgres                           │
│                                                                             │
│  /docker-entrypoint-initdb.d/ で以下を順次実行:                              │
│    00-init-app-user.sh   → idp (DB_OWNER) ユーザー作成                       │
│    01-add-bypassrls.sh   → BYPASSRLS 権限付与                                │
│    02-init-partman.sh    → pg_cron (postgres DB), pg_partman (idpserver DB) │
│    99-init-replication.sh → レプリケーション設定                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  postgres-user-init コンテナ                                                 │
│  ─────────────────────────────────────────────────────────────────────────  │
│  depends_on: postgres-primary (service_healthy)                             │
│                                                                             │
│  実行内容:                                                                   │
│    admin_user.sql → idp_admin_user 作成 (BYPASSRLS)                         │
│    app_user.sql   → idp_app_user 作成 (RLS適用)                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  flyway-migrator コンテナ                                                    │
│  ─────────────────────────────────────────────────────────────────────────  │
│  depends_on: postgres-primary (service_healthy)                             │
│              postgres-user-init (service_completed_successfully)            │
│                                                                             │
│  実行内容:                                                                   │
│    flyway migrate → DDL適用、パーティション設定、RLSポリシー設定               │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  pg-cron-setup コンテナ                                                      │
│  ─────────────────────────────────────────────────────────────────────────  │
│  depends_on: postgres-primary (service_healthy)                             │
│              flyway-migrator (service_completed_successfully)               │
│                                                                             │
│  実行内容:                                                                   │
│    setup-pg-cron-jobs.sql → postgres DB に接続してジョブ登録                  │
│      • partman-maintenance (毎日 02:00 UTC)                                  │
│      • archive-processing (毎日 03:00 UTC)                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 起動コマンド

```bash
# 全サービス起動
docker compose up -d

# データベース関連のみ起動
docker compose up -d postgres-primary postgres-replica postgres-user-init flyway-migrator pg-cron-setup
```

### 確認コマンド

```bash
# インストール済み拡張の確認
docker exec -it postgres-primary psql -U idp -d idpserver -c "\dx"

# pg_cron ジョブ確認
docker exec -it postgres-primary psql -U idp -d postgres -c "SELECT jobname, schedule, database, active FROM cron.job;"

# pg_partman 設定確認
docker exec -it postgres-primary psql -U idp -d idpserver -c "SELECT parent_table, partition_interval, retention FROM partman.part_config;"

# ユーザー確認
docker exec -it postgres-primary psql -U idpserver -d idpserver -c "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname IN ('idp', 'idp_admin_user', 'idp_app_user');"
```

**拡張の期待結果**（ローカル環境）:
```
    Name    | Version |   Schema   |                     Description
------------+---------+------------+------------------------------------------------------
 pg_partman | 5.x.x   | partman    | Extension to manage partitioned tables by time or ID
 plpgsql    | 1.0     | pg_catalog | PL/pgSQL procedural language
```

**Note**: `aws_s3`/`aws_commons` 拡張はAWS RDS/Aurora専用のため、ローカル環境には含まれません。

---

## PostgreSQL ユーザーと権限

### ユーザー一覧

| ユーザー | 役割 | SUPERUSER | BYPASSRLS | 用途 |
|---------|------|-----------|-----------|------|
| `idpserver` | PostgreSQL管理者 | Yes | Yes | Docker初期化、拡張作成 |
| `idp` | DB所有者 (DB_OWNER) | No | Yes | Flywayマイグレーション、pg_cronジョブ実行 |
| `idp_admin_user` | 管理API用 | No | Yes | Control Plane API（テナント横断操作） |
| `idp_app_user` | アプリケーション用 | No | No | 通常のAPI（RLS適用） |

### スキーマ権限

| スキーマ | Owner | idp | idp_admin_user | idp_app_user | 用途 |
|---------|-------|-----|----------------|--------------|------|
| `public` | pg_database_owner | UC | U | U | アプリケーションテーブル |
| `partman` | idpserver | UC | - | - | pg_partman管理テーブル |
| `archive` | idp | UC | - | - | アーカイブ一時保管 |
| `cron` (postgres DB) | postgres | U | - | - | pg_cronジョブ管理 |

**権限記号**: U=USAGE, C=CREATE

### 設定ファイルの責務

| ファイル | 責務 | 実行タイミング |
|---------|------|--------------|
| `postgresql/init/00-init-app-user.sh` | idp (DB_OWNER) ユーザー作成 | Docker初期化 |
| `postgresql/init/01-add-bypassrls.sh` | BYPASSRLS付与 | Docker初期化 |
| `postgresql/init/02-init-partman.sh` | pg_cron/pg_partman拡張作成、権限付与 | Docker初期化 |
| `postgresql/user/admin_user.sql` | idp_admin_user 作成・権限 | postgres-user-init |
| `postgresql/user/app_user.sql` | idp_app_user 作成・権限 | postgres-user-init |

### ユーザー構成図

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  idpserver (SUPERUSER)                                                      │
│  ─────────────────────                                                      │
│  - Docker初期化専用                                                          │
│  - pg_cron, pg_partman 拡張作成                                              │
│  - アプリケーションからは使用しない                                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  idp (DB_OWNER, BYPASSRLS)                                                  │
│  ─────────────────────────                                                  │
│  - Flywayマイグレーション実行                                                │
│  - pg_cronジョブ実行（partman-maintenance, archive-processing）              │
│  - DDL操作（テーブル作成・変更）                                              │
│  - アプリケーションからは使用しない                                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│  idp_admin_user (BYPASSRLS)       │ │  idp_app_user (RLS適用)           │
│  ─────────────────────────────    │ │  ─────────────────────────────    │
│  - Control Plane API              │ │  - 通常のAPI                       │
│  - テナント横断操作               │ │  - RLSによるテナント分離           │
│  - 初期データ投入                 │ │  - ユーザー操作                    │
│  - publicスキーマのみ             │ │  - publicスキーマのみ              │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

### 権限追加時の注意

新しいスキーマを作成した場合、ユーザー権限は自動付与されません。
アプリケーションからアクセスが必要な場合は、以下のファイルに権限を追加してください：

- `postgresql/user/admin_user.sql` - idp_admin_user用
- `postgresql/user/app_user.sql` - idp_app_user用

### 権限確認用SQL

```sql
-- ユーザー一覧と属性
SELECT
    rolname,
    rolsuper,
    rolcreaterole,
    rolcreatedb,
    rolbypassrls,
    rolconnlimit
FROM pg_roles
WHERE rolname IN ('idpserver', 'idp', 'idp_admin_user', 'idp_app_user')
ORDER BY rolname;

-- スキーマ一覧と権限
\dn+

-- 特定スキーマの権限確認
SELECT
    nspname AS schema_name,
    pg_get_userbyid(nspowner) AS owner,
    nspacl AS access_privileges
FROM pg_namespace
WHERE nspname IN ('public', 'partman', 'archive');

-- テーブル権限の確認
SELECT
    grantee,
    table_schema,
    table_name,
    privilege_type
FROM information_schema.table_privileges
WHERE grantee IN ('idp', 'idp_admin_user', 'idp_app_user')
ORDER BY grantee, table_schema, table_name;

-- デフォルト権限の確認
SELECT
    pg_get_userbyid(defaclrole) AS owner,
    defaclnamespace::regnamespace AS schema,
    CASE defaclobjtype
        WHEN 'r' THEN 'table'
        WHEN 'S' THEN 'sequence'
        WHEN 'f' THEN 'function'
        WHEN 'T' THEN 'type'
    END AS object_type,
    defaclacl AS permissions
FROM pg_default_acl
ORDER BY owner, schema;

-- pg_cron ジョブ一覧（postgres DBで実行）
-- psql -h localhost -U idp -d postgres
SELECT jobid, jobname, schedule, database, username, active
FROM cron.job;
```

### 権限記号リファレンス

**テーブル権限**:

| 記号 | 権限 | SQL |
|------|------|-----|
| `a` | INSERT | `GRANT INSERT` |
| `r` | SELECT | `GRANT SELECT` |
| `w` | UPDATE | `GRANT UPDATE` |
| `d` | DELETE | `GRANT DELETE` |
| `D` | TRUNCATE | `GRANT TRUNCATE` |
| `x` | REFERENCES | `GRANT REFERENCES` |
| `t` | TRIGGER | `GRANT TRIGGER` |

**シーケンス権限**:

| 記号 | 権限 | SQL |
|------|------|-----|
| `r` | SELECT (currval) | `GRANT SELECT` |
| `U` | USAGE (nextval) | `GRANT USAGE` |
| `w` | UPDATE (setval) | `GRANT UPDATE` |

**スキーマ権限**:

| 記号 | 権限 | SQL |
|------|------|-----|
| `U` | USAGE | `GRANT USAGE` |
| `C` | CREATE | `GRANT CREATE` |

**例**: `idp_admin_user=arwd/idp`
- `idp_admin_user` に `INSERT, SELECT, UPDATE, DELETE` 権限
- `/idp` は権限を付与したユーザー

---

## MySQL版 ローカル環境（Docker Compose）

### 構築フロー

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  MySQL コンテナ起動                                                          │
│  ─────────────────────────────────────────────────────────────────────────  │
│  image: mysql:8.0                                                           │
│  command: --default-authentication-plugin=mysql_native_password             │
│           --event-scheduler=ON                                              │
│                                                                             │
│  環境変数:                                                                   │
│    MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD          │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │ healthcheck OK
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  flyway-migrator コンテナ                                                    │
│  ─────────────────────────────────────────────────────────────────────────  │
│  depends_on: mysql (service_healthy)                                        │
│                                                                             │
│  実行内容:                                                                   │
│    V0_9_21_1__security_event_partition.mysql.sql                           │
│      ├─ CREATE TABLE security_event (パーティション付き)                     │
│      ├─ CREATE TABLE security_event_hook_results (パーティション付き)        │
│      ├─ CREATE PROCEDURE (※Flywayの制限により不完全)                        │
│      └─ CREATE EVENT evt_maintain_security_event_partitions                 │
│                                                                             │
│    V0_9_21_2__statistics.mysql.sql                                         │
│      ├─ CREATE TABLE statistics_daily_users (パーティション付き)             │
│      ├─ CREATE TABLE statistics_monthly_users (パーティション付き)           │
│      ├─ CREATE TABLE statistics_yearly_users (パーティション付き)            │
│      ├─ CREATE PROCEDURE (※Flywayの制限により不完全)                        │
│      └─ CREATE EVENT evt_maintain_statistics_partitions                     │
│                                                                             │
│  【注意】Flywayは DELIMITER // を正しく処理できないため、                     │
│         ストアドプロシージャは後続のコンテナで修正される                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │ service_completed_successfully
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  mysql-partition-setup コンテナ                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│  depends_on: flyway-migrator (service_completed_successfully)               │
│                                                                             │
│  Step 1: fix-stored-procedures.sql                                         │
│    └─ ストアドプロシージャを正しいバージョンで再作成                          │
│       (ローカル変数 → セッション変数 @p_name, @p_end に修正)                  │
│                                                                             │
│  Step 2: setup-partition-maintenance.sql                                   │
│    ├─ CALL maintain_security_event_partitions()                            │
│    │    └─ security_event, security_event_hook_results                     │
│    │       90日分のパーティション作成 (例: p20251218 ~ p20260317)            │
│    └─ CALL maintain_statistics_partitions()                                │
│         ├─ statistics_daily_users: 90日分                                   │
│         ├─ statistics_monthly_users: 3ヶ月分                                │
│         └─ statistics_yearly_users: 3ヶ月分                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │ service_completed_successfully
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  idp-server コンテナ起動                                                     │
│  ─────────────────────────────────────────────────────────────────────────  │
│  depends_on: mysql-partition-setup (service_completed_successfully)         │
│                                                                             │
│  パーティション設定完了後にアプリケーションが起動                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 日次メンテナンス（Event Scheduler）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  02:30 AM: evt_maintain_security_event_partitions                          │
│            └─ 新規パーティション作成 + 90日超過パーティション削除             │
│                                                                             │
│  03:00 AM: evt_maintain_statistics_partitions                              │
│            └─ 新規パーティション作成 + 保持期間超過パーティション削除          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 起動コマンド

```bash
# 全サービス起動
docker compose -f docker-compose-mysql.yaml up -d

# データベース関連のみ起動
docker compose -f docker-compose-mysql.yaml up -d mysql flyway-migrator mysql-partition-setup
```

### 確認コマンド

```bash
# Event Scheduler の状態確認
docker exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW VARIABLES LIKE 'event_scheduler';"

# パーティション一覧確認
docker exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" idpserver -e "
SELECT TABLE_NAME, COUNT(*) as partition_count
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'idpserver'
  AND TABLE_NAME IN ('security_event', 'security_event_hook_results',
                     'statistics_daily_users', 'statistics_monthly_users', 'statistics_yearly_users')
GROUP BY TABLE_NAME;"

# イベント一覧確認
docker exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" idpserver -e "
SELECT EVENT_NAME, STATUS, INTERVAL_VALUE, INTERVAL_FIELD, LAST_EXECUTED
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = 'idpserver';"

# ストアドプロシージャ一覧確認
docker exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" idpserver -e "
SELECT ROUTINE_NAME, ROUTINE_TYPE
FROM information_schema.ROUTINES
WHERE ROUTINE_SCHEMA = 'idpserver'
ORDER BY ROUTINE_NAME;"
```

### パーティション保持期間

| テーブル | パーティション単位 | 保持期間 |
|---------|-----------------|---------|
| security_event | 日次 | 90日 |
| security_event_hook_results | 日次 | 90日 |
| statistics_daily_users | 日次 | 90日 |
| statistics_monthly_users | 月次 | 13ヶ月 |
| statistics_yearly_users | 月次 | 60ヶ月 |

### MySQL版の技術的制約と解決策

#### 問題1: FlywayのDELIMITER制限

**問題**: FlywayはMySQLの`DELIMITER //`ステートメントを正しく処理できない

**解決策**: `mysql-partition-setup`コンテナで`fix-stored-procedures.sql`を実行し、
正しいストアドプロシージャを再作成

#### 問題2: PREPARE/EXECUTE内の変数スコープ

**問題**: MySQLのストアドプロシージャ内で`DECLARE`で宣言したローカル変数は、
`PREPARE/EXECUTE`で生成される動的SQL内から参照できない

```sql
-- NG: ローカル変数は PREPARE/EXECUTE 内で NULL になる
DECLARE partition_name VARCHAR(20);
SET partition_name = 'p20251218';
SET @sql = CONCAT('ALTER TABLE ... PARTITION ', partition_name, ' ...');

-- OK: セッション変数は PREPARE/EXECUTE 内で正しく参照できる
SET @p_name = 'p20251218';
SET @sql = CONCAT('ALTER TABLE ... PARTITION ', @p_name, ' ...');
```

**解決策**: すべてのストアドプロシージャでセッション変数（`@変数名`）を使用

### 設定ファイル一覧

| ファイル | 責務 |
|---------|------|
| `mysql/V0_9_21_1__security_event_partition.mysql.sql` | セキュリティイベントテーブル・パーティション設定 |
| `mysql/V0_9_21_2__statistics.mysql.sql` | 統計テーブル・パーティション設定 |
| `mysql/operation/fix-stored-procedures.sql` | ストアドプロシージャの修正版 |
| `mysql/operation/setup-partition-maintenance.sql` | パーティション初期作成実行 |
| `Dockerfile-mysql-partition-setup` | partition-setupコンテナ定義 |
| `entrypoint-mysql-partition-setup.sh` | partition-setupエントリポイント |

---

## docker build

```shell
docker build -f ./Dockerfile-flyway -t idp-flyway-migrator:latest .
```

## migrate

```shell
DB_TYPE=postgresql ./gradlew flywayClean flywayMigrate
```

```shell
DB_TYPE=postgresql DB_URL=jdbc:postgresql://localhost:54321/idpserver_reader ./gradlew flywayClean flywayMigrate
```

```shell
DB_TYPE=mysql ./gradlew flywayClean flywayMigrate
```


## Note

### 🛠 PostgreSQL → MySQL DDL Conversion Rules

This table summarizes key syntax and data type differences when converting DDL from PostgreSQL to MySQL (>= 5.7).

| PostgreSQL Syntax / Type             | MySQL Equivalent                         | Notes / Remarks                                                                 |
|-------------------------------------|------------------------------------------|----------------------------------------------------------------------------------|
| `CHAR(36)`                          | `CHAR(36)`                               | Commonly used for UUIDs (stored as strings)                                     |
| `VARCHAR(255)`                      | `VARCHAR(255)`                           | No change needed                                                                |
| `TEXT`                              | `TEXT`                                   | Direct equivalent for long text                                                 |
| `BOOLEAN`                           | `TINYINT(1)`                             | MySQL does not support native boolean types; `1 = TRUE`, `0 = FALSE`            |
| `TIMESTAMP DEFAULT now()`           | `DATETIME DEFAULT CURRENT_TIMESTAMP`     | Replace PostgreSQL's `now()` with MySQL's built-in timestamp default            |
| `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `DATETIME DEFAULT CURRENT_TIMESTAMP`   | Same as above                                                                   |
| `TIMESTAMP`                         | `DATETIME`                               | Use `DATETIME` for cross-databaseType compatibility                                 |
| `JSONB`                             | `JSON`                                   | MySQL 5.7+ supports native JSON type                                            |
| `INET`                              | `VARCHAR(45)`                            | IPv6-compatible IP address storage                                              |
| `SERIAL`                            | `INT AUTO_INCREMENT`                     | PostgreSQL's auto-increment shortcut                                            |
| `gen_random_uuid()`                 | `UUID()`                                 | Use MySQL's `UUID()` function if UUID generation is required at the DB level    |
| `UUID` type (extension)             | `CHAR(36)` + `UUID()`                    | PostgreSQL has `uuid` type, MySQL stores as string                              |
| `ON DELETE CASCADE`                 | `ON DELETE CASCADE`                      | Behavior is the same                                                            |
| `UNIQUE (...) WHERE ...`            | Not supported                            | Needs to be rewritten using triggers or application-level checks                |
| `CREATE VIEW`                       | `CREATE VIEW`                            | Syntax mostly compatible (but some expressions may differ)                      |
| `JSONB` Indexing (e.g. GIN)         | `JSON` + `Generated Columns + Index`     | MySQL has no GIN index; use generated columns for JSON fields                   |
