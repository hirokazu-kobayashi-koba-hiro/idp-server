# RFC 9396 Rich Authorization Requests - テスト失敗調査ガイド

## 失敗したテスト

### テスト: AS MUST refuse CIBA request with invalid authorization_details type

**ファイル**: `e2e/src/tests/spec/rfc9396_rar_ciba.test.js`

**失敗内容**:
```
Expected: "invalid_authorization_details"
Received: undefined
```

## 問題の詳細

### RFC 9396要件
RFC 9396 Section 4では以下のように規定されています：

> AS MUST refuse to process any unknown authorization details type or authorization details not conforming to the respective type definition.

### 実際の動作

1. **Backchannel Authentication Endpoint**:
   - リクエスト: `invalid_type` を含む `authorization_details`
   - 期待: `error: "invalid_authorization_details"` を返す
   - 実際: `auth_req_id` を返して成功（200 OK）

2. **Token Endpoint**:
   - リクエスト: 上記の `auth_req_id` でトークン取得
   - 実際: トークンが正常に発行される（**SPEC VIOLATION**）
   - access_tokenに無効な `invalid_type` が含まれる

### テスト出力の解析

```
=== Test: Invalid authorization_details type ===
Request authorization_details: [
  {
    "type": "invalid_type",
    "actions": ["some_action"],
    "locations": ["https://example.com/invalid"]
  }
]

CIBA Response Status: 200
CIBA Response Data: {
  "auth_req_id": "6e15a6fb-e638-486b-9d7e-8fba37ba7605",
  "interval": 5,
  "expires_in": 60
}

✗ auth_req_id returned - validation may be deferred to token endpoint

=== Attempting to complete authentication ===

Token Response Status: 200
Token Response Data: {
  "access_token": "eyJ...",
  "authorization_details": [
    {
      "locations": ["https://example.com/invalid"],
      "type": "invalid_type",  ← 無効なtypeがそのまま返却
      "actions": ["some_action"]
    }
  ],
  ...
}

✗✗ SPEC VIOLATION: Token issued with invalid authorization_details type
```

## 調査方法

### 1. テスト実行

```bash
cd e2e
npm test -- --testPathPattern=rfc9396_rar_ciba.test.js \
  --testNamePattern="AS MUST refuse CIBA request with invalid authorization_details type"
```

### 2. サーバーログ確認

**重要**: テスト実行中に並行してサーバーログを確認します。

```bash
# 別ターミナルで実行
cd /Users/hirokazu.kobayashi/work/idp-server
./gradlew bootRun 2>&1 | grep -A 10 -B 10 "authorization_details\|invalid_type"
```

**確認ポイント**:
- Backchannel Authentication Endpointで `authorization_details` がどう処理されているか
- 検証エラーログが出力されているか
- Token Endpointで `authorization_details` の検証が行われているか

### 3. ソースコード調査

#### 3.1 Backchannel Authentication Endpoint の処理

**調査対象ファイル**:
```bash
find libs -name "*BackchannelAuthentication*.java" | grep -v test
```

**確認ポイント**:
```java
// authorization_details の検証処理を探す
grep -r "authorization_details" libs/idp-server-*/src/main/java --include="*BackchannelAuthentication*.java"
grep -r "AuthorizationDetails" libs/idp-server-*/src/main/java --include="*BackchannelAuthentication*.java"
```

#### 3.2 Authorization Details Validator

**調査対象**:
```bash
find libs -name "*AuthorizationDetails*.java" | grep -v test
```

**確認ポイント**:
```java
// Validatorの存在と呼び出し箇所
grep -r "class.*AuthorizationDetails.*Validator" libs/idp-server-*/src/main/java
grep -r "validateType\|validateAuthorizationDetails" libs/idp-server-*/src/main/java
```

#### 3.3 サポートされているTypeの定義

**調査対象**:
```bash
# Tenant設定またはクライアント設定でサポートされているtypeを探す
grep -r "account_information\|payment_initiation" libs/idp-server-*/src/main/java --include="*Config*.java"
grep -r "authorization_details.*type" libs/idp-server-database/postgresql/*.sql
```

### 4. デバッグ用のテストケース追加

**ファイル**: `e2e/src/tests/spec/rfc9396_rar_ciba.test.js`

