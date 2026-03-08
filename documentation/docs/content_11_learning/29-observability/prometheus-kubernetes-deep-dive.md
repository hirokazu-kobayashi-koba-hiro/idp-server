---
sidebar_position: 6
---

# Prometheus 詳細編 — TSDB・PromQL・スケーリング

## このドキュメントの目的

[Prometheus と Kubernetes](./prometheus-kubernetes.md) の続編です。TSDB の内部構造、PromQL のクエリ技法、スケーリング戦略、よくある落とし穴を扱います。Prometheus の運用やダッシュボード・アラートの設計時に役立つ知識です。

---

## TSDB（Time Series Database）

Prometheus は専用の時系列データベースを内蔵しています。「追記のみの大量数値データ」と「直近データの高速クエリ」に特化した設計です。

### ストレージ構造

```
┌──────────────────────────────────────────────────────────┐
│  Head Block（インメモリ）   │  Persistent Blocks（ディスク） │
│  直近 2-3 時間             │  それ以前のデータ（不変）       │
│                            │                               │
│  ┌────────────┐           │  ┌─────────┐  ┌─────────┐   │
│  │ Active     │           │  │  2h     │  │  6h     │   │
│  │ Chunks     │  ──2h──→  │  │  Block  │  │  Block  │   │
│  │ (書込可)   │  compaction│  └─────────┘  └─────────┘   │
│  ├────────────┤           │       │                       │
│  │ mmap'd     │           │       │ compaction            │
│  │ Chunks     │           │       ▼                       │
│  │ (読取専用) │           │  ┌─────────┐                  │
│  ├────────────┤           │  │  18h    │                  │
│  │ Inverted   │           │  │  Block  │  ... → 最大31日  │
│  │ Index      │           │  └─────────┘                  │
│  └────────────┘           │                               │
│                            │                               │
│  WAL（Write-Ahead Log）    │                               │
│  クラッシュリカバリ用       │                               │
└──────────────────────────────────────────────────────────┘
```

#### Head Block

- 新しいサンプルは全て Head Block に書き込まれる（唯一のミュータブル領域）
- チャンクが **120 サンプル** または **2 時間** に達すると、ディスクにフラッシュされ memory-mapped になる
- **転置インデックス** をインメモリで保持し、ラベルからの時系列検索を高速化

#### WAL（Write-Ahead Log）

- Head Block への全書込みは WAL にも追記される
- クラッシュ後の起動時に WAL をリプレイして Head Block を復元
- 起動時間は WAL サイズに比例する

#### Persistent Blocks

Head が約 3 時間分のデータを蓄積すると **Head Compaction** が発生し、最古の 2 時間分が不変ブロックとしてディスクに書き出されます。

```
各ブロックのディレクトリ構成:

01BKGV7JBM69T2G1BGBGM6KB12/
  meta.json      ← 時間範囲、統計情報
  index          ← 転置インデックス（ラベル → 時系列 → チャンク参照）
  chunks/
    000001       ← チャンクデータ（Gorilla 圧縮、1-2 bytes/sample）
  tombstones     ← 削除マーカー
```

#### Compaction

ブロックは定期的にマージされ、クエリ効率を改善します。

```
2h → 6h → 18h → 54h → 162h → 486h（3倍ずつ拡大、最大31日）
```

#### Retention

| フラグ | デフォルト | 説明 |
|-------|----------|------|
| `--storage.tsdb.retention.time` | 15日 | 時間ベースの保持期間 |
| `--storage.tsdb.retention.size` | 無制限 | サイズベースの上限 |

#### メモリ使用量の決定要因

| 要因 | 影響 |
|------|------|
| **アクティブな時系列数** | 最大の影響。各時系列がメモリを消費 |
| **ラベル数/時系列** | ラベルが多いほどインデックスが大きくなる |
| **時系列チャーン** | Pod の作成/削除が頻繁だと GC 負荷が増大 |

> **容量計画の目安**: `必要ディスク = 保持期間(秒) × サンプル/秒 × 1〜2 bytes/sample`。時系列数を減らす方がスクレイプ間隔を伸ばすより効果的。

---

## PromQL

### データ型

| 型 | 説明 | 例 |
|----|------|-----|
| **Instant Vector** | 各時系列の最新 1 サンプル | `http_requests_total{job="api"}` |
| **Range Vector** | 各時系列の一定期間のサンプル列 | `http_requests_total[5m]` |
| **Scalar** | 単一の数値 | `3.14` |

```
Instant Vector（テーブル/グラフに表示可能）:
  http_requests_total{method="GET"}   → 1234 @now
  http_requests_total{method="POST"}  →  567 @now

Range Vector（関数の入力として使用）:
  http_requests_total{method="GET"}[5m]
    → 1200 @t-5m, 1210 @t-4m, 1220 @t-3m, 1230 @t-2m, 1234 @now
    → rate() 等の関数に渡して Instant Vector に変換
```

