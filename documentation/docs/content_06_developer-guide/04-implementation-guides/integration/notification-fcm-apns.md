# プッシュ通知 (FCM/APNs)

## 📍 このドキュメントの位置づけ

**対象読者**: プッシュ通知（FCM/APNs）の実装詳細を理解したい開発者

**このドキュメントで学べること**:
- FCM (Firebase Cloud Messaging) の実装詳細
- APNs (Apple Push Notification service) の実装詳細
- AuthenticationDeviceNotifier プラグインの実装方法
- 通知テンプレート管理
- JWT トークンキャッシュ戦略（APNs）
- エラーハンドリングとリトライ

**前提知識**:
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md)の理解
- [how-to-12: CIBA Flow (FIDO-UAF)](../../content_05_how-to/how-to-12-ciba-flow-fido-uaf.md)の理解
- FCM/APNsの基礎知識

---

## 🏗️ プッシュ通知アーキテクチャ

idp-serverは、**AuthenticationDeviceNotifier**プラグインインターフェースでプッシュ通知を実装します。

### 通知フロー

```
┌─────────────────────────────────────────────────────────────┐
│ 1. CIBA認証リクエスト受信                                    │
│    - auth_req_id 発行                                        │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. AuthenticationDeviceNotifier 選択                         │
│    - NotificationChannel に基づいて選択                      │
│    - fcm / apns / email / sms                                │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. プッシュ通知送信                                          │
│    - FCM: Firebase Admin SDK                                 │
│    - APNs: HTTP/2 APIs + JWT認証                             │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. ユーザー承認                                              │
│    - モバイルアプリでプッシュ通知受信                         │
│    - 認証処理（FIDO-UAF等）                                  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. トークン発行                                              │
│    - Poll/Ping方式でトークン取得                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔌 AuthenticationDeviceNotifier インターフェース

すべてのプッシュ通知実装は、このインターフェースを実装します。

```java
public interface AuthenticationDeviceNotifier {

  /**
   * 通知チャネル名を返す
   */
  NotificationChannel chanel();

  /**
   * プッシュ通知を送信
   *
   * @param tenant テナント情報
   * @param device 認証デバイス（通知トークンを含む）
   * @param configuration 認証設定（FCM/APNs設定を含む）
   * @return 通知結果
   */
  NotificationResult notify(
      Tenant tenant,
      AuthenticationDevice device,
      AuthenticationExecutionConfig configuration);
}
```

**参考実装**: [AuthenticationDeviceNotifier.java:25](../../../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/device/AuthenticationDeviceNotifier.java#L25)

### NotificationResult

通知結果を表すクラスです。

```java
public class NotificationResult {
  boolean success;
  String channel;
  Map<String, Object> data;
  String errorMessage;

  public static NotificationResult success(String channel, Map<String, Object> data) {
    return new NotificationResult(true, channel, data, null);
  }

  public static NotificationResult failure(String channel, String errorMessage) {
    return new NotificationResult(false, channel, Map.of(), errorMessage);
  }

  public boolean isSuccess() {
    return success;
  }

  public boolean isFailure() {
    return !success;
  }
}
```

**参考実装**: [NotificationResult.java:21](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/notification/NotificationResult.java#L21)

### NotificationTemplate

通知テンプレートを表すクラスです。

```java
public class NotificationTemplate implements JsonReadable {
  String sender;
  String title;
  String body;

  public String optSender(String defaultValue) {
    if (sender == null) {
      return defaultValue;
    }
    return sender;
  }

  public String optTitle(String defaultValue) {
    if (title == null) {
      return defaultValue;
    }
    return title;
  }

  public String optBody(String defaultValue) {
    if (body == null) {
      return defaultValue;
    }
    return body;
  }
}
```

**参考実装**: [NotificationTemplate.java:21](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/notification/NotificationTemplate.java#L21)

---

## 📱 FCM実装

### FcmNotifier

Firebase Admin SDK を使用してプッシュ通知を送信します。

```java
public class FcmNotifier implements AuthenticationDeviceNotifier {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, FirebaseMessaging> cache = new ConcurrentHashMap<>();

