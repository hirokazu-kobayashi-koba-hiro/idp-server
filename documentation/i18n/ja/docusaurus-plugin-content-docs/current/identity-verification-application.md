# 身元確認申込み

## 概要

`idp-server` は身元確認済みID（verified ID）を提供するにあたり、外部のサービスと連携した **申込み・審査・完了登録**
の一連の申込みを管理できます。

この機能を利用することで、ユーザーから収集した情報を外部の身元確認サービスに送信し、確認済みクレーム（`verified_claims`）として
`idp-server` に反映できます。

外部サービスとの連携はテンプレート形式で柔軟に定義可能であり、JSON Schema による構造化と検証が可能です。

## 利用方法

1. `Control Plane API` を使ってテンプレートを事前に登録する（テンプレートIDで管理）。
2. ユーザーが申込み操作を実行すると、定義済みテンプレートに従って外部APIへデータ送信。
3. 外部サービスからコールバック等で申請IDや審査結果、`verified_claims` 情報を受信。
4. `idp-server` は `verified_claims` をユーザーに紐づけて永続化。
5. 認証時、IDトークンやUserInfoに `verified_claims` が含まれる。

## 設定項目（テンプレート定義）

| 項目                              | 内容                                        |
|---------------------------------|-------------------------------------------|
| `id`                            | テンプレートのUUID                               |
| `type`                          | ワークフロー種別（例: `investment-account-opening`） |
| `external_service`              | 外部ワークフロー名（例: `mocky`）                     |
| `external_application_id_param` | 外部申請IDを示すキー（例: `application_id`）          |
| `oauth_authorization`           | 外部APIへの認証情報（OAuth2設定）                     |
| `verified_claims_schema`        | 検証済みクレームのスキーマ構造定義                         |
| `processes`                     | 外部身元確認API定義であるprocessを複数登録可能              |

### `processe`設定

| 項目                                    | 説明                                                   |
|---------------------------------------|------------------------------------------------------|
| `url`                                 | 外部申請APIのURL。POSTで呼び出される。                             |
| `method`                              | HTTPメソッド（通常は `"POST"`）                               |
| `headers`                             | API呼び出し時のHTTPヘッダ（`Content-Type`, `Authorization` など） |
| `dynamic_body_keys`                   | APIリクエストbodyに含める動的キー。ユーザー入力などから取得                    |
| `static_body`                         | APIリクエストbodyに含める固定キー（例：`{"service_code": "001"}`）    |
| `request_validation_schema`           | APIリクエストボディのバリデーションJSON Schema                       |
| `request_additional_parameter_schema` | ユーザーIDなど `idp-server` から補完する追加パラメータの定義               |
| `request_verification_schema`         | 重複申請や、ユーザー情報との不一致チェックを行うための定義                        |
| `response_validation_schema`          | 外部APIからのレスポンスのスキーマ定義。`application_id` などを格納可能な構造を記載  |

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

また、`store: true` が指定された項目は、申込みデータとして `idp-server` に保存されます。


## 申込APIのパスの動的設定

idp-server の申込APIは、テンプレート定義の内容に基づいて動的にルーティングされる 仕組みになっています。

具体的には、APIのパスの verification-type と process が、テンプレートの "type" フィールドと "processes" に定義されたキーにより組み立てられます。

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
POST /{tenant-id}/v1/identity-verification/applications/investment-account-opening/apply
POST /{tenant-id}/v1/identity-verification/applications/investment-account-opening/ekyc-request
```


### ポイント

- "type" は API の verification-type に対応
- "processes" の各キー名が process に対応
- APIリクエストを受けると、該当テンプレートとプロセス定義を読み取り、動的に外部API連携や申請処理を行う


## verified_claimsスキーマの例

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

- `verification`：確認の枠組み（OID4IDA準拠も可）
- `claims`：検証済み属性情報

## 注意事項

- `verified_claims_schema` に沿わないデータは保存されません。※今後の機能拡張としてverified_claimsへの動的マッピングを予定。
- コールバックはHTTPS通信で行い、認証ヘッダーなどでセキュアな設計を行ってください。
- 一部テンプレートは `request_verification_schema` による照合が行われ、申請の拒否条件（例：メールアドレス不一致）が適用される場合があります。
- 外部eKYCサービスのAPIの仕様変更に伴いテンプレートの更新が必要となる可能性があります。
