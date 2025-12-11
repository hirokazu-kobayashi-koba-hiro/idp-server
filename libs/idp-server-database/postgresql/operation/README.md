# PostgreSQL Operation Scripts

運用・保守用のSQLスクリプト集です。

## スクリプト一覧

| スクリプト | 説明 | 実行ユーザー |
|-----------|------|-------------|
| `01-create-users.sh` | アプリケーションユーザー作成 | superuser |
| `02-init-extensions.sql` | pg_cron/pg_partman拡張初期化 | superuser |
| `setup-pg-cron-jobs.sql` | pg_cronジョブ登録 | db_owner |
| `aggregate_historical_statistics.sql` | 過去データの統計集計 | db_owner |

---

## データベース構築手順（手動）

Docker Composeを使わずに手動でデータベースを構築する場合の手順です。

### 前提条件

- PostgreSQL 15以上
- pg_cron, pg_partman 拡張がインストール済み
- `postgresql.conf` に以下の設定が必要:
  ```
  shared_preload_libraries = 'pg_cron'
  cron.database_name = 'postgres'
  ```

**Note**: `pg_partman_bgw` は使用せず、pg_cron でパーティション管理をスケジュール実行しています。

**注意**: `cron.database_name = 'postgres'` を設定することで、pg_cronはクロスデータベースモードで動作します。
これにより `cron.schedule_in_database()` 関数を使用して任意のデータベースでジョブを実行できます。

### Step 1: データベース・ユーザー作成

```bash
# 環境変数設定
export POSTGRES_USER=idpserver
export POSTGRES_PASSWORD=<your_password>
export POSTGRES_DB=idpserver
export DB_OWNER_USER=idp
export IDP_DB_ADMIN_PASSWORD=<admin_password>
export IDP_DB_APP_PASSWORD=<app_password>

# ユーザー作成
./01-create-users.sh
```

### Step 2: pg_cron/pg_partman 拡張初期化

```bash
# superuserで実行
# pg_cron は postgres データベースに、pg_partman は idpserver データベースに作成
psql -U postgres -d postgres -f 02-init-extensions.sql
psql -U postgres -d idpserver -f 02-init-partman-extensions.sql

# カスタムDB_OWNER_USERを指定する場合
psql -U postgres -d postgres -v db_owner_user=myuser -f 02-init-extensions.sql
psql -U postgres -d idpserver -v db_owner_user=myuser -f 02-init-partman-extensions.sql
```

**Note**: Docker環境では `02-init-partman.sh` が両方の処理を行います。

### Step 3: Flywayマイグレーション実行

```bash
# Flyway実行（DB_OWNER_USERで実行）
flyway -url=jdbc:postgresql://localhost:5432/idpserver \
       -user=idp \
       -password=<password> \
       migrate
```

### Step 4: pg_cronジョブ登録

```bash
# DB_OWNER_USERで実行
# 重要: pg_cron は postgres データベースにインストールされているため、
#       postgres データベースに接続してジョブを登録します
psql -U idp -d postgres -f setup-pg-cron-jobs.sql
```

**Note**: `setup-pg-cron-jobs.sql` は `cron.schedule_in_database()` を使用して
`idpserver` データベースでジョブを実行するよう設定します。

### 構築順序図

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: ユーザー作成 (01-create-users.sh)                      │
│  ─────────────────────────────────────────────────────────────  │
│  • idp (DB_OWNER_USER) - DDL実行、BYPASSRLS                     │
│  • idp_admin_user - 管理API用、BYPASSRLS                        │
│  • idp_app_user - アプリケーション用、RLS適用                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 2: 拡張初期化 (02-init-partman.sh)                        │
│  ─────────────────────────────────────────────────────────────  │
│  • pg_cron 拡張作成（postgres DB、クロスデータベースモード）     │
│  • pg_partman 拡張作成（idpserver DB、partmanスキーマ）          │
│  • DB_OWNER_USERへの権限付与（cron関数実行権限含む）             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 3: Flywayマイグレーション                                  │
│  ─────────────────────────────────────────────────────────────  │
│  • テーブル作成（V0_9_0__init_lib.sql）                          │
│  • パーティション設定（V0_9_21_1, V0_9_21_2）                    │
│  • RLSポリシー設定                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Step 4: pg_cronジョブ登録 (setup-pg-cron-jobs.sql)             │
│  ─────────────────────────────────────────────────────────────  │
│  • postgres DBに接続してジョブ登録                               │
│  • cron.schedule_in_database() で idpserver を指定              │
│  • partman-maintenance ジョブ（毎日02:00 UTC）                   │
│  • archive-processing ジョブ（毎日03:00 UTC）                    │
└─────────────────────────────────────────────────────────────────┘
```

### Docker Compose との対応

| 手動構築 | Docker Compose |
|----------|---------------|
| `01-create-users.sh` | `postgres-user-init` コンテナ |
| `02-init-extensions.sql` | `02-init-partman.sh` (init スクリプト) |
| Flyway CLI | `flyway-migrator` コンテナ |
| `setup-pg-cron-jobs.sql` | `pg-cron-setup` コンテナ |

---

## aggregate_historical_statistics.sql

過去の `security_event` データを `statistics_monthly`, `statistics_daily_users`, `statistics_monthly_users` テーブルに集計するスクリプトです。

### 用途

- 統計機能の新規導入時に既存データを集計
- 統計データの再計算・修正
- データ移行後の統計再構築

### 前提条件

- PostgreSQL 14以上
- `security_event` テーブルにデータが存在すること
- 統計テーブル（`statistics_monthly`, `statistics_daily_users`, `statistics_monthly_users`）が作成済みであること

### 使用方法

#### 基本実行（過去12ヶ月）

```bash
psql -h <host> -U <user> -d <database> \
  -f aggregate_historical_statistics.sql
