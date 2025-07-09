# 身元確認申込み

## 概要

`idp-server` は身元確認済みID（verified ID）を提供するにあたり、外部のサービスと連携した **申込み・審査・完了登録**
の一連の申込みを管理できます。

この機能を利用することで、ユーザーから収集した情報を外部の身元確認サービスに送信し、確認済みクレーム（`verified_claims`）として
`idp-server` に反映できます。

外部サービスとの連携はテンプレート形式で柔軟に定義可能であり、JSON Schema による構造化と検証、そしてマッピングルールによる変換に対応しています。

## 利用方法

1. `Control Plane API` を使ってテンプレートを事前に登録する（テンプレートIDで管理）。
2. ユーザーが申込み操作を実行すると、定義済みテンプレートに従って外部APIへデータ送信。
3. 外部サービスからコールバック等で申請IDや審査結果を受信。
4. `idp-server` は `verified_claims` をユーザーに紐づけて永続化。
5. 認証時、IDトークンやUserInfoに `verified_claims` が含まれる。

## 設定項目（テンプレート定義）

| 項目                              | 内容                                        |
|---------------------------------|-------------------------------------------|
| `id`                            | テンプレートのUUID                               |
| `type`                          | ワークフロー種別（例: `investment-account-opening`） |
| `external_service`              | 外部ワークフロー名（例: `mocky`）                     |
| `external_application_id_param` | 外部申請IDを示すキー（例: `application_id`）          |
| `oauth_authorization`           | 外部APIへの共通の認証情報（OAuth2設定）                  |
| `hmac_authentication`           | 外部APIへの共通のHmac認証情報                        |
| `verified_claims_configuration` | verified_claimsへのマッピング定義（※後述）             |
| `processes`                     | 外部身元確認API定義であるprocessを複数登録可能              |

### `processe`設定

| 項目                                    | 説明                                                                              |
|---------------------------------------|---------------------------------------------------------------------------------|
| `url`                                 | 外部申請APIのURL。POSTで呼び出される。                                                        |
| `method`                              | HTTPメソッド（通常は `"POST"`）                                                          |
| `authType`                            | 外部APIへの認証方式を示す。`"oauth2"`：OAuth 2.0 認証を使用　`"hmac"`：HMAC署名認証を使用　`"none"`：認証なしで送信 |
| `oauth_authorization`                 | 外部APIへの個別の認証情報（OAuth2設定） ※共通と両方設定がある場合はこちらが優先される                                |
| `hmac_authentication`                 | 外部APIへの個別のHmac認証情報  ※共通と両方設定がある場合はこちらが優先される                                     |
| `headers`                             | API呼び出し時のHTTPヘッダ（`Content-Type`, `Authorization` など）                            |
| `dynamic_body_keys`                   | APIリクエストbodyに含める動的キー。ユーザー入力などから取得                                               |
| `static_body`                         | APIリクエストbodyに含める固定キー（例：`{"service_code": "001"}`）                               |
| `header_mapping_rules`                | 外部APIに送信するヘッダを、リクエストなどから動的に構築するマッピングルール。                                        |       
| `body_mapping_rules`                  | 外部APIに送信するボディを、リクエストなどから動的に構築するマッピングルール。                                        |      
| `query_mapping_rules`                 | 外部APIに送信するクエリパラメータを、リクエストなどから動的に構築するマッピングルール。                                   |        
| `request_validation_schema`           | APIリクエストボディのバリデーションJSON Schema                                                  |
| `request_additional_parameter_schema` | ユーザー情報などから補完する追加パラメータの定義。Httpによる外部通信によるパラメータの追加も可能。                             |
| `request_verification_schema`         | 重複申請や、ユーザー情報との不一致チェックを行うための定義                                                   |
| `response_validation_schema`          | 外部APIからのレスポンスのスキーマ定義。`application_id` などを格納可能な構造を記載                             |
| `request_mapping_rules`               | リクエストデータを加工・変換するためのマッピングルール（※後述）                                                |
| `response_mapping_rules`              | レスポンスデータを加工・変換するためのマッピングルール（※後述）                                                |

