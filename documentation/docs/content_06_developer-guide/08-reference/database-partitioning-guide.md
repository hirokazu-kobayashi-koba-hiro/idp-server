# データベースパーティショニングガイド

## 1. 概要

本ドキュメントでは、idp-serverにおけるPostgreSQLパーティショニングの設計指針と実装パターンを説明します。

### 1.1 パーティショニングとは

パーティショニングは、1つの論理テーブルを複数の物理テーブル（パーティション）に分割する技術です。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    security_event (親テーブル = 論理的な窓口)               │
│                                                                             │
│  「私はただの入り口です。実データは子テーブルが持っています」                │
│                                                                             │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────────────────┐
          │                         │                                     │
          ▼                         ▼                                     ▼
  ┌───────────────┐         ┌───────────────┐                     ┌───────────────┐
  │ _2025_12_01   │         │ _2025_12_02   │         ...         │ _2025_12_90   │
  │               │         │               │                     │               │
  │ 12/01のデータ │         │ 12/02のデータ │                     │ 90日後のデータ│
  │               │         │               │                     │               │
  │ 物理ファイルA │         │ 物理ファイルB │                     │ 物理ファイルN │
  └───────────────┘         └───────────────┘                     └───────────────┘

各パーティション = 独立した物理ファイル
```

### 1.2 主なメリット

| 項目 | 従来方式 | パーティショニング |
|------|----------|-------------------|
| **古いデータ削除** | DELETE文（数時間〜数日） | DROP TABLE（数秒） |
| **VACUUM負荷** | 高い | なし |
| **日付範囲クエリ** | 全件スキャン | パーティションプルーニング |
| **ストレージ管理** | 手動管理 | 自動削除で一定量維持 |

---

## 2. DELETE vs DROP TABLE

### 2.1 DELETE文の動作（通常テーブル）

```sql
DELETE FROM security_event WHERE created_at < '2025-09-05';
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          security_event                                     │
│                                                                             │
│  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐              │
│  │ 行1 │ 行2 │ 行3 │ 行4 │ 行5 │ 行6 │ 行7 │ 行8 │ 行9 │行10 │ ...          │
│  │ 古い│ 古い│ 新し│ 古い│ 新し│ 古い│ 新し│ 古い│ 新し│ 古い│              │
│  └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘              │
│     ↓     ↓           ↓           ↓           ↓           ↓                │
│    削除  削除        削除        削除        削除        削除               │
│   マーク マーク      マーク      マーク      マーク      マーク              │
│                                                                             │
│  処理内容:                                                                  │
│  1. 全行をスキャン (WHERE条件チェック)     ← 時間かかる                     │
│  2. 該当行に「削除済み」マークを付ける     ← 時間かかる                     │
│  3. トランザクションログに記録             ← I/O負荷                        │
│  4. 後でVACUUMで実際に領域回収             ← さらに時間かかる               │
│                                                                             │
│  結果: 5億行削除 = 数時間〜数日                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 DROP TABLEの動作（パーティションテーブル）

```sql
DROP TABLE security_event_2025_09_05;
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  パーティション = 独立した物理ファイル                                      │
│                                                                             │
│  ┌─────────────────┐                                                        │
│  │ _2025_09_05     │   rm /var/lib/postgresql/.../security_event_...        │
│  │                 │   ──────────────────────────────────────────────▶      │
│  │  物理ファイル   │                   ファイル削除！                        │
│  │    625GB        │                                                        │
│  └─────────────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│        🗑️ ゴミ箱へ（ファイルシステムレベルで即削除）                         │
│                                                                             │
│  処理内容:                                                                  │
│  1. メタデータ（カタログ）から登録解除    ← 一瞬                            │
│  2. ファイルシステムがファイル削除        ← 一瞬                            │
│                                                                             │
│  結果: 625GB削除 = 数秒                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 処理時間比較（5億行/日の場合）

| 操作 | DELETE方式 | DROP TABLE方式 |
|------|-----------|---------------|
| 該当行特定 | 30分 | - |
| 削除マーク付与 | 2時間 | - |
| トランザクションログ | 1時間 | - |
| VACUUM | 3時間 | - |
| メタデータ更新 | - | 0.1秒 |
| ファイル削除 | - | 0.5秒 |
| **合計** | **約6〜7時間** | **約1秒** |

---

## 3. パーティションプルーニング

クエリ実行時に、条件に合致するパーティションのみをスキャンする最適化機能です。

### 3.1 プルーニングが効く場合

```sql
SELECT * FROM security_event
WHERE created_at >= '2025-12-01' AND created_at < '2025-12-04';
```

```
┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐
│ 11/29     │ │ 11/30     │ │ 12/01     │ │ 12/02     │ │ 12/03     │
│           │ │           │ │  ✓ SCAN   │ │  ✓ SCAN   │ │  ✓ SCAN   │
│  ✗ SKIP   │ │  ✗ SKIP   │ │           │ │           │ │           │
└───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘

┌───────────┐ ┌───────────┐ ┌───────────┐
│ 12/04     │ │ 12/05     │ │ ...       │
│           │ │           │ │           │
│  ✗ SKIP   │ │  ✗ SKIP   │ │  ✗ SKIP   │
└───────────┘ └───────────┘ └───────────┘

結果: 90パーティション中、3パーティションのみスキャン = 約30倍高速
```

### 3.2 プルーニングが効かない場合

```sql
-- パーティションキー（created_at）を指定していない
SELECT * FROM security_event
WHERE user_id = 'user-uuid-xxx';
```

```
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ 12/01   │ │ 12/02   │ │ 12/03   │ │ ...     │ │ 03/01   │
│  SCAN   │ │  SCAN   │ │  SCAN   │ │  SCAN   │ │  SCAN   │
└─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘
                  ↑
        全90パーティションをスキャン（通常テーブルより遅くなる可能性）
```

---

## 4. パーティショニングの制約

### 4.1 PRIMARY KEY の制約

パーティションキーをPRIMARY KEYに含める必要があります。

```sql
-- ❌ 不可
PRIMARY KEY (id)

-- ✅ 必須
PRIMARY KEY (id, created_at)
```

**理由**: PostgreSQLは「このidはどのパーティションにある？」を効率的に判断するため

### 4.2 外部キー（FOREIGN KEY）の制約

他テーブルからパーティションテーブルへの外部キー参照は作成できません。

```sql
-- ❌ エラーになる
CREATE TABLE security_event_detail (
    id UUID PRIMARY KEY,
    security_event_id UUID,
    FOREIGN KEY (security_event_id) REFERENCES security_event(id)
);
```

**回避策**:
- アプリケーション層で整合性チェック
- RLSで制御

### 4.3 運用上のオーバーヘッド

| 項目 | 内容 |
|------|------|
| パーティション作成 | 毎日自動実行（pg_cron） |
| パーティション削除 | 毎日自動実行（pg_cron） |
| 監視 | pg_cronジョブ状態、パーティション数 |
| 障害対応 | DEFAULTパーティション肥大化の監視 |

---

## 5. パーティショニング適性判断

### 5.1 チェックリスト

パーティショニングを検討する際は、以下を確認してください。

| チェック項目 | 説明 |
|-------------|------|
| □ 主要クエリでパーティションキーを指定できるか？ | 日付範囲検索が主なら適している |
| □ 古いデータを「期間単位」で削除したいか？ | 90日経過で一括削除など |
| □ 外部キー参照がないか？ | 参照されている場合は不可 |
| □ PRIMARY KEY変更の影響を許容できるか？ | 複合キーへの変更 |

### 5.2 idp-serverのテーブル適性

| テーブル | 適性 | 理由 |
|---------|------|------|
| `security_event` | ✓ 適している | 日付検索が主、期間削除したい |
| `security_event_hook_results` | ✓ 適している | 同上 |
| `audit_log` | ✗ 不要 | 永続保存（コンプライアンス要件） |
| `oauth_token` | ✗ 不向き | ハッシュ検索が主、日付指定不可（後述） |
| `tenant` | ✗ 不向き | マスタデータ、外部キー参照あり |
| `idp_user` | ✗ 不向き | マスタデータ |

---

## 6. oauth_token がパーティショニングに不向きな理由

### 6.1 イントロスペクションの検索パターン

```
POST /introspect
token=eyJhbGciOiJSUzI1NiIs...
```

アプリケーション側の処理:

```sql
-- トークン文字列をハッシュ化してDBで検索
SELECT * FROM oauth_token
WHERE tenant_id = :tenantId
  AND hashed_access_token = :hashedToken;

