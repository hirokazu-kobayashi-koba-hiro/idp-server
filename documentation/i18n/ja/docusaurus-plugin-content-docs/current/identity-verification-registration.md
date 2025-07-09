# 身元確認データ登録

## 概要

`idp-server` は身元確認済みID（verified ID）を提供するにあたり、身元確認済みデータを直接登録することができます。

身元確認済みデータは利用者側で担保されていることを前提とします。

## 利用方法

1. `Control Plane API` を使ってテンプレートを事前に登録する（テンプレートIDで管理）。
2. 外部サービスから審査結果情報を受信。
3. `idp-server` は `verified_claims` をユーザーに紐づけて永続化。
4. 認証時、IDトークンやUserInfoに `verified_claims` が含まれる。

## 設定項目（テンプレート定義）

| 項目                              | 内容                            |
|---------------------------------|-------------------------------|
| `id`                            | テンプレートのUUID                   |
| `type`                          | ワークフロー種別（例: `trust-service`）  |
| `external_service`              | 外部ワークフロー名（例: `mocky`）         |
| `verified_claims_configuration` | verified_claimsへのマッピング定義（※後述） |
| `registration`                  | 身元確認結果登録API定義                 |

### `registration`設定

| 項目                            | 説明                             |
|-------------------------------|--------------------------------|
| `basic_auth`                  | Basic認証                        |        
| `request_validation_schema`   | APIリクエストボディのバリデーションJSON Schema |
| `request_verification_schema` | 重複申請や、ユーザー情報との不一致チェックを行うための定義  |

## JSON Schema による検証

テンプレート内では、各リクエスト／レスポンスについて `request_validation_schema` および `response_validation_schema`
を記述可能です。  
これにより、受け取る／送るデータの型、必須項目、フォーマットなどを厳格に制御できます。

```json
{
  "request_validation_schema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "request Application",
    "type": "object",
    "required": [
      "last_name",
      "first_name",
      "last_name_kana",
      "first_name_kana",
      "birthdate",
      "nationality",
      "email_address",
      "mobile_phone_number",
      "address"
    ],
    "properties": {
      "last_name": {
        "type": "string",
        "maxLength": 255
      },
      "first_name": {
        "type": "string",
        "maxLength": 255
      },
      "last_name_kana": {
        "type": "string",
        "maxLength": 255
      },
      "first_name_kana": {
        "type": "string",
        "maxLength": 255
      },
      "birthdate": {
        "type": "string",
        "format": "date"
      },
      "nationality": {
        "type": "string",
        "maxLength": 255
      },
      "email_address": {
        "type": "string",
        "maxLength": 255,
        "pattern": "^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$"
      },
      "mobile_phone_number": {
        "type": "string",
        "maxLength": 11,
        "pattern": "^[0-9]{10,11}$"
      },
      "address": {
        "type": "object",
        "required": [
          "street_address",
          "locality",
          "region",
          "postal_code",
          "country"
        ],
        "properties": {
          "street_address": {
            "type": "string",
            "maxLength": 255
          },
          "locality": {
            "type": "string",
            "maxLength": 255
          },
          "region": {
            "type": "string",
            "maxLength": 255
          },
          "postal_code": {
            "type": "string",
            "maxLength": 255
          },
          "country": {
            "type": "string",
            "maxLength": 255
          }
        }
      }
    }
  }
}
```

### JSON Schema バリデーション要素一覧

| 要素名         | 説明                                                   |
|-------------|------------------------------------------------------|
| `required`  | 必須項目を定義。未入力時は `missing property` としてバリデーションエラーになります。 |
| `format`    | `date`, `uuid`, `uri` などのフォーマットを検証（独自拡張可能）。          |
| `pattern`   | 正規表現パターンを指定して文字列形式を厳格にチェック。電話番号やメールに便利。              |
| `minLength` | 文字列の最小長を定義。                                          |
| `maxLength` | 文字列の最大長を定義。                                          |
| `enum`      | 許可された値のリストを定義（列挙）。 `"enum": ["1", "2", "3"]` など。     |

### 配列型（type: array）

| 要素名           | 説明                                       |
|---------------|------------------------------------------|
| `minItems`    | 配列の要素数の最小値を定義。要素が足りない場合にバリデーションエラー。      |
| `maxItems`    | 配列の要素数の最大値を定義。要素が多すぎるとバリデーションエラー。        |
| `uniqueItems` | `true` にすると、配列内の要素が重複してはいけない（ユニーク）制約になる。 |



## 🔧 verified_claims_configuration

外部から取得したデータを、`verified_claims` にどのようにマッピングするかを定義します。  
マッピングルール形式で記述し、柔軟な変換に対応。

### 例

```json
{
  "verified_claims_configuration": {
    "mapping_rules": [
      {
        "from": "$.verification.trust_framework",
        "to": "verification.trust_framework"
      },
      {
        "from": "$.verification.evidence[0].type",
        "to": "verification.evidence.0.type"
      },
      {
        "from": "$.verification.evidence[0].check_details[0].check_method",
        "to": "verification.evidence.0.check_details.0.check_method"
      },
      {
        "from": "$.verification.evidence[0].check_details[0].organization",
        "to": "verification.evidence.0.check_details.0.organization"
      },
      {
        "from": "$.claims.given_name",
        "to": "claims.given_name"
      },
      {
        "from": "$.claims.address.postal_code",
        "to": "claims.address.postal_code"
      }
    ]
  }
}
```

| フィールド          | 説明                                                       |
|----------------|----------------------------------------------------------|
| `from`         | JSONPathでデータの抽出元を指定                                      |
| `to`           | verified_claimsの構造に沿った出力先パス                              |
| `convert_type` | 省略可。型変換が必要な場合に指定（`string`, `int`, `boolean`, `datetime`） |

※ convert_type が省略された場合は自動判定。必要に応じて型変換は TypeConverter により実施されます。

### verified_claimsスキーマの例

```json
{
  "verification": {
    "trust_framework": "idv",
    "time": "2025-06-01T00:00:00Z"
  },
  "claims": {
    "given_name": "太郎",
    "family_name": "山田",
    "birthdate": "1990-01-01",
    "email": "taro@example.com"
  }
}
```

- verified_claimsは動的なJSON構造で保存され、認証フロー内でIDトークンやuserinfoに反映。
- コールバックデータがそのまま使えない場合でも、mapping_rulesを使って構造を変換可能。
- nested arrayやobjectのマッピングにも対応。

