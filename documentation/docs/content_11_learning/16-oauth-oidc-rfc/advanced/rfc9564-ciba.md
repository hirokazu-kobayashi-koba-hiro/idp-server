# OpenID Connect CIBA（Client-Initiated Backchannel Authentication）

CIBA は、ユーザーがクライアントデバイスとは別のデバイス（認証デバイス）で認証を行う「分離型」認証フローを定義した仕様です。

---

## 第1部: 概要編

### CIBA とは？

CIBA（Client-Initiated Backchannel Authentication）は、従来のリダイレクトベースの認証とは異なり、**バックチャネル**を通じて認証を開始するフローです。

```
従来の認証フロー（リダイレクト）:
  ┌────────┐     redirect     ┌────────┐
  │ Client │ ───────────────► │   OP   │
  │        │ ◄─────────────── │        │
  └────────┘     redirect     └────────┘
         │                          │
         └──── 同一デバイス ────────┘

CIBA（分離型）:
  ┌────────────────┐   backchannel   ┌────────┐
  │ Consumption    │ ───────────────► │   OP   │
  │    Device      │                  │        │
  └────────────────┘                  └────────┘
                                          │
                                          │ push
                                          ▼
                                   ┌────────────────┐
                                   │ Authentication │
                                   │    Device      │
                                   │  （スマホ等）   │
                                   └────────────────┘
```

### ユースケース

| ユースケース | 説明 |
|-------------|------|
| コールセンター | オペレーターが顧客を認証 |
| POS 端末 | 店舗端末での決済認証 |
| ATM | 銀行 ATM での本人確認 |
| IoT デバイス | 画面のないデバイスでの認証 |
| スマートスピーカー | 音声デバイスでの認証 |

### 3つのモード

| モード | 説明 |
|--------|------|
| Poll | クライアントが定期的にポーリング |
| Ping | OP がクライアントにコールバック通知 |
| Push | OP がトークンをクライアントにプッシュ |

---

## 第2部: 詳細編

### 認証リクエスト

```http
POST /bc-authorize HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

scope=openid profile
&client_notification_token=8d67dc78-7faa-4d41-aabd-67707b374255
&login_hint=user@example.com
&binding_message=Transaction: 100 EUR to DE89...
&requested_expiry=120
```

### リクエストパラメータ

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `scope` | ✅ | 要求するスコープ（openid 必須） |
| `client_notification_token` | △ | Ping/Push モードで必須 |
| `login_hint` | △ | ユーザーのヒント（メール等） |
| `id_token_hint` | △ | 既知の ID トークン |
| `login_hint_token` | △ | 署名付きヒントトークン |
| `binding_message` | △ | 認証デバイスに表示するメッセージ |
| `user_code` | △ | ユーザーが入力するコード |
| `requested_expiry` | △ | 認証リクエストの有効期限（秒） |
| `acr_values` | △ | 要求する認証レベル |

**注意**: `login_hint`、`id_token_hint`、`login_hint_token` のいずれか 1 つは必須。

### 認証レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1",
  "expires_in": 120,
  "interval": 5
}
```

| フィールド | 説明 |
|-----------|------|
| `auth_req_id` | 認証リクエスト ID |
| `expires_in` | 有効期限（秒） |
| `interval` | ポーリング間隔（Poll モード） |

### Poll モード

クライアントが定期的にトークンエンドポイントをポーリングします。

```
Poll モードのフロー:

  ┌────────┐   auth request   ┌────────┐
  │ Client │ ───────────────► │   OP   │
  │        │ ◄─────────────── │        │
  │        │   auth_req_id    │        │
  │        │                  │        │
  │        │   poll (pending) │        │ ──► ユーザーに通知
  │        │ ───────────────► │        │
  │        │ ◄─────────────── │        │
  │        │                  │        │
  │        │   poll (pending) │        │ ◄── ユーザーが認証
  │        │ ───────────────► │        │
  │        │ ◄─────────────── │        │
  │        │                  │        │
  │        │   poll           │        │
  │        │ ───────────────► │        │
  │        │ ◄─────────────── │        │
  └────────┘   access_token   └────────┘
```

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=urn:openid:params:grant-type:ciba
&auth_req_id=1c266114-a1be-4252-8ad1-04986c5b9ac1
```

#### 認証待ち

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "authorization_pending",
  "error_description": "The authorization request is still pending"
}
```

#### ポーリング過多

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "slow_down",
  "error_description": "You are polling too quickly"
}
```

#### 成功

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

### Ping モード

OP がクライアントに通知し、クライアントがトークンを取得します。

```
Ping モードのフロー:

  ┌────────┐   auth request   ┌────────┐
  │ Client │ ───────────────► │   OP   │
  │        │ ◄─────────────── │        │
  │        │   auth_req_id    │        │
  │        │                  │        │
  │        │                  │        │ ──► ユーザーに通知
  │        │                  │        │ ◄── ユーザーが認証
  │        │                  │        │
  │        │ ◄─────────────── │        │
  │        │   callback ping  │        │
  │        │                  │        │
  │        │   token request  │        │
  │        │ ───────────────► │        │
  │        │ ◄─────────────── │        │
  └────────┘   access_token   └────────┘
```

クライアント通知エンドポイントへのリクエスト:

```http
POST /ciba/callback HTTP/1.1
Host: client.example.com
Content-Type: application/json
Authorization: Bearer 8d67dc78-7faa-4d41-aabd-67707b374255

{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1"
}
```

