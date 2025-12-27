# RFC 9396: OAuth 2.0 Rich Authorization Requests（RAR）

RFC 9396 は、従来の `scope` パラメータでは表現できない複雑な認可要求を構造化された JSON で表現するための仕様です。

---

## 第1部: 概要編

### Rich Authorization Requests とは？

RAR は、認可リクエストで**詳細な認可情報**を JSON 形式で指定できるようにする拡張です。

```
従来（scope）:
  scope=read write

  問題:
    - 「何を」read するのか不明
    - 金額制限などを表現できない
    - リソース固有の条件を指定できない

RAR:
  authorization_details=[
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "amount": "100.00",
        "currency": "EUR"
      },
      "creditorAccount": {
        "iban": "DE89370400440532013000"
      }
    }
  ]
```

### なぜ RAR が必要なのか？

| 課題 | scope の限界 |
|------|-------------|
| 詳細な権限 | `read` ではなく「100EUR まで」を表現できない |
| リソース指定 | どのリソースに対する権限か不明確 |
| 条件付き認可 | 時間制限や回数制限を表現できない |
| 複雑なユースケース | 決済、銀行 API、ヘルスケアなど |

### 典型的なユースケース

```
1. 決済（PSD2/Open Banking）:
   「DE89...の口座から 100EUR を送金する権限」

2. データ共有:
   「2024年1月から3月の取引履歴を読み取る権限」

3. ヘルスケア:
   「患者 ID: 12345 のカルテを参照する権限」

4. ファイルアクセス:
   「/documents/contracts/ フォルダの PDF を読み取る権限」
```

---

## 第2部: 詳細編

### authorization_details パラメータ

| 特性 | 説明 |
|------|------|
| 形式 | JSON 配列 |
| 場所 | 認可エンドポイント、トークンエンドポイント、PAR |
| エンコード | URL エンコード必須 |
| 複数指定 | 配列で複数の認可詳細を指定可能 |

### 基本構造

```json
[
  {
    "type": "payment_initiation",
    "locations": ["https://api.bank.example.com"],
    "actions": ["initiate", "status"],
    "datatypes": ["payment"],
    "identifier": "payment-12345",
    "privileges": ["execute"],
    // type 固有のフィールド
    "instructedAmount": {
      "amount": "100.00",
      "currency": "EUR"
    }
  }
]
```

### 共通フィールド

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `type` | ✅ | 認可タイプの識別子 |
| `locations` | △ | リソースサーバーの URI |
| `actions` | △ | 許可されるアクション |
| `datatypes` | △ | アクセス対象のデータタイプ |
| `identifier` | △ | 特定のリソースの識別子 |
| `privileges` | △ | 許可される権限 |

### 認可リクエスト

#### GET リクエスト（エンコード済み）

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https://client.example.com/callback
  &state=xyz
  &authorization_details=%5B%7B%22type%22%3A%22payment_initiation%22%2C%22instructedAmount%22%3A%7B%22amount%22%3A%22100.00%22%2C%22currency%22%3A%22EUR%22%7D%7D%5D
```

#### PAR（Pushed Authorization Request）と組み合わせ

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/json
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

{
  "response_type": "code",
  "client_id": "s6BhdRkqt3",
  "redirect_uri": "https://client.example.com/callback",
  "state": "xyz",
  "code_challenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
  "code_challenge_method": "S256",
  "authorization_details": [
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "amount": "100.00",
        "currency": "EUR"
      },
      "creditorAccount": {
        "iban": "DE89370400440532013000"
      }
    }
  ]
}
```

### トークンリクエスト

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https://client.example.com/callback
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

### トークンレスポンス

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "authorization_details": [
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "amount": "100.00",
        "currency": "EUR"
      },
      "creditorAccount": {
        "iban": "DE89370400440532013000"
      }
    }
  ]
}
```

認可サーバーは、実際に付与された `authorization_details` をレスポンスに含めます。

### JWT アクセストークンの場合

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "https://api.bank.example.com",
  "exp": 1704153600,
  "iat": 1704150000,
  "authorization_details": [
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "amount": "100.00",
        "currency": "EUR"
      },
      "creditorAccount": {
        "iban": "DE89370400440532013000"
      }
    }
  ]
}
```

