---
name: dev-database
description: データベースアダプター（PostgreSQL/MySQL両対応）の開発・修正を行う際に使用。SqlExecutorパターン、マイグレーション、DB固有SQL実装時に役立つ。
---

# データベースアダプター開発ガイド

## ドキュメント

- `libs/idp-server-database/README.md` - データベース設計・マイグレーション

---

## 機能概要

データベースアダプターは、PostgreSQL/MySQL両方をサポートする永続化層。
- **両DB対応**: 全機能でPostgreSQL/MySQL両方実装必須
- **SqlExecutorパターン**: DB固有SQLを抽象化
- **Flyway**: マイグレーション管理
- **読み書き分離**: `@Transaction` / `@Transaction(readOnly = true)` アノテーション

---

## モジュール構成

```
libs/
├── idp-server-core-adapter/                    # 永続化実装
│   └── .../adapters/datasource/
│       ├── audit/
│       │   ├── command/
│       │   │   ├── AuditLogSqlExecutor.java       # インターフェース
│       │   │   ├── AuditLogSqlExecutors.java      # ファクトリ
│       │   │   ├── PostgresqlExecutor.java        # PostgreSQL実装
│       │   │   └── MysqlExecutor.java             # MySQL実装
│       │   └── query/
│       │       └── (同様の構造)
│       ├── token/
│       ├── identity/
│       ├── authentication/
│       ├── ciba/
│       └── ...
│
├── idp-server-database/                        # マイグレーション
│   ├── postgresql/
│   │   ├── V0_9_0__init_lib.sql               # 初期スキーマ
│   │   └── V1_0_0__*.sql                      # バージョン別マイグレーション
│   └── mysql/
│       ├── V0_9_0__init_lib.mysql.sql         # ※ .mysql.sql 接尾辞
│       └── V0_9_21_1__*.mysql.sql
│
└── idp-server-platform/                        # 共通基盤
    └── .../datasource/
        ├── DatabaseType.java                   # POSTGRESQL, SPANNER, MYSQL
        ├── SqlExecutor.java                    # SQL実行ヘルパー
        └── TransactionManager.java             # トランザクション管理
```

---

## SqlExecutor パターン

### 基本構造

`AuditLogSqlExecutor.java`:

```java
// 1. インターフェース定義（Tenant第一引数）
public interface AuditLogSqlExecutor {
    void insert(Tenant tenant, AuditLog auditLog);
}
```

`PostgresqlExecutor.java`:

```java
// 2. PostgreSQL実装
public class PostgresqlExecutor implements AuditLogSqlExecutor {
    @Override
    public void insert(Tenant tenant, AuditLog auditLog) {
        // SqlExecutorが内部でTransactionManager.getConnection()を使用
        SqlExecutor sqlExecutor = new SqlExecutor();
        String sqlTemplate = """
            INSERT INTO audit_log (
                id, type, description, tenant_id, client_id, user_id, ...
            ) VALUES (
                ?::uuid, ?, ?, ?::uuid, ...
            );
            """;
        List<Object> params = new ArrayList<>();
        params.add(auditLog.id().value());
        // ...
        sqlExecutor.execute(sqlTemplate, params);
    }
}
```

`MysqlExecutor.java`:

```java
// 3. MySQL実装
public class MysqlExecutor implements AuditLogSqlExecutor {
    @Override
    public void insert(Tenant tenant, AuditLog auditLog) {
        SqlExecutor sqlExecutor = new SqlExecutor();
        String sqlTemplate = """
            INSERT INTO audit_log (
                id, type, description, tenant_id, client_id, user_id, ...
            ) VALUES (
                ?, ?, ?, ?, ...
            );
            """;
        // MySQL固有: UUIDキャストなし
        // ...
    }
}
```

`AuditLogSqlExecutors.java`:

```java
// 4. ファクトリ（ディスパッチャー）
public class AuditLogSqlExecutors {
    Map<DatabaseType, AuditLogSqlExecutor> executors;

    public AuditLogSqlExecutors() {
        executors = new HashMap<>();
        executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
        executors.put(DatabaseType.MYSQL, new MysqlExecutor());
    }

    public AuditLogSqlExecutor get(DatabaseType databaseType) {
        return executors.get(databaseType);
    }
}
```

