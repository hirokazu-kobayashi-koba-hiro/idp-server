# OIDC4IDA verified_claims Gap分析

## 仕様要件

OIDC4IDA Section 5.1 で規定される verified_claims の構造:

```json
{
  "verified_claims": {
    "verification": { "trust_framework": "jp_aml", "evidence": [...] },
    "claims": { "given_name": "Taro", "family_name": "Tanaka" }
  }
}
```

### 仕様が規定する配信先

| 配信先 | 仕様の表現 | 意味 |
|--------|-----------|------|
| ID Token | "can be added to an ID Token" (Section 5.2) | 対応可能（CAN） |
| UserInfo | "can be added to an OpenID Connect UserInfo response" (Section 5.2) | 対応可能（CAN） |
| Access Token | "possible to utilize the format in OAuth access tokens" (Section 4.7) | 利用可能と言及のみ。具体的な構造要件・MUST/SHOULD は**未規定** |

**重要**: AT への verified_claims 格納は OIDC4IDA の規定対象外。idp-server 独自の拡張機能。

---

## 現状の実装状況

| 配信先 | Creator クラス | 仕様準拠性 | 状態 | 問題 |
|--------|--------------|-----------|------|------|
| ID Token | `VerifiedClaimsCreator` | 仕様準拠 | ✅ 正しい | `{verification: {...}, claims: {...}}` のネスト構造 |
| Access Token (全件) | `AccessTokenVerifiedClaimsCreator` | 独自拡張（仕様規定外） | ⚠️ 構造不統一 | `claims` をフラット展開、`verification` なし |
| Access Token (選択) | `AccessTokenSelectiveVerifiedClaimsCreator` | 独自拡張（仕様規定外） | ⚠️ 構造不統一 | 同上 |
| UserInfo | なし | 仕様で CAN | ❌ 未実装 | Creator 自体が存在しない |

---

## Gap詳細

### Gap 1: AccessTokenVerifiedClaimsCreator の出力構造

**現状 (L58-59):**
```java
Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();
map.put("verified_claims", userClaims);
```

**出力:**
```json
{"verified_claims": {"given_name": "Taro", "family_name": "Tanaka"}}
```

**仕様で求められる出力:**
```json
{"verified_claims": {"verification": {"trust_framework": "jp_aml"}, "claims": {"given_name": "Taro", "family_name": "Tanaka"}}}
```

**修正:**
```java
Map<String, Object> verified = new HashMap<>();
Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();
Map<String, Object> verification = userVerifiedClaims.getValueAsJsonNode("verification").toMap();
verified.put("verification", verification);
verified.put("claims", userClaims);
map.put("verified_claims", verified);
```

---

### Gap 2: AccessTokenSelectiveVerifiedClaimsCreator の出力構造

**現状 (L67-79):**
```java
Map<String, Object> verified = new HashMap<>();
Map<String, Object> verifiedClaims = new HashMap<>();
// ... scope ベースでフィルタリング ...
verified.put("verified_claims", verifiedClaims);  // verification なし
```

**出力:**
```json
{"verified_claims": {"given_name": "Taro"}}
```

**仕様で求められる出力:**
```json
{"verified_claims": {"verification": {"trust_framework": "jp_aml"}, "claims": {"given_name": "Taro"}}}
```

**修正:** verification を追加 + claims をネスト

---

### Gap 3: UserInfo 用 Creator の未実装

**現状:**
- `UserinfoCustomIndividualClaimsCreator` インターフェースは存在
- `UserinfoCustomUserClaimsCreationPluginLoader` で ServiceLoader 読み込み可能
- しかし `verified_claims` を返す実装クラスが存在しない
- META-INF/services にも `UserinfoCustomIndividualClaimsCreator` の登録ファイルなし

**必要な作業:**
1. `UserinfoVerifiedClaimsCreator` クラスを新規作成
2. META-INF/services に登録
3. `claims` パラメータの `userinfo.verified_claims` リクエストに対応

---

## 修正計画

### Phase 1: AT の構造統一（独自拡張の一貫性改善）