### ユースケース別の例

#### 1. 決済開始（Payment Initiation）

```json
[
  {
    "type": "payment_initiation",
    "actions": ["initiate", "status", "cancel"],
    "locations": ["https://api.bank.example.com/payments"],
    "instructedAmount": {
      "amount": "100.00",
      "currency": "EUR"
    },
    "creditorName": "Merchant XYZ",
    "creditorAccount": {
      "iban": "DE89370400440532013000"
    },
    "remittanceInformationUnstructured": "Ref: Invoice 12345"
  }
]
```

#### 2. 口座情報アクセス（Account Information）

```json
[
  {
    "type": "account_information",
    "actions": ["read"],
    "locations": ["https://api.bank.example.com/accounts"],
    "datatypes": ["accounts", "balances", "transactions"],
    "accounts": [
      {
        "iban": "DE89370400440532013000"
      }
    ],
    "historicalPeriod": {
      "from": "2024-01-01",
      "to": "2024-03-31"
    }
  }
]
```

#### 3. ヘルスケアデータ

```json
[
  {
    "type": "health_record_access",
    "actions": ["read"],
    "locations": ["https://api.hospital.example.com/records"],
    "datatypes": ["diagnoses", "medications", "lab_results"],
    "patient_id": "P-12345",
    "time_period": {
      "start": "2023-01-01",
      "end": "2024-01-01"
    }
  }
]
```

#### 4. ファイルストレージ

```json
[
  {
    "type": "file_access",
    "actions": ["read", "write"],
    "locations": ["https://api.storage.example.com"],
    "paths": [
      "/documents/contracts/*",
      "/documents/invoices/*"
    ],
    "file_types": ["pdf", "docx"]
  }
]
```

### 認可サーバーによる処理

```
authorization_details の処理フロー:

1. パース・検証
   ├── JSON 形式の検証
   ├── 必須フィールド（type）の確認
   └── type ごとのスキーマ検証

2. 認可画面の表示
   └── authorization_details の内容を
       ユーザーに分かりやすく表示

3. 認可の記録
   └── 認可コードまたはトークンに
       authorization_details を紐付け

4. トークン発行
   └── JWT の場合は authorization_details
       クレームを含める

5. イントロスペクション
   └── authorization_details を返す
```

### 認可画面の表示例

```
┌─────────────────────────────────────────────────────┐
│                    認可リクエスト                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Merchant XYZ があなたのアカウントへの               │
│  アクセスを要求しています:                           │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │ 決済の開始                                   │   │
│  │                                             │   │
│  │   金額: €100.00                             │   │
│  │   送金先: DE89 3704 0044 0532 0130 00       │   │
│  │   受取人: Merchant XYZ                      │   │
│  │   摘要: Ref: Invoice 12345                  │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│         [ 許可する ]      [ 拒否する ]               │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### scope との併用

```json
{
  "scope": "openid profile",
  "authorization_details": [
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "amount": "100.00",
        "currency": "EUR"
      }
    }
  ]
}
```

- `scope`: 一般的な権限（OpenID Connect など）
- `authorization_details`: 詳細な権限（決済など）

### リソースサーバーの検証

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| PAR の使用 | authorization_details は PAR 経由で送信 |
| 署名付きリクエスト | Request Object で署名 |
| 最小権限 | 必要最小限の権限のみ要求 |
| 有効期限 | 認可の有効期限を設定 |
| 監査ログ | authorization_details を含めて記録 |
| ユーザー同意 | 詳細を明確に表示して同意を得る |

### ディスカバリーメタデータ

```json
{
  "issuer": "https://auth.example.com",
  "authorization_details_types_supported": [
    "payment_initiation",
    "account_information"
  ]
}
```

---

## 参考リンク

- [RFC 9396 - OAuth 2.0 Rich Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9396)
- [RFC 9126 - OAuth 2.0 Pushed Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9126)
- [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)