---

## 読み書き分離

EntryServiceレイヤーで `@Transaction` アノテーションにより制御:

```java
// 書き込み（Primary）
@Transaction
public class OAuthFlowEntryService { ... }

// 読み取り専用（Replica）
@Transaction(readOnly = true)
public class UserinfoEntryService { ... }

@Transaction(readOnly = true)
public class OidcMetaDataEntryService { ... }
```

---

## PostgreSQL vs MySQL 差異

### INSERT時の競合処理

| 操作 | PostgreSQL | MySQL |
|------|-----------|-------|
| UPSERT | `ON CONFLICT (id) DO UPDATE SET ...` | `ON DUPLICATE KEY UPDATE ...` |
| INSERT IGNORE | `ON CONFLICT DO NOTHING` | `INSERT IGNORE INTO ...` |

### UUID型

| 操作 | PostgreSQL | MySQL |
|------|-----------|-------|
| UUIDキャスト | `?::uuid` | `?` (文字列として保存) |

### JSON操作

| 操作 | PostgreSQL | MySQL |
|------|-----------|-------|
| JSON型 | `jsonb` | `JSON` |
| JSON抽出 | `column->>'key'` | `JSON_UNQUOTE(JSON_EXTRACT(column, '$.key'))` |
| JSON検索 | `column @> '{"key": "value"}'` | `JSON_CONTAINS(column, '"value"', '$.key')` |

### 日時操作

| 操作 | PostgreSQL | MySQL |
|------|-----------|-------|
| 現在時刻 | `NOW()` | `NOW()` |
| タイムゾーン | `AT TIME ZONE 'UTC'` | `CONVERT_TZ(..., '+00:00', 'UTC')` |
| 間隔 | `INTERVAL '1 day'` | `INTERVAL 1 DAY` |

---

## マイグレーション

### ファイル命名規則

```
PostgreSQL: V{major}_{minor}_{patch}__{description}.sql
MySQL:      V{major}_{minor}_{patch}__{description}.mysql.sql

例:
- postgresql/V1_2_0__add_user_status_column.sql
- mysql/V1_2_0__add_user_status_column.mysql.sql
```

**注意**: MySQLは `.mysql.sql` 接尾辞が必須。

### 実行方法

```bash
# Docker経由（推奨）
docker compose up flyway-migrator

# 直接実行
./gradlew :libs:idp-server-database:flywayMigrate
```

### 両DB対応マイグレーション

PostgreSQL と MySQL で同じバージョン番号のファイルを作成:

```
libs/idp-server-database/
├── postgresql/
│   └── V1_2_0__add_user_status_column.sql
└── mysql/
    └── V1_2_0__add_user_status_column.mysql.sql
```

---

## Command / Query 分離

### 構造パターン

```
datasource/{domain}/
├── command/                    # 書き込み操作
│   ├── {Domain}CommandSqlExecutor.java
│   ├── {Domain}CommandSqlExecutors.java
│   ├── PostgresqlExecutor.java
│   └── MysqlExecutor.java
└── query/                      # 読み取り操作
    ├── {Domain}QuerySqlExecutor.java
    ├── {Domain}QuerySqlExecutors.java
    ├── PostgresqlExecutor.java
    └── MysqlExecutor.java
```

---

## DatabaseType

`DatabaseType.java`:

```java
public enum DatabaseType {
    POSTGRESQL,
    SPANNER,     // Google Cloud Spanner
    MYSQL;
}
```

---

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core-adapter:compileJava
./gradlew :libs:idp-server-database:build

# マイグレーション確認
./gradlew :libs:idp-server-database:flywayInfo

