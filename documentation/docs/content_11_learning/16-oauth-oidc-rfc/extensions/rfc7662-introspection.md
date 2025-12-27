# RFC 7662: トークンイントロスペクション

RFC 7662 は、リソースサーバーがアクセストークンの状態を認可サーバーに問い合わせるための仕様です。このドキュメントでは、トークンイントロスペクションの仕組みと実装方法を解説します。

---

## 第1部: 概要編

### トークンイントロスペクションとは何か？

トークンイントロスペクション（Token Introspection）は、トークンの**現在の状態**を認可サーバーに問い合わせる仕組みです。

トークンが有効かどうか、どのような権限を持っているかなどの情報を取得できます。

### なぜトークンイントロスペクションが必要なのか？

JWT（自己完結型トークン）を使えばリソースサーバー単独でトークンを検証できますが、以下のケースでは不十分です。

| 課題 | 説明 |
|------|------|
| トークン失効 | JWT は有効期限前でも失効させたい場合がある |
| リアルタイム検証 | ユーザーの権限変更を即座に反映したい |
| 不透明トークン | JWT ではない参照型トークンの検証 |
| 追加情報の取得 | トークンに含まれない情報（ユーザー詳細等）を取得 |

```
                         トークンが有効期限内でも...

┌─────────────┐                                    ┌─────────────┐
│   Admin     │  ユーザーの権限を剥奪              │   認可      │
│  Console    │ ─────────────────────────────────► │  サーバー   │
└─────────────┘                                    └─────────────┘
                                                          │
                                                          │ トークン失効
                                                          ▼
┌─────────────┐   アクセストークン (JWT)           ┌─────────────┐
│  クライアント │ ─────────────────────────────────►│  リソース   │
└─────────────┘                                    │  サーバー   │
                                                   └──────┬──────┘
                                                          │
                                                          │ イントロスペクション
                                                          ▼
                                                   ┌─────────────┐
                                                   │  認可       │
                                                   │  サーバー   │
                                                   │ active:false│
                                                   └─────────────┘
```

### 2つのトークン検証方式の比較

| 方式 | 説明 | メリット | デメリット |
|------|------|----------|------------|
| ローカル検証 | JWT を署名検証 | 高速、認可サーバーへの依存なし | 失効反映に遅延 |
| イントロスペクション | 認可サーバーに問い合わせ | リアルタイム検証、失効対応 | レイテンシ、認可サーバー負荷 |

多くの実装では、両方を組み合わせて使用します。

---

## 第2部: 詳細編

### イントロスペクションエンドポイント

#### リクエスト

```http
POST /introspect HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

token=mF_9.B5f-4.1JqM
&token_type_hint=access_token
```

| パラメータ | 必須 | 説明 |
|------------|------|------|
| `token` | ✅ | 検証対象のトークン |
| `token_type_hint` | △ | トークン種別のヒント（`access_token`, `refresh_token`） |

**重要**: イントロスペクションエンドポイントは認証が必要です。リソースサーバーは認可サーバーに事前登録されている必要があります。

#### レスポンス（有効なトークン）

```json
{
  "active": true,
  "scope": "read write",
  "client_id": "l238j323ds-23ij4",
  "username": "jdoe",
  "token_type": "Bearer",
  "exp": 1704070800,
  "iat": 1704067200,
  "nbf": 1704067200,
  "sub": "Z5O3upPC88QrAjx00dis",
  "aud": "https://api.example.com",
  "iss": "https://auth.example.com"
}
```

| フィールド | 説明 |
|------------|------|
| `active` | トークンが有効かどうか（**必須**） |
| `scope` | 付与されたスコープ |
| `client_id` | トークンを発行されたクライアント |
| `username` | リソースオーナーのユーザー名 |
| `token_type` | トークン種別（`Bearer`, `DPoP` など） |
| `exp` | 有効期限 |
| `iat` | 発行時刻 |
| `nbf` | 有効開始時刻 |
| `sub` | サブジェクト（ユーザー識別子） |
| `aud` | オーディエンス |
| `iss` | 発行者 |

#### レスポンス（無効なトークン）

```json
{
  "active": false
}
```

トークンが無効な場合、`active: false` のみを返します。無効の理由は返しません（セキュリティ上の理由）。

### トークンが無効と判定される条件