### ラベルマッチャー

| 演算子 | 意味 |
|--------|------|
| `=`  | 完全一致 |
| `!=` | 不一致 |
| `=~` | 正規表現マッチ（完全アンカー: `foo` は `^foo$`） |
| `!~` | 正規表現の否定マッチ |

### 重要な関数

#### rate() — カウンターの毎秒変化率

```promql
rate(http_server_requests_seconds_count[5m])
```

```
rate() の内部動作:

  1. 範囲内の最初と最後のサンプルを取得
     first=1200 @t-5m, last=1500 @t

  2. 差分 / 経過時間 を計算
     (1500 - 1200) / 300s = 1.0 req/s

  3. カウンターリセットを検出・補正
     値が減少した場合 → プロセス再起動とみなし補正

  4. ウィンドウ端への外挿
     サンプルがウィンドウ境界と完全一致しない場合に補正
```

**重要ルール**: rate のウィンドウは **スクレイプ間隔の 4 倍以上** にする。

| スクレイプ間隔 | 最小ウィンドウ | 推奨 |
|--------------|-------------|------|
| 10s | `[1m]` | `[2m]` |
| 15s | `[1m]` | `[2m]` |
| 30s | `[2m]` | `[5m]` |
| 60s | `[5m]` | `[5m]` |

理由: 4 倍のウィンドウなら、1 回のスクレイプ失敗を許容してもデータポイントが 2 つ以上残る。

#### rate() vs irate()

```
rate()  — 範囲内の全サンプルを使った平均変化率（安定）
irate() — 最後の 2 サンプルのみの瞬間変化率（敏感）

  ┌────────────────────────────────────────────┐
  │ サンプル:  10  12  11  15  30  31          │
  │                                            │
  │ rate([5m]):  全体の平均 → 滑らか            │
  │ irate([5m]): 30→31 のみ → スパイクに敏感    │
  └────────────────────────────────────────────┘
```

| 観点 | rate() | irate() |
|------|--------|---------|
| 使用サンプル数 | ウィンドウ内全て | 最後の 2 つのみ |
| 安定性 | 滑らか | 変動大 |
| **アラート** | **推奨** | 非推奨（`for` が頻繁にリセットされる） |
| **グラフ** | トレンド把握 | スパイク検出 |

#### histogram_quantile() — パーセンタイル計算

```promql
-- P99 レイテンシ（全 URI 合算）
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket{namespace="idp"}[5m])) by (le)
)
```

- `le`（less-than-or-equal）ラベルでバケット境界を識別
- バケット内は**線形補間**で近似
- **`by (le)` が必須**: `le` を残さないと計算不能

#### 集約演算子

```promql
-- URI 別のリクエストレート
sum(rate(http_server_requests_seconds_count[5m])) by (uri, method)

-- instance ラベルを除外して集約
sum(rate(http_server_requests_seconds_count[5m])) without (instance, pod)
```

主要な集約演算子: `sum`, `avg`, `min`, `max`, `count`, `topk`, `bottomk`, `quantile`

### Recording Rules

高コストなクエリを事前計算し、新しい時系列として保存します。

```yaml
groups:
  - name: idp_rules
    interval: 30s
    rules:
      - record: job:http_requests:rate5m
        expr: sum(rate(http_server_requests_seconds_count[5m])) by (job)
```

**命名規約**: `level:metric:operations`
- `level` = 集約レベル（`job` 等）
- `metric` = 元のメトリクス名
- `operations` = 適用した関数（`rate5m` 等）

**使い所**: ダッシュボードの複数パネルで使うクエリ、高カーディナリティの事前集約、アラート式

---

## スケーリングと長期保存

### Federation

```
[Global Prometheus]  ← 集約済みメトリクスのみ
    │           │
[DC1 Prometheus]  [DC2 Prometheus]  ← フル解像度メトリクス
    │                  │
 [targets]          [targets]
```

階層型 Federation は Global Prometheus がデータセンター Prometheus の `/federate` エンドポイントから Recording Rule の結果だけを取得するパターンです。

```yaml
scrape_configs:
  - job_name: 'federate'
    honor_labels: true          # ← 元のラベルを保持
    metrics_path: '/federate'
    params:
      'match[]':
        - '{__name__=~"job:.*"}'  # Recording Rule のみ取得
    static_configs:
      - targets: ['prometheus-dc1:9090', 'prometheus-dc2:9090']
```

#### Federation の限界

- 大規模環境で `/federate` がボトルネックになり得る
- HA ペアの重複排除なし
- 長期保存には不向き

### Remote Write / Remote Read