# テスト
./gradlew :libs:idp-server-core-adapter:test
```

---

## チェックリスト

新規DataSource実装時:

- [ ] SqlExecutor インターフェース作成（Tenant第一引数）
- [ ] PostgresqlExecutor 実装
- [ ] MysqlExecutor 実装
- [ ] SqlExecutors ファクトリ作成
- [ ] DataSource クラス作成
- [ ] PostgreSQL マイグレーション追加（.sql）
- [ ] MySQL マイグレーション追加（.mysql.sql）
- [ ] 両DBでテスト実行

---

## ページネーション（LIMIT/OFFSET）

### 必須ルール

1. **ORDER BYを必ず指定する** — ORDER BYなしのLIMIT/OFFSETは非決定的。ページ送りでレコードが重複・欠落する。
2. **tie-breakerとしてPKを追加する** — `ORDER BY created_at DESC` だけでは、同一`created_at`のレコード間でページ境界が非決定的。`ORDER BY created_at DESC, id DESC` のようにPKを追加して完全に決定的にする。
3. **CTEで先にLIMITしてからJOINする** — 全行をJOIN→GROUP BY→LIMITするとデータ量に比例して遅くなる。CTEで先にLIMIT/OFFSETしてからJOINすれば、JOINの対象行がLIMIT件数分のみになり大幅に高速化できる。

### パターン

```sql
-- NG: 全行JOIN→GROUP BY→LIMIT（200万行で3秒）
SELECT idp_user.*, roles, permissions
FROM idp_user
LEFT JOIN idp_user_roles ON ...
WHERE idp_user.tenant_id = ?
GROUP BY idp_user.id
ORDER BY idp_user.created_at DESC, idp_user.id DESC
LIMIT ? OFFSET ?;

-- OK: CTEで先にLIMIT→JOIN（200万行で185ms）
WITH paged_users AS (
  SELECT id, created_at FROM idp_user
  WHERE tenant_id = ?
  ORDER BY created_at DESC, id DESC
  LIMIT ? OFFSET ?
)
SELECT idp_user.*, roles, permissions
FROM paged_users
JOIN idp_user ON idp_user.id = paged_users.id
LEFT JOIN idp_user_roles ON ...
WHERE idp_user.id IN (SELECT id FROM paged_users)
GROUP BY idp_user.id
ORDER BY idp_user.created_at DESC, idp_user.id DESC;
```

### JOINテーブルの条件をCTEに含める

role/permissionなどJOINテーブルでフィルタする場合、CTEの外に条件を残すとページネーションが壊れる（LIMIT後にさらに絞り込まれて件数が減る）。CTE内にJOINを含めて正しく絞り込んでからLIMITする。

```java
if (hasRoleOrPermissionFilter) {
  cteFrom = """
    SELECT DISTINCT idp_user.id, idp_user.created_at FROM idp_user
    LEFT JOIN idp_user_roles ON idp_user.id = idp_user_roles.user_id
    LEFT JOIN role ON idp_user_roles.role_id = role.id
    ...
    """;
} else {
  cteFrom = "SELECT id, created_at FROM idp_user ";
}
```

---

## 悲観ロック（SELECT FOR UPDATE）

### 背景（Issue #1454）

`authentication_transaction` のように状態遷移を持つリソースに対して、複数リクエストが同時にDELETEを実行すると、`ON DELETE CASCADE` による子テーブル（`authentication_interactions`）の行削除順序が異なり、PostgreSQLでデッドロックが発生する。

```
Process A: DELETE authentication_transaction → CASCADE DELETE interactions (row 1, row 2)
Process B: DELETE authentication_transaction → CASCADE DELETE interactions (row 2, row 1)
→ 循環待ち → deadlock detected
```

### 対策パターン

親テーブルの行を `SELECT FOR UPDATE` で先にロックし、並行操作を直列化する。

```java
// QueryRepository インターフェース
AuthenticationTransaction getForUpdate(Tenant tenant, AuthenticationTransactionIdentifier identifier);
AuthenticationTransaction getForUpdate(Tenant tenant, AuthorizationIdentifier identifier);
```

```sql
-- PostgreSQL
SELECT ... FROM authentication_transaction
WHERE id = ?::uuid AND tenant_id = ?::uuid
FOR UPDATE

