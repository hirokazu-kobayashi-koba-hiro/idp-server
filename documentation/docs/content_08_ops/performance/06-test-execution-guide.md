# 性能テスト実行ガイド

本ドキュメントでは、性能テストの実行手順を step-by-step で説明する。

---

## 前提条件

- Docker / Docker Compose がインストールされていること
- k6 がインストールされていること（`brew install k6`）
- Python 3 がインストールされていること
- PostgreSQL クライアント（psql）がインストールされていること

---

## 実行順序の概要

```
Phase 1: Docker環境起動
    ↓
Phase 2: テストデータ一括準備（全測定に必要なデータを事前投入）
    ├── 2.1: テナント登録（最大10テナント）
    ├── 2.2: ユーザーデータ生成（各テナント100万件）
    └── 2.3: PostgreSQL COPY投入
    ↓
Phase 3: ストレステスト実行
    ↓
Phase 4: ロードテスト実行（パラメータ変更のみ、データ追加不要）
    ├── ベースライン測定
    ├── 同時負荷影響測定
    ├── マルチテナント影響測定（TENANT_COUNT で制御）
    ├── データスケール影響測定
    └── 負荷限界検証
```

:::tip 一括データ準備の利点
- テスト間の一貫性が保証される
- 測定ごとのデータ追加が不要
- パラメータ（`TENANT_COUNT`, `VU_COUNT`等）で各測定を制御
:::

---

## Phase 1: Docker環境起動

### Step 1.1: Docker Compose で起動

```bash
# プロジェクトルートで実行
docker compose up -d
```

### Step 1.2: 起動確認

```bash
# コンテナ状態確認
docker compose ps

# ヘルスチェック
curl http://localhost:8080/health
```

### Step 1.3: pg_stat_statements 有効化

```bash
docker exec -it postgres-primary psql -U idpserver -d idpserver -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
```

---

## Phase 2: テストデータ一括準備

全測定（ベースライン、同時負荷、マルチテナント、データスケール）に必要なデータを事前に一括投入する。

:::note 前提条件
- Docker環境が起動済みであること（Phase 1完了）
- `.env` ファイルに管理者情報が設定されていること
:::

### テスト方針に基づくデータ規模

| 測定観点 | テナント数 | ユーザー/テナント | 備考 |
|---------|----------|-----------------|------|
| ベースライン | 1 | 100,000 | 基準値測定 |
| 同時負荷 | 1 | 100,000 | VU/レートを変更 |
| マルチテナント | 1→5→10 | 100,000 | TENANT_COUNTで制御 |
| データスケール | 1 | 10万→100万 | 段階的に測定 |

**推奨構成**: 10テナント × 100,000ユーザー = 1,000,000ユーザー

### Step 2.1: テナント登録

オンボーディングAPIで全テナントを一括登録する。

```bash
# 全測定に必要な最大テナント数（10）を一括登録
./performance-test/scripts/register-tenants.sh -n 10

# 追加でテナントを登録する場合（既存を保持）
./performance-test/scripts/register-tenants.sh -n 5 -a

# ドライラン（実際には登録しない）
./performance-test/scripts/register-tenants.sh -n 10 -d true
```

パラメータ説明：
| パラメータ | 説明 |
|----------|------|
| -n | 登録するテナント数（必須） |
| -a | 追加モード：既存テナントを保持して追加 |
| -d | ドライラン（true/false、デフォルト: false） |
| -b | ベースURL（デフォルト: .envの`AUTHORIZATION_SERVER_URL`） |

生成されるファイル：
- `performance-test/data/performance-test-tenant.json` - テナント情報

確認：
```bash
cat ./performance-test/data/performance-test-tenant.json | jq 'length'
# → 10
```

### Step 2.2: ユーザーデータ生成

各テナントにユーザーデータを生成する。

```bash
# 推奨構成: 最初のテナント100万ユーザー、他9テナント各10万ユーザー
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000 \
  --first-tenant-users 1000000

# 均等構成: 全テナント各10万ユーザー
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000
```

パラメータ説明：
| パラメータ | 説明 |
|----------|------|
| --tenants-file | テナント情報JSONファイル |
| --users | 各テナントのユーザー数 |
| --first-tenant-users | 最初のテナントのみ別のユーザー数を指定（オプション） |

生成されるファイル（推奨構成の場合）：
| ファイル | 内容 |
|---------|------|
| `multi_tenant_1m+9x100k_users.tsv` | 全テナントのユーザーデータ |
| `multi_tenant_1m+9x100k_devices.tsv` | 全テナントの認証デバイスデータ |
| `multi_tenant_1m+9x100k_test_users.json` | k6テスト用（各テナント500ユーザー） |

### Step 2.3: データ投入

