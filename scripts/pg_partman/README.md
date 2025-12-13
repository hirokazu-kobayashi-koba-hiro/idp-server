# pg_partman 検証スクリプト

Amazon RDS for PostgreSQL の `pg_partman` 拡張を使用したパーティション自動管理の検証用スクリプト集です。

## 前提条件

- PostgreSQL 12.5 以降
- Docker環境（ローカル検証用）
- AWS RDS for PostgreSQL（本番環境）

## スクリプト一覧

| ファイル名 | 説明 |
|-----------|------|
| `setup-pg_partman.sh` | pg_partman拡張のセットアップ |
| `statistics-users-pg_partman.sh` | 統計ユーザーテーブルのパーティション設定 |
| `verify-pg_partman.sh` | パーティション動作検証 |
| `cleanup-pg_partman.sh` | 検証環境のクリーンアップ |

## 使用方法

### 1. セットアップ

```bash
# pg_partman拡張をインストール
./scripts/pg_partman/setup-pg_partman.sh

# 統計ユーザーテーブルにパーティション設定
./scripts/pg_partman/statistics-users-pg_partman.sh
```

### 2. 検証

```bash
# パーティション動作を検証
./scripts/pg_partman/verify-pg_partman.sh
```

### 3. クリーンアップ

```bash
# 検証環境を削除
./scripts/pg_partman/cleanup-pg_partman.sh
```

## 対象テーブル

| テーブル | パーティション戦略 | 間隔 | 保持期間 |
|---------|------------------|------|---------|
| `statistics_daily_users` | RANGE | 月別 | 6ヶ月 |
| `statistics_monthly_users` | RANGE | 年別 | 3年 |
| `statistics_yearly_users` | RANGE | 年別 | 5年 |

## pg_partman vs 手動管理

### pg_partman のメリット

1. **自動パーティション作成**: `p_premake` で事前作成
2. **自動保持管理**: `retention` 設定で古いパーティション自動削除
3. **メンテナンス簡素化**: `run_maintenance_proc()` で一括管理
4. **AWS RDS統合**: pg_cron との連携でフルマネージド運用

### 手動管理との比較

| 項目 | pg_partman | 手動cronスクリプト |
|-----|-----------|------------------|
| セットアップ | 拡張インストール必要 | スクリプトのみ |
| パーティション作成 | 自動 | cronジョブ必要 |
| 保持管理 | 設定のみ | DELETEロジック必要 |
| DEFAULTパーティション | 自動作成 | 手動作成必要 |
| LISTパーティション | 非推奨 | 対応可能 |
| 可搬性 | PostgreSQL依存 | DB非依存 |

## 注意事項

### ローカル検証環境

Docker公式PostgreSQLイメージには `pg_partman` が含まれていない場合があります。
その場合は以下のいずれかの方法で対応：

1. `pgxn` でインストール
2. `pg_partman` 同梱のDockerイメージを使用
3. ソースからビルド

### AWS RDS環境

RDS for PostgreSQL 12.5以降では `pg_partman` がプリインストールされています。
`rds_superuser` ロールで拡張を有効化できます。

## 参考リンク

- [AWS RDS PostgreSQL パーティション管理](https://docs.aws.amazon.com/ja_jp/AmazonRDS/latest/UserGuide/PostgreSQL_Partitions.html)
- [pg_partman GitHub](https://github.com/pgpartman/pg_partman)
- [PostgreSQL パーティショニング](https://www.postgresql.org/docs/current/ddl-partitioning.html)
