# 調査の進め方

[初動対応](first-response)で影響範囲の把握と方針決定ができたら、次は原因を特定します。

初動で「リリース直後 → ロールバック」と判断できれば調査は不要ですが、リリースに起因しない場合や、ロールバックしても再発する場合は、ここからの調査が必要になります。Web サービスでは「**どの層が詰まっているか**」を特定するのが最初のステップです。

---

## 調査の原則

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  1. 外側から内側へ                                          │
│     ALB → アプリ → DB → 外部サービス の順に調べる          │
│     → 外側で異常が見つかれば、内側は調べなくていい          │
│                                                             │
│  2. 変わったものを探す                                      │
│     「昨日まで動いていたのに今日動かない」                   │
│     → 昨日と今日で何が変わった？                            │
│     → デプロイ？設定変更？トラフィック？AWS 障害？           │
│                                                             │
│  3. 仮説→検証を繰り返す                                    │
│     「たぶん DB が遅い」→ DB のメトリクスを確認              │
│     → 正しければ DB を深掘り                                │
│     → 違えば次の仮説へ                                      │
│                                                             │
│  4. タイムラインを作る                                      │
│     何時何分に何が起きたかを時系列で整理                     │
│     → 因果関係が見えてくる                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## レイヤー別の調査

### レイヤー1: ALB（リクエストの入口）

```
確認すること:
  □ 5xx エラーの内訳（502/503/504 のどれ？）
     502: アプリが不正なレスポンスを返した
     503: ターゲットが Unhealthy
     504: タイムアウト（アプリが応答しない）
  □ ターゲットヘルス（何台 Healthy / Unhealthy？）
  □ リクエスト数の推移（急増していないか）

コマンド:
  # ターゲットヘルス確認
  aws elbv2 describe-target-health \
    --target-group-arn $TARGET_GROUP_ARN

  # ALB アクセスログ（S3 に保存されている場合）
  # エラーが多いパスを特定
  aws s3 cp s3://alb-logs/AWSLogs/... - | \
    grep " 5[0-9][0-9] " | \
    awk '{print $13}' | sort | uniq -c | sort -rn
```

### レイヤー2: アプリ（ECS / idp-server）

```
確認すること:
  □ タスクの状態（Running / Stopped / Pending）
  □ 最近のタスク停止理由
  □ アプリログのエラー
  □ CPU / メモリ使用率
  □ GC ログ（JVM）

コマンド:
  # タスク一覧
  aws ecs list-tasks --cluster idp-cluster --service idp-server

  # 停止理由の確認
  aws ecs describe-tasks --cluster idp-cluster \
    --tasks $TASK_ARN \
    --query 'tasks[0].stoppedReason'

  # アプリログ（直近のエラー）
  aws logs filter-log-events \
    --log-group-name /ecs/idp-server \
    --filter-pattern "ERROR" \
    --start-time $(date -d '15 minutes ago' +%s000) \
    --limit 20

  # CloudWatch Logs Insights
  fields @timestamp, @message
  | filter @message like /ERROR|Exception/
  | sort @timestamp desc
  | limit 50
```

### レイヤー3: DB（Aurora）

```
確認すること:
  □ 接続数（max_connections に近づいていないか）
  □ CPU / メモリ使用率
  □ スロークエリ（Performance Insights）
  □ Wait Event（ロック待ち等）
  □ レプリカラグ
  □ RDS イベント（フェイルオーバー等）

コマンド:
  # RDS イベント確認（直近1時間）
  aws rds describe-events \
    --source-identifier idp-cluster \
    --source-type db-cluster \
    --duration 60

  # 接続数確認（SQL）
  SELECT count(*) FROM pg_stat_activity;
  SELECT state, count(*) FROM pg_stat_activity GROUP BY state;

  # ロック待ちの確認
  SELECT blocked.pid, blocked.query, blocking.pid, blocking.query
  FROM pg_locks blocked
  JOIN pg_locks blocking ON blocked.transactionid = blocking.transactionid
    AND blocked.pid != blocking.pid
  JOIN pg_stat_activity blocked_activity ON blocked.pid = blocked_activity.pid
  JOIN pg_stat_activity blocking_activity ON blocking.pid = blocking_activity.pid
  WHERE NOT blocked.granted;
```

### レイヤー4: 外部サービス

```
確認すること:
  □ フック実行の遅延（Slack / Webhook / Email）
  □ Federation 先の IdP が応答しているか
  □ AWS サービスのステータス（health.aws.amazon.com）

コマンド:
  # AWS サービスヘルス
  aws health describe-events --filter '{
    "eventTypeCategories": ["issue"],
    "regions": ["ap-northeast-1"]
  }'
```

---

## タイムライン分析

原因特定で最も強力なのは**タイムラインの照合**です。

```
時刻          ALB 5xx    DB接続数    直近イベント
─────         ──────     ────────    ──────────
09:00         0.1%       50
09:15         0.1%       50          デプロイ開始
09:20         0.2%       80          デプロイ完了（v2 起動）
09:25         5.0%       120         ← ★ ここから急増
09:30         8.0%       150 (max)   コネクション枯渇

→ デプロイ完了直後にDB接続数が急増
→ v2 のコネクションプール設定を確認
→ min-idle が 10 → 30 に変更されていた
→ 4タスク × 30接続 = 120 で max_connections に到達
```

---

## 実事例: 統計テーブルのロック競合（#1442）

今回のセッションで実際に対応した事例を調査フローで振り返ります。

```
タイムライン:
  症状: INSERT INTO statistics_events の平均実行時間が 1.94秒（通常 0.53ms）

  調査フロー:
  ① ALB → エラーは出ていないが、レイテンシ悪化
  ② アプリ → SecurityEventHandler の非同期スレッドが詰まっている
  ③ DB → pg_stat_activity で transactionid の wait が 28 セッション
  ④ Wait Event の分析 → statistics_events の同一行にロック集中

  根本原因:
  → SecurityEventHandler が統計 UPDATE（ロック取得）後にフック実行（450ms I/O）
  → ロック保持中に I/O → カスケード的にロック待ち

  対処:
  Phase 1: 統計更新とフック実行の順序を入れ替え（即時緩和）
  Phase 3: バッチ集計に移行（根本解決）
```

> 詳細は [ケーススタディ: 統計テーブルのロック競合](../performance-tuning/case-study-lock-contention) を参照

---

## まとめ

```
調査の進め方:

  1. 外側から内側へ（ALB → アプリ → DB → 外部）
  2. 変わったものを探す（デプロイ？設定？トラフィック？）
  3. タイムラインを作る（時系列で因果関係を特定）
  4. 仮説→検証を繰り返す（推測で対処しない）
```

## 次のステップ

- [復旧パターン](recovery-patterns): 原因が特定できたら、どう復旧するか
