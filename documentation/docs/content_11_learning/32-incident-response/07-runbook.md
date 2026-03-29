# 認証サービスの障害ランブック

このランブックは、認証サービス（idp-server）でよく発生する障害パターンと対応手順をまとめたものです。アラートが鳴ったときに、このページを開いて手順に従えば対応できることを目指します。

---

## 使い方

1. アラート名から該当するセクションを探す
2. 「確認」の手順でどこに問題があるか特定
3. 「対処」の手順で復旧

---

## パターン一覧

| # | アラート / 症状 | 想定原因 | 重大度 |
|:---:|:---|:---|:---:|
| 1 | [5xx エラー率の急増](#1-5xx-エラー率の急増) | アプリバグ、DB障害、リソース枯渇 | Sev 1-2 |
| 2 | [全タスク Unhealthy](#2-全タスク-unhealthy) | アプリ起動失敗、ヘルスチェック失敗 | Sev 1 |
| 3 | [DB コネクション枯渇](#3-db-コネクション枯渇) | プール設定ミス、スロークエリ、ロック | Sev 2 |
| 4 | [レイテンシ悪化](#4-レイテンシ悪化) | DB遅延、外部サービス遅延、GC | Sev 2-3 |
| 5 | [Aurora フェイルオーバー](#5-aurora-フェイルオーバー) | ハードウェア障害、メンテナンス | Sev 2 |
| 6 | [ElastiCache 接続エラー](#6-elasticache-接続エラー) | ノード障害、メモリ枯渇 | Sev 2 |
| 7 | [証明書期限切れ](#7-証明書期限切れ) | 更新忘れ | Sev 1 |
| 8 | [ディスク容量不足](#8-ディスク容量不足) | ログ肥大、パーティション未削除 | Sev 3 |

---

## 1. 5xx エラー率の急増

### 確認

```bash
# 1. ALB の 5xx 内訳を確認
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name HTTPCode_Target_5XX_Count \
  --start-time $(date -u -d '30 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 --statistics Sum

# 2. 直近のデプロイがあったか
aws ecs describe-services --cluster idp-cluster --services idp-server \
  --query 'services[0].deployments'

# 3. アプリログでエラーを確認
aws logs filter-log-events \
  --log-group-name /ecs/idp-server \
  --filter-pattern "ERROR" \
  --start-time $(date -d '15 minutes ago' +%s000) \
  --limit 20
```

### 対処

| 状況 | 対処 |
|:---|:---|
| 直近のデプロイあり | **ロールバック**（最速） |
| デプロイなし + DB エラー | → [パターン3](#3-db-コネクション枯渇) へ |
| デプロイなし + OOM | ECS タスクのメモリ上限を確認、必要なら増加 |
| デプロイなし + 原因不明 | **アプリ再起動**（force-new-deployment） |

---

## 2. 全タスク Unhealthy

### 確認

```bash
# 1. ターゲットヘルス確認
aws elbv2 describe-target-health \
  --target-group-arn $TARGET_GROUP_ARN

# 2. タスクの状態確認
aws ecs describe-services --cluster idp-cluster --services idp-server \
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount}'

# 3. 停止したタスクの理由
aws ecs list-tasks --cluster idp-cluster --service idp-server \
  --desired-status STOPPED --max-items 5
# → 各タスクの stoppedReason を確認
```

### 対処

| 状況 | 対処 |
|:---|:---|
| タスクが起動しない（Pending のまま） | ECS イベントログ確認（リソース不足、イメージプル失敗） |
| タスクが起動直後に停止 | アプリログ確認（DB接続失敗、設定エラー） |
| ヘルスチェックだけ失敗 | ヘルスチェックパス（`/actuator/health/readiness`）を手動確認 |

---

## 3. DB コネクション枯渇

### 確認

```sql
-- 接続数
SELECT count(*) FROM pg_stat_activity;

-- 状態別
SELECT state, count(*) FROM pg_stat_activity GROUP BY state;

-- 待機中のクエリ
SELECT pid, state, wait_event_type, wait_event,
  NOW() - query_start AS duration, LEFT(query, 80)
FROM pg_stat_activity
WHERE state = 'active' AND wait_event_type = 'Lock'
ORDER BY duration DESC;
```

### 対処

| 状況 | 対処 |
|:---|:---|
| idle 接続が多い | HikariCP の idle-timeout を短縮 |
| active + Lock 待ちが多い | ロック元のクエリを特定して対処（→ [ロック実践](../postgresql/dev-04-transactions#11-ロック実践-よくある問題パターン)） |
| max_connections に到達 | RDS パラメータで max_connections を増加、またはアプリの pool-size を削減 |
| 一時的な対処 | 長時間の idle セッションを切断: `SELECT pg_terminate_backend(pid)` |

---

## 4. レイテンシ悪化

### 確認

```bash
# 1. どのエンドポイントが遅いか（ALBアクセスログ）
# /v1/tokens が遅い？ /v1/authorizations が遅い？

# 2. DB スロークエリ
# Performance Insights → Top SQL

# 3. GC ログ
aws logs filter-log-events \
  --log-group-name /ecs/idp-server \
  --filter-pattern "GC pause" \
  --start-time $(date -d '30 minutes ago' +%s000)
```

### 対処

| 状況 | 対処 |
|:---|:---|
| 特定クエリが遅い | EXPLAIN ANALYZE で実行計画確認、インデックス追加 |
| フック実行が遅い | フックのタイムアウト短縮、または一時無効化 |
| GC が頻発 | ヒープサイズ確認（`-Xmx`）、メモリリーク調査 |
| 全体的に遅い | スケールアウト（ECS タスク増加） |

---

## 5. Aurora フェイルオーバー

### 確認

```bash
# RDS イベント確認
aws rds describe-events \
  --source-identifier idp-cluster \
  --source-type db-cluster \
  --duration 60
```

### 対処

```
Aurora フェイルオーバーは自動で完了する（30秒〜1分）:
  ・Reader が新 Writer に昇格
  ・エンドポイントが自動更新
  ・アプリ側は一時的な接続エラー → HikariCP が自動再接続

やること:
  □ フェイルオーバーの完了を確認
  □ アプリの接続が復旧したことを確認
  □ 原因を確認（計画メンテナンス？ハードウェア障害？）
  □ ポストモーテムの要否判断
```

---

## 6. ElastiCache 接続エラー

### 確認

```bash
# ノードの状態
aws elasticache describe-cache-clusters \
  --cache-cluster-id idp-redis \
  --show-cache-node-info
```

### 対処

| 状況 | 対処 |
|:---|:---|
| ノードが Available でない | フェイルオーバーを待つ（Multi-AZ の場合は自動） |
| メモリ使用率 100% | maxmemory-policy 確認（`allkeys-lru` 推奨）、不要キーの削除 |
| 接続数上限 | アプリの Redis 接続プール設定を確認 |

---

## 7. 証明書期限切れ

### 確認

```bash
# ACM 証明書の期限確認
aws acm list-certificates --query 'CertificateSummaryList[*].[DomainName,NotAfter]'
```

### 対処

```
ACM（AWS Certificate Manager）を使っている場合:
  → DNS 検証なら自動更新される
  → 自動更新されていない場合は DNS レコードを確認

自己署名証明書の場合:
  → 新しい証明書を生成して ALB / CloudFront に適用
  → mkcert で生成している場合は有効期限を確認
```

---

## 8. ディスク容量不足

### 確認

```bash
# Aurora ストレージ使用量
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name VolumeBytesUsed \
  --dimensions Name=DBClusterIdentifier,Value=idp-cluster \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 --statistics Average
```

### 対処

| 状況 | 対処 |
|:---|:---|
| security_event パーティションが肥大 | pg_partman のメンテナンス実行、古いパーティションをアーカイブ |
| 不要なテストデータ | `DELETE FROM security_event WHERE description = 'benchmark-test-data'` |
| Aurora ストレージ | Aurora は自動拡張するが、不要データ削除でコスト削減 |

---

## このランブックの更新

障害対応のたびに以下を更新する:

```
□ 新しい障害パターンを追加
□ 既存パターンの対処手順を改善（実際に使って気づいた点）
□ 閾値の調整（アラートが鳴りすぎ or 鳴らなすぎ）
□ 確認コマンドの更新（リソース名の変更等）
```

---

## 関連ドキュメント

- [障害の分類と対応体制](severity-and-roles): 重大度の定義、役割分担
- [アラート設計](alert-design): 監視メトリクスと閾値
- [初動対応](first-response): 最初の5分
- [調査の進め方](investigation): 原因特定の方法
- [復旧パターン](recovery-patterns): ロールバック、スケールアウト
- [ポストモーテム](postmortem): 振り返りと再発防止