以下のいずれかに該当する場合、`active: false` が返されます。

- トークンが存在しない
- トークンの有効期限が切れている
- トークンが明示的に失効されている
- トークンがリクエスト元のリソースサーバー向けではない
- その他の理由でトークンが無効

### DPoP バウンドトークンのイントロスペクション

DPoP を使用している場合、追加の情報が返されます。

```json
{
  "active": true,
  "token_type": "DPoP",
  "cnf": {
    "jkt": "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I"
  }
}
```

`cnf.jkt` は DPoP 公開鍵のサムプリントです。リソースサーバーはこの値を使って DPoP Proof を検証します。

### 実装例

#### リソースサーバー（Java / Spring）

```java
@Service
public class TokenIntrospectionService {
    
    private final WebClient webClient;
    private final String introspectionEndpoint;
    private final String clientId;
    private final String clientSecret;
    
    public IntrospectionResponse introspect(String token) {
        String credentials = Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes());
        
        return webClient.post()
            .uri(introspectionEndpoint)
            .header("Authorization", "Basic " + credentials)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("token", token)
                .with("token_type_hint", "access_token"))
            .retrieve()
            .bodyToMono(IntrospectionResponse.class)
            .block();
    }
}

@Data
public class IntrospectionResponse {
    private boolean active;
    private String scope;
    private String clientId;
    private String username;
    private String tokenType;
    private Long exp;
    private Long iat;
    private String sub;
    private String aud;
    private String iss;
    private Cnf cnf;  // DPoP 用
    
    @Data
    public static class Cnf {
        private String jkt;
    }
}
```

#### 認可サーバー（Spring Authorization Server）

```java
@Configuration
public class IntrospectionConfig {
    
    @Bean
    public OAuth2TokenIntrospectionAuthenticationProvider 
            introspectionAuthenticationProvider(
                RegisteredClientRepository clientRepository,
                OAuth2AuthorizationService authorizationService) {
        return new OAuth2TokenIntrospectionAuthenticationProvider(
            clientRepository,
            authorizationService
        );
    }
}
```

### キャッシュ戦略

イントロスペクションはリクエストごとに認可サーバーへ問い合わせるため、パフォーマンスへの影響があります。以下の戦略を検討してください。

#### 1. ハイブリッド検証

```
1. JWT の署名検証（ローカル）
2. 有効期限チェック（ローカル）
3. 失効チェック（イントロスペクション or Redis）
```

#### 2. キャッシュ付きイントロスペクション

```java
@Service
public class CachedIntrospectionService {
    
    private final Cache<String, IntrospectionResponse> cache;
    private final TokenIntrospectionService introspectionService;
    
    public IntrospectionResponse introspect(String token) {
        String cacheKey = computeHash(token);
        
        IntrospectionResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            // キャッシュの有効期限もチェック
            if (cached.getExp() > Instant.now().getEpochSecond()) {
                return cached;
            }
        }
        
        IntrospectionResponse response = introspectionService.introspect(token);
        
        if (response.isActive()) {
            // 短い TTL でキャッシュ（例: 30秒）
            cache.put(cacheKey, response);
        }
        
        return response;
    }
}
```

**注意**: キャッシュを使用すると失効の反映が遅れます。セキュリティ要件に応じてキャッシュ TTL を調整してください。

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| エンドポイント認証 | 必ずリソースサーバーを認証する |
| HTTPS | すべての通信を TLS で保護 |
| トークン漏洩防止 | リクエストボディでトークンを送信（URL に含めない） |
| レスポンス情報 | 無効トークンの場合は詳細情報を返さない |
| レート制限 | 大量リクエストを制限 |

### イントロスペクション vs JWT 検証

| シナリオ | 推奨方式 |
|----------|----------|
| 高トラフィック、低レイテンシ要件 | JWT ローカル検証 + 定期的失効チェック |
| 即座の失効反映が必要 | イントロスペクション |
| 不透明トークン使用 | イントロスペクション |
| 金融グレードセキュリティ | イントロスペクション（+ DPoP） |
| マイクロサービス間通信 | ハイブリッド（JWT + 短い有効期限） |

---

## 参考リンク

- [RFC 7662 - OAuth 2.0 Token Introspection](https://datatracker.ietf.org/doc/html/rfc7662)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 7009 - OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009)
