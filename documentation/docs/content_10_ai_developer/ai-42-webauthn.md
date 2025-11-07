# idp-server-webauthn4j-adapter

## モジュール概要

**情報源**: `libs/idp-server-webauthn4j-adapter/`
**確認日**: 2025-10-12

### 責務

WebAuthn/FIDO2実装（webauthn4jライブラリ統合）。

**仕様**: [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)

### 主要機能

- **Registration**: 認証器登録（Passkey作成）
- **Authentication**: 認証器検証
- **Attestation**: 認証器証明（デバイス信頼性）
- **User Verification**: ユーザー検証（PIN/生体認証）

## WebAuthnExecutor インターフェース

**情報源**: [WebAuthnExecutor.java:23](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fido2/WebAuthnExecutor.java#L23)

**重要**: `authentication-interactors` モジュールで定義（Core層ではない）

```java
/**
 * WebAuthn実行エンジン（Plugin）
 * 確認方法: 実ファイルの23-51行目
 */
public interface WebAuthnExecutor {

  // ✅ Executorタイプ
  WebAuthnExecutorType type();

  // ✅ 登録チャレンジ生成
  WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  // ✅ 登録検証
  WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  // ✅ 認証チャレンジ生成
  WebAuthnChallenge challengeAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  // ✅ 認証検証
  WebAuthnVerificationResult verifyAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);
}
```

## WebAuthn4jExecutor - webauthn4j実装

**情報源**: [WebAuthn4jExecutor.java:28](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jExecutor.java#L28)

```java
/**
 * webauthn4jライブラリを使用したWebAuthn実装
 * 確認方法: 実ファイルの28-80行目
 */
public class WebAuthn4jExecutor implements WebAuthnExecutor {

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  @Override
  public WebAuthnExecutorType type() {
    return new WebAuthnExecutorType("webauthn4j");
  }

  @Override
  public WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    // 1. チャレンジ生成
    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    WebAuthnChallenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();

    // 2. チャレンジを一時保存（Redis/DB）
    transactionCommandRepository.register(
        tenant,
        authenticationTransactionIdentifier,
        type().value(),
        fido2Challenge);

    return fido2Challenge;
  }

  @Override
  public WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    // 1. 保存されたチャレンジ取得
    WebAuthnChallenge fido2Challenge =
        transactionQueryRepository.get(
            tenant,
            authenticationTransactionIdentifier,
            type().value(),
            WebAuthnChallenge.class);

    // 2. webauthn4jで検証
    WebAuthn4jRegistrationManager registrationManager =
        new WebAuthn4jRegistrationManager(configuration);

    return registrationManager.verify(userId, request, fido2Challenge);
  }
}
```

**重要ポイント**:
- ✅ チャレンジは一時保存（AuthenticationInteractionRepository）
- ✅ Tenant第一引数パターン
- ✅ webauthn4jライブラリをラップ
- ✅ 登録と認証で別々のメソッド（`challengeRegistration` vs `challengeAuthentication`）

## Plugin登録

```
# META-INF/services/org.idp.server.authentication.interactors.fido2.Fido2ExecutorFactory
org.idp.server.authenticators.webauthn4j.WebAuthn4jExecutorFactory
```

## Attestation Format

```
- packed     : FIDO2標準形式
- fido-u2f   : FIDO U2F互換
- android-key: Android KeyStore
- android-safetynet: Android SafetyNet
- apple      : Apple Anonymous Attestation
- none       : Attestationなし
```

## User Verification

```java
// User Verification Required
UserVerificationRequirement.REQUIRED  // PIN/生体認証必須

// User Verification Preferred
UserVerificationRequirement.PREFERRED // 可能なら実施

// User Verification Discouraged
UserVerificationRequirement.DISCOURAGED // 不要
```

## 関連ドキュメント

- [認証・連携層統合ドキュメント](./ai-40-authentication-federation.md) - WebAuthnを含む全認証モジュール
- [idp-server-authentication-interactors](./authentication-federation.md#idp-server-authentication-interactors) - 認証インタラクター
- [idp-server-core](./ai-11-core.md) - OAuth/OIDCコアエンジン

---

**情報源**:
- `libs/idp-server-webauthn4j-adapter/`配下の実装コード
- `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/webauthn/`
- [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)

**最終更新**: 2025-10-12
