# kind（Kubernetes IN Docker）

kindは、Dockerコンテナをノードとして使い、ローカルマシン上にKubernetesクラスターを構築するツールです。CI/CDパイプラインでのテストやローカル開発環境として広く利用されています。

---

## 目次

1. [kindとは](#1-kindとは)
2. [他のローカルK8sツールとの比較](#2-他のローカルk8sツールとの比較)
3. [インストール](#3-インストール)
4. [クラスターの作成と管理](#4-クラスターの作成と管理)
5. [クラスター設定のカスタマイズ](#5-クラスター設定のカスタマイズ)
6. [ローカルイメージの利用](#6-ローカルイメージの利用)
7. [Ingress の設定](#7-ingress-の設定)
8. [永続化ボリューム](#8-永続化ボリューム)
9. [マルチノードクラスター](#9-マルチノードクラスター)
10. [Metrics Server と HPA を試す](#10-metrics-server-と-hpa-を試す)
11. [トラブルシューティング](#11-トラブルシューティング)
12. [IDサービスでの活用](#12-idサービスでの活用)
13. [まとめ](#13-まとめ)

---

## 1. kindとは

### 概要

kind（**K**ubernetes **IN** **D**ocker）は、Dockerコンテナを「ノード」として使うローカルKubernetesクラスターです。各ノードは1つのDockerコンテナとして動作し、その中でkubelet、コンテナランタイム、Kubernetesコンポーネントが実行されます。

```
┌─── ホストマシン（macOS / Linux / Windows）──────────────────┐
│                                                              │
│   Docker Engine                                              │
│   ┌─────────────────────────────────────────────────────┐   │
│   │                                                     │   │
│   │  ┌─ コンテナ: kind-control-plane ────────────────┐  │   │
│   │  │                                                │  │   │
│   │  │  kube-apiserver                                │  │   │
│   │  │  kube-controller-manager                       │  │   │
│   │  │  kube-scheduler                                │  │   │
│   │  │  etcd                                          │  │   │
│   │  │  kubelet                                       │  │   │
│   │  │  containerd（入れ子コンテナ）                    │  │   │
│   │  │    ├── CoreDNS Pod                             │  │   │
│   │  │    ├── kube-proxy Pod                          │  │   │
│   │  │    └── ユーザーアプリ Pod                       │  │   │
│   │  │                                                │  │   │
│   │  └────────────────────────────────────────────────┘  │   │
│   │                                                     │   │
│   └─────────────────────────────────────────────────────┘   │
│                                                              │
│   kubectl ──→ localhost:6443 ──→ kind-control-plane          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Docker in Docker（DinD）の仕組み

kindのノードコンテナ内では、containerdがコンテナランタイムとして動作します。つまり「Dockerコンテナの中でさらにコンテナを動かす」入れ子構造です。

```
ホストの Docker Engine
  └── kind ノードコンテナ（docker run で起動）
       └── containerd（ノード内のコンテナランタイム）
            ├── Pod A のコンテナ
            ├── Pod B のコンテナ
            └── システムPod のコンテナ
```

### kindが選ばれる理由

| 理由 | 説明 |
|------|------|
| **軽量** | VMを使わず、Dockerコンテナだけで動作。起動が速い |
| **CI/CD向き** | Docker が動く環境ならどこでも使える。GitHub ActionsやGitLab CIで標準的 |
| **本物のK8s** | minikubeと異なり、kubeadmで構築された完全なKubernetesクラスター |
| **マルチノード** | 複数ノードクラスターをローカルで簡単に構築 |
| **K8s本体の開発ツール** | もともとKubernetesプロジェクト自身のテスト用に開発された |

---

## 2. 他のローカルK8sツールとの比較

| 観点 | kind | minikube | Docker Desktop K8s | k3d |
|------|------|----------|--------------------|-----|
| ノード実装 | Dockerコンテナ | VM（またはDocker） | VM内蔵 | Dockerコンテナ |
| マルチノード | 対応 | 対応（v1.10.1+） | 非対応（シングルのみ） | 対応 |
| 起動速度 | 速い（20〜40秒） | 遅い（1〜3分、VM起動） | 速い（常時起動） | 速い（20〜30秒） |
| K8sディストリ | 標準K8s（kubeadm） | 標準K8s（kubeadm） | 標準K8s | k3s（軽量版） |
| リソース消費 | 低〜中 | 高（VM分） | 中（常時起動） | 低 |
| CI/CD適性 | 非常に高い | 低い | 不可 | 高い |
| LoadBalancer | 追加設定が必要 | `minikube tunnel` | そのまま使える | 追加設定が必要 |
| GPU対応 | 非対応 | 対応 | 対応 | 非対応 |
| 用途 | テスト、CI/CD、学習 | ローカル開発、学習 | 手軽な開発 | テスト、CI/CD |

### 選定ガイド

```
┌─ どの場面で使うか ──────────────────────────────────────┐
│                                                         │
│  CI/CD パイプライン    → kind（軽量、Docker環境で完結）   │
│  マルチノードテスト    → kind（簡単に複数ノード構成）     │
│  日常の開発作業        → Docker Desktop K8s（常時起動）   │
│  GPU が必要            → minikube（GPU パススルー対応）   │
│  k3s / 軽量K8sテスト   → k3d（k3s on Docker）           │
│  K8s本体の開発・テスト → kind（K8s公式ツール）            │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 3. インストール

### 前提条件

- Docker がインストール・起動済みであること
- kubectl がインストール済みであること

### macOS

```bash
# Homebrew
brew install kind

# バージョン確認
kind version
```

### Linux

```bash
# バイナリダウンロード（AMD64）
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.25.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# ARM64
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.25.0/kind-linux-arm64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
```

### 動作確認

```bash
$ kind version
kind v0.25.0 go1.23.x ...

$ docker ps
# Docker が起動していることを確認
```

---

## 4. クラスターの作成と管理

### クラスター作成

```bash
# デフォルト設定で作成（シングルノード）
$ kind create cluster
Creating cluster "kind" ...
 ✓ Ensuring node image (kindest/node:v1.31.0)
 ✓ Preparing nodes
 ✓ Writing configuration
 ✓ Starting control-plane
 ✓ Installing CNI
 ✓ Installing StorageClass
Set kubectl context to "kind-kind"

# 名前を指定して作成
kind create cluster --name my-cluster

# K8sバージョンを指定
kind create cluster --image kindest/node:v1.30.0
```

### クラスター管理

```bash
# クラスター一覧
$ kind get clusters
kind
my-cluster

# クラスター情報
$ kubectl cluster-info --context kind-kind
Kubernetes control plane is running at https://127.0.0.1:6443
CoreDNS is running at https://127.0.0.1:6443/api/v1/...

# ノード確認
$ kubectl get nodes
NAME                 STATUS   ROLES           AGE   VERSION
kind-control-plane   Ready    control-plane   60s   v1.31.0

# クラスター削除
kind delete cluster
kind delete cluster --name my-cluster
```

### kubeconfig の切り替え

```bash
# kind が自動的に kubeconfig を設定
$ kubectl config get-contexts
CURRENT   NAME        CLUSTER     AUTHINFO    NAMESPACE
*         kind-kind   kind-kind   kind-kind

# 複数クラスター間の切り替え
kubectl config use-context kind-kind
kubectl config use-context kind-my-cluster
```

### Dockerコンテナとしての確認

```bash
# kindのノードはDockerコンテナ
$ docker ps
CONTAINER ID   IMAGE                  PORTS                       NAMES
a1b2c3d4e5f6   kindest/node:v1.31.0   127.0.0.1:6443->6443/tcp   kind-control-plane

# ノードコンテナに入る
docker exec -it kind-control-plane bash

# コンテナ内でcrictlでPodを確認
crictl ps
```

---

## 5. クラスター設定のカスタマイズ

### 設定ファイル（kind-config.yaml）

```yaml
# kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
networking:
  # Pod間通信のサブネット
  podSubnet: "10.244.0.0/16"
  # Serviceのサブネット
  serviceSubnet: "10.96.0.0/12"
```

```bash
# 設定ファイルを使ってクラスター作成
kind create cluster --config kind-config.yaml
```

### ポートマッピング

ホストマシンからクラスター内のサービスにアクセスするには、ポートマッピングを設定します。

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30000    # NodePort
        hostPort: 30000
        protocol: TCP
      - containerPort: 80       # Ingress HTTP
        hostPort: 80
        protocol: TCP
      - containerPort: 443      # Ingress HTTPS
        hostPort: 443
        protocol: TCP
```

### Feature Gate の有効化

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
featureGates:
  InPlacePodVerticalScaling: true    # VPA In-place Resize
nodes:
  - role: control-plane
```

---

## 6. ローカルイメージの利用

kindクラスターはDockerとは別のコンテナランタイム（containerd）を使うため、ホストのDockerイメージをそのままでは使えません。明示的にロードする必要があります。

### イメージのロード

```bash
# ローカルでビルド
docker build -t my-app:latest .

# kindクラスターにロード
kind load docker-image my-app:latest

# 名前付きクラスターの場合
kind load docker-image my-app:latest --name my-cluster
```

### ロードの確認

```bash
# ノード内でイメージを確認
docker exec -it kind-control-plane crictl images | grep my-app
```

### マニフェストでの指定

```yaml
spec:
  containers:
    - name: my-app
      image: my-app:latest
      imagePullPolicy: Never    # レジストリからpullしない
```

**重要**: `imagePullPolicy: Never` または `IfNotPresent` を指定すること。`Always` だとレジストリから取得しようとして失敗します。

---

## 7. Ingress の設定

### NGINX Ingress Controller のデプロイ

```yaml
# kind-config.yaml（Ingress用ポートマッピング）
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
```

```bash
# クラスター作成
kind create cluster --config kind-config.yaml

# NGINX Ingress Controller をデプロイ
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Ready になるまで待つ
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

### Ingress リソースの作成

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-app-ingress
spec:
  rules:
    - host: my-app.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: my-app
                port:
                  number: 8080
```

```bash
# /etc/hosts に追加
echo "127.0.0.1 my-app.local" | sudo tee -a /etc/hosts

# アクセス確認
curl http://my-app.local/
```

---

## 8. 永続化ボリューム

kindはデフォルトで `standard` StorageClass を提供しており、PersistentVolumeClaimを作成すると自動的にホストパスベースのPVが作られます。

### PVC の利用

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: my-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
  storageClassName: standard
```

### ホストディレクトリのマウント

ホストマシンのディレクトリをノードにマウントし、Podからアクセスすることもできます。

```yaml
# kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraMounts:
      - hostPath: /Users/me/data
        containerPath: /data
        readOnly: false
```

```yaml
# Pod からマウント
spec:
  containers:
    - name: my-app
      volumeMounts:
        - name: host-data
          mountPath: /app/data
  volumes:
    - name: host-data
      hostPath:
        path: /data
        type: Directory
```

---

## 9. マルチノードクラスター

kindの大きな利点の一つが、マルチノードクラスターを簡単に構築できることです。

### 設定例

```yaml
# kind-multi-node.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
  - role: worker
  - role: worker
  - role: worker
```

```bash
# マルチノードクラスター作成
$ kind create cluster --config kind-multi-node.yaml --name multi
Creating cluster "multi" ...
 ✓ Ensuring node image (kindest/node:v1.31.0)
 ✓ Preparing nodes
 ✓ Writing configuration
 ✓ Starting control-plane
 ✓ Installing CNI
 ✓ Installing StorageClass
 ✓ Joining worker nodes

$ kubectl get nodes
NAME                  STATUS   ROLES           AGE   VERSION
multi-control-plane   Ready    control-plane   60s   v1.31.0
multi-worker          Ready    <none>          40s   v1.31.0
multi-worker2         Ready    <none>          40s   v1.31.0
multi-worker3         Ready    <none>          40s   v1.31.0

$ docker ps
CONTAINER ID   IMAGE                  NAMES
a1b2c3d4e5f6   kindest/node:v1.31.0   multi-control-plane
b2c3d4e5f6a7   kindest/node:v1.31.0   multi-worker
c3d4e5f6a7b8   kindest/node:v1.31.0   multi-worker2
d4e5f6a7b8c9   kindest/node:v1.31.0   multi-worker3
```

### マルチノードの用途

| 用途 | 説明 |
|------|------|
| Pod分散テスト | `topologySpreadConstraints` や `podAntiAffinity` の動作確認 |
| ノード障害シミュレーション | `docker stop multi-worker` でノードダウンを再現 |
| DaemonSet テスト | 全ノードに1Podずつデプロイされることを確認 |
| ネットワークポリシー | ノード間通信の制御をテスト |

### ノード障害のシミュレーション

```bash
# ワーカーノードを停止
$ docker stop multi-worker
multi-worker

# ノードが NotReady になることを確認
$ kubectl get nodes
NAME                  STATUS     ROLES           AGE   VERSION
multi-control-plane   Ready      control-plane   5m    v1.31.0
multi-worker          NotReady   <none>          4m    v1.31.0
multi-worker2         Ready      <none>          4m    v1.31.0
multi-worker3         Ready      <none>          4m    v1.31.0

# Pod が他のノードに再スケジュールされることを確認
$ kubectl get pods -o wide

# ノードを復旧
$ docker start multi-worker
```

---

## 10. Metrics Server と HPA を試す

kindクラスターでHPA（Horizontal Pod Autoscaler）の動作を実際に観察します。

### Step 1: Metrics Server のインストール

kindではMetrics Serverのデフォルト設定だとTLS検証に失敗するため、`--kubelet-insecure-tls` フラグが必要です。

```bash
# Metrics Server をデプロイ（kind向け修正版）
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# kubelet-insecure-tls を追加
kubectl patch deployment metrics-server -n kube-system \
  --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]'

# 起動確認
kubectl wait --namespace kube-system \
  --for=condition=ready pod \
  --selector=k8s-app=metrics-server \
  --timeout=90s

# 動作確認
kubectl top nodes
```

### Step 2: テスト用アプリのデプロイ

```yaml
# stress-app.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: stress-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: stress-app
  template:
    metadata:
      labels:
        app: stress-app
    spec:
      containers:
        - name: stress-app
          image: registry.k8s.io/hpa-example
          ports:
            - containerPort: 80
          resources:
            requests:
              cpu: "200m"
            limits:
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: stress-app
spec:
  selector:
    app: stress-app
  ports:
    - port: 80
      targetPort: 80
```

```bash
kubectl apply -f stress-app.yaml
```

### Step 3: HPA の設定

```bash
kubectl autoscale deployment stress-app \
  --cpu-percent=50 \
  --min=1 \
  --max=5
```

### Step 4: 負荷をかけてPod増減を観察

ターミナルを3つ開き、同時に実行します。

```bash
# ターミナル1: HPAの状態をウォッチ
kubectl get hpa -w

# ターミナル2: Pod数をウォッチ
kubectl get pods -w

# ターミナル3: 負荷をかける
kubectl run -i --tty load-generator --rm \
  --image=busybox:1.36 \
  --restart=Never \
  -- /bin/sh -c "while sleep 0.01; do wget -q -O- http://stress-app; done"
```

### 観察結果の例

```
# ターミナル1（HPA）
NAME         REFERENCE               TARGETS    MINPODS   MAXPODS   REPLICAS
stress-app   Deployment/stress-app   0%/50%     1         5         1
stress-app   Deployment/stress-app   120%/50%   1         5         1
stress-app   Deployment/stress-app   120%/50%   1         5         3      ← スケールアウト
stress-app   Deployment/stress-app   65%/50%    1         5         3
stress-app   Deployment/stress-app   65%/50%    1         5         4      ← さらに追加
stress-app   Deployment/stress-app   45%/50%    1         5         4      ← 安定

# ターミナル2（Pod）
NAME                         READY   STATUS    AGE
stress-app-7b8f9c6d4-abc12   1/1     Running   2m
stress-app-7b8f9c6d4-def34   0/1     Pending   0s    ← HPA が追加
stress-app-7b8f9c6d4-def34   1/1     Running   3s
stress-app-7b8f9c6d4-ghi56   0/1     Pending   0s    ← さらに追加
stress-app-7b8f9c6d4-ghi56   1/1     Running   3s

# 負荷を止める（ターミナル3で Ctrl+C）
# → 5分後にスケールダウンが始まる
stress-app   Deployment/stress-app   0%/50%     1         5         4
stress-app   Deployment/stress-app   0%/50%     1         5         1      ← スケールダウン
```

### Step 5: 片付け

```bash
kubectl delete deployment stress-app
kubectl delete service stress-app
kubectl delete hpa stress-app
```

---

## 11. トラブルシューティング

### よくある問題と解決策

| 問題 | 原因 | 解決策 |
|------|------|--------|
| `kind create cluster` が失敗 | Docker が起動していない | `docker ps` で確認、Docker Desktop を起動 |
| `kind create cluster` が遅い | イメージの初回ダウンロード | `docker pull kindest/node:v1.31.0` で事前取得 |
| イメージが Pull できない | `imagePullPolicy: Always` になっている | `imagePullPolicy: Never` に変更し `kind load` を使う |
| Metrics Server が動かない | TLS検証エラー | `--kubelet-insecure-tls` を追加 |
| ポートフォワードが動かない | ポートが既に使われている | `lsof -i :PORT` で確認 |
| ノードが NotReady | リソース不足 | `docker stats` でメモリ確認、Docker Desktop のメモリ割当を増やす |
| PVC が Pending | StorageClass 未設定 | `storageClassName: standard` を指定 |

### デバッグコマンド

```bash
# クラスターのログ取得
kind export logs --name my-cluster ./kind-logs

# ノードコンテナの状態
docker inspect kind-control-plane

# ノード内のログ確認
docker exec -it kind-control-plane journalctl -u kubelet --no-pager -n 50

# 全Podのイベント確認
kubectl get events --sort-by='.lastTimestamp'
```

---

## 12. IDサービスでの活用

idp-server では、Docker Compose に加えて kind を使ったローカル K8s 環境を提供しています。
HPA やReadiness/Liveness Probe など、Kubernetes 固有の機能をローカルで検証できます。

セットアップ手順・構成・トラブルシューティングは **[Getting Started（kind / Kubernetes）](../../content_02_quickstart/quickstart-01-kind-getting-started.md)** を参照してください。

---

## 13. まとめ

- **kind** は Docker コンテナをノードとして使い、ローカルに完全な Kubernetes クラスターを構築するツール
- **起動が速く**（20〜40秒）、**CI/CD に最適**。Kubernetes公式プロジェクトのテストにも使われている
- **マルチノードクラスター**をローカルで簡単に構築でき、Pod分散やノード障害をシミュレーションできる
- **ローカルイメージ**は `kind load docker-image` でロードし、`imagePullPolicy: Never` で使用する
- **Metrics Server + HPA** をインストールすれば、オートスケーリングの動作をローカルで観察できる
- minikube と比べて VM を使わないため軽量だが、LoadBalancer や GPU パススルーには追加設定が必要

## 次のステップ
- [Kubernetes アーキテクチャ](./kubernetes-architecture.md) - K8sの構造を理解する
- [Kubernetes ワークロード](./kubernetes-workloads.md) - Pod、Deploymentの詳細
- [Kubernetes スケーリング](./kubernetes-scaling.md) - HPA/VPAの詳細

## 参考リソース
- [kind 公式ドキュメント](https://kind.sigs.k8s.io/)
- [kind クイックスタート](https://kind.sigs.k8s.io/docs/user/quick-start/)
- [kind 設定リファレンス](https://kind.sigs.k8s.io/docs/user/configuration/)
- [Kubernetes公式 HPA チュートリアル](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/)