-- MySQL（同一構文で動作）
SELECT ... FROM authentication_transaction
WHERE id = ? AND tenant_id = ?
FOR UPDATE
```

### EntryService での使い方

`@Transaction` 内で `getForUpdate()` により再取得し、以降の処理はロック済みオブジェクトを使う。

```java
@Transaction
public class CibaFlowEntryService {
  public AuthenticationInteractionRequestResult interact(
      ..., AuthenticationTransaction authenticationTransaction, ...) {

    // 引数の authenticationTransaction はトランザクション外で取得済み。
    // @Transaction 内で FOR UPDATE 付きで再取得して悲観ロックを取る。
    AuthenticationTransaction lockedTransaction =
        authenticationTransactionQueryRepository.getForUpdate(
            tenant, authenticationTransaction.identifier());

    // 以降は lockedTransaction を使い、元の authenticationTransaction は使わない
  }
}
```

### 適用箇所

状態遷移を持ち、並行書き込み（DELETE/UPDATE）が発生しうるリソースに適用:

| EntryService | メソッド | 理由 |
|-------------|---------|------|
| `CibaFlowEntryService` | `interact()` | FIDO-UAF認証完了 + キャンセルの同時到着 |
| `OAuthFlowEntryService` | `interact()`, `authorize()`, `deny()`, `callbackFederation()` | 二重クリック、タブ重複 |
| `UserOperationEntryService` | `interact()` | デバイス認証操作の並行実行 |

### 注意点

- `SELECT FOR UPDATE` はトランザクション内でのみ有効（`@Transaction` 必須）
- ロック保持中に外部サービス呼び出し（FIDO-UAF等）があると、その応答時間分だけロックが保持される。現状はデッドロック（500）よりマシというトレードオフ
- 読み取り専用クエリ（`get()` / `findList()`）には `FOR UPDATE` を付けない

---

## SQL エラーハンドリング

### SqlError 分類

`SqlErrorClassifier` がDB固有のエラーコードを統一分類し、`SqlExecutor` が適切な例外に変換する。

| SqlError | PostgreSQL | MySQL | 例外クラス | HTTP |
|----------|-----------|-------|-----------|------|
| `UNIQUE_VIOLATION` | 23505 | 1062 | `SqlDuplicateKeyException` | 409 |
| `FK_VIOLATION` | 23503 | 1451/1452 | `SqlForeignKeyViolationException` | 404 |
| `NOT_NULL_VIOLATION` | 23502 | 1048 | `SqlBadRequestException` | 400 |
| `CHECK_VIOLATION` | 23514 | 3819 | `SqlBadRequestException` | 400 |
| `DEADLOCK_DETECTED` | 40P01 | 1213 | `SqlTransactionConflictException` | 409 |
| `SERIALIZATION_FAILURE` | 40001 | 1205 | `SqlTransactionConflictException` | 409 |
| `OTHER` | — | — | `SqlRuntimeException` | 500 |

### FK違反の用途

`authentication_transaction` がCASCADE DELETEされた後に、別トランザクションが子テーブルへINSERT/UPDATEしようとした場合に発生。`ApiExceptionHandler` で「セッション期限切れ」として404を返す。

### トランザクション競合の用途

`SELECT FOR UPDATE` を導入してもデッドロックが完全にゼロにはならない（想定外のロック順序、アプリケーション外からの操作等）。`SqlTransactionConflictException` → 409でクライアントにリトライ可能であることを伝える。

---

## トラブルシューティング

### SQL構文エラー

| 問題 | 原因 | 解決策 |
|------|------|--------|
| PostgreSQLで動くがMySQLでエラー | DB固有構文の混在 | 両Executor実装を確認 |
| UUIDキャストエラー | `?::uuid`がMySQLで失敗 | MySQL版では文字列として扱う |
| JSONクエリ失敗 | JSON関数の差異 | 上記JSON操作表を参照 |

### デッドロック / ロック競合

| 問題 | 原因 | 解決策 |
|------|------|--------|
| `deadlock detected` (PostgreSQL 40P01) | CASCADE DELETE で子テーブルの行ロック順序が競合 | 親テーブルに `SELECT FOR UPDATE` を適用 |
| `Lock wait timeout exceeded` (MySQL 1205) | FOR UPDATE のロック保持時間が長い | 外部サービス呼び出しをロック区間外に分離検討 |
| FK violation after concurrent DELETE | 別トランザクションが先に親行を削除済み | `SqlForeignKeyViolationException` で404返却 |

### マイグレーション失敗

| 問題 | 原因 | 解決策 |
|------|------|--------|
| MySQLでファイル認識されない | 接尾辞が`.sql` | `.mysql.sql`に変更 |
| バージョン競合 | 既存マイグレーションとの衝突 | `flyway repair` 実行 |
| スキーマ不整合 | 手動変更との不一致 | `docker compose down -v` で初期化 |

---

## Redis Cache 実装

### CacheStore インターフェース

```java
public interface CacheStore {
  <T> void put(String key, T value);
  <T> void put(String key, T value, int timeToLiveSeconds);
  <T> Optional<T> find(String key, Class<T> type);
  boolean exists(String key);
  void delete(String key);
  long increment(String key, int timeToLiveSeconds);  // Lua スクリプトによるアトミックカウンター
}
```

実装: `JedisCacheStore`（Jedis ベース）。全操作で例外はログ出力のみ（non-blocking）。

### 用途

- Rate limiting カウンター（`increment`）
- 認証試行回数トラッキング
- トークン Introspection キャッシュ（TTL 60秒）

---

## 暗号化・ハッシュ化

### AesCipher vs HmacHasher

| 要件 | AesCipher | HmacHasher |
|------|-----------|------------|
| 元の値を復元する必要がある | ✓ (decrypt) | ✗ |
| 検索キーとして使う | ✗ | ✓ (hash して比較) |
| 機密データ保存 | ✓ | ✗ |
| PII（email, phone） | ✓ (復号して表示) | ✗ |

- **AesCipher**: AES-256-GCM、12byte IV、`EncryptedData`（ciphertext + IV）を返す
- **HmacHasher**: HMAC-SHA256、Base64URL エンコード、一方向（verify メソッドなし）

---

## Row Level Security (RLS) - PostgreSQL Only

PostgreSQL で行レベルのテナント分離を実現。

```sql
ALTER TABLE client_configuration ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON client_configuration
  USING (tenant_id = current_setting('app.tenant_id')::UUID);
