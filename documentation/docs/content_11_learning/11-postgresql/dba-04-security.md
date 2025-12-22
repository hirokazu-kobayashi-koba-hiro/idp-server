# PostgreSQL セキュリティガイド

このドキュメントでは、PostgreSQLのセキュリティ設定とベストプラクティスを解説します。

---

## 目次

1. [セキュリティの全体像](#1-セキュリティの全体像)
2. [認証 (Authentication)](#2-認証-authentication)
3. [ロールと権限](#3-ロールと権限)
4. [Row Level Security (RLS)](#4-row-level-security-rls)
5. [SSL/TLS暗号化](#5-ssltls暗号化)
6. [監査ログ](#6-監査ログ)
7. [セキュリティベストプラクティス](#7-セキュリティベストプラクティス)

---

## 1. セキュリティの全体像

### 1.1 多層防御

```
┌──────────────────────────────────────────────────────────────┐
│                    PostgreSQL セキュリティ層                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Layer 1: ネットワーク                                  │ │
│  │  - ファイアウォール                                     │ │
│  │  - listen_addresses                                    │ │
│  │  - SSL/TLS暗号化                                       │ │
│  └────────────────────────────────────────────────────────┘ │
│                          ↓                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Layer 2: 認証 (Authentication)                        │ │
│  │  - pg_hba.conf                                         │ │
│  │  - パスワード (scram-sha-256)                          │ │
│  │  - 証明書、LDAP、GSSAPI等                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                          ↓                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Layer 3: 認可 (Authorization)                         │ │
│  │  - ロールと権限 (GRANT/REVOKE)                         │ │
│  │  - スキーマ分離                                        │ │
│  │  - Row Level Security (RLS)                            │ │
│  └────────────────────────────────────────────────────────┘ │
│                          ↓                                   │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Layer 4: 監査と監視                                    │ │
│  │  - ログ設定                                            │ │
│  │  - 監査拡張 (pgAudit)                                  │ │
│  │  - 異常検知                                            │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 最小権限の原則

```
┌──────────────────────────────────────────────────────────────┐
│                    最小権限の原則                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【原則】                                                    │
│  各ユーザー/アプリケーションには、                           │
│  必要最小限の権限のみを付与する                              │
│                                                              │
│  【悪い例】                                                  │
│  - アプリケーションがスーパーユーザーで接続                  │
│  - 全テーブルへのフルアクセス権限                            │
│  - publicスキーマに全オブジェクトを配置                      │
│                                                              │
│  【良い例】                                                  │
│  - 用途別にロールを分離                                      │
│    - app_read: SELECT のみ                                  │
│    - app_write: SELECT, INSERT, UPDATE, DELETE              │
│    - app_admin: 上記 + DDL                                  │
│  - スキーマで論理的に分離                                    │
│  - Row Level Security で行単位のアクセス制御                 │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. 認証 (Authentication)

### 2.1 pg_hba.conf の構造

```
┌──────────────────────────────────────────────────────────────┐
│                    pg_hba.conf の形式                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  TYPE   DATABASE   USER   ADDRESS        METHOD   [OPTIONS]  │
│  ────   ────────   ────   ───────        ──────   ─────────  │
│                                                              │
│  TYPE:                                                       │
│  - local    : Unix ドメインソケット                          │
│  - host     : TCP/IP (SSL/非SSL 両方)                        │
│  - hostssl  : SSL 接続のみ                                   │
│  - hostnossl: 非SSL 接続のみ                                 │
│  - hostgssenc: GSSAPI 暗号化接続のみ                         │
│                                                              │
│  DATABASE:                                                   │
│  - all         : 全データベース                              │
│  - sameuser    : ユーザー名と同じ名前のDB                    │
│  - samerole    : ユーザーが属するロール名と同じDB            │
│  - replication : レプリケーション接続                        │
│  - dbname      : 特定のデータベース名                        │
│                                                              │
│  USER:                                                       │
│  - all       : 全ユーザー                                    │
│  - +rolename : ロールのメンバー                              │
│  - username  : 特定のユーザー名                              │
│                                                              │
│  ADDRESS:                                                    │
│  - CIDR表記  : 192.168.1.0/24                                │
│  - ホスト名  : client.example.com                            │
│  - all       : 全アドレス                                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 認証方式の比較

```
┌──────────────────────────────────────────────────────────────┐
│                      認証方式の比較                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌───────────────────┬───────────────────────────────────┐  │
│  │ 方式              │ 説明                              │  │
│  ├───────────────────┼───────────────────────────────────┤  │
│  │ trust             │ 無条件で許可 (危険！)             │  │
│  │ reject            │ 無条件で拒否                      │  │
│  │ scram-sha-256     │ パスワード認証 (推奨)             │  │
│  │ md5               │ MD5パスワード (非推奨)            │  │
│  │ password          │ 平文パスワード (危険！)           │  │
│  │ peer              │ OSユーザー名で認証 (localのみ)    │  │
│  │ ident             │ Identプロトコルで認証             │  │
│  │ cert              │ SSL証明書で認証                   │  │
│  │ ldap              │ LDAPサーバーで認証                │  │
│  │ radius            │ RADIUSサーバーで認証              │  │
│  │ gss               │ GSSAPI/Kerberos認証               │  │
│  └───────────────────┴───────────────────────────────────┘  │
│                                                              │
│  【推奨】                                                    │
│  - ローカル接続: peer (postgres OSユーザー)                 │
│  - リモート接続: scram-sha-256 + SSL                        │
│  - エンタープライズ: LDAP または cert                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 本番環境向け pg_hba.conf 例

```bash
# /var/lib/pgsql/16/data/pg_hba.conf

# TYPE  DATABASE        USER            ADDRESS                 METHOD

# ローカル接続 (postgres スーパーユーザー)
local   all             postgres                                peer

# ローカル接続 (一般ユーザー)
local   all             all                                     scram-sha-256

# ローカルホストからのTCP接続
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256

# アプリケーションサーバーからの接続 (SSL必須)
hostssl myapp           app_user        10.0.1.0/24             scram-sha-256
hostssl myapp           app_readonly    10.0.1.0/24             scram-sha-256

# 管理者からの接続 (特定IPのみ、SSL必須)
hostssl all             admin_user      10.0.100.10/32          scram-sha-256

# レプリケーション (SSL必須)
hostssl replication     repl_user       10.0.2.0/24             scram-sha-256

# その他は全て拒否
host    all             all             0.0.0.0/0               reject
host    all             all             ::/0                    reject
```

### 2.4 パスワード管理

```sql
-- パスワードの設定 (scram-sha-256)
ALTER USER myuser WITH PASSWORD 'secure_password_here';

-- パスワード有効期限の設定
ALTER USER myuser VALID UNTIL '2025-12-31';

-- 接続制限
ALTER USER myuser CONNECTION LIMIT 10;

-- パスワードの暗号化方式を確認
SHOW password_encryption;  -- scram-sha-256

-- ユーザーのパスワード情報確認
SELECT usename, passwd IS NOT NULL AS has_password, valuntil
FROM pg_shadow;
```

```ini
# postgresql.conf
password_encryption = 'scram-sha-256'  # デフォルトの暗号化方式
```

### 2.5 LDAP認証の設定

```bash
# pg_hba.conf (LDAP認証)
host    all    all    10.0.0.0/8    ldap ldapserver=ldap.example.com ldapbasedn="dc=example,dc=com" ldapsearchattribute=uid
```

```bash
# Simple Bind方式
host    all    all    10.0.0.0/8    ldap ldapserver=ldap.example.com ldapprefix="uid=" ldapsuffix=",ou=users,dc=example,dc=com"

# Search+Bind方式 (より柔軟)
host    all    all    10.0.0.0/8    ldap ldapserver=ldap.example.com ldapbasedn="ou=users,dc=example,dc=com" ldapsearchattribute=uid ldapbinddn="cn=pgbind,dc=example,dc=com" ldapbindpasswd="bindpass"
```

---

## 3. ロールと権限

### 3.1 ロールの基本

```
┌──────────────────────────────────────────────────────────────┐
│                      ロールの概念                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PostgreSQLでは「ユーザー」と「グループ」の区別がない        │
│  全て「ロール」として統一的に管理                            │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                      admin_role                      │    │
│  │                    (ログイン不可)                     │    │
│  └────────────────────────┬────────────────────────────┘    │
│                           │ GRANT admin_role TO ...         │
│            ┌──────────────┼──────────────┐                  │
│            ▼              ▼              ▼                  │
│     ┌───────────┐  ┌───────────┐  ┌───────────┐            │
│     │  alice    │  │   bob     │  │  charlie  │            │
│     │(ログイン可)│  │(ログイン可)│  │(ログイン可)│            │
│     └───────────┘  └───────────┘  └───────────┘            │
│                                                              │
│  【ロールの属性】                                            │
│  - LOGIN: ログイン可能                                       │
│  - SUPERUSER: スーパーユーザー                               │
│  - CREATEDB: データベース作成可能                            │
│  - CREATEROLE: ロール作成可能                                │
│  - REPLICATION: レプリケーション可能                         │
│  - INHERIT: メンバーシップの権限を継承                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 ロールの作成と管理

```sql
-- ログイン可能なユーザー作成
CREATE ROLE app_user WITH
    LOGIN
    PASSWORD 'secure_password'
    CONNECTION LIMIT 50
    VALID UNTIL '2025-12-31';

-- グループロール作成 (ログイン不可)
CREATE ROLE app_readers;
CREATE ROLE app_writers;
CREATE ROLE app_admins;

-- メンバーシップの付与
GRANT app_readers TO app_user;
GRANT app_writers TO app_user;

-- 管理者ロール
CREATE ROLE db_admin WITH
    LOGIN
    CREATEDB
    CREATEROLE
    PASSWORD 'admin_password';

-- ロールの確認
\du
SELECT * FROM pg_roles;

-- ロールの削除 (所有オブジェクトの移管が必要)
REASSIGN OWNED BY old_user TO new_user;
DROP OWNED BY old_user;
DROP ROLE old_user;
```

### 3.3 権限の種類

```
┌──────────────────────────────────────────────────────────────┐
│                       権限の種類                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【オブジェクトレベル権限】                                  │
│                                                              │
│  ┌─────────────────┬───────────────────────────────────┐    │
│  │ 権限            │ 対象オブジェクト                  │    │
│  ├─────────────────┼───────────────────────────────────┤    │
│  │ SELECT          │ テーブル, ビュー, シーケンス      │    │
│  │ INSERT          │ テーブル                          │    │
│  │ UPDATE          │ テーブル, シーケンス              │    │
│  │ DELETE          │ テーブル                          │    │
│  │ TRUNCATE        │ テーブル                          │    │
│  │ REFERENCES      │ テーブル                          │    │
│  │ TRIGGER         │ テーブル                          │    │
│  │ CREATE          │ データベース, スキーマ            │    │
│  │ CONNECT         │ データベース                      │    │
│  │ TEMPORARY       │ データベース                      │    │
│  │ EXECUTE         │ 関数, プロシージャ                │    │
│  │ USAGE           │ スキーマ, シーケンス, 型          │    │
│  └─────────────────┴───────────────────────────────────┘    │
│                                                              │
│  【特殊権限】                                                │
│  - ALL PRIVILEGES: 全ての権限                               │
│  - WITH GRANT OPTION: 権限を他者に付与可能                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.4 権限の付与と取り消し

```sql
-- データベースへの接続権限
GRANT CONNECT ON DATABASE myapp TO app_user;

-- スキーマへのアクセス権限
GRANT USAGE ON SCHEMA app TO app_readers;
GRANT CREATE ON SCHEMA app TO app_admins;

-- テーブルへの権限
GRANT SELECT ON TABLE app.users TO app_readers;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE app.users TO app_writers;
GRANT ALL PRIVILEGES ON TABLE app.users TO app_admins;

-- スキーマ内の全テーブルへの権限
GRANT SELECT ON ALL TABLES IN SCHEMA app TO app_readers;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO app_writers;

-- 今後作成されるテーブルへのデフォルト権限
ALTER DEFAULT PRIVILEGES IN SCHEMA app
    GRANT SELECT ON TABLES TO app_readers;

ALTER DEFAULT PRIVILEGES IN SCHEMA app
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_writers;

-- シーケンスへの権限
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA app TO app_writers;
ALTER DEFAULT PRIVILEGES IN SCHEMA app
    GRANT USAGE, SELECT ON SEQUENCES TO app_writers;

-- 関数への権限
GRANT EXECUTE ON FUNCTION app.my_function() TO app_users;

-- 権限の取り消し
REVOKE INSERT, UPDATE, DELETE ON TABLE app.users FROM app_readers;

-- 権限の確認
\dp app.users
SELECT * FROM information_schema.table_privileges
WHERE table_schema = 'app' AND table_name = 'users';
```

### 3.5 public スキーマのセキュリティ

```sql
-- デフォルトではpublicスキーマに誰でもオブジェクトを作成できる
-- これはセキュリティリスク

-- public スキーマからの CREATE 権限を削除
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- public スキーマの USAGE 権限を削除 (より厳格)
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- 特定のロールにのみ許可
GRANT USAGE ON SCHEMA public TO app_users;
```

### 3.6 推奨されるロール設計

```sql
-- 1. アプリケーション用ロール階層

-- 読み取り専用ロール
CREATE ROLE app_readonly;
GRANT CONNECT ON DATABASE myapp TO app_readonly;
GRANT USAGE ON SCHEMA app TO app_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA app TO app_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT ON TABLES TO app_readonly;

-- 読み書きロール
CREATE ROLE app_readwrite;
GRANT app_readonly TO app_readwrite;  -- 読み取り権限を継承
GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO app_readwrite;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA app TO app_readwrite;
ALTER DEFAULT PRIVILEGES IN SCHEMA app
    GRANT INSERT, UPDATE, DELETE ON TABLES TO app_readwrite;
ALTER DEFAULT PRIVILEGES IN SCHEMA app
    GRANT USAGE, SELECT ON SEQUENCES TO app_readwrite;

-- 管理者ロール
CREATE ROLE app_admin;
GRANT app_readwrite TO app_admin;
GRANT CREATE ON SCHEMA app TO app_admin;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA app TO app_admin;

-- 2. 実際のユーザーにロールを付与
CREATE ROLE web_app WITH LOGIN PASSWORD 'xxx';
GRANT app_readwrite TO web_app;

CREATE ROLE batch_app WITH LOGIN PASSWORD 'xxx';
GRANT app_readwrite TO batch_app;

CREATE ROLE report_app WITH LOGIN PASSWORD 'xxx';
GRANT app_readonly TO report_app;
```

---

## 4. Row Level Security (RLS)

### 4.1 RLSの概念

```
┌──────────────────────────────────────────────────────────────┐
│                  Row Level Security (RLS)                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  通常の権限: テーブル全体へのアクセス制御                    │
│  RLS: 行単位でのアクセス制御                                 │
│                                                              │
│  【使用例】                                                  │
│  - マルチテナントアプリケーション                            │
│  - 部署ごとのデータ分離                                      │
│  - ユーザーごとに自分のデータのみ表示                        │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  orders テーブル                                      │   │
│  │  ┌────────┬──────────┬─────────┬─────────┐          │   │
│  │  │ id     │ tenant_id│ user_id │ amount  │          │   │
│  │  ├────────┼──────────┼─────────┼─────────┤          │   │
│  │  │ 1      │ A        │ alice   │ 1000    │ ← tenant A│   │
│  │  │ 2      │ A        │ bob     │ 2000    │ ← tenant A│   │
│  │  │ 3      │ B        │ charlie │ 3000    │ ← tenant B│   │
│  │  │ 4      │ B        │ dave    │ 4000    │ ← tenant B│   │
│  │  └────────┴──────────┴─────────┴─────────┘          │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  tenant A のユーザーが SELECT * FROM orders を実行          │
│  → id 1, 2 のみ表示 (tenant_id = 'A' の行のみ)              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 RLSの基本設定

```sql
-- 1. テーブルでRLSを有効化
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

-- 2. ポリシーの作成
-- SELECT用ポリシー
CREATE POLICY orders_tenant_isolation ON orders
    FOR SELECT
    USING (tenant_id = current_setting('app.current_tenant'));

-- INSERT用ポリシー
CREATE POLICY orders_tenant_insert ON orders
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant'));

-- UPDATE用ポリシー
CREATE POLICY orders_tenant_update ON orders
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant'))
    WITH CHECK (tenant_id = current_setting('app.current_tenant'));

-- DELETE用ポリシー
CREATE POLICY orders_tenant_delete ON orders
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant'));

-- 全操作に対するポリシー (ALL)
CREATE POLICY orders_tenant_all ON orders
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant'))
    WITH CHECK (tenant_id = current_setting('app.current_tenant'));
```

### 4.3 アプリケーションからの使用

```sql
-- アプリケーションセッション開始時にテナントを設定
SET app.current_tenant = 'tenant_a';

-- 以降のクエリは自動的にフィルタリング
SELECT * FROM orders;  -- tenant_a の行のみ返る

-- トランザクション内で設定
BEGIN;
SET LOCAL app.current_tenant = 'tenant_a';
SELECT * FROM orders;
COMMIT;
```

### 4.4 ユーザーベースのRLS

```sql
-- ユーザーが自分のデータのみアクセス可能
CREATE POLICY user_isolation ON user_data
    FOR ALL
    USING (user_id = current_user)
    WITH CHECK (user_id = current_user);

-- または SESSION_USER を使用
CREATE POLICY user_isolation ON user_data
    FOR ALL
    USING (user_id = SESSION_USER);
```

### 4.5 複合ポリシー

```sql
-- 複数のポリシーがある場合、OR で結合される

-- 自分のデータへのアクセス
CREATE POLICY own_data ON documents
    FOR SELECT
    USING (owner_id = current_user);

-- 公開データへのアクセス
CREATE POLICY public_data ON documents
    FOR SELECT
    USING (is_public = true);

-- 管理者は全データにアクセス可能
CREATE POLICY admin_access ON documents
    FOR ALL
    TO admin_role
    USING (true);
```

### 4.6 RLSのバイパス

```sql
-- テーブル所有者はデフォルトでRLSをバイパス
-- これを防ぐ場合:
ALTER TABLE orders FORCE ROW LEVEL SECURITY;

-- スーパーユーザーは常にバイパス
-- BYPASSRLS属性を持つロールもバイパス
ALTER ROLE admin_user BYPASSRLS;

-- RLSをバイパスしないロール (デフォルト)
ALTER ROLE app_user NOBYPASSRLS;
```

### 4.7 RLSのデバッグ

```sql
-- ポリシーの確認
\d orders

SELECT * FROM pg_policies WHERE tablename = 'orders';

-- 現在のセッション変数の確認
SELECT current_setting('app.current_tenant', true);

-- RLSを一時的に無効化 (デバッグ用、スーパーユーザーのみ)
SET row_security = off;
SELECT * FROM orders;  -- 全行が見える
SET row_security = on;
```

---

## 5. SSL/TLS暗号化

### 5.1 SSL設定の概要

```
┌──────────────────────────────────────────────────────────────┐
│                      SSL/TLS暗号化                            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────┐                      ┌─────────────┐       │
│  │   Client    │ ◄──── SSL/TLS ────► │   Server    │       │
│  │             │      暗号化通信       │             │       │
│  └─────────────┘                      └─────────────┘       │
│                                                              │
│  【保護される内容】                                          │
│  - 認証情報 (パスワード)                                     │
│  - クエリ内容                                                │
│  - 結果データ                                                │
│                                                              │
│  【SSL設定レベル】                                           │
│  ┌──────────────┬────────────────────────────────────────┐  │
│  │ sslmode      │ 説明                                   │  │
│  ├──────────────┼────────────────────────────────────────┤  │
│  │ disable      │ SSLを使用しない                        │  │
│  │ allow        │ 非SSLを試し、失敗したらSSL            │  │
│  │ prefer       │ SSLを試し、失敗したら非SSL (デフォルト)│  │
│  │ require      │ SSL必須、証明書検証なし               │  │
│  │ verify-ca    │ SSL必須、CA証明書を検証               │  │
│  │ verify-full  │ SSL必須、CA+ホスト名を検証 (推奨)     │  │
│  └──────────────┴────────────────────────────────────────┘  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 サーバー証明書の作成

```bash
# 自己署名証明書の作成 (開発/テスト用)

# 秘密鍵の生成
openssl genrsa -out server.key 2048
chmod 600 server.key
chown postgres:postgres server.key

# 証明書の作成
openssl req -new -key server.key -out server.csr \
    -subj "/CN=postgres.example.com"

openssl x509 -req -in server.csr -signkey server.key \
    -out server.crt -days 365

# ファイルを配置
cp server.key server.crt /var/lib/pgsql/16/data/
```

### 5.3 サーバーのSSL設定

```ini
# postgresql.conf

# SSLを有効化
ssl = on

# 証明書ファイル
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'

# CA証明書 (クライアント証明書認証用)
ssl_ca_file = 'root.crt'

# 証明書失効リスト
ssl_crl_file = ''

# 暗号スイート (TLS 1.2以上を推奨)
ssl_ciphers = 'HIGH:MEDIUM:+3DES:!aNULL'
ssl_prefer_server_ciphers = on

# 最小TLSバージョン
ssl_min_protocol_version = 'TLSv1.2'
```

```bash
# pg_hba.conf でSSL接続を強制
hostssl all all 0.0.0.0/0 scram-sha-256
```

### 5.4 クライアント側のSSL設定

```bash
# 接続文字列でSSLモード指定
psql "host=db.example.com dbname=mydb user=myuser sslmode=verify-full sslrootcert=/path/to/root.crt"

# 環境変数
export PGSSLMODE=verify-full
export PGSSLROOTCERT=/path/to/root.crt

# libpq接続文字列
postgresql://myuser@db.example.com/mydb?sslmode=verify-full&sslrootcert=/path/to/root.crt
```

### 5.5 クライアント証明書認証

```bash
# クライアント証明書の作成
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr \
    -subj "/CN=myuser"
openssl x509 -req -in client.csr -CA root.crt -CAkey root.key \
    -CAcreateserial -out client.crt -days 365

# pg_hba.conf
hostssl all all 0.0.0.0/0 cert

# クライアント接続
psql "host=db.example.com dbname=mydb user=myuser \
    sslmode=verify-full \
    sslcert=client.crt \
    sslkey=client.key \
    sslrootcert=root.crt"
```

---

## 6. 監査ログ

### 6.1 標準ログ設定

```ini
# postgresql.conf

# ログ収集を有効化
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_rotation_age = 1d
log_rotation_size = 100MB

# 接続ログ
log_connections = on
log_disconnections = on

# ステートメントログ
log_statement = 'ddl'  # none, ddl, mod, all

# スロークエリログ
log_min_duration_statement = 1000  # 1秒以上

# ログフォーマット
log_line_prefix = '%t [%p]: user=%u,db=%d,app=%a,client=%h '

# エラーレベル
log_min_messages = warning
log_min_error_statement = error

# その他
log_checkpoints = on
log_lock_waits = on
log_temp_files = 0
```

### 6.2 pgAuditによる詳細監査

```bash
# pgAuditのインストール (RHEL系)
sudo dnf install pgaudit16
```

```ini
# postgresql.conf
shared_preload_libraries = 'pgaudit'

# 監査設定
pgaudit.log = 'read, write, ddl'
pgaudit.log_catalog = off
pgaudit.log_parameter = on
pgaudit.log_statement_once = on
```

```sql
-- データベースで拡張を有効化
CREATE EXTENSION pgaudit;

-- 特定のロールに対する監査
ALTER ROLE auditor SET pgaudit.log = 'all';

-- 特定テーブルの監査
ALTER TABLE sensitive_data SET (pgaudit.log = 'all');
```

### 6.3 監査ログの例

```
# 標準ログ
2024-01-15 10:30:45.123 JST [12345]: user=app_user,db=myapp,app=webapp,client=192.168.1.100
LOG:  statement: SELECT * FROM users WHERE id = 1;

# pgAuditログ
2024-01-15 10:30:45.123 JST [12345]: user=app_user,db=myapp,app=webapp,client=192.168.1.100
LOG:  AUDIT: SESSION,1,1,READ,SELECT,TABLE,public.users,"SELECT * FROM users WHERE id = 1",<none>
```

---

## 7. セキュリティベストプラクティス

### 7.1 チェックリスト

```
┌──────────────────────────────────────────────────────────────┐
│                セキュリティチェックリスト                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【認証】                                                    │
│  □ trust 認証を使用していない                                │
│  □ scram-sha-256 を使用している                              │
│  □ パスワードは十分に複雑                                    │
│  □ パスワード有効期限を設定                                  │
│  □ 接続数制限を設定                                          │
│                                                              │
│  【ネットワーク】                                            │
│  □ listen_addresses を必要最小限に制限                       │
│  □ pg_hba.conf で接続元を制限                                │
│  □ SSL/TLS を有効化                                          │
│  □ ファイアウォールでポートを制限                            │
│                                                              │
│  【権限】                                                    │
│  □ アプリケーションはスーパーユーザーで接続していない        │
│  □ 最小権限の原則を適用                                      │
│  □ public スキーマの権限を制限                               │
│  □ デフォルト権限を適切に設定                                │
│                                                              │
│  【監査】                                                    │
│  □ 接続/切断をログに記録                                     │
│  □ DDL操作をログに記録                                       │
│  □ センシティブデータへのアクセスを監査                      │
│  □ ログを安全に保管                                          │
│                                                              │
│  【その他】                                                  │
│  □ PostgreSQLを最新バージョンに更新                          │
│  □ 不要な拡張機能を削除                                      │
│  □ データファイルのパーミッションを確認 (700)               │
│  □ 定期的なセキュリティレビュー                              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 セキュリティ設定の確認クエリ

```sql
-- スーパーユーザーの確認
SELECT usename, usesuper FROM pg_user WHERE usesuper;

-- trust認証の確認
SELECT * FROM pg_hba_file_rules WHERE auth_method = 'trust';

-- パスワードなしユーザーの確認
SELECT usename FROM pg_shadow WHERE passwd IS NULL;

-- public スキーマの権限確認
SELECT nspname, nspacl FROM pg_namespace WHERE nspname = 'public';

-- 全テーブルの権限確認
SELECT schemaname, tablename, tableowner,
       has_table_privilege('public', schemaname || '.' || tablename, 'SELECT') as public_select
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema');

-- SSL接続の確認
SELECT datname, usename, ssl, client_addr
FROM pg_stat_ssl
JOIN pg_stat_activity USING (pid);

-- 接続制限の確認
SELECT rolname, rolconnlimit FROM pg_roles WHERE rolconnlimit > 0;
```

### 7.3 定期的なセキュリティタスク

```bash
# パスワードの定期変更
ALTER USER app_user WITH PASSWORD 'new_secure_password';

# 不要な接続の確認と終了
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename = 'suspicious_user';

# 権限の棚卸し
SELECT grantee, privilege_type, table_schema, table_name
FROM information_schema.table_privileges
WHERE grantee NOT IN ('postgres')
ORDER BY grantee, table_schema, table_name;

# ログのレビュー
grep -E "(FATAL|ERROR|authentication failed)" /var/lib/pgsql/16/data/log/*.log
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - クライアント認証](https://www.postgresql.org/docs/current/client-authentication.html)
- [PostgreSQL公式ドキュメント - ロール](https://www.postgresql.org/docs/current/user-manag.html)
- [PostgreSQL公式ドキュメント - Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [PostgreSQL公式ドキュメント - SSL](https://www.postgresql.org/docs/current/ssl-tcp.html)
- [pgAudit](https://www.pgaudit.org/)
- [OWASP Database Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Database_Security_Cheat_Sheet.html)
