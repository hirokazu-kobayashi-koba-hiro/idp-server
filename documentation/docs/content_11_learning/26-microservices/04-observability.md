---
sidebar_position: 4
---

# マイクロサービスの観測性

---

## 概要

マイクロサービスでは、複数のサービスが協調して動作するため、システム全体の状態把握が困難です。観測性（Observability）は、ログ、メトリクス、トレーシングの3つの柱で構成されます。

---

## 観測性の3つの柱

```
┌─────────────────────────────────────────┐
│         観測性（Observability）          │
├─────────────────────────────────────────┤
│  1. ログ（Logs）                        │
│     何が起きたかの記録                   │
│                                         │
│  2. メトリクス（Metrics）               │
│     数値データ（CPU、メモリ、リクエスト数等）│
│                                         │
│  3. トレーシング（Tracing）             │
│     リクエストの経路追跡                 │
└─────────────────────────────────────────┘
```

---

## ログ管理

### 構造化ログ

**JSON形式の推奨**:
```json
{
  "timestamp": "2026-01-24T10:15:30.123Z",
  "level": "ERROR",
  "service": "order-service",
  "trace_id": "abc123",
  "span_id": "def456",
  "user_id": "user-789",
  "message": "Payment failed",
  "error": {
    "type": "PaymentException",
    "message": "Insufficient funds"
  }
}
```

**利点**:
- 機械可読
- 検索・集計が容易
- トレースIDで関連ログを結合

---

### 集中ログ管理（ELK Stack）

```
┌─────────────────────────────────────────┐
│         ELK Stack                       │
├─────────────────────────────────────────┤
│  各サービス                              │
│    ↓ ログ出力                           │
│  Filebeat / Fluentd（ログ収集）         │
│    ↓                                    │
│  Logstash（フィルタ・変換）             │
│    ↓                                    │
│  Elasticsearch（保存・検索）            │
│    ↓                                    │
│  Kibana（可視化・分析）                 │
└─────────────────────────────────────────┘

代替スタック:
- Loki + Grafana
- CloudWatch Logs
- Google Cloud Logging
```

---

### ログレベルの使い分け

```
ERROR: 即座の対応が必要
├─ 例外発生、サービス停止
└─ アラート発報

WARN: 注意が必要だが動作継続
├─ リトライ発生、遅延警告
└─ モニタリング

INFO: 重要なイベント
├─ リクエスト受信、処理完了
└─ 通常の運用ログ

DEBUG: デバッグ情報
├─ 開発環境のみ
└─ 本番では通常OFF
```

---

## メトリクス

### 4つのゴールデンシグナル

```
1. レイテンシ（Latency）:
   リクエストの応答時間
   └─ p50, p95, p99 で測定

2. トラフィック（Traffic）:
   秒間リクエスト数（RPS）
   └─ スループットの指標

3. エラー（Errors）:
   失敗したリクエストの割合
   └─ エラー率

4. サチュレーション（Saturation）:
   リソースの使用率
   └─ CPU、メモリ、ディスクI/O等
```

---

### Prometheus + Grafana

```
メトリクス収集・可視化:

各サービス:
  ↓ メトリクスエンドポイント（/metrics）
Prometheus:
  ├─ 定期的にスクレイピング
  ├─ 時系列データ保存
  └─ アラート評価
  ↓
Grafana:
  └─ ダッシュボード表示
```

**メトリクスの例**:
```
http_requests_total{service="order", method="POST", status="200"} 1523
http_request_duration_seconds{service="order", quantile="0.95"} 0.234
db_connections_active{service="order"} 12
```

---

## 分散トレーシング

### OpenTelemetry

**リクエストの経路を追跡**:
```
リクエストID: trace-abc123

┌────────────────────────────────────────┐
│ Span 1: API Gateway                   │
│ Duration: 250ms                        │
│   ├─ Span 2: Order Service            │
│   │  Duration: 180ms                   │
│   │    ├─ Span 3: DB Query            │
│   │    │  Duration: 50ms               │
│   │    └─ Span 4: Inventory Service   │
│   │       Duration: 100ms              │
│   │         └─ Span 5: DB Query       │
│   │            Duration: 30ms          │
│   └─ Span 6: Notification Service     │
│      Duration: 20ms                    │
└────────────────────────────────────────┘

可視化:
- どこで時間がかかっているか一目瞭然
- ボトルネックの特定
- エラー箇所の特定
```