```javascript
it("DEBUG: Trace invalid type validation flow", async () => {
  // 1. サポートされているtypeを確認
  const validType = {
    "type": "account_information",
    "actions": ["list_accounts"],
    "locations": ["https://example.com/accounts"]
  };

  const validResponse = await requestBackchannelAuthentications({
    endpoint: serverConfig.backchannelAuthenticationEndpoint,
    clientId: clientSecretPostClient.clientId,
    scope: "openid profile",
    bindingMessage: ciba.bindingMessage,
    userCode: ciba.userCode,
    authorizationDetails: JSON.stringify([validType]),
    loginHint: ciba.loginHintSub,
    clientSecret: clientSecretPostClient.clientSecret,
  });

  console.log("Valid type response:", validResponse.data);

  // 2. 無効なtypeでリクエスト
  const invalidType = {
    "type": "completely_unknown_type",
    "actions": ["some_action"],
    "locations": ["https://example.com/test"]
  };

  const invalidResponse = await requestBackchannelAuthentications({
    endpoint: serverConfig.backchannelAuthenticationEndpoint,
    clientId: clientSecretPostClient.clientId,
    scope: "openid profile",
    bindingMessage: ciba.bindingMessage,
    userCode: ciba.userCode,
    authorizationDetails: JSON.stringify([invalidType]),
    loginHint: ciba.loginHintSub,
    clientSecret: clientSecretPostClient.clientSecret,
  });

  console.log("Invalid type response:", invalidResponse.data);

  // 比較
  console.log("Valid has auth_req_id:", !!validResponse.data.auth_req_id);
  console.log("Invalid has auth_req_id:", !!invalidResponse.data.auth_req_id);
  console.log("Invalid has error:", !!invalidResponse.data.error);
});
```

### 5. 設定ファイル確認

#### 5.1 テナント設定

**確認ポイント**:
```bash
# データベースでサポートされているauthorization_details typeを確認
psql -h localhost -U idp_app_user -d idp_server -c \
  "SELECT tenant_id, type, jsonb_pretty(configuration)
   FROM tenant_authorization_details_config
   WHERE tenant_id = '67e7eae6-62b0-4500-9eff-87459f63fc66';"
```

#### 5.2 クライアント設定

```bash
psql -h localhost -U idp_app_user -d idp_server -c \
  "SELECT client_id, allowed_authorization_details_types
   FROM client_configuration
   WHERE client_id = 'clientSecretPost';"
```

## 期待される修正箇所

### 1. Backchannel Authentication Endpoint

**ファイル（推定）**:
- `libs/idp-server-core/src/main/java/org/idp/server/oauth/ciba/request/BackchannelAuthenticationRequestValidator.java`
- `libs/idp-server-core/src/main/java/org/idp/server/oauth/rar/AuthorizationDetailsValidator.java`

**修正内容**:
```java
public void validate(BackchannelAuthenticationRequest request) {
    // 既存の検証...

    // authorization_details の検証を追加
    if (request.hasAuthorizationDetails()) {
        AuthorizationDetails authorizationDetails = request.authorizationDetails();
        authorizationDetailsValidator.validate(authorizationDetails, tenant);
        // ↑ ここで invalid_authorization_details エラーをthrow
    }
}
```

### 2. Authorization Details Validator

```java
public class AuthorizationDetailsValidator {

    public void validate(AuthorizationDetails authorizationDetails, Tenant tenant) {
        for (AuthorizationDetail detail : authorizationDetails.values()) {
            validateType(detail.type(), tenant);
        }
    }

    private void validateType(String type, Tenant tenant) {
        Set<String> supportedTypes = tenant.supportedAuthorizationDetailsTypes();
        if (!supportedTypes.contains(type)) {
            throw new InvalidAuthorizationDetailsException(
                "unauthorized authorization details type (" + type + ")"
            );
        }
    }
}
```

## RFC 9396 参照箇所

### Section 4: Authorization Request

> If the authorization server does not support authorization details in general or the specific authorization details type, the value of the type parameter, or other authorization details parameters, the authorization server returns the error code invalid_authorization_details.

### Section 7: Token Response

> The AS MUST refuse to process any unknown authorization details type or authorization details not conforming to the respective type definition. If the client is not authorized for such a request (e.g., the client exceeded the allowed scope), the authorization server refuses the request utilizing the error code invalid_authorization_details.

## 関連Issue

- Issue #940: RFC 9396 Rich Authorization Requests のテストケースを充実化

## 次のステップ

1. **サーバーログ確認**: テスト実行時のサーバー側の動作を確認
2. **ソースコード調査**: 上記の調査ポイントに従ってコードを確認
3. **修正実装**: Validatorを適切な場所に追加
4. **テスト再実行**: 修正後にテストが成功することを確認

## 参考

- RFC 9396: https://datatracker.ietf.org/doc/html/rfc9396
- テストファイル: `e2e/src/tests/spec/rfc9396_rar_ciba.test.js`
- Issue #940: GitHub Issues
