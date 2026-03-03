# Kubernetes ストレージ

Kubernetesの永続ボリューム（PV）、永続ボリュームクレーム（PVC）、StorageClassを学び、データの永続化を理解します。

---

## 目次

1. [ストレージの概要](#ストレージの概要)
2. [Volume Types](#volume-types)
3. [PersistentVolume と PVC](#persistentvolume-と-pvc)
4. [StorageClass](#storageclass)
5. [アクセスモード](#アクセスモード)
6. [クラウドストレージ](#クラウドストレージ)
7. [IDサービスでの構成例](#idサービスでの構成例)

---

## ストレージの概要

### ストレージ階層

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Storage                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  一時ストレージ（Pod終了で消失）                              │
│  ├── emptyDir                                                │
│  └── hostPath（開発用）                                      │
│                                                              │
│  永続ストレージ（Pod終了後も維持）                            │
│  ├── PersistentVolume (PV)                                   │
│  │   └── 管理者がプロビジョニング（静的）                     │
│  │   └── StorageClassで自動プロビジョニング（動的）           │
│  └── PersistentVolumeClaim (PVC)                             │
│      └── ユーザーがストレージを要求                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 関係図

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  ┌─────────────────┐      ┌─────────────────┐              │
│  │   StorageClass  │      │ 実際のストレージ  │              │
│  │  (gp3, standard) │      │  (EBS, NFS等)    │              │
│  └────────┬────────┘      └────────▲────────┘              │
│           │                        │                         │
│           │ 動的プロビジョニング     │                         │
│           ▼                        │                         │
│  ┌─────────────────┐      ┌───────┴────────┐               │
│  │       PVC       │ ◄──► │       PV       │               │
│  │ (ユーザーの要求)  │ Bound │(実際のボリューム) │               │
│  └────────┬────────┘      └────────────────┘               │
│           │                                                  │
│           │ マウント                                          │
│           ▼                                                  │
│  ┌─────────────────┐                                        │
│  │       Pod       │                                        │
│  │ volumeMounts:   │                                        │
│  │   /data         │                                        │
│  └─────────────────┘                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Volume Types

### emptyDir

Pod内のコンテナ間で共有される一時ストレージ。

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: shared-volume-pod
spec:
  containers:
    - name: app
      image: my-app
      volumeMounts:
        - name: shared-data
          mountPath: /app/data

    - name: sidecar
      image: log-processor
      volumeMounts:
        - name: shared-data
          mountPath: /logs

  volumes:
    - name: shared-data
      emptyDir: {}
      # メモリベース（RAMディスク）
      # emptyDir:
      #   medium: Memory
      #   sizeLimit: 100Mi
```

### hostPath

ノードのファイルシステムをマウント（開発・テスト用）。

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: hostpath-pod
spec:
  containers:
    - name: app
      image: my-app
      volumeMounts:
        - name: host-data
          mountPath: /data
  volumes:
    - name: host-data
      hostPath:
        path: /var/data
        type: DirectoryOrCreate
```

### configMap / secret

設定やシークレットをボリュームとしてマウント。

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: config-pod
spec:
  containers:
    - name: app
      image: my-app
      volumeMounts:
        - name: config
          mountPath: /app/config
          readOnly: true
        - name: secrets
          mountPath: /app/secrets
          readOnly: true

  volumes:
    - name: config
      configMap:
        name: app-config
    - name: secrets
      secret:
        secretName: app-secrets
```

---

## PersistentVolume と PVC

### 静的プロビジョニング

管理者がPVを事前に作成。

```yaml
# 1. 管理者がPVを作成
apiVersion: v1
kind: PersistentVolume
metadata:
  name: postgres-pv
spec:
  capacity:
    storage: 100Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  storageClassName: manual
  # NFSの例
  nfs:
    server: nfs-server.example.com
    path: /data/postgres

---
# 2. ユーザーがPVCを作成
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Gi
  storageClassName: manual

---
# 3. PodでPVCを使用
apiVersion: v1
kind: Pod
metadata:
  name: postgres
spec:
  containers:
    - name: postgres
      image: postgres:16
      volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumes:
    - name: data
      persistentVolumeClaim:
        claimName: postgres-pvc
```

### 動的プロビジョニング

StorageClassを使用して自動でPVを作成。

```yaml
# PVCを作成するとPVが自動作成される
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 100Gi
  storageClassName: gp3  # StorageClassを指定
```

---

## StorageClass

### AWS EBS（gp3）

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: gp3
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  iops: "3000"
  throughput: "125"
  encrypted: "true"
volumeBindingMode: WaitForFirstConsumer
reclaimPolicy: Delete
allowVolumeExpansion: true
```

### AWS EFS（共有ストレージ）

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-sc
provisioner: efs.csi.aws.com
parameters:
  provisioningMode: efs-ap
  fileSystemId: fs-xxxxxxxx
  directoryPerms: "700"
  basePath: "/dynamic_provisioning"
```

### GCP Persistent Disk

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast
provisioner: pd.csi.storage.gke.io
parameters:
  type: pd-ssd
volumeBindingMode: WaitForFirstConsumer
reclaimPolicy: Delete
```

### パラメータ説明

| パラメータ | 説明 |
|-----------|------|
| provisioner | ストレージプロバイダ |
| parameters | プロバイダ固有の設定 |
| volumeBindingMode | Immediate（即時）/ WaitForFirstConsumer（遅延） |
| reclaimPolicy | Delete（削除）/ Retain（保持） |
| allowVolumeExpansion | ボリューム拡張の可否 |

---

## アクセスモード

### モードの種類

```
┌─────────────────────────────────────────────────────────────┐
│                     Access Modes                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ReadWriteOnce (RWO)                                         │
│  └── 単一ノードから読み書き可能                               │
│  └── 用途: データベース（PostgreSQL等）                       │
│                                                              │
│  ReadOnlyMany (ROX)                                          │
│  └── 複数ノードから読み取り可能                               │
│  └── 用途: 静的コンテンツ、設定ファイル                       │
│                                                              │
│  ReadWriteMany (RWX)                                         │
│  └── 複数ノードから読み書き可能                               │
│  └── 用途: 共有ファイルストレージ                             │
│  └── 対応: NFS、EFS、Azure Files                             │
│                                                              │
│  ReadWriteOncePod (RWOP) [K8s 1.22+]                         │
│  └── 単一Podのみから読み書き可能                              │
│  └── 用途: 厳密な排他制御が必要な場合                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ストレージタイプ別サポート

| ストレージ | RWO | ROX | RWX |
|-----------|-----|-----|-----|
| AWS EBS | ○ | - | - |
| AWS EFS | ○ | ○ | ○ |
| GCP PD | ○ | ○ | - |
| Azure Disk | ○ | - | - |
| Azure Files | ○ | ○ | ○ |
| NFS | ○ | ○ | ○ |

---

## クラウドストレージ

### AWS構成例

```yaml
# EBS CSI Driver用StorageClass
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: ebs-gp3
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  iops: "3000"
  throughput: "125"
  encrypted: "true"
  kmsKeyId: arn:aws:kms:ap-northeast-1:xxx:key/xxx
volumeBindingMode: WaitForFirstConsumer
reclaimPolicy: Delete
allowVolumeExpansion: true

---
# データベース用PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: ebs-gp3
  resources:
    requests:
      storage: 100Gi
```

### EFS（共有ストレージ）

```yaml
# EFS用StorageClass
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-shared
provisioner: efs.csi.aws.com
parameters:
  provisioningMode: efs-ap
  fileSystemId: fs-xxxxxxxx
  directoryPerms: "755"

---
# 共有ストレージPVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: shared-uploads
spec:
  accessModes:
    - ReadWriteMany  # 複数Podから書き込み可能
  storageClassName: efs-shared
  resources:
    requests:
      storage: 50Gi
```

---

## IDサービスでの構成例

### PostgreSQL用ストレージ

```yaml
# StatefulSet用のvolumeClaimTemplates
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres-headless
  replicas: 1
  selector:
    matchLabels:
      app: postgres
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
          env:
            - name: POSTGRES_DB
              value: idp
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: username
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
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "1000m"

  # Pod毎に独立したPVCを作成
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes:
          - ReadWriteOnce
        storageClassName: ebs-gp3
        resources:
          requests:
            storage: 100Gi
```

### バックアップ用の追加ストレージ

```yaml
# バックアップ用PVC（S3に転送前の一時保存）
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: backup-staging
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: ebs-gp3
  resources:
    requests:
      storage: 50Gi

---
# バックアップCronJob
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: backup
              image: postgres:16
              command:
                - /bin/bash
                - -c
                - |
                  pg_dump -h postgres -U $PGUSER $PGDATABASE > /backup/backup-$(date +%Y%m%d).sql
                  # S3にアップロード
                  aws s3 cp /backup/backup-$(date +%Y%m%d).sql s3://$S3_BUCKET/
              env:
                - name: PGUSER
                  valueFrom:
                    secretKeyRef:
                      name: postgres-secret
                      key: username
                - name: PGPASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: postgres-secret
                      key: password
                - name: PGDATABASE
                  value: idp
                - name: S3_BUCKET
                  value: idp-backups
              volumeMounts:
                - name: backup
                  mountPath: /backup
          restartPolicy: OnFailure
          volumes:
            - name: backup
              persistentVolumeClaim:
                claimName: backup-staging
```

### ボリューム拡張

```bash
# PVCのサイズを拡張
kubectl patch pvc postgres-data-postgres-0 -p '{"spec":{"resources":{"requests":{"storage":"200Gi"}}}}'

# 拡張状態の確認
kubectl get pvc postgres-data-postgres-0
kubectl describe pvc postgres-data-postgres-0
```

---

## まとめ

### ストレージ選択ガイド

| ユースケース | 推奨ストレージ | アクセスモード |
|-------------|--------------|--------------|
| データベース | EBS/PD | RWO |
| 共有ファイル | EFS/NFS | RWX |
| 一時キャッシュ | emptyDir | - |
| ログ収集 | hostPath | - |
| 設定ファイル | ConfigMap | - |

### チェックリスト

- [ ] 適切なStorageClassを選択
- [ ] アクセスモードを確認
- [ ] リクレームポリシーを設定
- [ ] ボリューム拡張を有効化（必要な場合）
- [ ] バックアップ戦略を策定

### 次のステップ

- [Kubernetes設定管理](kubernetes-configuration.md) - ConfigMap、Secret
- [Kubernetesスケーリング](kubernetes-scaling.md) - HPA、VPA

---

## 参考リソース

- [Kubernetes Persistent Volumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
- [Storage Classes](https://kubernetes.io/docs/concepts/storage/storage-classes/)
- [AWS EBS CSI Driver](https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html)
- [AWS EFS CSI Driver](https://docs.aws.amazon.com/eks/latest/userguide/efs-csi.html)
