# Kubernetes ネットワーキング

KubernetesのService、Ingress、NetworkPolicyを学び、アプリケーションへのアクセス制御とルーティングを理解します。

---

## 目次

1. [ネットワークモデル](#ネットワークモデル)
2. [Service](#service)
3. [Ingress](#ingress)
4. [NetworkPolicy](#networkpolicy)
5. [DNS](#dns)
6. [IDサービスでの構成例](#idサービスでの構成例)

---

## ネットワークモデル

### 基本原則

Kubernetesネットワークは以下の原則に基づいています:

```
┌─────────────────────────────────────────────────────────────┐
│             Kubernetes ネットワーキング原則                   │
├─────────────────────────────────────────────────────────────┤
│  1. 全てのPodは一意のIPアドレスを持つ                         │
│  2. 全てのPodはNATなしで他のPodと通信可能                     │
│  3. 全てのNodeは全てのPodと通信可能                           │
│  4. Podが自身のIPとして認識するIPと、他から見えるIPが同一      │
└─────────────────────────────────────────────────────────────┘
```

### ネットワーク階層

```
┌─────────────────────────────────────────────────────────────┐
│                     外部トラフィック                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Ingress / LoadBalancer                     │
│                  (L7 / L4 ロードバランサー)                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        Service                               │
│              (クラスター内部のロードバランシング)              │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
     ┌─────────┐         ┌─────────┐         ┌─────────┐
     │   Pod   │         │   Pod   │         │   Pod   │
     │10.0.1.1 │         │10.0.1.2 │         │10.0.2.1 │
     └─────────┘         └─────────┘         └─────────┘
```

---

## Service

### Serviceの種類

```
┌─────────────────────────────────────────────────────────────┐
│                      Service Types                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ClusterIP (デフォルト)                                      │
│  └── クラスター内部からのみアクセス可能                       │
│  └── 内部マイクロサービス間通信                               │
│                                                              │
│  NodePort                                                    │
│  └── 各ノードの特定ポートで公開                               │
│  └── ノードIP:ポートでアクセス                                │
│  └── ポート範囲: 30000-32767                                 │
│                                                              │
│  LoadBalancer                                                │
│  └── 外部ロードバランサーを使用                               │
│  └── クラウドプロバイダの機能を使用                           │
│  └── 外部IPアドレスが自動割り当て                            │
│                                                              │
│  ExternalName                                                │
│  └── 外部サービスへのCNAMEエイリアス                         │
│  └── DNSレベルでのリダイレクト                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ClusterIP Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: idp-server
spec:
  type: ClusterIP  # デフォルト
  selector:
    app: idp-server
  ports:
    - name: http
      port: 80           # Serviceがリッスンするポート
      targetPort: 8080   # Pod側のポート
      protocol: TCP
```

```
クラスター内部:
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  他のPod ──► idp-server:80 ──► ClusterIP ──► Pod:8080       │
│              (Service名)      (10.96.x.x)                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### NodePort Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: idp-server-nodeport
spec:
  type: NodePort
  selector:
    app: idp-server
  ports:
    - port: 80
      targetPort: 8080
      nodePort: 30080    # 省略時は自動割り当て（30000-32767）
```

```
外部アクセス:
┌─────────────────────────────────────────────────────────────┐
│  クライアント                                                │
│       │                                                      │
│       ▼                                                      │
│  NodeIP:30080（どのノードでもOK）                            │
│       │                                                      │
│       ▼                                                      │
│  Service → Pod:8080                                          │
└─────────────────────────────────────────────────────────────┘
```

### LoadBalancer Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: idp-server-lb
  annotations:
    # AWS NLB を使用
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-internal: "false"
spec:
  type: LoadBalancer
  selector:
    app: idp-server
  ports:
    - port: 443
      targetPort: 8080
  # ソースIPの保持
  externalTrafficPolicy: Local
```

```
外部アクセス:
┌─────────────────────────────────────────────────────────────┐
│  クライアント                                                │
│       │                                                      │
│       ▼                                                      │
│  Cloud LoadBalancer (External IP)                           │
│       │                                                      │
│       ▼                                                      │
│  NodePort → Service → Pod                                    │
└─────────────────────────────────────────────────────────────┘
```

### Headless Service

StatefulSetやPodの直接アクセスに使用します。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-headless
spec:
  clusterIP: None  # Headless
  selector:
    app: postgres
  ports:
    - port: 5432
```

```
DNS解決:
postgres-headless → [postgres-0.IP, postgres-1.IP, postgres-2.IP]
postgres-0.postgres-headless → postgres-0のIP
```

---

## Ingress

### 概要

IngressはL7（HTTP/HTTPS）レベルでのルーティングを提供します。

```
┌─────────────────────────────────────────────────────────────┐
│                        Ingress                               │
│                                                              │
│  機能:                                                       │
│  - ホスト名ベースのルーティング                               │
│  - パスベースのルーティング                                   │
│  - TLS終端                                                   │
│  - 負荷分散                                                  │
│                                                              │
│  example.com           api.example.com                       │
│       │                      │                               │
│       ▼                      ▼                               │
│  ┌────────────────────────────────────────┐                 │
│  │              Ingress Controller         │                 │
│  │         (NGINX / ALB / Traefik)         │                 │
│  └────────────────────────────────────────┘                 │
│       │                      │                               │
│       ▼                      ▼                               │
│   frontend-svc            api-svc                            │
└─────────────────────────────────────────────────────────────┘
```

### Ingress定義（NGINX）

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: idp-ingress
  annotations:
    # NGINX Ingress Controller用
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
spec:
  ingressClassName: nginx

  # TLS設定
  tls:
    - hosts:
        - idp.example.com
      secretName: idp-tls-secret

  rules:
    # ホスト名ベースのルーティング
    - host: idp.example.com
      http:
        paths:
          # パスベースのルーティング
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: idp-api
                port:
                  number: 80

          - path: /
            pathType: Prefix
            backend:
              service:
                name: idp-frontend
                port:
                  number: 80
```

### AWS ALB Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: idp-alb-ingress
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:...
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/ssl-redirect: '443'
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
spec:
  rules:
    - host: idp.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: idp-server
                port:
                  number: 80
```

### TLS Secret

```bash
# TLS Secretの作成
kubectl create secret tls idp-tls-secret \
  --cert=cert.pem \
  --key=key.pem
```

```yaml
# または YAML で定義
apiVersion: v1
kind: Secret
metadata:
  name: idp-tls-secret
type: kubernetes.io/tls
data:
  tls.crt: <base64-encoded-cert>
  tls.key: <base64-encoded-key>
```

---

## NetworkPolicy

### 概要

NetworkPolicyは、Pod間のネットワークトラフィックを制御します。

```
┌─────────────────────────────────────────────────────────────┐
│                     NetworkPolicy                            │
│                                                              │
│  デフォルト: 全トラフィック許可                               │
│  NetworkPolicy適用後: 明示的に許可されたもののみ通過          │
│                                                              │
│  制御対象:                                                    │
│  - Ingress（入力トラフィック）                                │
│  - Egress（出力トラフィック）                                 │
│                                                              │
│  選択基準:                                                    │
│  - Podセレクター                                              │
│  - Namespaceセレクター                                        │
│  - IPブロック（CIDR）                                         │
└─────────────────────────────────────────────────────────────┘
```

### デフォルト拒否ポリシー

```yaml
# 全ての入力トラフィックを拒否
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  namespace: production
spec:
  podSelector: {}  # 全てのPodに適用
  policyTypes:
    - Ingress

---
# 全ての出力トラフィックを拒否
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-egress
  namespace: production
spec:
  podSelector: {}
  policyTypes:
    - Egress
```

### 特定トラフィックの許可

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: idp-server-policy
  namespace: production
spec:
  podSelector:
    matchLabels:
      app: idp-server
  policyTypes:
    - Ingress
    - Egress

  # 入力許可ルール
  ingress:
    # Ingressコントローラーからのトラフィック
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - port: 8080
          protocol: TCP

    # 同じNamespace内のfrontendからのトラフィック
    - from:
        - podSelector:
            matchLabels:
              app: frontend
      ports:
        - port: 8080

  # 出力許可ルール
  egress:
    # データベースへのアクセス
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - port: 5432

    # Redisへのアクセス
    - to:
        - podSelector:
            matchLabels:
              app: redis
      ports:
        - port: 6379

    # 外部DNS（kube-dns）
    - to:
        - namespaceSelector: {}
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - port: 53
          protocol: UDP
        - port: 53
          protocol: TCP
```

### ポリシー構成例

```
┌─────────────────────────────────────────────────────────────┐
│                      production namespace                    │
│                                                              │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐               │
│  │ ingress │ ──► │   idp   │ ──► │postgres │               │
│  │ nginx   │     │ server  │     │         │               │
│  └─────────┘     └────┬────┘     └─────────┘               │
│                       │                                      │
│                       ▼                                      │
│                  ┌─────────┐                                │
│                  │  redis  │                                │
│                  └─────────┘                                │
│                                                              │
│  NetworkPolicy:                                             │
│  - ingress-nginx → idp-server:8080 ✓                        │
│  - idp-server → postgres:5432 ✓                             │
│  - idp-server → redis:6379 ✓                                │
│  - その他 → idp-server ✗                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## DNS

### クラスター内DNS

```
┌─────────────────────────────────────────────────────────────┐
│                   Kubernetes DNS                             │
│                                                              │
│  Service DNS:                                                │
│  <service>.<namespace>.svc.cluster.local                     │
│                                                              │
│  例:                                                         │
│  idp-server                        → 同じNamespace          │
│  idp-server.production             → 異なるNamespace        │
│  idp-server.production.svc.cluster.local → 完全修飾名       │
│                                                              │
│  StatefulSet Pod DNS:                                        │
│  <pod-name>.<service>.<namespace>.svc.cluster.local          │
│                                                              │
│  例:                                                         │
│  postgres-0.postgres-headless.production.svc.cluster.local   │
└─────────────────────────────────────────────────────────────┘
```

### DNS設定のカスタマイズ

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: custom-dns-pod
spec:
  containers:
    - name: app
      image: my-app
  dnsPolicy: "None"  # カスタムDNS設定を使用
  dnsConfig:
    nameservers:
      - 10.96.0.10
    searches:
      - production.svc.cluster.local
      - svc.cluster.local
      - cluster.local
    options:
      - name: ndots
        value: "5"
```

---

## IDサービスでの構成例

### 完全な構成例

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: idp-system
  labels:
    name: idp-system

---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: idp-server
  namespace: idp-system
spec:
  type: ClusterIP
  selector:
    app: idp-server
  ports:
    - name: http
      port: 80
      targetPort: 8080
    - name: management
      port: 9090
      targetPort: 9090

---
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: idp-ingress
  namespace: idp-system
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - auth.example.com
      secretName: idp-tls
  rules:
    - host: auth.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: idp-server
                port:
                  number: 80

---
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: idp-server-network-policy
  namespace: idp-system
spec:
  podSelector:
    matchLabels:
      app: idp-server
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Ingress Controllerから
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - port: 8080
    # Prometheus（メトリクス収集）
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - port: 9090
  egress:
    # PostgreSQL
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - port: 5432
    # Redis
    - to:
        - podSelector:
            matchLabels:
              app: redis
      ports:
        - port: 6379
    # DNS
    - to:
        - namespaceSelector: {}
      ports:
        - port: 53
          protocol: UDP
    # 外部API（HTTPS）
    - to:
        - ipBlock:
            cidr: 0.0.0.0/0
      ports:
        - port: 443
```

---

## まとめ

### Serviceタイプの選択

| タイプ | 用途 | アクセス元 |
|-------|------|----------|
| ClusterIP | 内部通信 | クラスター内のみ |
| NodePort | 開発・テスト | NodeIP:Port |
| LoadBalancer | 本番公開 | 外部LB経由 |
| ExternalName | 外部サービス参照 | DNS CNAME |

### 次のステップ

- [Kubernetesストレージ](kubernetes-storage.md) - PV、PVC
- [Kubernetes設定管理](kubernetes-configuration.md) - ConfigMap、Secret
- [Kubernetesセキュリティ](kubernetes-security.md) - RBAC、PodSecurity

---

## 参考リソース

- [Kubernetes Services](https://kubernetes.io/docs/concepts/services-networking/service/)
- [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)
- [Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/)
- [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)
