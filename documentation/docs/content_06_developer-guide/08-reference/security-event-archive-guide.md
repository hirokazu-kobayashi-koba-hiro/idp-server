# セキュリティイベント アーカイブガイド

このドキュメントでは、セキュリティイベントの長期保存とクラウドストレージへのアーカイブ戦略について説明します。

---

## 概要

### 課題

`security_event` テーブルは90日間の保持期間後に自動削除されますが、コンプライアンスや監査要件により、より長期間のデータ保存が必要な場合があります。

### 解決策

pg_partmanの `retention_schema` 機能を活用し、削除前にパーティションをアーカイブスキーマに退避。その後、外部ストレージ（S3、GCS、Azure Blob等）にエクスポートします。

```
┌─────────────────────────────────────────────────────────────────────────┐
│  アーカイブフロー                                                        │
│                                                                         │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐ │
│  │ security    │   │  archive    │   │   外部      │   │  分析基盤   │ │
│  │  _event     │──▶│  schema     │──▶│ ストレージ  │──▶│ (Athena等)  │ │
│  │  (90日)     │   │ (退避)      │   │ (S3等)      │   │             │ │
│  └─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘ │
│        │                 │                 │                 │         │
│    pg_partman        自動移動          エクスポート       クエリ検索   │
│    retention        (DETACH)          (Parquet)         (SQL)         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## AWS構成の全体像

AWSを使用する場合の推奨アーキテクチャです。

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  AWS アーカイブアーキテクチャ                                                        │
│                                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │  VPC                                                                         │   │
│  │                                                                              │   │
│  │  ┌──────────────────────────────────────────────────────────┐               │   │
│  │  │  Aurora PostgreSQL                                        │               │   │
│  │  │  ┌────────────────┐    ┌────────────────┐                │               │   │
│  │  │  │ security_event │    │ archive schema │                │               │   │
│  │  │  │   (90日保持)   │───▶│  (退避領域)    │                │               │   │
│  │  │  └────────────────┘    └───────┬────────┘                │               │   │
│  │  │         │                      │                          │               │   │
│  │  │    pg_partman              pg_cron                        │               │   │
│  │  │    (02:00 UTC)            (03:00 UTC)                     │               │   │
│  │  │                                │                          │               │   │
│  │  └────────────────────────────────│──────────────────────────┘               │   │
│  │                                   │                                          │   │
│  │                                   │ aws_s3 拡張                              │   │
│  │                                   │ query_export_to_s3()                     │   │
│  │                                   ▼                                          │   │
│  └───────────────────────────────────│──────────────────────────────────────────┘   │
│                                      │                                              │
│  ┌───────────────────────────────────│──────────────────────────────────────────┐   │
│  │  S3 (アーカイブストレージ)        │                                           │   │
│  │                                   ▼                                           │   │
│  │  s3://idp-archive-bucket/                                                    │   │
│  │  └── security_event/                                                         │   │
│  │      ├── year=2024/                                                          │   │
│  │      │   ├── month=01/                                                       │   │
│  │      │   │   ├── day=01/data.csv                                             │   │
│  │      │   │   ├── day=02/data.csv                                             │   │
│  │      │   │   └── ...                                                         │   │
│  │      │   └── month=12/...                                                    │   │
│  │      └── year=2025/...                                                       │   │
│  │                                                                              │   │
│  │  ライフサイクルポリシー:                                                      │   │
│  │  ├── 0-90日:   S3 Standard                                                   │   │
│  │  ├── 90-365日: S3 Standard-IA                                                │   │
│  │  └── 365日〜:  S3 Glacier Deep Archive                                       │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                              │
│                                      │                                              │
│  ┌───────────────────────────────────│──────────────────────────────────────────┐   │
│  │  分析基盤                         │                                           │   │
│  │                                   ▼                                           │   │
│  │  ┌────────────────┐    ┌────────────────┐    ┌────────────────┐             │   │
│  │  │  Glue Crawler  │───▶│  Glue Catalog  │◀───│    Athena      │             │   │
│  │  │  (04:00 UTC)   │    │  (メタデータ)  │    │  (SQLクエリ)   │             │   │
│  │  └────────────────┘    └────────────────┘    └───────┬────────┘             │   │
│  │                                                      │                       │   │
│  │                                                      ▼                       │   │
│  │                                             ┌────────────────┐               │   │
│  │                                             │   QuickSight   │               │   │
│  │                                             │ (ダッシュボード)│               │   │
│  │                                             └────────────────┘               │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
│  ┌──────────────────────────────────────────────────────────────────────────────┐   │
│  │  セキュリティ・権限                                                           │   │
│  │                                                                              │   │
│  │  ┌────────────────┐    ┌────────────────┐    ┌────────────────┐             │   │
│  │  │  IAM Role      │    │  S3 Bucket     │    │    KMS         │             │   │
│  │  │  (Aurora用)    │    │    Policy      │    │  (暗号化キー)  │             │   │
│  │  │                │    │                │    │                │             │   │
│  │  │ s3:PutObject   │    │ Aurora からの  │    │ S3 SSE-KMS     │             │   │
│  │  │ s3:GetObject   │    │ アクセス許可   │    │                │             │   │
│  │  └────────────────┘    └────────────────┘    └────────────────┘             │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 処理タイムライン

```
┌─────────────────────────────────────────────────────────────────────────┐
│  日次処理スケジュール (UTC)                                              │
│                                                                         │
│  02:00  pg_partman maintenance                                          │
│         └── 90日経過パーティションを archive スキーマに DETACH           │
│                                                                         │
│  03:00  process_archived_partitions()                                   │
│         └── archive スキーマのテーブルを S3 にエクスポート & DROP        │
│                                                                         │
│  04:00  Glue Crawler                                                    │
│         └── S3 の新規パーティションを検出し Glue Catalog を更新         │
│                                                                         │
│  いつでも  Athena クエリ                                                 │
│         └── 過去データの検索・分析・レポート生成                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### コスト試算（詳細シミュレーション）

