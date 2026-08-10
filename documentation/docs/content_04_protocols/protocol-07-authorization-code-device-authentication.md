# 認可コードフロー デバイス認証拡張

## 概要

認可コードフローの中で、ブラウザとは別の**認証デバイス**（スマートフォンアプリ等）に認証を委ねるための、idp-server 独自のプロトコル拡張です。

ブラウザ側（サインイン画面）とデバイス側が別チャネルで進行し、サインイン画面はポーリングで完了を検知します。デカップルド型という点は CIBA と同じですが、開始点が異なります。

| | CIBA | 本拡張 |
|---|---|---|
| 開始点 | クライアントのバックエンド（`/v1/backchannel/authentications`） | ブラウザの認可リクエスト（`/v1/authorizations`） |
| 開始時のクライアント認証 | 必須 | 不要（認可リクエストのため） |
| 完了検知 | トークンエンドポイントのポーリング | `/authentication-status` のポーリング |
| トランザクション束縛 | `binding_message`（デバイスに表示） | ナンバーマッチング（デバイスに送らない） |

**開始時にクライアント認証が無い**ことが、CIBA との決定的な差です。攻撃者が被害者の識別子（`login_hint`）を知っていれば、攻撃者起点でフローを開始してデバイスへ通知を送れます。繰り返し通知して誤承認を狙う攻撃（プッシュ疲労 / MFA fatigue）が成立するため、本拡張ではナンバーマッチングでこれを塞ぎます。

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
 │                                │─── push（コードは載せない） ──▶│
 │                                │                              │
 │                                │◀── GET /v1/authentication-devices/{device-id}/authentications
 │                                │    number_matching_required で入力画面の要否を判定
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

### ブラウザ（サインイン画面）側

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

## ナンバーマッチングのセキュリティモデル

### 何を防ぐか

開始時にクライアント認証が無いため、攻撃者は被害者の識別子だけでフローを開始し、被害者のデバイスへ通知を送れます。ナンバーマッチングは「承認する人がサインイン画面を見ている本人であること」を要求することで、通知だけを見て承認する経路を塞ぎます。

### 何が保証を作るか

**コードを認証デバイスへ送らないこと**が保証の源です。

- コードはサーバーが生成し、発行エンドポイントのレスポンスとしてサインイン画面にのみ返す
- プッシュペイロードにもデバイス向けトランザクションにも含めない
- ユーザーが画面の値をデバイスへ転記する

コードをプッシュに載せてデバイス側で自動照合させる方式では、通知を受け取っただけの利用者が承認できてしまい、対策になりません。

CIBA の `binding_message` とは目的が逆です。あちらはリクエスト側の値をデバイスに**表示させる**もので、値はデバイスへ送られます。

### `number_matching_required` が下がらない理由

デバイスが入力画面の要否を判定するフラグ `number_matching_required` は、**チャレンジ成功を起点に `true` になり、コード検証に成功した後も `true` のままです。**

検証成功でクリアすると、フローを開始した攻撃者が自分でコード検証を通すことで「もう入力は不要」という状態を被害者のデバイスへ伝えられます。被害者は番号入力を求められないまま承認でき、ナンバーマッチングが塞いでいた経路が再び開きます。

実際にコードが検証済みかどうかは認証結果側で管理されるため、フラグを下げる必要はありません。

### ナンバーマッチングは認証要素ではない

コード照合はトランザクションの束縛であり、本人性を証明しません。単独で認証を完了させないよう、認証ポリシーの `success_conditions` にはナンバーマッチングと実際のデバイス認証（FIDO-UAF 等）の両方を要求します。

---

## 設定

認証ポリシー（`flow: oauth`）に、使用するインタラクションと実行順、完了条件を定義します。

同梱テンプレート: `config/templates/use-cases/mfa-fido-uaf/authentication-policy.json`

コードの桁数は認証設定 `authentication-device-number-matching` の `execution.details.length` で変更できます（既定 4 桁、数字のみ）。設定が無い場合は既定値で動作します。

詳細は [ナンバーマッチング設定](../content_06_developer-guide/05-configuration/authn/number-matching.md) を参照してください。

---

## 関連ドキュメント

- [認可コードフロー](./protocol-01-authorization-code-flow.md) - ベースとなるフロー
- [CIBA フロー](./protocol-02-ciba-flow.md) - バックエンド起点のデカップルド認証
- [認可コードフロー + FIDO-UAF](../content_05_how-to/phase-3-advanced/fido-uaf/04-authorization-code-flow.md) - 構築手順
- [ナンバーマッチング設定](../content_06_developer-guide/05-configuration/authn/number-matching.md) - 設定リファレンス