```

#### 日付範囲指定

```bash
psql -h <host> -U <user> -d <database> \
  -v start_date="'2024-01-01'" \
  -v end_date="'2024-12-31'" \
  -f aggregate_historical_statistics.sql
```

#### Docker環境での実行

```bash
# コンテナ内で直接実行
docker exec -i <container_name> psql -U <user> -d <database> \
  -v start_date="'2024-01-01'" \
  -v end_date="'2024-12-31'" \
  < aggregate_historical_statistics.sql

# 例: idp-server開発環境
docker exec -i postgres-primary psql -U idpserver -d idpserver \
  -v start_date="'2024-01-01'" \
  -v end_date="'2024-12-31'" \
  < aggregate_historical_statistics.sql
```

### 処理フロー

```
Step 1: statistics_daily_users への DAU ユーザー登録
        └─ security_event から active user events を抽出
        └─ (tenant_id, date, user_id) でユニーク化

Step 2: statistics_monthly_users への MAU ユーザー登録
        └─ security_event から active user events を抽出
        └─ (tenant_id, month, user_id) でユニーク化

Step 3: 集計用一時テーブル作成
        └─ イベント種別ごとの日次カウント
        └─ 累積MAU計算（日ごとの running total）

Step 4: statistics_monthly への集計データ投入
        └─ monthly_summary: MAU + イベント種別合計
        └─ daily_metrics: 日別の DAU, 累積MAU, イベント種別
```

### Active User Events

以下のイベントタイプがアクティブユーザー（DAU/MAU）としてカウントされます：

- `login_success`
- `issue_token_success`
- `refresh_token_success`
- `inspect_token_success`

### 身元確認イベント

身元確認申込み機能では、汎用イベントに加えてtype単位のカスタムイベントも記録されます：

| 操作 | 汎用イベント | type単位イベント |
|------|--------------|------------------|
| 申込み成功 | `identity_verification_application_apply` | `{type}_application_success` |
| 申込み失敗 | `identity_verification_application_failure` | `{type}_application_failure` |
| 承認 | `identity_verification_application_approved` | `{type}_approved` |
| 却下 | `identity_verification_application_rejected` | `{type}_rejected` |
| 後続プロセス成功 | - | `{type}_{process}_success` |
| 後続プロセス失敗 | - | `{type}_{process}_failure` |

例: type=`investment-account-opening`, process=`request-ekyc` の場合
- `investment-account-opening_application_success`
- `investment-account-opening_request-ekyc_success`
- `investment-account-opening_approved`

### 出力データ形式

#### monthly_summary
```json
{
  "mau": 150,
  "login_success": 1200,
  "oauth_authorize": 800,
  "password_success": 1100
}
```

#### daily_metrics
```json
{
  "2024-01-01": {
    "dau": 45,
    "mau": 45,
    "login_success": 120,
    "oauth_authorize": 80
  },
  "2024-01-02": {
    "dau": 52,
    "mau": 85,
    "login_success": 140,
    "oauth_authorize": 95
  }
}
```

- `dau`: その日のユニークユーザー数
- `mau`: その日までの累積ユニークユーザー数（月初からの running total）

### 注意事項

1. **冪等性**: `ON CONFLICT ... DO UPDATE` を使用しているため、複数回実行しても安全です
2. **既存データ**: 同じ (tenant_id, stat_month) のデータは上書きされます
3. **パフォーマンス**: 大量データの場合、日付範囲を分割して実行することを推奨
4. **RLS**: Row Level Security が有効な場合、適切な権限で実行してください

### トラブルシューティング

#### エラー: "relation does not exist"
統計テーブルが未作成です。マイグレーション `V0_10_0__statistics.sql` を実行してください。

#### 集計結果が0件
- `security_event` テーブルにデータがあるか確認
- 日付範囲が正しいか確認
- active user events (`login_success` など) が存在するか確認

#### 累積MAUが増加しない
ユーザーの初回アクティビティ日が正しく記録されているか `statistics_monthly_users.created_at` を確認してください。