```

`TransactionManager` が `SET LOCAL app.tenant_id = '{tenantId}'` でコネクションレベルの設定を行い、RLS ポリシーが自動でフィルタリングする。`LOCAL` スコープのためトランザクション終了時に自動クリア。

MySQL では RLS 非対応のため、アプリケーション層での明示的 WHERE 句が必要。

---

## JSONB vs TEXT 設計判断

### TEXT 化判断軸

JSONB カラムを TEXT に変える判断は、アプリ層の使い方で決まる：

| アプリ層の使い方 | TEXT 化可否 |
|---|---|
| `JSON.stringify` で write、`JSON.parse` で read（フルスナップショット）| ✅ TEXT 化可能 |
| `->>` / `->` 演算子で内部 path クエリ | ⚠️ クエリ実装次第（後述）|
| `@>` 演算子（JSONB 含包）+ GIN index | ❌ JSONB 維持 |
| `?` / `?&` / `?|` 演算子 + GIN index | ❌ JSONB 維持 |

→ **アプリで `JSON.parse` してドメインオブジェクト化するだけのカラムは TEXT 化候補**。  
→ JSONB の検索能力を使ってないカラムは、**書き込みコストだけ払って読みで得てない**。

### GIN(JSONB) の落とし穴

**`->>` 演算子では GIN が使われない**:

- GIN(detail) は `@>` / `?` / `?&` / `?|` でのみ planner に選ばれる
- アプリが `detail ->> 'key' = ?` で検索してる場合、**GIN は無視され Seq Scan + Filter**
- それでも書き込み時は GIN entry 更新コストが発生 → 完全に無駄

**確認方法**:

```sql
-- 該当カラムでの実際の検索クエリパターンを EXPLAIN ANALYZE
EXPLAIN (ANALYZE, BUFFERS)
SELECT ... FROM target_table WHERE detail @> '...'::jsonb;
-- vs
EXPLAIN (ANALYZE, BUFFERS)
SELECT ... FROM target_table WHERE detail ->> 'key' = '...';
```

`idx_scan = 0` の GIN は削除候補：

```sql
SELECT indexrelname, idx_scan, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
WHERE indexrelname LIKE '%detail%';
```

### TEXT 化の効果と代償

**効果**:
- INSERT/UPDATE 毎の `jsonb_in` パースコスト削減（大きい payload で顕著）
- GIN index 更新コスト削減（GIN を同時削除した場合）

**代償**:
- DB レベルで JSON 構造検証されなくなる → アプリ層で保証する責任
- 将来 JSON path 検索が必要になった場合、TEXT のままだと Seq Scan + Filter（パフォーマンス劣化）
- 該当カラムへの GIN/部分インデックスは作れない

---

## v2 新規テーブル swap パターン（zero-downtime な型変更）

`ALTER TABLE ... ALTER COLUMN TYPE` は **`AccessExclusiveLock` で全行 rewrite** が走り、本番では長時間ロックの原因に。新規テーブル方式で回避する。

### 適用条件

- **短命データ**（数分〜数時間）が理想：旧テーブルは expire 待ちで自然減
- 例: `authorization_request`、`authentication_transaction`、`ciba_grant` などの OAuth/CIBA フロー中間状態テーブル
- 長命データ（同意保持等）は dual-write + backfill が必要

### 手順

1. **新テーブル `*_v2` を `CREATE`**（既存と並行）
   - JSONB → TEXT、その他スキーマは同等
   - PK / FK / Index / RLS policy も移植
   - FK が他の v2 テーブルを参照する場合は v2 同士で繋ぐ（例: `authorization_code_grant_v2.authorization_request_id` → `authorization_request_v2.id`）
2. **Executor の SQL 文を v2 テーブルへ切替**
   - INSERT/UPDATE/DELETE/SELECT すべて v2 へ
   - `?::jsonb` キャストも同時削除（TEXT 化したカラム分）
3. **アプリをデプロイ**
   - 新規データは v2 に書き込まれる
   - 旧 v1 テーブルへは書き込みなし、読み取りもなし
4. **旧データの expire を待つ**
   - 数時間〜数日で v1 は空になる
5. **旧 v1 テーブルを DROP**（別 migration で）

### 制約

- 旧 v1 データを参照する経路がある場合（例: 起動直後の進行中フロー）、**dual-read** or **dual-write** 期間が必要
- Executor 修正時、テーブル名置換で `authorization_request_id` 等のカラム名を誤置換しないよう注意（`\b` 境界での置換が安全）

### 該当 migration 例

- PostgreSQL: `V0_10_2__authentication_v2_text_tables.sql`、`V0_10_4__oauth_ciba_v2_text_tables.sql`
- MySQL: 同名の `.mysql.sql`

---

## Index 設計の原則

### 単独 index vs 複合 index

`(A)` 単独 index と `(A, B, C)` 複合 index が両方ある場合、**多くのケースで単独は冗長**：

- 複合 index は `WHERE A = ?` でも **プレフィックス検索**で使える
- planner はテーブルサイズ次第で複合を選ぶ
- 単独 index は書き込みコストだけ払う

### 冗長 index の検出

```sql
-- 各 index の使用統計
SELECT
  relname, indexrelname, idx_scan, idx_tup_read,
  pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;
