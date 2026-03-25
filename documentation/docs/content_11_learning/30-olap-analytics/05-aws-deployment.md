# AWS 上の構築パターン

ClickHouse を AWS 上で構築する3つのパターンと、idp-server との統合を学びます。

---

## 3つのデプロイパターン

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  A: ClickHouse Cloud     最小構成で素早く始めたい            │
│  B: BYOC                 データを自分のAWS内に留めたい      │
│  C: セルフホスト (EKS)   コスト最小、フル制御               │
│                                                              │
│  構築の手軽さ:   A > B > C                                   │
│  運用の手軽さ:   A > B > C                                   │
│  コスト:         C < A < B                                   │
│  データ主権:     B = C > A                                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## パターン A: ClickHouse Cloud（フルマネージド）

```
┌─ AWS アカウント ──────────────────────────────────────────┐
│                                                            │
│  ┌─ VPC ───────────────┐    ┌──────────────────────────┐ │
│  │                      │    │ ClickHouse Cloud          │ │
│  │  ┌──────────┐       │    │ (clickhouse.cloud)        │ │
│  │  │ EKS      │       │    │                           │ │
│  │  │ idp-server│ HTTPS │────│→ 統計クエリ (JDBC)       │ │
│  │  └──────────┘       │    │                           │ │
│  │  ┌──────────┐       │    │ ClickPipes (CDC)          │ │
│  │  │ RDS      │ WAL  │────│→ security_event 自動同期  │ │
│  │  │ PostgreSQL│       │    │                           │ │
│  │  └──────────┘       │    └──────────────────────────┘ │
│  └──────────────────────┘                                  │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

| 項目 | 内容 |
|:---|:---|
| 構築時間 | 数分（GUIでポチポチ） |
| 運用 | 不要（全部マネージド） |
| 費用 | ~$190/月〜 |
| CDC | ClickPipes（PeerDB組み込み、GUIで設定） |
| データ所在 | ClickHouse 社のAWS |
| AWS Marketplace | 対応（AWS請求に一括化可能） |

**始め方**:
1. https://clickhouse.cloud でアカウント作成（$300 無料クレジット）
2. サービス作成（リージョン選択）
3. ClickPipes で RDS の接続情報を入力 → CDC 自動開始
4. JDBC URL を idp-server に設定

---

## パターン B: BYOC（Bring Your Own Cloud）

```
┌─ AWS アカウント ──────────────────────────────────────────┐
│                                                            │
│  ┌─ VPC ─────────────────────────────────────────────────┐│
│  │                                                        ││
│  │  ┌──────────────┐    ┌─────────────────────────────┐ ││
│  │  │ EKS          │    │ EKS (ClickHouse BYOC)       │ ││
│  │  │ ┌──────────┐ │    │ ┌───────────┐ ┌──────────┐ │ ││
│  │  │ │idp-server│ │VPC │ │ClickHouse │ │ClickPipes│ │ ││
│  │  │ └──────────┘ │内部│→│ Server    │ │(CDC)     │ │ ││
│  │  │ ┌──────────┐ │通信│ └───────────┘ └──────────┘ │ ││
│  │  │ │ RDS      │ │    │ ┌───────────┐              │ ││
│  │  │ │PostgreSQL│ │    │ │ EBS / S3  │              │ ││
│  │  │ └──────────┘ │    │ └───────────┘              │ ││
│  │  └──────────────┘    └─────────────────────────────┘ ││
│  │                        ↑ ClickHouse社がリモート運用   ││
│  │                        ↑ データは自分のAWS内に留まる  ││
│  └────────────────────────────────────────────────────────┘│
│                                                            │
└────────────────────────────────────────────────────────────┘
```

| 項目 | 内容 |
|:---|:---|
| 構築時間 | 数時間（Terraform で自動構築） |
| 運用 | ClickHouse 社がリモート管理 |
| 費用 | Cloud より少し高い（BYOCプレミアム） |
| CDC | ClickPipes 利用可能 |
| データ所在 | **自分のAWSアカウント内** |
| コンプライアンス | ✅ データが外部に出ない |

**セキュリティイベントは個人情報（user_id, IP等）を含むため、本番環境ではBYOCが現実的。**

---

## パターン C: セルフホスト（EKS）

```
┌─ AWS アカウント ──────────────────────────────────────────┐
│                                                            │
│  ┌─ VPC ─────────────────────────────────────────────────┐│
│  │                                                        ││
│  │  ┌─ EKS Cluster ──────────────────────────────────┐  ││
│  │  │                                                  │  ││
│  │  │  ┌─ Namespace: idp-server ────────────────────┐│  ││
│  │  │  │ idp-server (Pod)                            ││  ││
│  │  │  │ PeerDB (Pod) ─── CDC ──┐                   ││  ││
│  │  │  └────────────────────────┼────────────────────┘│  ││
│  │  │                           │                      │  ││
│  │  │  ┌─ Namespace: clickhouse ┼────────────────────┐│  ││
│  │  │  │                        ▼                     ││  ││
│  │  │  │  ┌──────────────────────────────────────┐  ││  ││
│  │  │  │  │ Altinity Operator                     │  ││  ││
│  │  │  │  └──────────┬───────────────────────────┘  ││  ││
│  │  │  │             │ 管理                          ││  ││
│  │  │  │             ▼                               ││  ││
│  │  │  │  ┌─────────────────┐ ┌─────────────────┐  ││  ││
│  │  │  │  │ ClickHouse      │ │ ClickHouse      │  ││  ││
│  │  │  │  │ Server (Pod)    │ │ Keeper (Pod)    │  ││  ││
│  │  │  │  │                 │ │ (ZK互換)        │  ││  ││
│  │  │  │  │ ┌─────────────┐│ └─────────────────┘  ││  ││
│  │  │  │  │ │EBS gp3 100GB││                       ││  ││
│  │  │  │  │ └─────────────┘│                       ││  ││
│  │  │  │  └─────────────────┘                       ││  ││
│  │  │  └────────────────────────────────────────────┘│  ││
│  │  │                                                  │  ││
│  │  └──────────────────────────────────────────────────┘  ││
│  │                                                        ││
│  │  ┌──────────┐                                         ││
│  │  │ RDS      │ ← 既存                                  ││
│  │  │PostgreSQL│                                         ││
│  │  └──────────┘                                         ││
│  └────────────────────────────────────────────────────────┘│
│                                                            │
└────────────────────────────────────────────────────────────┘
```

| 項目 | 内容 |
|:---|:---|
| 構築時間 | 数日 |
| 運用 | **全部自前**（バックアップ、監視、アップデート） |
| 費用 | EC2 + EBS のみ（ライセンス無料） |
| CDC | PeerDB を自前で構築・運用 |
| データ所在 | **自分のAWSアカウント内** |

### 導入手順

```bash
# 1. Altinity Operator インストール
helm repo add altinity https://helm.altinity.com
helm install clickhouse-operator altinity/altinity-clickhouse-operator \
  --namespace clickhouse --create-namespace

