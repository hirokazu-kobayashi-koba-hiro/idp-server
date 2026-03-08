---
sidebar_position: 5
---

# Prometheus と Kubernetes

## このドキュメントの目的

Prometheus が何をしてくれるのか、Kubernetes 環境でどう動くのかを理解することが目標です。

[メトリクスとアラート設計](./metrics-and-alerting.md) が「何を計測すべきか（設計論）」を扱うのに対し、本ドキュメントは「どう動いているか（仕組み）」を扱います。PromQL や TSDB の内部動作など、より深い内容は [詳細編](./prometheus-kubernetes-deep-dive.md) を参照してください。

---

## Prometheus とは

**「サービスが今どういう状態か」を数値で把握し、異常があれば通知するシステム**です。

### なぜ Kubernetes 環境で Prometheus か

メトリクス監視ツールは CloudWatch、Datadog、Zabbix など多数あります。その中で Prometheus が Kubernetes 環境のデファクトとなっている理由は **Kubernetes との深い統合** にあります。

| 特徴 | Prometheus | 従来型の監視ツール |
|------|-----------|-----------------|
| **ターゲット検出** | K8s API を Watch し、Pod の増減に自動追従 | エージェント配布やホスト登録が必要 |
| **設定管理** | ServiceMonitor CRD で宣言的に定義 | 設定ファイルの直接編集 |
| **スケーリング対応** | Pod が増えても自動でスクレイプ開始 | スケール時に手動で監視対象を追加 |
| **エコシステム** | kube-state-metrics、node-exporter 等が標準装備 | K8s 専用プラグインが別途必要 |
| **コスト** | OSS（無料） | SaaS は従量課金が多い |

Prometheus は CNCF の **Graduated プロジェクト**（Kubernetes に次ぐ 2 番目の卒業）であり、Kubernetes と同じ設計思想（宣言的、ラベルベース）で構築されているため、K8s 環境で最も自然に統合できます。

### 具体的に何をしてくれるか

```
┌────────────────────────────────────────────────────────────┐
│  1. 収集    アプリやインフラから数値データを定期的に取得      │
│             「今のリクエスト数は？」「メモリ使用量は？」      │
│                                                             │
│  2. 保存    時系列データベース（TSDB）に蓄積                 │
│             「1 時間前と比べてどう変化した？」                │
│                                                             │
│  3. クエリ  PromQL で集計・分析                              │
│             「エラー率は？」「P99 レイテンシは？」            │
│                                                             │
│  4. 可視化  Grafana でグラフ・ダッシュボード表示              │
│             リアルタイムにサービスの健全性を確認              │
│                                                             │
│  5. 通知    閾値を超えたら Slack / PagerDuty に自動アラート  │
│             「エラー率が 5% を超えました」                    │
└────────────────────────────────────────────────────────────┘
```

### アプリ・Kubernetes・Prometheus の関係

Spring Boot アプリを Kubernetes にデプロイして Prometheus で監視する全体像です。

