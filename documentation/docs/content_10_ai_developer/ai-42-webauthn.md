# idp-server-webauthn4j-adapter - WebAuthn4jライブラリ統合

## モジュール概要

**情報源**: `libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/`
**確認日**: 2025-11-08
**ライブラリ**: `com.webauthn4j:webauthn4j-core:0.28.5.RELEASE`

### 責務

WebAuthn/FIDO2実装（webauthn4jライブラリ統合）。

**仕様**: [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)

### 主要機能

- **Registration Challenge**: 認証器登録チャレンジ生成（Passkey作成開始）
- **Registration**: 認証器登録検証（Passkey作成完了）
- **Authentication Challenge**: 認証チャレンジ生成（認証開始）
- **Authentication**: 認証器検証（認証完了）
- **Attestation**: 認証器証明（デバイス信頼性）
- **User Verification**: ユーザー検証（PIN/生体認証）

## アーキテクチャ概要

### 4段階の認証フロー

WebAuthn4jは以下の4つのExecutorで構成され、AuthenticationExecutorインターフェースを実装：

1. **RegistrationChallengeExecutor**: 登録チャレンジ生成
2. **RegistrationExecutor**: 登録検証・Credential保存
3. **AuthenticationChallengeExecutor**: 認証チャレンジ生成
4. **AuthenticationExecutor**: 認証検証

**情報源**: `libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/`

## コアクラス構成

### 1. WebAuthn4jConfiguration - 設定管理

