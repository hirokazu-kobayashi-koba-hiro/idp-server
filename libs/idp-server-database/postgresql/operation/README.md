# PostgreSQL Operation Scripts

運用・保守用のSQLスクリプト集です。

## スクリプト一覧

| スクリプト | 説明 |
|-----------|------|
| `app_user.sql` | アプリケーションユーザー作成 |
| `aggregate_historical_statistics.sql` | 過去データの統計集計 |

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
