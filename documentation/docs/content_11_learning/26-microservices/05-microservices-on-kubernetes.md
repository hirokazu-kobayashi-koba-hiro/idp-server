---
sidebar_position: 5
---

# マイクロサービスとKubernetes

---

## 概要

マイクロサービスアーキテクチャには、複数の独立したサービスを効率的に管理する基盤が必要です。本ドキュメントでは、Kubernetesがマイクロサービス特有の課題をどう解決するかを解説します。

---

## マイクロサービスの運用課題

### 手動管理の限界

```
10個のマイクロサービスがある場合:

各サービスで必要な作業:
├─ デプロイ（本番、ステージング、開発）
├─ スケーリング（負荷に応じて）
├─ ヘルスチェック
├─ 障害時の再起動
├─ サービス間通信の設定
├─ ログの収集
├─ メトリクスの監視
└─ セキュリティ設定

10サービス × 8作業 = 80の運用タスク
50サービスなら: 400タスク

手動管理: 不可能
```

---

## KubernetesによるマイクロサービスPatrick管理

### 課題1: 大量のサービスのデプロイ

**問題**:
```
50のマイクロサービスを個別にデプロイ・管理
- 各サービスのバージョン管理
- デプロイ手順の標準化
- 環境ごとの設定差分
```

**Kubernetesの解決**:
```
宣言的な設定:

各サービスをYAMLで定義
  ↓
kubectl apply -f manifests/
  ↓
全サービスが自動的に:
├─ デプロイ
├─ スケジューリング
├─ ヘルスチェック
└─ 自動復旧

標準化された管理
```

---

### 課題2: サービス間のネットワーク管理

**問題**:
```
サービスAがサービスBを呼び出す際:
- サービスBのIPアドレスは？
- Podが増減したらどうなる？
- 複数インスタンスへのロードバランシング
```

**Kubernetesの解決**:
```
Service リソース:

order-service という名前でアクセス
  ↓
Kubernetes DNS が自動解決
  ↓
利用可能なPodに自動分散

コード内:
http://order-service/api/orders

設定不要:
- IPアドレスを知る必要なし
- Podの増減を意識不要
- ロードバランシング自動
```

---

### 課題3: 部分的なスケーリング

**問題**:
```
負荷の偏り:
├─ 注文サービス: 高負荷（スケール必要）
├─ ユーザーサービス: 中負荷（現状維持）
└─ 通知サービス: 低負荷（スケール不要）

各サービスを独立してスケール
```

**Kubernetesの解決**:
```
Horizontal Pod Autoscaler:

注文サービス:
CPU 70%超過 → 自動的に3 Pods → 8 Pods

ユーザーサービス:
CPU 40% → 3 Podsのまま

通知サービス:
CPU 20% → 1 Podのまま

各サービスが独立してスケーリング
リソースの最適利用
```

---

### 課題4: 障害の分離

**問題**:
```
決済サービスが障害:
- 他のサービスへの影響を最小化したい
- 決済サービスだけを再起動したい
- 障害の連鎖を防ぎたい
```

**Kubernetesの解決**:
```
Pod/Namespaceレベルでの分離:

決済サービスの障害:
├─ Liveness Probe が検知
├─ 該当Podのみ再起動
├─ 他のPod/サービスは影響なし
└─ 障害が分離される

さらに:
Network Policy で通信制限
→ 障害の伝播を防ぐ
```

---

## マイクロサービス特有のKubernetes活用

### 1. サービスごとの独立したデプロイ

```
従来の問題:
全サービスを同時リリース
- リスク大
- 調整コスト高
- ロールバック困難

Kubernetesでの解決:

サービスA: v1.0 → v1.1（月曜）
サービスB: v2.3 → v2.4（水曜）
サービスC: v3.1 → v3.2（金曜）

各サービスが独立したリリースサイクル:
kubectl apply -f order-service-v1.1.yaml
→ order-service のみ更新
→ 他サービスは無影響
```

---

### 2. サービスメッシュとの統合

