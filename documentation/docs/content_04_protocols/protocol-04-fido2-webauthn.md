# FIDO2 / WebAuthn 認証フロー

---

## 前提知識

このドキュメントを理解するには、以下の基礎知識が役立ちます：

- [OAuth 2.0の基本](../content_03_concepts/basic/basic-06-oauth2-authorization.md) - OAuth 2.0の認可の仕組み
- [認可コードグラントフロー](../content_03_concepts/basic/basic-08-oauth2-authorization-code-flow.md) - 認可フロー内での認証
- 公開鍵暗号の基本 - WebAuthnの暗号化の仕組み

---

## 概要

`idp-server` は、W3C WebAuthn および FIDO2 仕様に準拠したパスキー認証をサポートしています。

WebAuthn（Web Authentication API）は、公開鍵暗号方式を用いた強力な認証メカニズムであり、以下のような特徴があります：

- **フィッシング耐性**: 公開鍵認証により、パスワード漏洩のリスクを排除
- **パスワードレス認証**: 生体認証やPINを用いた認証でユーザー体験を向上
- **多様な認証器サポート**: セキュリティキー、スマートフォン、PC内蔵認証器に対応

### ユースケース

| ユースケース | 認証器タイプ | ユーザー体験 |
|---------|---------|---------|
| **パスワードレスログイン** | Platform（TouchID/FaceID/Windows Hello） | デバイス生体認証のみで即座にログイン |
| **2要素認証（2FA）** | Cross-platform（USB/NFCセキュリティキー） | パスワード + セキュリティキータップ |
| **高セキュリティ認証** | FIDO2認定セキュリティキー | PIN入力 + セキュリティキータップ |
| **スマホ連携認証** | Hybrid（QRコード経由） | QRコード読み取り + スマホ生体認証 |

---

## WebAuthn仕様準拠

`idp-server` は以下の仕様に準拠しています：