| ファイル | 変更内容 | 影響 |
|---------|---------|------|
| `AccessTokenVerifiedClaimsCreator.java` | `verification` + `claims` ネスト構造に修正 | AT introspect する RP に影響（**破壊的変更**） |
| `AccessTokenSelectiveVerifiedClaimsCreator.java` | 同上（scope フィルタリングは `claims` 内のみ） | 同上 |

**注意**: 仕様違反の修正ではなく、ID Token と構造を統一するための改善。既存の RP が現在のフラット構造に依存している場合は破壊的変更になる。

**設計判断:**
- `verification` は全件返す（AT に含める時点で全体の verification が前提）
- `claims` のみ scope ベースでフィルタリング
- ユーザーに `verification` が未設定の場合は空オブジェクト `{}` を返す

### Phase 2: UserInfo 対応（機能追加）

| ファイル | 変更内容 |
|---------|---------|
| `UserinfoVerifiedClaimsCreator.java` (新規) | `UserinfoCustomIndividualClaimsCreator` 実装 |
| META-INF/services (新規) | `org.idp.server.core.openid.userinfo.plugin.UserinfoCustomIndividualClaimsCreator` |

**設計判断:**
- `VerifiedClaimsCreator`（ID Token用）と同じ構造を返す
- ただし UserInfo はリクエストの `claims` パラメータではなく、`AuthorizationGrant` から判定
- `authorizationGrant` に `verified_claims` リクエストが含まれていれば返す

### Phase 3: 設定フラグ対応（#1499）

- `id_token_verified_claims` / `userinfo_verified_claims` フラグは別 Issue で対応
- Phase 1-2 完了後に参照を追加

---

## VerifiedClaimsCreator（正解実装）との差分

### ID Token 用（正解）の特徴

```java
// claims パラメータで明示的にリクエストされたフィールドのみ返す
VerifiedClaimsObject verifiedClaimsObject = requestedIdTokenClaims.verifiedClaims();
JsonNodeWrapper claimsNodeWrapper = verifiedClaimsObject.claimsNodeWrapper();

// リクエストされた claims のみ個別チェック
if (claimsNodeWrapper.contains("given_name") && userClaims.contains("given_name")) {
    verifiedClaims.put("given_name", ...);
}
```

### AT 用の設計方針

AT はリソースサーバー向けで、`claims` パラメータによる個別リクエストではなく:
- **全件版**: `access_token_verified_claims` フラグで全 claims を返す
- **選択版**: `verified_claims:*` scope プレフィックスで選択的に返す

両方とも `verification` 構造が欠落しているのが仕様違反。

### UserInfo 用の設計方針

UserInfo は `claims` パラメータの `userinfo.verified_claims` でリクエストされる。ID Token 用の `VerifiedClaimsCreator` と同じ**リクエスト駆動**のフィルタリングが必要。

ただし、`UserinfoCustomIndividualClaimsCreator` のインターフェースには `RequestedClaimsPayload` がない:

```java
// ID Token 用（リクエスト参照可能）
Map<String, Object> create(User user, Authentication authentication, AuthorizationGrant grant,
    IdTokenCustomClaims customClaims, RequestedClaimsPayload requestedClaimsPayload, ...);

// UserInfo 用（リクエスト参照なし）
Map<String, Object> create(User user, AuthorizationGrant grant, ...);
```

→ `AuthorizationGrant` から `requestedClaims` を取得できるか確認が必要。

---

---

## 仕様全体の準拠状況

### ID Token (VerifiedClaimsCreator)

| 要件 | セクション | 状態 | 詳細 |
|------|-----------|------|------|
| verification/claims のネスト構造 | 5.1 | ✅ 準拠 | 正しい構造で出力 |
| RPがリクエストしたデータのみ返す | 5.4 | ✅ 準拠 | `contains()` で明示的リクエストチェック |
| 保持していないクレームを省略 | 5.7.2 | ✅ 準拠 | リクエスト有 AND データ有 の二重チェック |
| 同意なしデータを省略 | 5.7.3 | ✅ 準拠 | AuthorizationGrant 経由で同意済みのみ到達 |
| **verification 要件不足時に verified_claims 全体を省略** | **5.7.4** | **❌ 非準拠** | **verification が空でも verified_claims を返してしまう** |
| リクエストされていないデータを返さない | 7 | ✅ 準拠 | 全フィールドがリクエスト存在チェック付き |