**問題**:
```
マイクロサービスで必要な機能:
├─ サービス間の暗号化通信（mTLS）
├─ トラフィック制御（カナリアリリース）
├─ リトライ・タイムアウト設定
├─ 分散トレーシング
└─ メトリクス収集

各サービスに実装: 大変
```

**Istio（Service Mesh）での解決**:
```
アプリケーションコードは変更不要

Istio導入:
  ↓
各Podに自動的にSidecarプロキシ注入
  ↓
機能が自動的に有効化:
├─ mTLS（自動）
├─ トラフィック制御（設定のみ）
├─ リトライ（設定のみ）
└─ トレーシング（自動）

マイクロサービス開発者:
└─ ビジネスロジックに集中
```

---

### 3. カナリアデプロイメント

**マイクロサービス特有のニーズ**:
```
新バージョンのリスク評価:
- 一部のトラフィックのみ新バージョンへ
- 問題なければ段階的に拡大
- サービスごとに実施
```

**Istio VirtualService**:
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
spec:
  hosts:
  - order-service
  http:
  - route:
    - destination:
        host: order-service
        subset: v1-0
      weight: 90
    - destination:
        host: order-service
        subset: v1-1
      weight: 10

段階的に:
90/10 → 70/30 → 50/50 → 0/100

サービスAはカナリア中
サービスBは通常運用
→ 独立して管理
```

---

### 4. 複数環境の管理

**マイクロサービス×環境の組み合わせ**:
```
10サービス × 3環境 = 30デプロイメント

production namespace:
├─ order-service (v1.5)
├─ user-service (v2.1)
└─ payment-service (v1.8)

staging namespace:
├─ order-service (v1.6-rc)
├─ user-service (v2.1)
└─ payment-service (v1.8)

development namespace:
├─ order-service (v1.7-dev)
└─ user-service (v2.2-dev)

Namespaceで分離:
- リソースの分離
- アクセス制御
- 設定の差分管理
```

---

## マイクロサービスならではの設定

### 1. サービス間の依存関係

**Init Container**:
```yaml
# 注文サービスは決済サービスが起動してから起動

initContainers:
- name: wait-for-payment-service
  image: busybox
  command:
  - sh
  - -c
  - |
    until nslookup payment-service; do
      echo waiting for payment-service
      sleep 2
    done

containers:
- name: order-service
  image: order-service:1.0
```

**効果**:
依存サービスが準備完了してから起動
→ 起動順序の制御

---

### 2. サービス間の通信制御

**Network Policy**:
```yaml
# 注文サービスは決済サービスとのみ通信可能

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: order-service-policy
spec:
  podSelector:
    matchLabels:
      app: order-service
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: payment-service
    ports:
    - protocol: TCP
      port: 8080
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432

マイクロサービスのセキュリティ:
各サービスが必要最小限の通信のみ許可
```

---

### 3. サービス別のリソース配分

**ResourceQuota（Namespace単位）**:
```yaml
# production namespaceのリソース上限

apiVersion: v1
kind: ResourceQuota
metadata:
  name: production-quota
  namespace: production
spec:
  hard:
    requests.cpu: "100"
    requests.memory: "200Gi"
    pods: "200"

効果:
- サービスの暴走防止
- 公平なリソース配分
- コスト管理
```

---

## マイクロサービスのデプロイパターン

### パターン1: サービスごとにローリングアップデート

```
50サービスを順次更新:

Week 1: サービスA、B、C更新
Week 2: サービスD、E、F更新
...

各サービスは Rolling Update:
├─ v1.0 Pod 3つ
├─ v1.1 Pod 1つ作成
├─ v1.1 準備完了確認
├─ v1.0 Pod 1つ削除
└─ 繰り返し

ダウンタイムゼロ
サービスごとに独立したリリースサイクル
```

---

### パターン2: 全サービス同時更新（リスク高）

```
避けるべきパターン:

全50サービスを同時更新
├─ リスク: 極大
├─ ロールバック: 困難
└─ 影響範囲: 全システム

マイクロサービスの利点を殺す
```

---

### パターン3: Blue-Green（重要サービスのみ）

```
決済サービス（クリティカル）:

