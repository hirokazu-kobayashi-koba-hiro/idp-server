# EKS Auto Scaling

EKS（Elastic Kubernetes Service）環境では、Podレベルのスケーリング（HPA/VPA）とノードレベルのスケーリング（Karpenter/Cluster Autoscaler）を組み合わせて、アプリケーションの負荷変動に自動対応します。本ドキュメントでは、EKSにおけるAuto Scalingの仕組みと設計パターンを体系的に学びます。

---

## 所要時間
約60分

## 学べること
- EKS Auto Scalingの全体像（Pod層とNode層の2層構造）
- HPA（Horizontal Pod Autoscaler）の仕組みとメトリクス設計
- VPA（Vertical Pod Autoscaler）の仕組みと使い分け
- Karpenter によるノードレベルの自動スケーリング
- Cluster Autoscaler との違いと選定基準
- KEDA によるイベント駆動スケーリング
- アプリケーション特性別のスケーリング設計指針

## 前提知識
- Kubernetesの基礎（Pod、Deployment、Service）
- [コンテナサービス](./aws-container-services.md)の基本概念
- [EC2](./aws-ec2.md)の Auto Scaling の基礎概念

---

## 目次

### 基礎
1. [EKS Auto Scaling の全体像](#1-eks-auto-scaling-の全体像)
2. [Metrics Server](#2-metrics-server)

### Podレベルのスケーリング
3. [HPA（Horizontal Pod Autoscaler）](#3-hpahorizontal-pod-autoscaler)
4. [VPA（Vertical Pod Autoscaler）](#4-vpavertical-pod-autoscaler)
5. [HPA vs VPA の使い分け](#5-hpa-vs-vpa-の使い分け)

### ノードレベルのスケーリング
6. [Karpenter](#6-karpenter)
7. [Cluster Autoscaler](#7-cluster-autoscaler)
8. [Karpenter vs Cluster Autoscaler](#8-karpenter-vs-cluster-autoscaler)

### 応用
9. [KEDA（イベント駆動スケーリング）](#9-kedaイベント駆動スケーリング)
10. [スケーリングの連携パターン](#10-スケーリングの連携パターン)
11. [スケーリングシミュレーション](#11-スケーリングシミュレーション)
12. [アプリケーション特性別の設計指針](#12-アプリケーション特性別の設計指針)
13. [IDサービスでの活用](#13-idサービスでの活用)
14. [まとめ](#14-まとめ)

---

## 1. EKS Auto Scaling の全体像

EKSにおけるAuto Scalingは、**Pod層**と**Node層**の2層で構成されます。

```
┌─────────────────────────────────────────────────────────┐
│                   EKS Auto Scaling                      │
│                                                         │
│  ┌─── Pod層（アプリケーション）─────────────────────┐   │
│  │                                                  │   │
│  │   HPA: Pod数を水平にスケール（台数増減）          │   │
│  │   VPA: Podのリソースを垂直にスケール（サイズ変更） │   │
│  │   KEDA: イベント駆動でPod数をスケール              │   │
│  │                                                  │   │
│  └──────────────────────────────────────────────────┘   │
│                       │                                  │
│                       │ Podが増えてノードが足りない       │
│                       ▼                                  │
│  ┌─── Node層（インフラ）───────────────────────────┐   │
│  │                                                  │   │
│  │   Karpenter: 必要なノードを自動プロビジョニング    │   │
│  │   Cluster Autoscaler: ASGベースのノードスケーリング│   │
│  │                                                  │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 2層構造が必要な理由

| 層 | 役割 | 不足するとどうなるか |
|----|------|---------------------|
| Pod層 | アプリの処理能力を調整 | リクエストが捌けず、レイテンシ増加・タイムアウト |
| Node層 | Podを配置するノードを調整 | Pending Pod が発生し、新しいPodが起動できない |

Pod層だけでは「Pod数を増やしたいが、ノードの空きがない」状態に対応できません。Node層だけでは「ノードはあるが、Pod数が足りずリクエストが捌けない」状態に対応できません。両方を組み合わせることで、アプリケーション負荷に応じた完全自動スケーリングが実現します。

---

## 2. Metrics Server

Metrics Serverは、Kubernetesクラスタ内のPod/NodeのCPU・メモリ使用量を収集する軽量コンポーネントです。HPAやVPAのスケーリング判断に必要なメトリクスを提供します。

### アーキテクチャ

```
┌─ Node ──────────────┐    ┌─ Node ──────────────┐
│  ┌─ Pod ─┐ ┌─ Pod ─┐│    │  ┌─ Pod ─┐ ┌─ Pod ─┐│
│  └───────┘ └───────┘│    │  └───────┘ └───────┘│
│       kubelet        │    │       kubelet        │
│   (cAdvisor内蔵)     │    │   (cAdvisor内蔵)     │
└──────────┬───────────┘    └──────────┬───────────┘
           │                           │
           ▼                           ▼
      ┌─────────────────────────────────────┐
      │         Metrics Server               │
      │  kubeletから定期的にメトリクス収集     │
      │  → Metrics API として公開             │
      └────────────────┬────────────────────┘
                       │
              ┌────────┴────────┐
              ▼                 ▼
          ┌──────┐         ┌──────┐
          │ HPA  │         │ VPA  │
          └──────┘         └──────┘
```

### EKSへのインストール

```bash
# Metrics Server をデプロイ
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# 動作確認
kubectl top nodes
kubectl top pods -n my-namespace
```

### 確認コマンド

```bash
# Nodeのリソース使用量
$ kubectl top nodes
NAME                          CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
ip-10-0-1-100.ec2.internal   250m         12%    1200Mi          30%
ip-10-0-2-200.ec2.internal   180m         9%     980Mi           24%

# Podのリソース使用量
$ kubectl top pods -n my-namespace
NAME                       CPU(cores)   MEMORY(bytes)
my-app-7b8f9c6d4-abc12    120m         512Mi
my-app-7b8f9c6d4-def34    95m          480Mi
```

---

## 3. HPA（Horizontal Pod Autoscaler）

HPAは、メトリクス（CPU使用率、メモリ使用量、カスタムメトリクス）に基づいてPod数を自動調整するKubernetes標準機能です。

### 仕組み

```
                  ┌──────────────────────────────┐
                  │     HPA Controller            │
                  │                                │
                  │  1. メトリクス取得（15秒間隔）   │
                  │  2. 必要Pod数を計算             │
                  │  3. Deploymentのreplicas更新    │
                  └──────────┬───────────────────┘
                             │
              ┌──────────────┼──────────────────┐
              ▼              ▼                   ▼
         Metrics API    Custom Metrics      External Metrics
         (CPU/Memory)   (Prometheus等)      (SQS queue等)
```

### 計算式

```
必要Pod数 = ceil( 現在のPod数 × (現在のメトリクス値 / 目標メトリクス値) )

例: 現在4Pod, CPU使用率80%, 目標50%
  → ceil(4 × 80/50) = ceil(6.4) = 7 Pod
```

### マニフェスト例

#### 基本（CPU使用率ベース）

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: my-app
  namespace: my-namespace
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

#### 複数メトリクス（CPU + メモリ）

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: my-app
  namespace: my-namespace
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 70
```

複数メトリクスを指定した場合、**最も多くのPod数を要求するメトリクス**が採用されます。

#### スケーリング動作の制御

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: my-app
  namespace: my-namespace
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30    # 30秒の安定化期間
      policies:
        - type: Percent
          value: 100                     # 最大で現在の100%増
          periodSeconds: 60
        - type: Pods
          value: 4                       # 最大で4Pod追加
          periodSeconds: 60
      selectPolicy: Max                  # 最も多い方を選択
    scaleDown:
      stabilizationWindowSeconds: 300   # 5分の安定化期間
      policies:
        - type: Percent
          value: 10                      # 最大で現在の10%減
          periodSeconds: 60
```

### スケーリング動作のポイント

| パラメータ | 説明 | 推奨値 |
|-----------|------|--------|
| `stabilizationWindowSeconds`（scaleUp） | スケールアウト判断の安定化期間 | 0〜60秒（素早く対応） |
| `stabilizationWindowSeconds`（scaleDown） | スケールイン判断の安定化期間 | 300秒（フラッピング防止） |
| `minReplicas` | 最小Pod数 | 2以上（高可用性） |
| `maxReplicas` | 最大Pod数 | コスト上限を考慮 |
| 目標CPU使用率 | スケールアウトのトリガー | 50〜70%（バースト余裕） |

### HPAの注意点

- **requests の設定が必須**: HPAのCPU/メモリ使用率は `requests` に対する割合で計算される。`requests` 未設定ではHPAが動作しない
- **スケールダウンは慎重に**: デフォルトの安定化期間は5分。短すぎるとフラッピング（頻繁な増減）が発生する
- **VPAとの併用制限**: 同一メトリクス（CPU/メモリ）でHPAとVPAを併用すると競合する

### 確認コマンド

```bash
# HPA の状態確認
$ kubectl get hpa -n my-namespace
NAME     REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
my-app   Deployment/my-app    35%/50%   2         10        3          2d

# HPA の詳細確認
kubectl describe hpa my-app -n my-namespace
```

---

## 4. VPA（Vertical Pod Autoscaler）

VPAは、Podのリソースリクエスト（`requests`）とリミット（`limits`）を使用実績に基づいて自動調整するコンポーネントです。

### 仕組み

```
┌─────────────────────────────────────────────────┐
│              VPA Controller                      │
│                                                  │
│  ┌──────────────┐  ┌──────────────┐             │
│  │  Recommender  │  │   Updater    │             │
│  │              │  │              │             │
│  │ メトリクス履歴 │  │ Podを再作成   │             │
│  │ を分析して    │  │ して新しい    │             │
│  │ 推奨値を算出  │  │ requests適用  │             │
│  └──────┬───────┘  └──────┬───────┘             │
│         │                  │                     │
│         ▼                  ▼                     │
│  ┌──────────────┐  ┌──────────────┐             │
│  │ Admission     │  │  Pod再起動   │             │
│  │ Controller    │  │  (eviction)  │             │
│  │ 新Pod作成時に  │  │              │             │
│  │ 推奨値を注入   │  │              │             │
│  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────┘
```

### 3つのコンポーネント

| コンポーネント | 役割 |
|---------------|------|
| **Recommender** | メトリクス履歴を分析し、最適なCPU/メモリの推奨値を算出 |
| **Updater** | 推奨値から大きく外れたPodをevict（退避）して再作成をトリガー |
| **Admission Controller** | 新しいPod作成時に推奨値を `requests`/`limits` に注入 |

### EKSへのインストール

```bash
# VPA をデプロイ
git clone https://github.com/kubernetes/autoscaler.git
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh
```

### マニフェスト例

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: my-app
  namespace: my-namespace
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  updatePolicy:
    updateMode: "Auto"          # 自動でPodを再作成して適用
  resourcePolicy:
    containerPolicies:
      - containerName: my-app
        minAllowed:
          cpu: "250m"
          memory: "512Mi"
        maxAllowed:
          cpu: "4000m"
          memory: "8Gi"
        controlledResources: ["cpu", "memory"]
```

### updateMode の選択肢

| モード | 動作 | 用途 |
|--------|------|------|
| **Off** | 推奨値の表示のみ（Podに変更なし） | 初期評価・分析 |
| **Initial** | Pod作成時にのみ推奨値を適用 | 既存Podの再起動を避けたい場合 |
| **Auto** | 推奨値に基づいてPodを自動で再作成 | 完全自動化 |

### 推奨値の確認

```bash
# VPA の推奨値を確認
$ kubectl describe vpa my-app -n my-namespace

Status:
  Recommendation:
    Container Recommendations:
      Container Name: my-app
      Lower Bound:
        Cpu:     200m
        Memory:  400Mi
      Target:
        Cpu:     500m
        Memory:  1Gi
      Upper Bound:
        Cpu:     2000m
        Memory:  4Gi
      Uncapped Target:
        Cpu:     500m
        Memory:  1Gi
```

| 値 | 意味 |
|----|------|
| **Target** | 推奨値（これを `requests` に設定すべき） |
| **Lower Bound** | 最低限必要なリソース |
| **Upper Bound** | 過去の最大使用量に基づく上限 |
| **Uncapped Target** | `minAllowed`/`maxAllowed` の制約を無視した推奨値 |

### VPAの注意点

- **Podの再起動が発生する**: `Auto` モードではリソース変更のためPodがevictされる。Kubernetes 1.35以降では In-place Resource Resize（GA）によりPod再起動なしでリソース変更が可能
- **HPAとの併用制限**: CPU/メモリで同時にHPAとVPAを使うと競合する。VPAは `Off` モードで推奨値確認のみに使い、手動で `requests` を調整する運用が安全

---

## 5. HPA vs VPA の使い分け

| 観点 | HPA | VPA |
|------|-----|-----|
| スケール方向 | 水平（Pod数を増減） | 垂直（Podのサイズを増減） |
| 再起動 | なし（新Podを追加/削除） | あり（Podを再作成） |
| 対応速度 | 高速（数十秒〜） | 低速（Pod再作成が必要） |
| 適したワークロード | ステートレスなWebアプリ | バッチ処理、単一Podサービス |

### 言語・ランタイム別の影響

アプリケーションの言語やランタイムによって、スケーリング戦略の最適解は異なります。

| 特性 | Java（Spring Boot等） | Go | Node.js |
|------|----------------------|-----|---------|
| **起動時間** | 遅い（5〜30秒、JVM + DI初期化） | 速い（ミリ秒〜数秒） | 速い（数秒） |
| **メモリ特性** | JVMヒープで固定消費が大きい（256MB〜1GB+） | 低い（数十MB〜） | 中程度（50〜200MB） |
| **CPU特性** | マルチスレッドで1Pod内の並列処理が得意 | goroutineでCPUコアを効率的に使う | シングルスレッド、I/O待ちが多い処理に強い |
| **HPAとの相性** | 良い（ただし起動が遅いため事前スケールが有効） | 非常に良い（高速起動で即座に応答） | 良い（CPU利用率よりリクエスト数ベースが有効） |
| **VPAとの相性** | 注意（JVMヒープ設定の再調整が必要） | 良い（メモリ使用量が素直に変動） | 良い（V8ヒープは自動調整） |

### HPA設計への影響

```
┌──── Java ──────────────────────────────────────────┐
│                                                     │
│ 起動が遅い → 事前スケーリングが重要                  │
│ メモリが大きい → 1Podのサイズが大きくなる             │
│ マルチスレッド → 1Podで多くのリクエストを処理可能     │
│                                                     │
│ → minReplicas を多めに設定（急な負荷に備える）       │
│ → maxReplicas は控えめ（Podが大きいのでコスト注意）  │
│ → 目標CPU使用率を低め（50%）に設定                   │
│   （バースト時の余裕を確保）                         │
└─────────────────────────────────────────────────────┘

┌──── Go ────────────────────────────────────────────┐
│                                                     │
│ 起動が速い → リアクティブスケーリングで十分           │
│ メモリが小さい → 小さいPodを多数並べられる            │
│ CPU効率が良い → CPU使用率ベースのHPAが素直に効く      │
│                                                     │
│ → minReplicas は最低限（2）でOK                      │
│ → maxReplicas を多めに設定可能（コスト効率が良い）   │
│ → 目標CPU使用率 60〜70% で効率重視                   │
└─────────────────────────────────────────────────────┘

┌──── Node.js ───────────────────────────────────────┐
│                                                     │
│ シングルスレッド → CPU 1コアあたり1プロセス           │
│ I/O多重化が得意 → I/Oバウンドに強い                  │
│ イベントループがブロックされると全体が止まる           │
│                                                     │
│ → CPU requests を 1000m（1コア）に設定               │
│ → CPUバウンド処理がある場合は小さいPodを多数並べる    │
│ → I/Oバウンド中心なら接続数ベースのカスタムメトリクス │
│   が有効                                             │
└─────────────────────────────────────────────────────┘
```

### ワークロード別の推奨パターン

```
┌──────────────────────────────────────────────────────┐
│ ステートレス Web アプリ（API サーバー等）              │
│                                                      │
│  → HPA（メイン）+ VPA Off（推奨値の参考にのみ使用）   │
│                                                      │
│  理由: Pod数の増減が高速で、再起動不要                 │
│       水平スケールは言語を問わず有効                   │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ バッチ処理・データ処理（スケールアウトしにくい処理）    │
│                                                      │
│  → VPA Auto（Podサイズを自動調整）                    │
│                                                      │
│  理由: 処理を分割できない場合、Podを大きくするしかない  │
└──────────────────────────────────────────────────────┘
```

---

## 6. Karpenter

Karpenterは、AWSが開発したKubernetesノードの自動プロビジョニングツールです。Pending状態のPodを検知し、最適なEC2インスタンスを直接起動します。

> Karpenter の概念・NodePool設計パターン・KWOK ローカル検証については [Karpenter ノードオートスケーリング](../../13-docker-kubernetes/karpenter-node-scaling.md) も参照。

### 仕組み

```
┌──────────────────────────────────────────────────────────┐
│                     Karpenter                             │
│                                                           │
│  1. Pending Pod を検知                                    │
│  2. Podの要求リソース（CPU/Memory/GPU等）を集約           │
│  3. 最適なインスタンスタイプを選択                         │
│  4. EC2 Fleet API で直接インスタンスを起動                 │
│  5. ノードが不要になったら自動で削除（Consolidation）      │
│                                                           │
│  ┌────────────────────────────────────────────────┐      │
│  │             NodePool (旧 Provisioner)           │      │
│  │                                                │      │
│  │  - 許可するインスタンスファミリー                │      │
│  │  - AZ制約                                       │      │
│  │  - 容量タイプ（On-Demand / Spot）               │      │
│  │  - TTL（ノードの有効期限）                       │      │
│  │  - リソース上限                                  │      │
│  └────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────┘
```

### スケーリングの流れ

```
[HPA] Podを4→7に増やす
  │
  ▼
[Scheduler] 3つの新Podを配置しようとする
  │
  ├─ 既存ノードに空きあり → そこに配置（Karpenter関与なし）
  │
  └─ 既存ノードに空きなし → Pod が Pending 状態
       │
       ▼
  [Karpenter] Pending Podを検知
       │
       ▼
  NodePoolの制約を確認
  → 最適なインスタンスタイプを選択（例: m7g.xlarge）
       │
       ▼
  EC2 Fleet APIで起動（ASGを経由しない）
       │
       ▼
  ノード Ready → Pending Pod がスケジュールされる
```

### EKSへのインストール

```bash
# Helm でインストール
helm repo add karpenter https://charts.karpenter.sh
helm repo update

helm install karpenter karpenter/karpenter \
  --namespace kube-system \
  --set "settings.clusterName=my-cluster" \
  --set "settings.interruptionQueue=my-cluster" \
  --set controller.resources.requests.cpu=1 \
  --set controller.resources.requests.memory=1Gi
```

### NodePool の設定例

```yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: default
spec:
  template:
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64", "arm64"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand"]               # Spot も可
        - key: karpenter.k8s.aws/instance-category
          operator: In
          values: ["m", "c", "r"]             # 汎用/コンピュート/メモリ最適化
        - key: karpenter.k8s.aws/instance-generation
          operator: Gt
          values: ["5"]                        # 第6世代以降
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
  limits:
    cpu: "100"                                 # クラスタ全体のCPU上限
    memory: "400Gi"                            # クラスタ全体のメモリ上限
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 30s
---
apiVersion: karpenter.k8s.aws/v1
kind: EC2NodeClass
metadata:
  name: default
spec:
  amiSelectorTerms:
    - alias: al2023@latest                     # Amazon Linux 2023
  subnetSelectorTerms:
    - tags:
        karpenter.sh/discovery: "my-cluster"
  securityGroupSelectorTerms:
    - tags:
        karpenter.sh/discovery: "my-cluster"
  role: "KarpenterNodeRole-my-cluster"
```

### Consolidation（統合）

Karpenterは、使用率が低いノードを検知し、Podを他のノードに移動させてから不要ノードを削除します。

```
統合前:
  Node-1 (m7g.xlarge): Pod-A [200m CPU]  ← 使用率 5%
  Node-2 (m7g.xlarge): Pod-B [300m CPU]  ← 使用率 7%
  Node-3 (m7g.xlarge): Pod-C [500m CPU]  ← 使用率 12%

統合後:
  Node-3 (m7g.xlarge): Pod-A + Pod-B + Pod-C [1000m CPU] ← 使用率 25%
  Node-1: 削除
  Node-2: 削除
```

### Karpenter の利点

| 利点 | 説明 |
|------|------|
| **高速プロビジョニング** | ASGを経由せず、EC2 Fleet APIを直接呼び出すため高速（通常1〜2分） |
| **インスタンスタイプの自動選択** | Podの要求に最適なインスタンスタイプを自動選択 |
| **Consolidation** | 使用率の低いノードを自動統合してコスト削減 |
| **Spot対応** | On-DemandとSpotを混在可能。Spot中断時も自動復旧 |
| **ノードの自動更新** | `expireAfter` で古いノードを自動入れ替え |

---

## 7. Cluster Autoscaler

Cluster Autoscaler（CA）は、Kubernetes公式のノードスケーリングツールです。Auto Scaling Group（ASG）のDesired Capacityを調整してノードを増減します。

### 仕組み

```
[Pending Pod 検知]
       │
       ▼
[Cluster Autoscaler]
       │
       ▼
[ASGのDesired Capacityを +1]
       │
       ▼
[ASG が EC2 インスタンスを起動]
       │
       ▼
[ノード Ready → Pod スケジュール]
```

### マニフェスト例

```yaml
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
    spec:
      serviceAccountName: cluster-autoscaler
      containers:
        - name: cluster-autoscaler
          image: registry.k8s.io/autoscaling/cluster-autoscaler:v1.30.0
          command:
            - ./cluster-autoscaler
            - --v=4
            - --stderrthreshold=info
            - --cloud-provider=aws
            - --skip-nodes-with-local-storage=false
            - --expander=least-waste
            - --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled,k8s.io/cluster-autoscaler/my-cluster
            - --balance-similar-node-groups
            - --skip-nodes-with-system-pods=false
```

---

## 8. Karpenter vs Cluster Autoscaler

| 観点 | Karpenter | Cluster Autoscaler |
|------|-----------|-------------------|
| ノード起動方式 | EC2 Fleet API 直接 | ASG 経由 |
| 起動速度 | 高速（1〜2分） | 中程度（2〜5分） |
| インスタンスタイプ選択 | Podの要求に応じて自動選択 | ASGで事前定義 |
| Consolidation | 自動で不要ノードを統合 | 使用率の低いノードを削除のみ |
| 設定の柔軟性 | NodePoolで宣言的に定義 | ASG + Launch Template |
| Spot対応 | 混在可能、自動Fallback | ASG混在が必要 |
| 成熟度 | 2024年GA（v1.0.0）、急速に普及 | 長い実績がある |
| クラウド対応 | AWS中心（Azure/GCPプロバイダーはプレビュー段階） | マルチクラウド対応 |

### 選定基準

```
┌────────────────────────────────────────────────────┐
│ Karpenter を選ぶ場合                               │
│                                                    │
│ ✓ EKS を使っている（AWS環境）                       │
│ ✓ 高速なノードプロビジョニングが必要                 │
│ ✓ インスタンスタイプの自動最適化でコストを抑えたい    │
│ ✓ 新規構築で選択の自由がある                        │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│ Cluster Autoscaler を選ぶ場合                      │
│                                                    │
│ ✓ マルチクラウド対応が必要                          │
│ ✓ 既にASGベースの運用が確立している                  │
│ ✓ 固定のインスタンスタイプで運用したい               │
└────────────────────────────────────────────────────┘
```

---

## 9. KEDA（イベント駆動スケーリング）

KEDA（Kubernetes Event-Driven Autoscaling）は、外部イベントソース（SQS、Kafka、Prometheus等）のメトリクスに基づいてPod数をスケーリングします。

### 仕組み

```
┌─ 外部イベントソース ──┐
│                       │
│  SQS キュー深度       │
│  Kafka Consumer Lag   │
│  Prometheus メトリクス │
│  CloudWatch メトリクス │
│  Cron スケジュール     │
│                       │
└───────┬───────────────┘
        │
        ▼
┌───────────────────┐
│      KEDA          │
│                    │
│  ScaledObject で   │
│  スケーリング定義  │
│                    │
│  → HPA を自動生成  │
│  → 0→N / N→0 対応 │
└───────┬───────────┘
        │
        ▼
┌───────────────────┐
│   Deployment       │
│  replicas: 0 → N   │
└───────────────────┘
```

### HPAとの違い

| 観点 | HPA | KEDA |
|------|-----|------|
| メトリクスソース | Metrics API（CPU/メモリ） | 外部イベントソース |
| ゼロスケール | 不可（min=1以上） | 可能（0→N） |
| トリガー | リソース使用率 | キュー深度、リクエスト数等 |

### マニフェスト例（SQSキュー連動）

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: notification-worker
  namespace: my-namespace
spec:
  scaleTargetRef:
    name: notification-worker
  minReplicaCount: 0              # キューが空なら0Podに
  maxReplicaCount: 10
  pollingInterval: 15             # 15秒間隔でチェック
  cooldownPeriod: 60
  triggers:
    - type: aws-sqs-queue
      metadata:
        queueURL: https://sqs.ap-northeast-1.amazonaws.com/123456789012/my-notifications
        queueLength: "5"          # メッセージ5件あたり1Pod
        awsRegion: ap-northeast-1
      authenticationRef:
        name: keda-aws-credentials
```

### よくある活用例

| シナリオ | トリガー | スケール対象 |
|---------|---------|-------------|
| メール・プッシュ通知 | SQSキュー深度 | 通知ワーカーPod |
| 画像・動画変換 | S3イベント数 | メディア処理Pod |
| ログ集約・分析 | Kinesisシャード数 | ログ処理Pod |
| 定期バッチ処理 | Cronスケジュール | バッチ処理Pod |

---

## 10. スケーリングの連携パターン

### HPA + Karpenter の連携構成

```
┌──────────────────────────────────────────────────────────────┐
│                EKS Auto Scaling 構成                         │
│                                                              │
│  ┌─── アプリケーション Deployment ───────────────────────┐  │
│  │                                                        │  │
│  │  HPA: CPU 50%, min=2, max=10                           │  │
│  │  → リクエスト負荷に応じてPod数を自動調整               │  │
│  │                                                        │  │
│  │  VPA (Off モード): 推奨値を参考に requests を手動設定   │  │
│  │                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─── Karpenter ─────────────────────────────────────────┐  │
│  │                                                        │  │
│  │  NodePool:                                             │  │
│  │    インスタンスファミリー: m, c                         │  │
│  │    容量タイプ: on-demand                               │  │
│  │    CPU上限: 100                                        │  │
│  │    Consolidation: WhenEmptyOrUnderutilized             │  │
│  │                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  連携フロー:                                                 │
│    負荷増加 → HPA が Pod を増やす                            │
│            → ノード不足なら Karpenter がノードを追加          │
│    負荷減少 → HPA が Pod を減らす                            │
│            → 空きノードを Karpenter が Consolidation          │
└──────────────────────────────────────────────────────────────┘
```

### スケーリングのタイムライン（言語別）

ノードの新規プロビジョニングが必要なケースでの所要時間を比較します。

```
[共通] 0s〜18s: HPA メトリクス取得 → Pod Pending → Karpenter 検知
[共通] 18s〜90s: Karpenter が EC2 起動 → ノード Ready

─── ここからアプリ起動時間が言語で異なる ───

Go:      90s + 数秒（バイナリ起動）         → 約1.5分で処理開始
Node.js: 90s + 3〜5秒（プロセス起動）       → 約1.5分で処理開始
Java:    90s + 15〜60秒（JVM + DI初期化）    → 約2〜2.5分で処理開始
```

| フェーズ | 所要時間 | 言語依存 |
|---------|---------|---------|
| HPA 判断 | 15〜30秒 | なし |
| Karpenter ノード起動 | 60〜90秒 | なし |
| アプリ起動（Go） | 数秒 | 高速 |
| アプリ起動（Node.js） | 3〜5秒 | 高速 |
| アプリ起動（Java/Spring Boot） | 15〜60秒 | 遅い |

起動が遅いランタイムほど、事前スケーリングの重要性が高くなります。

### 事前スケーリングとの組み合わせ

予測可能な負荷パターン（朝のピーク等）には、HPAの反応を待たずに事前スケールする方法が有効です。特にJavaのように起動が遅い言語では効果が大きくなります。

```yaml
# CronJob で事前スケール
apiVersion: batch/v1
kind: CronJob
metadata:
  name: pre-scale-morning
  namespace: my-namespace
spec:
  schedule: "0 8 * * 1-5"        # 平日8:00
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: scale
              image: bitnami/kubectl:latest
              command:
                - kubectl
                - scale
                - deployment/my-app
                - --replicas=6
                - -n
                - my-namespace
          restartPolicy: OnFailure
```

---

## 11. スケーリングシミュレーション

HPA + Karpenter がどのように連携するか、リクエスト数・CPU使用率・Pod数の変化を時系列で追います。

### 前提条件

```
HPA設定:
  目標CPU使用率: 50%
  minReplicas: 2
  maxReplicas: 10
  scaleUp stabilization: 30秒
  scaleDown stabilization: 300秒（5分）

Pod設定:
  CPU requests: 1000m（1コア）
  1 Podの処理能力: 約 200 req/s（CPU 50%時）

初期状態:
  Pod数: 2
  リクエスト数: 100 req/s
  CPU使用率: 25%
```

### シナリオ1: 朝の緩やかな負荷増加

ログインが徐々に増える典型的な朝のパターンです。

```
時刻    リクエスト数     CPU使用率          Pod数
        (req/s)         (対requests比)

8:00    100             25%                2
         :               :                 :
         :               :          HPAは動かない（50%未満）
         :               :                 :
8:15    300             75% ■■■■■■■■       2
                         ↑
                    目標(50%)超過！
                    HPA計算: ceil(2 × 75/50) = 3

8:16    300             75%                2 → 3 (HPA: +1)
                                           ↑
                                      ノードに空きあり
                                      → 即座にスケジュール

8:17    350             58% ■■■■■■         3
                         ↑
                    まだ50%超過
                    HPA計算: ceil(3 × 58/50) = 4

8:18    350             58%                3 → 4 (HPA: +1)

8:20    400             50% ■■■■■          4
                         ↑
                    目標に到達 → HPAは安定
                         :
8:30    400             50%                4 ← 安定状態
```

**ポイント**: 緩やかな増加では、HPAが段階的にPodを追加し、ノードに空きがあればKarpenterの出番なし。

### シナリオ2: 突発的なスパイク（ノード追加あり）

キャンペーン開始やTV放映後の急激なアクセス増加です。

```
時刻    リクエスト数     CPU使用率          Pod数     ノード数

10:00   400             50%                4         2
         :               :                 :          :
10:01   1200            150% ■■■■■■■■■■■   4         2
                          ↑
                     大幅超過！
                     HPA計算: ceil(4 × 150/50) = 12
                     → maxReplicas=10 で制限

10:01   1200            150%               4 → 10    2
                                            ↑
                                       +6 Pod要求
                                       ノード空き: 2Pod分のみ
                                       → 4Pod が Pending!

10:01   ─── Karpenter が Pending Pod を検知 ──────────────

10:02   1200            (一部Pod Pending)  6稼働     2
                                           4待機

        ─── Karpenter: 最適ノード選択 ────────────────────
        │ Pending 4Pod × CPU 1000m = 4000m 必要           │
        │ → m7g.xlarge (4 vCPU) を 1台起動                │
        └─────────────────────────────────────────────────┘

10:03   1200            (ノード起動中)      6稼働     2 → 3
                                           4待機      (起動中)

        ─── 言語によってここの時間が変わる ───────────────
        │ Go:      ノードReady後 数秒で Pod Ready          │
        │ Node.js: ノードReady後 3〜5秒で Pod Ready        │
        │ Java:    ノードReady後 15〜60秒で Pod Ready      │
        │          さらにウォームアップに数分かかる         │
        └─────────────────────────────────────────────────┘

10:04   1200            60% ■■■■■■         10稼働    3
(Go)                     ↑
                    10Podで分散 → 目標近くまで低下
                    → すぐにフルパフォーマンスで処理

10:05   1200            80% ■■■■■■■■       10稼働    3
(Java)                   ↑
                    全Pod Ready → だがウォームアップ中
                    新Podは既存Podの2〜3倍遅い
                    → CPU使用率が想定より高い（後述）
```

**ポイント**: 急なスパイクでは、HPAが一気にPodを増やそうとし、ノード不足でKarpenterが介入する。言語の起動時間が「スパイクの間に応答できるか」を左右する。さらにJavaは「Pod Readyだが本番パフォーマンスではない」ウォームアップ問題がある（シナリオ5で詳述）。

### シナリオ3: 負荷減少 → スケールダウン → Consolidation

夜間にトラフィックが減少するパターンです。

```
時刻    リクエスト数     CPU使用率          Pod数     ノード数

22:00   800             40%                10        3
         :               :                 :          :
22:05   400             20% ■■             10        3
                         ↑
                    50%を大きく下回る
                    HPA計算: ceil(10 × 20/50) = 4
                    ただし stabilization 300秒
                    → まだ待機

22:10   400             20%                10        3
                         ↑
                    5分経過 → スケールダウン開始
                    ただし scaleDown policy: 10%/60s
                    → 1回で最大1Pod削除

22:11   400             22%                10 → 9    3
22:12   400             25%                9 → 8     3
22:13   400             28%                8 → 7     3
22:14   400             33%                7 → 6     3
22:15   400             40%                6 → 5     3
22:16   400             50%                5 → 4     3
                         ↑
                    目標に到達 → スケールダウン停止

         :               :                 :          :
                    ─── Karpenter Consolidation ──────
                    │ Node-3: Pod 0台（全Podが他に移動）   │
                    │ → 空ノード → 30秒後に削除           │
                    └──────────────────────────────────┘

22:17   400             50%                4         3 → 2
                                                      ↑
                                                 空ノード削除
```

**ポイント**: スケールダウンはscaleUp よりはるかに慎重。stabilizationWindow（5分）+ policy制限（10%/分）でフラッピングを防止する。Karpenterは空ノードを検知して自動で削除する。

### シナリオ4: フラッピング（アンチパターン）

stabilizationWindow が短すぎると発生する、Pod数が激しく増減する問題です。

```
─── stabilizationWindowSeconds: 0（悪い設定）───

時刻    リクエスト数     CPU使用率    Pod数    問題

12:00   600             60%          5
12:01   400             40%          5 → 4    ← スケールダウン
12:02   500             62%          4 → 5    ← スケールアップ
12:03   400             40%          5 → 4    ← また下がる
12:04   500             62%          4 → 5    ← また上がる
12:05   400             40%          5 → 4    ← 繰り返し...

問題:
  - Pod の作成・削除が頻繁に発生
  - 起動中のPodがReadyになる前に削除される
  - Karpenter もノードの追加・削除を繰り返す
  - リクエストの一部がエラーになる

─── stabilizationWindowSeconds: 300（良い設定）───

時刻    リクエスト数     CPU使用率    Pod数    状態

12:00   600             60%          5
12:01   400             40%          5        ← 待機中（5分間様子見）
12:02   500             50%          5        ← 待機中
12:03   400             40%          5        ← 待機中
12:04   500             50%          5        ← 待機中
12:05   450             45%          5        ← 待機中
12:06   400             40%          5 → 4    ← 5分間安定して低い→安全にスケールダウン
```

### シナリオ5: Javaウォームアップ問題（コールドPod）

Javaアプリケーション特有の問題です。Pod がReadiness Probeを通過しても、JIT（Just-In-Time）コンパイラが最適化を完了するまでフルパフォーマンスは出ません。

#### ウォームアップとは

```
Java Pod のライフサイクル:

  起動 ──→ クラスロード ──→ DI初期化 ──→ Readiness ──→ ウォームアップ ──→ 最適
  0s        5s              15s         30s           30s〜300s          5分〜
  │         │               │           │             │                 │
  │         │               │           │             │                 │
  │←─── Pod未起動 ────────→│←─ Ready ─→│←─ コールド →│←── フル性能 ──→│
                                         ↑
                                    ここでトラフィック
                                    を受け始めるが...

Readiness通過時のパフォーマンス:
  ┌─────────────────────────────────────────────────┐
  │ 処理能力    JIT最適化されたPod: 200 req/s        │
  │            コールドPod:         50〜80 req/s     │
  │            → 2〜4倍の性能差                      │
  │                                                  │
  │ レイテンシ  JIT最適化されたPod: p95 = 50ms       │
  │            コールドPod:         p95 = 200〜500ms │
  │            → 4〜10倍の遅延                       │
  │                                                  │
  │ CPU使用率  JIT最適化されたPod: 50%               │
  │            コールドPod:         90〜120%          │
  │            → JITコンパイル自体がCPUを消費        │
  └─────────────────────────────────────────────────┘
```

#### コールドPodが引き起こす悪循環

```
時刻    リクエスト数     Pod別CPU使用率                     Pod数

        既存Pod      新Pod(コールド)
10:05   1200         50%        120%                       10
                      ↑          ↑
                   正常     JITコンパイル中 + インタプリタ実行
                             で CPU が跳ね上がる

        ─── 平均CPU = (50%×6 + 120%×4) / 10 = 78% ──────

10:06   1200         全Pod平均 78% ■■■■■■■■                10
                          ↑
                     目標(50%)を大幅超過!
                     HPAは「Podが足りない」と判断
                     → しかし maxReplicas=10 で追加不可

                     ─── 実際はPodは足りている ──────────
                     │ ウォームアップが完了すれば           │
                     │ 10Pod × 200req/s = 2000 req/s       │
                     │ → 1200 req/s は余裕で捌ける         │
                     └────────────────────────────────────┘

10:08   1200         55%        80%                        10
                                 ↑
                            JIT最適化が進行中

10:10   1200         50%        55%                        10
                                 ↑
                            ほぼウォームアップ完了

10:12   1200         50%        50%                        10
                                 ↑
                            フルパフォーマンス到達
```

**問題**: コールドPodのCPU高騰がクラスタ全体の平均CPUを押し上げ、HPAが「まだ足りない」と誤判断する。最悪の場合、maxReplicasに張り付いたまま不要なノードまで追加される。

#### 対策

| 対策 | 効果 | 実装コスト | 説明 |
|------|------|-----------|------|
| **Startup Probe** | 中 | 低 | 起動完了までトラフィックを送らない。ただしウォームアップは解決しない |
| **Readiness Probe の遅延** | 中 | 低 | `initialDelaySeconds` を長めに設定（60〜120秒）。ただしスケーリング速度が犠牲 |
| **事前スケーリング** | 高 | 低 | CronJobで事前にPodを増やし、ウォームアップ時間を確保 |
| **ウォームアップリクエスト** | 高 | 中 | PostStart hookで内部APIにダミーリクエストを送り、JIT最適化を促進 |
| **CRaC** | 非常に高 | 中 | JVMチェックポイントから復元。起動もウォームアップもスキップ |
| **GraalVM Native Image** | 非常に高 | 高 | AOTコンパイルでJIT不要。起動ミリ秒、ウォームアップ不要。ただしピーク性能は従来JVMに劣る場合あり |

#### 対策1: ウォームアップリクエスト（PostStart Hook）

```yaml
spec:
  containers:
    - name: my-app
      lifecycle:
        postStart:
          exec:
            command:
              - /bin/sh
              - -c
              - |
                # アプリ起動を待つ
                sleep 30
                # 主要エンドポイントにダミーリクエストを送る
                for i in $(seq 1 100); do
                  curl -s http://localhost:8080/health > /dev/null
                  curl -s http://localhost:8080/api/warmup > /dev/null
                done
```

#### 対策2: CRaC（Coordinated Restore at Checkpoint）

```
通常のJava起動:
  JVM起動 → クラスロード → DI初期化 → JIT → Ready
  0s                                         30s

CRaC復元:
  チェックポイントから復元 → Ready（JIT最適化済み）
  0s                         1〜2s

┌──────────────────────────────────────────────────┐
│ CRaCの仕組み                                      │
│                                                    │
│ 1. 本番相当の環境でアプリを起動                    │
│ 2. ウォームアップ完了後、JVMの状態をファイルに保存  │
│    （メモリ、JITコンパイル結果、コネクション状態等） │
│ 3. Pod起動時はそのファイルから復元                  │
│ → 起動 1〜2秒、かつウォームアップ済み              │
└──────────────────────────────────────────────────┘
```

#### 対策3: GraalVM Native Image

```
通常のJVM:
  インタプリタ → JITコンパイル → 最適化コード
  起動: 15〜60秒, ウォームアップ: 1〜5分
  ピーク性能: 非常に高い（JIT最適化の恩恵）

GraalVM Native Image:
  AOTコンパイル済みバイナリ → 即座に最適化コード
  起動: 10〜100ms, ウォームアップ: 不要
  ピーク性能: JVMの80〜90%程度

┌──────────────────────────────────────────────────┐
│ トレードオフ                                       │
│                                                    │
│ ✓ 起動が劇的に速い（Goと同等）                     │
│ ✓ ウォームアップ不要                               │
│ ✓ メモリ消費が少ない                               │
│                                                    │
│ ✗ ビルド時間が長い（数分〜数十分）                  │
│ ✗ リフレクション等の動的機能に制約                  │
│ ✗ ピーク性能は従来JVMに劣る場合がある               │
│ ✗ 一部ライブラリが非対応                            │
└──────────────────────────────────────────────────┘
```

#### Go / Node.js との比較

```
Go:
  起動: ミリ秒         ウォームアップ: 不要
  → コールドPod問題なし。HPA がそのまま効く。

Node.js:
  起動: 数秒           ウォームアップ: ほぼ不要（V8のJITは軽量）
  → 最初の数リクエストがやや遅いが、実用上問題ない。

Java (標準JVM):
  起動: 15〜60秒       ウォームアップ: 1〜5分
  → コールドPod問題あり。対策が必要。

Java (CRaC):
  起動: 1〜2秒         ウォームアップ: 不要（チェックポイントに含まれる）
  → Go に近い特性。ただしチェックポイント管理の運用コスト。

Java (GraalVM Native):
  起動: 10〜100ms      ウォームアップ: 不要
  → Go と同等。ただしピーク性能とライブラリ互換性にトレードオフ。
```

### シミュレーションのまとめ

| シナリオ | HPAの動き | Karpenterの動き | 言語の影響 |
|---------|----------|----------------|-----------|
| 緩やかな増加 | 段階的にPod追加 | 通常不要 | 小さい |
| 突発スパイク | 一気にmax付近まで | ノード追加 | 大きい（起動時間が効く） |
| 負荷減少 | 慎重にスケールダウン | 空ノードをConsolidation | なし |
| フラッピング | stabilizationWindowで防止 | 短すぎると巻き込まれる | なし |
| Javaウォームアップ | コールドPodのCPU高騰で誤判断 | 不要なノード追加の可能性 | Java固有（CRaC/Native Imageで解消可能） |

---

## 12. アプリケーション特性別の設計指針

### 言語別の推奨スケーリング構成

| 設定項目 | Java（Spring Boot等） | Go | Node.js |
|---------|----------------------|-----|---------|
| **HPA 目標CPU** | 50%（余裕を持つ） | 60〜70%（効率重視） | 50〜60% |
| **minReplicas** | 多め（3〜4）起動が遅いため | 少なめ（2）で十分 | 少なめ（2〜3） |
| **maxReplicas** | 控えめ（Podが大きいためコスト注意） | 多め（小さいPodを多数並べる） | 多め |
| **CPU requests** | 1000〜2000m（マルチスレッド活用） | 500〜1000m | 1000m（1コア = 1プロセス） |
| **Memory requests** | 1〜4Gi（JVMヒープ考慮） | 128〜512Mi | 256Mi〜1Gi |
| **事前スケーリング** | 強く推奨（起動に15〜60秒） | 不要（ミリ秒で起動） | 通常不要 |
| **VPA活用** | Off（JVMヒープ設定が複雑） | Auto可能 | Off推奨（HPAと併用） |

### maxReplicas と DB コネクションの関係

Pod数を増やす際、DBコネクションプールの合計が上限を超えないように注意が必要です。これは言語を問わず共通の制約です。

```
例: PostgreSQL max_connections = 100

Java（HikariCP デフォルト pool=10）:
  → max 8 Pod × 10 = 80 コネクション（上限内）

Go（sql.DB デフォルト 無制限、通常 pool=25 に制限）:
  → max 8 Pod × 25 = 200 コネクション（上限超過！→ pool=10 に制限）

Node.js（pg pool デフォルト=10）:
  → max 8 Pod × 10 = 80 コネクション（上限内）
```

DBコネクションが不足する場合は、RDS Proxyの導入を検討します。RDS Proxyはアプリ側のコネクション数を集約し、DB側の接続数を大幅に削減できます。

### 負荷パターンとスケーリング戦略

```
リクエスト数
  │
  │              ┌─────┐
  │         ┌────┘     │
  │    ┌────┘          └───┐
  │────┘                    └────────
  └──────────────────────────────── 時刻
  0:00  6:00  9:00  18:00  22:00

  │ HPA min=2 │ 事前スケール │ HPA min=2 │
  │           │ → HPA で調整 │           │
```

| 時間帯 | 戦略 |
|--------|------|
| 深夜〜早朝（0:00-6:00） | min=2 で低コスト運用 |
| 朝（8:00） | CronJobで事前スケール（起動が遅い言語で特に有効） |
| 日中（9:00-18:00） | HPAが負荷に応じて自動調整 |
| 夜間（22:00〜） | HPAが自然にスケールダウン |

### Spot インスタンスの言語別適性

| 観点 | Java | Go | Node.js |
|------|------|-----|---------|
| Spot中断時の復旧 | 遅い（JVM再起動に時間） | 速い | 速い |
| Graceful Shutdown | `preStop` + `SIGTERM`で対応可能 | `SIGTERM` ハンドリングが簡潔 | `SIGTERM` ハンドリングが簡潔 |
| Spot適性 | ステートレスなワーカーのみ | 広く適用可能 | 広く適用可能 |

---

## 13. IDサービスでの活用

idp-server（Java/Spring Boot）は以下の特性があり、**HPA + Karpenter + 事前スケーリング**の構成が適しています。

| 特性 | スケーリングへの影響 |
|------|---------------------|
| ステートレス | HPAによる水平スケーリングに適している |
| Java（Spring Boot） | JVM起動が遅い → 事前スケーリングが有効 |
| セキュリティ重要 | Spotインスタンスは避け、On-Demand推奨 |
| DB接続あり | コネクションプール上限がPod数の上限になる |

---

## 14. まとめ

- **EKSのAuto Scaling**は Pod層（HPA/VPA/KEDA）と Node層（Karpenter/Cluster Autoscaler）の2層構造
- **HPA**はPod数を水平にスケーリングする標準機能で、ステートレスなWebアプリに最適
- **VPA**はPodのリソースサイズを垂直に調整する。HPAとの併用時は`Off`モードで推奨値参照のみが安全
- **Karpenter**はAWS環境でのノードプロビジョニングに推奨。高速起動とConsolidationによるコスト最適化が特徴
- **Cluster Autoscaler**はマルチクラウドが必要な場合や既存ASG環境との互換性が必要な場合に選択
- **KEDA**はキュー深度やイベント駆動のスケーリングに使い、0→Nスケールが可能
- **言語特性がスケーリング設計に影響する**: Goは高速起動で反応型スケーリングが有効、Javaは起動が遅いため事前スケーリングが重要、Node.jsはI/Oバウンド特性を考慮したメトリクス選択が鍵

## 次のステップ
- [コンテナサービス](./aws-container-services.md) - ECS/EKSの基本概念を学ぶ
- [ロードバランシング](../04-networking/aws-load-balancing.md) - スケーリングと連携するALBの設定を学ぶ
- [モニタリング](../05-security-monitoring/aws-monitoring.md) - スケーリング判断の基盤となるメトリクス監視を学ぶ

## 参考リソース
- [Amazon EKS ユーザーガイド](https://docs.aws.amazon.com/eks/latest/userguide/)
- [Kubernetes HPA ドキュメント](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [Kubernetes VPA ドキュメント](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler)
- [Karpenter ドキュメント](https://karpenter.sh/docs/)
- [KEDA ドキュメント](https://keda.sh/docs/)
- [Cluster Autoscaler ドキュメント](https://github.com/kubernetes/autoscaler/tree/master/cluster-autoscaler)