Prometheus は `remote_write` でサンプルをリアルタイムに外部ストレージに送信できます。

```
┌────────────┐                     ┌──────────────────┐
│ Prometheus │  remote_write       │ Long-term Storage│
│            │ ──────────────────→ │                  │
│  WAL ──→ Queue Manager          │  Thanos          │
│          └→ [Shard 1] ────────→ │  Mimir           │
│          └→ [Shard 2] ────────→ │  Cortex          │
│          └→ [Shard N] ────────→ │                  │
└────────────┘                     └──────────────────┘
```

### Thanos vs Mimir

| 観点 | Thanos | Mimir |
|------|--------|-------|
| **アーキテクチャ** | 分散型（Sidecar が既存 Prometheus を拡張） | 集中型（Prometheus は remote_write のみ） |
| **導入の容易さ** | Sidecar 追加から始められる | 別途マイクロサービス群のデプロイが必要 |
| **大規模対応** | 大規模だと Sidecar 管理が複雑に | 水平スケール前提の設計 |
| **マルチテナント** | ラベルベース | ネイティブサポート |
| **ダウンサンプリング** | Compactor が 5m / 1h 解像度を自動生成 | なし |
| **推奨ケース** | 既存 Prometheus の段階的拡張 | 新規の大規模マルチテナント環境 |

---

## よくある落とし穴

### 1. 高カーディナリティ

```
✗ user_id、request_id、IP アドレスをラベルに使う
  → 数百万の時系列が生成され、メモリが枯渇

✓ 有限の値のみラベルに使う（method, status, uri テンプレート）

検出方法:
  prometheus_tsdb_head_series               ← アクティブ時系列数
  topk(10, count by (__name__)({__name__=~".+"}))  ← メトリクス別の時系列数
```

### 2. rate() のウィンドウが短すぎる

```
✗ rate(metric[30s]) + scrape_interval=15s
  → 最大 2 ポイント。1 回のスクレイプ失敗で結果が 0 件に

✓ rate(metric[5m]) + scrape_interval=15s
  → 約 20 ポイント。安定した結果
```

### 3. Staleness（陳腐化）の誤解

Pod が削除されても時系列は即座に消えません。

```
┌────────────────────────────────────────────────────┐
│ Pod 削除                                            │
│     │                                              │
│     ▼                                              │
│ 次のスクレイプで時系列が消える                        │
│     │                                              │
│     ▼                                              │
│ Prometheus が Staleness Marker（特殊な NaN）を書込み │
│     │                                              │
│     ▼                                              │
│ PromQL がこの時系列を結果から除外                    │
│                                                    │
│ Lookback Delta（デフォルト 5 分）以内にサンプルが     │
│ なければ、時系列は自動的にクエリ結果から消える         │
└────────────────────────────────────────────────────┘
```

### 4. Counter リセットの見落とし

```
✗ Gauge に rate() を適用
  → 値の減少がカウンターリセットと誤認され、不正な結果に

✓ Gauge の変化率には deriv() を使用
  rate() / irate() / increase() はカウンター専用
```

### 5. histogram_quantile() で by (le) を忘れる

```promql
-- ✗ le が消えて計算不能
histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[5m])))

-- ✓ le を残す
histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[5m])) by (le))

-- ✓ URI 別に分けつつ le を残す
histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[5m])) by (le, uri))
```

### 6. アラートに irate() を使う

```
✗ irate() は最後の 2 サンプルのみ使用
  → 瞬間的なスパイクでアラート発火 → 次の評価で解消 → for リセット
  → アラートが安定しない

✓ アラートには rate() を使用
```

---

## まとめ

| トピック | ポイント |
|---------|---------|
| TSDB | Head Block（インメモリ）+ Persistent Blocks（不変）。1-2 bytes/sample の高効率圧縮 |
| PromQL | `rate()` はカウンターの変化率。ウィンドウはスクレイプ間隔の 4 倍以上。アラートには `irate()` ではなく `rate()` |
| スケーリング | Federation → Thanos/Mimir。Federation は簡易、Thanos は段階的拡張、Mimir は大規模向け |
| 落とし穴 | 高カーディナリティ、短すぎる rate ウィンドウ、Gauge への rate 適用が 3 大落とし穴 |

---

## 参考リソース

- [Prometheus 公式ドキュメント](https://prometheus.io/docs/)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)
- [Prometheus TSDB - Ganesh Vernekar's Blog Series](https://ganeshvernekar.com/blog/prometheus-tsdb-the-head-block/)
- [Thanos Documentation](https://thanos.io/tip/thanos/getting-started.md/)
- [Grafana Mimir Documentation](https://grafana.com/docs/mimir/latest/)

---

**最終更新**: 2026-03-08
**対象**: SaaS アプリケーション開発者、SRE、プラットフォームエンジニア
