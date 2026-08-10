# ナンバーマッチング

認可コードフローで認証デバイスへステップアップする際の、プッシュ疲労（push fatigue）対策である
`authentication-device-number-matching` の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

サインイン画面に表示された数字コードを、ユーザーが認証デバイスへ転記させる方式です。

**コードは認証デバイスに送信されません。** サーバーは発行したコードを保存し、サインイン画面にだけ返します。
承認者が元の画面を見ないと承認できないため、届いた通知を反射的に承認してしまう攻撃が成立しなくなります。

2つのインタラクションで構成されます。

| インタラクション | 呼び出し元 | 動作 |
|---|---|---|
| `authentication-device-number-matching-challenge` | サインイン画面 | コードを生成して保存し、レスポンス `number_matching_code` で画面に返す |
| `authentication-device-number-matching` | 認証デバイス | 転記されたコードを送り、保存値と照合する |

コード発行はプッシュ配信とは分離されています。プッシュ（FCM）は CIBA と共通の
`authentication-device-notification` 側にあるため、**ナンバーマッチングの利用にプッシュは必須ではありません**。

CIBA の `authentication-device-binding-message` とは目的が異なります。バインディングメッセージは
リクエスト側の値をデバイスに **表示させる** もので、値はデバイスへ送られます。ナンバーマッチングの
コードはデバイスへ送らないことが対策の核心です。

---

## 設定

### 認証設定（任意）

コードの桁数を変える場合のみ登録します。**設定が無い場合は既定値で動作します。**

```json
{
  "id": "01997e5b-1a2c-4c33-9f8e-3f2a5b7c9d10",
  "type": "authentication-device-number-matching",
  "interactions": {
    "authentication-device-number-matching": {
      "execution": {
        "details": {
          "length": 4
        }
      }
    }
  }
}
```

| 項目 | 内容 | 既定値 |
|---|---|---|
| `execution.details.length` | コードの桁数 | `4` |

コードは数字のみで、`SecureRandom` により生成されます。

`length` に 0 以下を指定した場合は既定値の 4 にフォールバックします。桁数 0 は空文字列となり、
空の送信値が常に一致してしまうためです。

### 認証ポリシー

ナンバーマッチングを使うフローの認証ポリシーに、2つのインタラクションを許可し実行順を定義します。

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "device_fido_uaf_authentication",
      "priority": 10,
      "conditions": {
        "acr_values": ["urn:idp:acr:device"]
      },
      "available_methods": [
        "authentication-device-notification",
        "authentication-device-number-matching-challenge",
        "authentication-device-number-matching",
        "authentication-device-deny",
        "fido-uaf"
      ],
      "step_definitions": [
        { "method": "authentication-device-number-matching-challenge", "order": 1, "requires_user": false },
        { "method": "authentication-device-number-matching", "order": 2, "requires_user": false },
        { "method": "fido-uaf", "order": 3, "requires_user": true }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.authentication-device-number-matching.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.fido-uaf-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

`success_conditions` にナンバーマッチングと FIDO-UAF の両方を並べることで、コード照合だけでは
認証を完了させず、生体認証まで到達させます。

同梱テンプレート: `config/templates/use-cases/mfa-fido-uaf/authentication-policy.json`

---

## 利用方法

### 1. コード発行（サインイン画面）

```http
POST /{tenant-id}/v1/authorizations/{id}/authentication-device-number-matching-challenge
```

```json
{
  "number_matching_code": "8341"
}
```

この値を画面に表示します。デバイスには送りません。

### 2. コード検証（認証デバイス）

```http
POST /{tenant-id}/v1/authorizations/{id}/authentication-device-number-matching
```

```json
{
  "number_matching_code": "8341"
}
```

| 状況 | HTTP | `error` | `error_description` |
|---|---|---|---|
| 一致 | 200 | - | - |
| チャレンジ未実行 | 400 | `invalid_request` | `number_matching_code has not been issued` |
| 不一致 | 400 | `invalid_request` | `number_matching_code does not match` |

### 3. 入力画面の要否判定（認証デバイス）

認証トランザクション取得APIのレスポンスに含まれる `number_matching_required` で判定します。

```http
GET /{tenant-id}/v1/authentication-devices/{device-id}/authentications
```

```json
{
  "list": [
    { "id": "...", "flow": "oauth", "number_matching_required": true }
  ]
}
```

コードが発行されると `true` になります。**検証成功後も `true` のままです。**
検証成功でクリアすると、フローを開始した攻撃者が自分でコード検証を通すことで
「もう入力は不要」という状態を被害者のデバイスへ伝えられてしまい、ナンバーマッチングが
塞いでいるプッシュ疲労の経路が再び開くためです。

---

## 関連ドキュメント

- [認可コードフロー デバイス認証拡張](../../../content_04_protocols/protocol-07-authorization-code-device-authentication.md) - プロトコル定義
- [認可コードフロー + FIDO-UAF](../../../content_05_how-to/phase-3-advanced/fido-uaf/04-authorization-code-flow.md) - 構築手順
- [認証デバイス通知](./authentication-device.md) - プッシュ配信側の設定
- [FIDO-UAF](./fido-uaf.md)