**情報源**: [WebAuthn4jConfiguration.java:25-110](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jConfiguration.java#L25)

```java
/**
 * WebAuthn設定（RP情報・検証要件）
 */
public class WebAuthn4jConfiguration {
  String rpId;                          // Relying Party ID (例: example.com)
  String rpName;                        // Relying Party名
  String origin;                        // オリジン（例: https://example.com）
  byte[] tokenBindingId;                // Token Binding ID（通常null）
  String attestationPreference;         // Attestation設定（none/indirect/direct）
  String authenticatorAttachment;       // 認証器タイプ（platform/cross-platform）

  boolean requireResidentKey;           // Resident Key必須か
  boolean userVerificationRequired;     // ユーザー検証（PIN/生体認証）必須か
  boolean userPresenceRequired;         // ユーザー存在確認必須か

  // webauthn4jのRegistrationParametersに変換
  RegistrationParameters toRegistrationParameters(WebAuthn4jChallenge challenge) {
    Origin origin = Origin.create(this.origin);
    ServerProperty serverProperty =
        new ServerProperty(origin, rpId, challenge, tokenBindingId);

    return new RegistrationParameters(
        serverProperty,
        null, // pubKeyCredParams
        userVerificationRequired,
        userPresenceRequired);
  }
}
```

**重要設定項目**:
- `rpId`: WebAuthnのスコープ（通常はドメイン名）
- `origin`: フロントエンドのURL（https必須）
- `userVerificationRequired`: PIN/生体認証の要否
- `userPresenceRequired`: タップ/タッチの要否

### 2. WebAuthn4jChallenge - チャレンジ生成

**情報源**: [WebAuthn4jChallenge.java:29-82](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jChallenge.java#L29)

```java
/**
 * WebAuthnチャレンジ（Challengeインターフェース実装）
 */
public class WebAuthn4jChallenge implements Challenge {
  byte[] value;

  // UUID v4ベースの16バイトチャレンジ生成
  public static WebAuthn4jChallenge generate() {
    UUID uuid = UUID.randomUUID();
    long hi = uuid.getMostSignificantBits();
    long lo = uuid.getLeastSignificantBits();
    byte[] value = ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
    return new WebAuthn4jChallenge(value);
  }

  // Base64URL文字列として取得
  public String challengeAsString() {
    return Base64.getUrlEncoder().encodeToString(value);
  }

  // FIDO2Challenge（アプリケーション層）に変換
  public Fido2Challenge toWebAuthnChallenge() {
    return new Fido2Challenge(challengeAsString());
  }
}
```

**チャレンジ設計**:
- UUIDv4ベース（128bit = 16バイト）
- Base64URLエンコード
- リプレイ攻撃防止のため、各操作で一意

### 3. WebAuthn4jCredential - Credential管理

**情報源**: [WebAuthn4jCredential.java:23-95](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jCredential.java#L23)

```java
/**
 * WebAuthn認証情報（Passkey）
 */
public class WebAuthn4jCredential {
  String id;                  // Credential ID（Base64URL）
  String userId;              // ユーザーID
  String rpId;                // Relying Party ID
  String publicKey;           // 公開鍵（Base64URL）
  String attestationObject;   // Attestationデータ（Base64URL）
  long signCount;             // 署名カウンタ（クローン検知）

  // バイト配列として取得（webauthn4j検証用）
  public byte[] idAsBytes() {
    return Base64.getUrlDecoder().decode(id);
  }

  public byte[] attestationObjectAsBytes() {
    return Base64.getUrlDecoder().decode(attestationObject);
  }

  // スネークケースMapに変換（JSON保存用）
  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("id", id);
    result.put("user_id", userId);
    result.put("rp_id", rpId);
    result.put("public_key", publicKey);
    result.put("attestation_object", attestationObject);
    result.put("sign_count", signCount);
    return result;
  }
}
```

**Credentialライフサイクル**:
1. Registration時に作成（`WebAuthn4jRegistrationManager`）
2. `WebAuthn4jCredentialRepository.register()`で保存
3. Authentication時に`get(id)`で取得
4. 検証成功後に`updateSignCount()`でカウンタ更新

## WebAuthn4jライブラリ統合パターン

### 1. 登録フロー - WebAuthn4jRegistrationManager

**情報源**: [WebAuthn4jRegistrationManager.java:27-104](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jRegistrationManager.java#L27)

```java
/**
 * webauthn4jを使った登録検証・Credential作成
 */
public class WebAuthn4jRegistrationManager {
  WebAuthnManager webAuthnManager;           // webauthn4jコアマネージャー
  WebAuthn4jConfiguration configuration;
  WebAuthn4jChallenge webAuthn4jChallenge;
  String request;                            // クライアントからのJSON
  String userId;

  public WebAuthn4jRegistrationManager(...) {
    // NonStrictモード（本番はStrictモード推奨）
    this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
  }

  public WebAuthn4jCredential verifyAndCreateCredential() {
    // 1. JSONをRegistrationDataにパース
    RegistrationData registrationData = parseRequest();

    // 2. 検証パラメータ構築
    RegistrationParameters registrationParameters =
        configuration.toRegistrationParameters(webAuthn4jChallenge);

    // 3. webauthn4jで検証
    RegistrationData verified =
        webAuthnManager.verify(registrationData, registrationParameters);

    // 4. Credential ID抽出
    byte[] credentialId = Objects.requireNonNull(
        verified.getAttestationObject()
            .getAuthenticatorData()
            .getAttestedCredentialData()
    ).getCredentialId();

    // 5. AttestedCredentialDataをシリアライズ
    ObjectConverter objectConverter = new ObjectConverter();
    AttestedCredentialDataConverter converter =
        new AttestedCredentialDataConverter(objectConverter);
    byte[] attestedCredentialData =
        converter.convert(verified.getAttestationObject()
            .getAuthenticatorData()
            .getAttestedCredentialData());

    // 6. WebAuthn4jCredential作成
    String id = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(credentialId);
    String attestationDataString = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(attestedCredentialData);

    return new WebAuthn4jCredential(
        id, userId, configuration.rpId(), "", attestationDataString, 0);
  }

  private RegistrationData parseRequest() {
    try {
      return webAuthnManager.parseRegistrationResponseJSON(request);
    } catch (Exception e) {
      throw new WebAuthn4jBadRequestException(
          "webauthn registration request is invalid", e);
    }
  }
}
```

**登録処理の重要ポイント**:
1. **webauthn4j検証**: `WebAuthnManager.verify()`でRFC準拠検証
2. **AttestedCredentialData保存**: 公開鍵・Credential IDを含む
3. **Base64URLエンコード**: すべてのバイナリデータをBase64URL化
4. **例外処理**: webauthn4jの例外を`WebAuthn4jBadRequestException`でラップ

### 2. 認証フロー - WebAuthn4jAuthenticationManager

**情報源**: [WebAuthn4jAuthenticationManager.java:29-102](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jAuthenticationManager.java#L29)

```java
/**
 * webauthn4jを使った認証検証
 */
public class WebAuthn4jAuthenticationManager {
  WebAuthnManager webAuthnManager;
  WebAuthn4jConfiguration configuration;
  WebAuthn4jChallenge challenge;
  String request;

  public WebAuthn4jAuthenticationManager(...) {
    this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
  }

  // UserHandle（userID）を抽出
  public String extractUserId() {
    AuthenticationData authenticationData = parseAuthenticationData();
    return new String(
        Objects.requireNonNull(authenticationData.getUserHandle()),
        StandardCharsets.UTF_8);
  }

  // 認証検証
  public void verify(WebAuthn4jCredential credential) {
    // 1. JSONをAuthenticationDataにパース
    AuthenticationData authenticationData = parseAuthenticationData();

    // 2. CredentialをCredentialRecordに変換
    WebAuthn4jCredentialConverter converter =
        new WebAuthn4jCredentialConverter(credential);
    CredentialRecordImpl credentialRecord = converter.convert();

    // 3. 検証パラメータ構築
    AuthenticationParameters authenticationParameters =
        toAuthenticationParameters(credentialRecord);

    // 4. webauthn4jで検証
    AuthenticationData verifiedData =
        webAuthnManager.verify(authenticationData, authenticationParameters);

    // 検証成功（例外が投げられなければOK）
  }

  private AuthenticationParameters toAuthenticationParameters(
      CredentialRecordImpl credentialRecord) {
    // Server properties
    Origin origin = Origin.create(configuration.origin());
    ServerProperty serverProperty =
        new ServerProperty(origin, configuration.rpId(), challenge, null);

    // expectations
    boolean userVerificationRequired = configuration.userVerificationRequired();
    boolean userPresenceRequired = configuration.userPresenceRequired();

    return new AuthenticationParameters(
        serverProperty,
        credentialRecord,
        null,  // allowCredentials
        userVerificationRequired,
        userPresenceRequired);
  }

  private AuthenticationData parseAuthenticationData() {
    try {
      return webAuthnManager.parseAuthenticationResponseJSON(request);
    } catch (Exception e) {
      throw new WebAuthn4jBadRequestException(
          "Failed to parse authentication response", e);
    }
  }
}
```

**認証処理の重要ポイント**:
1. **Credential変換**: 保存された`WebAuthn4jCredential`を`CredentialRecordImpl`に変換
2. **UserHandle抽出**: クライアントが送信したUserIDを抽出
3. **検証成功判定**: 例外が投げられなければ認証成功
4. **SignCount更新**: 検証後に別途更新（クローン検知）

### 3. WebAuthn4jCredentialConverter - Credential変換

**情報源**: [WebAuthn4jCredentialConverter.java:27-56](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jCredentialConverter.java#L27)

```java
/**
 * WebAuthn4jCredential → CredentialRecordImpl変換
 */
public class WebAuthn4jCredentialConverter {
  WebAuthn4jCredential credential;
  AttestedCredentialDataConverter attestedCredentialDataConverter;

  public CredentialRecordImpl convert() {
    Base64.Decoder urlDecoder = Base64.getUrlDecoder();

    // AttestedCredentialDataをデシリアライズ
    AttestedCredentialData deserializedAttestedCredentialData =
        attestedCredentialDataConverter.convert(
            urlDecoder.decode(credential.attestationObject()));

    // CredentialRecordImpl作成
    return new CredentialRecordImpl(
        new NoneAttestationStatement(),        // Attestation（none）
        null,                                  // isBackupEligible
        null,                                  // isBackedUp
        null,                                  // attestedCredentialData
        credential.signCount(),                // signCount
        deserializedAttestedCredentialData,    // attestedCredentialData
        new AuthenticationExtensionsAuthenticatorOutputs<>(),
        null,                                  // clientExtensions
        null,                                  // authenticatorExtensions
        null                                   // transports
    );
  }
}
```

**Credential変換の役割**:
- 保存されたBase64URL文字列をバイナリに変換
- webauthn4jの`CredentialRecordImpl`に再構築
- 認証検証時に使用

## AuthenticationExecutor実装（4つのフェーズ）

### 1. WebAuthn4jRegistrationChallengeExecutor - 登録チャレンジ生成

**情報源**: [WebAuthn4jRegistrationChallengeExecutor.java:33-78](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jRegistrationChallengeExecutor.java#L33)

```java
/**
 * 登録チャレンジ生成Executor
 * function: "webauthn4j_registration_challenge"
 */
public class WebAuthn4jRegistrationChallengeExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  @Override
  public String function() {
    return "webauthn4j_registration_challenge";
  }

  public Fido2ExecutorType type() {
    return new Fido2ExecutorType("webauthn4j");
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    // 1. チャレンジ生成（UUIDベース）
    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    Fido2Challenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();

    // 2. チャレンジを一時保存（Redis/DBに保存）
    transactionCommandRepository.register(
        tenant, identifier, type().value(), fido2Challenge);

    // 3. クライアントにチャレンジを返す
    Map<String, Object> contents = new HashMap<>();
    contents.put("challenge", webAuthn4jChallenge.challengeAsString());
    Map<String, Object> response = new HashMap<>();
    response.put("execution_webauthn4j", contents);

    return AuthenticationExecutionResult.success(response);
  }
}
```

**処理フロー**:
1. UUID v4ベースの16バイトチャレンジ生成
2. AuthenticationInteractionRepositoryに一時保存
3. Base64URL文字列としてクライアントに返却

### 2. WebAuthn4jRegistrationExecutor - 登録検証

**情報源**: [WebAuthn4jRegistrationExecutor.java:35-91](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jRegistrationExecutor.java#L35)

```java
/**
 * 登録検証Executor
 * function: "webauthn4j_registration"
 */
public class WebAuthn4jRegistrationExecutor implements AuthenticationExecutor {

  @Override
  public String function() {
    return "webauthn4j_registration";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    // 1. 保存されたチャレンジを取得
    Fido2Challenge fido2Challenge =
        transactionQueryRepository.get(
            tenant, identifier, type().value(), Fido2Challenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge =
        new WebAuthn4jChallenge(fido2Challenge.challenge());

    // 2. リクエストをJSON文字列に変換
    String requestString = jsonConverter.write(request.toMap());

    // 3. 設定を取得
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);

    // 4. userId生成（UUID v4）
    String userId = UUID.randomUUID().toString();

    // 5. WebAuthn4jRegistrationManagerで検証・Credential作成
    WebAuthn4jRegistrationManager manager =
        new WebAuthn4jRegistrationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString, userId);

    WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();

    // 6. Credentialを永続化
    credentialRepository.register(webAuthn4jCredential);

    // 7. 結果を返す
    Map<String, Object> response = new HashMap<>();
    response.put("execution_webauthn4j", webAuthn4jCredential.toMap());

    return AuthenticationExecutionResult.success(response);
  }
}
```

**処理フロー**:
1. 保存されたチャレンジを取得
2. クライアントからのリクエストをJSON化
3. WebAuthn4jRegistrationManagerで検証
4. Credentialを永続化（PostgreSQL/MySQL）
5. 成功結果を返却

### 3. WebAuthn4jAuthenticationChallengeExecutor - 認証チャレンジ生成

**情報源**: [WebAuthn4jAuthenticationChallengeExecutor.java:34-79](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jAuthenticationChallengeExecutor.java#L34)

```java
/**
 * 認証チャレンジ生成Executor
 * function: "webauthn4j_authentication_challenge"
 */
public class WebAuthn4jAuthenticationChallengeExecutor implements AuthenticationExecutor {

  @Override
  public String function() {
    return "webauthn4j_authentication_challenge";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    // 1. チャレンジ生成
    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    Fido2Challenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();

    // 2. チャレンジを一時保存
    transactionCommandRepository.register(
        tenant, identifier, type().value(), fido2Challenge);

    // 3. クライアントにチャレンジを返す
    Map<String, Object> contents = new HashMap<>();
    contents.put("challenge", webAuthn4jChallenge.challengeAsString());
    Map<String, Object> response = new HashMap<>();
    response.put("execution_webauthn4j", contents);

    return AuthenticationExecutionResult.success(response);
  }
}
```

**登録チャレンジとの違い**:
- 基本的なフローは同じ
- `function()`が`webauthn4j_authentication_challenge`
- 認証フロー用のチャレンジ

### 4. WebAuthn4jAuthenticationExecutor - 認証検証

**情報源**: [WebAuthn4jAuthenticationExecutor.java:34-92](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jAuthenticationExecutor.java#L34)

```java
/**
 * 認証検証Executor
 * function: "webauthn4j_authentication"
 */
public class WebAuthn4jAuthenticationExecutor implements AuthenticationExecutor {

  @Override
  public String function() {
    return "webauthn4j_authentication";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    // 1. 保存されたチャレンジを取得
    Fido2Challenge fido2Challenge =
        transactionQueryRepository.get(
            tenant, identifier, type().value(), Fido2Challenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge =
        new WebAuthn4jChallenge(fido2Challenge.challenge());

    // 2. リクエストをJSON文字列に変換
    String requestString = jsonConverter.write(request.toMap());

    // 3. 設定を取得
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);

    // 4. WebAuthn4jAuthenticationManagerで検証
    WebAuthn4jAuthenticationManager manager =
        new WebAuthn4jAuthenticationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString);

    // 5. Credential ID取得・検証
    String id = request.optValueAsString("id", "");
    WebAuthn4jCredential webAuthn4jCredential = credentialRepository.get(id);

    manager.verify(webAuthn4jCredential);

    // 6. 成功結果を返す
    Map<String, Object> contents = new HashMap<>();
    contents.put("id", id);
    contents.put("status", "ok");
    Map<String, Object> response = new HashMap<>();
    response.put("execution_webauthn4j", contents);

    return AuthenticationExecutionResult.success(response);
  }
}
```

**処理フロー**:
1. 保存されたチャレンジを取得
2. クライアントからのリクエストをJSON化
3. Credential IDで保存されたCredentialを取得
4. WebAuthn4jAuthenticationManagerで検証
5. 成功結果を返却

**重要**: `verify()`が例外を投げない = 認証成功

## Plugin登録とFactory

### Plugin登録

**情報源**: `libs/idp-server-webauthn4j-adapter/src/main/resources/META-INF/services/org.idp.server.platform.dependency.ApplicationComponentProvider`

```
org.idp.server.authenticators.webauthn4j.WebAuthn4jApplicationComponentProvider
```

### ExecutorFactory実装

4つのExecutorに対応する4つのFactoryが存在：

1. `WebAuthn4jRegistrationChallengeExecutorFactory`
2. `WebAuthn4jRegistrationExecutorFactory`
3. `WebAuthn4jAuthenticationChallengeExecutorFactory`
4. `WebAuthn4jAuthenticationExecutorFactory`

**Factoryパターン**:
```java
public class WebAuthn4jRegistrationChallengeExecutorFactory {
  public WebAuthn4jRegistrationChallengeExecutor create() {
    AuthenticationInteractionCommandRepository commandRepo = ...;
    AuthenticationInteractionQueryRepository queryRepo = ...;
    WebAuthn4jCredentialRepository credentialRepo = ...;

    return new WebAuthn4jRegistrationChallengeExecutor(
        commandRepo, queryRepo, credentialRepo);
  }
}
```

## WebAuthn4jライブラリの正しい使い方

### WebAuthnManagerの作成

**情報源**: [WebAuthn4j公式ドキュメント](https://webauthn4j.github.io/webauthn4j/ja/)

#### NonStrictモード（推奨）

```java
WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
```

**公式ドキュメント引用**:
> 大多数のサイトは厳密な構成証明ステートメントの検証を必要とせず、エンタープライズ用途以外では厳密な構成証明ステートメントの検証は非推奨

**現在の実装**: ✅ 正しく使用
```java
// WebAuthn4jRegistrationManager.java:40
this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
```

#### Strictモード（エンタープライズ用途のみ）

```java
List<AttestationStatementVerifier> verifiers = Arrays.asList(
    new PackedAttestationStatementVerifier(),
    new TPMAttestationStatementVerifier()
);
WebAuthnManager webAuthnManager = new WebAuthnManager(
    verifiers,
    new DefaultCertPathTrustworthinessVerifier(trustAnchorRepository)
);
```

### チャレンジ生成のベストプラクティス

**公式推奨**: SecureRandomで32バイトのランダムチャレンジ生成

```java
// 公式推奨パターン
byte[] challenge = new byte[32];
new SecureRandom().nextBytes(challenge);
```

**現在の実装**: UUIDベース（16バイト）

```java
// WebAuthn4jChallenge.java:42-48
public static WebAuthn4jChallenge generate() {
    UUID uuid = UUID.randomUUID();
    long hi = uuid.getMostSignificantBits();
    long lo = uuid.getLeastSignificantBits();
    byte[] value = ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
    return new WebAuthn4jChallenge(value);
}
```

**評価**:
- ✅ 一意性: UUID v4は暗号学的に安全
- ⚠️ サイズ: 16バイト（公式推奨32バイトより小さい）
- ✅ エンコード: Base64URLエンコード済み
- ✅ リプレイ攻撃対策: 各操作で一意

### CredentialRecordの永続化

**公式ガイド引用**:
> 登録時に永続化する際は、検索する際の利便性を考え、credentialIdをキーに永続化すると良い

**現在の実装**: ✅ 正しく実装

```java
// WebAuthn4jCredentialRepository.java:19-29
public interface WebAuthn4jCredentialRepository {
  void register(WebAuthn4jCredential credential);      // credentialで保存
  WebAuthn4jCredentials findAll(String userId);        // userIdで検索
  WebAuthn4jCredential get(String id);                 // credentialIdで取得
  void updateSignCount(String credentialId, long signCount);  // カウンタ更新
  void delete(String credentialId);                    // credentialIdで削除
}
```

**データベーススキーマ**: `libs/idp-server-database/postgresql/V0_9_1__webauthn4j.sql`
```sql
CREATE TABLE webauthn4j_credential (
    id VARCHAR(255) PRIMARY KEY,           -- credentialId（Base64URL）
    user_id VARCHAR(255) NOT NULL,
    rp_id VARCHAR(255) NOT NULL,
    attestation_object TEXT NOT NULL,      -- AttestedCredentialData（Base64URL）
    sign_count BIGINT DEFAULT 0,           -- カウンタ（クローン検知）
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_webauthn4j_user_id ON webauthn4j_credential(user_id);
```

### カウンタ更新（クローン検知）

**公式ドキュメント**: 認証成功後は必ずカウンタを更新してクローン検知を実施

**現在の実装状況**:
- ❌ **未実装**: `WebAuthn4jAuthenticationExecutor`で`updateSignCount()`を呼び出していない

**改善案**:
```java
// WebAuthn4jAuthenticationExecutor.java:82-92（改善版）
manager.verify(webAuthn4jCredential);

// カウンタ更新追加
AuthenticationData authenticationData = manager.parseAuthenticationData();
long newSignCount = authenticationData.getAuthenticatorData().getSignCount();
credentialRepository.updateSignCount(id, newSignCount);

// クローン検知
if (newSignCount <= webAuthn4jCredential.signCount()) {
    throw new WebAuthn4jBadRequestException("Possible credential clone detected");
}
```

### セキュリティ検証項目

**公式ドキュメントの重要な検証項目**:

| 検証項目 | 目的 | 現在の実装 |
|---------|------|-----------|
| **チャレンジ一致** | リプレイ攻撃防止 | ✅ webauthn4jが自動検証 |
| **Origin検証** | フィッシング対策 | ✅ `ServerProperty`で設定 |
| **RPId確認** | スコープ管理 | ✅ `configuration.rpId()`で設定 |
| **カウンタ監視** | クローン検知 | ❌ 未実装（要改善） |
| **UserVerification** | ユーザー本人確認 | ✅ `userVerificationRequired`で設定 |
| **UserPresence** | 物理的存在確認 | ✅ `userPresenceRequired`で設定 |

### セキュリティ上の注意事項

#### 1. Attestation検証の混在禁止

**公式ドキュメント引用**:
> Attestation検証を行わないNoneAttestationStatementVerifierなどを、他の検証を行うAttestationStatementVerifierと混ぜてしまうと、Attestation検証迂回に使用される抜け穴となる

**現在の実装**: ✅ 安全（NonStrictモード一貫使用）

#### 2. WebAuthn4Jのスコープ外の実装

**公式ドキュメント**:
> HTTPパラメータ処理、セッション管理、CredentialRecord永続化はアプリケーション責務

**現在の実装**: ✅ 適切に分離
- HTTPパラメータ: `AuthenticationExecutionRequest`でカプセル化
- セッション管理: `AuthenticationInteractionRepository`で管理
- 永続化: `WebAuthn4jCredentialRepository`でカプセル化

#### 3. チャレンジ保存の重要性

```java
// ✅ 正しい実装（チャレンジを一時保存）
transactionCommandRepository.register(
    tenant, identifier, type().value(), fido2Challenge);

// ❌ 間違った実装（チャレンジを保存しない）
// リプレイ攻撃の危険性
```

## WebAuthn仕様準拠

### Attestation Format

**情報源**: [Web Authentication Level 2 - Attestation](https://www.w3.org/TR/webauthn-2/#sctn-attestation)

```
- packed           : FIDO2標準形式（推奨）
- fido-u2f         : FIDO U2F互換
- android-key      : Android KeyStore
- android-safetynet: Android SafetyNet
- apple            : Apple Anonymous Attestation
- none             : Attestationなし（現在の実装）
```

**現在の実装**:
```java
// WebAuthn4jCredentialConverter.java:44
return new CredentialRecordImpl(
    new NoneAttestationStatement(),  // Attestationなし
    ...
);
```

### User Verification

**情報源**: [Web Authentication Level 2 - User Verification](https://www.w3.org/TR/webauthn-2/#user-verification)

```java
// User Verification Required
UserVerificationRequirement.REQUIRED  // PIN/生体認証必須（Passkey推奨）

// User Verification Preferred
UserVerificationRequirement.PREFERRED // 可能なら実施（デフォルト）

// User Verification Discouraged
UserVerificationRequirement.DISCOURAGED // 不要（多要素認証時）
```

**設定方法**:
```java
// WebAuthn4jConfiguration.java:56-57
boolean userVerificationRequired;     // PIN/生体認証必須か
boolean userPresenceRequired;         // タップ/タッチ必須か
```

## 実装改善推奨事項

### 1. カウンタ更新の実装（必須）

**優先度**: 🔴 高（セキュリティリスク）

```java
// WebAuthn4jAuthenticationExecutor.execute() に追加
manager.verify(webAuthn4jCredential);

// カウンタ更新処理を追加
long newSignCount = extractSignCount(manager);
if (newSignCount <= webAuthn4jCredential.signCount()) {
    throw new WebAuthn4jBadRequestException("Credential clone detected");
}
credentialRepository.updateSignCount(id, newSignCount);
```

### 2. チャレンジサイズの拡大（推奨）

**優先度**: 🟡 中（公式推奨に合わせる）

```java
// WebAuthn4jChallenge.generate() の改善
public static WebAuthn4jChallenge generate() {
    byte[] value = new byte[32];  // 16 → 32バイト
    new SecureRandom().nextBytes(value);
    return new WebAuthn4jChallenge(value);
}
```

### 3. Attestation検証の強化（オプション）

**優先度**: 🟢 低（エンタープライズ用途のみ）

エンタープライズ用途でデバイス検証が必要な場合：
- `WebAuthnManager.createNonStrictWebAuthnManager()` → Strictモードに変更
- TrustAnchorRepository実装
- AttestationStatementVerifier選定

## データソース層実装

### WebAuthn4jCredentialDataSource

**情報源**: [WebAuthn4jCredentialDataSource.java](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/datasource/credential/WebAuthn4jCredentialDataSource.java)

**DataSource-SqlExecutor パターン**:
```
WebAuthn4jCredentialDataSource
  ├─ PostgresqlExecutor (PostgreSQL用SQL)
  └─ MysqlExecutor (MySQL用SQL)
```

**SQL実装**:
```sql
-- PostgreSQL: JSONB型使用
INSERT INTO webauthn4j_credential (id, user_id, rp_id, credential_data)
VALUES (?, ?, ?, ?::jsonb)

-- MySQL: JSON型使用
INSERT INTO webauthn4j_credential (id, user_id, rp_id, credential_data)
VALUES (?, ?, ?, ?)
```

**JsonConverter使用**:
```java
JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
String json = jsonConverter.write(credential.toMap());
```

## シーケンス図

### 登録フロー（Registration）

```
Client                Executor              Manager              Repository
  |                      |                     |                     |
  |--- GET /challenge -->|                     |                     |
  |                      |-- generate() ------>|                     |
  |                      |<- challenge --------|                     |
  |                      |-- save challenge ----------------------->|
  |<-- challenge --------|                     |                     |
  |                      |                     |                     |
  |--- POST /register -->|                     |                     |
  |  (credential data)   |-- load challenge ----------------------->|
  |                      |<- challenge ---------------------------- |
  |                      |-- verify(data, challenge) ------------->|
  |                      |<- verified data ------------------------|
  |                      |-- create credential -------------------->|
  |                      |<- credential ----------------------------|
  |                      |-- register(credential) ----------------->|
  |<-- success ----------|                     |                     |
```

### 認証フロー（Authentication）

```
Client                Executor              Manager              Repository
  |                      |                     |                     |
  |--- GET /challenge -->|                     |                     |
  |                      |-- generate() ------>|                     |
  |                      |<- challenge --------|                     |
  |                      |-- save challenge ----------------------->|
  |<-- challenge --------|                     |                     |
  |                      |                     |                     |
  |--- POST /auth ------>|                     |                     |
  |  (assertion)         |-- load challenge ----------------------->|
  |                      |<- challenge ---------------------------- |
  |                      |-- get(credentialId) -------------------->|
  |                      |<- credential ----------------------------|
  |                      |-- verify(assertion, credential) -------->|
  |                      |<- verification result -------------------|
  |                      |-- updateSignCount(id, count) ----------->| ⚠️ 要実装
  |<-- success ----------|                     |                     |
```

## まとめ

### WebAuthn4jライブラリの使い方のポイント

1. **WebAuthnManager**: `createNonStrictWebAuthnManager()`を使用（エンタープライズ以外）
2. **チャレンジ**: UUIDベース（16バイト）で現在実装、公式推奨は32バイト
3. **検証**: `webAuthnManager.verify()`で自動的にRFC準拠検証
4. **Credential保存**: `credentialId`をキーとして永続化
5. **カウンタ更新**: 認証成功後に必ず更新（クローン検知） - ⚠️ **現在未実装**
6. **例外処理**: webauthn4jの例外を`WebAuthn4jBadRequestException`でラップ

### アーキテクチャパターン

- **4段階Executor**: Registration/Authentication × Challenge/Execution
- **Manager層**: webauthn4jライブラリをラップ
- **Repository層**: Credential永続化（PostgreSQL/MySQL対応）
- **DataSource-SqlExecutor**: DB別SQL実装

### セキュリティ評価

| 項目 | 状態 | 評価 |
|------|-----|-----|
| WebAuthnManager | NonStrictモード | ✅ 公式推奨通り |
| チャレンジ生成 | UUID v4（16バイト） | ⚠️ 公式推奨は32バイト |
| チャレンジ保存 | AuthenticationInteractionRepository | ✅ 正しく実装 |
| Origin検証 | ServerPropertyで設定 | ✅ 正しく実装 |
| カウンタ更新 | 未実装 | ❌ 要改善（セキュリティリスク） |
| Credential永続化 | credentialIdをキー | ✅ 公式推奨通り |

## 関連ドキュメント

- [認証・連携層統合ドキュメント](./ai-40-authentication-federation.md) - WebAuthnを含む全認証モジュール
- [idp-server-authentication-interactors](./ai-40-authentication-federation.md) - 認証インタラクター
- [idp-server-core](./ai-11-core.md) - OAuth/OIDCコアエンジン
- [idp-server-platform](./ai-12-platform.md) - JsonConverter, PluginLoader

## 参考資料

**公式仕様**:
- [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)
- [WebAuthn4j公式ドキュメント（日本語）](https://webauthn4j.github.io/webauthn4j/ja/)
- [FIDO Alliance Specifications](https://fidoalliance.org/specifications/)

**情報源**:
- `libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/`
- `libs/idp-server-webauthn4j-adapter/build.gradle` - `com.webauthn4j:webauthn4j-core:0.28.5.RELEASE`
- `libs/idp-server-database/postgresql/V0_9_1__webauthn4j.sql`

**確認方法**:
```bash
# WebAuthn4j関連ファイル確認
find libs/idp-server-webauthn4j-adapter -name "*.java" -type f

# ライブラリバージョン確認
grep "webauthn4j" libs/idp-server-webauthn4j-adapter/build.gradle

# データベーススキーマ確認
grep "webauthn4j_credential" libs/idp-server-database/postgresql/*.sql
```

**最終更新**: 2025-11-08