  @Override
  public NotificationChannel chanel() {
    return new NotificationChannel("fcm");
  }

  @Override
  public NotificationResult notify(
      Tenant tenant,
      AuthenticationDevice device,
      AuthenticationExecutionConfig configuration) {

    try {
      // 1. 通知トークンの存在確認
      if (!device.hasNotificationToken()) {
        return NotificationResult.failure("fcm", "Device has no notification token");
      }

      // 2. FCM設定の取得
      Object fcmConfigData = configuration.details().get("fcm");
      if (fcmConfigData == null) {
        return NotificationResult.failure("fcm", "FCM configuration not found");
      }

      FcmConfiguration fcmConfiguration =
          jsonConverter.read(fcmConfigData, FcmConfiguration.class);

      // 3. FirebaseMessaging インスタンス取得（キャッシュ）
      FirebaseMessaging firebaseMessaging =
          getOrInitFirebaseMessaging(tenant, fcmConfiguration);

      // 4. 通知テンプレート取得
      NotificationTemplate notificationTemplate = fcmConfiguration.findTemplate("default");
      String notificationToken = device.notificationToken().value();

      // 5. メッセージ構築
      Message message =
          Message.builder()
              .setToken(notificationToken)
              .setAndroidConfig(
                  AndroidConfig.builder()
                      .setPriority(AndroidConfig.Priority.HIGH)
                      .putData("sender", notificationTemplate.optSender(tenant.identifierValue()))
                      .putData("title", notificationTemplate.optTitle("Transaction Authentication"))
                      .putData("body", notificationTemplate.optBody("Please approve the transaction."))
                      .build())
              .setApnsConfig(
                  ApnsConfig.builder()
                      .putHeader("apns-priority", "10")
                      .putCustomData("sender", notificationTemplate.optSender(tenant.identifierValue()))
                      .putCustomData("title", notificationTemplate.optTitle("Transaction Authentication"))
                      .putCustomData("body", notificationTemplate.optBody("Please approve the transaction."))
                      .setAps(Aps.builder().setContentAvailable(true).build())
                      .build())
              .build();

      // 6. 送信
      String result = firebaseMessaging.send(message);

      return NotificationResult.success("fcm", Map.of("result", result));

    } catch (Exception e) {
      return NotificationResult.failure("fcm", e.getMessage());
    }
  }

  FirebaseMessaging getOrInitFirebaseMessaging(Tenant tenant, FcmConfiguration fcmConfiguration) {
    return cache.computeIfAbsent(
        tenant.identifierValue(),
        (key) -> {
          try {
            String credential = fcmConfiguration.credential();
            FirebaseOptions options =
                FirebaseOptions.builder()
                    .setCredentials(
                        GoogleCredentials.fromStream(
                            new ByteArrayInputStream(credential.getBytes())))
                    .build();

            FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, tenant.identifierValue());
            return FirebaseMessaging.getInstance(firebaseApp);

          } catch (IOException e) {
            throw new FcmRuntimeException(e);
          }
        });
  }
}
```

**参考実装**: [FcmNotifier.java:37](../../../../../libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/notification/push/fcm/FcmNotifier.java#L37)

### FCM設定

```json
{
  "fcm": {
    "credential": "{Firebase Admin SDK JSON}",
    "templates": {
      "default": {
        "sender": "MyBank",
        "title": "Transaction Authentication",
        "body": "Please approve the transaction to continue."
      }
    }
  }
}
```

**credential**: Firebase Admin SDK のサービスアカウントキー（JSON形式）

### 重要なポイント

#### 1. テナントごとのFirebaseApp

```java
// ✅ テナントごとに FirebaseApp を初期化
FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, tenant.identifierValue());
```

**理由**: マルチテナント環境で、テナントごとに異なるFirebaseプロジェクトを使用するため

#### 2. Android / iOS 両対応

```java
Message message = Message.builder()
    .setToken(notificationToken)
    .setAndroidConfig(...)  // Android向け設定
    .setApnsConfig(...)     // iOS向け設定
    .build();