-- ※ created_at は検索条件に使えない
-- ※ クライアントはトークン文字列しか知らない
```

### 6.2 パーティショニングした場合の問題

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  仮に PARTITION BY RANGE (access_token_created_at) とした場合               │
│                                                                             │
│  SELECT * FROM oauth_token                                                  │
│  WHERE hashed_access_token = :hash;                                         │
│        ↑ access_token_created_at は不明！                                   │
│                                                                             │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐               │
│  │ 12/01   │ │ 12/02   │ │ 12/03   │ │ ...     │ │ 03/01   │               │
│  │  SCAN   │ │  SCAN   │ │  SCAN   │ │  SCAN   │ │  SCAN   │               │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘               │
│                               ↑                                             │
│                    全90パーティションをスキャン！                            │
│                    パーティションプルーニング効かない                        │
│                                                                             │
│  結果:                                                                      │
│  - パーティショニングのメリット（検索高速化）が得られない                    │
│  - むしろオーバーヘッドで遅くなる可能性                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 oauth_token の運用戦略

| 方式 | 説明 | メリット | デメリット |
|------|------|----------|-----------|
| 定期DELETE + VACUUM | 従来方式で期限切れトークンを削除 | シンプル | 大量データ時は重い |
| TTLインデックス | PostgreSQL拡張で自動削除 | 自動化 | 拡張導入が必要 |
| アプリ層での期限チェック | DBに問い合わせず期限判定 | DB負荷軽減 | 取り消し対応が別途必要 |

---

## 7. 実装例

### 7.1 パーティションテーブル作成（V0_9_1）

```sql
-- 親テーブル作成
CREATE TABLE security_event (
    id UUID,
    type VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    detail JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id, created_at)  -- パーティションキーを含める
)
PARTITION BY RANGE (created_at);

-- デフォルトパーティション（安全弁）
CREATE TABLE security_event_default PARTITION OF security_event DEFAULT;

-- 日別パーティション作成（90日分）
DO $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..89 LOOP
        partition_date := CURRENT_DATE + (i || ' days')::interval;
        partition_name := 'security_event_' || to_char(partition_date, 'YYYY_MM_DD');

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF security_event
             FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            to_char(partition_date, 'YYYY-MM-DD'),
            to_char(partition_date + interval '1 day', 'YYYY-MM-DD')
        );
    END LOOP;
END $$;
```

### 7.2 自動パーティション管理（V0_9_2）

```sql
-- pg_cron拡張有効化
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- 新規パーティション作成関数
CREATE OR REPLACE FUNCTION create_next_day_partitions()
RETURNS void AS $$
DECLARE
    next_day DATE := CURRENT_DATE + interval '90 days';
    partition_name TEXT;
BEGIN
    partition_name := 'security_event_' || to_char(next_day, 'YYYY_MM_DD');

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF security_event
         FOR VALUES FROM (%L) TO (%L)',
        partition_name,
        to_char(next_day, 'YYYY-MM-DD'),
        to_char(next_day + interval '1 day', 'YYYY-MM-DD')
    );
END;
$$ LANGUAGE plpgsql;

-- 古いパーティション削除関数
CREATE OR REPLACE FUNCTION drop_old_daily_partitions()
RETURNS void AS $$
DECLARE
    cutoff_date DATE := CURRENT_DATE - interval '90 days';
    partition_name TEXT;
BEGIN
    partition_name := 'security_event_' || to_char(cutoff_date, 'YYYY_MM_DD');
    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', partition_name);
END;
$$ LANGUAGE plpgsql;

-- スケジュール設定（postgres DBに接続して実行）
-- psql -h localhost -U idp -d postgres
SELECT cron.schedule_in_database('create-next-day-partitions', '0 2 * * *',
                     'SELECT create_next_day_partitions();', 'idpserver');
SELECT cron.schedule_in_database('drop-old-daily-partitions', '0 3 * * *',
                     'SELECT drop_old_daily_partitions();', 'idpserver');
```

---

## 8. 運用監視

### 8.1 パーティション一覧確認

```sql
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)) as size
FROM pg_tables
WHERE tablename LIKE 'security_event_%'
ORDER BY tablename DESC
LIMIT 10;
```

### 8.2 pg_cronジョブ状態確認

```sql
SELECT jobid, jobname, schedule, command, nodename
FROM cron.job;
```

### 8.3 DEFAULTパーティション監視

```sql
-- DEFAULTパーティションにデータが溜まっていないか確認
SELECT COUNT(*) FROM security_event_default;

