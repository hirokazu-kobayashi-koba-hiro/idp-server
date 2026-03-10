# Karpenter ノードオートスケーリング

Karpenter を使用したノードの自動プロビジョニングと最適化を学びます。NodePool 設計、Disruption 管理、KWOK によるローカル検証までを体系的に扱います。

---

## 目次

1. [Karpenter とは](#karpenter-とは)
2. [Cluster Autoscaler との比較](#cluster-autoscaler-との比較)
3. [アーキテクチャ](#アーキテクチャ)
4. [コアコンセプト](#コアコンセプト)
5. [スケジューリングフロー](#スケジューリングフロー)
6. [Disruption（中断管理）](#disruption中断管理)
7. [NodePool 設計パターン](#nodepool-設計パターン)
8. [ローカル検証（KWOK プロバイダー）](#ローカル検証kwok-プロバイダー)
9. [IDサービスでの想定構成](#idサービスでの想定構成)
10. [チェックリスト](#チェックリスト)
11. [次のステップ](#次のステップ)

---

## Karpenter とは

Karpenter は AWS が開発し、現在は Kubernetes SIGs（CNCF）でホストされるノードオートスケーラー。Pod のリソース要求に応じて、最適なインスタンスタイプのノードを直接プロビジョニングする。

従来の Cluster Autoscaler がノードグループ（ASG）単位でスケーリングするのに対し、Karpenter はノードグループに依存しない **Group-less アーキテクチャ** を採用している。

```
┌─────────────────────────────────────────────────────────────┐
│              Cluster Autoscaler（従来型）                      │
│                                                              │
│  NodeGroup A (m5.large)   NodeGroup B (c5.xlarge)            │
│  ┌──────┐ ┌──────┐       ┌──────┐ ┌──────┐                │
│  │Node 1│ │Node 2│       │Node 3│ │Node 4│                │
│  └──────┘ └──────┘       └──────┘ └──────┘                │
│  → グループごとに固定インスタンスタイプ                        │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│              Karpenter（Group-less）                          │
│                                                              │
│  NodePool（ポリシーのみ定義）                                 │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                     │
│  │m5.lg │ │c5.xl │ │m5.xl │ │c5.lg │                     │
│  └──────┘ └──────┘ └──────┘ └──────┘                     │
│  → Pod の要求に応じて最適なインスタンスを都度選択             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

- **ノードグループ不要**: 事前にインスタンスグループを定義する必要がない
- **直接プロビジョニング**: クラウド API を直接呼び出してノードを作成
- **最適インスタンス選択**: Pod の要求（CPU/メモリ/GPU）に基づき最適なインスタンスタイプを自動選択
- **高速スケーリング**: ASG を経由しないため起動が速い

---

## Cluster Autoscaler との比較

| 項目 | Cluster Autoscaler | Karpenter |
|------|-------------------|-----------|
| スケジューリング方式 | ノードグループの容量を拡張 | Pod 要求から直接ノードを作成 |
| ノードグループ | 必須（ASG） | 不要（NodePool でポリシー定義） |
| インスタンス選択 | グループ内の固定タイプ | 要求に応じて動的に選択 |
| スケーリング速度 | 数分（ASG 経由） | 数十秒（直接プロビジョニング） |
| Bin Packing | 限定的 | Pod 要求を集約して最適なインスタンスを選択 |
| コスト最適化 | 手動でグループ設計が必要 | Spot / On-demand 混合を自動判断 |
| Consolidation | なし | 使用率の低いノードを自動統合 |
| Drift 検知 | なし | NodePool 変更時に自動でノードを置換 |
| クラウド対応 | AWS, GCP, Azure 等 | AWS（GA）、Azure（プレビュー） |
| API バージョン | Kubernetes Autoscaler SIG | `karpenter.sh/v1` |

---

## アーキテクチャ

### 全体構成

```
┌─────────────────────────────────────────────────────────────┐
│                    Karpenter アーキテクチャ                    │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                  Kubernetes Cluster                     │ │
│  │                                                        │ │
│  │  ┌──────────┐   watch    ┌──────────────────────┐     │ │
│  │  │ Pending  │ ─────────► │  Karpenter           │     │ │
│  │  │ Pods     │            │  Controller          │     │ │
│  │  └──────────┘            │                      │     │ │
│  │                          │  ┌────────────────┐  │     │ │
│  │                          │  │ Provisioner    │  │     │ │
│  │                          │  │ - Bin Packing  │  │     │ │
│  │                          │  │ - Scheduling   │  │     │ │
│  │                          │  └───────┬────────┘  │     │ │
│  │                          │          │           │     │ │
│  │                          │  ┌───────▼────────┐  │     │ │
│  │                          │  │ Disruption     │  │     │ │
│  │                          │  │ Controller     │  │     │ │
│  │                          │  └────────────────┘  │     │ │
│  │                          └──────────┬───────────┘     │ │
│  │                                     │                  │ │
│  └─────────────────────────────────────┼──────────────────┘ │
│                                        │                     │
│                              ┌─────────▼──────────┐         │
│                              │  Cloud Provider API │         │
│                              │  (EC2 / KWOK etc.)  │         │
│                              └─────────┬──────────┘         │
│                                        │                     │
│                              ┌─────────▼──────────┐         │
│                              │  New Node           │         │
│                              │  (最適なインスタンス)  │         │
│                              └────────────────────┘         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### コンポーネント

| コンポーネント | 役割 |
|--------------|------|
| Karpenter Controller | Pending Pod を監視し、ノードのプロビジョニングとライフサイクルを管理 |
| Provisioner | Pod の要求を分析し、Bin Packing で最適なノード構成を決定 |
| Disruption Controller | Consolidation、Drift、Expiration によるノードの中断を管理 |
| Cloud Provider | 実際のノード作成・削除をクラウド API 経由で実行 |

---

## コアコンセプト

### NodePool

NodePool はノードのプロビジョニングポリシーを定義するリソース。どのようなノードを作成するかの **制約** と **上限** を宣言する。

```yaml
# nodepool.yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: default
spec:
  template:
    spec:
      requirements:
        # アーキテクチャ制約
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64"]
        # OS 制約
        - key: kubernetes.io/os
          operator: In
          values: ["linux"]
        # 購入タイプ（Spot / On-demand）
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand", "spot"]
        # インスタンスファミリー
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["m5", "m6i", "c5", "c6i", "r5", "r6i"]

      # NodeClass への参照
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default

      # ノードの有効期限（自動ローテーション）
      expireAfter: 720h  # 30日

  # クラスタ全体のリソース上限
  limits:
    cpu: 100       # 最大 100 vCPU
    memory: 200Gi  # 最大 200 GiB

  # 中断ポリシー
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 30s
```

主要フィールド:

| フィールド | 説明 |
|-----------|------|
| `requirements` | ノードに求める制約（arch, os, capacity-type, instance-family 等） |
| `nodeClassRef` | クラウドプロバイダー固有設定への参照 |
| `expireAfter` | ノードの最大生存期間（自動ローテーション） |
| `limits` | この NodePool が管理するリソースの上限 |
| `disruption` | Consolidation ポリシーの設定 |

### NodeClass

NodeClass はクラウドプロバイダー固有の設定を定義する。AWS では `EC2NodeClass`、KWOK では `KWOKNodeClass` を使用する。

```yaml
# ec2nodeclass.yaml（AWS の場合）
apiVersion: karpenter.k8s.aws/v1
kind: EC2NodeClass
metadata:
  name: default
spec:
  # AMI の選択
  amiSelectorTerms:
    - alias: al2023@latest  # Amazon Linux 2023 の最新 AMI

  # サブネットの選択
  subnetSelectorTerms:
    - tags:
        karpenter.sh/discovery: my-cluster

  # セキュリティグループの選択
  securityGroupSelectorTerms:
    - tags:
        karpenter.sh/discovery: my-cluster

  # IAM ロール
  role: KarpenterNodeRole-my-cluster

  # ブロックデバイスマッピング
  blockDeviceMappings:
    - deviceName: /dev/xvda
      ebs:
        volumeSize: 100Gi
        volumeType: gp3
        encrypted: true
```

```yaml
# kwok-nodeclass.yaml（KWOK ローカル検証の場合）
apiVersion: karpenter.kwok.sh/v1alpha1
kind: KWOKNodeClass
metadata:
  name: default
```

### NodeClaim

NodeClaim は Karpenter が内部的に生成するプロビジョニングリクエスト。ユーザーが直接作成することはなく、Karpenter Controller が NodePool のポリシーに基づいて自動生成する。

```
┌─────────────────────────────────────────────────────────────┐
│                  NodeClaim ライフサイクル                      │
│                                                              │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐               │
│  │ Created  │──►│Registered│──►│  Ready   │               │
│  │          │   │          │   │          │               │
│  │NodeClaim │   │Node が   │   │Pod が    │               │
│  │生成      │   │クラスタに │   │スケジュー │               │
│  │          │   │参加      │   │ル可能    │               │
│  └──────────┘   └──────────┘   └─────┬────┘               │
│                                      │                      │
│                          ┌───────────┼───────────┐         │
│                          │           │           │         │
│                          ▼           ▼           ▼         │
│                    ┌──────────┐ ┌──────────┐ ┌────────┐  │
│                    │Consoli-  │ │  Drift   │ │Expired │  │
│                    │dated     │ │          │ │        │  │
│                    └────┬─────┘ └────┬─────┘ └───┬────┘  │
│                         │            │           │        │
│                         └────────────┼───────────┘        │
│                                      ▼                     │
│                                ┌──────────┐                │
│                                │ Deleted  │                │
│                                └──────────┘                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```bash
# NodeClaim の確認
kubectl get nodeclaim

# 出力例
# NAME            TYPE        CAPACITY    ZONE             NODE          READY   AGE
# default-abc12   m5.xlarge   on-demand   ap-northeast-1a  ip-10-0-1-5   True    5m
```

---

## スケジューリングフロー

### Pod Pending からノード作成まで

```
┌─────────────────────────────────────────────────────────────┐
│              Karpenter スケジューリングフロー                  │
│                                                              │
│  Step 1: Pod が Pending 状態になる                            │
│  ┌──────┐ ┌──────┐ ┌──────┐                                │
│  │Pod A │ │Pod B │ │Pod C │  ← リソース不足で Pending       │
│  │1 CPU │ │2 CPU │ │1 CPU │                                 │
│  │2Gi   │ │4Gi   │ │2Gi   │                                 │
│  └──────┘ └──────┘ └──────┘                                │
│       │        │        │                                    │
│       ▼        ▼        ▼                                    │
│  Step 2: バッチスケジューリング（複数 Pod をまとめて評価）      │
│  ┌─────────────────────────────────────────┐               │
│  │  Karpenter Controller                    │               │
│  │  合計要求: 4 CPU, 8 Gi                    │               │
│  └────────────────────┬────────────────────┘               │
│                       │                                      │
│                       ▼                                      │
│  Step 3: Bin Packing（最適なインスタンスを選択）              │
│  ┌─────────────────────────────────────────┐               │
│  │  候補:                                    │               │
│  │  m5.xlarge (4 CPU, 16Gi) ← 最適 ✓        │               │
│  │  m5.2xlarge (8 CPU, 32Gi) ← オーバースペック│               │
│  │  m5.large (2 CPU, 8Gi) ← リソース不足 ✗    │               │
│  └────────────────────┬────────────────────┘               │
│                       │                                      │
│                       ▼                                      │
│  Step 4: ノード作成 → Pod スケジュール                       │
│  ┌─────────────────────────────────────────┐               │
│  │  Node (m5.xlarge)                        │               │
│  │  ┌──────┐ ┌──────┐ ┌──────┐            │               │
│  │  │Pod A │ │Pod B │ │Pod C │            │               │
│  │  └──────┘ └──────┘ └──────┘            │               │
│  └─────────────────────────────────────────┘               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### バッチスケジューリング

Karpenter は個々の Pending Pod に対して1ノードずつ作成するのではなく、一定時間（デフォルト10秒）の間に溜まった Pending Pod をまとめて評価する。これにより、複数の Pod を1つのノードに効率的に配置できる。

### Bin Packing

Bin Packing は Pod のリソース要求を集約し、最もフィットするインスタンスタイプを選択するアルゴリズム。無駄なリソースを最小化しつつ、全 Pod の要求を満たすノードを選ぶ。

```
┌─────────────────────────────────────────────────────────────┐
│                    Bin Packing の例                           │
│                                                              │
│  要求: Pod A (1CPU, 2Gi) + Pod B (2CPU, 4Gi)                │
│                                                              │
│  ✗ 2 × m5.large (各 2CPU, 8Gi)  → 1CPU+4Gi の無駄           │
│  ┌─────────────┐ ┌─────────────┐                           │
│  │ [Pod A    ] │ │ [Pod B    ] │                           │
│  │ [  空き   ] │ │ [  空き   ] │                           │
│  └─────────────┘ └─────────────┘                           │
│                                                              │
│  ✓ 1 × m5.xlarge (4CPU, 16Gi)  → 最小構成で全 Pod 収容      │
│  ┌───────────────────────────┐                              │
│  │ [Pod A] [Pod B] [ 空き  ] │                              │
│  └───────────────────────────┘                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Disruption（中断管理）

Karpenter はノードのライフサイクルを自動管理し、コスト最適化とセキュリティを維持する。

### Consolidation

Consolidation はワークロードの減少時にノードを統合し、コストを削減する機能。

```yaml
# nodepool.yaml
spec:
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 30s
```

| ポリシー | 説明 |
|---------|------|
| `WhenEmpty` | Pod がゼロのノードのみ削除 |
| `WhenEmptyOrUnderutilized` | 空ノードの削除に加え、低使用率ノードの Pod を他ノードに移動して統合 |

```
┌─────────────────────────────────────────────────────────────┐
│              Consolidation の動作                             │
│                                                              │
│  Before（使用率が低い）:                                      │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐                │
│  │ Node A    │ │ Node B    │ │ Node C    │                │
│  │ [Pod1]    │ │ [Pod2]    │ │           │  ← Empty       │
│  │ [ 空き ]  │ │ [ 空き ]  │ │           │                │
│  └───────────┘ └───────────┘ └───────────┘                │
│       │              │              │                       │
│       │              │              ▼                       │
│       │              │         WhenEmpty で削除             │
│       │              │                                      │
│       ▼              ▼                                      │
│  After（統合後）:                                             │
│  ┌───────────┐                                              │
│  │ Node A    │                                              │
│  │ [Pod1]    │  ← Pod2 を移動して Node B も削除             │
│  │ [Pod2]    │                                              │
│  └───────────┘                                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Drift

NodePool や NodeClass の設定を変更すると、既存ノードが新しい仕様と「ずれた」状態（Drift）になる。Karpenter は Drift を自動検知し、新しい仕様のノードに順次置換する。

Drift が発生する例:
- NodePool の `requirements` でインスタンスファミリーを変更
- EC2NodeClass の AMI を更新
- NodePool の `labels` や `taints` を変更

```
┌─────────────────────────────────────────────────────────────┐
│                     Drift の動作                              │
│                                                              │
│  1. NodePool の AMI を更新                                   │
│     amiSelectorTerms: al2023@v20240101                       │
│                  → al2023@v20240301                           │
│                                                              │
│  2. 既存ノードが Drifted 状態に                               │
│     ┌─────────────┐                                         │
│     │ Node (old)  │ ← Drifted                               │
│     │ AMI: v0101  │                                          │
│     └──────┬──────┘                                         │
│            │                                                 │
│  3. 新ノード作成 → Pod 移動 → 旧ノード削除                   │
│            │        ┌─────────────┐                         │
│            └──────► │ Node (new)  │                         │
│                     │ AMI: v0301  │                         │
│                     └─────────────┘                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Expiration

`expireAfter` で指定した期間を超えたノードを自動的に置換する。長時間稼働するノードのセキュリティパッチ適用やリソースリークの防止に有効。

```yaml
# nodepool.yaml
spec:
  template:
    spec:
      expireAfter: 720h  # 30日でノードをローテーション
```

---

## NodePool 設計パターン

### 一般ワークロード用

```yaml
# nodepool-general.yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: general
spec:
  template:
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand"]
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["m5", "m6i", "m7i"]
        - key: karpenter.k8s.aws/instance-size
          operator: In
          values: ["large", "xlarge", "2xlarge"]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
      expireAfter: 720h
  limits:
    cpu: 100
    memory: 200Gi
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 60s
  # 優先度（数値が大きいほど優先）
  weight: 50
```

### Spot + On-demand 混合

コスト最適化のために Spot インスタンスを優先し、Spot が確保できない場合は On-demand にフォールバックする構成。

```yaml
# nodepool-spot.yaml（優先: Spot）
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: spot
spec:
  template:
    spec:
      requirements:
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot"]
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["m5", "m6i", "c5", "c6i", "r5", "r6i"]
        - key: karpenter.k8s.aws/instance-size
          operator: In
          values: ["large", "xlarge", "2xlarge"]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
  limits:
    cpu: 200
  weight: 80  # Spot を優先

---
# nodepool-ondemand.yaml（フォールバック: On-demand）
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: on-demand-fallback
spec:
  template:
    spec:
      requirements:
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand"]
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["m5", "m6i"]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
  limits:
    cpu: 100
  weight: 20  # フォールバック
```

### GPU / 特殊用途

```yaml
# nodepool-gpu.yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: gpu
spec:
  template:
    spec:
      requirements:
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["g5", "p4d"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand"]
      # GPU ノード用の taint（GPU ワークロードのみスケジュール）
      taints:
        - key: nvidia.com/gpu
          effect: NoSchedule
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: gpu
  limits:
    cpu: 48
    nvidia.com/gpu: 8
  weight: 10
```

### 複数 NodePool の優先度（weight）

`weight` フィールドで複数 NodePool 間の優先度を制御する。数値が大きい NodePool が優先的に使用される。

```
┌─────────────────────────────────────────────────────────────┐
│              NodePool 優先度（weight）                        │
│                                                              │
│  weight: 80  ──► Spot NodePool         ← 最優先             │
│  weight: 50  ──► General NodePool      ← 標準               │
│  weight: 20  ──► On-demand Fallback    ← フォールバック      │
│  weight: 10  ──► GPU NodePool          ← 特殊用途           │
│                                                              │
│  Karpenter は weight が高い NodePool から順に                │
│  Pod の要求を満たせるか評価する                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## ローカル検証（KWOK プロバイダー）

### KWOK とは

KWOK（Kubernetes WithOut Kubelet）は、実際の VM やコンテナを起動せずに仮想ノードをシミュレートするツール。通常のノードには [kubelet](kubernetes-architecture.md)（コンテナの起動・管理を行うエージェント）が存在するが、KWOK ノードにはこれがなく、Pod は `Running` と表示されるだけで実際のコンテナプロセスは動作しない。Karpenter の KWOK プロバイダーを使うことで、クラウド環境なしでノードオートスケーリングの動作を検証できる。

```
┌─────────────────────────────────────────────────────────────┐
│                KWOK プロバイダーの仕組み                       │
│                                                              │
│  通常の Karpenter:                                           │
│  Karpenter ──► EC2 API ──► 実際の EC2 インスタンス            │
│                                                              │
│  KWOK プロバイダー:                                           │
│  Karpenter ──► KWOK ──► 仮想ノード（リソース消費なし）        │
│                                                              │
│  検証できること:                                              │
│  ✓ ノード追加の判断ロジック                                   │
│  ✓ Consolidation の動作                                      │
│  ✓ NodePool ポリシーの挙動                                   │
│                                                              │
│  検証できないこと:                                            │
│  ✗ 実際のコンテナ実行                                        │
│  ✗ ネットワーク/ストレージの動作                              │
│  ✗ クラウドプロバイダーとの連携                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 検証環境の構成

プロジェクトの `k8s/local/karpenter-kwok/` に検証環境が用意されている。

```
k8s/local/karpenter-kwok/
├── README.md              # 詳細ドキュメント
├── setup.sh               # セットアップスクリプト
├── teardown.sh            # クリーンアップスクリプト
├── verify.sh              # 動作検証スクリプト
├── kind-config.yaml       # Kind クラスタ設定
└── manifests/
    ├── nodepool.yaml      # NodePool + KWOKNodeClass
    └── demo-workload.yaml # スケール検証用デモ Deployment
```

> **注意**: この検証環境は idp-server のローカル環境（`k8s/local/` の `idp-local` クラスタ）とは独立した別クラスタで動作する。idp-server の実ワークロードは使わず、デモ用の nginx Pod で Karpenter の動作を学習する構成。

### ハンズオン手順

#### 1. 環境セットアップ

```bash
# 前提条件: Docker, kind, kubectl, helm, Go がインストール済み

# セットアップ（Kind クラスタ作成 → KWOK インストール → Karpenter ビルド・デプロイ）
cd k8s/local/karpenter-kwok
bash setup.sh
```

#### 2. スケールアウト（ノード自動追加）

```bash
# デモワークロードを 5 replicas にスケール
kubectl scale deployment demo-inflate --replicas=5

# ノードの追加を監視
kubectl get nodes -w

# 出力例
# NAME                          STATUS   ROLES           AGE   VERSION
# karpenter-kwok-control-plane  Ready    control-plane   10m   v1.31.0
# kwok-abcde                    Ready    <none>          5s    v1.31.0

# Pod の配置状況を確認
kubectl get pods -o wide
```

#### 3. スケールイン（Consolidation 確認）

```bash
# replicas を 0 に戻す
kubectl scale deployment demo-inflate --replicas=0

# ノードの削除を監視（consolidateAfter: 30s 後に削除）
kubectl get nodes -w

# Karpenter のログで Consolidation の判断を確認
kubectl -n kube-system logs -l app.kubernetes.io/name=karpenter -f
```

#### 4. NodePool の状態確認

```bash
# NodePool の状態
kubectl get nodepool

# NodePool の詳細（リソース使用状況）
kubectl describe nodepool default
```

#### 5. 自動検証

```bash
# スケールアウト → Consolidation の一連の動作を自動検証
bash verify.sh
```

#### 6. クリーンアップ

```bash
# Kind クラスタごと削除
bash teardown.sh
```

---

## IDサービスでの想定構成

### EKS + Karpenter 構成

```
┌─────────────────────────────────────────────────────────────┐
│                EKS + Karpenter 構成                           │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                    EKS Cluster                         │ │
│  │                                                        │ │
│  │  ┌──────────────┐  ┌──────────────┐                   │ │
│  │  │ NodePool:    │  │ NodePool:    │                   │ │
│  │  │ idp-server   │  │ system       │                   │ │
│  │  │              │  │              │                   │ │
│  │  │ m5/m6i       │  │ m5.large     │                   │ │
│  │  │ on-demand    │  │ on-demand    │                   │ │
│  │  └──────┬───────┘  └──────┬───────┘                   │ │
│  │         │                 │                            │ │
│  │         ▼                 ▼                            │ │
│  │  ┌────────────┐  ┌────────────┐                      │ │
│  │  │ idp-server │  │ monitoring │                      │ │
│  │  │ Pod × N    │  │ logging    │                      │ │
│  │  └────────────┘  └────────────┘                      │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### EC2NodeClass の設計例

```yaml
# ec2nodeclass-idp.yaml
apiVersion: karpenter.k8s.aws/v1
kind: EC2NodeClass
metadata:
  name: idp-server
spec:
  amiSelectorTerms:
    - alias: al2023@latest
  subnetSelectorTerms:
    - tags:
        karpenter.sh/discovery: idp-cluster
        network: private  # プライベートサブネットのみ
  securityGroupSelectorTerms:
    - tags:
        karpenter.sh/discovery: idp-cluster
  role: KarpenterNodeRole-idp-cluster
  blockDeviceMappings:
    - deviceName: /dev/xvda
      ebs:
        volumeSize: 50Gi
        volumeType: gp3
        encrypted: true
  tags:
    Environment: production
    Service: idp-server
```

### NodePool 設計例（idp-server 用）

```yaml
# nodepool-idp-server.yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: idp-server
spec:
  template:
    metadata:
      labels:
        workload-type: idp-server
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["on-demand"]  # 認証基盤は On-demand で安定性を優先
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: ["m5", "m6i", "m7i"]  # メモリバランス型
        - key: karpenter.k8s.aws/instance-size
          operator: In
          values: ["large", "xlarge", "2xlarge"]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: idp-server
      expireAfter: 336h  # 14日でローテーション
  limits:
    cpu: 64
    memory: 128Gi
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 60s  # 安定性のため長めに設定
    # メンテナンスウィンドウでの disruption を許可
    budgets:
      - nodes: "10%"  # 同時に中断するノードは全体の10%まで
```

### HPA + Karpenter の連携

HPA（Pod 水平スケーリング）と Karpenter（ノードスケーリング）は自然に連携する。

```
┌─────────────────────────────────────────────────────────────┐
│             HPA + Karpenter 連携フロー                        │
│                                                              │
│  1. リクエスト増加                                           │
│     ┌──────────────────────────────────────┐                │
│     │ idp-server Pod: CPU 使用率上昇 (85%) │                │
│     └──────────────────┬───────────────────┘                │
│                        │                                     │
│  2. HPA が Pod を追加                                        │
│                        ▼                                     │
│     ┌──────────────────────────────────────┐                │
│     │ HPA: replicas 3 → 6                  │                │
│     │ 新 Pod 3つが Pending に               │                │
│     └──────────────────┬───────────────────┘                │
│                        │                                     │
│  3. Karpenter がノードを追加                                 │
│                        ▼                                     │
│     ┌──────────────────────────────────────┐                │
│     │ Karpenter: 新ノード作成               │                │
│     │ Pending Pod をスケジュール             │                │
│     └──────────────────┬───────────────────┘                │
│                        │                                     │
│  4. リクエスト減少（逆順で縮退）                              │
│                        ▼                                     │
│     HPA: replicas 6 → 3                                     │
│     Karpenter: 空ノードを Consolidation で削除               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```yaml
# hpa-idp-server.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: idp-server-hpa
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
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
```

ポイント:
- HPA がリソース使用率に基づき Pod 数を増減する
- Pod が Pending になると Karpenter が自動でノードを追加する
- ワークロード減少時は HPA が Pod を減らし、Karpenter が空ノードを Consolidation で削除する
- 2つのコントローラーが独立して動作するため、設定の衝突は発生しない

---

## チェックリスト

- [ ] NodePool の `requirements` でインスタンスタイプの範囲を適切に制約
- [ ] NodePool の `limits` でリソース上限を設定（コスト暴走防止）
- [ ] `expireAfter` でノードの定期ローテーションを有効化
- [ ] `consolidationPolicy` と `consolidateAfter` を設定
- [ ] EC2NodeClass でプライベートサブネットとセキュリティグループを正しく指定
- [ ] EBS ボリュームの暗号化を有効化
- [ ] HPA と Karpenter の連携を確認（Pod Pending → ノード追加の動作）
- [ ] PodDisruptionBudget を設定（Consolidation 時の可用性確保）
- [ ] Disruption の `budgets` で同時中断ノード数を制限

---

## 次のステップ

- [EKS Auto Scaling](../27-aws/02-computing/aws-eks-autoscaling.md) - HPA + Karpenter の連携シミュレーション、言語別設計指針、IDサービス構成
- [Kubernetes スケーリング](kubernetes-scaling.md) - HPA、VPA、Cluster Autoscaler
- [kind ローカルクラスター](kind-local-cluster.md) - Kind による HPA ハンズオン
- [Kubernetes 可観測性](kubernetes-observability.md) - ログ、メトリクス、トレース

---

## 参考リソース

- [Karpenter 公式ドキュメント](https://karpenter.sh/)
- [Karpenter Best Practices](https://aws.github.io/aws-eks-best-practices/karpenter/)
- [NodePool コンセプト](https://karpenter.sh/docs/concepts/nodepools/)
- [Disruption コンセプト](https://karpenter.sh/docs/concepts/disruption/)
- [KWOK プロジェクト](https://kwok.sigs.k8s.io/)
- [kubernetes-sigs/karpenter](https://github.com/kubernetes-sigs/karpenter)