```

FCMは**Android と iOS の両方**に対応しています。

#### 3. キャッシュによるパフォーマンス最適化

```java
Map<String, FirebaseMessaging> cache = new ConcurrentHashMap<>();

FirebaseMessaging getOrInitFirebaseMessaging(...) {
  return cache.computeIfAbsent(tenant.identifierValue(), ...);
}
```

---

## 🍎 APNs実装

### ApnsNotifier

APNs HTTP/2 APIを使用してプッシュ通知を送信します。

```java
public class ApnsNotifier implements AuthenticationDeviceNotifier {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, JwtTokenCache> jwtTokenCache = new ConcurrentHashMap<>();
  HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
  JsonWebSignatureFactory jwsFactory = new JsonWebSignatureFactory();

  private static final String PRODUCTION_URL = "https://api.push.apple.com";
  private static final String DEVELOPMENT_URL = "https://api.sandbox.push.apple.com";
  private static final long TOKEN_DURATION_SECONDS = 3600; // 1時間

  @Override
  public NotificationChannel chanel() {
    return new NotificationChannel("apns");
  }

  @Override
  public NotificationResult notify(
      Tenant tenant,
      AuthenticationDevice device,
      AuthenticationExecutionConfig configuration) {

    try {
      // 1. 通知トークンの存在確認
      if (!device.hasNotificationToken()) {
        return NotificationResult.failure("apns", "Device has no notification token");
      }

      // 2. APNs設定の取得
      Object apnsConfigData = configuration.details().get("apns");
      if (apnsConfigData == null) {
        return NotificationResult.failure("apns", "APNs configuration not found");
      }

      ApnsConfiguration apnsConfiguration =
          jsonConverter.read(apnsConfigData, ApnsConfiguration.class);

      // 3. JWT トークン取得（キャッシュまたは新規作成）
      String jwtToken = getOrCreateJwtToken(tenant, apnsConfiguration);

      // 4. 通知ペイロード作成
      NotificationTemplate notificationTemplate = apnsConfiguration.findTemplate("default");
      String payload = createApnsPayload(notificationTemplate, tenant);

      // 5. APNs HTTP/2 リクエスト
      String notificationToken = device.notificationToken().value();
      String apnsUrl =
          (apnsConfiguration.isProduction() ? PRODUCTION_URL : DEVELOPMENT_URL)
              + "/3/device/"
              + notificationToken;

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(apnsUrl))
              .header("Authorization", "bearer " + jwtToken)
              .header("apns-topic", apnsConfiguration.bundleId())
              .header("apns-priority", "10")
              .header("apns-push-type", "alert")
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build();

      // 6. 送信
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String apnsId = response.headers().firstValue("apns-id").orElse("unknown");

      // 7. レスポンス処理
      if (response.statusCode() == 200) {
        return NotificationResult.success("apns", Map.of("apns-id", apnsId));
      } else {
        String errorMessage = handleApnsError(response, apnsId, tenant);
        return NotificationResult.failure("apns", errorMessage);
      }

    } catch (Exception e) {
      return NotificationResult.failure("apns", e.getMessage());
    }
  }
}
```

**参考実装**: [ApnsNotifier.java:42](../../../../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java#L42)

### APNs設定

```json
{
  "apns": {
    "team_id": "YOUR_TEAM_ID",
    "key_id": "YOUR_KEY_ID",
    "key_content": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
    "bundle_id": "com.example.app",
    "is_production": false,
    "templates": {
      "default": {
        "sender": "MyBank",
        "title": "Transaction Authentication",
        "body": "Please approve the transaction to continue."
      }
    }
  }
}
```

**設定パラメータ**:
- `team_id`: Apple Developer Team ID
- `key_id`: APNs認証キーID（.p8ファイルのKey ID）
- `key_content`: 秘密鍵（PEM形式）
- `bundle_id`: アプリのBundle Identifier
- `is_production`: 本番環境 (true) / 開発環境 (false)

### APNsペイロード作成

```java
String createApnsPayload(NotificationTemplate template, Tenant tenant) {
  try {
    Map<String, Object> aps = new HashMap<>();
    Map<String, String> alert = new HashMap<>();

    String title = template.optTitle("Transaction Authentication");
    String body = template.optBody("Please approve the transaction to continue.");
    String sender = template.optSender(tenant.identifierValue());

    alert.put("title", title);
    alert.put("body", body);
    aps.put("alert", alert);

    Map<String, Object> payload = new HashMap<>();
    payload.put("aps", aps);
    payload.put("sender", sender);  // カスタムデータ

    return jsonConverter.write(payload);
  } catch (Exception e) {
    throw new ApnsRuntimeException("Failed to create APNs payload", e);
  }
}
```

**ペイロード例**:
```json
{
  "aps": {
    "alert": {
      "title": "Transaction Authentication",
      "body": "Please approve the transaction to continue."
    }
  },
  "sender": "tenant-id-12345678"
}
```

### JWT トークン生成とキャッシュ

APNsは、**JWT トークンによる認証**を使用します。トークンは1時間有効で、キャッシュにより再利用します。

```java
String getOrCreateJwtToken(Tenant tenant, ApnsConfiguration config) {
  String cacheKey = createCacheKey(tenant);
  JwtTokenCache cachedToken = jwtTokenCache.get(cacheKey);

  // キャッシュがあり、まだ有効な場合は再利用
  if (cachedToken != null && !cachedToken.shouldRefresh()) {
    return cachedToken.token();
  }

  // 新しいJWTトークン作成
  try {
    LocalDateTime now = SystemDateTime.now();
    LocalDateTime expiresAt = now.plusSeconds(TOKEN_DURATION_SECONDS);

    // JWT Claims
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", config.teamId());
    claims.put("iat", SystemDateTime.toEpochSecond(now));

    // JWT Headers
    Map<String, Object> customHeaders = new HashMap<>();
    customHeaders.put("kid", config.keyId());

    // ES256 署名
    JsonWebSignature jws =
        jwsFactory.createWithAsymmetricKeyForPem(claims, customHeaders, config.keyContent());
    String token = jws.serialize();

    // キャッシュに保存
    jwtTokenCache.put(cacheKey, new JwtTokenCache(token, expiresAt));

    return token;

  } catch (Exception e) {
    throw new ApnsRuntimeException("Failed to create JWT token", e);
  }
}