-- 0以外の場合、パーティション作成が追いついていない可能性
```

---

## 9. 統計データテーブルのパーティショニング考察

### 9.1 統計データテーブル一覧

idp-serverには以下の統計データテーブルがあります。

| テーブル | 粒度 | 用途 |
|---------|------|------|
| `statistics_monthly` | 月次集計 | MAU、イベントカウント等の集計データ |
| `statistics_yearly` | 年次集計 | YAU等の年次集計データ |
| `statistics_daily_users` | 日×ユーザー | DAU計算用（ユニークユーザー追跡） |
| `statistics_monthly_users` | 月×ユーザー | MAU計算用（ユニークユーザー追跡） |
| `statistics_yearly_users` | 年×ユーザー | YAU計算用（ユニークユーザー追跡） |

### 9.2 パーティショニング適性判断

#### security_event との比較

| 項目 | security_event | statistics_daily_users |
|------|----------------|----------------------|
| **1日のレコード数** | 5億 | 100万（最大） |
| **1日のサイズ** | 625GB | ~56MB |
| **90日のサイズ** | 56TB | ~5GB |
| **削除負荷** | 非常に高い | 低い |

#### データ量試算（100万ユーザー想定）

```
statistics_daily_users:
  1レコード ≈ 56 bytes (UUID×2 + DATE + TIMESTAMP×2)
  1日 = 100万レコード × 56 bytes = 56MB
  90日 = 56MB × 90 = 約5GB

security_event（比較用）:
  1日 = 5億レコード × 1.25KB = 625GB
  90日 = 56TB
```

**データ量の差: 約10,000倍**

#### 結論: パーティショニングは不要

| テーブル | 判定 | 理由 |
|---------|------|------|
| `statistics_monthly` | ✗ 不要 | 月次集計のため年間12レコード/テナント程度 |
| `statistics_yearly` | ✗ 不要 | 年次集計のため年間1レコード/テナント |
| `statistics_daily_users` | ✗ 不要 | 5GB/90日程度、DELETE文で十分高速 |
| `statistics_monthly_users` | ✗ 不要 | 700MB/12ヶ月程度 |
| `statistics_yearly_users` | ✗ 不要 | 200MB/年程度、永続保持推奨 |

### 9.3 推奨運用方法

パーティショニングではなく、既存のクリーンアップ関数をpg_cronで定期実行します。

```sql
-- 統計データのクリーンアップスケジュール（推奨）
-- postgres DBに接続して実行: psql -h localhost -U idp -d postgres

-- daily_users: 90日保持
SELECT cron.schedule_in_database(
    'cleanup-daily-users',
    '0 4 * * *',  -- 毎日午前4時
    'SELECT cleanup_old_daily_users(90);',
    'idpserver'
);

-- monthly_users: 13ヶ月保持（YoY比較用）
SELECT cron.schedule_in_database(
    'cleanup-monthly-users',
    '0 4 1 * *',  -- 毎月1日午前4時
    'SELECT cleanup_old_monthly_users(13);',
    'idpserver'
);

-- statistics_monthly: 25ヶ月保持（YoY比較用）
SELECT cron.schedule_in_database(
    'cleanup-monthly-statistics',
    '0 4 1 * *',
    'SELECT cleanup_old_statistics(25);',
    'idpserver'
);

-- yearly_users, statistics_yearly: 永続保持（削除しない）
```

### 9.4 判断のポイント

パーティショニングを導入すべきかの判断基準:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  パーティショニング導入判断フローチャート                                    │
│                                                                             │
│  Q1: 1日のデータ量は100GB以上か？                                           │
│      │                                                                      │
│      ├─ Yes → Q2へ                                                          │
│      └─ No  → パーティショニング不要（DELETE文で十分）                       │
│                                                                             │
│  Q2: 古いデータを日単位/月単位で一括削除したいか？                          │
│      │                                                                      │
│      ├─ Yes → Q3へ                                                          │
│      └─ No  → パーティショニング不要                                        │
│                                                                             │
│  Q3: クエリで日付範囲検索が主か？                                           │
│      │                                                                      │
│      ├─ Yes → パーティショニング推奨                                        │
│      └─ No  → パーティショニング非推奨（プルーニング効果なし）              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**統計データテーブルの場合:**
- Q1: No（5GB/90日、100GB/日より大幅に小さい）
- → パーティショニング不要

---

## 10. 関連Issue・PR

| Issue/PR | 内容 |
|----------|------|
| #950 | パーティショニング要件定義 |

---

## 11. pg_partman / pg_cron 初期化

### 11.1 Docker環境での初期化

idp-serverでは、Docker PostgreSQLイメージの初期化スクリプトで pg_partman と pg_cron を設定しています。

**重要**: pg_cron はクロスデータベースモードで動作します。
- pg_cron 拡張は `postgres` データベースにインストール
- ジョブは `cron.schedule_in_database()` で `idpserver` データベースを指定して実行
- これにより複数データベースのジョブを一元管理可能

**初期化スクリプト位置**: `libs/idp-server-database/postgresql/init/02-init-partman.sh`

```bash
#!/bin/sh
set -eu

