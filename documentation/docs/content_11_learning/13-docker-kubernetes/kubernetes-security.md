# Kubernetes セキュリティ

RBAC、Pod Security Standards、ネットワークポリシーなど、Kubernetesのセキュリティ機能を学びます。

---

## 目次

1. [セキュリティの階層](#セキュリティの階層)
2. [RBAC](#rbac)
3. [ServiceAccount](#serviceaccount)
4. [Pod Security Standards](#pod-security-standards)
5. [Secrets管理](#secrets管理)
6. [イメージセキュリティ](#イメージセキュリティ)
7. [IDサービスでの構成例](#idサービスでの構成例)

---

## セキュリティの階層

```
┌─────────────────────────────────────────────────────────────┐
│                  Kubernetesセキュリティ階層                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クラスターレベル                                             │
│  ├── ネットワークポリシー（Pod間通信制御）                    │
│  ├── RBAC（APIアクセス制御）                                 │
│  └── Admission Controller（リソース検証）                    │
│                                                              │
│  ノードレベル                                                │
│  ├── カーネルハードニング                                    │
│  ├── コンテナランタイムセキュリティ                          │
│  └── Kubelet認証                                            │
│                                                              │
│  Podレベル                                                   │
│  ├── Pod Security Standards                                  │
│  ├── SecurityContext                                         │
│  └── Service Account                                         │
│                                                              │
│  コンテナレベル                                              │
│  ├── イメージスキャン                                        │
│  ├── 読み取り専用ファイルシステム                            │
│  └── リソース制限                                            │
│                                                              │
│  アプリケーションレベル                                       │
│  ├── シークレット管理                                        │
│  ├── TLS通信                                                │
│  └── 認証・認可                                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## RBAC

### 基本概念

```
┌─────────────────────────────────────────────────────────────┐
│                         RBAC                                 │
│                                                              │
│  Subject（誰が）                                             │
│  ├── User（ユーザー）                                        │
│  ├── Group（グループ）                                       │
│  └── ServiceAccount（サービスアカウント）                    │
│                                                              │
│           │                                                  │
│           ▼                                                  │
│  RoleBinding / ClusterRoleBinding（紐付け）                  │
│           │                                                  │
│           ▼                                                  │
│  Role / ClusterRole（何ができるか）                          │
│  └── Resources + Verbs                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Role と ClusterRole

```yaml
# Role: Namespace内のリソースに対する権限
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
  namespace: production
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "watch", "list"]
  - apiGroups: [""]
    resources: ["pods/log"]
    verbs: ["get"]

---
# ClusterRole: クラスター全体のリソースに対する権限
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: secret-reader
rules:
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get", "watch", "list"]
```

### RoleBinding と ClusterRoleBinding

```yaml
# RoleBinding: RoleをSubjectに紐付け
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: read-pods
  namespace: production
subjects:
  # ServiceAccountへの紐付け
  - kind: ServiceAccount
    name: idp-server
    namespace: production
  # Userへの紐付け
  - kind: User
    name: developer@example.com
    apiGroup: rbac.authorization.k8s.io
  # Groupへの紐付け
  - kind: Group
    name: developers
    apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io

---
# ClusterRoleBinding: クラスター全体での紐付け
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: read-secrets-global
subjects:
  - kind: ServiceAccount
    name: monitoring
    namespace: monitoring
roleRef:
  kind: ClusterRole
  name: secret-reader
  apiGroup: rbac.authorization.k8s.io
```

### よく使うVerbs

| Verb | 説明 |
|------|------|
| get | 単一リソースの取得 |
| list | リソース一覧の取得 |
| watch | リソースの変更監視 |
| create | リソースの作成 |
| update | リソースの更新 |
| patch | リソースの部分更新 |
| delete | リソースの削除 |

### 権限の確認

```bash
# 自分の権限確認
kubectl auth can-i create pods
kubectl auth can-i delete pods --namespace production

# 他のユーザーの権限確認（管理者のみ）
kubectl auth can-i create pods --as developer@example.com

# ServiceAccountの権限確認
kubectl auth can-i list secrets --as system:serviceaccount:production:idp-server

# 全権限の一覧
kubectl auth can-i --list
```

---

## ServiceAccount

### 基本設定

```yaml
# ServiceAccount作成
apiVersion: v1
kind: ServiceAccount
metadata:
  name: idp-server
  namespace: production
automountServiceAccountToken: false  # 明示的に無効化

---
# Podでの使用
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
spec:
  template:
    spec:
      serviceAccountName: idp-server
      automountServiceAccountToken: false  # トークン不要な場合

      containers:
        - name: idp-server
          image: my-idp-server:latest
```

### AWS IRSA（IAM Roles for Service Accounts）

```yaml
# EKSでAWSリソースにアクセスする場合
apiVersion: v1
kind: ServiceAccount
metadata:
  name: idp-server
  namespace: production
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/idp-server-role

---
# 対応するIAMロール（Terraform）
# resource "aws_iam_role" "idp_server" {
#   name = "idp-server-role"
#   assume_role_policy = data.aws_iam_policy_document.assume_role.json
# }
```

---

## Pod Security Standards

### セキュリティレベル

```
┌─────────────────────────────────────────────────────────────┐
│                 Pod Security Standards                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Privileged（特権）                                          │
│  └── 制限なし                                                │
│  └── 信頼されたワークロードのみ                               │
│                                                              │
│  Baseline（基本）                                            │
│  └── 既知の特権昇格を防止                                    │
│  └── hostNetwork, hostPID, hostIPC 禁止                     │
│  └── privileged コンテナ禁止                                 │
│                                                              │
│  Restricted（制限）                                          │
│  └── 最小権限の原則を適用                                    │
│  └── 非rootユーザーでの実行を強制                            │
│  └── 特定のCapabilityのみ許可                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Namespace への適用

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: production
  labels:
    # Pod Security Standards を適用
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/enforce-version: latest
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/audit-version: latest
    pod-security.kubernetes.io/warn: restricted
    pod-security.kubernetes.io/warn-version: latest
```

### SecurityContext

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
spec:
  template:
    spec:
      # Pod レベルの SecurityContext
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault

      containers:
        - name: idp-server
          image: my-idp-server:latest

          # コンテナレベルの SecurityContext
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
              # 必要な場合のみ追加
              # add:
              #   - NET_BIND_SERVICE

          volumeMounts:
            # 書き込み可能な一時ディレクトリ
            - name: tmp
              mountPath: /tmp
            - name: logs
              mountPath: /app/logs

      volumes:
        - name: tmp
          emptyDir: {}
        - name: logs
          emptyDir: {}
```

---

## Secrets管理

### ベストプラクティス

```
┌─────────────────────────────────────────────────────────────┐
│                   Secrets管理のベストプラクティス              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. GitにSecretsをコミットしない                             │
│     └── .gitignore に追加                                   │
│     └── Sealed Secrets / SOPS を使用                        │
│                                                              │
│  2. 外部シークレットマネージャーを使用                        │
│     └── AWS Secrets Manager                                 │
│     └── HashiCorp Vault                                     │
│     └── External Secrets Operator                           │
│                                                              │
│  3. etcdの暗号化を有効化                                     │
│     └── EncryptionConfiguration                              │
│                                                              │
│  4. RBACでSecretsへのアクセスを制限                          │
│                                                              │
│  5. 環境変数よりボリュームマウントを推奨                      │
│     └── プロセス一覧で環境変数が見える可能性                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Sealed Secrets

```bash
# Sealed Secrets コントローラーのインストール
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# kubesealでシークレットを暗号化
kubectl create secret generic my-secret \
  --from-literal=password=supersecret \
  --dry-run=client -o yaml | \
  kubeseal --format yaml > sealed-secret.yaml

# 暗号化されたシークレットは安全にGitにコミット可能
```

```yaml
# sealed-secret.yaml（暗号化済み）
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: my-secret
  namespace: production
spec:
  encryptedData:
    password: AgBy3i...（暗号化されたデータ）
```

---

## イメージセキュリティ

### イメージスキャン

```bash
# Trivyでスキャン
trivy image my-idp-server:latest

# 重大な脆弱性のみ
trivy image --severity HIGH,CRITICAL my-idp-server:latest

# CI/CDでの使用
trivy image --exit-code 1 --severity CRITICAL my-idp-server:latest
```

### イメージポリシー

```yaml
# Kyverno ポリシー例
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: require-image-signature
spec:
  validationFailureAction: enforce
  rules:
    - name: verify-signature
      match:
        any:
          - resources:
              kinds:
                - Pod
      verifyImages:
        - imageReferences:
            - "ghcr.io/myorg/*"
          attestors:
            - entries:
                - keys:
                    publicKeys: |
                      -----BEGIN PUBLIC KEY-----
                      ...
                      -----END PUBLIC KEY-----
```

### ベースイメージの選択

```dockerfile
# 推奨: Distroless（最小攻撃面）
FROM gcr.io/distroless/java21-debian12

# 推奨: Alpine（軽量）
FROM eclipse-temurin:21-jre-alpine

# 非推奨: フルOS
# FROM ubuntu:22.04
```

---

## IDサービスでの構成例

### 完全なセキュリティ構成

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: idp-system
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/enforce-version: latest

---
# serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: idp-server
  namespace: idp-system
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/idp-server-role

---
# role.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: idp-server-role
  namespace: idp-system
rules:
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["secrets"]
    resourceNames: ["idp-config"]
    verbs: ["get"]

---
# rolebinding.yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: idp-server-rolebinding
  namespace: idp-system
subjects:
  - kind: ServiceAccount
    name: idp-server
    namespace: idp-system
roleRef:
  kind: Role
  name: idp-server-role
  apiGroup: rbac.authorization.k8s.io

---
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
  namespace: idp-system
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
      serviceAccountName: idp-server
      automountServiceAccountToken: false

      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault

      containers:
        - name: idp-server
          image: ghcr.io/myorg/idp-server:v1.0.0@sha256:abc123...
          imagePullPolicy: Always

          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL

          ports:
            - name: http
              containerPort: 8080

          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"

          volumeMounts:
            - name: tmp
              mountPath: /tmp
            - name: secrets
              mountPath: /app/secrets
              readOnly: true

          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "kubernetes,production"

      volumes:
        - name: tmp
          emptyDir: {}
        - name: secrets
          secret:
            secretName: idp-secrets
            defaultMode: 0400

      imagePullSecrets:
        - name: ghcr-credentials

---
# networkpolicy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: idp-server-policy
  namespace: idp-system
spec:
  podSelector:
    matchLabels:
      app: idp-server
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - port: 8080
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - port: 5432
    - to:
        - namespaceSelector: {}
      ports:
        - port: 53
          protocol: UDP
```

---

## まとめ

### セキュリティチェックリスト

- [ ] RBACで最小権限の原則を適用
- [ ] Pod Security Standardsを有効化
- [ ] 非rootユーザーでコンテナを実行
- [ ] readOnlyRootFilesystemを有効化
- [ ] NetworkPolicyを設定
- [ ] Secretsを外部マネージャーで管理
- [ ] イメージスキャンをCI/CDに組み込み
- [ ] イメージ署名を検証

### 次のステップ

- [kubectlコマンドリファレンス](kubectl-commands.md) - よく使うコマンド集

---

## 参考リソース

- [Kubernetes Security](https://kubernetes.io/docs/concepts/security/)
- [RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
- [Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/)
- [Sealed Secrets](https://sealed-secrets.netlify.app/)
- [Kyverno](https://kyverno.io/)
- [Trivy](https://trivy.dev/)