**5.7.4 の問題**: `VerifiedClaimsCreator.create()` L121-123 で verification/claims が空でも常に `verified_claims` を返す。空の場合はオブジェクト自体を省略すべき。

### Discovery メタデータ (ServerConfigurationResponseCreator)

| フィールド | OIDC4IDA 要件 | 状態 | 詳細 |
|-----------|-------------|------|------|
| `verified_claims_supported` | - | ✅ 実装済み | boolean フラグ |
| `trust_frameworks_supported` | REQUIRED | ✅ 実装済み | - |
| `claims_in_verified_claims_supported` | REQUIRED | ✅ 実装済み | - |
| `evidence_supported` | REQUIRED | ✅ 実装済み | - |
| `documents_supported` | REQUIRED (evidence に document 含む時) | ⚠️ フィールド名不一致 | `id_documents_supported` として実装 |
| `documents_methods_supported` | SHOULD (evidence に document 含む時) | ⚠️ フィールド名不一致 | `id_documents_verification_methods_supported` として実装 |
| `electronic_records_supported` | REQUIRED (evidence に electronic_record 含む時) | ❌ 未実装 | フィールド自体が存在しない |
| `documents_check_methods_supported` | SHOULD (evidence に document 含む時) | ❌ 未実装 | - |
| `claims_parameter_supported` | SHALL 公開 | ✅ 実装済み | - |

### データ最小化ルール

| ルール | セクション | ID Token | AT | UserInfo |
|--------|-----------|----------|------|---------|
| リクエストされたもののみ返す | 5.4 / 7 | ✅ | N/A (独自拡張) | ❌ 未実装 |
| 持っていないクレームを省略 | 5.7.2 | ✅ | ⚠️ 全件返す設計 | ❌ 未実装 |
| verification 不足時に全体省略 | 5.7.4 | ❌ | N/A | ❌ 未実装 |

---

## 修正優先度

### P1: 仕様非準拠（MUST/SHALL 違反）

| # | 問題 | 対象ファイル | 修正内容 |
|---|------|------------|---------|
| 1 | 5.7.4: verification 空時に verified_claims を省略しない | `VerifiedClaimsCreator.java` | verification/claims が空なら空 Map を返す |
| 2 | UserInfo で verified_claims 未対応 | 新規 `UserinfoVerifiedClaimsCreator.java` | Creator 実装 + ServiceLoader 登録 |
| 3 | Discovery: `electronic_records_supported` 未実装 | `AuthorizationServerConfiguration` + `ServerConfigurationResponseCreator` | フィールド追加 |

### P2: 構造一貫性（独自拡張の改善）

| # | 問題 | 対象ファイル | 修正内容 |
|---|------|------------|---------|
| 4 | AT の verified_claims が verification なしでフラット展開 | `AccessTokenVerifiedClaimsCreator.java` | verification + claims ネスト化 |
| 5 | AT selective も同上 | `AccessTokenSelectiveVerifiedClaimsCreator.java` | 同上 |

### P3: Discovery フィールド名修正

| # | 問題 | 対象ファイル | 修正内容 |
|---|------|------------|---------|
| 6 | `id_documents_supported` → `documents_supported` | `ServerConfigurationResponseCreator` | フィールド名変更（後方互換注意） |
| 7 | `id_documents_verification_methods_supported` → `documents_methods_supported` | 同上 | 同上 |
| 8 | `documents_check_methods_supported` 追加 | 同上 | 新規フィールド |

---

## E2E テスト計画

| テスト | 検証内容 |
|--------|---------|
| AT verified_claims 構造 | `verification` + `claims` のネスト確認 |
| AT selective 構造 | scope フィルタリング + ネスト確認 |
| UserInfo verified_claims | UserInfo レスポンスに verified_claims が含まれる |
| 既存 ID Token テスト | 回帰テスト（壊れていないこと） |

---

## 既存 E2E テスト参照

- `e2e/src/tests/spec/oidc_for_identity_assurance.test.js` — ID Token の verified_claims テスト（動作中）
- 同ファイル L126 — UserInfo の verified_claims チェック（コメントアウト中）