---

### トレーシングツール

```
主要ツール:
├─ Jaeger
├─ Zipkin
├─ AWS X-Ray
└─ Google Cloud Trace

実装:
各サービスで Trace ID と Span ID を伝搬:

HTTP Header:
X-Trace-Id: abc123
X-Span-Id: def456
X-Parent-Span-Id: xyz789
```

---

## ヘルスチェック

### Liveness vs Readiness

```
Liveness Probe（生存確認）:
├─ プロセスが生きているか
└─ 失敗 → コンテナ再起動

GET /health/live
→ 200 OK（生きている）
→ 503 Service Unavailable（死んでいる）

Readiness Probe（準備確認）:
├─ リクエストを受け付けられるか
└─ 失敗 → トラフィックを流さない

GET /health/ready
→ 200 OK（準備完了）
→ 503 Service Unavailable（準備中/障害中）

例: DB接続確認
準備中: DB接続不可 → 503
準備完了: DB接続OK → 200
```

---

## ダッシュボード設計

### サービス別ダッシュボード

```
Order Service ダッシュボード:

┌──────────────────────────────────┐
│ リクエスト数（RPS）              │
│ [グラフ: 過去24時間の推移]       │
├──────────────────────────────────┤
│ レイテンシ（p95）                │
│ [グラフ: 234ms]                  │
├──────────────────────────────────┤
│ エラー率                          │
│ [グラフ: 0.3%]                   │
├──────────────────────────────────┤
│ CPU使用率                         │
│ [グラフ: 45%]                    │
├──────────────────────────────────┤
│ DB接続数                          │
│ [グラフ: 15/100]                 │
└──────────────────────────────────┘
```

---

### 全体俯瞰ダッシュボード

```
System Overview:

サービス一覧:
├─ API Gateway:     ✅ 正常
├─ Order Service:   ⚠️  高負荷
├─ Inventory:       ✅ 正常
├─ Payment:         ❌ 障害
└─ Notification:    ✅ 正常

アクティブアラート: 2件
最近のデプロイ: Payment Service (10分前)
```

---

## アラート設計

### アラートルール

```
適切なアラート設定:

✓ 実行可能なアラート:
「Payment Service のエラー率が5%を超えた」
→ 調査・対応が必要

❌ ノイズ:
「CPU使用率が50%を超えた」
→ 正常範囲、対応不要

原則:
- アラートは「対応が必要なもの」のみ
- SLO（Service Level Objective）ベース
- オンコール対応可能な件数に抑える
```

---

### SLI / SLO / SLA

```
SLI（Service Level Indicator）:
測定可能な指標
例: リクエスト成功率、レイテンシp95

SLO（Service Level Objective）:
目標値
例: 成功率 99.9%、p95 < 200ms

SLA（Service Level Agreement）:
顧客との契約
例: 稼働率 99.95%、違反時は返金

関係:
SLA ≧ SLO（バッファを持たせる）
SLO を SLI で測定
```

---

## まとめ

### 観測性の実装

```
最小構成:
├─ 構造化ログ（JSON）
├─ 基本メトリクス（4つのゴールデンシグナル）
└─ ヘルスチェック

推奨構成:
├─ 上記 +
├─ 分散トレーシング
├─ ダッシュボード
└─ アラート

エンタープライズ:
├─ 上記 +
├─ SLI/SLO管理
├─ 異常検知（AI活用）
└─ 自動復旧
```

---

## 参考リンク

- [OpenTelemetry](https://opentelemetry.io/)
- [The Four Golden Signals (Google SRE)](https://sre.google/sre-book/monitoring-distributed-systems/)
- [Prometheus Documentation](https://prometheus.io/docs/)

**最終更新**: 2026-01-24
