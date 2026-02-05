# パフォーマンステストレポート生成スキル

このスキルはパフォーマンステストを実行し、結果をMarkdownレポートとして生成します。

## 使用方法

```
/perf-report <コマンド> [オプション]
```

---

## コマンド一覧

### ストレステスト実行

```bash
# 単一シナリオ実行
./performance-test/scripts/run-stress-test.sh scenario-5-token-client-credentials

# 全シナリオ実行
./performance-test/scripts/run-stress-test.sh all

# VU数・実行時間を指定
VU_COUNT=200 DURATION=60s ./performance-test/scripts/run-stress-test.sh all
```

### 日報レポート生成

```bash
# 今日の結果
./performance-test/scripts/generate-daily-report.sh

# 特定日の結果
./performance-test/scripts/generate-daily-report.sh 2026-02-04
```

### 個別ファイルのレポート

```bash
./performance-test/scripts/generate-report.sh <result-json-path>
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

## 結果ディレクトリ構成

```
performance-test/result/stress/
└── 2026-02-04/                                              # 日付ディレクトリ
    ├── scenario-1-authorization-request-20260204-103045.json  # 実行日時付きファイル
    ├── scenario-5-token-client-credentials-20260204-103245.json
    ├── execution-log.json                                   # 実行履歴
    └── daily-report.md                                      # 日報レポート
```

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

## 実行例

```
# ストレステスト実行とレポート生成
/perf-report run all

# 今日の日報を確認
/perf-report daily

# 特定日の日報を確認
/perf-report daily 2026-02-04
```
