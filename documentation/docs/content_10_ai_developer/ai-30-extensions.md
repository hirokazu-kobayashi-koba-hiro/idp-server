# 拡張機能層 - OAuth/OIDC拡張仕様

## 概要

OAuth 2.0/OpenID Connectの拡張仕様を実装するモジュール群。Pluginインターフェースを通じてCore層に機能を追加。

**5つの拡張モジュール**:
1. **idp-server-core-extension-ciba** - Client Initiated Backchannel Authentication
2. **idp-server-core-extension-fapi** - Financial-grade API
3. **idp-server-core-extension-ida** - Identity Assurance
4. **idp-server-core-extension-pkce** - Proof Key for Code Exchange
5. **idp-server-core-extension-verifiable-credentials** - Verifiable Credentials

---

## idp-server-core-extension-ciba

**情報源**: `libs/idp-server-core-extension-ciba/`

### 責務

CIBA (Client Initiated Backchannel Authentication) 仕様実装。

**RFC**: [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)

### 主要機能

- **Backchannel Authentication Endpoint**: `/bc-authorize`
- **非同期認証**: プッシュ通知によるユーザー認証
- **Polling/Push/Ping モード**: 3つの通知モードをサポート

### 実装パターン

```java
// Plugin登録（META-INF/services）
org.idp.server.core.openid.oauth.request.AuthorizationRequestObjectFactory
→ org.idp.server.core.extension.ciba.CibaAuthorizationRequestObjectFactory
```

---

## idp-server-core-extension-fapi

**情報源**: `libs/idp-server-core-extension-fapi/`

### 責務

FAPI (Financial-grade API) セキュリティプロファイル実装。

