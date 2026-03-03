# Kubernetes ワークロード

Kubernetesでアプリケーションを実行するためのワークロードリソース（Pod、Deployment、StatefulSet、Job等）を学びます。

---

## 目次

1. [Pod](#pod)
2. [Deployment](#deployment)
3. [StatefulSet](#statefulset)
4. [DaemonSet](#daemonset)
5. [Job と CronJob](#job-と-cronjob)
6. [ワークロードの選択基準](#ワークロードの選択基準)

---

## Pod

### 概要

Podは、Kubernetesにおける最小のデプロイ単位です。1つ以上のコンテナをグループ化し、共有リソースを持ちます。

```
┌─────────────────────────────────────────────────────────────┐
│                           Pod                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    共有リソース                       │   │
│  │  - ネットワーク（同一IPアドレス）                      │   │
│  │  - ストレージ（ボリューム）                            │   │
│  │  - 仕様（再起動ポリシー等）                            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐                    │
│  │  Container 1   │  │  Container 2   │                    │
│  │  (メインアプリ)  │  │  (サイドカー)   │                    │
│  │    :8080       │  │    :9090       │                    │
│  └────────────────┘  └────────────────┘                    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                      Volume                          │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 基本的なPod定義

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: idp-server
  labels:
    app: idp-server
    version: v1
spec:
  containers:
    - name: idp-server
      image: my-idp-server:latest
      ports:
        - name: http
          containerPort: 8080
      env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
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
          port: 8080
        initialDelaySeconds: 60
        periodSeconds: 10
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 5
  restartPolicy: Always
```

### ヘルスチェック

```yaml
# Liveness Probe: コンテナが生きているか
# 失敗 → コンテナ再起動
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3

# Readiness Probe: トラフィックを受ける準備ができているか
# 失敗 → Serviceのエンドポイントから除外
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3

# Startup Probe: 起動が完了したか
# 成功するまでLiveness/Readinessは実行されない
startupProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 30  # 150秒まで待機
```

### リソース管理

```yaml
resources:
  # 最小保証リソース（スケジューリングに使用）
  requests:
    memory: "512Mi"
    cpu: "250m"      # 0.25 CPU
  # 最大使用可能リソース
  limits:
    memory: "1Gi"
    cpu: "500m"      # 0.5 CPU

# QoSクラス（自動決定）
# Guaranteed: requests = limits
# Burstable: requests < limits
# BestEffort: 指定なし
```

---

## Deployment

### 概要

Deploymentは、ステートレスアプリケーションのデプロイと管理を行うワークロードリソースです。

```
┌─────────────────────────────────────────────────────────────┐
│                       Deployment                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │                      ReplicaSet                        │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │ │
│  │  │    Pod      │  │    Pod      │  │    Pod      │   │ │
│  │  │ idp-server  │  │ idp-server  │  │ idp-server  │   │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘   │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                              │
│  管理機能:                                                   │
│  - レプリカ数の維持                                          │
│  - ローリングアップデート                                     │
│  - ロールバック                                              │
└─────────────────────────────────────────────────────────────┘
```

### 完全なDeployment定義

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
  labels:
    app: idp-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: idp-server

  # アップデート戦略
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1      # 更新中に停止可能な最大Pod数
      maxSurge: 1            # 一時的に追加可能な最大Pod数

  # Pod テンプレート
  template:
    metadata:
      labels:
        app: idp-server
        version: v1
    spec:
      # 同じノードに配置しない
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: idp-server
                topologyKey: kubernetes.io/hostname

      containers:
        - name: idp-server
          image: my-idp-server:v1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "kubernetes"
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: idp-secrets
                  key: database-url
          envFrom:
            - configMapRef:
                name: idp-config
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
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
          volumeMounts:
            - name: app-logs
              mountPath: /app/logs

      volumes:
        - name: app-logs
          emptyDir: {}

      # 優雅な終了
      terminationGracePeriodSeconds: 60
```

### ローリングアップデート

```
更新前:
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   Pod v1     │ │   Pod v1     │ │   Pod v1     │
└──────────────┘ └──────────────┘ └──────────────┘

Step 1: 新Pod作成
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   Pod v1     │ │   Pod v1     │ │   Pod v1     │ │   Pod v2 ↑   │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

Step 2: 旧Pod削除
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   Pod v1 ↓   │ │   Pod v1     │ │   Pod v2     │
└──────────────┘ └──────────────┘ └──────────────┘

...繰り返し...

更新後:
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   Pod v2     │ │   Pod v2     │ │   Pod v2     │
└──────────────┘ └──────────────┘ └──────────────┘
```

### 更新とロールバック

```bash
# イメージ更新
kubectl set image deployment/idp-server idp-server=my-idp-server:v2.0.0

# ロールアウト状態確認
kubectl rollout status deployment/idp-server

# ロールアウト履歴
kubectl rollout history deployment/idp-server

# ロールバック
kubectl rollout undo deployment/idp-server

# 特定リビジョンにロールバック
kubectl rollout undo deployment/idp-server --to-revision=2

# ロールアウト一時停止
kubectl rollout pause deployment/idp-server

# ロールアウト再開
kubectl rollout resume deployment/idp-server
```

---

## StatefulSet

### 概要

StatefulSetは、ステートフルアプリケーション（データベース等）のデプロイを管理します。

```
┌─────────────────────────────────────────────────────────────┐
│                       StatefulSet                            │
│                                                              │
│  特徴:                                                       │
│  - 安定したネットワークID（pod-0, pod-1, pod-2）              │
│  - 順序付きデプロイ（0→1→2）                                 │
│  - 順序付き削除（2→1→0）                                     │
│  - 永続ボリュームの安定した紐付け                             │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  postgres-0 │  │  postgres-1 │  │  postgres-2 │         │
│  │  (primary)  │  │  (replica)  │  │  (replica)  │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                  │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐         │
│  │    PVC-0    │  │    PVC-1    │  │    PVC-2    │         │
│  │   (10Gi)    │  │   (10Gi)    │  │   (10Gi)    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### StatefulSet定義例

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres-headless
  replicas: 3
  selector:
    matchLabels:
      app: postgres

  # 順序付きデプロイ（デフォルト）
  podManagementPolicy: OrderedReady
  # または並列デプロイ: Parallel

  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16
          ports:
            - containerPort: 5432
              name: postgres
          env:
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: password
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data

  # PVC テンプレート
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: gp3
        resources:
          requests:
            storage: 10Gi

---
# Headless Service（必須）
apiVersion: v1
kind: Service
metadata:
  name: postgres-headless
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
```

### DeploymentとStatefulSetの比較

| 項目 | Deployment | StatefulSet |
|------|-----------|-------------|
| Pod名 | ランダムなサフィックス | 順序付き（-0, -1, -2） |
| 起動順序 | 並列 | 順序付き |
| 削除順序 | 任意 | 逆順序 |
| PVC | 共有または個別指定 | Pod毎に自動作成 |
| ネットワークID | 不定 | 安定（DNS名固定） |
| 用途 | ステートレスアプリ | データベース等 |

---

## DaemonSet

### 概要

DaemonSetは、全てのノード（または特定ノード）で1つずつPodを実行します。

```
┌─────────────────────────────────────────────────────────────┐
│                        DaemonSet                             │
│                                                              │
│  用途:                                                       │
│  - ログ収集（Fluentd、Fluent Bit）                           │
│  - モニタリング（Prometheus Node Exporter）                   │
│  - ネットワークプラグイン（CNI）                              │
│  - ストレージプラグイン                                       │
│                                                              │
│  Node 1           Node 2           Node 3                    │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │ fluentd  │    │ fluentd  │    │ fluentd  │              │
│  │ (Pod)    │    │ (Pod)    │    │ (Pod)    │              │
│  └──────────┘    └──────────┘    └──────────┘              │
└─────────────────────────────────────────────────────────────┘
```

### DaemonSet定義例

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentd
  namespace: kube-system
spec:
  selector:
    matchLabels:
      app: fluentd
  template:
    metadata:
      labels:
        app: fluentd
    spec:
      # 特定ノードのみで実行
      nodeSelector:
        kubernetes.io/os: linux

      tolerations:
        # マスターノードでも実行
        - key: node-role.kubernetes.io/control-plane
          effect: NoSchedule

      containers:
        - name: fluentd
          image: fluent/fluentd:v1.16
          resources:
            limits:
              memory: 200Mi
            requests:
              cpu: 100m
              memory: 200Mi
          volumeMounts:
            - name: varlog
              mountPath: /var/log
            - name: containers
              mountPath: /var/lib/docker/containers
              readOnly: true

      volumes:
        - name: varlog
          hostPath:
            path: /var/log
        - name: containers
          hostPath:
            path: /var/lib/docker/containers
```

---

## Job と CronJob

### Job

1回限りのバッチ処理を実行します。

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
spec:
  # 完了に必要な成功Pod数
  completions: 1
  # 並列実行数
  parallelism: 1
  # リトライ回数
  backoffLimit: 3
  # 完了後の保持時間
  ttlSecondsAfterFinished: 3600

  template:
    spec:
      containers:
        - name: migration
          image: my-app:latest
          command: ["./migrate.sh"]
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: url
      restartPolicy: Never
```

### CronJob

定期的なバッチ処理を実行します。

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: backup-job
spec:
  # Cron形式（分 時 日 月 曜日）
  schedule: "0 2 * * *"  # 毎日2:00

  # タイムゾーン（Kubernetes 1.27+）
  timeZone: "Asia/Tokyo"

  # 同時実行ポリシー
  concurrencyPolicy: Forbid  # Allow, Forbid, Replace

  # 開始デッドライン（秒）
  startingDeadlineSeconds: 200

  # 履歴保持数
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3

  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: backup
              image: backup-tool:latest
              command: ["/backup.sh"]
              env:
                - name: S3_BUCKET
                  value: "my-backup-bucket"
          restartPolicy: OnFailure
```

### 並列Job

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: parallel-processing
spec:
  completions: 10    # 10個のタスクを完了
  parallelism: 3     # 最大3並列

  template:
    spec:
      containers:
        - name: worker
          image: worker:latest
          env:
            - name: TASK_INDEX
              valueFrom:
                fieldRef:
                  fieldPath: metadata.annotations['batch.kubernetes.io/job-completion-index']
      restartPolicy: Never
```

---

## ワークロードの選択基準

### 選択フローチャート

```
アプリケーションの種類は？
│
├─ Webサービス/API
│   └─ ステートレス？
│       ├─ Yes → Deployment
│       └─ No  → StatefulSet
│
├─ データベース
│   └─ StatefulSet
│
├─ キャッシュ（Redis等）
│   └─ 永続化必要？
│       ├─ Yes → StatefulSet
│       └─ No  → Deployment
│
├─ バッチ処理
│   └─ 定期実行？
│       ├─ Yes → CronJob
│       └─ No  → Job
│
└─ ノード毎のエージェント
    └─ DaemonSet
```

### 比較表

| ワークロード | 用途 | Pod名 | スケーリング | 永続化 |
|-------------|------|-------|-------------|--------|
| Deployment | Webアプリ | ランダム | 水平 | 共有/なし |
| StatefulSet | DB | 順序付き | 水平 | Pod毎 |
| DaemonSet | エージェント | ノード毎 | ノード数 | ホスト |
| Job | バッチ | ランダム | 並列数 | なし |
| CronJob | 定期バッチ | ランダム | 並列数 | なし |

---

## まとめ

### チェックリスト

- [ ] 適切なワークロードタイプを選択
- [ ] リソース（requests/limits）を設定
- [ ] ヘルスチェック（liveness/readiness）を設定
- [ ] アフィニティ/アンチアフィニティを検討
- [ ] 更新戦略を設定（Deployment）
- [ ] 永続ボリュームを設定（StatefulSet）

### 次のステップ

- [Kubernetesネットワーキング](kubernetes-networking.md) - Service、Ingress
- [Kubernetesストレージ](kubernetes-storage.md) - PV、PVC
- [Kubernetesスケーリング](kubernetes-scaling.md) - HPA、VPA

---

## 参考リソース

- [Kubernetes Workloads](https://kubernetes.io/docs/concepts/workloads/)
- [Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- [StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- [Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/)
