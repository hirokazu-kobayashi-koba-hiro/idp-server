---
name: test-performance
description: 性能テスト（Performance Testing）の実行・分析・レポート生成を行う際に使用。k6によるストレステスト/ロードテスト、テストデータ準備、結果分析、レポート生成、チューニングガイドに役立つ。
---

# 性能テスト開発ガイド

## ドキュメント

- `performance-test/README.md` - 性能テスト概要・実行手順
- `documentation/docs/content_08_ops/performance/` - 性能検証ドキュメント一式

---

## 機能概要

idp-serverの性能テストはk6を使用して実行。

- **ストレステスト**: システムの限界を特定（単体API）
- **ロードテスト**: 想定負荷での安定性検証（E2Eフロー）
- **スケーラビリティテスト**: マルチテナント・ユーザー数増加時の性能

---

## ディレクトリ構成

```
performance-test/
├── README.md                           # 実行手順
├── load/                               # ロードテストシナリオ
│   ├── scenario-1-ciba-login.js       # CIBA ログイン
│   ├── scenario-2-multi-ciba-login.js # マルチテナントCIBA
│   ├── scenario-3-peak-login.js       # ピーク負荷
│   └── scenario-4-authorization-code.js # 認可コードフロー
│
├── stress/                             # ストレステストシナリオ
│   ├── scenario-1-authorization-request.js  # 認可リクエスト
│   ├── scenario-2-bc.js                     # CIBA BC Request
│   ├── scenario-3-ciba-*.js                 # CIBA各種パターン
│   ├── scenario-4-token-password.js         # Password Grant
│   ├── scenario-5-token-client-credentials.js # Client Credentials
│   ├── scenario-6-jwks.js                   # JWKS
│   ├── scenario-7-token-introspection.js    # Token Introspection
│   ├── scenario-8-authentication-device.js  # 認証デバイス
│   └── scenario-9-identity-verification-application.js # 身元確認
│
├── scripts/                            # データ準備・実行スクリプト
│   ├── register-tenants.sh            # テナント登録
│   ├── generate_users.py              # ユーザーデータ生成
│   ├── import_users.sh                # PostgreSQL投入
│   ├── run-stress-test.sh             # ストレステスト実行
│   ├── generate-daily-report.sh       # 日報レポート生成
│   └── generate-report.sh            # 個別レポート生成
│
├── data/                               # テストデータ
│   └── performance-test-tenant.json   # テナント設定
│
└── result/                             # テスト結果
    └── stress/
        └── YYYY-MM-DD/                # 日付ディレクトリ
            ├── scenario-*.json        # 実行結果
            ├── execution-log.json     # 実行履歴
            └── daily-report.md        # 日報レポート
```

---

## 前提条件

```bash
# k6 インストール
brew install k6

# Python 3（ユーザーデータ生成用）
python3 --version

# PostgreSQL クライアント（データ投入用）
psql --version
```

---

## クイックスタート

### 1. テストデータ準備

```bash
# テナント登録（10テナント）
./performance-test/scripts/register-tenants.sh -n 10

# ユーザーデータ生成（1テナント100万 + 9テナント各10万）
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000 \
  --first-tenant-users 1000000

# PostgreSQL投入
./performance-test/scripts/import_users.sh multi_tenant_1m+9x100k

# k6用データ設定
cp ./performance-test/data/multi_tenant_1m+9x100k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json
```

### 2. 環境変数設定

```bash
export BASE_URL=https://api.local.dev
# または
export BASE_URL=http://localhost:8080
```

### 3. テスト実行

```bash
# ストレステスト（Client Credentials）
k6 run ./performance-test/stress/scenario-5-token-client-credentials.js

# ロードテスト（CIBAログイン）
k6 run ./performance-test/load/scenario-1-ciba-login.js

# パラメータ指定
VU_COUNT=200 DURATION=60s k6 run ./performance-test/stress/scenario-5-token-client-credentials.js
```

---

## スクリプトによる実行・レポート生成

```bash
# 単一シナリオ実行
./performance-test/scripts/run-stress-test.sh scenario-5-token-client-credentials

# 全シナリオ実行
./performance-test/scripts/run-stress-test.sh all

# VU数・実行時間を指定
VU_COUNT=200 DURATION=60s ./performance-test/scripts/run-stress-test.sh all

# 今日の日報レポート生成
./performance-test/scripts/generate-daily-report.sh

# 特定日の日報レポート生成
./performance-test/scripts/generate-daily-report.sh 2026-02-04

# 個別ファイルのレポート
./performance-test/scripts/generate-report.sh <result-json-path>
```

---

## テスト種別

### ストレステスト

システムの限界を特定。単体API評価。

```bash
# デフォルト: 120 VUs、30秒
k6 run ./performance-test/stress/scenario-5-token-client-credentials.js

# カスタマイズ
VU_COUNT=200 DURATION=60s k6 run ./performance-test/stress/scenario-5-token-client-credentials.js
```

### ロードテスト

想定負荷での安定性検証。E2Eフロー評価。

```bash
# CIBAフロー（シングルテナント）
k6 run ./performance-test/load/scenario-1-ciba-login.js

# マルチテナントCIBA
TENANT_COUNT=5 k6 run ./performance-test/load/scenario-2-multi-ciba-login.js

# 認可コードフロー
k6 run ./performance-test/load/scenario-4-authorization-code.js
```

---

## シナリオ一覧

| シナリオ名 | 説明 |
|-----------|------|
| `scenario-1-authorization-request` | 認可リクエスト |
| `scenario-2-bc` | Backchannel Authentication |
| `scenario-3-ciba-device` | CIBA (device) |
| `scenario-3-ciba-sub` | CIBA (sub) |
| `scenario-3-ciba-email` | CIBA (email) |
| `scenario-3-ciba-phone` | CIBA (phone) |
| `scenario-3-ciba-ex-sub` | CIBA (ex-sub) |
| `scenario-4-token-password` | Token (password) |
| `scenario-5-token-client-credentials` | Token (client_credentials) |
| `scenario-6-jwks` | JWKS |
| `scenario-7-token-introspection` | Token Introspection |
| `scenario-8-authentication-device` | デバイス認証 |
| `scenario-9-identity-verification-application` | 本人確認申請 |

---

## 性能目標

### API単体TPS

| エンドポイント | TPS目標 | p95目標 |
|--------------|---------|---------|
| Authorization Request | 2,000+ | 200ms |
| Token (Client Credentials) | 1,000+ | 250ms |
| Token Introspection | 2,000+ | 200ms |
| JWKS | 2,000+ | 200ms |
| CIBA BC Request | 1,000+ | 250ms |

### 評価指標

| 指標 | 説明 | 目標 |
|-----|------|------|
| p95 | 95%タイルの応答時間 | 500ms以下 |
| p99 | 99%タイルの応答時間 | 1s以下 |
| エラー率 | 失敗リクエストの割合 | 0.1%未満 |

---

## レポート形式

### 日報レポート (daily-report.md)

- **サマリー**: 実行テスト数、総実行時間
- **テスト実行履歴**: 各テストの開始時刻、実行時間、結果
- **パフォーマンス結果**: 各シナリオのTPS、レイテンシ、成功率

### 評価基準

| 指標 | 閾値 | 判定 |
|------|------|------|
| p95レイテンシ | < 500ms | 合格/不合格 |
| エラー率 | < 0.1% | 合格/不合格 |
| 成功率 | 100% | 合格/不合格 |

---

## 環境変数

```bash
VU_COUNT=200       # VU数（デフォルト: 120）
DURATION=60s       # 実行時間（デフォルト: 30s）
BASE_URL=...       # テスト対象URL
TENANT_INDEX=0     # テナントインデックス
```

---

## k6スクリプト構造

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

// 環境変数でカスタマイズ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '120');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

// テストデータ読み込み
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));
const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const url = `${baseUrl}/${config.tenantId}/v1/tokens`;

  const payload = `grant_type=client_credentials&client_id=${config.clientId}&...`;

  const res = http.post(url, payload, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
```

---

## テストデータ準備詳細

### テナント登録

```bash
# 基本
./performance-test/scripts/register-tenants.sh -n 10

# オプション
-n <数>     # 登録テナント数（必須）
-a          # 追加モード（既存保持）
-d true     # ドライラン
-b <URL>    # ベースURL
```

### ユーザーデータ生成

```bash
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000 \
  --first-tenant-users 1000000

# オプション
--users <数>              # 各テナントのユーザー数
--first-tenant-users <数> # 最初のテナントのみ別のユーザー数
```

### データ投入

```bash
./performance-test/scripts/import_users.sh multi_tenant_1m+9x100k
```

---

## 結果確認

### k6出力

```
✓ status is 200

checks.........................: 100.00% ✓ 35640    ✗ 0
data_received..................: 15 MB   499 kB/s
data_sent......................: 9.2 MB  307 kB/s
http_req_blocked...............: avg=12.34µs  p(95)=20.12µs
http_req_duration..............: avg=99.51ms  p(95)=156.42ms
http_reqs......................: 35640   1188.006/s
iteration_duration.............: avg=100.89ms p(95)=157.83ms
iterations.....................: 35640   1188.006/s
vus............................: 120     min=120    max=120
```

### 結果ファイル

```bash
# HTML レポート
k6 run --out web-dashboard=export=result.html ./performance-test/stress/...

# JSON出力
k6 run --out json=result.json ./performance-test/stress/...
```

---

## トラブルシューティング

| 問題 | 原因 | 解決策 |
|------|------|--------|
| テナント設定読み込みエラー | JSONファイル未生成 | `register-tenants.sh` を先に実行 |
| 接続エラー | サーバー未起動 | `docker compose up -d` 確認 |
| 高エラー率 | DBコネクション枯渇 | コネクションプール設定確認 |
| p95超過 | リソース不足 | VU数を下げて再測定 |

---

## 詳細ドキュメント

| ドキュメント | 内容 |
|------------|------|
| `00-overview.md` | 性能検証概要 |
| `01-test-environment.md` | テスト環境・構成 |
| `02-stress-test-results.md` | ストレステスト結果 |
| `03-load-test-results.md` | ロードテスト結果 |
| `04-scalability-evaluation.md` | スケーラビリティ評価 |
| `05-tuning-guide.md` | チューニングガイド |
| `06-test-execution-guide.md` | Step-by-Step実行ガイド |
| `07-test-strategy.md` | 性能テスト方針 |

パス: `documentation/docs/content_08_ops/performance/`

---

## 関連スキル

| スキル | 用途 |
|--------|------|
| `/ops-local-env` | ローカル環境構築 |
| `/ops-deployment` | 運用・監視 |
| `/dev-database` | データベース設定・チューニング |
