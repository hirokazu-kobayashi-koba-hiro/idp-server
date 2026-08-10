# 認可コードフロー デバイス認証拡張

## 概要

認可コードフローの中で、ブラウザとは別の**認証デバイス**（スマートフォンアプリ等）に認証を委ねるための、idp-server 独自のプロトコル拡張です。

ブラウザの認可リクエストで開始し、サインイン画面とデバイスが別チャネルで進行します。サインイン画面は `/authentication-status` のポーリングで完了を検知します。

---

## シーケンス

```
ブラウザ(SPA)                  idp-server                   認証デバイス
 │                                │                              │
 ├─ GET  /v1/authorizations ──────▶                              │
 │◀─ 302 サインイン画面へ (id 付与) │                              │
 ├─ GET  /{id}/view-data ─────────▶                              │
 │                                │                              │
 ├─ POST /{id}/authentication-device-number-matching-challenge ─▶│
 │◀─ { number_matching_code }      │  コードを生成・サーバー保存    │
 ├─ 画面にコードを表示              │                              │
 │                                │                              │
 ├─ POST /{id}/authentication-device-notification ──────────────▶│ (任意)
 │                                │─── push ────────────────────▶│
 │                                │                              │
 │                                │◀── GET /v1/authentication-devices/{device-id}/authentications
 │                                │                              │
 │                                │◀── POST /{id}/authentication-device-number-matching
 │                                │    ユーザーが画面から転記したコード
 │                                │                              │
 │                                │◀── POST /v1/authentications/{transaction-id}/fido-uaf-authentication-challenge
 │                                │◀── POST /v1/authentications/{transaction-id}/fido-uaf-authentication
 │                                │                              │
 ├─ GET  /{id}/authentication-status ──▶ success                 │
 ├─ POST /{id}/authorize ─────────▶                              │
 │◀─ 302 redirect_uri?code=...     │                              │
```

---

## エンドポイント体系

### サインイン画面側

| エンドポイント | 用途 |
|---|---|
| `GET /{tenant-id}/v1/authorizations` | 認可リクエスト。サインイン画面へリダイレクトし、トランザクション `id` を付与 |
| `GET /{tenant-id}/v1/authorizations/{id}/view-data` | 画面表示用データ（クライアント名・スコープ等）の取得 |
| `POST /{tenant-id}/v1/authorizations/{id}/{interaction-type}` | 認証インタラクションの実行 |
| `GET /{tenant-id}/v1/authorizations/{id}/authentication-status` | 認証完了のポーリング。`status` は `in_progress` / `success` / `failure` / `locked` |
| `POST /{tenant-id}/v1/authorizations/{id}/authorize` | 認可の確定。`redirect_uri` へ認可コードを返す |

`{interaction-type}` のうち本拡張で使うもの:

| 値 | 呼び出し元 | 用途 |
|---|---|---|
| `authentication-device-number-matching-challenge` | サインイン画面 | ナンバーマッチングコードの発行 |
| `authentication-device-notification` | サインイン画面 | デバイスへのプッシュ送信（任意） |
| `authentication-device-number-matching` | 認証デバイス | コードの検証 |
| `authentication-device-deny` | 認証デバイス | 認証の拒否 |

### 認証デバイス側

| エンドポイント | 用途 |
|---|---|
| `GET /{tenant-id}/v1/authentication-devices/{device-id}/authentications` | 自デバイス宛の認証トランザクション取得 |
| `POST /{tenant-id}/v1/authentications/{transaction-id}/{interaction-type}` | FIDO-UAF 等のデバイス認証の実行 |

デバイス向けトランザクション取得は、テナント設定 `identity_policy_config.authentication_device_rule.authentication_type` によりデバイス認証を要求できます。認証されていないデバイスにはリクエスト詳細（`context`）を返しません。

---

## ナンバーマッチング

サーバーが数字コードを生成してサインイン画面に返し、ユーザーがそれを認証デバイスへ転記します。**コードは認証デバイスに送信されません。**

| インタラクション | 動作 |
|---|---|
| `authentication-device-number-matching-challenge` | コードを生成・保存し、レスポンス `number_matching_code` でサインイン画面に返す |
| `authentication-device-number-matching` | 転記されたコードを保存値と照合する |

コード発行はプッシュ配信（`authentication-device-notification`）と別のインタラクションであり、ナンバーマッチングの利用にプッシュは必須ではありません。

認証デバイスは、トランザクション取得APIのレスポンスに含まれる `number_matching_required` で入力画面の要否を判定します。

---

## 設定

認証ポリシー（`flow: oauth`）に、使用するインタラクションと実行順、完了条件を定義します。

同梱テンプレート: `config/templates/use-cases/mfa-fido-uaf/authentication-policy.json`

コードの桁数は認証設定 `authentication-device-number-matching` の `execution.details.length` で変更できます（既定 4 桁、数字のみ）。設定が無い場合は既定値で動作します。

---

## 関連ドキュメント

- [認可コードフロー](./protocol-01-authorization-code-flow.md) - ベースとなるフロー
- [CIBA フロー](./protocol-02-ciba-flow.md) - バックエンド起点のデカップルド認証
- [認可コードフロー + FIDO-UAF](../content_05_how-to/phase-3-advanced/fido-uaf/04-authorization-code-flow.md) - 構築手順と設計意図
- [ナンバーマッチング設定](../content_06_developer-guide/05-configuration/authn/number-matching.md) - 設定リファレンス
