# OpenID for Verifiable Presentations（OID4VP）

OID4VP は、ウォレットが保持する Verifiable Credential を検証者（Verifier）に提示するための仕様です。

---

## 第1部: 概要編

### Verifiable Presentation とは？

Verifiable Presentation（VP）は、ウォレットが Verifiable Credential を検証者に提示する際のコンテナです。

```
Verifiable Presentation の役割:

  ┌─────────────────────────────────────────────────────────────┐
  │                  Verifiable Presentation                   │
  ├─────────────────────────────────────────────────────────────┤
  │  holder: did:key:z6Mkj3PUd1...                             │
  │  verifiableCredential:                                      │
  │    ┌─────────────────────────────────────────────────────┐ │
  │    │  Verifiable Credential #1 (運転免許証)               │ │
  │    │  issuer: did:example:dmv                             │ │
  │    │  credentialSubject: { name: 山田太郎, ... }          │ │
  │    └─────────────────────────────────────────────────────┘ │
  │    ┌─────────────────────────────────────────────────────┐ │
  │    │  Verifiable Credential #2 (銀行口座証明)             │ │
  │    │  issuer: did:example:bank                            │ │
  │    │  credentialSubject: { accountHolder: 山田太郎 }      │ │
  │    └─────────────────────────────────────────────────────┘ │
  │  proof:                                                     │
  │    type: Ed25519Signature2020                              │
  │    created: 2024-01-15T10:00:00Z                           │
  │    challenge: xyz123 (Verifier から受け取った nonce)       │
  │    verificationMethod: did:key:z6Mkj3PUd1...#key-1         │
  └─────────────────────────────────────────────────────────────┘

VP は Holder（ウォレット）が署名 → Verifier への提示時点での所有を証明
```

### OID4VP のフロー

```
Same-Device フロー（スマホで提示）:

  ┌────────┐                              ┌──────────┐
  │ Wallet │                              │ Verifier │
  │ (User) │                              │ (RP)     │
  └────────┘                              └──────────┘
       │                                         │
       │  1. 認可リクエスト（QR or ディープリンク）│
       │ ◄─────────────────────────────────────── │
       │                                         │
       │  2. Credential 選択・同意               │
       │     (ユーザー操作)                       │
       │                                         │
       │  3. VP Token（認可レスポンス）           │
       │ ─────────────────────────────────────► │
       │                                         │
       │  4. VP の検証                           │
       │                                         │
       │  5. サービス提供                        │
       │ ◄─────────────────────────────────────── │


Cross-Device フロー（PC で QR を表示、スマホで読み取り）:

  ┌────────┐          ┌────────┐          ┌──────────┐
  │ Wallet │          │ Browser│          │ Verifier │
  │(スマホ)│          │  (PC)  │          │   (RP)   │
  └────────┘          └────────┘          └──────────┘
       │                   │                    │
       │                   │  1. QR 表示        │
       │                   │ ◄────────────────── │
       │                   │                    │
       │  2. QR スキャン   │                    │
       │ ◄─────────────────│                    │
       │                   │                    │
       │  3. VP 提示                            │
       │ ───────────────────────────────────► │
       │                   │                    │
       │                   │  4. 認証完了通知   │
       │                   │ ◄────────────────── │
```

### ユースケース

| ユースケース | Verifier | 要求される VC |
|-------------|----------|--------------|
| 年齢確認 | 酒類販売店 | 運転免許証（年齢のみ） |
| オンライン契約 | 不動産会社 | 本人確認書類 |
| 資格確認 | 病院 | 医師免許証 |
| KYC | 銀行 | 本人確認済み証明 |
| ログイン | Web サービス | 認証クレデンシャル |

---

## 第2部: 詳細編

### 認可リクエスト

Verifier からウォレットへのリクエスト。

#### Same-Device（ディープリンク）

```
openid4vp://?
  client_id=https://verifier.example.com
  &request_uri=https://verifier.example.com/request/xyz123
```

#### Cross-Device（QR コード）

```
openid4vp://?
  client_id=https://verifier.example.com
  &request_uri=https://verifier.example.com/request/xyz123
```

#### Request Object

```json
{
  "response_type": "vp_token",
  "client_id": "https://verifier.example.com",
  "redirect_uri": "https://verifier.example.com/callback",
  "response_mode": "direct_post",
  "nonce": "n-0S6_WzA2Mj",
  "state": "af0ifjsldkj",
  "presentation_definition": {
    "id": "example_presentation",
    "input_descriptors": [
      {
        "id": "id_card",
        "name": "本人確認書類",
        "purpose": "年齢確認のため生年月日を確認します",
        "constraints": {
          "fields": [
            {
              "path": ["$.credentialSubject.birthdate", "$.vc.credentialSubject.birthdate"],
              "filter": {
                "type": "string",
                "format": "date"
              }
            }
          ]
        }
      }
    ]
  },
  "client_metadata": {
    "client_name": "Example Verifier",
    "logo_uri": "https://verifier.example.com/logo.png",
    "tos_uri": "https://verifier.example.com/tos",
    "policy_uri": "https://verifier.example.com/privacy"
  }
}
```

### Presentation Definition

要求する Credential の条件を定義。

```json
{
  "id": "employment_verification",
  "name": "在籍証明",
  "purpose": "従業員であることを確認します",
  "input_descriptors": [
    {
      "id": "employee_credential",
      "name": "従業員証明書",
      "constraints": {
        "limit_disclosure": "required",
        "fields": [
          {
            "path": ["$.type"],
            "filter": {
              "type": "array",
              "contains": {
                "const": "EmployeeCredential"
              }
            }
          },
          {
            "path": ["$.credentialSubject.employeeId"],
            "purpose": "従業員番号の確認"
          },
          {
            "path": ["$.credentialSubject.department"],
            "purpose": "所属部署の確認",
            "optional": true
          }
        ]
      }
    }
  ]
}
```

### Input Descriptor の制約

| フィールド | 説明 |
|-----------|------|
| `path` | JSONPath 式で値の位置を指定 |
| `filter` | JSON Schema で値の条件を指定 |
| `purpose` | フィールドの利用目的 |
| `optional` | オプションフィールド |
| `intent_to_retain` | Verifier がデータを保持する意図 |

### limit_disclosure

選択的開示の制御。

```json
{
  "constraints": {
    "limit_disclosure": "required",
    "fields": [
      {
        "path": ["$.credentialSubject.age_over_21"]
      }
    ]
  }
}
```

| 値 | 説明 |
|----|------|
| `required` | 指定フィールドのみ開示（SD-JWT 等で必須） |
| `preferred` | 可能であれば制限 |

### Response Mode

#### direct_post

VP を直接 Verifier に POST（推奨）。

```http
POST /callback HTTP/1.1
Host: verifier.example.com
Content-Type: application/x-www-form-urlencoded

vp_token=eyJhbGciOiJFUzI1NiIs...
&presentation_submission={...}
&state=af0ifjsldkj
```

#### direct_post.jwt

JWT でラップして送信。

```http
POST /callback HTTP/1.1
Host: verifier.example.com
Content-Type: application/x-www-form-urlencoded

response=eyJhbGciOiJFUzI1NiIs...
```

#### fragment

Same-Device でリダイレクト。

```
https://verifier.example.com/callback#
  vp_token=eyJhbGciOiJFUzI1NiIs...
  &presentation_submission={...}
  &state=af0ifjsldkj
```

### VP Token

ウォレットが生成する Verifiable Presentation。

#### JWT 形式

```json
{
  "header": {
    "alg": "ES256",
    "typ": "JWT",
    "kid": "did:key:z6Mkj3PUd1...#key-1"
  },
  "payload": {
    "iss": "did:key:z6Mkj3PUd1...",
    "aud": "https://verifier.example.com",
    "nbf": 1704153600,
    "exp": 1704157200,
    "nonce": "n-0S6_WzA2Mj",
    "vp": {
      "@context": ["https://www.w3.org/2018/credentials/v1"],
      "type": ["VerifiablePresentation"],
      "verifiableCredential": [
        "eyJhbGciOiJFUzI1NiIs..."
      ]
    }
  }
}
```

### Presentation Submission

どの Credential がどの要件を満たすかを示すマッピング。

```json
{
  "id": "presentation_submission_1",
  "definition_id": "example_presentation",
  "descriptor_map": [
    {
      "id": "id_card",
      "format": "jwt_vp_json",
      "path": "$",
      "path_nested": {
        "format": "jwt_vc_json",
        "path": "$.vp.verifiableCredential[0]"
      }
    }
  ]
}
```

### Verifier Metadata

```json
{
  "client_id": "https://verifier.example.com",
  "client_name": "Example Verifier",
  "client_name#ja": "サンプル検証者",
  "logo_uri": "https://verifier.example.com/logo.png",
  "tos_uri": "https://verifier.example.com/tos",
  "policy_uri": "https://verifier.example.com/privacy",
  "vp_formats": {
    "jwt_vp_json": {
      "alg": ["ES256", "ES384"]
    },
    "jwt_vc_json": {
      "alg": ["ES256", "ES384"]
    },
    "mso_mdoc": {
      "alg": ["ES256"]
    }
  }
}
```


### SIOP v2 との組み合わせ

OID4VP は SIOP v2（Self-Issued OpenID Provider）と組み合わせて使用されることが多いです。

```
SIOP v2 + OID4VP:

  認可リクエスト:
    response_type=id_token vp_token
    → ID Token（認証）+ VP Token（属性証明）を同時に要求

  レスポンス:
    id_token=eyJ... （Wallet が発行した ID Token）
    vp_token=eyJ... （Verifiable Presentation）
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| nonce | 必須：リプレイ攻撃を防止 |
| audience | 必須：VP が特定の Verifier 向けであることを検証 |
| 有効期限 | 必須：VP の有効期限を短く設定 |
| Holder Binding | 推奨：Credential が正当な Holder からのものか検証 |
| 選択的開示 | 推奨：必要最小限の情報のみ開示 |
| TLS | 必須：すべての通信で使用 |
| Verifier 検証 | 推奨：信頼できる Verifier かウォレットで確認 |

### エラーコード

| エラー | 説明 |
|--------|------|
| `invalid_request` | 不正なリクエスト |
| `invalid_client` | 不明または不正な Verifier |
| `vp_formats_not_supported` | サポートされていない VP フォーマット |
| `invalid_presentation_definition` | 不正な Presentation Definition |
| `user_cancelled` | ユーザーがキャンセル |

---

## 参考リンク

- [OpenID for Verifiable Presentations](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [DIF Presentation Exchange](https://identity.foundation/presentation-exchange/)
- [Self-Issued OpenID Provider v2](https://openid.net/specs/openid-connect-self-issued-v2-1_0.html)
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
