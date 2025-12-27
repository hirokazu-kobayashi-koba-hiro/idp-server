# OpenID for Verifiable Credential Issuance（OID4VCI）

OID4VCI は、OAuth 2.0 を基盤として Verifiable Credential（検証可能なクレデンシャル）を発行するための仕様です。

---

## 第1部: 概要編

### Verifiable Credential とは？

Verifiable Credential（VC）は、発行者がデジタル署名した検証可能な証明書です。

```
従来の証明書:
  紙の証明書 → コピー・改ざんが容易
  PDF → 電子透かし等で保護するが限定的

Verifiable Credential:
  デジタル署名 → 改ざん検知可能
  選択的開示 → 必要な情報のみ開示
  分散型検証 → 発行者に問い合わせ不要

  ┌─────────────────────────────────────────────────┐
  │              Verifiable Credential              │
  ├─────────────────────────────────────────────────┤
  │  @context: ["https://www.w3.org/..."]          │
  │  type: ["VerifiableCredential", "IDCard"]       │
  │  issuer: did:example:university                 │
  │  issuanceDate: 2024-01-01                       │
  │  credentialSubject:                             │
  │    name: 山田太郎                               │
  │    degree: 工学修士                             │
  │  proof:                                         │
  │    type: Ed25519Signature2020                   │
  │    verificationMethod: did:example:uni#key-1    │
  │    proofValue: z3FXQjecWufY46...                │
  └─────────────────────────────────────────────────┘
```

### OID4VCI の役割

OID4VCI は、既存の OAuth 2.0 インフラを活用して VC を発行する仕組みを提供します。

```
OID4VCI のフロー:

  ┌────────┐                              ┌─────────────┐
  │ Wallet │                              │   Issuer    │
  │ (User) │                              │ (大学、銀行) │
  └────────┘                              └─────────────┘
       │                                         │
       │  1. Credential Offer を受け取る          │
       │ ◄─────────────────────────────────────── │
       │                                         │
       │  2. 認可リクエスト（OAuth 2.0）          │
       │ ─────────────────────────────────────► │
       │                                         │
       │  3. ユーザー認証・同意                   │
       │ ◄──────────────────────────────────────►│
       │                                         │
       │  4. 認可コード / アクセストークン         │
       │ ◄─────────────────────────────────────── │
       │                                         │
       │  5. Credential Request                  │
       │ ─────────────────────────────────────► │
       │                                         │
       │  6. Verifiable Credential               │
       │ ◄─────────────────────────────────────── │
       └──────────────────────────────────────────┘
```

### 主要なコンポーネント

| コンポーネント | 説明 |
|---------------|------|
| Credential Issuer | VC を発行するサービス |
| Wallet | ユーザーが VC を保管・管理するアプリ |
| Credential Offer | 発行者からウォレットへの発行提案 |
| Credential Endpoint | VC を発行するエンドポイント |

### ユースケース

| ユースケース | 発行者 | Credential |
|-------------|--------|------------|
| 大学卒業証明 | 大学 | 学位証明書 |
| 運転免許証 | 行政機関 | mDL（モバイル運転免許証） |
| 従業員証明 | 企業 | 在籍証明書 |
| 銀行口座証明 | 銀行 | 口座保有証明 |
| 医療資格 | 医療機関 | 医師免許証 |

---

## 第2部: 詳細編

### Credential Issuer Metadata

発行者のメタデータは `/.well-known/openid-credential-issuer` で公開されます。

```json
{
  "credential_issuer": "https://issuer.example.com",
  "authorization_servers": ["https://auth.example.com"],
  "credential_endpoint": "https://issuer.example.com/credentials",
  "batch_credential_endpoint": "https://issuer.example.com/credentials/batch",
  "deferred_credential_endpoint": "https://issuer.example.com/credentials/deferred",
  "credential_configurations_supported": {
    "UniversityDegree_jwt_vc_json": {
      "format": "jwt_vc_json",
      "scope": "UniversityDegree",
      "cryptographic_binding_methods_supported": ["did:key", "did:web"],
      "credential_signing_alg_values_supported": ["ES256", "ES384"],
      "credential_definition": {
        "type": ["VerifiableCredential", "UniversityDegreeCredential"],
        "credentialSubject": {
          "given_name": {
            "display": [
              {"name": "名", "locale": "ja"},
              {"name": "Given Name", "locale": "en"}
            ]
          },
          "family_name": {
            "display": [
              {"name": "姓", "locale": "ja"},
              {"name": "Family Name", "locale": "en"}
            ]
          },
          "degree": {
            "display": [
              {"name": "学位", "locale": "ja"},
              {"name": "Degree", "locale": "en"}
            ]
          }
        }
      },
      "display": [
        {
          "name": "大学学位証明書",
          "locale": "ja",
          "logo": {
            "uri": "https://issuer.example.com/logo.png"
          },
          "background_color": "#12107c",
          "text_color": "#ffffff"
        }
      ]
    },
    "DriverLicense_mso_mdoc": {
      "format": "mso_mdoc",
      "doctype": "org.iso.18013.5.1.mDL",
      "cryptographic_binding_methods_supported": ["cose_key"],
      "credential_signing_alg_values_supported": ["ES256"],
      "display": [
        {
          "name": "運転免許証",
          "locale": "ja"
        }
      ]
    }
  }
}
```

### Credential Offer

発行者がウォレットに VC 発行を提案します。

#### QR コードまたはディープリンク

```
openid-credential-offer://?credential_offer_uri=https://issuer.example.com/offers/abc123
```

または直接 JSON を含める:

```
openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A...%7D
```

#### Credential Offer の構造

```json
{
  "credential_issuer": "https://issuer.example.com",
  "credential_configuration_ids": [
    "UniversityDegree_jwt_vc_json"
  ],
  "grants": {
    "authorization_code": {
      "issuer_state": "eyJhbGciOiJSU0..."
    },
    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
      "pre-authorized_code": "SplxlOBeZQQYbYS6WxSbIA",
      "tx_code": {
        "input_mode": "numeric",
        "length": 6,
        "description": "SMSで送信されたコードを入力してください"
      }
    }
  }
}
```

### 認可コードフロー

標準的な OAuth 2.0 認可コードフローを使用します。

```http
GET /authorize?
  response_type=code
  &client_id=wallet-app
  &redirect_uri=https://wallet.example.com/callback
  &scope=openid UniversityDegree
  &state=xyz
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
  &issuer_state=eyJhbGciOiJSU0...
  &authorization_details=[{
    "type": "openid_credential",
    "credential_configuration_id": "UniversityDegree_jwt_vc_json"
  }]
```

### Pre-Authorized Code フロー

ユーザーが事前に認証済みの場合に使用される簡略化フロー。

```
Pre-Authorized Code フロー:

  ┌────────┐                              ┌─────────────┐
  │ Wallet │                              │   Issuer    │
  └────────┘                              └─────────────┘
       │                                         │
       │  1. Credential Offer                    │
       │     (pre-authorized_code 含む)          │
       │ ◄─────────────────────────────────────── │
       │                                         │
       │  2. Token Request                       │
       │     grant_type=pre-authorized_code      │
       │ ─────────────────────────────────────► │
       │                                         │
       │  3. Access Token                        │
       │ ◄─────────────────────────────────────── │
       │                                         │
       │  4. Credential Request                  │
       │ ─────────────────────────────────────► │
       │                                         │
       │  5. Verifiable Credential               │
       │ ◄─────────────────────────────────────── │
```

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:pre-authorized_code
&pre-authorized_code=SplxlOBeZQQYbYS6WxSbIA
&tx_code=493536
```

### Credential Request

アクセストークンを使用して VC を要求します。

```http
POST /credentials HTTP/1.1
Host: issuer.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

{
  "format": "jwt_vc_json",
  "credential_definition": {
    "type": ["VerifiableCredential", "UniversityDegreeCredential"]
  },
  "proof": {
    "proof_type": "jwt",
    "jwt": "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0In0..."
  }
}
```

### Proof of Possession

ウォレットが鍵の所有を証明する JWT。

```json
{
  "header": {
    "alg": "ES256",
    "typ": "openid4vci-proof+jwt",
    "kid": "did:key:z6Mkj3PUd1WjvaDhNZhhhXQdz5UnZXmS7ehtx8bsPpD47kKc#key-1"
  },
  "payload": {
    "iss": "did:key:z6Mkj3PUd1WjvaDhNZhhhXQdz5UnZXmS7ehtx8bsPpD47kKc",
    "aud": "https://issuer.example.com",
    "iat": 1704153600,
    "nonce": "tZignsnFbp"
  }
}
```

### Credential Response

#### 即時発行

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "credential": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsInN1YiI6ImRpZDprZXk6ejZNa2ozUFVkMVdqdkFEaE5aaGhoWFFkejVVblpYbVM3ZWh0eDhic1BwRDQ3a0tjIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWVDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImRlZ3JlZSI6eyJ0eXBlIjoiQmFjaGVsb3JEZWdyZWUiLCJuYW1lIjoi5bel5a2m5L+u5aOrIn19fX0.signature",
  "c_nonce": "fGFF7UkhLa",
  "c_nonce_expires_in": 86400
}
```

#### 遅延発行

大量処理や手動審査が必要な場合。

```http
HTTP/1.1 202 Accepted
Content-Type: application/json

{
  "transaction_id": "8xLOxBtZp8",
  "c_nonce": "wlbQc6pCJp",
  "c_nonce_expires_in": 86400
}
```

後で Deferred Credential Endpoint にポーリング:

```http
POST /credentials/deferred HTTP/1.1
Host: issuer.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

{
  "transaction_id": "8xLOxBtZp8"
}
```

### Batch Credential Request

複数の VC を一度に要求。

```http
POST /credentials/batch HTTP/1.1
Host: issuer.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

{
  "credential_requests": [
    {
      "format": "jwt_vc_json",
      "credential_definition": {
        "type": ["VerifiableCredential", "UniversityDegreeCredential"]
      },
      "proof": {
        "proof_type": "jwt",
        "jwt": "eyJhbGciOiJFUzI1NiIs..."
      }
    },
    {
      "format": "jwt_vc_json",
      "credential_definition": {
        "type": ["VerifiableCredential", "EmployeeIDCredential"]
      },
      "proof": {
        "proof_type": "jwt",
        "jwt": "eyJhbGciOiJFUzI1NiIs..."
      }
    }
  ]
}
```

### Credential フォーマット

| フォーマット | 説明 |
|-------------|------|
| `jwt_vc_json` | JWT でエンコードされた W3C VC |
| `jwt_vc_json-ld` | JSON-LD + JWT の W3C VC |
| `ldp_vc` | Linked Data Proof の W3C VC |
| `mso_mdoc` | ISO/IEC 18013-5 の mDL 形式 |
| `vc+sd-jwt` | SD-JWT 形式の VC |


### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| Proof of Possession | 必須：鍵バインディングで VC を保護 |
| nonce | 必須：リプレイ攻撃を防止 |
| PKCE | 必須：認可コードフローで使用 |
| TLS | 必須：すべての通信で使用 |
| TX コード | 推奨：Pre-Authorized Code フローで追加認証 |
| Credential 有効期限 | 推奨：適切な期限を設定 |

### エラーコード

| エラー | 説明 |
|--------|------|
| `invalid_credential_request` | 不正な Credential Request |
| `unsupported_credential_type` | サポートされていない Credential タイプ |
| `unsupported_credential_format` | サポートされていないフォーマット |
| `invalid_proof` | 不正な Proof of Possession |
| `invalid_nonce` | 不正または期限切れの nonce |
| `issuance_pending` | 遅延発行中 |

---

## 参考リンク

- [OpenID for Verifiable Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
- [SD-JWT-based Verifiable Credentials](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/)
- [ISO/IEC 18013-5 Mobile Driving License](https://www.iso.org/standard/69084.html)
