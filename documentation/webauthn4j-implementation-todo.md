# WebAuthn4j実装 改善TODOリスト

**作成日**: 2025-11-08
**対象モジュール**: `libs/idp-server-webauthn4j-adapter`
**目的**: WebAuthn4jライブラリの正しい使用と複数Origin対応

---

## 🔴 優先度: 高（セキュリティ・機能不全）

### 1. カウンタ更新の実装（クローン検知）

**ファイル**: `WebAuthn4jAuthenticationExecutor.java:82`

**問題**:
- 認証成功後に `signCount` を更新していない
- Credential クローン検知ができない（セキュリティリスク）

**現在のコード**:
```java
// WebAuthn4jAuthenticationExecutor.java:82
manager.verify(webAuthn4jCredential);

Map<String, Object> contents = new HashMap<>();
contents.put("id", id);
contents.put("status", "ok");
```

**修正案**:
```java
// 1. 認証データからsignCountを取得
AuthenticationData authenticationData = manager.getAuthenticationData();
long newSignCount = authenticationData.getAuthenticatorData().getSignCount();

// 2. クローン検知
if (newSignCount > 0 && newSignCount <= webAuthn4jCredential.signCount()) {
    throw new WebAuthn4jBadRequestException(
        "Possible credential clone detected. " +
        "Current: " + webAuthn4jCredential.signCount() +
        ", New: " + newSignCount
    );
}

// 3. 検証
manager.verify(webAuthn4jCredential);

// 4. カウンタ更新
credentialRepository.updateSignCount(id, newSignCount);
```

**追加必要事項**:
- `WebAuthn4jAuthenticationManager` に `getAuthenticationData()` メソッド追加
- テストケース追加

**影響範囲**: セキュリティ
**工数**: 2時間

---

### 2. 非推奨APIからBuilderパターンへの移行

**ファイル**:
- `WebAuthn4jConfiguration.java:66-67`
- `WebAuthn4jAuthenticationManager.java:78-79`

**問題**:
- `new ServerProperty(origin, rpId, challenge, tokenBindingId)` は非推奨
- WebAuthn4j公式で推奨される `ServerProperty.builder()` を使用していない

**現在のコード**:
```java
// WebAuthn4jConfiguration.java:66-67
Origin origin = Origin.create(this.origin);
ServerProperty serverProperty =
    new ServerProperty(origin, rpId, webAuthn4jChallenge, tokenBindingId);
```

**修正案**:
```java
Origin origin = Origin.create(this.origin);
ServerProperty serverProperty = ServerProperty.builder()
    .origin(origin)
    .rpId(rpId)
    .challenge(webAuthn4jChallenge)
    .build();
```

**影響範囲**:
- `WebAuthn4jConfiguration.toRegistrationParameters()`
- `WebAuthn4jAuthenticationManager.toAuthenticationParameters()`

**工数**: 1時間

---

### 3. デッドコードの削除

**ファイル**: `WebAuthn4jConfiguration.java:69-75`

**問題**:
- `serverProperty2` が定義されているが使用されていない
- コンパイル時に警告が出る可能性

**現在のコード**:
```java
// WebAuthn4jConfiguration.java:69-75
ServerProperty serverProperty2 =
    ServerProperty.builder()
        .origin(origin)
        .rpId(rpId)
        .origins(new HashSet<>())  // 空のSet
        .challenge(null)           // null
        .build();
// ← 使われていない
```

**修正案**: 削除

**工数**: 5分

---

## 🟡 優先度: 中（機能拡張）

### 4. 複数Origin対応

**ファイル**: `WebAuthn4jConfiguration.java`

**問題**:
- 現在は単一Origin (`String origin`) のみサポート
- サブドメイン・開発環境・モバイルアプリに対応できない

**現在のフィールド**:
```java
String origin;  // 単一Originのみ
```

**修正案**:
```java
// 複数Origin対応
List<String> allowedOrigins;  // Web + Android + iOS統合

// Builderパターンで複数Origin設定
RegistrationParameters toRegistrationParameters(WebAuthn4jChallenge challenge) {
    Set<Origin> origins = allowedOrigins.stream()
        .map(Origin::create)
        .collect(Collectors.toSet());

    ServerProperty serverProperty = ServerProperty.builder()
        .origins(origins)  // ← 複数Origin
        .rpId(rpId)
        .challenge(challenge)
        .build();

    return new RegistrationParameters(
        serverProperty,
        null,
        userVerificationRequired,
        userPresenceRequired
    );
}
```