#### 前提条件

| 項目 | 値 |
|------|-----|
| ユーザー数 | 100万人 |
| 1ユーザーあたりイベント数/日 | 100件 |
| 1日のイベント総数 | 1億件 |
| 1イベントあたりのサイズ | 500 bytes |
| 1日のデータ量 | **約50GB** |
| リージョン | ap-northeast-1 (東京) |

#### ストレージ単価（東京リージョン参考値）

| ストレージ種別 | 単価 (GB/月) |
|---------------|-------------|
| Aurora PostgreSQL | $0.12 |
| S3 Standard | $0.025 |
| S3 Glacier Deep Archive | $0.002 |

※ [AWS公式料金ページ](https://aws.amazon.com/s3/pricing/)で最新価格を確認してください

---

#### パターンA: DBにずっと保持する場合

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Aurora PostgreSQL にすべて保持                                          │
│                                                                         │
│  データ増加: 50GB/日 × 365日 = 18.25TB/年                               │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                                                                 │   │
│  │  1年後:  18.25TB  │  2年後:  36.5TB  │  3年後:  54.75TB        │   │
│  │                                                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

| 期間 | 累積データ量 | Aurora ストレージ月額 |
|------|-------------|---------------------|
| 3ヶ月後 | 4.5TB | $540 |
| 6ヶ月後 | 9TB | $1,080 |
| 1年後 | 18.25TB | $2,190 |
| 2年後 | 36.5TB | $4,380 |
| 3年後 | 54.75TB | $6,570 |
| **5年後** | **91.25TB** | **$10,950/月** |

**年間コスト（5年運用時）**: 約 **$131,400/年**（ストレージのみ）

---

#### パターンB: 90日でS3にアーカイブする場合

```
┌─────────────────────────────────────────────────────────────────────────┐
│  90日保持 + S3アーカイブ                                                 │
│                                                                         │
│  Aurora: 50GB × 90日 = 4.5TB (固定)                                     │
│  S3: 毎日50GBが追加され、1年後にGlacierへ                               │
│                                                                         │
│  ┌───────────────┐   ┌───────────────┐   ┌───────────────┐             │
│  │ Aurora        │   │ S3 Standard   │   │ S3 Glacier    │             │
│  │ 4.5TB (固定)  │   │ (90日〜1年)   │   │ (1年超)       │             │
│  └───────────────┘   └───────────────┘   └───────────────┘             │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

| ストレージ | データ量 | 単価 | 月額コスト |
|-----------|---------|------|-----------|
| Aurora (90日分) | 4.5TB | $0.12/GB | $540 |
| S3 Standard (90日〜1年分) | 13.75TB | $0.025/GB | $344 |
| S3 Glacier (1年超、5年運用時) | 73TB | $0.002/GB | $146 |
| **合計（5年運用時）** | **91.25TB** | - | **$1,030/月** |

**年間コスト（5年運用時）**: 約 **$12,360/年**（ストレージのみ）

---

#### コスト比較サマリー

```
┌─────────────────────────────────────────────────────────────────────────┐
│  5年間の累積コスト比較                                                   │
│                                                                         │
│  パターンA (全てAurora)                                                  │
│  ████████████████████████████████████████████████████  $657,000         │
│                                                                         │
│  パターンB (90日+S3)                                                     │
│  █████  $61,800                                                         │
│                                                                         │
│  削減効果: 約90%削減 ($595,200の節約)                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

| 指標 | パターンA (全てAurora) | パターンB (90日+S3) | 削減率 |
|------|----------------------|--------------------|----|
| 1年目 月額 | $1,095〜$2,190 | $540〜$884 | 60% |
| 5年目 月額 | $10,950 | $1,030 | **91%** |
| 5年間累積 | $657,000 | $61,800 | **91%** |
| Auroraストレージ上限 | 128TBで限界 | 4.5TB固定 | - |

---

#### 追加コスト（パターンB）

| 項目 | 月額コスト |
|------|-----------|
| S3へのエクスポート（PUT） | ~$50 |
| Athenaクエリ（100GB/月スキャン） | ~$0.5 |
| Glue Crawler（1回/日） | ~$15 |
| データ転送（リージョン内） | $0 |
| **追加コスト合計** | **~$66/月** |

---

#### 結論

| 観点 | パターンA | パターンB |
|------|----------|----------|
| **コスト** | 高い（年々増加） | 低い（ほぼ固定） |
| **スケーラビリティ** | 128TB上限 | 実質無制限 |
| **クエリ性能（直近90日）** | 高速 | 高速 |
| **クエリ性能（過去データ）** | 高速 | 中速（Athena） |
| **運用複雑度** | シンプル | やや複雑 |
| **推奨ユースケース** | 小規模・短期 | 大規模・長期保存 |

**推奨**: 100万ユーザー規模では**パターンB（90日+S3アーカイブ）**を推奨

※ 上記は概算です。実際のコストは[AWS Pricing Calculator](https://calculator.aws/)で試算してください

### 必要なAWSリソース

| リソース | 用途 | 設定のポイント |
|----------|------|---------------|
| **Aurora PostgreSQL** | メインDB | `aws_s3` 拡張有効化、IAMロール関連付け |
| **S3 Bucket** | アーカイブストレージ | ライフサイクルポリシー、暗号化(SSE-KMS) |
| **IAM Role** | Aurora→S3アクセス | `s3:PutObject`, `s3:GetObject` |
| **Glue Crawler** | スキーマ自動検出 | 日次スケジュール |
| **Glue Catalog** | メタデータ管理 | Athenaと共有 |
| **Athena** | SQLクエリ | Workgroup設定、結果出力先S3 |
| **KMS** (オプション) | 暗号化 | S3, Aurora両方で使用 |

---

## Step 1: アーカイブスキーマの作成

```sql
-- アーカイブ用スキーマを作成
CREATE SCHEMA IF NOT EXISTS archive;

COMMENT ON SCHEMA archive IS 'Detached partitions awaiting export to external storage';
```

---

## Step 2: pg_partman設定の更新

```sql
-- security_event のアーカイブ設定
UPDATE partman.part_config
SET
    retention = '90 days',
    retention_keep_table = true,       -- 削除せずにDETACHのみ
    retention_schema = 'archive'       -- archiveスキーマに移動
WHERE parent_table = 'public.security_event';

-- security_event_hook_results も同様
UPDATE partman.part_config
SET
    retention = '90 days',
    retention_keep_table = true,
    retention_schema = 'archive'
WHERE parent_table = 'public.security_event_hook_results';

-- 設定確認
SELECT parent_table, retention, retention_keep_table, retention_schema
FROM partman.part_config
WHERE parent_table LIKE 'public.security_event%';
```

**動作**:
- 90日経過したパーティションは親テーブルからDETACH
- `archive` スキーマに移動（例: `archive.security_event_p20250905`）
- テーブルは削除されずに保持

---

## Step 3: エクスポート関数の作成

### 3.1 インターフェース関数（スタブ）

```sql
-- エクスポート関数のインターフェース定義
-- 利用者が自身の環境に合わせて実装する
CREATE OR REPLACE FUNCTION archive.export_partition_to_external_storage(
    p_schema_name TEXT,
    p_table_name TEXT,
    p_destination_path TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
BEGIN
    -- デフォルト実装: 何もしない（利用者が上書き実装）
    RAISE NOTICE 'archive.export_partition_to_external_storage() not implemented. Override this function for your environment.';
    RAISE NOTICE 'Table: %.%, Path: %', p_schema_name, p_table_name, COALESCE(p_destination_path, '(default)');
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive.export_partition_to_external_storage(TEXT, TEXT, TEXT) IS
'Export archived partition to external storage. Override this function for your cloud environment (AWS S3, GCS, Azure Blob, etc.)';
```

### 3.2 メイン処理関数

```sql
CREATE OR REPLACE FUNCTION archive.process_archived_partitions(
    p_dry_run BOOLEAN DEFAULT FALSE
)
RETURNS TABLE(
    table_name TEXT,
    row_count BIGINT,
    exported BOOLEAN,
    dropped BOOLEAN,
    message TEXT
) AS $$
DECLARE
    tbl RECORD;
    v_row_count BIGINT;
    export_success BOOLEAN;
BEGIN
    RAISE NOTICE 'Starting archive processing (dry_run=%)', p_dry_run;

    FOR tbl IN
        SELECT t.tablename, t.schemaname
        FROM pg_tables t
        WHERE t.schemaname = 'archive'
        ORDER BY t.tablename
    LOOP
        table_name := tbl.tablename;
        exported := FALSE;
        dropped := FALSE;

        -- 行数取得
        EXECUTE format('SELECT COUNT(*) FROM %I.%I', tbl.schemaname, tbl.tablename)
        INTO v_row_count;
        row_count := v_row_count;

        IF p_dry_run THEN
            message := format('Would process: %s.%s (%s rows)', tbl.schemaname, tbl.tablename, v_row_count);
            RAISE NOTICE '%', message;
        ELSE
            -- エクスポート実行
            BEGIN
                export_success := archive.export_partition_to_external_storage(tbl.schemaname, tbl.tablename);
                exported := export_success;

                IF export_success THEN
                    -- エクスポート成功時のみ削除
                    EXECUTE format('DROP TABLE %I.%I', tbl.schemaname, tbl.tablename);
                    dropped := TRUE;
                    message := format('Exported and dropped (%s rows)', v_row_count);
                    RAISE NOTICE 'Dropped archived table %.%', tbl.schemaname, tbl.tablename;
                ELSE
                    message := format('Export not performed, table retained (%s rows)', v_row_count);
                    RAISE NOTICE 'Keeping archived table %.%', tbl.schemaname, tbl.tablename;
                END IF;
            EXCEPTION WHEN OTHERS THEN
                message := format('Error: %s', SQLERRM);
                RAISE WARNING 'Error processing %.%: %', tbl.schemaname, tbl.tablename, SQLERRM;
            END;
        END IF;

        RETURN NEXT;
    END LOOP;

    RAISE NOTICE 'Archive processing complete';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive.process_archived_partitions(BOOLEAN) IS
'Process archived partitions: export to external storage and drop if successful. Use p_dry_run=TRUE to preview without changes.';
```

---

## Step 4: pg_cronジョブの設定

**重要**: pg_cron は `postgres` データベースにインストールされています（クロスデータベースモード）。
ジョブ登録は `postgres` データベースに接続して `cron.schedule_in_database()` を使用します。

```sql
-- postgres データベースに接続
-- psql -h localhost -U idp -d postgres

-- アーカイブ処理ジョブ（毎日03:00、pg_partmanメンテナンス後）
SELECT cron.schedule_in_database(
    'archive-processing',
    '0 3 * * *',
    $$SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE)$$,
    'idpserver'  -- 実行対象データベース
);

-- ジョブ確認
SELECT jobid, jobname, schedule, database, command, active
FROM cron.job
WHERE jobname = 'archive-processing';
```

---

## Step 5: クラウド別エクスポート実装

### AWS S3 (Aurora PostgreSQL)

```sql
-- aws_s3 拡張を使用
CREATE OR REPLACE FUNCTION archive.export_partition_to_external_storage(
    p_schema_name TEXT,
    p_table_name TEXT,
    p_destination_path TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
DECLARE
    s3_bucket TEXT := 'your-archive-bucket';
    s3_region TEXT := 'ap-northeast-1';
    s3_path TEXT;
    result_rows BIGINT;
BEGIN
    -- S3パスの構築（Hive形式パーティション）
    -- 例: security_event_p20250905 → security_event/year=2025/month=09/day=05/
    s3_path := COALESCE(p_destination_path, archive.build_s3_path(p_table_name));

    -- S3にエクスポート（Parquet形式推奨だがCSVも可）
    SELECT aws_s3.query_export_to_s3(
        format('SELECT * FROM %I.%I', p_schema_name, p_table_name),
        aws_commons.create_s3_uri(s3_bucket, s3_path, s3_region),
        options := 'FORMAT CSV, HEADER TRUE'
    ) INTO result_rows;

    RAISE NOTICE 'Exported % rows to s3://%/%', result_rows, s3_bucket, s3_path;
    RETURN TRUE;

EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'S3 export failed: %', SQLERRM;
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- S3パス構築ヘルパー関数
CREATE OR REPLACE FUNCTION archive.build_s3_path(p_table_name TEXT)
RETURNS TEXT AS $$
DECLARE
    -- security_event_p20250905 → 2025, 09, 05 を抽出
    date_part TEXT;
    year_val TEXT;
    month_val TEXT;
    day_val TEXT;
    base_name TEXT;
BEGIN
    -- パーティション名からベース名と日付を抽出
    -- 例: security_event_p20250905 または security_event_hook_results_p20250905
    IF p_table_name ~ '_p[0-9]{8}$' THEN
        date_part := substring(p_table_name from '_p([0-9]{8})$');
        base_name := regexp_replace(p_table_name, '_p[0-9]{8}$', '');
        year_val := substring(date_part from 1 for 4);
        month_val := substring(date_part from 5 for 2);
        day_val := substring(date_part from 7 for 2);

        RETURN format('%s/year=%s/month=%s/day=%s/data.csv',
                      base_name, year_val, month_val, day_val);
    ELSE
        -- フォールバック
        RETURN format('archive/%s/data.csv', p_table_name);
    END IF;
END;
$$ LANGUAGE plpgsql;
```

### Google Cloud Storage (Cloud SQL)

```sql
-- pg_export_data 関数を使用（要: cloudsql_import_export権限）
CREATE OR REPLACE FUNCTION archive.export_partition_to_external_storage(
    p_schema_name TEXT,
    p_table_name TEXT,
    p_destination_path TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
DECLARE
    gcs_bucket TEXT := 'gs://your-archive-bucket';
    gcs_path TEXT;
BEGIN
    gcs_path := COALESCE(p_destination_path, archive.build_gcs_path(p_table_name));

    -- Cloud SQLからGCSにエクスポート
    -- Note: Cloud SQL Admin APIを使用する場合は外部スクリプトが必要
    PERFORM pg_export_data(
        format('SELECT * FROM %I.%I', p_schema_name, p_table_name),
        gcs_bucket || '/' || gcs_path
    );

    RETURN TRUE;
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'GCS export failed: %', SQLERRM;
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql;
```

### ローカルファイル（Docker環境）

```sql
-- COPYコマンドでローカルエクスポート（pg_write_server_files権限必要）
-- デフォルトパス: /var/lib/postgresql/data/archive (Dockerデータボリューム内)
CREATE OR REPLACE FUNCTION archive.export_partition_to_external_storage(
    p_schema_name TEXT,
    p_table_name TEXT,
    p_destination_path TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
DECLARE
    v_row_count BIGINT;
    v_export_dir TEXT;
    v_file_path TEXT;
BEGIN
    -- 行数取得
    EXECUTE format('SELECT COUNT(*) FROM %I.%I', p_schema_name, p_table_name)
    INTO v_row_count;

    IF v_row_count = 0 THEN
        RAISE NOTICE 'Table %.% is empty, skipping export', p_schema_name, p_table_name;
        RETURN TRUE;  -- 空テーブルは成功扱い
    END IF;

    -- パーティションテーブル以外はスキップ
    IF p_table_name !~ '_p\d{8}$' THEN
        RAISE NOTICE 'Table %.% is not a partition table, skipping', p_schema_name, p_table_name;
        RETURN FALSE;
    END IF;

    -- エクスポートディレクトリ取得
    v_export_dir := archive.get_config('export_directory', '/var/lib/postgresql/data/archive');

    -- ファイルパス構築（フラット構造）
    v_file_path := v_export_dir || '/' || p_table_name || '.csv';

    -- カスタムパスが指定された場合はそちらを使用
    IF p_destination_path IS NOT NULL THEN
        v_file_path := p_destination_path;
    END IF;

    RAISE NOTICE 'Exporting %.% (% rows) to %',
        p_schema_name, p_table_name, v_row_count, v_file_path;

    -- CSVファイルにエクスポート
    BEGIN
        EXECUTE format(
            'COPY (SELECT * FROM %I.%I) TO %L WITH (FORMAT CSV, HEADER)',
            p_schema_name, p_table_name, v_file_path
        );
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'Failed to export %.% to %: %',
            p_schema_name, p_table_name, v_file_path, SQLERRM;
        RETURN FALSE;
    END;

    RAISE NOTICE 'Successfully exported %.% to %', p_schema_name, p_table_name, v_file_path;
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;
```

**必要な権限**: `GRANT pg_write_server_files TO <db_owner_user>;`

---

## Step 6: Athenaでのクエリ設定（AWS）

### 6.1 Glue Crawlerの設定

```json
{
  "Name": "security-event-archive-crawler",
  "Role": "arn:aws:iam::ACCOUNT:role/GlueCrawlerRole",
  "DatabaseName": "idp_archive",
  "Targets": {
    "S3Targets": [
      {
        "Path": "s3://your-archive-bucket/security_event/"
      }
    ]
  },
  "SchemaChangePolicy": {
    "UpdateBehavior": "UPDATE_IN_DATABASE",
    "DeleteBehavior": "LOG"
  },
  "Schedule": "cron(0 4 * * ? *)"
}
```

### 6.2 Athenaテーブル（手動作成の場合）

```sql
CREATE EXTERNAL TABLE idp_archive.security_event (
    id STRING,
    type STRING,
    description STRING,
    tenant_id STRING,
    tenant_name STRING,
    client_id STRING,
    client_name STRING,
    user_id STRING,
    user_name STRING,
    external_user_id STRING,
    ip_address STRING,
    user_agent STRING,
    detail STRING,
    created_at TIMESTAMP
)
PARTITIONED BY (
    year STRING,
    month STRING,
    day STRING
)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY ','
STORED AS TEXTFILE
LOCATION 's3://your-archive-bucket/security_event/'
TBLPROPERTIES ('skip.header.line.count'='1');

-- パーティション追加
MSCK REPAIR TABLE idp_archive.security_event;
```

### 6.3 Athenaクエリ例

```sql
-- 過去1年のログイン失敗を調査
SELECT
    tenant_id,
    user_id,
    created_at,
    detail
FROM idp_archive.security_event
WHERE year = '2024'
  AND type = 'login_failure'
  AND tenant_id = 'your-tenant-id'
ORDER BY created_at DESC
LIMIT 100;

-- テナント別のイベント集計
SELECT
    tenant_id,
    type,
    COUNT(*) as event_count
FROM idp_archive.security_event
WHERE year = '2024' AND month = '06'
GROUP BY tenant_id, type
ORDER BY event_count DESC;

-- 不審なIPアドレスの検出
SELECT
    ip_address,
    COUNT(DISTINCT user_id) as affected_users,
    COUNT(*) as attempt_count
FROM idp_archive.security_event
WHERE type = 'login_failure'
  AND year >= '2024'
GROUP BY ip_address
HAVING COUNT(*) > 100
ORDER BY attempt_count DESC;
```

---

## 運用ガイド

### アーカイブ状況の確認

```sql
-- archiveスキーマのテーブル一覧（archive.get_archive_status関数を使用）
SELECT * FROM archive.get_archive_status();

-- または手動クエリ
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size('archive.' || tablename)) as size
FROM pg_tables
WHERE schemaname = 'archive'
ORDER BY tablename DESC;

-- pg_cronジョブの実行履歴
-- 注意: pg_cron は postgres データベースにインストールされています
-- psql -h localhost -U idp -d postgres で接続して実行
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

-- dry runでアーカイブ対象を確認
SELECT * FROM archive.process_archived_partitions(p_dry_run := TRUE);
```

### トラブルシューティング

#### archiveスキーマにテーブルが溜まる

**原因**: エクスポート関数が未実装または失敗している

**対処**:
1. `archive.export_partition_to_external_storage()` 関数が実装されているか確認
2. pg_cronジョブのログを確認
3. 手動でエクスポートを試行

```sql
-- 手動エクスポートテスト
SELECT archive.export_partition_to_external_storage('archive', 'security_event_p20250905');

-- またはprocess_archived_partitionsで一括処理
SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE);
```

#### S3エクスポートが失敗する

**原因**: IAMロール権限不足、S3バケットポリシー

**対処**:
1. Aurora/RDSのIAMロールに `s3:PutObject` 権限があるか確認
2. S3バケットポリシーでRDSからのアクセスを許可

#### Athenaでデータが見えない

**原因**: パーティションが認識されていない

**対処**:
```sql
-- パーティション再スキャン
MSCK REPAIR TABLE idp_archive.security_event;

-- または手動でパーティション追加
ALTER TABLE idp_archive.security_event
ADD PARTITION (year='2025', month='09', day='05')
LOCATION 's3://your-archive-bucket/security_event/year=2025/month=09/day=05/';
```

---

## データ形式の推奨

### CSV vs Parquet

| 形式 | 圧縮率 | クエリ性能 | 互換性 |
|------|--------|-----------|--------|
| CSV | 低 | 低 | 高（どこでも読める） |
| Parquet | 高 | 高 | 中（専用ツール必要） |

**推奨**: 長期保存・分析用途には **Parquet形式** を推奨

Parquet形式でエクスポートする場合は、以下のような外部ツールを使用:
- `pg_dump` + `parquet-tools`
- AWS Glue ETL Job
- Python + pandas + pyarrow

---

## 関連ドキュメント

- [データベースパーティショニングガイド](./database-partitioning-guide.md) - pg_partman設定詳細
- [テナント統計機能 実装ガイド](./tenant-statistics-implementation.md) - 統計データのパーティショニング
- [セキュリティイベント](../../content_03_concepts/concept-11-security-events.md) - イベント種別一覧
