# WebAuthn4j Adapter 設計ガイド

## 概要

`idp-server-webauthn4j-adapter` は、WebAuthn/FIDO2 認証機能を提供するアダプターモジュールです。
[webauthn4j](https://github.com/webauthn4j/webauthn4j) ライブラリをラップし、idp-server のアーキテクチャに統合します。

このドキュメントでは、WebAuthn4j Adapter の設計思想と実装パターンを解説します。

---

## アーキテクチャ

### モジュール構成

```
┌─────────────────────────────────────────────────────────────────┐
│                 idp-server-authentication-interactors           │
│  (Fido2*Interactor: ビジネスロジック層)                          │
├─────────────────────────────────────────────────────────────────┤
│                 idp-server-webauthn4j-adapter                   │
│  (WebAuthn4j*Executor: WebAuthn処理層)                          │
├─────────────────────────────────────────────────────────────────┤
│                 webauthn4j (外部ライブラリ)                      │
│  (WebAuthnManager: 暗号検証・データパース)                       │
└─────────────────────────────────────────────────────────────────┘
```

### 責務分離

| レイヤー | モジュール | 責務 |
|---------|-----------|------|
| **Interactor** | `idp-server-authentication-interactors` | 認証フロー制御、トランザクション管理 |
| **Executor** | `idp-server-webauthn4j-adapter` | WebAuthn処理、クレデンシャル管理 |
| **Library** | `webauthn4j` | 暗号検証、データ構造パース |

### 設計原則: ライブラリ依存の分離

```java
// ❌ 悪い例: コアモジュールがwebauthn4jに直接依存
// idp-server-core 内
import com.webauthn4j.WebAuthnManager;  // ← NG: 外部ライブラリ依存

// ✅ 良い例: アダプター経由でアクセス
// idp-server-authentication-interactors 内
AuthenticationExecutor executor = executors.get("webauthn4j_authentication");
AuthenticationExecutionResult result = executor.execute(...);
```

**理由**:
- webauthn4j ライブラリのバージョンアップに対する影響を局所化
- 将来的に別のWebAuthnライブラリへの差し替えを可能に
- コアモジュールの外部依存を最小化

---

## DDL設計: Core + JSONB パターン

### 設計思想

WebAuthn仕様は継続的に進化しています（Level 2 → Level 3 → Level 4...）。
新しい仕様が追加されるたびにDDL変更が必要になることを避けるため、**Core + JSONB パターン**を採用しています。

```
┌─────────────────────────────────────────────────────────────────┐
│                    webauthn_credentials テーブル                 │
├─────────────────────────────────────────────────────────────────┤
│  【Core Columns】検索・ポリシー判定に使用                        │
│  - id, user_id, rp_id, aaguid                                   │
│  - sign_count, rk, backup_eligible, backup_state                │
├─────────────────────────────────────────────────────────────────┤
│  【JSONB Columns】拡張データ・将来の仕様追加用                   │
│  - authenticator: {"transports": [...], "attachment": "..."}    │
│  - attestation: {"type": "...", "format": "..."}                │
│  - extensions: {"cred_protect": 2, "prf": {...}}                │
│  - device: {"name": "...", "registered_ip": "..."}              │
│  - metadata: {} (将来の拡張用)                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Core Columns（検索・ポリシー用）

| カラム | 型 | 用途 |
|--------|------|------|
| `id` | TEXT | Credential ID（主キー） |
| `tenant_id` | UUID | マルチテナント分離 |
| `user_id` | VARCHAR(256) | FIDO2 User ID |
| `rp_id` | VARCHAR(256) | Relying Party ID |
| `aaguid` | VARCHAR(36) | 認証器モデル識別（脆弱性追跡・ポリシー制御） |
| `sign_count` | BIGINT | 署名カウンタ（クローン検出） |
| `rk` | BOOLEAN | Resident Key（Discoverable Credential） |
| `backup_eligible` | BOOLEAN | WebAuthn Level 3: BE フラグ |
| `backup_state` | BOOLEAN | WebAuthn Level 3: BS フラグ |

### JSONB Columns（拡張用）

| カラム | 内容例 | 用途 |
|--------|--------|------|
| `authenticator` | `{"transports": ["internal"], "attachment": "platform"}` | 認証器メタデータ |
| `attestation` | `{"type": "none", "format": "none"}` | Attestation情報 |
| `extensions` | `{"cred_protect": 2}` | WebAuthn拡張 |
| `device` | `{"name": "MacBook Pro", "registered_ip": "..."}` | デバイス情報 |
| `metadata` | `{}` | 将来の拡張用 |

### Core Column選定基準

カラムをCoreにするかJSONBにするかは、以下の基準で判断します:

```
┌─────────────────────────────────────────────────────────────────┐
│  Core Column にすべき場合                                        │
│  ✅ WHERE句で頻繁に検索する                                      │
│  ✅ インデックスが必要                                           │
│  ✅ セキュリティポリシー判定に使用                               │
│  ✅ 例: aaguid（脆弱性のある認証器をブロック）                   │
├─────────────────────────────────────────────────────────────────┤
│  JSONB Column にすべき場合                                       │
│  ✅ 表示・監査目的のみ                                           │
│  ✅ 将来の仕様追加で変更される可能性                             │
│  ✅ 複雑なネスト構造                                             │
│  ✅ 例: transports（UX最適化用、ポリシー判定には不使用）         │
└─────────────────────────────────────────────────────────────────┘
```

### WebAuthn Level 4+ への対応

JSONB パターンにより、WebAuthn Level 4 で新しいフラグや拡張が追加されても：

```sql
-- DDL変更不要！JSONBカラムに追加するだけ
-- extensions: {"cred_protect": 2, "new_level4_feature": {...}}
```

ただし、検索やポリシー判定に必要な新フラグが追加された場合は、Core Columnへの追加を検討します。

---

## データモデル: WebAuthn4jCredential

### クラス構造

**情報源**: [WebAuthn4jCredential.java](../../../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jCredential.java)

```java
public class WebAuthn4jCredential {
  // Core Fields（DB Core Columns対応）
  String id;
  String userId;
  String username;
  String userDisplayName;
  String rpId;
  String aaguid;
  String attestedCredentialData;
  Integer signatureAlgorithm;
  long signCount;
  Boolean rk;
  Boolean backupEligible;
  Boolean backupState;

  // JSON Fields（DB JSONB Columns対応）
  Map<String, Object> authenticator;
  Map<String, Object> attestation;
  Map<String, Object> extensions;
  Map<String, Object> device;
  Map<String, Object> metadata;

  // Timestamps
  Long createdAt;
  Long updatedAt;
  Long authenticatedAt;

  // Convenience methods（JSONB内の頻出フィールドへのアクセス）
  public List<String> transports() {
    return (List<String>) authenticator.get("transports");
  }

  public String attestationType() {
    return (String) attestation.get("type");
  }

  public Integer credProtect() {
    return (Integer) extensions.get("cred_protect");
  }
}
```

### JSON Map の設計意図

```java
// ❌ 避けるべき: 個別フィールドの乱立
String transport1;
String transport2;
String transport3;
String attestationType;
String attestationFormat;
Integer credProtect;
Boolean prfEnabled;
// ... WebAuthn Level 4で追加されるたびに増える

// ✅ 採用: JSON Mapによる柔軟な構造
Map<String, Object> authenticator;  // transports, attachment
Map<String, Object> attestation;    // type, format
Map<String, Object> extensions;     // cred_protect, prf, largeBlob, ...
```

---

## Executor パターン

### 4つのExecutor

| Executor | 機能 | 入力 | 出力 |
|----------|------|------|------|
| `WebAuthn4jRegistrationChallengeExecutor` | 登録チャレンジ生成 | user info, rp info | challenge, options |
| `WebAuthn4jRegistrationExecutor` | 登録検証・保存 | attestation response | credential |
| `WebAuthn4jAuthenticationChallengeExecutor` | 認証チャレンジ生成 | user credentials | challenge, allowCredentials |
| `WebAuthn4jAuthenticationExecutor` | 認証検証 | assertion response | verification result |

### 処理フロー

```
【登録フロー】
Client                    Server (Interactor)              Server (Executor)
  │                            │                               │
  │──GET /challenge──────────▶│                               │
  │                            │──execute()────────────────▶│
  │                            │                               │ RegistrationChallengeExecutor
  │                            │◀──challenge, options────────│
  │◀──challenge, options──────│                               │
  │                            │                               │
  │──POST /register───────────▶│                               │
  │   (attestation response)   │──execute()────────────────▶│
  │                            │                               │ RegistrationExecutor
  │                            │                               │ ├─ verify attestation
  │                            │                               │ ├─ extract credential data
  │                            │                               │ └─ save to DB
  │                            │◀──success─────────────────────│
  │◀──success─────────────────│                               │
```

### Executor実装例

**情報源**: [WebAuthn4jRegistrationExecutor.java](../../../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jRegistrationExecutor.java)

```java
@Override
public AuthenticationExecutionResult execute(
    Tenant tenant,
    AuthenticationTransactionIdentifier identifier,
    AuthenticationExecutionRequest request,
    RequestAttributes requestAttributes,
    AuthenticationExecutionConfig configuration) {

  // 1. チャレンジコンテキスト取得
  WebAuthn4jChallengeContext context = transactionQueryRepository.get(
      tenant, identifier, type().value(), WebAuthn4jChallengeContext.class);

  // 2. WebAuthn4jManagerで検証
  WebAuthn4jRegistrationManager manager = new WebAuthn4jRegistrationManager(
      configuration, context.challenge(), request, ...);
  WebAuthn4jCredential credential = manager.verifyAndCreateCredential();

  // 3. クレデンシャル保存
  credentialRepository.register(tenant, credential);

  return AuthenticationExecutionResult.success(credential.toMap());
}
```

---

## データベース抽象化

### 両DB対応の設計

```
┌─────────────────────────────────────────────────────────────────┐
│              WebAuthn4jCredentialRepository (Interface)         │
├─────────────────────────────────────────────────────────────────┤
│              WebAuthn4jCredentialDataSource (実装)              │
│                           │                                     │
│              ┌────────────┴────────────┐                       │
│              ▼                         ▼                        │
│    PostgresqlExecutor           MysqlExecutor                   │
│    (JSONB, ?::uuid)             (JSON, CAST)                    │
└─────────────────────────────────────────────────────────────────┘
```

### DB固有の差異

| 項目 | PostgreSQL | MySQL |
|------|------------|-------|
| JSON型 | `JSONB` | `JSON` |
| UUID キャスト | `?::uuid` | `?`（BINARY(16)） |
| JSON挿入 | `?::jsonb` | `CAST(? AS JSON)` |
| 部分インデックス | `WHERE rk = true` | 不可 |
| GINインデックス | ✅ 対応 | ❌ 非対応 |

### 実装例: PostgresqlExecutor

**情報源**: [PostgresqlExecutor.java](../../../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/datasource/credential/PostgresqlExecutor.java)

```java
@Override
public void register(Tenant tenant, WebAuthn4jCredential credential) {
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  String sqlTemplate = """
      INSERT INTO webauthn_credentials (
        id, tenant_id, user_id, ...,
        rk, backup_eligible, backup_state,
        authenticator, attestation, extensions, device, metadata,
        created_at
      )
      VALUES (?, ?::uuid, ?, ..., ?, ?, ?,
              ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
              to_timestamp(?::bigint / 1000.0));
      """;

  List<Object> params = new ArrayList<>();
  params.add(credential.id());
  params.add(tenant.identifierUUID());
  // ... core columns ...
  params.add(jsonConverter.write(credential.authenticator()));
  params.add(jsonConverter.write(credential.attestation()));
  // ...

  sqlExecutor.execute(sqlTemplate, params);
}
```

---

## セキュリティ考慮事項

### 1. クローン検出（signCount検証）

```java
// 認証時にsignCountを検証
if (storedSignCount > 0 && receivedSignCount <= storedSignCount) {
  // クローンされた認証器の可能性
  log.warn("Possible cloned authenticator detected: credentialId={}", credentialId);
  // ポリシーに応じて拒否または警告
}
```

### 2. Backup Flags の活用

```java
// 同期パスキー（Synced Passkey）の検出
if (credential.backupEligible() && credential.backupState()) {
  // iCloud Keychain, Google Password Manager等で同期されている
  // セキュリティポリシーに応じた制御が可能
}
```

### 3. AAGUID による認証器制御

```java
// 脆弱性のある認証器モデルをブロック
Set<String> blockedAaguids = Set.of(
    "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"  // 脆弱性CVE-XXXX-XXXXの認証器
);

if (blockedAaguids.contains(credential.aaguid())) {
  throw new SecurityException("This authenticator model is blocked due to known vulnerabilities");
}
```

### 4. allowCredentials 検証（CVE-2025-26788対策）

Non-Discoverable Credential フローでは、allowCredentials リストを検証して
Credential ID の改ざんによるなりすまし攻撃を防止します。

**情報源**: [WebAuthn4jAuthenticationManager.java](../../../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jAuthenticationManager.java)

```java
// チャレンジ生成時に許可されたCredential IDリストを保存
List<byte[]> allowCredentials = challenge.getAllowCredentials();

// 認証検証時にCredential IDがリストに含まれるか確認
AuthenticationParameters params = new AuthenticationParameters(
    serverProperty,
    credentialRecord,
    allowCredentials,  // ← nullではなくチャレンジから取得
    userVerificationRequired,
    userPresenceRequired
);
```

---

## 関連ファイル一覧

### コア実装

| ファイル | 説明 |
|---------|------|
| `WebAuthn4jCredential.java` | クレデンシャルモデル |
| `WebAuthn4jCredentialRepository.java` | リポジトリインターフェース |
| `WebAuthn4jCredentialDataSource.java` | リポジトリ実装 |
| `WebAuthn4jRegistrationChallengeExecutor.java` | 登録チャレンジ生成 |
| `WebAuthn4jRegistrationExecutor.java` | 登録検証 |
| `WebAuthn4jAuthenticationChallengeExecutor.java` | 認証チャレンジ生成 |
| `WebAuthn4jAuthenticationExecutor.java` | 認証検証 |
| `WebAuthn4jConfiguration.java` | 設定クラス |

### データベース

| ファイル | 説明 |
|---------|------|
| `V0_9_27_2__webauthn4j.sql` | PostgreSQLスキーマ |
| `V0_9_27_2__webauthn.mysql.sql` | MySQLスキーマ |
| `PostgresqlExecutor.java` | PostgreSQL実装 |
| `MysqlExecutor.java` | MySQL実装 |
| `ModelConverter.java` | DB結果→モデル変換 |

---

## 参考情報

### WebAuthn仕様

- [W3C Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)
- [W3C Web Authentication Level 3](https://www.w3.org/TR/webauthn-3/)
- [FIDO Alliance Specifications](https://fidoalliance.org/specifications/)

### 関連ドキュメント

- [FIDO2/WebAuthn改善バックログ](../../99-refactoring/refactor-02-fido2-webauthn-improvements.md)
- [認証インタラクター](./authentication-interactor.md)
- [AAGUID解説](../../../../content_11_learning/05-fido-webauthn/fido2-aaguid-authenticator-identification.md)
- [Attestation解説](../../../../content_11_learning/05-fido-webauthn/fido2-attestation-types-and-verification.md)

---

**最終更新**: 2026-01-22