String createCacheKey(Tenant tenant) {
  return "jwt-" + tenant.identifierValue();
}
```

**JWT トークンの仕様**:
- **アルゴリズム**: ES256（ECDSA with SHA-256）
- **Header**: `kid` に Key ID を設定
- **Claims**:
  - `iss`: Team ID
  - `iat`: 発行時刻（Unix time）
- **有効期限**: 1時間（推奨）

### APNsエラーハンドリング

```java
String handleApnsError(HttpResponse<String> response, String apnsId, Tenant tenant) {
  try {
    int statusCode = response.statusCode();
    String responseBody = response.body();

    // JSONレスポンスをパース
    if (Objects.nonNull(responseBody) && !responseBody.isEmpty()) {
      JsonNodeWrapper errorJson = JsonNodeWrapper.fromString(responseBody);
      String reason = errorJson.getValueOrEmptyAsString("reason");

      log.warn(
          "APNs notification failed - Status: {}, Reason: {}, APNs-ID: {}",
          statusCode, reason, apnsId);

      // 特定エラーの処理
      switch (reason) {
        case "BadDeviceToken" -> log.warn("Invalid device token");
        case "TopicDisallowed" -> log.warn("Topic not allowed");
        case "ExpiredProviderToken" -> {
          log.warn("JWT token expired, clearing cache");
          jwtTokenCache.remove(createCacheKey(tenant));
        }
      }

      return "Status: " + statusCode + ", Reason: " + reason;
    }

    return "Status: " + statusCode + ", APNs-ID: " + apnsId;

  } catch (Exception e) {
    log.error("Error parsing APNs error response: {}", e.getMessage());
    return "Error parsing APNs response: " + e.getMessage();
  }
}
```

**主なエラー理由**:
- `BadDeviceToken`: 無効なデバイストークン
- `TopicDisallowed`: Bundle IDが許可されていない
- `ExpiredProviderToken`: JWT トークンの有効期限切れ
- `DeviceTokenNotForTopic`: デバイストークンとBundle IDの不一致

---

## 📋 実装チェックリスト

### FCM実装

- [ ] **Firebase Admin SDK依存関係**:
  ```groovy
  implementation 'com.google.firebase:firebase-admin:9.2.0'
  ```

- [ ] **FCM Configuration**:
  - [ ] Firebase Admin SDK JSON を取得
  - [ ] `credential` フィールドに設定

- [ ] **通知テンプレート**:
  - [ ] `sender`, `title`, `body` を設定
  - [ ] デフォルト値を適切に設定

- [ ] **FirebaseApp初期化**:
  - [ ] テナントごとに FirebaseApp を初期化
  - [ ] キャッシュで再利用

- [ ] **プラグイン登録**:
  ```
  META-INF/services/org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifier
  org.idp.server.notification.push.fcm.FcmNotifier
  ```

### APNs実装

- [ ] **APNs認証キー取得**:
  - [ ] Apple Developer PortalでKey作成（.p8ファイル）
  - [ ] Team ID, Key ID, 秘密鍵を取得

- [ ] **APNs Configuration**:
  - [ ] `team_id`, `key_id`, `key_content` を設定
  - [ ] `bundle_id` を設定
  - [ ] `is_production` を適切に設定

- [ ] **JWT トークン生成**:
  - [ ] ES256 署名
  - [ ] Header に `kid` 設定
  - [ ] Claims に `iss` (Team ID), `iat` 設定

- [ ] **トークンキャッシュ**:
  - [ ] 1時間有効
  - [ ] テナントごとにキャッシュ
  - [ ] `ExpiredProviderToken` エラー時にキャッシュクリア

- [ ] **HTTP/2 リクエスト**:
  - [ ] Authorization: bearer `<jwt_token>`
  - [ ] apns-topic: `<bundle_id>`
  - [ ] apns-priority: 10（即時配信）
  - [ ] apns-push-type: alert

- [ ] **エラーハンドリング**:
  - [ ] ステータスコード確認（200以外はエラー）
  - [ ] `reason` フィールドからエラー理由取得
  - [ ] 適切なログ出力

- [ ] **プラグイン登録**:
  ```
  META-INF/services/org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifier
  org.idp.server.notification.push.apns.ApnsNotifier
  ```

---

## 🧪 テスト実装例

### FCM通知テスト

```java
@Test
void testFcmNotification() {
  // 1. テナント作成
  Tenant tenant = new Tenant(new TenantIdentifier("tenant-123"), ...);

  // 2. デバイス作成（通知トークン含む）
  AuthenticationDevice device = AuthenticationDevice.builder()
      .notificationToken(new NotificationToken("fcm-token-xyz"))
      .build();

  // 3. FCM設定
  Map<String, Object> fcmConfig = Map.of(
      "credential", "{Firebase Admin SDK JSON}",
      "templates", Map.of(
          "default", Map.of(
              "title", "Test Notification",
              "body", "Test Body"
          )
      )
  );

  AuthenticationExecutionConfig config = AuthenticationExecutionConfig.builder()
      .details(Map.of("fcm", fcmConfig))
      .build();

  // 4. 通知送信
  FcmNotifier notifier = new FcmNotifier();
  NotificationResult result = notifier.notify(tenant, device, config);

  // 5. 検証
  assertTrue(result.isSuccess());
  assertThat(result.channel()).isEqualTo("fcm");
}
```

### APNs通知テスト

```java
@Test
void testApnsNotification() {
  // 1. テナント作成
  Tenant tenant = new Tenant(new TenantIdentifier("tenant-456"), ...);

  // 2. デバイス作成
  AuthenticationDevice device = AuthenticationDevice.builder()
      .notificationToken(new NotificationToken("apns-token-abc"))
      .build();

  // 3. APNs設定
  Map<String, Object> apnsConfig = Map.of(
      "team_id", "YOUR_TEAM_ID",
      "key_id", "YOUR_KEY_ID",
      "key_content", "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
      "bundle_id", "com.example.app",
      "is_production", false,
      "templates", Map.of(
          "default", Map.of(
              "title", "Test Notification",
              "body", "Test Body"
          )
      )
  );

  AuthenticationExecutionConfig config = AuthenticationExecutionConfig.builder()
      .details(Map.of("apns", apnsConfig))
      .build();

  // 4. 通知送信
  ApnsNotifier notifier = new ApnsNotifier();
  NotificationResult result = notifier.notify(tenant, device, config);

  // 5. 検証
  assertTrue(result.isSuccess());
  assertThat(result.channel()).isEqualTo("apns");
  assertThat(result.data()).containsKey("apns-id");
}
```

---

## 🚨 よくある間違い

### 1. FCM: FirebaseApp の重複初期化

```java
// ❌ 誤り: 毎回 FirebaseApp を初期化（エラーになる）
FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, "app-name");

