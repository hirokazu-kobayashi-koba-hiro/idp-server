# FIDO2関連ファイル一覧

## WebAuthn4jアダプター（コア実装）

```
libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/
├── WebAuthn4jRegistrationManager.java      # 登録処理・アテステーション検証
├── WebAuthn4jAuthenticationManager.java    # 認証処理・署名検証
├── WebAuthn4jConfiguration.java            # 設定パース
├── WebAuthn4jManagerFactory.java           # Manager生成ファクトリ
├── WebAuthn4jRegistrationExecutor.java     # 登録実行
├── WebAuthn4jAuthenticationExecutor.java   # 認証実行
├── WebAuthn4jRegistrationChallengeExecutor.java
├── WebAuthn4jAuthenticationChallengeExecutor.java
└── WebAuthn4jBadRequestException.java
```

## FIDO2インタラクター

```
libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fido2/
├── Fido2RegistrationInteractor.java
├── Fido2RegistrationInteractorFactory.java
├── Fido2AuthenticationInteractor.java
├── Fido2AuthenticationInteractorFactory.java
├── Fido2AuthenticationChallengeInteractor.java
├── Fido2DeregistrationInteractor.java
├── Fido2DeregistrationInteractorFactory.java
├── Fido2Configuration.java
├── Fido2MetadataConfig.java
├── Fido2VerificationResult.java
├── Fido2ExecutorType.java
└── Fido2CredentialNotFoundException.java
```

## デバイスシークレット発行

```
libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/device/
├── DeviceSecretIssuer.java           # シークレット発行ロジック
└── AuthenticationDevice.java         # 認証デバイスエンティティ

libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/policy/
└── AuthenticationDeviceRule.java     # テナントポリシー設定
```

## How-toドキュメント

```
documentation/docs/content_05_how-to/phase-3-advanced/fido2/
├── 01-registration.md          # パスキー登録
├── 02-authentication.md        # パスキー認証
├── 03-management.md            # パスキー管理
└── 04-attestation-verification.md  # アテステーション検証
```

## 学習ドキュメント

```
documentation/docs/content_11_learning/05-fido-webauthn/
├── fido2-webauthn-passwordless.md
├── fido2-passkey-discoverable-credential.md
├── fido2-architecture-rp-browser-authenticator.md
├── fido2-registration-flow-interface.md
├── fido2-authentication-flow-interface.md
├── fido2-aaguid-authenticator-identification.md
├── fido2-attestation-types-and-verification.md
├── fido2-attestation-production-considerations.md
├── fido2-metadata-service.md
├── fido2-security-considerations.md
├── fido2-cross-device-authentication.md
├── fido2-identifiers-idp-integration.md
├── fido2-webauthn-level-specification-evolution.md
├── fido2-performance-analysis.md
└── fido2-faq-troubleshooting.md
```

## 開発者ガイド

```
documentation/docs/content_06_developer-guide/
├── 04-implementation-guides/authentication/webauthn4j-adapter.md
├── 05-configuration/authn-fido2.md
└── 05-configuration/authn-webauthn.md
```

## E2Eテスト

```
e2e/src/tests/usecase/mfa/
├── mfa-05-fido2.test.js                        # 基本フロー
└── mfa-06-fido2-attestation-verification.test.js  # アテステーション検証

e2e/src/tests/usecase/device-credential/
└── device-credential-04-device-secret-issuance.test.js  # デバイスシークレット自動発行

e2e/src/lib/fido/
└── fido2.js                                    # テスト用ヘルパー
```

## サンプルアプリ

```
sample-web/src/components/
├── PasskeyRegistration.tsx     # 登録UI
├── PasskeyAuthentication.tsx   # 認証UI
└── UserInfo.tsx                # デバイス一覧

sample-web/src/pages/
└── api/
    └── userinfo.ts             # Userinfo API

app-view/pages/
├── signup/fido2.tsx            # 登録ページ
├── signin/fido2.tsx            # 認証ページ
└── add-passkey.tsx             # パスキー追加
```

## プロトコルドキュメント

```
documentation/docs/content_04_protocols/
├── protocol-04-fido2-webauthn.md
└── protocol-04-fido2-webauthn-detail-registration.md
```

## コンセプトドキュメント

```
documentation/docs/content_03_concepts/
├── basic-16-fido-webauthn-passwordless.md
├── basic-17-fido2-passkey-discoverable-credential.md
├── basic-18-fido2-architecture-rp-browser-authenticator.md
├── basic-19-fido2-registration-flow-interface.md
├── basic-20-fido2-authentication-flow-interface.md
└── basic-21-fido2-webauthn-level-specification-evolution.md
```
