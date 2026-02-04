# security_event インデックス ベンチマーク

Issue #1227 の複合インデックス効果を検証するためのベンチマークスクリプトです。

## 概要

- **目的**: 複合インデックス追加前後のクエリパフォーマンス比較
- **データ量**: 1000万件
- **ボトルネック再現**: 特定ユーザー（`heavy_user_001`）に10%のデータを集中

## 前提条件

- PostgreSQL 14+
- ローカル環境が起動済み（`docker compose up -d`）
- 十分なディスク容量（約5GB以上）

## 実行手順

### Step 1: 現在のデータ件数を確認

```bash
docker exec -it postgres-primary psql -U idp -d idpserver -c "SELECT COUNT(*) FROM security_event;"
```

### Step 2: ベンチマーク用テナントの作成（オプション）

既存テナントを使う場合はスキップ可。スクリプトはランダムUUIDでテナントを作成します。

### Step 3: テストデータ投入

```bash
# スクリプトをコンテナにコピー
docker cp libs/idp-server-database/scripts/benchmark postgres-primary:/tmp/

# テストデータ投入
docker exec -it postgres-primary psql -U idp -d idpserver -f /tmp/benchmark/01_insert_test_data.sql
```

**所要時間**: 約10-30分（環境依存）

### Step 4: インデックスなしでベンチマーク

```bash
docker exec -it postgres-primary psql -U idp -d idpserver -f /tmp/benchmark/02_benchmark_without_index.sql
```

結果を記録してください（Execution Time）。

### Step 5: 複合インデックスを作成

```bash
docker exec -it postgres-primary psql -U idp -d idpserver -f /tmp/benchmark/03_create_composite_indexes.sql
```

### Step 6: インデックスありでベンチマーク

```bash
docker exec -it postgres-primary psql -U idp -d idpserver -f /tmp/benchmark/04_benchmark_with_index.sql
```

結果を比較してください。

### Step 7: クリーンアップ

```bash
docker exec -it postgres-primary psql -U idp -d idpserver -f /tmp/benchmark/99_cleanup.sql
```

## 期待される結果

| 条件 | インデックスなし | インデックスあり | 改善率 |
|------|-----------------|-----------------|--------|
| external_user_id検索 | 5-10秒 | 10-50ms | 100-500倍 |
| client_id検索 | 3-8秒 | 10-50ms | 60-160倍 |
| type検索 | 2-5秒 | 10-50ms | 40-100倍 |

## トラブルシューティング

### メモリ不足エラー

`work_mem` を増やしてください：
```sql
SET work_mem = '256MB';
```

### 投入が遅い

バッチサイズを調整してください（`01_insert_test_data.sql` の `batch_size` 変数）。

### パーティションエラー

`created_at` の日付範囲がパーティションに存在することを確認：
```sql
SELECT * FROM partman.part_config WHERE parent_table = 'public.security_event';
```

## 関連ファイル

- `01_insert_test_data.sql` - 1000万件投入スクリプト
- `02_benchmark_without_index.sql` - インデックスなしベンチマーク
- `03_create_composite_indexes.sql` - 複合インデックス作成
- `04_benchmark_with_index.sql` - インデックスありベンチマーク
- `99_cleanup.sql` - テストデータ削除