**仕様**: [FAPI 1.0 Advanced](https://openid.net/specs/openid-financial-api-part-2-1_0.html)

### 主要機能

- **PAR (Pushed Authorization Request)**: 認可リクエストをBackchannelで送信
- **JAR (JWT Authorization Request)**: 認可リクエストのJWT署名
- **JARM (JWT Authorization Response Mode)**: 認可レスポンスのJWT署名
- **MTLS**: 相互TLS認証

### セキュリティ強化

**情報源**: `libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/`

#### FAPI Verifier実装

```java
// ✅ FAPI Baseline検証
public class FapiBaselineVerifier implements AuthorizationRequestVerifier {
  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_BASELINE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    // FAPI 1.0 Baseline要件検証
  }
}

// ✅ FAPI Advance検証
public class FapiAdvanceVerifier implements AuthorizationRequestVerifier {
  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_ADVANCE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    // FAPI 1.0 Advanced要件検証
    // - JAR（JWT Authorization Request）検証
    // - JARM（JWT Authorization Response Mode）検証
    // - PAR（Pushed Authorization Request）検証
  }
}
```

**情報源**:
- [FapiBaselineVerifier.java:30](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiBaselineVerifier.java#L30)
- [FapiAdvanceVerifier.java:34](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiAdvanceVerifier.java#L34)

#### MTLS Client Authenticator

```java
// ✅ TLS Client Auth (MTLS)
public class TlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.tls_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    // X.509証明書検証
    X509Certification x509Certification = parseOrThrowExceptionIfNoneMatch(context);
    // クライアント認証
    return new ClientCredentials(...);
  }
}

// ✅ Self-Signed TLS Client Auth
public class SelfSignedTlsClientAuthAuthenticator implements ClientAuthenticator {
  // 自己署名証明書によるMTLS認証
}
```

**情報源**:
- [TlsClientAuthAuthenticator.java:35](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/TlsClientAuthAuthenticator.java#L35)
- [SelfSignedTlsClientAuthAuthenticator.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/SelfSignedTlsClientAuthAuthenticator.java)

---

## idp-server-core-extension-ida

**情報源**: `libs/idp-server-core-extension-ida/`

### 責務

IDA (Identity Assurance) 身元保証実装。

**仕様**: [OpenID Connect for Identity Assurance 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html)

### 主要機能

- **verified_claims**: 検証済み身元情報
- **trust_framework**: 信頼フレームワーク（eKYC等）
- **evidence**: 身元確認エビデンス
- **verification**: 検証方法・検証者情報

### Claimsパターン

```json
{
  "verified_claims": {
    "verification": {
      "trust_framework": "jp_aml",
      "time": "2023-04-01T10:00:00Z",
      "verification_process": "f24c6f-6d3f-4ec5-973e-b0d8506f3bc7"
    },
    "claims": {
      "given_name": "太郎",
      "family_name": "山田",
      "birthdate": "1985-01-01",
      "address": {
        "country": "JP",
        "postal_code": "100-0001",
        "region": "東京都",
        "locality": "千代田区"
      }
    }
  }
}
```

---

## idp-server-core-extension-pkce

**情報源**: `libs/idp-server-core-extension-pkce/`

### 責務

PKCE (Proof Key for Code Exchange) 実装。

**RFC**: [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636.html)

### 主要機能

- **code_challenge**: SHA-256ハッシュ生成
- **code_verifier**: ランダム文字列検証
- **code_challenge_method**: `S256` / `plain`

### フロー

```
1. Authorization Request:
   code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
   code_challenge_method=S256

2. Token Request:
   code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

3. Verification:
   BASE64URL(SHA256(code_verifier)) == code_challenge
```

---

## idp-server-core-extension-verifiable-credentials

**情報源**: `libs/idp-server-core-extension-verifiable-credentials/`

### 責務

Verifiable Credentials (検証可能な資格情報) 実装。

**仕様**: [OpenID for Verifiable Credentials](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)

### 主要機能

- **Credential Issuance**: VC発行
- **Credential Format**: `jwt_vc_json`, `ldp_vc`
- **Batch Credential**: 複数VC一括発行
- **Deferred Credential**: 遅延発行

### Credential構造

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
      "degree": {},
      "gpa": {"display": [{"name": "GPA"}]}
    }
  }
}
```

---

## Plugin登録パターン

全拡張モジュールは`META-INF/services`でPlugin登録。

### 登録例

```
# ファイル: META-INF/services/org.idp.server.core.openid.oauth.request.AuthorizationRequestObjectFactory
org.idp.server.core.extension.ciba.CibaAuthorizationRequestObjectFactory
org.idp.server.core.extension.fapi.ParAuthorizationRequestObjectFactory

# ファイル: META-INF/services/org.idp.server.core.openid.oauth.plugin.AuthorizationRequestExtensionVerifier
org.idp.server.core.extension.pkce.PkceVerifier
org.idp.server.core.extension.fapi.JarVerifier
```

### PluginLoader - 静的メソッドAPI

**情報源**: [PluginLoader.java:25-91](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/PluginLoader.java#L25-L91)

```java
// ✅ 正しいAPI: 静的メソッド使用
// 内部モジュールからロード
List<AuthorizationRequestExtensionVerifier> internalVerifiers =
    PluginLoader.loadFromInternalModule(AuthorizationRequestExtensionVerifier.class);

// 外部JARからロード（plugins/ディレクトリ）
List<AuthorizationRequestExtensionVerifier> externalVerifiers =
    PluginLoader.loadFromExternalModule(AuthorizationRequestExtensionVerifier.class);

// 両方をマージして使用
List<AuthorizationRequestExtensionVerifier> allVerifiers = new ArrayList<>();
allVerifiers.addAll(internalVerifiers);
allVerifiers.addAll(externalVerifiers);

// 全Verifierを実行
for (AuthorizationRequestExtensionVerifier verifier : allVerifiers) {
  verifier.verify(authorizationRequest, clientConfiguration);
}
```

**重要**: PluginLoaderは**インスタンス化不可**。全て静的メソッドで提供。

---

## まとめ

### 拡張機能層を理解するための5つのポイント

1. **Plugin パターン**: `META-INF/services`でCore層に機能追加
2. **RFC準拠**: 各拡張仕様の厳密準拠
3. **セキュリティ強化**: FAPI/PKCEによる攻撃対策
4. **身元保証**: IDAによる信頼性の高いID連携
5. **分離設計**: Coreに影響を与えない独立モジュール

### 次のステップ

- [認証・連携層（Authentication, Federation, WebAuthn）](./ai-40-authentication-federation.md)
- [通知・イベント層（Notification, Security Event）](./ai-50-notification-security-event.md)

---

## ドキュメント修正履歴

### 2025-10-12: 実装検証に基づく修正

#### 修正1: FAPI セキュリティ強化セクションの全面改訂 (59-128行目)

**問題**: 存在しないクラス名で想像実装を記載

**修正前**:
```java
// ❌ 存在しないクラス
AuthorizationRequestExtensionVerifier parVerifier = new ParRequestVerifier();  // ❌ 存在しない
AuthorizationRequestExtensionVerifier jarVerifier = new JarRequestVerifier();  // ❌ 存在しない
ClientAuthenticator mtlsAuthenticator = new MtlsClientAuthenticator();         // ❌ 存在しない
```

**修正後**:
```java
// ✅ 実際のクラス
public class FapiBaselineVerifier implements AuthorizationRequestVerifier {
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_BASELINE;
  }
}

