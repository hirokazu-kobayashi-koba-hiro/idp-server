# 調査の進め方

[初動対応](first-response)で影響範囲の把握と方針決定ができたら、次は原因を特定します。

初動で「リリース直後 → ロールバック」と判断できれば調査は不要ですが、リリースに起因しない場合や、ロールバックしても再発する場合は、ここからの調査が必要になります。Web サービスでは「**どの層が詰まっているか**」を特定するのが最初のステップです。

---

## 調査の原則

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  1. 外側から内側へ                                          │
│     LB → アプリ → DB → 外部サービス の順に調べる           │
│     → 外側で異常が見つかれば、内側は調べなくていい          │
│                                                             │
│  2. 変わったものを探す                                      │
│     「昨日まで動いていたのに今日動かない」                   │
│     → 昨日と今日で何が変わった？                            │
│     → デプロイ？設定変更？トラフィック？インフラ障害？       │
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

### レイヤー1: ロードバランサー（リクエストの入口）

```
確認すること:
  □ 5xx エラーの内訳（502/503/504 のどれ？）
     502: アプリが不正なレスポンスを返した
     503: バックエンドが Unhealthy
     504: タイムアウト（アプリが応答しない）
  □ バックエンドのヘルス（何台 Healthy / Unhealthy？）
  □ リクエスト数の推移（急増していないか）

確認方法:
  ・ロードバランサーのダッシュボード（エラー率、レイテンシ、ヘルスチェック）
  ・アクセスログでエラーが多いパスを特定
```

### レイヤー2: アプリ

```
確認すること:
  □ インスタンス / コンテナの状態（稼働中？停止？再起動ループ？）
  □ 最近の停止理由
  □ アプリログのエラー（ERROR / Exception で検索）
  □ CPU / メモリ使用率
  □ GC ログ（JVM の場合）

確認方法:
  ・コンテナオーケストレーションのダッシュボード（Kubernetes / ECS）
  ・ログ集約ツールで ERROR をフィルタ（CloudWatch Logs / Datadog / Grafana Loki）
  ・APM ツールでトレースを確認（Datadog APM / New Relic / Jaeger）
```

### レイヤー3: DB

```
確認すること:
  □ 接続数（max_connections に近づいていないか）
  □ CPU / メモリ使用率
  □ スロークエリ
  □ Wait Event（ロック待ち等）
  □ レプリカラグ
  □ DB イベント（フェイルオーバー等）

確認方法:
  ・DB のダッシュボード（Performance Insights / pgAdmin / Datadog DBM）
  ・SQL で直接確認:

  -- 接続数
  SELECT count(*) FROM pg_stat_activity;
  SELECT state, count(*) FROM pg_stat_activity GROUP BY state;

  -- ロック待ち
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
  □ 外部 API 呼び出しの遅延（Webhook / メール送信等）
  □ 外部 IdP が応答しているか
  □ クラウドプロバイダーのステータスページ

確認方法:
  ・外部サービスのステータスページを確認
    （AWS: health.aws.amazon.com / GCP: status.cloud.google.com）
  ・アプリログで外部呼び出しのタイムアウト / エラーを検索
  ・APM ツールで外部呼び出しのレイテンシを確認
```

---

## タイムライン分析

原因特定で最も強力なのは**タイムラインの照合**です。

```
時刻          5xx率      DB接続数    直近イベント
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
  ① LB → エラーは出ていないが、レイテンシ悪化
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

  1. 外側から内側へ（LB → アプリ → DB → 外部）
  2. 変わったものを探す（デプロイ？設定？トラフィック？）
  3. タイムラインを作る（時系列で因果関係を特定）
  4. 仮説→検証を繰り返す（推測で対処しない）
```

## 次のステップ

- [復旧パターン](recovery-patterns): 原因が特定できたら、どう復旧するか