- [W3C WebAuthn Level 2](https://www.w3.org/TR/webauthn-2/)
- [FIDO CTAP2.1](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)
- [WebAuthn4j 0.30.0.RELEASE](https://github.com/webauthn4j/webauthn4j) - サーバー側検証ライブラリ

---

## シーケンス

### 登録フロー（Registration）

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Browser as ブラウザ
    participant idp as idp-server
    participant Auth as 認証器（Authenticator）

    User ->> Browser: 1. パスキー登録開始
    Browser ->> idp: 2. 登録チャレンジリクエスト
    idp -->> Browser: 3. チャレンジ + オプション返却

    Browser ->> Auth: 4. navigator.credentials.create()
    Auth -->> User: 5. ユーザー検証要求（生体認証/PIN）
    User -->> Auth: 6. 検証完了
    Auth -->> Auth: 7. 鍵ペア生成 + Attestation作成
    Auth -->> Browser: 8. Attestation Response返却

    Browser ->> idp: 9. 登録リクエスト
    idp -->> idp: 10. WebAuthn4j検証
    idp -->> idp: 11. Credential保存
    idp -->> Browser: 12. 登録完了レスポンス
    Browser -->> User: 13. 登録成功通知
```

**主要ステップ**:

1. **チャレンジ取得（1-3）**: ユーザーが登録開始 → サーバーがチャレンジ生成
2. **認証器操作（4-8）**: ブラウザが認証器を呼び出し → 鍵ペア生成 → 公開鍵返却
3. **サーバー検証・保存（9-13）**: サーバーが署名検証 → 公開鍵をデータベース保存

---

### 認証フロー（Authentication）

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Browser as ブラウザ
    participant idp as idp-server
    participant Auth as 認証器（Authenticator）

    User ->> Browser: 1. パスキー認証開始
    Browser ->> idp: 2. 認証チャレンジリクエスト
    idp -->> idp: 3. ユーザーのCredential取得
    idp -->> Browser: 4. チャレンジ + allowCredentials返却

    Browser ->> Auth: 5. navigator.credentials.get()
    Auth -->> User: 6. ユーザー検証要求（生体認証/PIN）
    User -->> Auth: 7. 検証完了
    Auth -->> Auth: 8. Assertion作成（署名生成）
    Auth -->> Browser: 9. Assertion Response返却

    Browser ->> idp: 10. 認証リクエスト
    idp -->> idp: 11. Credential取得
    idp -->> idp: 12. WebAuthn4j検証（署名検証）
    idp -->> idp: 13. signCount更新
    idp -->> Browser: 14. 認証成功レスポンス
    Browser -->> User: 15. ログイン完了
```

**主要ステップ**:

1. **チャレンジ取得（1-4）**: ユーザーが認証開始 → サーバーが保存済みCredential ID返却
2. **認証器操作（5-9）**: ブラウザが認証器を呼び出し → 秘密鍵で署名生成
3. **サーバー検証（10-15）**: サーバーが公開鍵で署名検証 → 認証成功

---

## ユーザー体験に影響するパラメータ

WebAuthnでは、以下のパラメータがユーザーの認証体験に直接影響します。

### 主要パラメータ

| パラメータ | 設定値 | ユーザー体験 | ユースケース |
|-----------|--------|-------------|-------------|
| **residentKey** | `required` | ユーザー名入力不要 | パスワードレスログイン |
|  | `discouraged` | ユーザー名入力必須 | 2要素認証 |
| **userVerification** | `required` | 毎回PIN/生体認証 | 高セキュリティ認証 |
|  | `discouraged` | タップのみ | UX優先 |
| **authenticatorAttachment** | `platform` | デバイス内蔵認証器のみ | TouchID/FaceID |
|  | `cross-platform` | 外部セキュリティキーのみ | YubiKey等 |
|  | 未指定 | 全認証器から選択可能 | 柔軟な認証 |

**詳細**:
- [FIDO2 / WebAuthn 登録フロー詳細](protocol-04-fido2-webauthn-detail-registration.md) - 登録時のパラメータと挙動
- [FIDO2 / WebAuthn 詳細ガイド](protocol-04-fido2-webauthn-detail.md) - 認証時のパラメータと挙動

---

## 設定

### テナント設定項目

| 項目 | 説明 | デフォルト値 |
|------|------|------------|
| `rpId` | Relying Party ID（ドメイン名） | テナントドメイン |
| `origin` | 許可するOriginリスト | テナントURL |
| `timeout` | チャレンジ有効期限（ミリ秒） | 120000（2分） |
| `authenticatorSelection.residentKey` | Resident Key要件 | `preferred` |
| `authenticatorSelection.userVerification` | ユーザー検証要件 | `preferred` |
| `authenticatorSelection.authenticatorAttachment` | 認証器タイプ制約 | 未指定 |

### 設定例: パスワードレスログイン

```json
{
  "rpId": "example.com",
  "origin": "https://example.com",
  "timeout": 120000,
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required",
    "authenticatorAttachment": "platform"
  }
}
```

**動作**: ユーザー名入力不要 + TouchID/FaceID認証のみ

---

### 設定例: 2要素認証（セキュリティキー）

```json
{
  "authenticatorSelection": {
    "residentKey": "discouraged",
    "userVerification": "discouraged",
    "authenticatorAttachment": "cross-platform"
  }
}
```

**動作**: ユーザー名入力 + セキュリティキータップ

---

## 認証ポリシー連携

WebAuthn認証は、認可コードフロー内の認証ステップとして利用できます。

### 認証ポリシー設定例

```json
{
  "authentication_policy": {
    "id": "policy-fido2",
    "conditions": {
      "acr_values": ["fido2"]
    },
    "available_methods": [
      {
        "type": "fido2",
        "configuration": {
          "authenticatorSelection": {
            "residentKey": "required",
            "userVerification": "required"
          }
        }
      }
    ],
    "success_conditions": {
      "required_methods": ["fido2"]
    }
  }
}
```

### 多要素認証フロー

```json
{
  "authentication_policy": {
    "available_methods": [
      {"type": "password"},
      {"type": "fido2"}
    ],
    "success_conditions": {
      "required_methods": ["password", "fido2"],
      "order": "sequential"
    }
  }
}
```

**動作**: パスワード認証成功 → FIDO2認証 → ログイン完了

---

## セキュリティ

### 主要な検証項目

`idp-server` は WebAuthn4j を使用して以下を自動検証します：

| 検証項目 | 目的 |
|---------|------|
| **Origin検証** | フィッシング攻撃防止（異なるドメインからの認証を拒否） |
| **Challenge検証** | 再利用攻撃防止（チャレンジは1回のみ有効） |
| **署名検証** | 秘密鍵の所有証明（公開鍵で署名を検証） |
| **signCount検証** | Credentialクローン検出（カウンタ増加を確認） |

### FAPI準拠

FAPI 1.0 Advanced準拠のための設定：

```json
{
  "authenticatorSelection": {
    "userVerification": "required"
  },
  "timeout": 300000
}
```

**追加要件**:
- TLS 1.2以上
- MTLS（Mutual TLS）推奨

---

## トラブルシューティング

### よくある問題

| 問題 | 原因 | 解決策 |
|------|------|--------|
| **登録時**"認証器が見つかりません" | authenticatorAttachment制約 | [登録詳細 3.1](protocol-04-fido2-webauthn-detail-registration.md#31-認証器が見つかりません) |
| **登録時**"ユーザー検証に失敗" | userVerification="required"だが認証器非対応 | [登録詳細 3.2](protocol-04-fido2-webauthn-detail-registration.md#32-ユーザー検証に失敗しました) |
| **認証時**"認証器が見つかりません" | allowCredentialsとCredential不一致 | [認証詳細 5.1](protocol-04-fido2-webauthn-detail.md#51-認証器が見つかりません) |
| **認証時**パスワードレスログイン不可 | rk=falseでallowCredentials=[] | [認証詳細 5.3](protocol-04-fido2-webauthn-detail.md#53-パスワードレスログインできない) |

**詳細**:
- [FIDO2 / WebAuthn 登録フロー詳細 - トラブルシューティング](protocol-04-fido2-webauthn-detail-registration.md#3-登録時のトラブルシューティング)
- [FIDO2 / WebAuthn 詳細ガイド - トラブルシューティング](protocol-04-fido2-webauthn-detail.md#5-トラブルシューティング)

---

## 参考資料

### 標準仕様
- [W3C WebAuthn Level 2 Recommendation](https://www.w3.org/TR/webauthn-2/)
- [FIDO CTAP2.1 Specification](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)
- [FAPI 1.0 Advanced](https://openid.net/specs/openid-financial-api-part-2-1_0.html)

### ライブラリ
- [WebAuthn4j GitHub](https://github.com/webauthn4j/webauthn4j)
- [WebAuthn4j Documentation](https://webauthn4j.github.io/webauthn4j/en/)

### 関連ドキュメント
- [FIDO2 / WebAuthn 登録フロー詳細](protocol-04-fido2-webauthn-detail-registration.md) - 登録時のパラメータ・トラブルシューティング
- [FIDO2 / WebAuthn 詳細ガイド](protocol-04-fido2-webauthn-detail.md) - 認証時のパラメータ・トラブルシューティング
- [認証設定ガイド](../content_06_developer-guide/05-configuration/authn/webauthn.md) - テナント設定方法
- [AI開発者向けガイド](../content_10_ai_developer/ai-14-authentication-federation.md) - 実装クラス詳細

---