public class FapiAdvanceVerifier implements AuthorizationRequestVerifier {
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_ADVANCE;
  }
}

public class TlsClientAuthAuthenticator implements ClientAuthenticator {
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.tls_client_auth;
  }
}
```

**追加内容**:
- FAPI Baseline vs Advanced の明確な区別
- `AuthorizationProfile` による検証プロファイル管理
- MTLS実装クラス2種（TlsClientAuth, SelfSignedTlsClientAuth）
- 実装ファイルへの正確なリンク

**検証**:
- [FapiBaselineVerifier.java:30](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiBaselineVerifier.java#L30)
- [FapiAdvanceVerifier.java:34](../..//..libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiAdvanceVerifier.java#L34)
- [TlsClientAuthAuthenticator.java:35](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/TlsClientAuthAuthenticator.java#L35)

### 検証済み項目

#### ✅ PKCE Verifier
- [PkceVerifier.java:23](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/PkceVerifier.java#L23)
- `AuthorizationRequestExtensionVerifier` 実装を確認

#### ✅ Plugin登録パターン
- PluginLoader静的メソッドAPI（loadFromInternalModule, loadFromExternalModule）が正確

#### ✅ 5つの拡張モジュール
- CIBA, FAPI, IDA, PKCE, Verifiable Credentials の全てが実在確認済み

### 修正の原則

**CLAUDE.md「想像ドキュメント作成防止」に基づく修正**:
1. **実装ファースト**: 実際のVerifier/Authenticatorクラスを確認
2. **クラス名の正確性**: 存在しないクラス名を排除
3. **情報源記録**: 全てのクラスにファイルパス・行番号を明記
4. **プロファイルパターン**: `AuthorizationProfile` による検証管理を説明

---

**情報源**:
- `libs/idp-server-core-extension-*/`配下の実装コード
- [FapiBaselineVerifier.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiBaselineVerifier.java)
- [FapiAdvanceVerifier.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiAdvanceVerifier.java)
- [TlsClientAuthAuthenticator.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/TlsClientAuthAuthenticator.java)
- [PkceVerifier.java](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/PkceVerifier.java)
- [intro-01-tech-overview.md](../content_01_intro/intro-01-tech-overview.md)
- RFC 7636 (PKCE), OpenID Connect CIBA, FAPI, IDA仕様

**最終更新**: 2025-10-12
**レビュー実施**: 2025-10-12 - AI開発者向けドキュメント品質改善プロジェクト