```
┌─────────────────────────────────────────────────────────────────────┐
│ Kubernetes クラスター                                                │
│                                                                      │
│  ┌─────────────────────────────────────────┐  ┌──────────────────┐ │
│  │ idp namespace                            │  │ monitoring ns    │ │
│  │                                          │  │                  │ │
│  │  ┌──────────────────────────────────┐   │  │  ┌────────────┐ │ │
│  │  │ Pod: idp-server                   │   │  │  │ Prometheus │ │ │
│  │  │                                   │   │  │  │            │ │ │
│  │  │  Spring Boot (Java)               │   │  │  │ 15秒ごとに │ │ │
│  │  │    │                              │   │  │  │ HTTP GET   │ │ │
│  │  │    ├─ ビジネスロジック             │   │  │  └──────┬─────┘ │ │
│  │  │    │   認証・認可・トークン発行     │   │  │         │       │ │
│  │  │    │                              │   │  │         │       │ │
│  │  │    ├─ Micrometer                  │   │  │         │       │ │
│  │  │    │   リクエスト数、レイテンシ、   │   │  │         │       │ │
│  │  │    │   JVMメモリ等を自動計測       │   │  │         │       │ │
│  │  │    │                              │   │  │         │       │ │
│  │  │    └─ /actuator/prometheus  ◄──────┼───┼──┼─────────┘       │ │
│  │  │       メトリクスをテキスト形式で公開│   │  │                  │ │
│  │  └──────────────────────────────────┘   │  │  ┌────────────┐ │ │
│  │                                          │  │  │  Grafana   │ │ │
│  │  ┌──────────────────────────────────┐   │  │  │            │ │ │
│  │  │ Service: idp-server               │   │  │  │ Prometheus │ │ │
│  │  │   labels: app=idp-server   ◄──────┼───┼──┼──┤ に問合せ   │ │ │
│  │  └──────────────────────────────────┘   │  │  │ → グラフ化 │ │ │
│  └─────────────────────────────────────────┘  │  └────────────┘ │ │
│                                                │                  │ │
│  ServiceMonitor（CRD）                         │                  │ │
│  ┌─────────────────────────────────────────┐  │                  │ │
│  │ "app=idp-server の Service の http ポート│  │                  │ │
│  │  を /actuator/prometheus で 15秒ごとに   │──┘                  │ │
│  │  スクレイプせよ"                         │    Prometheus が     │ │
│  └─────────────────────────────────────────┘    この定義を読む    │ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**データの流れ**:

```
1. アプリが計測      Spring Boot + Micrometer が HTTP リクエスト数、
                     レイテンシ、JVM メモリ等を自動計測

2. エンドポイント公開  /actuator/prometheus でテキスト形式のメトリクスを公開
                     （curl で直接確認できる）

3. Prometheus が収集  ServiceMonitor の定義に従い、15 秒ごとに
                     /actuator/prometheus を HTTP GET

4. Grafana が可視化   Prometheus に PromQL でクエリし、
                     時系列グラフとして表示