# Application owner user configuration
: "${DB_OWNER_USER:=idp}"

# ================================================
# Initialize pg_cron in postgres database (cross-database setup)
# ================================================
# pg_cron is configured with cron.database_name=postgres
# This allows scheduling jobs that run on any database using
# cron.schedule_in_database() function.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
  -- pg_cron for scheduled execution (cross-database mode)
  -- Note: pg_cron must be in shared_preload_libraries
  CREATE EXTENSION IF NOT EXISTS pg_cron;

  -- Grant cron schema permissions to application owner user
  -- This allows the user to create and manage scheduled jobs
  GRANT USAGE ON SCHEMA cron TO ${DB_OWNER_USER};
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cron TO ${DB_OWNER_USER};
EOSQL

# ================================================
# Initialize pg_partman in application database
# ================================================
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- pg_partman for partition management
  CREATE SCHEMA IF NOT EXISTS partman;
  CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;

  -- ================================================
  -- Grant permissions to application owner user
  -- ================================================

  -- partman schema permissions
  GRANT USAGE, CREATE ON SCHEMA partman TO ${DB_OWNER_USER};
  GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO ${DB_OWNER_USER};
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA partman TO ${DB_OWNER_USER};
  GRANT ALL ON ALL SEQUENCES IN SCHEMA partman TO ${DB_OWNER_USER};

  -- ================================================
  -- Grant file write permission for archive export
  -- ================================================
  -- Required for COPY TO file in archive.export_partition_to_external_storage()
  -- This allows the DB owner to export archived partitions to local files.
  -- Note: In production with AWS RDS/Aurora, use aws_s3 extension instead.
  GRANT pg_write_server_files TO ${DB_OWNER_USER};
EOSQL

# ================================================
# Create archive export directory
# ================================================
# pg_partman archive export writes CSV files to this directory.
# Located inside the data volume so it persists and is writable by postgres.
ARCHIVE_DIR="/var/lib/postgresql/data/archive"
mkdir -p "$ARCHIVE_DIR"
chown postgres:postgres "$ARCHIVE_DIR"
echo "Archive export directory created: $ARCHIVE_DIR"

echo "pg_cron and pg_partman extensions initialized with permissions for '${DB_OWNER_USER}'"
```

### 11.2 トラブルシューティング

#### Permission denied エラー

```
/bin/sh: /docker-entrypoint-initdb.d/02-init-partman.sh: Permission denied
```

**原因**: シェルスクリプトの読み取り権限が不足

**解決策**:
```bash
chmod 755 libs/idp-server-database/postgresql/init/02-init-partman.sh
```

#### partman スキーマが存在しない

```
ERROR: schema "partman" does not exist
```

**原因**: 初期化スクリプトが実行されていない

**解決策**:
1. Docker ボリュームを削除して再初期化
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

2. 手動で初期化（既存データがある場合）
   ```sql
   CREATE SCHEMA IF NOT EXISTS partman;
   CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;
   ```

#### RAISE NOTICE エラー

```
ERROR: syntax error at or near "RAISE"
```

**原因**: `RAISE NOTICE` は PL/pgSQL 専用構文であり、プレーンSQLでは使用不可

**解決策**: シェルの `echo` コマンドを使用
```bash
# ❌ 誤り（EOSQL内）
# RAISE NOTICE 'Completed';