`import_users.sh` スクリプトでデータを投入する。

```bash
# 推奨構成のデータを投入
./performance-test/scripts/import_users.sh multi_tenant_1m+9x100k

# 均等構成の場合
./performance-test/scripts/import_users.sh multi_tenant_10x100k
```

### Step 2.4: k6テスト用JSONの配置

```bash
# 推奨構成の場合
cp ./performance-test/data/multi_tenant_1m+9x100k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json
```

### Step 2.5: 投入確認

```bash
# テナント別ユーザー数を確認
docker exec postgres-primary psql -U idpserver -d idpserver -c "
SELECT tenant_id, COUNT(*) as user_count
FROM idp_user
GROUP BY tenant_id
ORDER BY user_count DESC
LIMIT 15;
"

# 合計確認
docker exec postgres-primary psql -U idpserver -d idpserver -c "
SELECT
  (SELECT COUNT(*) FROM idp_user) as total_users,
  (SELECT COUNT(*) FROM idp_user_authentication_devices) as total_devices,
  (SELECT COUNT(DISTINCT tenant_id) FROM idp_user) as tenant_count;
"
```

期待結果（10テナント × 10万ユーザー）：
```
 total_users | total_devices | tenant_count
-------------+---------------+--------------
     1000000 |       1000000 |           10
```

---

## Phase 3: ストレステスト実行

### 結果ディレクトリ作成

```bash
mkdir -p performance-test/result/stress
mkdir -p performance-test/result/load
```

### Step 3.1: 認可リクエスト

```bash
k6 run --summary-export=./performance-test/result/stress/scenario-1-authorization-request.json \
  ./performance-test/stress/scenario-1-authorization-request.js
```

（以下のストレステスト項目は変更なし）

---

## Phase 4: ロードテスト実行

ロードテストは環境変数でパラメータをカスタマイズ可能。
Phase 2 でデータ投入済みのため、各測定はパラメータ変更のみで実施。

### Step 4.0: 投入確認

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "
SELECT
  (SELECT COUNT(*) FROM idp_user) as users,
  (SELECT COUNT(*) FROM idp_user_authentication_devices) as devices;
"
```

期待結果（10テナント × 10万ユーザー）：
```
 total_users | total_devices | tenant_count
-------------+---------------+--------------
     1000000 |       1000000 |           10
```

### 環境変数パラメータ一覧

| シナリオ | パラメータ | デフォルト | 説明 |
|---------|-----------|-----------|------|
| scenario-1 | `VU_COUNT` | 50 | 同時VU数 |
| | `MAX_VU_COUNT` | VU_COUNT×2 | 最大VU数 |
| | `LOGIN_RATE` | 20 | ログインリクエスト/秒 |
| | `INTROSPECT_RATE` | 80 | Introspectリクエスト/秒 |
| | `DURATION` | 5m | テスト時間 |
| scenario-2 | `TENANT_COUNT` | 全テナント | 使用するテナント数（マルチテナント測定用） |
| | `TOTAL_VU_COUNT` | 50 | 全テナント合計VU数 |
| | `TOTAL_RATE` | 20 | 全テナント合計リクエスト/秒 |
| | `DURATION` | 5m | テスト時間 |
| scenario-3 | `PEAK_RATE` | 30 | ピークリクエスト/秒 |
| | `VU_COUNT` | 5 | 初期VU数 |
| | `MAX_VU_COUNT` | 20 | 最大VU数 |
| | `RAMP_UP_DURATION` | 3m | ランプアップ時間 |
| | `SUSTAIN_DURATION` | 5m | 維持時間 |
| | `RAMP_DOWN_DURATION` | 2m | ランプダウン時間 |
| scenario-4 | `TOTAL_VU_COUNT` | 30 | 全テナント合計VU数 |
| | `TOTAL_RATE` | 10 | 全テナント合計リクエスト/秒 |
| | `DURATION` | 5m | テスト時間 |

### Step 7.1: シングルテナントCIBA

```bash
# デフォルト設定で実行
k6 run ./performance-test/load/scenario-1-ciba-login.js

# カスタム設定で実行
VU_COUNT=100 LOGIN_RATE=50 DURATION=10m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js
```

### Step 7.2: マルチテナントCIBA

```bash
# デフォルト設定で実行
k6 run ./performance-test/load/scenario-2-multi-ciba-login.js

# カスタム設定で実行
TOTAL_VU_COUNT=100 TOTAL_RATE=50 DURATION=10m \
  k6 run ./performance-test/load/scenario-2-multi-ciba-login.js
```

### Step 7.3: ピーク負荷

```bash
# デフォルト設定で実行
k6 run ./performance-test/load/scenario-3-peak-login.js