**設定例**:
```json
{
  "rp_id": "example.com",
  "allowed_origins": [
    "https://example.com",
    "https://www.example.com",
    "https://auth.example.com",
    "http://localhost:3000",
    "android:apk-key-hash:Sm3afRQVJi8fLPxK3IT7gPSrVJn9c0uKbFGqL8QW3vU",
    "ios:bundle-id:com.example.myapp"
  ]
}
```

**影響範囲**:
- `WebAuthn4jConfiguration` クラス全体
- 設定ファイルのJSON構造
- マイグレーション対応（既存の `origin` → `allowed_origins`）

**工数**: 4時間

---

### 5. チャレンジサイズの拡大

**ファイル**: `WebAuthn4jChallenge.java:42-48`

**問題**:
- 現在16バイト（UUID v4）
- WebAuthn4j公式推奨は32バイト（SecureRandom）

**現在のコード**:
```java
public static WebAuthn4jChallenge generate() {
    UUID uuid = UUID.randomUUID();
    long hi = uuid.getMostSignificantBits();
    long lo = uuid.getLeastSignificantBits();
    byte[] value = ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
    return new WebAuthn4jChallenge(value);
}
```

**修正案**:
```java
public static WebAuthn4jChallenge generate() {
    byte[] value = new byte[32];  // 32バイトに拡大
    new SecureRandom().nextBytes(value);
    return new WebAuthn4jChallenge(value);
}
```

**評価**:
- ✅ 既存実装（16バイト）でもセキュリティ上は問題なし
- ⚠️ 公式推奨に合わせることで将来の互換性向上

**影響範囲**: チャレンジ生成のみ
**工数**: 30分

---

### 6. FIXMEコメントの解決

**ファイル**: `WebAuthn4jConfiguration.java:61`

**問題**:
- `// FIXME` コメントが残っている
- 実装意図が不明

**現在のコード**:
```java
// FIXME
RegistrationParameters toRegistrationParameters(WebAuthn4jChallenge webAuthn4jChallenge) {
    ...
}
```

**対応**:
- FIXMEの理由を特定
- 必要な修正を実施
- コメント削除またはTODOコメントに変更

**工数**: 1時間（調査含む）

---

## 🟢 優先度: 低（将来拡張）

### 7. TopOrigin対応（iframe統合）

**ファイル**: `WebAuthn4jConfiguration.java`

**問題**:
- iframe内でのWebAuthn使用時に必要な `topOrigin` 未対応

**修正案**:
```java
List<String> allowedTopOrigins;  // TopOrigin対応

ServerProperty serverProperty = ServerProperty.builder()
    .origins(origins)
    .rpId(rpId)
    .challenge(challenge)
    .topOrigins(allowedTopOrigins)  // ← TopOrigin追加
    .build();
```

**必要性**: マイクロフロントエンド構成の場合のみ
**工数**: 2時間

---

### 8. Attestation検証の強化

**ファイル**:
- `WebAuthn4jRegistrationManager.java:40`
- `WebAuthn4jAuthenticationManager.java:36`

**問題**:
- 現在は `WebAuthnManager.createNonStrictWebAuthnManager()` のみ
- エンタープライズ用途でのAttestation検証が不可

**修正案**:
```java
// 設定に応じて切り替え
WebAuthnManager webAuthnManager;

if (configuration.strictAttestationMode()) {
    List<AttestationStatementVerifier> verifiers = Arrays.asList(
        new PackedAttestationStatementVerifier(),
        new TPMAttestationStatementVerifier()
    );
    webAuthnManager = new WebAuthnManager(
        verifiers,
        new DefaultCertPathTrustworthinessVerifier(trustAnchorRepository)
    );
} else {
    webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
}
```

**必要性**: エンタープライズ用途のみ
**工数**: 8時間（TrustAnchorRepository実装含む）

---

### 9. CredentialConverter の TODO解決

**ファイル**: `WebAuthn4jCredentialConverter.java:43`

