# Kubernetes 可観測性

ログ、メトリクス、トレースを使用したKubernetesアプリケーションの監視と可観測性を学びます。

---

## 目次

1. [可観測性の3本柱](#可観測性の3本柱)
2. [ログ管理](#ログ管理)
3. [メトリクス](#メトリクス)
4. [分散トレーシング](#分散トレーシング)
5. [アラート](#アラート)
6. [IDサービスでの構成例](#idサービスでの構成例)

---

## 可観測性の3本柱

```
┌─────────────────────────────────────────────────────────────┐
│                    可観測性の3本柱                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Logs（ログ）                                                │
│  └── 何が起きたか                                            │
│  └── イベント、エラー、デバッグ情報                           │
│  └── ツール: Fluentd, Loki, CloudWatch Logs                 │
│                                                              │
│  Metrics（メトリクス）                                        │
│  └── どれくらいか                                            │
│  └── CPU、メモリ、リクエスト数、レイテンシ                    │
│  └── ツール: Prometheus, Grafana, CloudWatch                │
│                                                              │
│  Traces（トレース）                                          │
│  └── どこで起きたか                                          │
│  └── リクエストの流れ、サービス間の依存関係                   │
│  └── ツール: Jaeger, Zipkin, X-Ray                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## ログ管理

### kubectlでのログ確認

```bash
# Podのログ
kubectl logs pod-name

# 特定コンテナのログ
kubectl logs pod-name -c container-name

# リアルタイムフォロー
kubectl logs -f pod-name

# 過去N行
kubectl logs --tail=100 pod-name

# 時間範囲
kubectl logs --since=1h pod-name
kubectl logs --since-time=2024-01-01T00:00:00Z pod-name

# 前のコンテナのログ（再起動後）
kubectl logs pod-name --previous

# ラベルセレクターで複数Pod
kubectl logs -l app=idp-server --all-containers
```

### Fluentd/Fluent Bit

```yaml
# Fluent Bit DaemonSet
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluent-bit
  namespace: logging
spec:
  selector:
    matchLabels:
      app: fluent-bit
  template:
    metadata:
      labels:
        app: fluent-bit
    spec:
      serviceAccountName: fluent-bit
      containers:
        - name: fluent-bit
          image: fluent/fluent-bit:2.2
          resources:
            limits:
              memory: 200Mi
            requests:
              cpu: 100m
              memory: 100Mi
          volumeMounts:
            - name: varlog
              mountPath: /var/log
            - name: varlibdockercontainers
              mountPath: /var/lib/docker/containers
              readOnly: true
            - name: config
              mountPath: /fluent-bit/etc/
      volumes:
        - name: varlog
          hostPath:
            path: /var/log
        - name: varlibdockercontainers
          hostPath:
            path: /var/lib/docker/containers
        - name: config
          configMap:
            name: fluent-bit-config

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: logging
data:
  fluent-bit.conf: |
    [SERVICE]
        Flush         1
        Log_Level     info
        Daemon        off
        Parsers_File  parsers.conf

    [INPUT]
        Name              tail
        Tag               kube.*
        Path              /var/log/containers/*.log
        Parser            docker
        DB                /var/log/flb_kube.db
        Mem_Buf_Limit     5MB
        Skip_Long_Lines   On
        Refresh_Interval  10

    [FILTER]
        Name                kubernetes
        Match               kube.*
        Kube_URL            https://kubernetes.default.svc:443
        Kube_CA_File        /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        Kube_Token_File     /var/run/secrets/kubernetes.io/serviceaccount/token
        Merge_Log           On
        K8S-Logging.Parser  On
        K8S-Logging.Exclude On

    [OUTPUT]
        Name            es
        Match           *
        Host            elasticsearch
        Port            9200
        Index           kubernetes
        Type            _doc

  parsers.conf: |
    [PARSER]
        Name   docker
        Format json
        Time_Key time
        Time_Format %Y-%m-%dT%H:%M:%S.%L
```

### 構造化ログの推奨

```json
// アプリケーションログ（JSON形式）
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "service": "idp-server",
  "traceId": "abc123",
  "spanId": "def456",
  "message": "Token issued",
  "clientId": "my-app",
  "userId": "user123",
  "responseTime": 45
}
```

---

## メトリクス

### Metrics Server

```bash
# Metrics Serverのインストール
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# リソース使用状況の確認
kubectl top nodes
kubectl top pods
kubectl top pods -l app=idp-server --containers
```

### Prometheus Stack

```yaml
# Prometheus Operator（helm）
# helm install prometheus prometheus-community/kube-prometheus-stack

# ServiceMonitor
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: idp-server
  namespace: monitoring
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: idp-server
  namespaceSelector:
    matchNames:
      - production
  endpoints:
    - port: management
      path: /actuator/prometheus
      interval: 30s
```

### アプリケーションメトリクス

```yaml
# Spring Boot Actuatorの設定
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: idp-server
    export:
      prometheus:
        enabled: true
```

### Grafanaダッシュボード

```
┌─────────────────────────────────────────────────────────────┐
│                   IDサービス ダッシュボード                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ Request Rate    │  │ Error Rate      │                  │
│  │    1,234 req/s  │  │    0.5%         │                  │
│  └─────────────────┘  └─────────────────┘                  │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ P95 Latency     │  │ Pod Count       │                  │
│  │    45ms         │  │    5/10         │                  │
│  └─────────────────┘  └─────────────────┘                  │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │               Token発行数 (時系列)                    │   │
│  │  ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄              │   │
│  │  ████████████████████████████████████████           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 重要なメトリクス

| カテゴリ | メトリクス | 説明 |
|---------|----------|------|
| RED | Request Rate | リクエスト数/秒 |
| RED | Error Rate | エラー率 |
| RED | Duration | レスポンスタイム |
| USE | Utilization | CPU/メモリ使用率 |
| USE | Saturation | キュー長 |
| USE | Errors | システムエラー |

---

## 分散トレーシング

### OpenTelemetry

```yaml
# OpenTelemetry Collector
apiVersion: opentelemetry.io/v1alpha1
kind: OpenTelemetryCollector
metadata:
  name: otel-collector
spec:
  mode: deployment
  config: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318

    processors:
      batch:
        timeout: 1s
        send_batch_size: 1024

    exporters:
      jaeger:
        endpoint: jaeger-collector:14250
        tls:
          insecure: true

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [batch]
          exporters: [jaeger]
```

### Spring Bootでの設定

```yaml
# application.yml
spring:
  application:
    name: idp-server

management:
  tracing:
    sampling:
      probability: 1.0  # 100%サンプリング（本番では調整）

# 依存関係（build.gradle）
# implementation 'io.micrometer:micrometer-tracing-bridge-otel'
# implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

### トレースの可視化

```
┌─────────────────────────────────────────────────────────────┐
│                    リクエストトレース                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  [Gateway]                                                   │
│  ├─ 5ms  HTTP GET /authorize                                │
│  │                                                           │
│  [idp-server]                                               │
│  ├─ 2ms  Validate client                                    │
│  ├─ 3ms  Check session                                      │
│  │  └─ [Redis] 1ms GET session:abc123                       │
│  ├─ 5ms  Authenticate user                                  │
│  │  └─ [PostgreSQL] 3ms SELECT user                        │
│  ├─ 2ms  Generate tokens                                    │
│  └─ 1ms  Store authorization                                │
│     └─ [PostgreSQL] 1ms INSERT authorization               │
│                                                              │
│  Total: 18ms                                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## アラート

### PrometheusRule

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: idp-server-alerts
  namespace: monitoring
spec:
  groups:
    - name: idp-server
      rules:
        # 高エラー率
        - alert: HighErrorRate
          expr: |
            sum(rate(http_server_requests_seconds_count{status=~"5..",app="idp-server"}[5m]))
            /
            sum(rate(http_server_requests_seconds_count{app="idp-server"}[5m]))
            > 0.05
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "High error rate on IDP Server"
            description: "Error rate is {{ $value | humanizePercentage }}"

        # 高レイテンシ
        - alert: HighLatency
          expr: |
            histogram_quantile(0.95,
              sum(rate(http_server_requests_seconds_bucket{app="idp-server"}[5m])) by (le)
            ) > 1
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "High latency on IDP Server"
            description: "P95 latency is {{ $value | humanizeDuration }}"

        # Podが不足
        - alert: InsufficientPods
          expr: |
            kube_deployment_status_replicas_available{deployment="idp-server"}
            < kube_deployment_spec_replicas{deployment="idp-server"}
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "IDP Server has insufficient pods"

        # Pod再起動
        - alert: PodRestartLoop
          expr: |
            increase(kube_pod_container_status_restarts_total{container="idp-server"}[1h]) > 3
          labels:
            severity: warning
          annotations:
            summary: "Pod is restarting frequently"
```

### Alertmanager設定

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: alertmanager-config
  namespace: monitoring
stringData:
  alertmanager.yaml: |
    global:
      resolve_timeout: 5m
      slack_api_url: 'https://hooks.slack.com/services/xxx'

    route:
      group_by: ['alertname', 'severity']
      group_wait: 30s
      group_interval: 5m
      repeat_interval: 12h
      receiver: 'slack-notifications'
      routes:
        - match:
            severity: critical
          receiver: 'pagerduty'
        - match:
            severity: warning
          receiver: 'slack-notifications'

    receivers:
      - name: 'slack-notifications'
        slack_configs:
          - channel: '#alerts'
            send_resolved: true
            title: '{{ .Status | toUpper }}: {{ .CommonLabels.alertname }}'
            text: '{{ .CommonAnnotations.description }}'

      - name: 'pagerduty'
        pagerduty_configs:
          - service_key: 'xxx'
            send_resolved: true
```

---

## IDサービスでの構成例

### 完全な監視構成

```yaml
# deployment.yaml（監視設定込み）
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
  namespace: production
spec:
  template:
    metadata:
      labels:
        app: idp-server
      annotations:
        # Prometheusアノテーション
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: idp-server
          image: ghcr.io/myorg/idp-server:v1.0.0
          ports:
            - name: http
              containerPort: 8080
            - name: management
              containerPort: 9090
          env:
            - name: OTEL_EXPORTER_OTLP_ENDPOINT
              value: "http://otel-collector:4317"
            - name: OTEL_SERVICE_NAME
              value: "idp-server"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: management
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: management

---
# ServiceMonitor
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: idp-server
  namespace: monitoring
spec:
  selector:
    matchLabels:
      app: idp-server
  namespaceSelector:
    matchNames:
      - production
  endpoints:
    - port: management
      path: /actuator/prometheus
      interval: 15s
```

### 推奨構成

```
┌─────────────────────────────────────────────────────────────┐
│                     監視アーキテクチャ                       │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    アプリケーション                   │   │
│  │  idp-server (Spring Boot + Actuator + Micrometer)    │   │
│  └────────────────────────┬────────────────────────────┘   │
│                           │                                  │
│           ┌───────────────┼───────────────┐                 │
│           ▼               ▼               ▼                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│  │  Fluent Bit  │ │  Prometheus  │ │ OTel Collector│        │
│  │   (Logs)     │ │  (Metrics)   │ │  (Traces)    │        │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘        │
│         │                │                │                  │
│         ▼                ▼                ▼                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│  │Elasticsearch │ │   Grafana    │ │    Jaeger    │        │
│  │   / Loki     │ │              │ │              │        │
│  └──────────────┘ └──────────────┘ └──────────────┘        │
│                           │                                  │
│                           ▼                                  │
│                    ┌──────────────┐                         │
│                    │ Alertmanager │                         │
│                    └──────┬───────┘                         │
│                           │                                  │
│                    Slack / PagerDuty                        │
└─────────────────────────────────────────────────────────────┘
```

---

## まとめ

### チェックリスト

- [ ] 構造化ログを出力
- [ ] メトリクスエンドポイントを公開
- [ ] トレースIDをログに含める
- [ ] 重要なSLIを定義
- [ ] アラートルールを設定
- [ ] ダッシュボードを作成

### 次のステップ

- [Kubernetesセキュリティ](kubernetes-security.md) - RBAC、PodSecurity
- [kubectlコマンドリファレンス](kubectl-commands.md) - よく使うコマンド集

---

## 参考リソース

- [Kubernetes Monitoring](https://kubernetes.io/docs/tasks/debug/)
- [Prometheus Operator](https://prometheus-operator.dev/)
- [Grafana Loki](https://grafana.com/oss/loki/)
- [OpenTelemetry](https://opentelemetry.io/)
- [Jaeger](https://www.jaegertracing.io/)