# 2. ClickHouse クラスタ作成（CRD）
kubectl apply -f clickhouse-cluster.yaml

# 3. PeerDB で CDC 設定
kubectl apply -f peerdb-deployment.yaml
```

### clickhouse-cluster.yaml（最小構成）

```yaml
apiVersion: clickhouse.altinity.com/v1
kind: ClickHouseInstallation
metadata:
  name: idp-analytics
  namespace: clickhouse
spec:
  configuration:
    clusters:
      - name: default
        layout:
          shardsCount: 1
          replicasCount: 1
    zookeeper:
      nodes:
        - host: clickhouse-keeper
    settings:
      max_memory_usage: 10000000000
  defaults:
    templates:
      podTemplate: clickhouse-pod
      volumeClaimTemplate: data-volume
  templates:
    podTemplates:
      - name: clickhouse-pod
        spec:
          containers:
            - name: clickhouse
              resources:
                requests:
                  cpu: "2"
                  memory: "8Gi"
                limits:
                  cpu: "4"
                  memory: "16Gi"
    volumeClaimTemplates:
      - name: data-volume
        spec:
          accessModes: ["ReadWriteOnce"]
          storageClassName: gp3
          resources:
            requests:
              storage: 100Gi
```

---

## コスト比較

### 小規模（~10万件/日）

| | A: Cloud | B: BYOC | C: セルフホスト |
|---|:---:|:---:|:---:|
| ClickHouse | $190 | $300 | $0（OSS） |
| EC2 / ノード | - | - | $60 |
| EBS | - | - | $8 |
| PeerDB | 含む | 含む | $15（t3.small） |
| EKS コントロールプレーン | - | 含む | $73（既存なら$0） |
| **合計** | **$190/月** | **$300/月** | **$83〜156/月** |

### 中規模（~500万件/日）

| | A: Cloud | B: BYOC | C: セルフホスト |
|---|:---:|:---:|:---:|
| **合計** | **$500/月** | **$800/月** | **$200〜300/月** |

---

## 容量の目安（セルフホスト、EBS 100GB）

| 日次イベント | ClickHouse 保持可能期間 |
|:---:|:---:|
| 10万件/日 | 数十年 |
| 500万件/日 | 1年以上 |
| 2000万件/日 | 100日 |

ストレージが足りなくなったら EBS を拡張するか、S3 にコールドストレージ（ClickHouse の S3 テーブルエンジン）。

---

## 選択ガイド

```
┌──────────────────────────────────────────────────────┐
│              どれを選ぶか？                            │
│                                                      │
│  まず試したい           → A (Cloud、無料トライアル)  │
│  データを外に出せない   → B (BYOC) or C (セルフ)     │
│  コスト最小             → C (セルフホスト)            │
│  運用リソースがない     → A (Cloud) or B (BYOC)      │
│  既存EKSがある          → C (ノード追加だけ)          │
│                                                      │
│  idp-server のおすすめ:                               │
│  検証 → A (Cloud) で無料トライアル                   │
│  本番 → C (セルフホスト) で EKS に相乗り             │
│                                                      │
└──────────────────────────────────────────────────────┘
```

---

## 関連ドキュメント

- [OLTP + OLAP デュアル構成](dual-architecture): アプリケーション設計
- [データ投入パターン](data-ingestion): CDC, S3, JDBC
- [AWS](../aws/): EKS, EC2 の基礎