Blue環境: v1.0（現行）
Green環境: v1.1（新バージョン）

テスト完了後:
└─ Ingressを切り替え
   → 即座に全トラフィックがv1.1へ
   → 問題あれば即座にBlueへ戻す

クリティカルなサービスのみ適用
コストとリスクのバランス
```

---

## サービス間通信の実現

### Kubernetes Service による抽象化

```
マイクロサービスの要件:
「サービスBを呼び出したい」
「でもPodのIPは動的に変わる」

Kubernetes Service:

order-service (Service)
  ↓ 固定されたDNS名
  ↓ ClusterIP: 10.96.0.10（固定）
  ↓ セレクター: app=order
  ↓
┌────────┐ ┌────────┐ ┌────────┐
│Pod 1   │ │Pod 2   │ │Pod 3   │
│10.1.1.1│ │10.1.1.2│ │10.1.1.3│
└────────┘ └────────┘ └────────┘

アプリコード:
http://order-service/api/orders

Podが増減しても:
- コード変更不要
- 設定変更不要
- 自動的にロードバランシング
```

---

### サービスメッシュによる高度な制御

```
マイクロサービスで必要な機能:

1. トラフィック分割（A/Bテスト）
   v1.0: 80%
   v1.1: 20%

2. タイムアウト設定
   決済サービス: 30秒
   通知サービス: 5秒

3. リトライ設定
   一時的なエラー: 3回リトライ

4. サーキットブレーカー
   エラー率5%超過 → 一時的に遮断

Istio で全て設定可能:
- アプリコード変更不要
- YAML設定のみ
```

---

## マイクロサービスの観測性

### 分散トレーシングの重要性

```
モノリス:
1リクエスト = 1プロセス内で完結
→ ログで追跡可能

マイクロサービス:
1リクエスト = 5-10サービスを横断

例: 注文処理
API Gateway
  ↓ 50ms
Order Service
  ↓ 100ms
Inventory Service
  ↓ 200ms (遅い！)
Payment Service
  ↓ 30ms
Notification Service

合計: 380ms

どこが遅い？
→ 分散トレーシング必須
```

---

**Istioでの自動トレーシング**:
```
Istio導入:
  ↓
各サービスのSidecarが自動的に:
├─ Trace ID を生成・伝播
├─ Span情報を記録
└─ Jaeger/Zipkinに送信

開発者:
└─ コード変更不要
   （HTTPヘッダーの伝播のみ実装）

結果:
全リクエストの経路が可視化
ボトルネック特定が容易
```

---

## マイクロサービスのスケーリング

### サービスごとの独立したスケーリング

```
負荷パターンの違い:

平常時:
├─ 注文サービス: 100 req/s → 3 Pods
├─ 商品サービス: 50 req/s → 2 Pods
└─ 通知サービス: 10 req/s → 1 Pod

セール時:
├─ 注文サービス: 1000 req/s → 30 Pods ⬆
├─ 商品サービス: 500 req/s → 15 Pods ⬆
└─ 通知サービス: 100 req/s → 3 Pods ⬆

モノリスの場合:
全機能を含むアプリ全体をスケール
→ 非効率（不要な機能もスケール）

マイクロサービス + Kubernetes:
必要なサービスのみスケール
→ リソース効率的
```

---

## マイクロサービスの設定管理

### ConfigMap での環境差分

```
マイクロサービスの設定課題:
- 50サービス × 3環境 = 150の設定
- 環境ごとに異なる値
- セキュアな管理

production:
  order-service → ConfigMap (production)
  user-service → ConfigMap (production)

staging:
  order-service → ConfigMap (staging)
  user-service → ConfigMap (staging)

同じコンテナイメージ:
環境変数で動作を切り替え

利点:
- イメージの再利用
- 設定の一元管理
- 環境の一貫性
```

---

## マイクロサービスの障害対策

### 自己修復

```
マイクロサービスの課題:
あるサービスがクラッシュ
→ 影響を最小化
→ 自動復旧

Kubernetes:

Liveness Probe 失敗:
  ↓
Podを自動再起動
  ↓