```

各コンポーネントの責務は明確に分離されています。

| コンポーネント | やること | やらないこと |
|--------------|---------|------------|
| **アプリ（Spring Boot）** | メトリクスを計測し `/actuator/prometheus` で公開 | 送信先の設定、データ保存 |
| **Kubernetes（Service）** | Pod へのネットワーク到達性を提供 | メトリクスの内容には関与しない |
| **ServiceMonitor** | 「何をどこからスクレイプするか」を宣言的に定義 | 実際のスクレイプは行わない |
| **Prometheus** | メトリクスを定期収集・保存・クエリ | 可視化は行わない |
| **Grafana** | Prometheus のデータをグラフ・ダッシュボードで表示 | データの収集・保存は行わない |

### 何を監視できるか

| レイヤー | 知りたいこと | メトリクス例 | 収集元 |
|---------|------------|------------|--------|
| アプリケーション | リクエストは捌けているか | HTTP リクエスト数、レイテンシ、エラー率 | Spring Boot Actuator |
| JVM | メモリやスレッドは大丈夫か | ヒープメモリ、GC 停止時間、スレッド数 | Micrometer |
| コンテナ | Pod のリソースは足りているか | CPU 使用率、メモリ使用量 | cAdvisor (kubelet 内蔵) |
| ノード | ホストマシンは健全か | CPU、メモリ、ディスク、ネットワーク | node-exporter |
| Kubernetes | クラスターは正常か | Pod 数、Deployment 状態、再起動回数 | kube-state-metrics |

### エコシステムにおける位置づけ

オブザーバビリティの 3 本柱（メトリクス・ログ・トレース）のうち、Prometheus は**メトリクス**を担当します。

```
┌─────────────────────────────────────────────────────────────┐
│                  オブザーバビリティの 3 本柱                   │
│                                                              │
│  メトリクス         ログ                トレース               │
│  「どれくらいか」   「何が起きたか」     「どこで起きたか」      │
│  ┌───────────┐    ┌───────────┐      ┌───────────┐         │
│  │Prometheus │    │ Loki /    │      │ Jaeger /  │         │
│  │           │    │ Fluentd   │      │ Tempo     │         │
│  └─────┬─────┘    └─────┬─────┘      └─────┬─────┘         │
│        │                │                  │                 │
│        └────────────────┼──────────────────┘                 │
│                         ▼                                    │
│                 ┌──────────────┐                             │
│                 │   Grafana    │  ← 3 本柱を統合表示          │
│                 └──────────────┘                             │
│                                                              │
│  Kubernetes 環境では kube-prometheus-stack（Helm chart）で    │
│  Prometheus + Grafana + Alertmanager を一括デプロイするのが定番│
└─────────────────────────────────────────────────────────────┘
```

---

## Prometheus アーキテクチャ

### コンポーネント構成

```
┌─────────────────────────────────────────────────────────────────┐
│                     Prometheus エコシステム                       │
│                                                                  │
│  ┌──────────────────────────────────────┐                       │
│  │         Prometheus Server            │                       │
│  │                                      │                       │
│  │  Service Discovery ──→ Scraper       │                       │
│  │  (K8s API, Consul,     (HTTP GET     │                       │
│  │   DNS, file, etc.)      /metrics)    │                       │
│  │                           │          │                       │
│  │                           ▼          │                       │
│  │                        TSDB          │                       │
│  │                   (時系列DB)          │                       │
│  │                           │          │                       │
│  │                     PromQL Engine    │                       │
│  └──────────┬───────────────┬──────────┘                       │
│             │               │                                    │
│             ▼               ▼                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Alertmanager │  │   Grafana    │  │ Pushgateway  │          │
│  │              │  │              │  │              │          │
│  │ グルーピング  │  │ 可視化       │  │ 短命ジョブ用  │          │
│  │ 抑制         │  │ ダッシュボード│  │ メトリクス    │          │
│  │ ルーティング  │  │              │  │ キャッシュ    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│        │                                                         │
│   Slack / PagerDuty / Email                                     │
└─────────────────────────────────────────────────────────────────┘
```

| コンポーネント | 役割 |
|--------------|------|
| **Prometheus Server** | ターゲット検出、メトリクス収集、TSDB ストレージ、PromQL クエリ |
| **Alertmanager** | アラートのグルーピング・抑制・サイレンス・ルーティング |
| **Pushgateway** | 短命ジョブ（バッチ、cron）のメトリクスを一時保持 |
| **Grafana** | 可視化・ダッシュボード（Prometheus 公式コンポーネントではないが事実上の標準） |

### Pull モデル

Prometheus の最大の特徴は **Pull（スクレイプ）モデル**です。

```
Push モデル（Datadog, CloudWatch）:
  [App] ──push──→ [Monitoring System]
  アプリがメトリクスを送る。送信先をアプリ側に設定。

Pull モデル（Prometheus）:
  [App] ←──scrape── [Prometheus]
  Prometheus がメトリクスを取りに行く。ターゲットは /metrics を公開するだけ。
```

#### Pull モデルの利点

| 利点 | 説明 |
|------|------|
| **ターゲット死活監視が自動** | スクレイプ失敗 = `up == 0`。Push では「送ってこない」と「落ちている」の区別が困難 |
| **デバッグが容易** | `curl http://target:8080/metrics` で何が公開されているか即座に確認できる |
| **収集頻度の中央制御** | Prometheus 側でスクレイプ間隔を決定。個別のアプリが監視システムを過負荷にしない |
| **エージェント不要** | HTTP エンドポイントを公開するだけ。Push 用エージェントの配布・管理が不要 |
| **サービスディスカバリ連携** | K8s API を Watch し、Pod 追加時に自動でスクレイプ開始 |

#### Pull モデルの制約

| 制約 | 対処法 |
|------|--------|
| **短命プロセスのメトリクス欠損** | スクレイプ間隔以下の寿命だとメトリクスが取れない → Pushgateway |
| **ネットワーク到達性** | Prometheus がターゲットにアクセスできる必要あり → Federation / Remote Write |

### データモデル

時系列はメトリクス名 + ラベルの組み合わせで一意に識別されます。

```
http_server_requests_seconds_count{method="GET", uri="/v1/tokens", status="200"}
                                    │                                          │
                                    └── ラベル（key=value ペア）                ┘
```

