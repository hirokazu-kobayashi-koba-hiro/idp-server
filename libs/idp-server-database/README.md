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
# pg_cron ジョブ確認
docker exec -it postgres-primary psql -U idp -d postgres -c "SELECT jobname, schedule, database, active FROM cron.job;"

# pg_partman 設定確認
docker exec -it postgres-primary psql -U idp -d idpserver -c "SELECT parent_table, partition_interval, retention FROM partman.part_config;"

# ユーザー確認
docker exec -it postgres-primary psql -U idpserver -d idpserver -c "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname IN ('idp', 'idp_admin_user', 'idp_app_user');"
```

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
