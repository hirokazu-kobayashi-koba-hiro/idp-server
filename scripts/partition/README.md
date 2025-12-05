# PostgreSQL パーティショニング検証・シミュレーションツール

このディレクトリには、PostgreSQLテーブルパーティショニングの性能検証とライフサイクルシミュレーション用のスクリプトが含まれています。

## スクリプト一覧

| ファイル名 | 対象テーブル | 種別 | 説明 |
|-----------|-------------|------|------|
| `audit-log-benchmark.sh` | audit_log | ベンチマーク | パーティション vs 通常テーブルの性能比較 |
| `audit-log-lifecycle.sh` | audit_log | ライフサイクル | cron運用シミュレーション |
| `statistics-users-benchmark.sh` | statistics_*_users | ベンチマーク | DAU/MAU/YAU テーブルの性能比較 |
| `statistics-users-lifecycle.sh` | statistics_*_users | ライフサイクル | cron運用シミュレーション |
| `security-event-benchmark.sh` | security_event | ベンチマーク | セキュリティイベントの性能比較 |
| `security-event-lifecycle.sh` | security_event | ライフサイクル | cron運用シミュレーション |

## 使用方法

### ベンチマーク

```bash
# 監査ログ（100万行）
./scripts/partition/audit-log-benchmark.sh

# 統計ユーザーテーブル（100万行）
./scripts/partition/statistics-users-benchmark.sh

# セキュリティイベント（100万行）
./scripts/partition/security-event-benchmark.sh

# 行数を指定
./scripts/partition/audit-log-benchmark.sh 500000
```

### ライフサイクルシミュレーション

```bash
# 12ヶ月分のシミュレーション
./scripts/partition/audit-log-lifecycle.sh 12

# エラーテスト（パーティション未作成時の動作確認）
./scripts/partition/audit-log-lifecycle.sh --error-test 3

# cronスクリプト例を表示
./scripts/partition/audit-log-lifecycle.sh --generate

# クリーンアップ
./scripts/partition/audit-log-lifecycle.sh --cleanup
```

---

## 検証結果サマリー

### 1. パーティション構成

| テーブル | パーティション戦略 | パーティションキー |
|---------|------------------|------------------|
| audit_log | RANGE (月別) | created_at |
| security_event | RANGE (月別) | event_timestamp |
| security_event_hook_results | RANGE (月別) | execution_timestamp |
| statistics_daily_users | RANGE (月別) | stat_date |
| statistics_monthly_users | RANGE (年別) | stat_month |
| statistics_yearly_users | LIST (年別) | stat_year |

### 2. 性能改善効果

#### audit_log（100万行）

| クエリタイプ | 通常テーブル | パーティション | 改善率 |
|------------|------------|--------------|-------|
| 特定月のCOUNT | 45ms | 8ms | **82%** |
| 日付範囲+条件フィルタ | 12ms | 2ms | **83%** |
| 月別集計 | 890ms | 720ms | 19% |

#### security_event（100万行）

| クエリタイプ | 通常テーブル | パーティション | 改善率 |
|------------|------------|--------------|-------|
| 特定テナント・特定月 | 0.34ms | 0.06ms | **82%** |
| イベントタイプ別集計 | 1855ms | 379ms | **80%** |
| Hook種類別成功率 | 508ms | 161ms | **68%** |

#### statistics_yearly_users（100万行）

| クエリタイプ | 通常テーブル | パーティション | 改善率 |
|------------|------------|--------------|-------|
| 特定年のYAU | 256ms | 17ms | **93%** |
| 年別推移 | 1420ms | 890ms | 37% |

### 3. 削除性能

| 操作 | 100万行削除時間 |
|-----|---------------|
| DELETE文 | 2〜5秒 |
| DROP PARTITION | 0.01〜0.1秒 |

**結論**: DROP PARTITIONはDELETEの50〜500倍高速

---

## 重要な知見

### パーティションプルーニングが効くクエリ

パーティションキー（日付カラム）を条件に含むクエリで大幅な性能改善：

```sql
-- ✅ パーティションプルーニングが効く
SELECT * FROM audit_log
WHERE created_at >= '2024-03-01' AND created_at < '2024-04-01';

-- ❌ パーティションプルーニングが効かない（全パーティションスキャン）
SELECT * FROM audit_log WHERE event_type = 'login_success';
```

### DEFAULTパーティションの重要性

DEFAULTパーティションがない場合、対応するパーティションが存在しないデータの挿入は失敗する：

```
ERROR: no partition of relation "audit_log" found for row
```

DEFAULTパーティションを作成することで：
- cronジョブ失敗時のデータ損失を防止
- アプリケーションエラーを回避
- 後から適切なパーティションにデータを移動可能

```sql
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;
```

### PRIMARY KEY制約の注意点

