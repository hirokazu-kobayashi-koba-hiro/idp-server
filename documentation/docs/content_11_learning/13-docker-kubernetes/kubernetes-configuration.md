# Kubernetes 設定管理

ConfigMapとSecretを使用した設定管理と、環境変数・ボリュームマウントによる設定の注入方法を学びます。

---

## 目次

1. [設定管理の概要](#設定管理の概要)
2. [ConfigMap](#configmap)
3. [Secret](#secret)
4. [設定の注入方法](#設定の注入方法)
5. [設定の更新](#設定の更新)
6. [External Secrets](#external-secrets)
7. [IDサービスでの構成例](#idサービスでの構成例)

---

## 設定管理の概要

### 設定の分離

```
┌─────────────────────────────────────────────────────────────┐
│               Kubernetes 設定管理の原則                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  アプリケーションコード        設定                           │
│  ┌─────────────────┐      ┌─────────────────┐              │
│  │                 │      │  ConfigMap      │              │
│  │   コンテナ       │ ◄─── │  (非機密情報)    │              │
│  │   イメージ       │      │                 │              │
│  │                 │      ├─────────────────┤              │
│  │                 │ ◄─── │  Secret         │              │
│  │                 │      │  (機密情報)      │              │
│  └─────────────────┘      └─────────────────┘              │
│                                                              │
│  メリット:                                                   │
│  - 環境毎の設定切り替えが容易                                 │
│  - イメージの再ビルド不要                                     │
│  - 機密情報の分離管理                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## ConfigMap

### 作成方法

#### リテラルから作成

```bash
kubectl create configmap app-config \
  --from-literal=SPRING_PROFILES_ACTIVE=production \
  --from-literal=LOG_LEVEL=INFO
```

#### ファイルから作成

```bash
# 単一ファイル
kubectl create configmap app-config \
  --from-file=application.yml

# ディレクトリ
kubectl create configmap app-config \
  --from-file=config/
```

#### YAMLから作成

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: idp-config
  namespace: production
data:
  # キー・バリュー形式
  SPRING_PROFILES_ACTIVE: "production"
  LOG_LEVEL: "INFO"
  SERVER_PORT: "8080"

  # ファイル形式
  application.yml: |
    spring:
      application:
        name: idp-server
      datasource:
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
    server:
      port: 8080
      shutdown: graceful

  logback-spring.xml: |
    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
      <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
          <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
      </appender>
      <root level="INFO">
        <appender-ref ref="CONSOLE" />
      </root>
    </configuration>
```

### ConfigMapの確認

```bash
# 一覧
kubectl get configmap

# 詳細
kubectl describe configmap idp-config

# YAML出力
kubectl get configmap idp-config -o yaml

# 特定キーの値
kubectl get configmap idp-config -o jsonpath='{.data.SPRING_PROFILES_ACTIVE}'
```

---

## Secret

### Secretの種類

```
┌─────────────────────────────────────────────────────────────┐
│                      Secret Types                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Opaque (デフォルト)                                         │
│  └── 任意のデータ                                            │
│                                                              │
│  kubernetes.io/basic-auth                                    │
│  └── Basic認証（username, password）                         │
│                                                              │
│  kubernetes.io/ssh-auth                                      │
│  └── SSH認証（ssh-privatekey）                               │
│                                                              │
│  kubernetes.io/tls                                           │
│  └── TLS証明書（tls.crt, tls.key）                           │
│                                                              │
│  kubernetes.io/dockerconfigjson                              │
│  └── Dockerレジストリ認証                                    │
│                                                              │
│  kubernetes.io/service-account-token                         │
│  └── ServiceAccountトークン                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Secretの作成

#### コマンドで作成

```bash
# 基本的なSecret
kubectl create secret generic db-secret \
  --from-literal=username=admin \
  --from-literal=password=supersecret

# ファイルから
kubectl create secret generic tls-secret \
  --from-file=tls.crt=cert.pem \
  --from-file=tls.key=key.pem

# TLS Secret
kubectl create secret tls idp-tls \
  --cert=cert.pem \
  --key=key.pem

# Dockerレジストリ認証
kubectl create secret docker-registry regcred \
  --docker-server=ghcr.io \
  --docker-username=user \
  --docker-password=token
```

#### YAMLで作成

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: idp-secrets
  namespace: production
type: Opaque
data:
  # Base64エンコード必須
  database-url: amRiYzpwb3N0Z3Jlc3FsOi8vZGI6NTQzMi9pZHA=
  database-username: aWRw
  database-password: c3VwZXJzZWNyZXQ=
  jwt-secret: bXktand0LXNlY3JldC1rZXktMTIzNDU2Nzg5MA==

---
# stringDataを使用（エンコード不要）
apiVersion: v1
kind: Secret
metadata:
  name: idp-secrets-plain
type: Opaque
stringData:
  database-url: "jdbc:postgresql://db:5432/idp"
  database-username: "idp"
  database-password: "supersecret"
```

### Base64エンコード

```bash
# エンコード
echo -n "supersecret" | base64
# 出力: c3VwZXJzZWNyZXQ=

# デコード
echo "c3VwZXJzZWNyZXQ=" | base64 -d
# 出力: supersecret
```

---

## 設定の注入方法

### 環境変数として注入

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
spec:
  template:
    spec:
      containers:
        - name: idp-server
          image: my-idp-server:latest
          env:
            # 直接指定
            - name: SERVER_PORT
              value: "8080"

            # ConfigMapから
            - name: SPRING_PROFILES_ACTIVE
              valueFrom:
                configMapKeyRef:
                  name: idp-config
                  key: SPRING_PROFILES_ACTIVE

            # Secretから
            - name: DATABASE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: database-password

            # Podの情報
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: POD_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP

          # ConfigMap全体を環境変数に
          envFrom:
            - configMapRef:
                name: idp-config
            - secretRef:
                name: idp-secrets
                # optional: true  # Secretが存在しなくてもOK
```

### ボリュームとしてマウント

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
spec:
  template:
    spec:
      containers:
        - name: idp-server
          image: my-idp-server:latest
          volumeMounts:
            # ConfigMapをファイルとしてマウント
            - name: config-volume
              mountPath: /app/config
              readOnly: true

            # 特定のキーのみマウント
            - name: app-settings
              mountPath: /app/application.yml
              subPath: application.yml
              readOnly: true

            # Secretをマウント
            - name: secrets-volume
              mountPath: /app/secrets
              readOnly: true

      volumes:
        - name: config-volume
          configMap:
            name: idp-config

        - name: app-settings
          configMap:
            name: idp-config
            items:
              - key: application.yml
                path: application.yml

        - name: secrets-volume
          secret:
            secretName: idp-secrets
            # パーミッション設定
            defaultMode: 0400
```

### マウント方法の比較

| 方法 | メリット | デメリット |
|------|---------|----------|
| 環境変数 | シンプル、アクセスが容易 | 起動時のみ読み込み |
| ボリュームマウント | 動的更新可能、ファイル形式 | ファイル操作が必要 |

---

## 設定の更新

### ConfigMap/Secretの更新

```bash
# 編集
kubectl edit configmap idp-config

# 置き換え
kubectl create configmap idp-config \
  --from-file=config/ \
  --dry-run=client -o yaml | kubectl apply -f -

# パッチ
kubectl patch configmap idp-config \
  --type merge \
  -p '{"data":{"LOG_LEVEL":"DEBUG"}}'
```

### 更新時の動作

```
┌─────────────────────────────────────────────────────────────┐
│                    設定更新時の動作                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  環境変数として注入した場合:                                  │
│  └── Podの再起動が必要                                       │
│  └── 自動では反映されない                                    │
│                                                              │
│  ボリュームとしてマウントした場合:                            │
│  └── 自動で更新される（kubeletが定期的に同期）               │
│  └── 更新間隔: デフォルト約1分                               │
│  └── subPathマウントは更新されない                           │
│                                                              │
│  アプリケーションが設定変更を検知する方法:                    │
│  └── ファイル監視（inotify等）                               │
│  └── Spring Cloud Config Reload                              │
│  └── Sidecar + SIGHUP                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ローリング更新のトリガー

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
spec:
  template:
    metadata:
      annotations:
        # ConfigMapのハッシュをアノテーションに追加
        # ConfigMap更新時にPodが再作成される
        checksum/config: "{{ sha256sum .Values.configmap | trunc 63 }}"
```

```bash
# 手動でローリング更新をトリガー
kubectl rollout restart deployment/idp-server
```

---

## External Secrets

### AWS Secrets Manager連携

```yaml
# External Secretsオペレーター使用
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
spec:
  provider:
    aws:
      service: SecretsManager
      region: ap-northeast-1
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets-sa

---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: idp-external-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore

  target:
    name: idp-secrets
    creationPolicy: Owner

  data:
    - secretKey: database-password
      remoteRef:
        key: idp/production/database
        property: password

    - secretKey: jwt-secret
      remoteRef:
        key: idp/production/jwt
        property: secret-key
```

### HashiCorp Vault連携

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: vault-backend
spec:
  provider:
    vault:
      server: "https://vault.example.com"
      path: "secret"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "idp-app"
          serviceAccountRef:
            name: idp-sa

---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: idp-vault-secrets
spec:
  refreshInterval: 5m
  secretStoreRef:
    name: vault-backend
    kind: SecretStore

  target:
    name: idp-secrets

  data:
    - secretKey: database-password
      remoteRef:
        key: idp/database
        property: password
```

---

## IDサービスでの構成例

### 完全な設定構成

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: idp-config
  namespace: idp-system
data:
  SPRING_PROFILES_ACTIVE: "kubernetes,production"
  SERVER_PORT: "8080"
  MANAGEMENT_SERVER_PORT: "9090"

  application.yml: |
    spring:
      application:
        name: idp-server
      datasource:
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
      redis:
        timeout: 5000ms
    server:
      port: 8080
      shutdown: graceful
      tomcat:
        max-threads: 200
        accept-count: 100
    management:
      server:
        port: 9090
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      endpoint:
        health:
          show-details: always
          probes:
            enabled: true

---
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: idp-secrets
  namespace: idp-system
type: Opaque
stringData:
  DATABASE_URL: "jdbc:postgresql://postgres:5432/idp"
  DATABASE_USERNAME: "idp"
  DATABASE_PASSWORD: "your-secure-password"
  REDIS_HOST: "redis"
  REDIS_PASSWORD: "redis-password"
  JWT_SECRET_KEY: "your-jwt-secret-key-at-least-32-chars"

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

      containers:
        - name: idp-server
          image: ghcr.io/myorg/idp-server:v1.0.0
          ports:
            - name: http
              containerPort: 8080
            - name: management
              containerPort: 9090

          # 環境変数（ConfigMapから）
          envFrom:
            - configMapRef:
                name: idp-config

          # 環境変数（Secretから）
          env:
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: DATABASE_URL
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: DATABASE_USERNAME
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: DATABASE_PASSWORD
            - name: SPRING_REDIS_HOST
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: REDIS_HOST
            - name: JWT_SECRET_KEY
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: JWT_SECRET_KEY

          # 設定ファイルをマウント
          volumeMounts:
            - name: config
              mountPath: /app/config/application.yml
              subPath: application.yml
              readOnly: true

          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: management
            initialDelaySeconds: 60
            periodSeconds: 10

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: management
            initialDelaySeconds: 30
            periodSeconds: 5

      volumes:
        - name: config
          configMap:
            name: idp-config

      # イメージプルシークレット
      imagePullSecrets:
        - name: ghcr-secret
```

---

## まとめ

### チェックリスト

- [ ] 機密情報はSecretに格納
- [ ] 非機密設定はConfigMapに格納
- [ ] Base64エンコードを忘れずに（Secret）
- [ ] 適切な注入方法を選択（環境変数 vs ボリューム）
- [ ] 設定更新時の反映方法を検討
- [ ] 本番環境ではExternal Secretsを検討

### 次のステップ

- [Kubernetesスケーリング](kubernetes-scaling.md) - HPA、VPA
- [Kubernetesセキュリティ](kubernetes-security.md) - RBAC、PodSecurity

---

## 参考リソース

- [ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [External Secrets Operator](https://external-secrets.io/)
- [Sealed Secrets](https://sealed-secrets.netlify.app/)
