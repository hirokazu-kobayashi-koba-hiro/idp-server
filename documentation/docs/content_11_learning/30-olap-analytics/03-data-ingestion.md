# データ投入パターン

[ClickHouse 入門](clickhouse-basics) で学んだように、ClickHouse は列指向の OLAP エンジンで、大量データの集計・分析が得意です。一方で、トランザクションや1行単位の更新は苦手なため、書き込みは PostgreSQL が担い、分析用データを ClickHouse に連携する構成が一般的です。

ここでは「PostgreSQL にあるデータを、どうやって ClickHouse に持っていくか」の具体的な方法を学びます。

ClickHouse の INSERT 特性（[バッチ INSERT が必須](clickhouse-basics#insert-のベストプラクティス)）を踏まえて、各パターンがその制約をどう解決しているかにも注目してください。

---

## 5つのパターン

各パターンで必要なインフラ構成が異なります。ClickHouse 自体のデプロイについては [AWS 上の構築パターン](aws-deployment) を参照してください。

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  ① JDBC（アプリから直接）                                    │
│  App ──── JDBC/HTTP ────→ ClickHouse                        │
│  必要: ClickHouse                                            │
│                                                              │
│  ② CDC（WAL監視、自動同期）                                  │
│  PostgreSQL ── WAL ──→ PeerDB ──→ ClickHouse                │
│  必要: ClickHouse + PeerDB                                   │
│                                                              │
│  ③ Kafka（ストリーミング）                                   │
│  App ──→ Kafka ──→ Kafka Engine ──→ ClickHouse              │
│  必要: ClickHouse + Kafka                                    │
│                                                              │
│  ④ S3 ファイルロード（バッチ）                               │
│  PostgreSQL ──→ S3 (Parquet) ──→ ClickHouse                 │
│  必要: ClickHouse + S3                                       │
│                                                              │
│  ⑤ PostgreSQL エンジン（直接参照）                           │
│  ClickHouse ──── クエリ時に読み取り ──→ PostgreSQL           │
│  必要: ClickHouse のみ（追加インフラなし）                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## ① JDBC（アプリから直接 INSERT）

アプリケーションから ClickHouse に直接書き込む。PostgreSQL と同じ JDBC インターフェース。

```java
// build.gradle
// implementation 'com.clickhouse:clickhouse-jdbc:0.7.1'

@Configuration
public class ClickHouseConfig {
    @Bean("clickHouseJdbc")
    public JdbcTemplate clickHouseJdbcTemplate() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:ch://clickhouse-host:8123/default");
        ds.setUsername("default");
        return new JdbcTemplate(ds);
    }
}

@Repository
public class AnalyticsRepository {
    @Qualifier("clickHouseJdbc")
    private final JdbcTemplate jdbc;

    public long countLoginFailures(UUID tenantId, LocalDate date) {
        return jdbc.queryForObject(
            "SELECT count(*) FROM security_event WHERE tenant_id = ? AND type = 'login_failure' AND toDate(created_at) = ?",
            Long.class, tenantId, date);
    }
}
```

| メリット | デメリット |
|---------|----------|
| 既存の JDBC 知識で使える | アプリに ClickHouse 依存が入る |
| 遅延ゼロ | 書き込み先が2箇所（PG + CH）になる |
| バッチINSERTで高速 | 障害時のデータ整合性を自前で管理 |

---

## ② CDC — PeerDB（推奨）

PostgreSQL の WAL（Write-Ahead Log）を監視し、INSERT/UPDATE/DELETE を自動で ClickHouse に同期。**アプリ側の変更ゼロ**。

```
PostgreSQL                    PeerDB               ClickHouse
┌──────────┐  論理レプリ  ┌──────────┐  HTTP   ┌──────────┐
│ INSERT   │ ──────────→ │ WAL解析   │ ─────→ │ INSERT   │
│ security │  ケーション  │ 変換      │  INSERT │ security │
│ _event   │             │ バッチ化  │         │ _event   │
└──────────┘             └──────────┘         └──────────┘
```

```bash
# PeerDB の設定（Kubernetes / Docker）
# PostgreSQL の論理レプリケーションを有効化
# postgresql.conf: wal_level = logical

# ミラー作成
peerdb mirror create \
  --source postgres \
  --destination clickhouse \
  --table security_event \
  --mode cdc
```

| メリット | デメリット |
|---------|----------|
| アプリ変更ゼロ | PeerDB の運用が必要 |
| WAL ベースで高整合性 | 数秒の遅延 |
| INSERT/UPDATE/DELETE 全対応 | PostgreSQL の論理レプリケーション設定が必要 |

> **Note**: ClickHouse Cloud では ClickPipes として PeerDB が組み込まれており、GUIで設定するだけで使えます。

---

## ③ Kafka 経由

既に Kafka を使っている場合、ClickHouse の Kafka テーブルエンジンで直接 consume できる。

```sql
-- ClickHouse 側の設定だけで完結
-- 1. Kafka テーブル（consumer）
CREATE TABLE security_event_kafka (
    id UUID,
    type String,
    tenant_id UUID,
    user_id Nullable(UUID),
    created_at DateTime
) ENGINE = Kafka
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'security-events',
    kafka_group_name = 'clickhouse',
    kafka_format = 'JSONEachRow';

-- 2. 本テーブル
CREATE TABLE security_event (
    ...
) ENGINE = MergeTree()
ORDER BY (tenant_id, type, created_at);

-- 3. 自動転送（Materialized View）
CREATE MATERIALIZED VIEW security_event_mv TO security_event AS
SELECT * FROM security_event_kafka;
```

| メリット | デメリット |
|---------|----------|
| Kafka エコシステムと統合 | Kafka クラスタの運用が必要 |
| 高スループット | 既に Kafka がないなら過剰 |
| スキーマ進化に対応 | |

---

## ④ S3 ファイルロード（バッチ）

PostgreSQL のアーカイブデータを S3 に置いて、ClickHouse から直接読み込む。

```sql
-- S3 上の Parquet ファイルを直接 INSERT
INSERT INTO security_event
SELECT *
FROM s3(
    'https://s3.amazonaws.com/my-bucket/archive/2026/03/*.parquet',
    'access_key',
    'secret_key',
    'Parquet'
);

-- または S3 を直接クエリ（INSERT せずに分析）
SELECT type, count(*)
FROM s3('s3://my-bucket/archive/**/*.parquet', ...)
GROUP BY type;
```

| メリット | デメリット |
|---------|----------|
| 最もシンプル、低コスト | リアルタイム性なし |
| 既存のアーカイブ機構を活用 | Parquet 変換が必要 |
| ClickHouse なしでも S3 + Athena で分析可 | |

> **idp-server との接点**: `archive.process_archived_partitions()` の stub 実装を S3 エクスポートに実装すれば、そのまま使える。

---

## ⑤ PostgreSQL エンジン（直接参照）

ClickHouse から PostgreSQL を直接クエリ。データ移動なし。

```sql
-- ClickHouse 側で PostgreSQL テーブルを定義
CREATE TABLE pg_security_event (
    id UUID,
    type String,
    tenant_id UUID,
    created_at DateTime
)
ENGINE = PostgreSQL(
    'postgres-host:5432',
    'idpserver',
    'security_event',
    'app_user',
    'password'
);

-- ClickHouse から PostgreSQL のデータを直接クエリ
SELECT type, count(*) FROM pg_security_event GROUP BY type;
```

| メリット | デメリット |
|---------|----------|
| データ移動不要 | 毎回 PostgreSQL にクエリ（遅い） |
| セットアップが簡単 | PostgreSQL に負荷がかかる |
| 検証用に便利 | 本番には不向き |

---

## 選択ガイド

| 状況 | おすすめ |
|------|---------|
| アプリ変更したくない | ② CDC (PeerDB) |
| 既に Kafka がある | ③ Kafka |
| コスト最小、遅延OK | ④ S3 ロード |
| まず試したい | ⑤ PostgreSQL エンジン |
| 最速・最大制御 | ① JDBC |

### idp-server の段階的導入

```
Step 1: ⑤ PostgreSQL エンジンで検証（数分で試せる）
Step 2: ④ S3 ロードでアーカイブデータを分析
Step 3: ② CDC で本番リアルタイム同期
```

---

## 次のステップ

- [OLTP + OLAP デュアル構成](dual-architecture): PostgreSQL + ClickHouse の組み合わせ設計
- [AWS 上の構築パターン](aws-deployment): EKS, Cloud, BYOC
