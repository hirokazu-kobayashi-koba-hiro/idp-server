# 初期登録（ ユーザー属性登録）

このドキュメントは、初期登録（ユーザーの属性登録）機能の`概要`・`設定`・`利用方法`及び`内部ロジック`について説明するものです。

---

## 概要

初期登録（ユーザー属性登録）は、OpenID Connect 認証フローの中で、ユーザーを新規作成するためのプロセスです。  
この機能では、設定に基づき、登録時に必要なユーザー属性（例：メールアドレス、名前、パスワードなど）を柔軟に構成できます。

この登録プロセスは、認可サーバーの `/v1/authorizations/{id}/initial-registration` エンドポイントを通じて提供され、  
`prompt=create` などの認可リクエストによって開始されます。

また、パスワードのハッシュ化や重複ユーザーの検出など、ユーザー管理における基本的な安全対策も組み込まれています。

テナントは `initial-registration` の登録を行うことでこの機能を利用することが可能です。

## 設定

### 📘 全体構成

```json
{
  "id": "UUID",
  "type": "initial-registration",
  "payload": {
    "required": [],
    "properties": {}
  }
}
```

| フィールド     | 説明                                         |
|-----------|--------------------------------------------|
| `id`      | スキーマの一意な識別子（UUIDなど）                        |
| `type`    | スキーマのタイプ。ここでは `"initial-registration"` 固定。 |
| `payload` | 実際のユーザー属性スキーマ本体。JSON Schema準拠。             |

---

### 🔧 payload の構成

#### ✔ required

```json
{
  "required": [
    "email",
    "password",
    "name"
  ]
}
```

- 登録／更新時に**必須となる属性キー**の一覧。

---

#### 🧩 properties

項目一覧：

| 項目                      | 型       | 説明                                          |
|-------------------------|---------|---------------------------------------------|
| `name`                  | string  | End-User の表示用フルネーム。肩書きや称号 (suffix) を含むこともある |
| `given_name`            | string  | 名（Given Name）                               |
| `family_name`           | string  | 姓（Family Name）                              |
| `middle_name`           | string  | ミドルネーム                                      |
| `nickname`              | string  | ニックネーム                                      |
| `preferred_username`    | string  | End-User の選好するユーザー名（例：janedoe）              |
| `profile`               | string  | プロフィールページのURL                               |
| `picture`               | string  | プロフィール画像のURL                                |
| `website`               | string  | End-User のWebサイトURL                         |
| `email`                 | string  | End-User の選好するEmailアドレス                     |
| `email_verified`        | boolean | Emailアドレスが検証済みかどうか                          |
| `gender`                | string  | 性別（例：male, female）                          |
| `birthdate`             | string  | 生年月日（例：1990-01-01）                          |
| `zoneinfo`              | string  | タイムゾーン情報                                    |
| `locale`                | string  | ロケール（例：ja-JP）                               |
| `phone_number`          | string  | 電話番号（E.164形式が推奨）                            |
| `phone_number_verified` | boolean | 電話番号が検証済みかどうか                               |
| `address`               | object  | 郵送先住所（JSONオブジェクト）                           |
| `custom_properties`     | object  | カスタムのユーザークレーム（JSONオブジェクト）                   |
| `password`              | object  | パスワード.DB登録時はハッシュ化を行う。                       |

#### 🧩 `プロパティ属性`

プロパティごとに属性を定義します。

| フィールド         | 説明                                                        |
|---------------|-----------------------------------------------------------|
| `type`        | `string`, `integer`, `boolean`, `object`, `array` のいずれか。  |
| `items`       | `array`型の場合の要素の型。                                         |
| `enum`        | 許可された値の一覧（例：`["male", "female"]`）。                        |
| `minLength`   | 文字列の最小文字数。                                                |
| `maxLength`   | 文字列の最大文字数。                                                |
| `pattern`     | 正規表現による文字列制約（例：`^[A-Z][a-z]+$`）。                          |
| `format`      | `email`, `uuid`, `uri`, `date`, `mobile_phone_number` など。 |
| `description` | 項目の説明。UIなどで利用可能。                                          |

例:

```json
{
  "properties": {
    "email": {
      "type": "string",
      "format": "email",
      "minLength": 5,
      "maxLength": 256,
      "description": "ログインに使用するメールアドレス"
    }
  }
}
```

---

### 🧪 サンプル（抜粋）

```json
{
  "type": "initial-registration",
  "payload": {
    "required": [
      "email",
      "password",
      "name"
    ],
    "properties": {
      "email": {
        "type": "string",
        "format": "email",
        "maxLength": 255
      },
      "password": {
        "type": "string",
        "pattern": "^(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()]).+$",
        "minLength": 8,
        "maxLength": 64
      },
      "name": {
        "type": "string",
        "maxLength": 255
      }
    }
  }
}
```

---

## 利用方法

1. 方式：`initial-registration` の設定を登録する
2. 認可リクエスト `prompt=create` を指定するなどして、signup画面を表示する
3. 認可画面から`initial-registration`のschema定義に応じたリクエストボディで `/v1/authorizations/{id}/initial-registration` にリクエストを行う。


### 🔁 API リクエスト例

```http
POST /v1/authorizations/1234567890/initial-registration
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "Secret123!",
  "name": "Taro Yamada"
}
```

## 内部ロジック

1. **スキーマ取得**
   `AuthenticationConfigurationQueryRepository.get(tenant, "initial-registration", Map.class)` により、テナントごとの属性スキーマ（JSON Schema）を取得。

2. **JSON Schemaバリデーション**
   `JsonSchemaValidator` によって `AuthenticationInteractionRequest` の内容を検証。

3. **ユーザー重複チェック**
   すでに登録済みのユーザーがいないかを確認。

4. **ユーザー生成**
   バリデーション済みの属性から `User` を生成し、password属性が存在する場合は `passwordEncodeDelegation` によりパスワードをハッシュ化。

5. **成功レスポンス生成**
   認証成功時は `user` と `authentication` 情報を含むレスポンスを返却。

6. **バリデーションエラー時 or 重複時**
   適切な `clientError` レスポンスと共に `user_signup_failure` や `user_signup_conflict` の `SecurityEventType` を発行。

このように、初期登録ではテナントごとに定義されたJSON Schemaに従って、柔軟に入力項目の構造・制約をカスタマイズできます。

### 💡 補足事項

- `additionalProperties: false` により、未定義の項目は拒否されます。
- スキーマは OpenID Connect や OAuth 2.0 における**ユーザー情報（claims）管理**と連携可能です。
- `custom_properties` により、動的拡張も可能です（管理画面による動的フォーム生成をサポート）。