// ✅ 正しい: キャッシュで再利用
FirebaseMessaging getOrInitFirebaseMessaging(...) {
  return cache.computeIfAbsent(tenant.identifierValue(), ...);
}
```

### 2. APNs: JWT トークンの再作成

```java
// ❌ 誤り: 毎回 JWT トークンを作成（パフォーマンス低下）
String jwtToken = createJwtToken(config);

// ✅ 正しい: キャッシュで1時間再利用
String jwtToken = getOrCreateJwtToken(tenant, config);
```

### 3. APNs: 環境URL の間違い

```java
// ❌ 誤り: 本番環境で開発URLを使用
String apnsUrl = "https://api.sandbox.push.apple.com/3/device/" + token;

// ✅ 正しい: 設定に基づいて切り替え
String apnsUrl = (apnsConfiguration.isProduction()
    ? "https://api.push.apple.com"
    : "https://api.sandbox.push.apple.com") + "/3/device/" + token;
```

### 4. 通知トークンの存在確認忘れ

```java
// ❌ 誤り: 通知トークンなしで送信（エラー）
String notificationToken = device.notificationToken().value();  // NullPointerException

// ✅ 正しい: 存在確認
if (!device.hasNotificationToken()) {
  return NotificationResult.failure("apns", "Device has no notification token");
}
String notificationToken = device.notificationToken().value();
```

### 5. APNs: ExpiredProviderToken エラー時のキャッシュクリア忘れ

```java
// ❌ 誤り: エラーをログ出力するだけ
case "ExpiredProviderToken" -> log.warn("JWT token expired");

