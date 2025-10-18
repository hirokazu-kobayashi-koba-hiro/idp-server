# idp-server-notification-fcm-adapter

## モジュール概要

**情報源**: `libs/idp-server-notification-fcm-adapter/`
**確認日**: 2025-10-12

### 責務

Firebase Cloud Messaging (FCM) によるプッシュ通知。

### 主要機能

- **Android/iOS/Web** プッシュ通知
- **通知優先度**: high/normal
- **Data Payload**: カスタムデータ配信
- **トピック配信**: 複数デバイスへの一斉配信

## 実装パターン

**情報源**: [FcmNotifier.java:37-108](../../../libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/notification/push/fcm/FcmNotifier.java#L37-L108)

```java
/**
 * FCM通知実装
 * 確認方法: 実ファイルの37-108行目
 */
public class FcmNotifier implements AuthenticationDeviceNotifier {

  LoggerWrapper log = LoggerWrapper.getLogger(FcmNotifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, FirebaseMessaging> cache = new ConcurrentHashMap<>();  // ✅ テナント別キャッシュ

  @Override
  public NotificationChannel chanel() {
    return new NotificationChannel("fcm");
  }

  @Override
  public NotificationResult notify(
      Tenant tenant,
      AuthenticationDevice device,
      AuthenticationExecutionConfig configuration) {

    // 1. FCM設定取得
    FcmConfiguration fcmConfiguration =
        jsonConverter.read(configuration.details().get("fcm"), FcmConfiguration.class);

    // 2. FirebaseMessaging取得（テナント別キャッシュ）
    FirebaseMessaging firebaseMessaging = getOrInitFirebaseMessaging(tenant, fcmConfiguration);

    // 3. 通知テンプレート・トークン取得
    NotificationTemplate template = fcmConfiguration.findTemplate("default");
    String notificationToken = device.notificationToken().value();

    // 4. メッセージ構築（Android/iOS両対応）
    Message message =
        Message.builder()
            .setToken(notificationToken)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .putData("sender", template.optSender(tenant.identifierValue()))
                    .putData("title", template.optTitle("Transaction Authentication"))
                    .putData("body", template.optBody("Please approve..."))
                    .build())
            .setApnsConfig(
                ApnsConfig.builder()
                    .putHeader("apns-priority", "10")
                    .setAps(Aps.builder().setContentAvailable(true).build())
                    .build())
            .build();

    // 5. 送信
    String result = firebaseMessaging.send(message);
    return NotificationResult.success("fcm", Map.of("result", result));
  }

  // ✅ テナント別FirebaseMessaging初期化・キャッシュ
  FirebaseMessaging getOrInitFirebaseMessaging(Tenant tenant, FcmConfiguration config) {
    return cache.computeIfAbsent(tenant.identifierValue(), (key) -> {
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(
              new ByteArrayInputStream(config.credential().getBytes())))
          .build();
      FirebaseApp app = FirebaseApp.initializeApp(options, tenant.identifierValue());
      return FirebaseMessaging.getInstance(app);
    });
  }
}
```

## 重要ポイント

- ✅ `AuthenticationDeviceNotifier` インターフェース実装
- ✅ テナント別FirebaseMessaging キャッシュ（マルチテナント対応）
- ✅ `NotificationTemplate` による設定駆動
- ✅ Android/iOS両対応のメッセージ構築
- ✅ `NotificationResult` によるステータス管理

## 関連ドキュメント

- [通知・イベント層統合ドキュメント](./ai-50-notification-security-event.md) - FCMを含む全通知モジュール
- [idp-server-notification-apns-adapter](./ai-52-notification-apns.md) - Apple Push Notification
- [idp-server-email-aws-adapter](./ai-53-email-aws.md) - メール送信

---

**情報源**:
- `libs/idp-server-notification-fcm-adapter/`配下の実装コード
- [FcmNotifier.java](../../../libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/notification/push/fcm/FcmNotifier.java)

**最終更新**: 2025-10-12
