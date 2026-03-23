# verified_claims 機能ギャップ分析

> 分析日: 2026-03-23

## 実装の全体像

verified_claims を返す仕組みが3つある:

| クラス | 対象 | トリガー | 返す内容 |
|--------|------|---------|---------|
| `VerifiedClaimsCreator` | ID Token | `claims` パラメータの `id_token.verified_claims` | `verification` + `claims`（要求されたクレームのみ） |
| `AccessTokenVerifiedClaimsCreator` | Access Token (JWT) | `access_token_verified_claims: true` | `claims` のみ（全クレーム） |
| `AccessTokenSelectiveVerifiedClaimsCreator` | Access Token (JWT) | `access_token_selective_verified_claims: true` + `verified_claims:*` スコープ | `claims` のみ（スコープで選択） |

## 動作確認結果

### 1. eKYC 申込み → 承認 → IV Results API

**結果: OK**

```json
// GET /v1/me/identity-verification/results?type=authentication-assurance
{
  "verified_claims": {
    "claims": {
      "given_name": "Taro",
      "family_name": "Tanaka",
      "birthdate": "1990-01-15",
      "address": { "country": "JP", ... }
    },
    "verification": {
      "trust_framework": "jp_aml"
    }
  }
}
```

### 2. ID Token（`claims` パラメータ経由）

**結果: 未確認（CIBAフローでは確認困難）**

- `VerifiedClaimsCreator` は `claims` パラメータの `id_token.verified_claims` を要求した場合のみ動作
- 認可コードフローでは E2E テスト (`oidc_for_identity_assurance.test.js`) で動作確認済み
- CIBA フローでは `claims` パラメータを signed request JWT に含めたが、ID Token に `verified_claims` は含まれなかった
- `id_token_strict_mode: true` の場合、`claims` パラメータで `essential: true` を指定しないとクレームが含まれない可能性あり

### 3. Access Token（`verified_claims:*` スコープ経由）

**結果: 部分的に OK**

```json
// Access Token JWT payload
{
  "verified_claims": {
    "birthdate": "1990-01-15",
    "given_name": "Taro",
    "family_name": "Tanaka"
  }
}
```

- `verified_claims:given_name`, `verified_claims:family_name`, `verified_claims:birthdate` スコープで選択的にクレームが含まれた
- ただし `verification`（`trust_framework` 等）が含まれていない

### 4. UserInfo

**結果: verified_claims なし**

- UserInfo には `verified_claims` が返らない
- E2E テストでも UserInfo の `verified_claims` チェックはコメントアウトされている（126行目）
- UserInfo 用の `verified_claims` 返却実装が存在しない可能性

## 確認済みの問題

### Issue 1: `AccessTokenSelectiveVerifiedClaimsCreator` に `verification` が含まれない

**ファイル**: `libs/idp-server-core-extension-ida/src/main/java/org/idp/server/core/extension/identity/verified/AccessTokenSelectiveVerifiedClaimsCreator.java`

**現状**:
```java
// create() メソッド
verified.put("verified_claims", verifiedClaims);  // claims のみ
```

**あるべき姿**:
```java
Map<String, Object> result = new HashMap<>();
Map<String, Object> verificationMap = new HashMap<>();
JsonNodeWrapper verification = userVerifiedClaims.getValueAsJsonNode("verification");
if (verification.contains("trust_framework")) {
    verificationMap.put("trust_framework", verification.getValueOrEmptyAsString("trust_framework"));
}
result.put("verification", verificationMap);
result.put("claims", verifiedClaims);
verified.put("verified_claims", result);
```

### Issue 2: `AccessTokenVerifiedClaimsCreator` にも同じ問題

**ファイル**: `libs/idp-server-core-extension-ida/src/main/java/org/idp/server/core/extension/identity/verified/AccessTokenVerifiedClaimsCreator.java`

**現状**:
```java
map.put("verified_claims", userClaims);  // claims のみ、verification なし
```

### Issue 3: UserInfo に verified_claims が返らない

- `VerifiedClaimsCreator` は `CustomIndividualClaimsCreator` を実装しており、ID Token 用
- UserInfo 用の対応する Creator が存在しない
- E2E テストでもコメントアウトされている

### Issue 4: CIBA フローで `claims` パラメータが ID Token に反映されない可能性

- CIBA signed request JWT に `claims` パラメータを含めたが、ID Token には `verified_claims` が含まれなかった
- `id_token_strict_mode: true` との相互作用の可能性
- 認可コードフローでは動作する（E2E テストで確認済み）

## OIDC for IDA 仕様との比較

OIDC for Identity Assurance 1.0 仕様では:

1. `verified_claims` は `claims` パラメータで要求する（ID Token / UserInfo 両方）
2. レスポンスには `verification`（trust_framework, evidence 等）+ `claims` の構造で返す
3. `verified_claims_supported: true` が Discovery に必要

### 仕様準拠状況

| 要件 | ID Token | UserInfo | Access Token |
|------|----------|----------|-------------|
| `claims` パラメータで要求 | OK（認可コードフロー） | 未実装 | N/A（スコープベース） |
| `verification` を含む | OK | - | 未実装 |
| `claims` を含む | OK | - | OK |
| スコープベースの選択 | N/A | N/A | OK（`verified_claims:*`） |

## ユーザーの verified_claims データ構造

`User.verifiedClaims` は `HashMap<String, Object>` で以下の構造:

```json
{
  "verification": {
    "trust_framework": "jp_aml"
  },
  "claims": {
    "given_name": "Taro",
    "family_name": "Tanaka",
    "birthdate": "1990-01-15",
    "address": { ... }
  }
}
```

これは `identity-verification-config.json` の `result.verified_claims_mapping_rules` で定義したマッピングに基づいて生成される。`verification.trust_framework` と `claims.*` の両方がユーザーに保存されている。

## 各 Creator の実装詳細

### `VerifiedClaimsCreator`（ID Token 用）
- **インターフェース**: `CustomIndividualClaimsCreator`
- **条件**: `authorizationGrant.idTokenClaims().hasVerifiedClaims()` → `claims` パラメータで `id_token.verified_claims` を要求した場合のみ
- **出力**: `{ "verified_claims": { "verification": { "trust_framework": "..." }, "claims": { ... } } }`
- **フィルタ**: `claims` パラメータで要求されたクレームのみ（`claimsNodeWrapper.contains("given_name")` 等で個別チェック）
- **評価**: OIDC4IDA 仕様に沿った正しい実装

### `AccessTokenVerifiedClaimsCreator`（AT 全クレーム）
- **インターフェース**: `AccessTokenCustomClaimsCreator`
- **条件**: `enabledAccessTokenVerifiedClaims()` が true
- **出力**: `{ "verified_claims": { "given_name": "...", ... } }` — **claims の中身だけフラット**
- **問題**: `verification` が欠落、OIDC4IDA の構造（`{ verification: {}, claims: {} }`）と不一致

### `AccessTokenSelectiveVerifiedClaimsCreator`（AT スコープ選択）
- **インターフェース**: `AccessTokenCustomClaimsCreator`
- **条件**: `enabledAccessTokenSelectiveVerifiedClaims()` が true + `verified_claims:*` スコープ
- **出力**: `{ "verified_claims": { "given_name": "...", ... } }` — **claims の中身だけフラット**
- **問題**: 同上。`verification` が欠落

## 推奨アクション

### Priority 1: Access Token の verified_claims 構造を OIDC4IDA 準拠にする

**対象ファイル**:
- `AccessTokenSelectiveVerifiedClaimsCreator.java`
- `AccessTokenVerifiedClaimsCreator.java`

**修正内容**: `verified_claims` の出力を `{ verification: {}, claims: {} }` 構造にする

**`AccessTokenSelectiveVerifiedClaimsCreator.create()` の修正案**:
```java
Map<String, Object> verified = new HashMap<>();
Map<String, Object> verifiedClaimsResult = new HashMap<>();

// verification を含める
JsonNodeWrapper verification = userVerifiedClaims.getValueAsJsonNode("verification");
verifiedClaimsResult.put("verification", verification.toMap());

// claims をスコープフィルタで選択
Map<String, Object> filteredClaims = new HashMap<>();
Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();
for (String scope : filteredScopes) {
    String claimName = scope.substring(prefix.length());
    if (userClaims.containsKey(claimName)) {
        filteredClaims.put(claimName, userClaims.get(claimName));
    }
}
verifiedClaimsResult.put("claims", filteredClaims);

verified.put("verified_claims", verifiedClaimsResult);
return verified;
```

### Priority 2: UserInfo で verified_claims を返す

- `CustomIndividualClaimsCreator` の UserInfo 版（`CustomUserinfoClaimsCreator` 等）を実装
- `claims` パラメータの `userinfo.verified_claims` で要求された場合に返す
- または `verified_claims:*` スコープベースで返す

### Priority 3: CIBA フローで `claims` パラメータが ID Token に反映されるか調査

- CIBA signed request JWT の `claims` パラメータが `GrantIdTokenClaims` に反映されるかを追跡
- `id_token_strict_mode: true` との相互作用を確認