# カスタム設定で実行
PEAK_RATE=100 MAX_VU_COUNT=50 SUSTAIN_DURATION=10m \
  k6 run ./performance-test/load/scenario-3-peak-login.js
```

### Step 7.4: 認可コードフロー

パスワード認証 → トークン発行 → Userinfo → Introspectionのフローを実行。

```bash
# デフォルト設定で実行
k6 run ./performance-test/load/scenario-4-authorization-code.js

# カスタム設定で実行
TOTAL_VU_COUNT=60 TOTAL_RATE=20 DURATION=10m \
  k6 run ./performance-test/load/scenario-4-authorization-code.js
```

---

## Phase 7.5: 測定観点別テスト実行

[テスト方針](./07-test-strategy)に基づいた測定観点別の実行手順。

### 7.5.1: ベースライン測定

最小構成での基準性能値を取得。

```bash
# シングルテナント、低負荷で実行
VU_COUNT=10 LOGIN_RATE=5 INTROSPECT_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/baseline.json

# 認可コードフローのベースライン
TOTAL_VU_COUNT=10 TOTAL_RATE=5 DURATION=5m \
  k6 run ./performance-test/load/scenario-4-authorization-code.js \
  --summary-export=./performance-test/result/load/baseline-authcode.json
```

### 7.5.2: 同時負荷影響測定

VU数を段階的に増加させてブレークポイントを特定。

```bash
# Step 1: 50 VU
VU_COUNT=50 LOGIN_RATE=100 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/load-vu50.json

# Step 2: 100 VU
VU_COUNT=100 LOGIN_RATE=200 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/load-vu100.json

# Step 3: 200 VU
VU_COUNT=200 LOGIN_RATE=400 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/load-vu200.json

# Step 4: 500 VU
VU_COUNT=500 LOGIN_RATE=1000 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/load-vu500.json
```

### 7.5.3: マルチテナント影響測定

テナント数を変えてRLSオーバーヘッドを測定。
`TENANT_COUNT` パラメータで使用するテナント数を制御（データ追加不要）。

:::note 前提条件
Phase 2 で10テナント分のデータが投入済みであること。
:::

```bash
# Step 1: 1テナント（ベースライン）
TENANT_COUNT=1 TOTAL_VU_COUNT=50 TOTAL_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-2-multi-ciba-login.js \
  --summary-export=./performance-test/result/load/multi-tenant-1.json

# Step 2: 5テナント
TENANT_COUNT=5 TOTAL_VU_COUNT=50 TOTAL_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-2-multi-ciba-login.js \
  --summary-export=./performance-test/result/load/multi-tenant-5.json

# Step 3: 10テナント
TENANT_COUNT=10 TOTAL_VU_COUNT=50 TOTAL_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-2-multi-ciba-login.js \
  --summary-export=./performance-test/result/load/multi-tenant-10.json
```

### 7.5.4: データスケール影響測定

ユーザー数を変えて性能劣化を測定。

:::warning 独立した測定として実施
この測定はデータ規模による性能差を比較するため、Phase 2とは独立して実施する。

**各ステップの手順**：
1. ユーザーデータのクリーンアップ
   ```bash
   docker exec postgres-primary psql -U idpserver -d idpserver -c "TRUNCATE idp_user CASCADE;"
   ```
2. 指定規模のデータを生成・投入
3. テスト実行

**注意**: この測定でPhase 2のユーザーデータは削除される。測定後に他のテストを実施する場合は、Phase 2を再実行すること。
:::

```bash
# Step 1: 10,000ユーザー（クリーン環境で実施）
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 10000
./performance-test/scripts/import_users.sh multi_tenant_10x10k
cp ./performance-test/data/multi_tenant_10x10k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json

VU_COUNT=50 LOGIN_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/scale-10k.json

# Step 2: 100,000ユーザー
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000
./performance-test/scripts/import_users.sh multi_tenant_10x100k
cp ./performance-test/data/multi_tenant_10x100k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json

VU_COUNT=50 LOGIN_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/scale-100k.json

# Step 3: 1,000,000ユーザー
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000 \
  --first-tenant-users 1000000
./performance-test/scripts/import_users.sh multi_tenant_1m+9x100k
cp ./performance-test/data/multi_tenant_1m+9x100k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json

VU_COUNT=50 LOGIN_RATE=20 DURATION=5m \
  k6 run ./performance-test/load/scenario-1-ciba-login.js \
  --summary-export=./performance-test/result/load/scale-1m.json
