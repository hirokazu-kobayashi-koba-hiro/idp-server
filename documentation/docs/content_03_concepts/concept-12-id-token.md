# IDトークン（ID Token）

## IDトークンとは

**IDトークン（ID Token）** は、OpenID Connect認証において、**認証結果とユーザー情報を証明するJWT**です。

### 役割
- ✅ 認証が成功したことの証明
- ✅ 認証されたユーザーの識別子（`sub`）の提供
- ✅ 認証時刻・認証方法などのメタ情報の伝達

### 発行フロー
- **Authorization Code Flow**: トークンエンドポイントで発行
- **CIBA**: トークンエンドポイントで発行
- **Hybrid Flow**: 認可エンドポイントとトークンエンドポイントで発行

**RFC準拠**: [OpenID Connect Core 1.0 Section 2](https://openid.net/specs/openid-connect-core-1_0.html#IDToken)

---

## 標準クレーム（Standard Claims）

以下は OpenID Connect 仕様で定義された標準クレームの一覧です：

| Claim                   | 型       | 説明                                          |
|-------------------------|---------|---------------------------------------------|
| `sub`                   | string  | Subject - Issuer における End-User の識別子         |
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
| `updated_at`            | number  | 最終更新日時（UNIXタイムスタンプ）                         |
| `iss`                   | string  | 発行者識別子（例：`https://idp.example.com`）         |
| `aud`                   | string  | 対象クライアントID                                  |
| `exp`                   | number  | 有効期限（UNIXタイムスタンプ）                           |
| `iat`                   | number  | 発行時刻（UNIXタイムスタンプ）                           |
| `auth_time`             | number  | 最終認証時刻                                      |
| `acr`                   | string  | 認証コンテキストクラス参照（例：`urn:acr:1`）                |
| `amr`                   | array   | 認証方法参照（例：`password`, `mfa`, `webauthn`）     |
| `nonce`                 | string  | リプレイ攻撃防止のための一意なトークン                         |
| `azp`                   | string  | 承認されたパーティー（`aud` に複数値がある場合に使用）              |

---

## 3. カスタムクレーム.claims:xx スコープによる動的設定

`idp-server` では、スコープに `claims:`プレフィックス付きスコープが含まれる場合、ユーザーのカスタム属性やロール情報を動的に
ID トークンに含めることができます。

これは `ScopeMappingCustomClaimsCreator` により実現され、以下の条件で動作します：

* `id_token_strict_mode` が無効であること
* `enabledCustomClaimsScopeMapping` が有効であること
* 対象スコープが `claims:` プレフィックスで始まっていること

対象スコープが `claims:roles` の場合、ユーザーが持つロール一覧（リスト形式）が `roles` クレームとして付加されます。

同様に `claims:assigned_tenants` や `claims:assigned_organizations` を指定することで、関連するテナントや組織のIDを含めることも可能です。

---

## 4. Verified Claims（OIDC4IDA）

Identity Assurance（OIDC for Identity Assurance）に対応するため、IDトークンに `verified_claims`
を含めて、正式に検証されたユーザー属性を伝えることができます。

### verified_claims の構造例：

```json
{
  "verified_claims": {
    "verification": {
      "trust_framework": "de_aml",
      "time": "2025-04-01T12:00:00Z",
      "evidence": [
        {
          "type": "id_document",
          "method": "pipp",
          "time": "2025-04-01T11:00:00Z"
        }
      ]
    },
    "claims": {
      "given_name": "Taro",
      "family_name": "Yamada",
      "birthdate": "1990-01-01"
    }
  }
}
```

* `verification`：検証のメタ情報（フレームワーク、時刻、証拠）
* `claims`：検証済みの属性情報
* 信頼フレームワーク（例：`de_aml`, `eidas`）および証拠種別はOID4IDA仕様に準拠

---

## 5. 署名と暗号化

* **署名（JWS）**：デフォルトは `RS256`、`ES256` も対応
* **暗号化（JWE）**：`id_token_encrypted_response_alg` により設定可能

---

## 6. IDトークンの例（JWT）

### Base64 エンコード形式：

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2lkcC5leGFtcGxlLmNvbSIsInN1YiI6ImFiYzEyMyIsImF1ZCI6IjEyMzQiLCJleHAiOjE2OTU1NTU2MDAsImlhdCI6MTY5NTU1MjAwMCwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIn0.XYZ
```

### デコード済み Payload：

```json
{
  "iss": "https://idp.example.com",
  "sub": "abc123",
  "aud": "1234",
  "exp": 1695555600,
  "iat": 1695552000,
  "email": "user@example.com"
}
```

---

このように、IDトークンはセキュアかつ柔軟に拡張可能であり、テナント設定やプロファイルに応じてカスタマイズ可能です。

---

## 📚 関連ドキュメント

### 技術詳細
- [AI開発者向け: Core - Token](../content_10_ai_developer/ai-11-core.md#token---トークンドメイン) - トークン発行・検証ロジック
- [AI開発者向け: Core - OAuth](../content_10_ai_developer/ai-11-core.md#oauth---oauth-20コア) - OAuth 2.0仕様準拠実装

### 仕様
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) - ID Token仕様
