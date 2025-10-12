# idp-server-notification-apns-adapter

## モジュール概要

**情報源**: `libs/idp-server-notification-apns-adapter/`
**確認日**: 2025-10-12

### 責務

Apple Push Notification Service (APNS) によるプッシュ通知。

### 主要機能

- **iOS/macOS** プッシュ通知
- **Badge/Sound/Alert** 設定
- **Silent Notification**: バックグラウンド更新
- **Token Authentication**: JWT認証

## 実装パターン

**情報源**: [ApnsNotifier.java:42-134](../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java#L42-L134)

```java
/**
 * APNS通知実装
 * 確認方法: 実ファイルの42-134行目
 */
public class ApnsNotifier implements AuthenticationDeviceNotifier {

  LoggerWrapper log = LoggerWrapper.getLogger(ApnsNotifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, JwtTokenCache> jwtTokenCache = new ConcurrentHashMap<>();  // ✅ JWT認証トークンキャッシュ
  HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

  private static final String PRODUCTION_URL = "https://api.push.apple.com";
  private static final String DEVELOPMENT_URL = "https://api.sandbox.push.apple.com";

  @Override
  public NotificationChannel chanel() {
    return new NotificationChannel("apns");
  }

  @Override
  public NotificationResult notify(
      Tenant tenant,
      AuthenticationDevice device,
      AuthenticationExecutionConfig configuration) {

    // 1. APNS設定取得
    ApnsConfiguration apnsConfiguration =
        jsonConverter.read(configuration.details().get("apns"), ApnsConfiguration.class);

    // 2. JWT認証トークン取得（キャッシュ、1時間有効）
    String jwtToken = getOrCreateJwtToken(tenant, apnsConfiguration);

    // 3. 通知テンプレート取得
    NotificationTemplate template = apnsConfiguration.findTemplate("default");
    String notificationToken = device.notificationToken().value();

    // 4. APNsペイロード構築（JSON）
    String payload = createApnsPayload(template, tenant);

    // 5. HTTP/2リクエスト送信
    String apnsUrl = (apnsConfiguration.isProduction() ? PRODUCTION_URL : DEVELOPMENT_URL)
        + "/3/device/" + notificationToken;

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

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      return NotificationResult.success("apns", Map.of("status", "sent"));
    } else {
      return NotificationResult.failure("apns", "APNs returned: " + response.statusCode());
    }
  }
}
```

## 重要ポイント

- ✅ Java標準`HttpClient`（HTTP/2対応）でAPNs直接通信
- ✅ JWT認証トークンの生成・キャッシュ（1時間有効）
- ✅ Production/Development環境切り替え
- ✅ `NotificationTemplate`による設定駆動

## 関連ドキュメント

- [通知・イベント層統合ドキュメント](./ai-50-notification-security-event.md) - APNSを含む全通知モジュール
- [idp-server-notification-fcm-adapter](./ai-51-notification-fcm.md) - Firebase Cloud Messaging
- [idp-server-email-aws-adapter](./ai-53-email-aws.md) - メール送信

---

**情報源**:
- `libs/idp-server-notification-apns-adapter/`配下の実装コード
- [ApnsNotifier.java](../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java)

**最終更新**: 2025-10-12
