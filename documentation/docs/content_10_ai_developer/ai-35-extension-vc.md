# idp-server-core-extension-verifiable-credentials - VC拡張

## モジュール概要

**情報源**: `libs/idp-server-core-extension-verifiable-credentials/`
**確認日**: 2025-10-12

### 責務

Verifiable Credentials (検証可能な資格情報) 実装。

**仕様**: [OpenID for Verifiable Credentials](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)

### 主要機能

- **Credential Issuance**: VC発行
- **Credential Format**: `jwt_vc_json`, `ldp_vc`
- **Batch Credential**: 複数VC一括発行
- **Deferred Credential**: 遅延発行

## Credential 構造

### Credential Definition

```json
{
  "format": "jwt_vc_json",
  "credential_definition": {
    "type": ["VerifiableCredential", "UniversityDegreeCredential"],
    "credentialSubject": {
      "given_name": {
        "display": [{"name": "Given Name", "locale": "en-US"}]
      },
      "family_name": {
        "display": [{"name": "Family Name", "locale": "en-US"}]
      },
      "degree": {
        "display": [{"name": "Degree"}]
      },
      "gpa": {
        "display": [{"name": "GPA"}]
      }
    }
  }
}
```

### Credential Response

```json
{
  "format": "jwt_vc_json",
  "credential": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOi...",
  "c_nonce": "tZignsnFbp",
  "c_nonce_expires_in": 86400
}
```

## Credential Issuance Flow

### 1. Authorization Request

```
GET /authorize?
  response_type=code&
  client_id=s6BhdRkqt3&
  redirect_uri=https://client.example.org/cb&
  scope=openid&
  authorization_details=[{
    "type": "openid_credential",
    "format": "jwt_vc_json",
    "credential_definition": {
      "type": ["VerifiableCredential", "UniversityDegreeCredential"]
    }
  }]
```

### 2. Token Request

```
POST /token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=SplxlOBeZQQYbYS6WxSbIA&
redirect_uri=https://client.example.org/cb
```

**Response**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "c_nonce": "tZignsnFbp",
  "c_nonce_expires_in": 86400
}
```

### 3. Credential Request

```
POST /credential
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR...
Content-Type: application/json

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

## Batch Credential Issuance

複数のVCを一括発行。

```json
{
  "credential_requests": [
    {
      "format": "jwt_vc_json",
      "credential_definition": {
        "type": ["VerifiableCredential", "UniversityDegreeCredential"]
      }
    },
    {
      "format": "jwt_vc_json",
      "credential_definition": {
        "type": ["VerifiableCredential", "EmployeeIDCredential"]
      }
    }
  ]
}
```

## Deferred Credential Issuance

即座に発行できない場合の遅延発行。

**Initial Response**:
```json
{
  "transaction_id": "8xLOxBtZp8",
  "c_nonce": "wlbQc6pCJp",
  "c_nonce_expires_in": 86400
}
```

**Deferred Request**:
```
POST /deferred
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR...
Content-Type: application/json

{
  "transaction_id": "8xLOxBtZp8"
}
```

---

## 次のステップ

- [拡張機能層トップに戻る](./ai-30-extensions.md)
- [他の拡張モジュール](./extensions.md#概要)

---

**情報源**:
- `libs/idp-server-core-extension-verifiable-credentials/`
- [OpenID for Verifiable Credentials](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)

**最終更新**: 2025-10-12