// ✅ 正しい: キャッシュをクリアして次回再作成
case "ExpiredProviderToken" -> {
  log.warn("JWT token expired, clearing cache");
  jwtTokenCache.remove(createCacheKey(tenant));
}
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [how-to-12: CIBA Flow (FIDO-UAF)](../../content_05_how-to/how-to-12-ciba-flow-fido-uaf.md) - プッシュ通知を使用するフロー
- [how-to-08: MFA設定](../../content_05_how-to/how-to-08-mfa-setup.md) - デバイス登録

**実装詳細**:
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md) - プラグインシステムの詳細
- [03-application-plane/06-ciba-flow.md](../03-application-plane/06-ciba-flow.md) - CIBAフロー

**参考実装クラス**:
- [AuthenticationDeviceNotifier.java](../../../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/device/AuthenticationDeviceNotifier.java)
- [FcmNotifier.java](../../../../../libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/notification/push/fcm/FcmNotifier.java)
- [ApnsNotifier.java](../../../../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java)
- [NotificationTemplate.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/notification/NotificationTemplate.java)
- [NotificationResult.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/notification/NotificationResult.java)

**外部リソース**:
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- [Apple Push Notification service](https://developer.apple.com/documentation/usernotifications)
- [APNs Provider Authentication Token](https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/establishing_a_token-based_connection_to_apns)

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐⭐ (中級)
