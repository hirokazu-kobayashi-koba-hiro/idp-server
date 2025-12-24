# 性能検証概要

本ドキュメントは、idp-server の性能検証に関する包括的な情報を提供する。

:::info テスト環境
本ドキュメントの測定結果はローカル検証環境（2 vCPU × 2インスタンス、120 VU）での実測値です。
本番環境では個別に性能検証を実施してください。
:::

---

## 目的

idp-server が以下の要件を満たすことを検証する：

1. **スループット**: 想定負荷（中規模: 〜1,000 req/s）を安定して処理可能
2. **レイテンシ**: 各エンドポイントが許容範囲内の応答時間で応答
3. **スケーラビリティ**: テナント数・ユーザー数の増加に対して適切にスケール
4. **安定性**: 長時間負荷に対しても性能劣化がない

---

## 測定指標

### スループット指標

| 指標 | 説明 | 目標値 |
|-----|------|-------|
| TPS (Transactions Per Second) | 1秒あたりの処理トランザクション数 | エンドポイント依存 |
| RPS (Requests Per Second) | 1秒あたりのHTTPリクエスト数 | 〜1,000 req/s |
| イテレーション数 | シナリオ全体の完了数 | シナリオ依存 |

### レイテンシ指標

| 指標 | 説明 | 目標値 |
|-----|------|-------|
| 平均応答時間 (avg) | 全リクエストの平均応答時間 | - |
| 中央値 (med) | 応答時間の中央値 | - |
| p90 | 90%タイルの応答時間 | - |
| p95 | 95%タイルの応答時間 | 500ms以下 |
| p99 | 99%タイルの応答時間 | 1s以下 |

### 信頼性指標

| 指標 | 説明 | 目標値 |
|-----|------|-------|
| エラー率 | 失敗リクエストの割合 | 0.1%未満 |
| 成功率 | 成功リクエストの割合 | 99.9%以上 |

---

## テスト種別

### ストレステスト (Stress Test)

システムの限界を特定するためのテスト。

- **目的**: 最大スループットとブレークポイントの特定
- **負荷パターン**: 120 VUs、30秒間の継続負荷
- **評価対象**: 各エンドポイント単体

### ロードテスト (Load Test)

想定負荷での安定性を検証するテスト。

- **目的**: 実運用想定での性能安定性確認
- **負荷パターン**: 複合シナリオ、5-10分間
- **評価対象**: E2Eフロー（認証→トークン発行→検証）

### スケーラビリティテスト

負荷増加に対するシステムの拡張性を検証するテスト。

- **目的**: 水平スケール時のスループット向上率
- **評価対象**: マルチテナント、ユーザー数増加

---

## 検証対象エンドポイント

### OAuth 2.0/OIDC 基本フロー

| エンドポイント | 用途 | 優先度 |
|--------------|------|-------|
| `/authorizations` | 認可リクエスト | 高 |
| `/tokens` | トークン発行 | 高 |
| `/tokens` (client_credentials) | Client Credentials Grant | 高 |
| `/tokens` (password) | Resource Owner Password Grant | 中 |
| `/introspection` | トークン検証 | 高 |
| `/userinfo` | ユーザー情報取得 | 中 |
| `/jwks` | 公開鍵取得 | 高 |

### CIBA フロー

| エンドポイント | 用途 | 優先度 |
|--------------|------|-------|
| `/backchannel/authentications` | CIBA認証リクエスト | 高 |
| `/authentication-devices/{id}/authentications` | 認証トランザクション取得 | 高 |
| `/authentications/{id}/authentication-device-binding-message` | バインディングメッセージ | 高 |
| `/tokens` (urn:openid:params:grant-type:ciba) | CIBAトークン発行 | 高 |

---

## 性能目標サマリー

### API単体TPS（1 HTTPリクエスト）

| カテゴリ | エンドポイント | TPS目標 | p95目標 |
|---------|--------------|---------|---------|
| 認可 | Authorization Request | 2,000+ | 200ms |
| トークン発行 | Token (Client Credentials) | 1,000+ | 250ms |
| トークン検証 | Token Introspection | 2,000+ | 200ms |
| 公開鍵 | JWKS | 2,000+ | 200ms |
| CIBA | BC Request | 1,000+ | 250ms |

### フロー完了TPS（複数HTTPリクエスト）

| フロー | 構成API数 | 完了TPS目標 | p95目標 |
|-------|----------|-----------|---------|
| CIBA Full Flow | 5 | 250+ | 300ms |

実測値は[ストレステスト結果](./02-stress-test-results)を参照。

詳細は[性能テスト方針](./07-test-strategy)を参照。

---

## テストシナリオ一覧

### ロードテスト

| シナリオ | ファイル | 説明 | 設定読込 |
|---------|---------|------|---------|
| CIBA ログイン | `scenario-1-ciba-login.js` | シングルテナントCIBAフロー + Introspection | 自動 |
| マルチテナントCIBA | `scenario-2-multi-ciba-login.js` | 複数テナント並列CIBAフロー | 自動 |
| ピーク負荷 | `scenario-3-peak-login.js` | ランプアップ/ダウン負荷パターン | 自動 |
| 認可コードフロー | `scenario-4-authorization-code.js` | パスワード認証 → トークン → Userinfo → Introspection | 自動 |

:::tip 設定の自動読み込み
ロードテストは `performance-test-tenant.json` から設定を自動読み込みします。
環境変数 `BASE_URL` のみ必要（デフォルト: `http://localhost:8080`）
:::

### ストレステスト

| シナリオ | ファイル | 説明 |
|---------|---------|------|
| 認可リクエスト | `scenario-1-authorization-request.js` | 認可エンドポイント単体 |
| BC Request | `scenario-2-bc.js` | CIBA認証リクエスト単体 |
| CIBA Device | `scenario-3-ciba-device.js` | device パターン |
| CIBA Sub | `scenario-3-ciba-sub.js` | sub パターン |
| CIBA Email | `scenario-3-ciba-email.js` | email パターン |
| CIBA Phone | `scenario-3-ciba-phone.js` | phone パターン |
| CIBA Ex-Sub | `scenario-3-ciba-ex-sub.js` | external subject パターン |
| Token Password | `scenario-4-token-password.js` | Password Grant |
| Token Client Credentials | `scenario-5-token-client-credentials.js` | Client Credentials Grant |
| JWKS | `scenario-6-jwks.js` | 公開鍵取得 |
| Token Introspection | `scenario-7-token-introspection.js` | トークン検証 |
| Authentication Device | `scenario-8-authentication-device.js` | 認証デバイス |
| Identity Verification | `scenario-9-identity-verification-application.js` | 身元確認申請 |

---

## ドキュメント構成

| ドキュメント | 内容 |
|------------|------|
| [01-test-environment.md](./01-test-environment.md) | テスト環境・構成 |
| [02-stress-test-results.md](./02-stress-test-results.md) | ストレステスト結果 |
| [03-load-test-results.md](./03-load-test-results.md) | ロードテスト結果 |
| [04-scalability-evaluation.md](./04-scalability-evaluation.md) | スケーラビリティ評価 |
| [05-tuning-guide.md](./05-tuning-guide.md) | チューニングガイド |
| [06-test-execution-guide.md](./06-test-execution-guide.md) | テスト実行ガイド（Step-by-Step） |
| [07-test-strategy.md](./07-test-strategy.md) | **性能テスト方針**（テスト条件・評価基準） |