**問題**:
- `// TODO` コメントが残っている
- CredentialRecordImplのパラメータが一部null

**現在のコード**:
```java
// TODO
return new CredentialRecordImpl(
    new NoneAttestationStatement(),
    null,  // isBackupEligible
    null,  // isBackedUp
    null,  // attestedCredentialData
    credential.signCount(),
    deserializedAttestedCredentialData,
    new AuthenticationExtensionsAuthenticatorOutputs<>(),
    null,  // clientExtensions
    null,  // authenticatorExtensions
    null   // transports
);
```

**対応**:
- 各パラメータの適切な値を設定
- TODOコメント削除

**工数**: 2時間

---

### 10. TokenBinding削除

**ファイル**:
- `WebAuthn4jConfiguration.java:30`
- `WebAuthn4jAuthenticationManager.java:77`

**問題**:
- Token Bindingは非推奨（WebAuthn Level 3で削除予定）
- 現在の実装では常に `null` だが、フィールドとして残っている

**修正案**:
```java
// WebAuthn4jConfiguration.java
// byte[] tokenBindingId; ← 削除

// ServerProperty構築時も不要
ServerProperty serverProperty = ServerProperty.builder()
    .origins(origins)
    .rpId(rpId)
    .challenge(challenge)
    // tokenBindingIdは指定しない
    .build();
```

**影響範囲**:
- `WebAuthn4jConfiguration` クラス
- JSON設定ファイル（後方互換性維持）

**工数**: 1時間

---

## 📋 実装順序の推奨

### Phase 1: セキュリティ修正（必須）
1. ✅ カウンタ更新の実装（#1）
2. ✅ 非推奨APIからBuilderパターンへ移行（#2）
3. ✅ デッドコード削除（#3）

**期限**: 1週間以内
**工数**: 3.5時間

### Phase 2: 機能拡張（推奨）
4. ✅ 複数Origin対応（#4）
5. ✅ チャレンジサイズ拡大（#5）
6. ✅ FIXMEコメント解決（#6）

**期限**: 2週間以内
**工数**: 5.5時間

### Phase 3: 将来拡張（オプション）
7. ⏸️ TopOrigin対応（#7） - 必要時のみ
8. ⏸️ Attestation検証強化（#8） - エンタープライズのみ
9. ⏸️ CredentialConverter TODO（#9）
10. ⏸️ TokenBinding削除（#10）

**期限**: 必要時
**工数**: 13時間

---

## 🧪 テスト項目

### セキュリティテスト
- [ ] カウンタ更新が正しく動作するか
- [ ] カウンタクローン検知が動作するか
- [ ] 複数Originでの認証が正しく動作するか

### 互換性テスト
- [ ] 既存の単一Origin設定が動作するか
- [ ] マイグレーション後も正しく動作するか

### E2Eテスト
- [ ] Web→登録→認証が成功するか
- [ ] Android→登録→認証が成功するか（モバイル対応後）
- [ ] iOS→登録→認証が成功するか（モバイル対応後）

---

## 📝 マイグレーションガイド

### 既存設定の移行

**Before (単一Origin)**:
```json
{
  "rp_id": "example.com",
  "origin": "https://example.com"
}
```

**After (複数Origin)**:
```json
{
  "rp_id": "example.com",
  "allowed_origins": [
    "https://example.com"
  ]
}
```

### 後方互換性

```java
// WebAuthn4jConfigurationでの対応
public WebAuthn4jConfiguration {
    @Deprecated
    String origin;  // 後方互換性のため残す

    List<String> allowedOrigins;

    // コンストラクタで自動変換
    public List<String> getAllowedOrigins() {
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            return allowedOrigins;
        }
        // 旧設定からの自動変換
        if (origin != null && !origin.isEmpty()) {
            return List.of(origin);
        }
        return List.of();
    }
}
```

---

## 📚 参考資料

- [WebAuthn4j公式ドキュメント（日本語）](https://webauthn4j.github.io/webauthn4j/ja/)
- [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)
- [idp-server AI開発者向けドキュメント](../documentation/docs/content_10_ai_developer/ai-42-webauthn.md)

---

**最終更新**: 2025-11-08
**レビュー担当**: 開発チーム
**承認**: 未定
