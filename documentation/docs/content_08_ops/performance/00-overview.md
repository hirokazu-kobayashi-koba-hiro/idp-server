# 性能検証概要

本ドキュメントは、idp-server の性能検証に関する包括的な情報を提供する。

:::caution 暫定版
本ドキュメントシリーズは過去のテスト結果に基づく暫定版です。
最新コードベースでの再検証は [Issue #1140](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1140) で計画されています。
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

| カテゴリ | エンドポイント | TPS目標 | p95目標 |
|---------|--------------|---------|---------|
| 高頻度API | JWKS | 2,000+ | 150ms |
| 高頻度API | Introspection | 1,500+ | 200ms |
| 認証フロー | Authorization | 1,000+ | 200ms |
| 認証フロー | CIBA Full | 500+ | 500ms |
| トークン発行 | Client Credentials | 500+ | 300ms |

---

## ドキュメント構成

| ドキュメント | 内容 |
|------------|------|
| [01-test-environment.md](./01-test-environment.md) | テスト環境・構成 |
| [02-stress-test-results.md](./02-stress-test-results.md) | ストレステスト結果 |
| [03-load-test-results.md](./03-load-test-results.md) | ロードテスト結果 |
| [04-scalability-evaluation.md](./04-scalability-evaluation.md) | スケーラビリティ評価 |
| [05-tuning-guide.md](./05-tuning-guide.md) | チューニングガイド |

---

## 関連リソース

- [k6テストスクリプト](../../../../performance-test/)
- [テストデータ生成](../../../../performance-test/data/)
