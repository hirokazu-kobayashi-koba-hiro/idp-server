# PostgreSQL 学習ガイド

このディレクトリには、PostgreSQLの運用と開発に関する包括的な学習ドキュメントが含まれています。

---

## 目次

### 概要・基礎

| ドキュメント | 内容 |
|-------------|------|
| [00-overview.md](00-overview.md) | PostgreSQL内部構造ガイド |

---

### DBA（データベース管理者）向け

PostgreSQLのインストール、運用、保守に関するドキュメントです。

| # | ドキュメント | 内容 |
|---|-------------|------|
| 00 | [dba-00-managed-vs-self-hosted.md](dba-00-managed-vs-self-hosted.md) | マネージド vs セルフホスト比較（AWS RDS） |
| 01 | [dba-01-installation.md](dba-01-installation.md) | インストールと初期設定 |
| 02 | [dba-02-backup-recovery.md](dba-02-backup-recovery.md) | バックアップとリカバリ |
| 03 | [dba-03-replication-ha.md](dba-03-replication-ha.md) | レプリケーションと高可用性 |
| 04 | [dba-04-security.md](dba-04-security.md) | セキュリティ設定 |
| 05 | [dba-05-monitoring.md](dba-05-monitoring.md) | 監視とアラート |
| 06 | [dba-06-maintenance.md](dba-06-maintenance.md) | 定期メンテナンス |
| 07 | [dba-07-partitioning.md](dba-07-partitioning.md) | パーティショニング |
| 08 | [dba-08-planner.md](dba-08-planner.md) | クエリプランナーの詳細 |
| 09 | [dba-09-extensions.md](dba-09-extensions.md) | よく使う拡張機能 |

---

### 開発者向け

アプリケーション開発者がPostgreSQLを効果的に使用するためのドキュメントです。

| # | ドキュメント | 内容 |
|---|-------------|------|
| 01 | [dev-01-sql-basics.md](dev-01-sql-basics.md) | SQL基礎（SELECT, INSERT, UPDATE, DELETE, JOIN） |
| 02 | [dev-02-table-design.md](dev-02-table-design.md) | テーブル設計（正規化、データ型、制約） |
| 03 | [dev-03-indexes.md](dev-03-indexes.md) | インデックス設計（B-tree, GIN, GiST, BRIN） |
| 04 | [dev-04-transactions.md](dev-04-transactions.md) | トランザクション（ACID、分離レベル、ロック） |
| 05 | [dev-05-query-optimization.md](dev-05-query-optimization.md) | クエリ最適化（EXPLAIN、実行計画） |
| 06 | [dev-06-connection-pooling.md](dev-06-connection-pooling.md) | コネクションプーリング（PgBouncer, HikariCP） |
| 07 | [dev-07-plpgsql-basics.md](dev-07-plpgsql-basics.md) | PL/pgSQL 基本編（関数、プロシージャ、エラーハンドリング） |
| 08 | [dev-08-plpgsql-advanced.md](dev-08-plpgsql-advanced.md) | PL/pgSQL 応用編（動的SQL、トリガー、パフォーマンス、セキュリティ） |

---

## 学習パス

### 初心者（アプリケーション開発者）

1. **dev-01-sql-basics.md** - SQLの基本操作を習得
2. **dev-02-table-design.md** - 適切なテーブル設計を学ぶ
3. **dev-03-indexes.md** - インデックスの効果的な使い方
4. **dev-04-transactions.md** - トランザクションとデータ整合性
5. **00-overview.md** - 内部構造の基礎知識

### 中級者（パフォーマンスチューニング）

1. **dev-05-query-optimization.md** - クエリの最適化手法
2. **dev-06-connection-pooling.md** - 接続管理の最適化
3. **dev-07-plpgsql-basics.md** - PL/pgSQL基本（関数とプロシージャ）
4. **dev-08-plpgsql-advanced.md** - PL/pgSQL応用（動的SQL、トリガー、最適化）
5. **dba-08-planner.md** - プランナーの深い理解
6. **dba-07-partitioning.md** - 大規模テーブルの分割

### 運用担当者（DBA）

1. **dba-00-managed-vs-self-hosted.md** - 運用方式の選択
2. **dba-01-installation.md** - セットアップの基礎
3. **dba-02-backup-recovery.md** - データ保護の基本
4. **dba-03-replication-ha.md** - 高可用性構成
5. **dba-04-security.md** - セキュリティ強化
6. **dba-05-monitoring.md** - 監視体制の構築
7. **dba-06-maintenance.md** - 日常保守作業
8. **dba-09-extensions.md** - 拡張機能の活用

---

## 関連リソース

### 公式ドキュメント
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [PostgreSQL Internals](https://www.postgresql.org/docs/current/internals.html)

### オンライン書籍
- [The Internals of PostgreSQL](https://www.interdb.jp/pg/) - 内部構造の詳細解説

### AWS RDS
- [Amazon RDS for PostgreSQL User Guide](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html)
- [Amazon RDS Proxy User Guide](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html)