各時系列は `(timestamp, float64 value)` のペア（サンプル）を時系列順に保持します。同じメトリクス名でもラベルが異なれば別の時系列です。

---

## Kubernetes 統合

### Service Discovery

Prometheus は Kubernetes API を Watch し、リソースの変化に応じてスクレイプターゲットを動的に更新します。Pod が追加されれば自動で収集開始、削除されれば自動で停止します。

#### SD ロール

| ロール | 検出対象 | ターゲットアドレス | 用途 |
|--------|---------|-------------------|------|
| `node` | クラスターノード | ノードの InternalIP | node-exporter, kubelet |
| `pod` | 全 Pod のコンテナポート | `<pod_ip>:<container_port>` | Service なしの Pod |
| `service` | Service のポート | `<svc>.<ns>.svc:<port>` | ブラックボックスプローブ |
| `endpoints` | Endpoints（Service 背後の Pod） | `<pod_ip>:<port>` | **最も一般的** |
| `endpointslice` | EndpointSlice（endpoints の改良版） | `<pod_ip>:<port>` | 大規模クラスター向け |
| `ingress` | Ingress ルール | Ingress ホスト | HTTP プローブ |

### Prometheus Operator と CRD

[Prometheus Operator](https://prometheus-operator.dev/) は Kubernetes ネイティブな CRD で Prometheus の設定を管理します。YAML を書くだけでスクレイプ設定やアラートルールを追加でき、Prometheus の設定ファイルを直接触る必要がありません。

#### CRD 一覧

| CRD | 役割 |
|-----|------|
| `Prometheus` | Prometheus StatefulSet のデプロイ |
| `Alertmanager` | Alertmanager StatefulSet のデプロイ |
| `ServiceMonitor` | **Service 経由のスクレイプ定義** |
| `PodMonitor` | Pod 直接のスクレイプ定義 |
| `PrometheusRule` | アラート / Recording Rule の定義 |
| `ScrapeConfig` | 生のスクレイプ設定（エスケープハッチ） |

#### ServiceMonitor の仕組み

ServiceMonitor は「どの Service のどのポートからメトリクスを取るか」を宣言的に定義する CRD です。

```
┌──────────────────────────────────────────────────────────────┐
│  1. Operator が ServiceMonitor を Watch                       │
│                                                               │
│  ServiceMonitor                  Prometheus CR                │
│  ┌──────────────────┐          ┌───────────────────────┐    │
│  │ selector:         │          │ serviceMonitorSelector:│    │
│  │   app: idp-server │          │   release: prometheus  │    │
│  │ endpoints:        │          └───────────────────────┘    │
│  │   port: http      │                                       │
│  │   path: /actuator │                                       │
│  │         /prometheus│                                       │
│  └──────────────────┘                                        │
│            │                                                  │
│  2. Operator が scrape_configs を自動生成                     │
│            │                                                  │
│            ▼                                                  │
│  ┌──────────────────────────────────────────┐                │
│  │ scrape_configs:                           │                │
│  │   - job_name: serviceMonitor/.../0       │                │
│  │     kubernetes_sd_configs:                │                │
│  │       - role: endpoints                   │                │
│  │         namespaces: [idp]                 │                │
│  │     metrics_path: /actuator/prometheus    │                │
│  └──────────────────────────────────────────┘                │
│            │                                                  │
│  3. Secret に保存 → config-reloader が Prometheus をリロード  │
│     （再起動不要）                                             │
└──────────────────────────────────────────────────────────────┘
```

**ポイント**: ServiceMonitor を作成・更新するだけで、Prometheus のスクレイプ設定が自動的に反映されます。Prometheus Pod の再起動は不要です。

#### ServiceMonitor のセレクター

Prometheus CR の `serviceMonitorSelector` と ServiceMonitor の `metadata.labels` がマッチする必要があります。

```yaml
# Prometheus CR 側
serviceMonitorSelector:
  matchLabels:
    release: prometheus       # ← このラベルを持つ ServiceMonitor のみ取り込む

# ServiceMonitor 側
metadata:
  labels:
    release: prometheus       # ← マッチする
```

また、ServiceMonitor の `spec.selector` が対象 Service の `metadata.labels` とマッチする必要があります（`spec.selector` であり、Service の `spec.selector` ではない点に注意）。

### kube-prometheus-stack

`kube-prometheus-stack` は Helm chart で Prometheus エコシステムを一括デプロイします。

```
kube-prometheus-stack が含むもの:

  ┌─────────────────────────────────────────────┐
  │ Prometheus Operator                          │
  │ Prometheus Server                            │
  │ Alertmanager                                 │
  │ Grafana + Sidecar（ダッシュボード自動検出）    │
  │ node-exporter                                │
  │ kube-state-metrics                           │
  │ デフォルトの PrometheusRule（K8s アラート）     │
  │ デフォルトの Grafana ダッシュボード             │
  └─────────────────────────────────────────────┘
```

#### Grafana Sidecar によるダッシュボード自動検出

Grafana Pod には **sidecar コンテナ** が同梱されており、`grafana_dashboard: "1"` ラベルを持つ ConfigMap を自動検出してダッシュボードとしてロードします。

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboard-idp-server
  namespace: monitoring
  labels:
    grafana_dashboard: "1"          # ← sidecar がこのラベルを検出
data:
  idp-server-dashboard.json: |
    { "title": "IDP Server", "panels": [...] }
```

---

## セットアップに必要な準備

Prometheus でアプリケーションを監視するには、**3 つのレイヤー**で準備が必要です。

```
┌──────────────────────────────────────────────────────────────┐
│                                                               │
│  Step 1: アプリケーション          「メトリクスを公開する」      │
│  ─────────────────────────────────────────────────────────── │
│  ・メトリクスライブラリの追加                                   │
│  ・エンドポイントの有効化                                      │
│                                                               │
│  Step 2: Kubernetes マニフェスト   「何をスクレイプするか定義」  │
│  ─────────────────────────────────────────────────────────── │
│  ・Service にラベル付与                                       │
│  ・ServiceMonitor の作成                                      │
│  ・Grafana ダッシュボードの作成                                │
│                                                               │
│  Step 3: 監視基盤のインストール     「Prometheus + Grafana」    │
│  ─────────────────────────────────────────────────────────── │
│  ・kube-prometheus-stack の Helm インストール                  │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### Step 1: アプリケーション側の準備

アプリケーションが `/actuator/prometheus` でメトリクスを公開できるようにします。

**1-1. 依存ライブラリの追加**（`build.gradle`）

```groovy
dependencies {
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

**1-2. エンドポイントの有効化**（`application.yaml`）

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus   # ← prometheus を追加
```

**確認方法**: アプリを起動して `curl http://localhost:8080/actuator/prometheus` を実行。メトリクスがテキスト形式で出力されれば OK。

```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 4.2345678E7
...
```

### Step 2: Kubernetes マニフェストの準備

**2-1. Service にラベルとポート名を設定**

ServiceMonitor がマッチングに使うため、Service の `metadata.labels` と `ports.name` が必要です。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: idp-server
  labels:
    app: idp-server          # ← ServiceMonitor の selector がこれにマッチ
spec:
  selector:
    app: idp-server
  ports:
    - name: http             # ← ServiceMonitor の port 指定で使用
      port: 8080
      targetPort: 8080
```

**2-2. ServiceMonitor の作成**

Prometheus に「どの Service からメトリクスを収集するか」を伝える CRD です。

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: idp-server
  namespace: idp
  labels:
    release: prometheus      # Prometheus CR のセレクターにマッチさせる
spec:
  selector:
    matchLabels:
      app: idp-server        # ← Service の metadata.labels にマッチ
  namespaceSelector:
    matchNames: [idp]
  endpoints:
    - port: http             # ← Service の ports.name にマッチ
      path: /actuator/prometheus
      interval: 15s
```

**2-3. Grafana ダッシュボードの作成**（任意）

`grafana_dashboard: "1"` ラベル付き ConfigMap を作成すると、Grafana sidecar が自動検出してダッシュボードを追加します。

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboard-my-app
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
data:
  my-app-dashboard.json: |
    { "title": "My App", "panels": [...] }
```

### Step 3: 監視基盤のインストール

kube-prometheus-stack を Helm でインストールします。これで Prometheus、Grafana、Alertmanager が一括デプロイされます。

```bash
# Helm リポジトリ追加
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# インストール
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false
```

> `serviceMonitorSelectorNilUsesHelmValues=false` は重要です。これを設定しないと、`release: prometheus` ラベルのない ServiceMonitor が無視されます。

### セットアップ後の確認

```bash
# 1. Prometheus ターゲットの確認
#    Prometheus UI → Status → Targets で idp-server が "UP" であること
kubectl port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090 -n monitoring

# 2. Grafana でダッシュボード確認
kubectl port-forward svc/prometheus-grafana 3000:80 -n monitoring
#    ブラウザで http://localhost:3000 → Dashboards から確認
```

### よくあるトラブル

| 症状 | 原因 | 対処 |
|------|------|------|
| ターゲットが表示されない | ServiceMonitor のラベルが Prometheus CR のセレクターにマッチしない | Helm の `serviceMonitorSelectorNilUsesHelmValues=false` を確認 |
| ターゲットが DROPPED | Service の `metadata.labels` が ServiceMonitor の `selector` にマッチしない | Service に `labels: app: idp-server` を追加 |
| ダッシュボードに "No data" | Prometheus がまだスクレイプしていない / メトリクス名が違う | Prometheus UI で直接クエリして確認 |
| `/actuator/prometheus` が 404 | micrometer-registry-prometheus が未追加 / exposure 設定漏れ | `build.gradle` と `application.yaml` を確認 |

---

## idp-server での実装

上記セットアップ手順を idp-server がどのように適用しているかを示します。

```
┌──────────────────────────────────────────────────────────────┐
│ Step 1: アプリケーション                                       │
│                                                               │
│  app/build.gradle                                             │
│    implementation 'io.micrometer:micrometer-registry-prometheus│
│                                                               │
│  app/src/main/resources/application.yaml                      │
│    management.endpoints.web.exposure.include                  │
│      = health,info,prometheus                                 │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│ Step 2: Kubernetes マニフェスト（k8s/local/manifests/）        │
│                                                               │
│  idp-server.yaml                                              │
│    Service: labels: app=idp-server, ports: name=http          │
│                                                               │
│  servicemonitor.yaml                                          │
│    selector: app=idp-server, port: http,                      │
│    path: /actuator/prometheus                                 │
│                                                               │
│  grafana-dashboard-idp-server.yaml                            │
│    8 パネル: HTTP req/s, レイテンシ, エラー率,                  │
│    JVM ヒープ, スレッド, CPU, GC, Executor                     │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│ Step 3: 監視基盤（up-app-only.sh で自動インストール）           │
│                                                               │
│  helm install prometheus prometheus-community/                │
│    kube-prometheus-stack --namespace monitoring                │
└──────────────────────────────────────────────────────────────┘
```

---

## まとめ

| トピック | ポイント |
|---------|---------|
| Prometheus とは | サービスの状態を数値で収集・保存・クエリ・可視化・通知するシステム |
| Pull モデル | Prometheus がターゲットからメトリクスを取りに行く。`up` メトリクスで死活監視が自動化 |
| K8s 統合 | Prometheus Operator が ServiceMonitor CRD を scrape_configs に自動変換。再起動不要 |
| kube-prometheus-stack | Prometheus + Grafana + Alertmanager を Helm で一括デプロイ |

より深い内容（TSDB 内部構造、PromQL、スケーリング、よくある落とし穴）は [詳細編](./prometheus-kubernetes-deep-dive.md) を参照してください。

---

## 参考リソース

- [Prometheus 公式ドキュメント](https://prometheus.io/docs/)
- [Prometheus Operator Design](https://prometheus-operator.dev/docs/getting-started/design/)
- [kube-prometheus-stack Helm Chart](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack)

---

**最終更新**: 2026-03-08
**対象**: SaaS アプリケーション開発者、SRE、プラットフォームエンジニア
