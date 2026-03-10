# Getting Started（kind / Kubernetes）

Docker Compose の代わりに **kind（Kubernetes IN Docker）** を使って idp-server をローカルで起動する手順です。

HPA（水平Pod自動スケーリング）や Readiness/Liveness Probe など、Kubernetes 固有の機能をローカルで検証できます。

## Docker Compose との比較

| 確認項目 | docker compose | kind |
|---------|----------------|------|
| アプリの起動確認 | `docker compose up` | `bash k8s/local/up-app-only.sh` |
| HPA の動作確認 | - | Pod 自動スケーリング |
| Readiness/Liveness Probe | - | Pod ヘルスチェック |
| Pod 分散・障害復旧 | - | Deployment による自動復旧 |
| FIDO2/MFA（ブラウザUI） | OK | OK |

## 構成

idp-server のみ kind クラスターにデプロイし、DB/Redis/nginx 等は Docker Compose で動かすハイブリッド構成です。

```
┌─── ホストマシン ──────────────────────────────────────────────┐
│                                                               │
│  ブラウザ                                                     │
│    │                                                          │
│    ▼                                                          │
│  ┌─ Docker Compose (docker-compose-kind.yaml) ─────────────┐ │
│  │                                                          │ │
│  │  nginx (:443)  ──────────────────┐                       │ │
│  │  app-view (:3000)                │                       │ │
│  │  sample-web (:3010)              │                       │ │
│  │  mockoon (:4000)                 │                       │ │
│  │                                  ▼                       │ │
│  │  ┌─ kind cluster (idp-local) ──────────────────────┐    │ │
│  │  │                                                  │    │ │
│  │  │  NodePort :30080 → host :8080                   │    │ │
│  │  │                                                  │    │ │
│  │  │  ┌─ idp-server Pod 1 ─┐  ┌─ idp-server Pod 2 ─┐│    │ │
│  │  │  │  :8080              │  │  :8080              ││    │ │
│  │  │  └─────────────────────┘  └─────────────────────┘│    │ │
│  │  │                                                  │    │ │
│  │  │  HPA: CPU 70%, min=1, max=3                     │    │ │
│  │  │  Metrics Server                                  │    │ │
│  │  └──────────────────────────────────────────────────┘    │ │
│  │          │                                               │ │
│  │          │ host.docker.internal                          │ │
│  │          ▼                                               │ │
│  │  postgres-primary (:5432)                                │ │
│  │  postgres-replica (:5433)                                │ │
│  │  redis (:6379)                                           │ │
│  └──────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

## 前提条件

[Docker Compose 版の Getting Started](./quickstart-01-getting-started.md) の「前提条件」「サブドメイン設定」「環境変数の設定」が完了していること。

追加で必要なツール:

| ツール | インストール |
|-------|-------------|
| **kind** | `brew install kind` |
| **kubectl** | `brew install kubectl` |
| **python3** | macOS 標準搭載（`status.sh` の JSON 解析で使用） |

## ファイル構成

```
k8s/local/
├── up-app-only.sh           # 起動（1コマンド）
├── down-app-only.sh         # 停止
├── status.sh                # 状態確認
├── kind-config.yaml         # kind クラスター設定
├── nginx-kind.conf          # nginx 設定（→ kind NodePort）
└── manifests/
    ├── namespace.yaml        # idp namespace
    └── idp-server.yaml       # Deployment + Service(NodePort) + HPA

docker-compose-kind.yaml      # DB/Redis/nginx/UI（Docker Compose側）
```

## クイックスタート

### 起動

```bash
bash k8s/local/up-app-only.sh
```

このスクリプトが以下をすべて自動で行います:

| Step | 処理 | 詳細 |
|------|------|------|
| 1 | 前提条件チェック | kind, kubectl, docker, .env, TLS証明書 |
| 2 | Docker Compose 起動 | `docker-compose-kind.yaml` で DB/Redis/nginx/UI を起動 |
| 3 | .env 読み込み | 環境変数を source |
| 4 | kind クラスター作成 | 既存ならスキップ、kubeconfig を export |
| 5 | idp-server イメージビルド | `docker build` → `kind load docker-image` |
| 6 | Kubernetes リソース作成 | Secret（.env から動的生成）、ConfigMap（.env + kind用DB/Redis接続先）|
| 7 | idp-server デプロイ | Deployment + NodePort Service + HPA |
| 8 | hostAliases 設定 | Pod 内から `api.local.test` 等を名前解決するための `/etc/hosts` 設定 |
| 9 | Metrics Server | HPA 用の CPU/メモリメトリクス収集 |

### 状態確認

```bash
bash k8s/local/status.sh
```

### ヘルスチェック

```bash
# NodePort 直接
curl http://localhost:8080/actuator/health

# nginx 経由
curl -k https://api.local.test/actuator/health
```

### Kubernetes リソース確認

```bash
# Pod 確認
kubectl get pods -n idp

# HPA 確認
kubectl get hpa -n idp

# ログ確認
kubectl logs -n idp -l app=idp-server -f