# ✅ 正解（EOSQL外）
echo "Completed"
```

### 11.3 Docker Compose 設定

pg_partman / pg_cron を使用するには、`shared_preload_libraries` と `cron.database_name` の設定が必要です。

```yaml
services:
  postgres:
    image: postgres:16
    # pg_cron はクロスデータベースモードで postgres データベースにインストール
    command: postgres -c shared_preload_libraries=pg_cron,pg_partman_bgw -c cron.database_name=postgres
    environment:
      POSTGRES_DB: idpserver
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    volumes:
      - ./libs/idp-server-database/postgresql/init:/docker-entrypoint-initdb.d
```

**Note**:
- `docker-entrypoint-initdb.d` のスクリプトは、データディレクトリが空の場合のみ実行されます。
- `cron.database_name=postgres` により、pg_cron は postgres データベースで動作し、`cron.schedule_in_database()` で他のデータベースのジョブも実行可能です。

---

## 12. 運用カスタマイズ

### 12.1 デフォルト設定

| 項目 | デフォルト値 | 設定場所 |
|------|-------------|---------|
| メンテナンス実行時刻 | 毎日 02:00 UTC | `setup-pg-cron-jobs.sql` |
| security_event 保持期間 | 90日 | `V0_9_21_1__add_event_partitioning.sql` |
| security_event_hook_results 保持期間 | 90日 | 同上 |
| statistics_*_users 保持期間 | テーブルにより異なる | `V0_9_21_2__statistics.sql` |

### 12.2 cronスケジュールの変更

`setup-pg-cron-jobs.sql` を編集するか、直接SQLを実行します。

**重要**: pg_cron は `postgres` データベースにインストールされているため、cron操作は `postgres` データベースに接続して行います。

```sql
-- postgres データベースに接続して操作
-- psql -h localhost -U idp -d postgres

-- 現在のスケジュール確認
SELECT jobid, jobname, schedule, database, command, active FROM cron.job;

-- スケジュール変更（例: 毎時実行に変更）
SELECT cron.unschedule('partman-maintenance');
SELECT cron.schedule_in_database(
    'partman-maintenance',
    '0 * * * *',  -- 毎時0分
    $$CALL partman.run_maintenance_proc()$$,
    'idpserver'   -- 実行対象データベース
);

-- ジョブの一時停止
UPDATE cron.job SET active = false WHERE jobname = 'partman-maintenance';

-- ジョブの再開
UPDATE cron.job SET active = true WHERE jobname = 'partman-maintenance';
```

### 12.3 保持期間の変更

```sql
-- 現在の設定確認
SELECT parent_table, partition_interval, retention, premake
FROM partman.part_config;

-- security_event の保持期間を180日に変更
UPDATE partman.part_config
SET retention = '180 days'
WHERE parent_table = 'public.security_event';

-- security_event_hook_results の保持期間を30日に短縮
UPDATE partman.part_config
SET retention = '30 days'
WHERE parent_table = 'public.security_event_hook_results';
```

### 12.4 partman.part_config の主要設定項目

| カラム | 説明 | 例 |
|--------|------|-----|
| `partition_interval` | パーティション間隔 | `1 day`, `1 month` |
| `retention` | 保持期間（超過で自動削除） | `90 days`, `1 year` |
| `premake` | 事前作成するパーティション数 | `90` |
| `infinite_time_partitions` | 無限にパーティション作成 | `true` |
| `retention_keep_table` | 削除時にテーブルを残すか | `false` |
| `retention_keep_index` | 削除時にインデックスを残すか | `false` |

### 12.5 手動メンテナンス実行

cronを待たずに即時実行したい場合：

```sql
-- パーティション作成・削除を即時実行
CALL partman.run_maintenance_proc();

-- 実行結果確認
SELECT * FROM cron.job_run_details ORDER BY runid DESC LIMIT 5;
```

### 12.6 DDL vs 運用スクリプトの切り分け

| 項目 | 管理場所 | 変更方法 |
|------|----------|---------|
| テーブル構造 | DDL (Flyway) | 新規マイグレーション作成 |
| パーティション初期設定 | DDL (Flyway) | 新規マイグレーション作成 |
| cronスケジュール | `setup-pg-cron-jobs.sql` | ファイル編集 or 直接SQL |
| 保持期間 | `partman.part_config` | 直接SQL |

**設計思想**: DDLはスキーマ定義（リリース時に確定）、運用設定は環境ごとに調整可能。

---

## 13. 参考資料

- [PostgreSQL Table Partitioning](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [pg_cron Extension](https://github.com/citusdata/pg_cron)
- [pg_partman Extension](https://github.com/pgpartman/pg_partman)
