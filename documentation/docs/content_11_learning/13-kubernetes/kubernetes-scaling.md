# Kubernetes スケーリング

Horizontal Pod Autoscaler（HPA）、Vertical Pod Autoscaler（VPA）、Cluster Autoscalerを使用した自動スケーリングを学びます。

---

## 目次

1. [スケーリングの種類](#スケーリングの種類)
2. [手動スケーリング](#手動スケーリング)
3. [Horizontal Pod Autoscaler](#horizontal-pod-autoscaler)
4. [Vertical Pod Autoscaler](#vertical-pod-autoscaler)
5. [Cluster Autoscaler](#cluster-autoscaler)
6. [KEDA](#keda)
7. [IDサービスでの構成例](#idサービスでの構成例)

---

## スケーリングの種類

### 水平 vs 垂直スケーリング

```
┌─────────────────────────────────────────────────────────────┐
│                   スケーリングの種類                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  水平スケーリング（Horizontal Scaling）                       │
│  └── Pod数を増減                                             │
│  └── HPA（Horizontal Pod Autoscaler）                        │
│                                                              │
│  Before:  [Pod] [Pod]                                        │
│  After:   [Pod] [Pod] [Pod] [Pod]                            │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  垂直スケーリング（Vertical Scaling）                         │
│  └── Pod単位のリソース（CPU/メモリ）を増減                    │
│  └── VPA（Vertical Pod Autoscaler）                          │
│                                                              │
│  Before:  [Pod 256Mi]                                        │
│  After:   [Pod 512Mi]                                        │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  クラスタースケーリング（Cluster Scaling）                    │
│  └── ノード数を増減                                          │
│  └── Cluster Autoscaler                                      │
│                                                              │
│  Before:  [Node1] [Node2]                                    │
│  After:   [Node1] [Node2] [Node3]                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 手動スケーリング

### Deploymentのスケーリング

```bash
# レプリカ数の変更
kubectl scale deployment idp-server --replicas=5

# 現在の状態確認
kubectl get deployment idp-server

# スケーリング状況の監視
kubectl rollout status deployment/idp-server

# Podの状態確認
kubectl get pods -l app=idp-server
```

### マニフェストでの指定

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
spec:
  replicas: 3  # 固定レプリカ数
  selector:
    matchLabels:
      app: idp-server
  template:
    spec:
      containers:
        - name: idp-server
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
```

---

## Horizontal Pod Autoscaler

### 基本概念

```
┌─────────────────────────────────────────────────────────────┐
│                  Horizontal Pod Autoscaler                   │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Metrics Server                      │   │
│  │              (CPU/メモリ使用率の収集)                  │   │
│  └────────────────────────┬────────────────────────────┘   │
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                        HPA                           │   │
│  │   Target: 50% CPU                                    │   │
│  │   Min: 2, Max: 10                                    │   │
│  └────────────────────────┬────────────────────────────┘   │
│                           │                                  │
│           ┌───────────────┼───────────────┐                 │
│           ▼               ▼               ▼                 │
│      ┌─────────┐     ┌─────────┐     ┌─────────┐          │
│      │   Pod   │     │   Pod   │     │   Pod   │          │
│      │  CPU:30% │     │  CPU:40% │     │  CPU:35% │          │
│      └─────────┘     └─────────┘     └─────────┘          │
│                                                              │
│      平均: 35% < 50% → スケールダウン可能                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### HPA v2の定義

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: idp-server-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: idp-server

  # レプリカ数の範囲
  minReplicas: 2
  maxReplicas: 10

  # スケーリングメトリクス
  metrics:
    # CPU使用率ベース
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70

    # メモリ使用率ベース
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80

  # スケーリング動作の制御
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # 5分間安定してから
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60  # 1分毎に最大10%削減
        - type: Pods
          value: 1
          periodSeconds: 60  # または1分毎に1Pod削減
      selectPolicy: Min  # 最小の削減を選択

    scaleUp:
      stabilizationWindowSeconds: 0  # 即座にスケールアップ
      policies:
        - type: Percent
          value: 100
          periodSeconds: 15  # 15秒毎に最大100%増加
        - type: Pods
          value: 4
          periodSeconds: 15  # または15秒毎に4Pod増加
      selectPolicy: Max  # 最大の増加を選択
```

### カスタムメトリクスを使用したHPA

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: idp-server-hpa-custom
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: idp-server
  minReplicas: 2
  maxReplicas: 20
  metrics:
    # Prometheusカスタムメトリクス
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "1000"

    # 外部メトリクス（SQSキュー長など）
    - type: External
      external:
        metric:
          name: sqs_messages_visible
          selector:
            matchLabels:
              queue: idp-events
        target:
          type: AverageValue
          averageValue: "30"
```

### HPAの確認

```bash
# HPA一覧
kubectl get hpa

# HPA詳細
kubectl describe hpa idp-server-hpa

# 出力例
# NAME             REFERENCE               TARGETS   MINPODS   MAXPODS   REPLICAS
# idp-server-hpa   Deployment/idp-server   45%/70%   2         10        3

# メトリクスの確認
kubectl top pods -l app=idp-server
```

---

## Vertical Pod Autoscaler

### VPAの動作

```
┌─────────────────────────────────────────────────────────────┐
│                  Vertical Pod Autoscaler                     │
│                                                              │
│  コンポーネント:                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Recommender │  │   Updater   │  │  Admission  │         │
│  │             │  │             │  │ Controller  │         │
│  │ 推奨値計算   │  │ Pod再作成   │  │ 新Pod設定   │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                  │
│         ▼                ▼                ▼                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                      VPA                             │   │
│  │   推奨: CPU 500m, Memory 1Gi                         │   │
│  └──────────────────────┬──────────────────────────────┘   │
│                         │                                    │
│                         ▼                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                     Pod                              │   │
│  │   現在: CPU 250m, Memory 512Mi                       │   │
│  │   → 再作成後: CPU 500m, Memory 1Gi                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### VPA定義

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: idp-server-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: idp-server

  # 更新モード
  updatePolicy:
    updateMode: "Auto"  # Off, Initial, Recreate, Auto

  # リソースポリシー
  resourcePolicy:
    containerPolicies:
      - containerName: idp-server
        minAllowed:
          cpu: 100m
          memory: 256Mi
        maxAllowed:
          cpu: 2
          memory: 4Gi
        controlledResources: ["cpu", "memory"]
        controlledValues: RequestsAndLimits
```

### 更新モード

| モード | 説明 |
|--------|------|
| Off | 推奨値の計算のみ（適用しない） |
| Initial | 新規Pod作成時のみ適用 |
| Recreate | Podを再作成して適用 |
| Auto | 自動で最適な方法を選択 |

### VPAの確認

```bash
# VPA一覧
kubectl get vpa

# 推奨値の確認
kubectl describe vpa idp-server-vpa

# 出力例
# Recommendation:
#   Container Recommendations:
#     Container Name:  idp-server
#     Lower Bound:
#       Cpu:     250m
#       Memory:  512Mi
#     Target:
#       Cpu:     500m
#       Memory:  1Gi
#     Upper Bound:
#       Cpu:     1
#       Memory:  2Gi
```

### HPAとVPAの併用

```yaml
# HPAとVPAを併用する場合の注意点
# - VPAはCPUを制御しない設定にする
# - メモリのみVPAで制御

apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: idp-server-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: idp-server
  updatePolicy:
    updateMode: "Auto"
  resourcePolicy:
    containerPolicies:
      - containerName: idp-server
        # CPUはHPAで制御するため除外
        controlledResources: ["memory"]
```

---

## Cluster Autoscaler

### 動作概念

```
┌─────────────────────────────────────────────────────────────┐
│                   Cluster Autoscaler                         │
│                                                              │
│  スケールアップ:                                              │
│  Pod作成 → リソース不足でPending → ノード追加                 │
│                                                              │
│  ┌──────┐  Pending   ┌─────────────────┐                   │
│  │ Pod  │ ─────────► │ Cluster         │ ──► [New Node]    │
│  └──────┘            │ Autoscaler      │                    │
│                      └─────────────────┘                    │
│                                                              │
│  スケールダウン:                                              │
│  低使用率ノード → Pod移動 → ノード削除                        │
│                                                              │
│  [Node 20%] ──► 条件:                                       │
│  使用率、未スケジュールPodなし、PDB考慮 → ノード削除           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### AWS EKS での設定

```yaml
# Cluster Autoscaler Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cluster-autoscaler
  namespace: kube-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cluster-autoscaler
  template:
    metadata:
      labels:
        app: cluster-autoscaler
    spec:
      serviceAccountName: cluster-autoscaler
      containers:
        - name: cluster-autoscaler
          image: registry.k8s.io/autoscaling/cluster-autoscaler:v1.28.0
          command:
            - ./cluster-autoscaler
            - --v=4
            - --stderrthreshold=info
            - --cloud-provider=aws
            - --skip-nodes-with-local-storage=false
            - --expander=least-waste
            - --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled,k8s.io/cluster-autoscaler/my-cluster
            - --balance-similar-node-groups
            - --scale-down-delay-after-add=10m
            - --scale-down-unneeded-time=10m
          resources:
            limits:
              cpu: 100m
              memory: 600Mi
            requests:
              cpu: 100m
              memory: 600Mi
```

### Karpenter（AWS推奨）

```yaml
# Karpenter NodePool
apiVersion: karpenter.sh/v1beta1
kind: NodePool
metadata:
  name: default
spec:
  template:
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64"]
        - key: kubernetes.io/os
          operator: In
          values: ["linux"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot", "on-demand"]
        - key: node.kubernetes.io/instance-type
          operator: In
          values: ["m5.large", "m5.xlarge", "m5.2xlarge"]

      nodeClassRef:
        name: default

  limits:
    cpu: 1000
    memory: 1000Gi

  disruption:
    consolidationPolicy: WhenUnderutilized
    consolidateAfter: 30s
```

---

## KEDA

### KEDAの概要

```
┌─────────────────────────────────────────────────────────────┐
│                          KEDA                                │
│            (Kubernetes Event-driven Autoscaling)             │
│                                                              │
│  特徴:                                                       │
│  - イベント駆動のスケーリング                                 │
│  - 0からNまでスケール可能                                     │
│  - 多様なスケーラー（AWS SQS, Kafka, Prometheus等）           │
│                                                              │
│  ┌────────────────┐     ┌────────────────┐                 │
│  │  Event Source  │     │     KEDA       │                 │
│  │  (SQS, Kafka)  │────►│    Operator    │                 │
│  └────────────────┘     └───────┬────────┘                 │
│                                 │                            │
│                                 ▼                            │
│                         ┌────────────────┐                  │
│                         │   Deployment   │                  │
│                         │  replicas: 0→N │                  │
│                         └────────────────┘                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### KEDA ScaledObject

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: idp-event-processor
spec:
  scaleTargetRef:
    name: event-processor
  pollingInterval: 30
  cooldownPeriod: 300
  minReplicaCount: 0   # 0までスケールダウン可能
  maxReplicaCount: 100
  triggers:
    # AWS SQSキュー
    - type: aws-sqs-queue
      metadata:
        queueURL: https://sqs.ap-northeast-1.amazonaws.com/123456789/idp-events
        queueLength: "5"
        awsRegion: ap-northeast-1
      authenticationRef:
        name: keda-aws-credentials

    # Prometheus メトリクス
    - type: prometheus
      metadata:
        serverAddress: http://prometheus:9090
        metricName: http_requests_total
        threshold: "100"
        query: |
          sum(rate(http_requests_total{app="idp-server"}[2m]))
```

---

## IDサービスでの構成例

### 完全なスケーリング構成

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
  namespace: production
spec:
  replicas: 3
  selector:
    matchLabels:
      app: idp-server
  template:
    metadata:
      labels:
        app: idp-server
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: idp-server
                topologyKey: topology.kubernetes.io/zone
      containers:
        - name: idp-server
          image: ghcr.io/myorg/idp-server:v1.0.0
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"

---
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: idp-server-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: idp-server
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 15

---
# pdb.yaml (Pod Disruption Budget)
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: idp-server-pdb
  namespace: production
spec:
  minAvailable: 2  # 最低2Podは維持
  selector:
    matchLabels:
      app: idp-server
```

---

## まとめ

### スケーリング手法の選択

| 手法 | ユースケース | 特徴 |
|------|------------|------|
| HPA | Webアプリ、API | 負荷に応じてPod数を増減 |
| VPA | バッチ処理 | Pod単位のリソースを最適化 |
| Cluster Autoscaler | 汎用 | ノード数を自動調整 |
| KEDA | イベント駆動 | キューベースのスケーリング |

### チェックリスト

- [ ] リソースのrequests/limitsを設定
- [ ] HPAのmin/maxを適切に設定
- [ ] スケールダウンの安定化時間を設定
- [ ] PodDisruptionBudgetを設定
- [ ] Podアンチアフィニティで分散配置

### 次のステップ

- [Kubernetes可観測性](kubernetes-observability.md) - ログ、メトリクス、トレース
- [Kubernetesセキュリティ](kubernetes-security.md) - RBAC、PodSecurity

---

## 参考リソース

- [Horizontal Pod Autoscaling](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [Vertical Pod Autoscaler](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler)
- [Cluster Autoscaler](https://github.com/kubernetes/autoscaler/tree/master/cluster-autoscaler)
- [KEDA](https://keda.sh/)
- [Karpenter](https://karpenter.sh/)