#### `processes` に定義できる処理タイプ例

- `apply`：申請データの送信
- `request-ekyc`：eKYC用URL取得
- `complete-ekyc`：完了通知
- `callback-examination`：審査状態の通知（コールバック）
- `callback-result`：verified_claims 登録用データの受信（コールバック）

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

| 要素名           | 説明                                                   |
|---------------|------------------------------------------------------|
| `required`    | 必須項目を定義。未入力時は `missing property` としてバリデーションエラーになります。 |
| `format`      | `date`, `uuid`, `uri` などのフォーマットを検証（独自拡張可能）。          |
| `pattern`     | 正規表現パターンを指定して文字列形式を厳格にチェック。電話番号やメールに便利。              |
| `minLength`   | 文字列の最小長を定義。                                          |
| `maxLength`   | 文字列の最大長を定義。                                          |
| `enum`        | 許可された値のリストを定義（列挙）。 `"enum": ["1", "2", "3"]` など。     |


### 配列型（type: array）

| 要素名           | 説明                                                   |
|---------------|------------------------------------------------------|
| `minItems`    | 配列の要素数の最小値を定義。要素が足りない場合にバリデーションエラー。                  |
| `maxItems`    | 配列の要素数の最大値を定義。要素が多すぎるとバリデーションエラー。                    |
| `uniqueItems` | `true` にすると、配列内の要素が重複してはいけない（ユニーク）制約になる。             |



### idp-server 独自拡張

また、idp-server 独自として下記パラメータをサポートします。

| 拡張要素名     | 説明                                                               |
| --------- | ---------------------------------------------------------------- |
| `store`   | `true` の場合、そのフィールド値を永続化対象として扱う（例えば後工程のマッピング処理などで使用）。             |
| `respond` | `true` の場合、レスポンス時にこの項目を返却対象に含める（デフォルトは `true`。明示的に `false` にも可）。 |



## 申込APIのパスの動的設定

idp-server の申込APIは、テンプレート定義の内容に基づいて動的にルーティングされる 仕組みになっています。

具体的には、APIのパスの verification-type と process が、テンプレートの "type" フィールドと "processes"
に定義されたキーにより組み立てられます。

ベースPath
POST /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{process}

### 例

```json
{
  "type": "investment-account-opening",
  "processes": {
    "apply": {},
    "ekyc-request": {}
  }
}
```

この定義の場合

```
POST /{tenant-id}/v1/me/identity-verification/applications/investment-account-opening/apply
POST /{tenant-id}/v1/me/identity-verification/applications/investment-account-opening/ekyc-request
```

### ポイント

- "type" は API の verification-type に対応
- "processes" の各キー名が process に対応
- APIリクエストを受けると、該当テンプレートとプロセス定義を読み取り、動的に外部API連携や申請処理を行う

## 🔧 Mapping Rules（マッピングルール）

`request_mapping_rules` および `response_mapping_rules` により、リクエストやレスポンスのデータを柔軟に変換できます。

### 例

```json
{
  "request_mapping_rules": [
    {
      "from": "$.header.Authorization",
      "to": "token",
      "convert_type": "string"
    },
    {
      "from": "$.body.user_id",
      "to": "applicant_id",
      "convert_type": "string"
    },
    {
      "from": "$.body.emails[0].value",
      "to": "email",
      "convert_type": "string"
    }
  ]
}
```

### 定義フィールド

| フィールド          | 内容                                           |
|----------------|----------------------------------------------|
| `from`         | JSONPath形式の入力元（`$.header.xxx`, `$.body.xxx`） |
| `to`           | 出力キー名（最終的なJSONにおけるキー）                        |
| `convert_type` | 変換型：`string`, `int`, `boolean`, `datetime` 等 |

### マッピングルールの処理順

1. ヘッダー + ボディを 1つのオブジェクトに統合
2. MappingRule に従いJSONPathから値を抽出
3. 型変換（必要に応じて）
4. フラットマップに追加 → ネスト構造へ復元

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