```

- `idx_scan = 0` → 読み取りで使われてない、書き込みコストだけ
- 単独 index が複合 index にカバーされるか確認:
  ```sql
  SELECT indexrelname, pg_get_indexdef(indexrelid)
  FROM pg_stat_user_indexes WHERE relname = '<table>';
  ```

### 削除判断フロー

```
[index 候補]
   │
   ▼
[idx_scan = 0?]
   │ Yes
   ▼ → 削除可（書き込みコストだけ払ってる）
   │ No
   ▼
[プレフィックスとして複合にカバーされる?]
   │ Yes
   ▼ → 削除可（planner は複合を選ぶ）
   │ No
   ▼
[特定の検索クエリで実際に選ばれる?]
   ├ Yes → 維持
   └ No  → 削除候補（EXPLAIN ANALYZE で再確認）
```

### バッチ統計集計と index

バッチ集計クエリ（`WHERE created_at >= X`）はパーティション pruning が一次フィルタとして効くので、**`created_at` 単独 index は必須ではない**。  
ただし、パーティション内で Index Only Scan を期待する場合は維持が安全。

---

## 関連スキル

| スキル | 用途 |
|--------|------|
| `/ops-local-env` | ローカルDB環境構築 |
| `/dev-architecture` | アーキテクチャ全体像 |
| `/ops-deployment` | 運用・監視 |
| `/test-performance` | 性能計測・チューニング |
| `/perf-improvement-playbook` | 性能改善 現状調査プレイブック |
