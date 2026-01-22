# idp-server パフォーマンステストガイド

[k6](https://k6.io/)、PostgreSQLパフォーマンス分析、合成テストデータを使用した`idp-server`のパフォーマンステストガイド。

> **Note**: MySQLについては [README-mysql.md](./README-mysql.md) を参照

---

## パフォーマンステストの種類

idp-serverがさまざまな条件下で確実に動作することを確認するため、異なる種類のパフォーマンステストを実施します。

| テスト種別 | 説明 |
|-----------|------|
| **Load（負荷）** | 想定される使用状況（例: 500 RPS）での最大持続スループットを特定。**定常状態の動作**に焦点 |
| **Stress（ストレス）** | 想定負荷を超えて**障害モード**、**エラー率**、**グレースフルデグラデーション**を観察 |
| **Spike（スパイク）** | 急激で極端な負荷増加（例: 0から1000 RPS）をテストし、**バースト耐性**を測定 |
| **Soak（浸漬）** | 長時間（1時間以上）典型的な負荷で実行し、**メモリリーク**、**GC問題**、**経時的なパフォーマンス劣化**を検出 |

---

## テストデータの準備

### ユーザーデータ生成

`generate_users.py`を使用してテストユーザーデータを作成します。

- `idp_user`テーブル用ユーザーTSVファイル
- `idp_user_authentication_devices`テーブル用デバイスTSVファイル
- k6 CIBAテスト用テストユーザーJSONファイル

#### 推奨: 複合セットアップ（1M + 9x100K）

大規模シングルテナントテストとマルチテナントテストの両方をサポート：

```bash
# 1. 10テナントを登録
./performance-test/scripts/register-tenants.sh -n 10

# 2. 生成: 最初のテナント100万ユーザー、他の9テナント各10万ユーザー
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000 \
  --first-tenant-users 1000000

# 3. PostgreSQLにインポート
./performance-test/scripts/import_users.sh multi_tenant_1m+9x100k

# 4. k6テスト用セットアップ
cp ./performance-test/data/multi_tenant_1m+9x100k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json
```

**結果:**
- テナント1: 1,000,000ユーザー（大規模テスト用）
- テナント2-10: 各100,000ユーザー（マルチテナントテスト用）
- 合計: 1,900,000ユーザー

---

## k6のインストール

### 標準版k6

```bash
brew install k6
```

### SQLite対応版k6（FIDO2テスト用）

FIDO2のフルフローテストには、credentialを永続化するためSQLite対応版k6が必要です。

```bash
# Goのインストール（未インストールの場合）
brew install go

# xk6ビルドツールのインストール
go install go.k6.io/xk6/cmd/xk6@latest

# SQLite対応版k6をビルド（CGO有効が必須）
cd /path/to/idp-server
CGO_ENABLED=1 ~/go/bin/xk6 build \
  --with github.com/grafana/xk6-sql \
  --with github.com/grafana/xk6-sql-driver-sqlite3 \
  --output ./performance-test/k6-sqlite
```

> **Note**: go-sqlite3はCGOを必要とするため、`CGO_ENABLED=1`が必須です。

---

## 環境変数の設定

### ローカル環境

```bash
export BASE_URL=https://api.local.dev
export TENANT_ID=67e7eae6-62b0-4500-9eff-87459f63fc66
export CLIENT_ID=clientSecretPost
export CLIENT_SECRET=clientSecretPostPassword1234567890...
export REDIRECT_URI=https://www.certification.openid.net/test/a/idp_oidc_basic/callback
```

---

## テストの実行

### 結果ディレクトリの作成

```bash
mkdir -p performance-test/result/stress
mkdir -p performance-test/result/load
```

### ストレステスト

```bash
# 認可リクエスト
k6 run --summary-export=./performance-test/result/stress/scenario-1-authorization-request.json \
  ./performance-test/stress/scenario-1-authorization-request.js

# Backchannel Authentication
k6 run --summary-export=./performance-test/result/stress/scenario-2-bc.json \
  ./performance-test/stress/scenario-2-bc.js

# CIBA（login_hintパターン別）
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-device.json \
  ./performance-test/stress/scenario-3-ciba-device.js
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-sub.json \
  ./performance-test/stress/scenario-3-ciba-sub.js
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-email.json \
  ./performance-test/stress/scenario-3-ciba-email.js

# トークン（パスワードグラント）
k6 run --summary-export=./performance-test/result/stress/scenario-4-token-password.json \
  ./performance-test/stress/scenario-4-token-password.js

# トークン（クライアントクレデンシャル）
k6 run --summary-export=./performance-test/result/stress/scenario-5-token-client-credentials.json \
  ./performance-test/stress/scenario-5-token-client-credentials.js

# JWKS
k6 run --summary-export=./performance-test/result/stress/scenario-6-jwks.json \
  ./performance-test/stress/scenario-6-jwks.js

# トークンイントロスペクション
k6 run --summary-export=./performance-test/result/stress/scenario-7-token-introspection.json \
  ./performance-test/stress/scenario-7-token-introspection.js

# デバイス認証
k6 run --summary-export=./performance-test/result/stress/scenario-8-authentication-device.json \
  ./performance-test/stress/scenario-8-authentication-device.js

# 本人確認申請
k6 run --summary-export=./performance-test/result/stress/scenario-9-identity-verification-application.json \
  ./performance-test/stress/scenario-9-identity-verification-application.js
```

### 負荷テスト

```bash
k6 run ./performance-test/load/scenario-1-ciba-login.js
k6 run ./performance-test/load/scenario-2-multi-ciba-login.js

# deleteExpiredDataシナリオには管理API認証情報が必要
export IDP_SERVER_API_KEY=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
export IDP_SERVER_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
k6 run ./performance-test/load/scenario-3-peak-login.js

k6 run ./performance-test/load/scenario-4-authorization-code.js
```

---

## FIDO2パフォーマンステスト

FIDO2（WebAuthn/Passkey）のフルフローテストは、ECDSA署名生成を含むため特別な対応が必要です。

### アーキテクチャ

```
performance-test/
├── k6-sqlite                      # SQLite対応k6バイナリ
├── libs/
│   ├── fido2.js                   # FIDO2暗号処理ライブラリ
│   │                              # - CBOR エンコーディング
│   │                              # - ES256 (P-256 ECDSA) 鍵生成・署名
│   │                              # - AuthenticatorData生成
│   │                              # - JWK形式での秘密鍵エクスポート
│   └── credential-store.js        # SQLiteストレージ
│                                  # - credentialの永続化
│                                  # - signCount管理
├── data/
│   └── fido2-credentials.db       # 自動生成されるSQLiteデータベース
└── stress/
    ├── scenario-10-fido2-registration.js   # 登録フルフロー
    └── scenario-11-fido2-authentication.js # 認証フルフロー
```

### SQLiteスキーマ

```sql
CREATE TABLE credentials (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  credential_id TEXT UNIQUE NOT NULL,  -- Base64URL形式
  private_key_jwk TEXT NOT NULL,       -- JWK形式の秘密鍵（JSON）
  email TEXT NOT NULL,
  user_id TEXT,
  sign_count INTEGER DEFAULT 0,        -- 認証のたびにインクリメント
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  last_used_at TEXT
);
```

### テストの実行

#### Step 1: FIDO2登録テスト（credentialをSQLiteに保存）

```bash
cd /path/to/idp-server

# DBをクリアして新規登録（初回または再テスト時）
./performance-test/k6-sqlite run -e CLEAR_DB=true \
  ./performance-test/stress/scenario-10-fido2-registration.js

# 追加登録（既存データを保持）
./performance-test/k6-sqlite run \
  ./performance-test/stress/scenario-10-fido2-registration.js
```

**テストフロー:**
1. 認可開始
2. ユーザー登録（initial-registration）
3. Email MFA チャレンジ・検証
4. FIDO2登録チャレンジ取得
5. クレデンシャル生成（ECDSA鍵ペア）
6. FIDO2登録完了
7. **SQLiteに秘密鍵を保存**

#### Step 2: FIDO2認証テスト（SQLiteからcredentialを読み込み）

```bash
./performance-test/k6-sqlite run \
  ./performance-test/stress/scenario-11-fido2-authentication.js
```

**テストフロー:**
1. SQLiteからcredentialを取得（VU/iterationでラウンドロビン）
2. 認可開始
3. FIDO2認証チャレンジ取得
4. アサーション生成（保存された秘密鍵で署名）
5. FIDO2認証完了
6. **SQLiteのsignCountを更新**

### 環境変数

| 変数 | デフォルト値 | 説明 |
|------|-------------|------|
| `BASE_URL` | https://api.local.dev | idp-serverのベースURL |
| `TENANT_ID` | (設定ファイルから) | テナントID |
| `RP_ID` | local.dev | Relying Party ID |
| `ORIGIN` | https://auth.local.dev | WebAuthnのorigin |
| `VU_COUNT` | 10 | 同時仮想ユーザー数 |
| `DURATION` | 30s | テスト期間 |
| `CLEAR_DB` | false | trueで既存データをクリア |

### signCountの管理

FIDO2仕様では、認証のたびにsignCountがインクリメントされ、サーバー側で検証されます。

- **登録時**: `sign_count = 0` でSQLiteに保存
- **認証時**:
  1. SQLiteから現在のsignCountを取得
  2. `sign_count + 1` でassertion生成
  3. 認証成功後、SQLiteのsignCountを更新

これにより、同じcredentialを複数回認証してもsignCountが正しくインクリメントされます。

### 出力例

```
=== FIDO2 Registration Full Flow Performance Summary ===
Total Iterations: 385
Iterations/sec: 12.58

Step Durations (avg / p95):
  Authorization:        36.10ms / 89.00ms
  User Registration:    217.71ms / 375.80ms
  Email MFA:            346.21ms / 520.40ms
  FIDO2 Challenge:      32.93ms / 86.40ms
  Credential Gen:       2.55ms / 6.00ms
  FIDO2 Complete:       51.09ms / 115.00ms
  Total:                687.02ms / 950.60ms

Errors:
  Authorization: 0
  User Registration: 0
  Email MFA: 0
  FIDO2: 0
```

---

## リソースモニタリング

### CPU・メモリ使用量

```bash
docker stats $(docker compose ps -q idp-server)
```

### アプリケーションログ

各ステップの実行時間を分析する場合、application.yamlでファイルログを有効化：

```yaml
logging:
  level:
    root: info
    web: info
  file:
    name: logs/idp-server.log
    pattern:
      file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

---

## 結果の分析

### k6結果の例

```
checks_total.......................: 13585  444.5355/s
checks_succeeded...................: 99.85% 13565 out of 13585
checks_failed......................: 0.14%  20 out of 13585
http_req_duration..................: avg=223.44ms p(95)=512.14ms
iterations.........................: 2717   88.91/s
```

| メトリクス | 説明 |
|-----------|------|
| `checks_total` | `check()`呼び出しの総数 |
| `http_req_duration` | HTTPリクエストの完了時間 |
| `iterations` | シナリオ全体の反復回数 |

### PostgreSQL: pg_stat_statements

`pg_stat_statements`はすべてのクエリの実行統計を追跡する強力なPostgreSQL拡張機能です。

#### 拡張機能の有効化

Docker Composeの場合：

```yaml
services:
  postgresql:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: idpserver
    command: ["postgres", "-c", "shared_preload_libraries=pg_stat_statements"]
```

#### 拡張機能の作成（初回のみ）

```sql
CREATE EXTENSION pg_stat_statements;
```

#### クエリ統計の表示

```sql
SELECT query, calls, total_exec_time, mean_exec_time, rows,
       shared_blks_hit, shared_blks_read
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
```

| カラム | 説明 |
|--------|------|
| `query` | 正規化されたSQL（リテラルは`?`に置換） |
| `calls` | クエリの実行回数 |
| `total_exec_time` | 総実行時間（ミリ秒） |
| `mean_exec_time` | 平均実行時間（ミリ秒） |
| `rows` | 返された行の総数 |
| `shared_blks_hit` | キャッシュヒット（高いほど良い） |
| `shared_blks_read` | ディスク読み取り（高いとパフォーマンス低下の可能性） |

#### 統計のリセット

パフォーマンステスト前に統計をリセット：

```sql
SELECT pg_stat_statements_reset();
```

---

## Tips

### Dockerディスク使用量の確認

```bash
docker system df
```

### Dockerデータのクリーンアップ

```bash
docker system prune -a
docker volume prune
docker builder prune
```