```

:::tip 簡易測定
推奨構成（1テナント100万 + 9テナント10万）を使用し、TENANT_INDEX=0 で最初のテナント（100万ユーザー）を指定することで大規模データスケールの影響を測定できる。
:::

### 7.5.5: 負荷限界検証

ピーク負荷を段階的に増加させてブレークポイントを特定。

```bash
# Step 1: 30 req/s
PEAK_RATE=30 MAX_VU_COUNT=20 \
  k6 run ./performance-test/load/scenario-3-peak-login.js \
  --summary-export=./performance-test/result/load/peak-30.json

# Step 2: 50 req/s
PEAK_RATE=50 MAX_VU_COUNT=30 \
  k6 run ./performance-test/load/scenario-3-peak-login.js \
  --summary-export=./performance-test/result/load/peak-50.json

# Step 3: 100 req/s
PEAK_RATE=100 MAX_VU_COUNT=60 \
  k6 run ./performance-test/load/scenario-3-peak-login.js \
  --summary-export=./performance-test/result/load/peak-100.json

# Step 4: 200 req/s
PEAK_RATE=200 MAX_VU_COUNT=120 \
  k6 run ./performance-test/load/scenario-3-peak-login.js \
  --summary-export=./performance-test/result/load/peak-200.json
```

:::tip 結果の比較
各ステップの結果JSONを比較して、エラー率の増加開始点やレイテンシ劣化曲線を確認してください。
```bash
# 結果サマリーの確認
cat ./performance-test/result/load/load-vu*.json | jq '{file: input_filename, http_req_duration_p95: .metrics.http_req_duration.values.p95}'
```
:::

---

## Phase 8: 結果分析

### Step 8.1: pg_stat_statements確認

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "
SELECT
  query,
  calls,
  round(total_exec_time::numeric, 2) as total_time_ms,
  round(mean_exec_time::numeric, 2) as mean_time_ms,
  rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
"
```

### Step 8.2: 統計リセット（次回テスト前）

```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "SELECT pg_stat_statements_reset();"
```

### Step 8.3: リソース監視

```bash
# テスト中に別ターミナルで実行
docker stats $(docker compose ps -q idp-server)
```

---

## Phase 9: クリーンアップ

### ユーザーデータのクリーンアップ

性能テスト用テナントのユーザーデータを削除する。

```bash
# 特定テナントのユーザーデータを削除
TENANT_ID="<対象テナントID>"
docker exec postgres-primary psql -U idpserver -d idpserver -c "
DELETE FROM idp_user WHERE tenant_id = '$TENANT_ID';
"

# 全テナントのユーザーデータを削除する場合（注意: 全データが削除される）
# docker exec postgres-primary psql -U idpserver -d idpserver -c "TRUNCATE idp_user CASCADE;"
```

### Docker環境停止

```bash
docker compose down
```

---

## 一括実行スクリプト

全ストレステストを順次実行する場合：

```bash
#!/bin/bash
set -e

RESULT_DIR="./performance-test/result/stress"
STRESS_DIR="./performance-test/stress"

mkdir -p $RESULT_DIR

scenarios=(
  "scenario-1-authorization-request"
  "scenario-2-bc"
  "scenario-3-ciba-device"
  "scenario-3-ciba-sub"
  "scenario-3-ciba-email"
  "scenario-3-ciba-phone"
  "scenario-3-ciba-ex-sub"
  "scenario-4-token-password"
  "scenario-5-token-client-credentials"
  "scenario-6-jwks"
  "scenario-7-token-introspection"
  "scenario-8-authentication-device"
  "scenario-9-identity-verification-application"
)

for scenario in "${scenarios[@]}"; do
  echo "=========================================="
  echo "Running: $scenario"
  echo "=========================================="
  k6 run --summary-export="${RESULT_DIR}/${scenario}.json" "${STRESS_DIR}/${scenario}.js"
  echo ""
  sleep 5  # クールダウン
done

echo "All stress tests completed!"
```

---

## トラブルシューティング

### データ投入エラー

```
ERROR: duplicate key value violates unique constraint
```

→ 既存データを削除してから再投入：
```bash
PGPASSWORD=idpserver psql -U idpserver -d idpserver -h localhost -p 5432 -c "TRUNCATE idp_user CASCADE;"
```

### k6接続エラー

```
ERRO[0001] Request Failed error="Get http://localhost:8080/..."
```

→ 環境変数とサーバー起動を確認：
```bash
echo $BASE_URL
curl $BASE_URL/health
```

### メモリ不足

```
java.lang.OutOfMemoryError: Java heap space
```

→ JVMヒープサイズを増加：
```yaml
environment:
  JAVA_TOOL_OPTIONS: "-Xms1g -Xmx4g"
```

---

## 関連ドキュメント

- [テスト環境](./01-test-environment.md)
- [ストレステスト結果](./02-stress-test-results.md)
- [チューニングガイド](./05-tuning-guide.md)