# メトリクス確認（起動後 1-2 分で利用可能）
kubectl top pods -n idp
```

### 停止

```bash
bash k8s/local/down-app-only.sh
```

## Docker Compose との主な差分

| 項目 | Docker Compose | kind |
|------|---------------|------|
| DB/Redis 接続先 | `postgres-primary` / `redis` | `host.docker.internal:5432` / `host.docker.internal:6379` |
| TLS 証明書 | volume mount | Secret → Pod にマウント |
| ローカルドメイン解決 | `extra_hosts: host-gateway` | `hostAliases`（動的 IP 解決） |
| ConfigMap/Secret | `.env` → Docker Compose 変数展開 | `.env` → `kubectl create configmap/secret` |

## ネットワーク経路と性能特性

### Docker Compose のネットワーク経路

Docker Compose では、nginx から idp-server へのリクエストは Docker ブリッジネットワーク1回で到達します。

```
クライアント
  │
  ▼
nginx (:443)
  │
  │  Docker bridge network（1ホップ）
  ▼
idp-server コンテナ (:8080)
```

### kind のネットワーク経路

kind では、kind ノード自体が Docker コンテナであるため、ネットワークが二重になります。

```
クライアント
  │
  ▼
nginx (:443)
  │
  │  ① Docker bridge network
  ▼
kind ノードコンテナ (:30080 NodePort)
  │
  │  ② kube-proxy (iptables NAT)
  ▼
Pod ネットワーク (veth)
  │
  │  ③ Pod 内部
  ▼
idp-server Pod (:8080)
```

毎リクエストごとに ①Docker NAT → ②iptables ルール評価 → ③Pod ネットワーク を通過するため、Docker Compose と比べてリクエストあたりのレイテンシが増加します。負荷試験では、このオーバーヘッドが TPS の低下として顕著に現れます。

### 本番環境（EKS）のネットワーク経路

EKS では AWS VPC CNI プラグインにより、Pod が VPC のネイティブ IP アドレスを直接持ちます。kind のような二重 NAT は発生しません。

```
クライアント (インターネット)
  │
  ▼
ALB (Application Load Balancer)
  │
  │  ① VPC ネットワーク（ターゲットグループ → Pod IP 直接）
  ▼
EC2 ノード (EKS ワーカー)
  │
  │  ② ENI (Elastic Network Interface) → Pod に直結
  ▼
idp-server Pod (:8080)
  │
  │  ③ VPC ネットワーク（Pod IP → RDS/ElastiCache エンドポイント）
  ▼
RDS (PostgreSQL) / ElastiCache (Redis)
```

**ポイント:**

- **ALB → Pod 直接ルーティング**: AWS ALB の IP ターゲットモードでは、ALB が Pod の VPC IP に直接リクエストを送る。kube-proxy (iptables) を経由しない
- **ENI 直結**: AWS VPC CNI が各 Pod に ENI のセカンダリ IP を割り当てるため、Pod は VPC ネットワークに直接参加する。Docker NAT やブリッジネットワークを介さない
- **RDS/ElastiCache へのアクセス**: Pod から RDS・ElastiCache への通信も VPC 内で完結し、NAT 変換なし

### kind vs EKS 比較

| | kind（ローカル） | EKS（本番） |
|---|---|---|
| ノード | Docker コンテナ内で K8s を実行（二重NAT） | EC2 インスタンス（専用VM、NAT なし） |
| Pod ネットワーク | veth + bridge（Docker 内） | VPC ネイティブ（ENI 直結） |
| LB → Pod | nginx → Docker NAT → iptables → Pod | ALB → Pod IP 直接（IP ターゲット） |
| DB/Cache 接続 | Pod → host.docker.internal（NAT経由） | Pod → RDS/ElastiCache（VPC 内直接） |
| kube-proxy | iptables（シングルノード） | iptables or eBPF（ノード分散） |

kind の性能劣化は **kind 固有** であり、EKS では発生しません。

**kind の用途は HPA やマニフェストの動作確認であり、負荷試験は Docker Compose または EKS 上で行ってください。**

## HPA の動作確認

kind 環境では HPA によるオートスケーリングをローカルで確認できます。

```bash
# HPA の状態をウォッチ
kubectl get hpa -n idp -w

# 別ターミナルで負荷をかける（例: k6 ストレステスト）
k6 run ./performance-test/stress/scenario-5-token-client-credentials.js

# Pod 数が自動で増減するのを観察
kubectl get pods -n idp -w
```

## トラブルシューティング

| 問題 | 原因 | 解決策 |
|------|------|--------|
| `ErrImageNeverPull` | kind にイメージが未ロード | `kind load docker-image --name idp-local idp-server:latest` |
| `context "kind-idp-local" does not exist` | kubeconfig 消失 | `kind export kubeconfig --name idp-local` |
| `PKIX path building failed` | rootCA 未信頼 | Secret `root-ca-cert` が作成されているか確認 |
| `ConnectException: null` (api.local.test) | DNS 解決不可 | hostAliases が設定されているか `kubectl exec POD -- cat /etc/hosts` で確認 |
| Docker ディスク不足 | kind ノードイメージが大きい | `docker system prune -a` で不要イメージ削除 |
| `docker compose --wait` がハング | ワンショットコンテナに healthcheck 無し | `up-app-only.sh` は `--wait` 不使用（個別に待機） |

## 次のステップ

- [設定テンプレート](./quickstart-02-setting-templates.md) - テンプレートを使ったセットアップ
- [主要ユースケース](./quickstart-03-common-use-cases.md) - ログイン、MFA、パスワードレス等
- [kind の仕組みを学ぶ](../content_11_learning/13-docker-kubernetes/kind-local-cluster.md) - kind の基礎知識