パーティションテーブルのPRIMARY KEYにはパーティションキーを含める必要がある：

```sql
-- ❌ エラー: パーティションキーがPRIMARY KEYに含まれていない
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (created_at);

-- ✅ 正しい: パーティションキーをPRIMARY KEYに含める
CREATE TABLE audit_log (
    id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
```

### 式ベースのパーティションキーの制約

PRIMARY KEY制約と式ベースのパーティションキーは併用できない：

```sql
-- ❌ エラー: PRIMARY KEY constraint with partition key definition
CREATE TABLE statistics_monthly_users (
    ...
    PRIMARY KEY (tenant_id, stat_month, user_id)
) PARTITION BY LIST (LEFT(stat_month, 4));

-- ✅ 正しい: RANGEパーティションで文字列比較を使用
CREATE TABLE statistics_monthly_users (
    ...
    PRIMARY KEY (tenant_id, stat_month, user_id)
) PARTITION BY RANGE (stat_month);
-- パーティション: FOR VALUES FROM ('2024-01') TO ('2025-01')
```

---

## 運用ガイドライン

### cronジョブ設定例

```bash
# /etc/cron.d/partition-maintenance

# 毎月1日 AM 2:00: 新規パーティション作成（来月分を事前作成）
0 2 1 * * postgres /opt/scripts/create-partitions.sh

# 毎月1日 AM 3:00: 古いパーティション削除
0 3 1 * * postgres /opt/scripts/drop-old-partitions.sh
```

### 保持期間の設定指針

| テーブル | 推奨保持期間 | 根拠 |
|---------|------------|-----|
| audit_log | 12ヶ月〜7年 | コンプライアンス要件（PCI DSS, SOX等） |
| security_event | 6ヶ月〜2年 | セキュリティ監査・インシデント調査 |
| statistics_daily_users | 6ヶ月 | DAU分析に必要な期間 |
| statistics_monthly_users | 2〜3年 | MAU推移分析 |
| statistics_yearly_users | 5年以上 | 長期トレンド分析 |

### DEFAULTパーティション監視

```bash
#!/bin/bash
# /opt/scripts/monitor-default-partitions.sh

ALERT_THRESHOLD=100

result=$(psql -t -c "
SELECT COALESCE((SELECT count(*) FROM audit_log_default), 0)
")

if [ "$result" -gt "$ALERT_THRESHOLD" ]; then
    echo "ALERT: DEFAULTパーティションに ${result} レコードが存在"
    # Slack/PagerDuty通知
fi
```

### DEFAULTパーティションからのデータ移動

```sql
-- 1. 適切なパーティションを作成
CREATE TABLE audit_log_2024_01 PARTITION OF audit_log
FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- 2. DEFAULTからデータを移動（トランザクション内で実行）
BEGIN;
INSERT INTO audit_log_2024_01
SELECT * FROM audit_log_default
WHERE created_at >= '2024-01-01' AND created_at < '2024-02-01';

DELETE FROM audit_log_default
WHERE created_at >= '2024-01-01' AND created_at < '2024-02-01';
COMMIT;
```

---

## パフォーマンスチューニング

### 推奨設定

```sql
-- パーティションプルーニングを有効化（デフォルトでON）
SET enable_partition_pruning = on;

-- 並列クエリを有効化
SET max_parallel_workers_per_gather = 4;
```

### EXPLAIN ANALYZEでの確認

パーティションプルーニングが効いているか確認：

```sql
EXPLAIN (ANALYZE, COSTS, BUFFERS)
SELECT count(*) FROM audit_log
WHERE created_at >= '2024-03-01' AND created_at < '2024-04-01';

-- 期待される出力:
-- Append (actual rows=...)
--   Subplans Removed: 11  ← 他の11パーティションがスキップされた
--   ->  Seq Scan on audit_log_2024_03
```

---

## トラブルシューティング

### よくある問題

#### 1. パーティション作成失敗

```
ERROR: cannot create partitioned table as partition of relation "xxx"
```

**原因**: 親テーブルがパーティションテーブルではない
**対処**: 親テーブルの`PARTITION BY`句を確認

#### 2. データ挿入失敗

```
ERROR: no partition of relation "xxx" found for row
```

**原因**: 対応するパーティションが存在しない
**対処**: DEFAULTパーティションを作成するか、適切なパーティションを作成

#### 3. パーティションキー変更不可

```
ERROR: cannot UPDATE partition key column
```

**原因**: パーティションキーカラムの更新は制限されている
**対処**: 行を削除して再挿入するか、設計を見直す

---

## 関連ドキュメント

- [PostgreSQL公式: テーブルパーティショニング](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [PostgreSQL公式: パーティションプルーニング](https://www.postgresql.org/docs/current/ddl-partitioning.html#DDL-PARTITION-PRUNING)
