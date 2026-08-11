# CIBA 委譲 ユースケーステンプレート

認可コードフロー（ブラウザのサインイン画面）の認証を、**CIBA フローへ委譲**する構成のテンプレートです。

サインイン画面は認証を自分では行わず、外部API認証（`external-api-authentication`）を通じて認可サーバーの CIBA エンドポイントを呼び、ユーザーがスマートフォンで承認するのを待ちます。

**idp-server のコード変更は不要です。** 委譲は設定だけで成立します。

---

## 何が起きるか

```
ブラウザ                    idp-server                    スマートフォン
  │                            │                              │
  ├─ 認可リクエスト ──────────▶│                              │
  ├─ ciba_start ──────────────▶├─ 自身の backchannel を呼ぶ ──▶│ 通知
  ├─ ciba_poll（承認待ち）────▶│  400 authorization_pending    │
  │                            │                              ├─ 生体認証で承認
  ├─ ciba_poll ───────────────▶│  200 + access_token           │
  ├─ userinfo ────────────────▶│  ユーザー確定                 │
  ├─ authorize ───────────────▶│                              │
  │◀─ 認可コード                │                              │
```

委譲先の認可サーバーは**このテンプレートが作るテナント自身**です。third party を用意しなくても全経路を通せます。実運用では `authentication-config-external-api.json` の URL とクライアント資格情報を、委譲したい相手のものに差し替えてください。

---

## 設定内容

| 設定 | 内容 |
|------|------|
| テナント | パスワードポリシー、セッション設定、CIBA 設定、認証デバイスルール |
| 認証設定 | `initial-registration` / `fido-uaf` / `external-api-authentication`（委譲） |
| 認証ポリシー（oauth） | `ciba_delegation`（委譲のみ） / `password_and_ciba_delegation`（パスワード + 委譲） / 既定（パスワード・初期登録） |
| 認証ポリシー（ciba） | FIDO-UAF |
| クライアント | 認可コードフロー + CIBA + JWT Bearer |

### 委譲の3インタラクション

| interaction | 呼び先 | 動作 |
|---|---|---|
| `ciba_start` | `POST /{tenant}/v1/backchannel/authentications` | `auth_req_id` をサーバー側に保存 |
| `ciba_poll` | `POST /{tenant}/v1/tokens` | 保存した `auth_req_id` でポーリング。成功時に `access_token` を保存 |
| `userinfo` | `GET /{tenant}/v1/userinfo` | 保存したトークンでユーザーを確定 |

`auth_req_id` と `access_token` は `http_request_store` でサーバー側に保持され、サインイン画面には返りません。

### 2つの使い方

`acr_values` でポリシーを出し分けます。

| 指定 | 適用ポリシー | 認証 |
|---|---|---|
| `urn:idp:acr:ciba-delegation` | `ciba_delegation` | CIBA 委譲のみ |
| `urn:idp:acr:password-ciba` | `password_and_ciba_delegation` | パスワード → CIBA 委譲の2要素 |
| 指定なし | 既定 | パスワード / 初期登録（ユーザー登録用） |

2要素版では `identity_match_field` により「デバイスで承認した人 == パスワードを入れた人」が検証され、新しいユーザーは作られません。

---

## ファイル構成

| ファイル | 内容 |
|---|---|
| `setup.sh` | 一括セットアップ |
| `verify.sh` | ユーザー登録 + FIDO-UAF 登録 + CIBA の動作確認 |
| `ciba-device-auth.sh` | デバイス側の承認 |
| `delete.sh` / `update.sh` | 削除 / 更新 |
| `authentication-config-external-api.json` | **委譲の設定（このテンプレートの中心）** |
| `authentication-policy-oauth.json` | 委譲ポリシー2種 + 既定 |
| `VERIFY.md` | 手順（curl 付き） |

---

## セットアップ

### 前提条件

- idp-server が起動済み
- プロジェクトルートの `.env` に管理者認証情報

### 実行

```bash
./config/templates/use-cases/ciba-delegation/setup.sh

# ドライラン
DRY_RUN=true ./config/templates/use-cases/ciba-delegation/setup.sh
```

生成された JSON は `config/generated/ciba-delegation/` に保存されます。

### 動作確認

委譲を試すには、**承認する側のユーザーと FIDO-UAF デバイスが先に必要**です。誰も承認できないと委譲は完了しません。

[VERIFY.md](./VERIFY.md) に curl 付きの手順があります。

| フェーズ | 内容 |
|---|---|
| Phase 1 | ユーザー登録 + FIDO-UAF デバイス登録（**委譲の前提**） |
| Phase 2 | CIBA 単体の動作確認 |
| Phase 3 | 認可コードフローの認証を CIBA へ委譲 |
| Phase 4 | パスワード + CIBA 委譲（2要素） |

スクリプトでまとめて実行する場合:

```bash
# Phase 1 相当（ユーザー登録 + FIDO-UAF デバイス登録）
./config/templates/use-cases/ciba-delegation/verify.sh

# デバイス側の承認（Phase 3 の Step 19）
./config/templates/use-cases/ciba-delegation/ciba-device-auth.sh
```

`ciba-device-auth.sh` は `config/generated/${ORGANIZATION_NAME}/device-credentials.json` を読みます。このファイルを書くのは `verify.sh` だけなので、**VERIFY.md の Phase 1 を手で実行した場合は使えません**。手で登録したデバイスの `device_id` / `device_secret` を使ってください。

別の名前で構築した場合は、どのスクリプトにも同じ `ORGANIZATION_NAME` を渡します。

```bash
ORGANIZATION_NAME=my-org ./setup.sh
ORGANIZATION_NAME=my-org ./verify.sh
```

---

## 実運用にあたって

### 委譲先を別の認可サーバーにする

`authentication-config-external-api.json` の3つの `url` と、`client_id` / `client_secret` を委譲先のものに変更します。認証設定は管理APIで更新できます。

### 注意点

- **ポリシーの priority は大きい方が勝ちます。** 既定ポリシーより大きい値にしないと当たりません（このテンプレートでは 200 / 210）
- **ポーリング中の `authorization_pending` は失敗として記録されます。** 非2xx が失敗に数えられるためです。委譲ポリシーに `failure_conditions` を置くと、ポーリングでトランザクションがロックします
- `step_definitions[].method` はインタラクション名ではなく認証方式名（`external-api` / `password`）です。`success_conditions` の path はインタラクション名（`$.external-api-authentication.*`）で、両者は別物です

---

## 関連ドキュメント

- [Quickstart: CIBA 委譲](../../../../documentation/docs/content_02_quickstart/quickstart-15-ciba-delegation.md)
- [Quickstart: CIBA](../../../../documentation/docs/content_02_quickstart/quickstart-11-ciba.md)
- [Quickstart: 外部API認証](../../../../documentation/docs/content_02_quickstart/quickstart-14-external-api-authentication.md)
- [設定リファレンス: 外部API認証](../../../../documentation/docs/content_06_developer-guide/05-configuration/authn/external-api.md)
