# idp-server-core-extension-ciba - CIBA拡張

## モジュール概要

**情報源**: `libs/idp-server-core-extension-ciba/`
**確認日**: 2025-10-12

### 責務

CIBA (Client Initiated Backchannel Authentication) 仕様実装。

**RFC**: [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)

### 主要機能

- **Backchannel Authentication Endpoint**: `/bc-authorize`
- **非同期認証**: プッシュ通知によるユーザー認証
- **Polling/Push/Ping モード**: 3つの通知モードをサポート

## パッケージ構造

**情報源**: `find libs/idp-server-core-extension-ciba/src/main/java -type d`

```
libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/
├── handler/                  # CIBAエンドポイント処理
│   ├── CibaRequestHandler    # Backchannel Authentication Request
│   ├── CibaAuthorizeHandler  # ユーザー認証承認
│   ├── CibaDenyHandler       # ユーザー認証拒否
│   └── io/                   # I/O定義
├── token/                    # トークン発行
│   ├── CibaGrantService      # CIBA Grant処理
│   └── CibaGrantServiceFactory
├── request/                  # リクエスト処理
│   ├── BackchannelAuthenticationRequest
│   └── RequestObjectPatternFactory
├── grant/                    # グラント管理
│   ├── CibaGrant
│   └── CibaGrantFactory
├── context/                  # コンテキスト
├── validator/                # 入力検証
├── verifier/                 # ビジネス検証
├── clientnotification/       # クライアント通知（Push/Ping）
└── repository/               # Repository定義
```

## 主要クラス

### CibaRequestHandler

**情報源**: [CibaRequestHandler.java:61](../../libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/handler/CibaRequestHandler.java#L61)

```java
/**
 * Backchannel Authentication Request処理
 * 確認方法: 実ファイルの61-120行目
 */
public class CibaRequestHandler {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  CibaGrantRepository cibaGrantRepository;
  CibaContextCreators contextCreators;
  ClientAuthenticationHandler clientAuthenticationHandler;

  // Backchannel Authentication Request処理
  public BackchannelAuthenticationResponse handle(
      CibaRequest request,
      Tenant tenant,
      AuthorizationServerConfiguration serverConfig,
      ClientConfiguration clientConfig) {

    // 1. Validator - 入力検証
    CibaRequestValidator validator = new CibaRequestValidator(request, serverConfig, clientConfig);
    validator.validate();

    // 2. Verifier - ビジネスルール検証
    CibaRequestVerifier verifier = new CibaRequestVerifier(...);
    verifier.verify();

    // 3. Context作成・永続化
    CibaRequestContext context = contextCreator.create(...);
    BackchannelAuthenticationRequest authRequest = context.toBackchannelAuthenticationRequest();
    backchannelAuthenticationRequestRepository.register(tenant, authRequest);

    // 4. レスポンス生成（auth_req_id返却）
    return BackchannelAuthenticationResponseBuilder.build(authRequest);
  }
}
```

### CibaGrantService

**情報源**: [CibaGrantService.java](../../libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/token/CibaGrantService.java)

```java
/**
 * CIBA Grant処理（Token Endpoint）
 * grant_type=urn:openid:params:grant-type:ciba
 */
public class CibaGrantService implements OAuthTokenCreationService {

  public OAuthToken create(TokenRequestContext context) {
    // auth_req_idからCibaGrantを取得
    // トークン発行
  }
}
```

## Plugin登録

```
# META-INF/services/org.idp.server.core.openid.token.service.OAuthTokenCreationServiceFactory
org.idp.server.core.extension.ciba.token.CibaGrantServiceFactory
```

## CIBA フロー

### 1. Backchannel Authentication Request

```
POST /bc-authorize
Content-Type: application/x-www-form-urlencoded

scope=openid+email&
client_notification_token=8d67dc78-7faa-4d41-aabd-67707b374255&
binding_message=W4SCT&
login_hint=+1-310-123-4567
```

### 2. Authentication Request Response

```json
{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1",
  "expires_in": 120,
  "interval": 5
}
```

### 3. Token Request (Polling)

```
POST /token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:openid:params:grant-type:ciba&
auth_req_id=1c266114-a1be-4252-8ad1-04986c5b9ac1&
client_id=s6BhdRkqt3
```

## 通知モード

### Poll Mode

クライアントが定期的にトークンエンドポイントをポーリング。

```
interval: 5秒ごとにポーリング
```

### Push Mode

認証完了時にクライアントに通知を送信。

```json
{
  "notification_endpoint": "https://client.example.com/ciba-notification",
  "client_notification_token": "8d67dc78-7faa-4d41-aabd-67707b374255"
}
```

### Ping Mode

認証完了を通知し、クライアントがトークンを取得。

---

## 次のステップ

- [拡張機能層トップに戻る](./ai-30-extensions.md)
- [他の拡張モジュール](./ai-30-extensions.md#概要)

---

**情報源**:
- `libs/idp-server-core-extension-ciba/`
- [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)

**最終更新**: 2025-10-12