### Push モード

OP がトークンを直接クライアントにプッシュします。

```
Push モードのフロー:

  ┌────────┐   auth request   ┌────────┐
  │ Client │ ───────────────► │   OP   │
  │        │ ◄─────────────── │        │
  │        │   auth_req_id    │        │
  │        │                  │        │
  │        │                  │        │ ──► ユーザーに通知
  │        │                  │        │ ◄── ユーザーが認証
  │        │                  │        │
  │        │ ◄─────────────── │        │
  │        │   tokens (push)  │        │
  └────────┘                  └────────┘
```

クライアント通知エンドポイントへのリクエスト:

```http
POST /ciba/callback HTTP/1.1
Host: client.example.com
Content-Type: application/json
Authorization: Bearer 8d67dc78-7faa-4d41-aabd-67707b374255

{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1",
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://auth.example.com",
  "backchannel_authentication_endpoint": "https://auth.example.com/bc-authorize",
  "backchannel_token_delivery_modes_supported": ["poll", "ping", "push"],
  "backchannel_authentication_request_signing_alg_values_supported": ["PS256", "ES256"],
  "backchannel_user_code_parameter_supported": true
}
```

### クライアント登録

```json
{
  "client_id": "s6BhdRkqt3",
  "backchannel_token_delivery_mode": "ping",
  "backchannel_client_notification_endpoint": "https://client.example.com/ciba/callback",
  "backchannel_authentication_request_signing_alg": "PS256",
  "backchannel_user_code_parameter": true
}
```

### 実装例

#### Java（クライアント側）

```java
@Service
public class CIBAClient {

    private final String cibaEndpoint;
    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final RestTemplate restTemplate;

    public CIBAAuthResponse initiateAuth(CIBAAuthRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("scope", request.getScope());
        body.add("login_hint", request.getLoginHint());

        if (request.getBindingMessage() != null) {
            body.add("binding_message", request.getBindingMessage());
        }

        if (request.getClientNotificationToken() != null) {
            body.add("client_notification_token", request.getClientNotificationToken());
        }

        if (request.getRequestedExpiry() != null) {
            body.add("requested_expiry", request.getRequestedExpiry().toString());
        }

        ResponseEntity<CIBAAuthResponse> response = restTemplate.postForEntity(
            cibaEndpoint,
            new HttpEntity<>(body, headers),
            CIBAAuthResponse.class
        );

        return response.getBody();
    }

    // Poll モード用
    public TokenResponse pollForToken(String authReqId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "urn:openid:params:grant-type:ciba");
        body.add("auth_req_id", authReqId);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                tokenEndpoint,
                new HttpEntity<>(body, headers),
                TokenResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            CIBAError error = parseError(e.getResponseBodyAsString());
            if ("authorization_pending".equals(error.getError())) {
                return null; // まだ認証中
            }
            throw e;
        }
    }

    // Poll モードのポーリングループ
    public TokenResponse waitForAuth(String authReqId, int interval, int expiresIn) {
        long deadline = System.currentTimeMillis() + (expiresIn * 1000L);

        while (System.currentTimeMillis() < deadline) {
            TokenResponse token = pollForToken(authReqId);
            if (token != null) {
                return token;
            }

            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
        }

        throw new TimeoutException("CIBA authentication timed out");
    }
}
```

#### Java（OP 側 - Ping/Push 通知）

```java
@Service
public class CIBANotificationService {

    private final RestTemplate restTemplate;

    // Ping モード
    public void sendPingNotification(CIBASession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(session.getClientNotificationToken());

        Map<String, Object> body = Map.of(
            "auth_req_id", session.getAuthReqId()
        );

        restTemplate.postForEntity(
            session.getClientNotificationEndpoint(),
            new HttpEntity<>(body, headers),
            Void.class
        );
    }

    // Push モード
    public void sendPushNotification(CIBASession session, TokenSet tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(session.getClientNotificationToken());

        Map<String, Object> body = new HashMap<>();
        body.put("auth_req_id", session.getAuthReqId());
        body.put("access_token", tokens.getAccessToken());
        body.put("token_type", "Bearer");
        body.put("expires_in", tokens.getExpiresIn());
        if (tokens.getRefreshToken() != null) {
            body.put("refresh_token", tokens.getRefreshToken());
        }
        if (tokens.getIdToken() != null) {
            body.put("id_token", tokens.getIdToken());
        }

        restTemplate.postForEntity(
            session.getClientNotificationEndpoint(),
            new HttpEntity<>(body, headers),
            Void.class
        );
    }
}
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| クライアント認証 | 機密クライアント必須 |
| binding_message | 取引内容を明示（フィッシング対策） |
| user_code | 高リスク操作では必須 |
| 有効期限 | 短く設定（2-5分） |
| 通知トークン | ランダムで十分な長さ |
| HTTPS | すべての通信で必須 |

### エラーコード

| エラー | 説明 |
|--------|------|
| `authorization_pending` | 認証待ち（ポーリング継続） |
| `slow_down` | ポーリング過多 |
| `expired_token` | auth_req_id の有効期限切れ |
| `access_denied` | ユーザーが拒否 |
| `invalid_grant` | auth_req_id が無効 |

---

## 参考リンク

- [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
- [FAPI-CIBA Profile](https://openid.net/specs/openid-financial-api-ciba-1_0.html)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
