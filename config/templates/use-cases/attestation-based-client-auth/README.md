# Attestation-Based Client Authentication Use Case Template

ネイティブアプリを**シークレットを配布せずに**認証するテンプレートセット（[draft-ietf-oauth-attestation-based-client-auth-10](https://datatracker.ietf.org/doc/draft-ietf-oauth-attestation-based-client-auth/)）。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。

アプリの**インスタンスごとに端末内で生成した鍵**（Client Instance Key）で認証します。リクエストには2つの JWT をヘッダで載せます。

| ヘッダ | JWT | 署名する鍵 | 主張 |
|--------|-----|-----------|------|
| `OAuth-Client-Attestation` | Client Attestation JWT | Client Attester の鍵、または登録済み Client Instance Key | このアプリがこの公開鍵を持っている |
| `OAuth-Client-Attestation-PoP` | Client Attestation PoP JWT | Client Instance Key | その鍵を**いま**保持している |

## 設定内容

| 項目 | 設定値 |
|------|--------|
| クライアント認証 | `attest_jwt_client_auth` |
| 対応 alg | Attestation / PoP とも `ES256`, `RS256` |
| 信頼モデル | `attester_jwks` と `registered_instance_key` の**両方**をクライアント2つで用意 |
| Challenge | エンドポイントは公開。強制は `false`（移行期の姿） |
| Challenge 有効期間 | 300 秒。単回消費ではなく期間内は再利用可 |
| インスタンス登録ポリシー | `require_authentication_device` |
| ユーザー認証 | FIDO-UAF + パスワードフォールバック（`require_authentication_device` の前提） |

`require_authentication_device` は `device_id` が**このサーバーが発行した認証デバイス**であることを要求します。デバイスの発行元が FIDO-UAF 登録なので、FIDO-UAF の設定一式が同梱されています。デバイスを介さない構成にする場合は `attestation_only` に変更してください（→ [EXPERIMENTS.md](./EXPERIMENTS.md)）。

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（ABCA 有効の認可サーバー設定） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST .../authentication-configurations` |
| `authentication-config-fido-uaf.json` | FIDO-UAF 認証 | 同上 |
| `authentication-config-device-notification.json` | デバイス通知 | 同上 |
| `authentication-config-email.json` | メール認証（フォールバック） | 同上 |
| `authentication-policy.json` | FIDO-UAF + パスワードフォールバック | `POST .../authentication-policies` |
| `attester-jwks-client-template.json` | ABCA クライアント（`attester_jwks`） | `POST .../clients` |
| `self-signed-client-template.json` | ABCA クライアント（`registered_instance_key`） | 同上 |
| `mint-attestation.mjs` | 2つの JWT を生成する CLI（Node 組み込みのみ、依存なし） | - |
| `setup.sh` | 上記を順番に実行する | - |
| `verify.sh` | 公開メタデータと Challenge エンドポイントを確認する | - |
| `update.sh` / `delete.sh` | 設定更新 / 削除 | - |
| `mock-server.js` | FIDO-UAF / 通知のモック | - |

## セットアップ手順

```bash
# 1. .env を用意（AUTHORIZATION_SERVER_URL, ADMIN_* が必要）
# 2. 実行
./config/templates/use-cases/attestation-based-client-auth/setup.sh

# 組織名を変える場合
ORGANIZATION_NAME=my-abca ./setup.sh
```

生成物は `config/generated/<組織名>/` に出ます。

| ファイル | 中身 |
|---------|------|
| `onboarding.json` | Organization / Organizer Tenant / Admin User / 管理クライアント |
| `public-tenant.json` | Public Tenant と認可サーバー設定 |
| `attester-jwks-client.json` | `attester_jwks` クライアント |
| `self-signed-client.json` | `registered_instance_key` クライアント |
| `attester-keys.json` | Client Attester の鍵ペア（**秘密鍵を含む**） |

`attester-keys.json` は本来 Attester のバックエンドが持つ鍵です。認可サーバーには公開鍵（JWK Set）だけがクライアント設定として登録されます。

## 次に

- 動作確認 → [VERIFY.md](./VERIFY.md)
- 設定を変えて挙動を見る → [EXPERIMENTS.md](./EXPERIMENTS.md)
- 仕組みの解説 → [プロトコル解説](../../../../documentation/docs/content_04_protocols/protocol-08-attestation-based-client-authentication.md)