Readiness Probe で準備確認
  ↓
準備完了後にトラフィック流す

他のサービスは:
└─ 無影響で稼働継続
   （障害の分離）
```

---

### レプリカによる冗長性

```
各サービスを複数Podで実行:

order-service:
├─ Pod 1（ノードA）
├─ Pod 2（ノードB）
└─ Pod 3（ノードC）

Pod 1 がクラッシュ:
├─ 他の2 Podで処理継続
├─ Kubernetesが自動的に新Podを起動
└─ 数秒〜数十秒で復旧

高可用性を自動的に実現
```

---

## 実際の構成例

### ECサイトのマイクロサービス on Kubernetes

```
production namespace:

Ingress（外部公開）
  ↓
API Gateway Service (3 Pods)
  ├→ User Service (3 Pods)
  │   └→ PostgreSQL (StatefulSet, 1 Master + 2 Replicas)
  ├→ Order Service (5 Pods, HPA有効)
  │   └→ PostgreSQL (StatefulSet)
  ├→ Product Service (2 Pods)
  │   └→ MongoDB (StatefulSet)
  ├→ Inventory Service (3 Pods)
  │   └→ Redis (StatefulSet)
  └→ Payment Service (3 Pods, Blue-Green)
      └→ PostgreSQL (StatefulSet)

共通サービス:
├─ Kafka (StatefulSet, 3 brokers)
├─ Elasticsearch (StatefulSet, 3 nodes)
└─ Prometheus (Deployment)

Service Mesh:
└─ Istio（全Podに Sidecar）

合計: 約50 Pods
管理: Kubernetesが自動化
```

---

## モノリス vs マイクロサービス on Kubernetes

### デプロイの違い

```
モノリス:
├─ 1つのDeployment
├─ 1つのService
├─ シンプル
└─ でも全体を更新必要

マイクロサービス:
├─ 10-50のDeployment
├─ 10-50のService
├─ 複雑
└─ 部分的な更新が可能

Kubernetes:
├─ 複雑さを抽象化
├─ 宣言的な設定
└─ 自動化で管理可能に
```

---

## いつKubernetesを導入すべきか

### 適している状況

```
マイクロサービスの数:
✓ 10以上のサービス
✓ 頻繁なデプロイ（週次以上）
✓ 自動スケーリングが必要
✓ 高可用性要求
✓ チームが複数

判断:
サービス数が増えるほど、Kubernetesの価値増大
```

---

### 代替案

```
少数のマイクロサービス（3-5個）:

選択肢A: Docker Compose
├─ シンプル
├─ 小規模に最適
└─ 単一ホストのみ

選択肢B: AWS ECS / Fargate
├─ マネージド
├─ Kubernetes学習不要
└─ AWS依存

選択肢C: マネージドKubernetes (EKS/GKE/AKS)
├─ Kubernetesの利点
├─ 運用はクラウドが担当
└─ コストは高め

判断基準:
サービス数、チーム規模、運用リソース
```

---

## まとめ

### Kubernetesがマイクロサービスに提供する価値

```
自動化:
├─ デプロイ
├─ スケーリング
├─ 復旧
└─ ロードバランシング

抽象化:
├─ サービスディスカバリ
├─ ネットワーク管理
└─ ストレージ管理

標準化:
├─ 宣言的な設定
├─ 再現性
└─ 環境の一貫性

マイクロサービスの複雑性を管理可能に
```

### マイクロサービス特有の活用

```
1. サービスごとの独立デプロイ
2. 部分的スケーリング
3. 障害の分離と自動復旧
4. サービスメッシュとの統合
5. 複数環境の効率的管理

Kubernetes = マイクロサービスのプラットフォーム
```

---

## 参考リンク

- [Kubernetes Documentation - Services](https://kubernetes.io/docs/concepts/services-networking/service/)
- [Istio - Traffic Management](https://istio.io/latest/docs/concepts/traffic-management/)
- [Microservices on Kubernetes (Google Cloud)](https://cloud.google.com/architecture/microservices-architecture-on-gke)

**最終更新**: 2026-01-24
