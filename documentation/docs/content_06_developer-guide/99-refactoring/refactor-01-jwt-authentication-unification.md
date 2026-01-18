# JWT認証ロジックの共通化検討

## 概要

現在、JWT署名検証のロジックが複数箇所に分散しています。本ドキュメントでは、これらの共通化について検討します。

---

## 現状の実装

### 1. クライアント認証

OAuthクライアントがトークンエンドポイントにアクセスする際の認証。

| クラス | 認証方式 | 使用クラス |
|--------|---------|-----------|
| `ClientSecretJwtAuthenticator` | client_secret_jwt (HMAC) | `JoseHandler` |
| `PrivateKeyJwtAuthenticator` | private_key_jwt (RSA/EC) | `JoseHandler` |

**パス:** `org.idp.server.core.openid.oauth.clientauthenticator`

**特徴:**
- `ClientAuthenticator`プラグインインターフェースを実装
- `JoseHandler`で高レベルのJWT処理
- `ClientConfiguration`からJWKS/シークレットを取得

### 2. デバイス認証（JWT Bearer Grant）

デバイスがJWT Bearer Grantでトークンを取得する際の認証。

| クラス | 認証方式 | 使用クラス |
|--------|---------|-----------|
| `JwtBearerGrantService.verifyDeviceSignature()` | device_secret_jwt / private_key_jwt | `JsonWebSignatureVerifierFactory` |

**パス:** `org.idp.server.core.openid.token.service`

**特徴:**
- `OAuthTokenCreationService`の一部として実装
- `JsonWebSignatureVerifierFactory`を直接使用
- `DeviceCredential`からJWKS/シークレットを取得

### 3. デバイスAPIエンドポイント認証

デバイスがAPIエンドポイントにアクセスする際の認証。

| クラス | 認証方式 | 使用クラス |
|--------|---------|-----------|
| `DeviceAuthenticationVerifier` | device_secret_jwt / private_key_jwt | `JsonWebSignatureVerifierFactory` |

**パス:** `org.idp.server.core.openid.identity.device.authentication`

**特徴:**
- EntryServiceから呼び出される検証クラス
- `JsonWebSignatureVerifierFactory`を直接使用
- `DeviceCredential`からJWKS/シークレットを取得

---

## 類似点と相違点

### 類似点

1. **JWT署名検証**という共通の目的
2. **対称鍵（HMAC）と非対称鍵（RSA/EC）**の両方をサポート
3. **クレデンシャル（鍵情報）の取得 → 署名検証**という流れ

### 相違点

| 観点 | クライアント認証 | デバイス認証 |
|------|-----------------|-------------|
| 主体 | OAuthクライアント | 認証デバイス |
| クレデンシャル取得元 | `ClientConfiguration` | `DeviceCredential` |
| JWT処理レベル | 高レベル (`JoseHandler`) | 低レベル (`JsonWebSignatureVerifierFactory`) |
| プラグイン機構 | あり (`ClientAuthenticator`) | なし |
| クレーム検証 | `ClientAuthenticationJwtValidatable` | `JwtBearerGrantVerifier` |

---

## 共通化の選択肢

### Option A: DeviceAuthenticatorプラグイン化

クライアント認証と同じパターンでデバイス認証をプラグイン化する。

```
org.idp.server.core.openid.identity.device.authenticator/
├── DeviceAuthenticator.java          (プラグインインターフェース)
├── DeviceAuthenticators.java         (コレクション)
├── DeviceSecretJwtAuthenticator.java (HMAC実装)
├── DevicePrivateKeyJwtAuthenticator.java (RSA/EC実装)
└── DeviceAuthenticationContext.java  (コンテキスト)
```

**メリット:**
- クライアント認証と一貫したアーキテクチャ
- 新しい認証方式の追加が容易（プラグイン）
- テストしやすい

**デメリット:**
- 大きな変更が必要
- 既存の`JwtBearerGrantService`の修正が必要

### Option B: 共通署名検証ユーティリティ

低レベルの署名検証を共通ユーティリティに抽出する。

```
org.idp.server.platform.jose/
└── JwtSignatureVerificationService.java

// 使用例
JwtSignatureVerificationService verifier = new JwtSignatureVerificationService();
verifier.verifySymmetric(jws, secretValue);
verifier.verifyAsymmetric(jws, jwks);
```

**メリット:**
- 最小限の変更で実現可能
- 既存のコードへの影響が少ない

**デメリット:**
- アーキテクチャの一貫性は改善されない
- 単なるコード重複の排除に留まる

### Option C: JoseHandlerの拡張

`JoseHandler`をデバイス認証でも使えるように拡張する。

```java
// 現在のJoseHandler
joseHandler.handle(assertion, publicJwks, encryptionJwks, secret);

// 拡張案: クレデンシャル抽象化
joseHandler.handle(assertion, JwtCredential.symmetric(secret));
joseHandler.handle(assertion, JwtCredential.asymmetric(jwks));
```

**メリット:**
- 既存の高レベルAPIを活用
- 変更範囲が限定的

**デメリット:**
- `JoseHandler`の責務が増える
- プラットフォーム層への変更が必要

### Option D: 現状維持

リファクタリングを行わず、現状のまま運用する。

**メリット:**
- 変更リスクなし
- 開発コストなし

**デメリット:**
- コード重複が残る
- バグ修正時に複数箇所の修正が必要

---

## 推奨案

### 短期: Option B（共通ユーティリティ）

まず低レベルの署名検証を共通化し、重複を排除する。

```java
public class JwtSignatureVerifier {

  public void verifyWithSecret(JsonWebSignature jws, String secret) {
    JsonWebSignatureVerifierFactory factory =
        new JsonWebSignatureVerifierFactory(jws, "", secret);
    JsonWebSignatureVerifier verifier = factory.create().getLeft();
    verifier.verify(jws);
  }

  public void verifyWithJwks(JsonWebSignature jws, String jwks) {
    JsonWebSignatureVerifierFactory factory =
        new JsonWebSignatureVerifierFactory(jws, jwks, "");
    JsonWebSignatureVerifier verifier = factory.create().getLeft();
    verifier.verify(jws);
  }
}
```

### 長期: Option A（プラグイン化）

デバイス認証の要件が増えてきた場合、クライアント認証と同様のプラグインアーキテクチャを導入する。

---

## 影響範囲

### Option B を採用した場合

| ファイル | 変更内容 |
|---------|---------|
| `JwtSignatureVerifier.java` | 新規作成 |
| `JwtBearerGrantService.java` | 共通化クラスを使用 |
| `DeviceAuthenticationVerifier.java` | 共通化クラスを使用 |
| `ClientSecretJwtAuthenticator.java` | 変更なし（JoseHandler継続） |
| `PrivateKeyJwtAuthenticator.java` | 変更なし（JoseHandler継続） |

---

## 関連ファイル

```
クライアント認証:
libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/
├── ClientSecretJwtAuthenticator.java
├── PrivateKeyJwtAuthenticator.java
├── ClientAuthenticationJwtValidatable.java
└── plugin/ClientAuthenticator.java

デバイス認証:
libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/
└── JwtBearerGrantService.java

libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/device/authentication/
└── DeviceAuthenticationVerifier.java

共通基盤:
libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/
├── JoseHandler.java
├── JsonWebSignature.java
├── JsonWebSignatureVerifier.java
└── JsonWebSignatureVerifierFactory.java
```

---

## 次のアクション

- [ ] チームでOption B/A/C/Dを議論
- [ ] 採用するOptionを決定
- [ ] 実装計画を作成
