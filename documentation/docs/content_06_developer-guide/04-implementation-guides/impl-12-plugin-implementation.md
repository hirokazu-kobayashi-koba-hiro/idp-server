# Plugin実装ガイド

## このドキュメントの目的

**PluginLoaderパターン**を使って、拡張可能な機能を実装できるようになることが目標です。

### 所要時間
⏱️ **約30分**（実装 + テスト）

### 前提知識
- [03. 共通実装パターン](../03-common-patterns.md#4-plugin-パターン)

---

## Pluginパターンとは

複数の実装を動的に切り替える仕組み。

```
Map<Type, Service> services = PluginLoader.loadFromInternalModule(ServiceClass);
Service service = services.get(type);
service.execute(...);
```

**用途**:
- ✅ Grant Type別のトークン生成（`authorization_code`, `client_credentials`等）
- ✅ 認証方式別の処理（`password`, `sms`, `fido2`等）
- ✅ 通知チャネル別の送信（`fcm`, `apns`, `email`等）

---

## 実装例: 通知送信Plugin

複数の通知チャネル（FCM, APNS, Email）を動的に切り替える例。

---

## Step 1: Pluginインターフェース定義

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/notification/NotificationSender.java`

```java
package org.idp.server.core.notification;

public interface NotificationSender {

  /**
   * サポートする通知チャネル
   *
   * ⚠️ 重要: PluginLoaderがこのメソッドでキーを判定
   */
  NotificationChannel supportedChannel();

  /**
   * 通知送信
   *
   * @param notification 通知内容
   * @return 送信結果
   */
  NotificationResult send(Notification notification);

  /**
   * 送信可能かチェック
   *
   * @param notification 通知内容
   * @return 送信可能な場合true
   */
  boolean canSend(Notification notification);
}
```

**ポイント**:
- ✅ `supportedChannel()`メソッド必須（PluginLoaderが使用）
- ✅ ビジネスロジックメソッド（`send()`, `canSend()`）

---

## Step 2: Plugin実装

### FCM Plugin

**ファイル**: `libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/adapter/fcm/FcmNotificationSender.java`

```java
package org.idp.server.adapter.fcm;

import org.idp.server.core.notification.*;

/**
 * FCM通知送信Plugin
 *
 * ⚠️ 重要: @Pluginアノテーション不要（PluginLoaderが自動検出）
 */
public class FcmNotificationSender implements NotificationSender {

  @Override
  public NotificationChannel supportedChannel() {
    return NotificationChannel.FCM;
  }

  @Override
  public NotificationResult send(Notification notification) {
    // FCM固有の送信ロジック
    String fcmToken = notification.recipient().fcmToken();
    String message = notification.message();

    // FCM API呼び出し
    FcmClient fcmClient = new FcmClient();
    FcmResponse response = fcmClient.send(fcmToken, message);

    if (response.isSuccess()) {
      return NotificationResult.success(notification.id());
    } else {
      return NotificationResult.failure(notification.id(), response.error());
    }
  }

  @Override
  public boolean canSend(Notification notification) {
    return notification.recipient().hasFcmToken();
  }
}
```

### APNS Plugin

**ファイル**: `libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/adapter/apns/ApnsNotificationSender.java`

```java
package org.idp.server.adapter.apns;

import org.idp.server.core.notification.*;

/**
 * APNS通知送信Plugin
 */
public class ApnsNotificationSender implements NotificationSender {

  @Override
  public NotificationChannel supportedChannel() {
    return NotificationChannel.APNS;
  }

  @Override
  public NotificationResult send(Notification notification) {
    // APNS固有の送信ロジック
    String apnsToken = notification.recipient().apnsToken();
    String message = notification.message();

    // APNS API呼び出し
    ApnsClient apnsClient = new ApnsClient();
    ApnsResponse response = apnsClient.send(apnsToken, message);

    if (response.isSuccess()) {
      return NotificationResult.success(notification.id());
    } else {
      return NotificationResult.failure(notification.id(), response.error());
    }
  }

  @Override
  public boolean canSend(Notification notification) {
    return notification.recipient().hasApnsToken();
  }
}
```

---

## Step 3: Pluginの使用（Handler/Service）

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/notification/NotificationHandler.java`

```java
package org.idp.server.core.notification;

import java.util.Map;
import org.idp.server.platform.plugin.PluginLoader;

public class NotificationHandler {

  private final Map<NotificationChannel, NotificationSender> senders;

  public NotificationHandler() {
    // ✅ PluginLoader: 静的メソッド使用
    this.senders = PluginLoader.loadFromInternalModule(NotificationSender.class);
  }

  /**
   * 通知送信
   *
   * @param notification 通知内容
   * @return 送信結果
   */
  public NotificationResult send(Notification notification) {
    // 1. 通知チャネル取得
    NotificationChannel channel = notification.channel();

    // 2. 対応するPlugin選択
    NotificationSender sender = senders.get(channel);

    if (sender == null) {
      throw new UnsupportedNotificationChannelException(channel);
    }

    // 3. 送信可能かチェック
    if (!sender.canSend(notification)) {
      return NotificationResult.failure(
          notification.id(),
          "Cannot send notification: recipient does not have required token");
    }

    // 4. Plugin実行
    return sender.send(notification);
  }

  /**
   * サポートしている通知チャネル一覧
   */
  public Set<NotificationChannel> supportedChannels() {
    return senders.keySet();
  }
}
```

**ポイント**:
- ✅ `PluginLoader.loadFromInternalModule()`静的メソッド使用
- ✅ `Map<NotificationChannel, NotificationSender>`で動的選択
- ✅ Plugin実装の追加・削除が容易

---

## Step 4: テスト

### ユニットテスト

**ファイル**: `libs/idp-server-core/src/test/java/org/idp/server/core/notification/NotificationHandlerTest.java`

```java
package org.idp.server.core.notification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NotificationHandlerTest {

  @Test
  void shouldLoadPlugins() {
    NotificationHandler handler = new NotificationHandler();

    // Pluginが読み込まれているか確認
    Set<NotificationChannel> channels = handler.supportedChannels();

    assertTrue(channels.contains(NotificationChannel.FCM));
    assertTrue(channels.contains(NotificationChannel.APNS));
  }

  @Test
  void shouldSendFcmNotification() {
    NotificationHandler handler = new NotificationHandler();

    Notification notification = Notification.fcm(
        new NotificationId("test-id"),
        new Recipient("fcm-token-123"),
        "Test message"
    );

    NotificationResult result = handler.send(notification);

    assertTrue(result.isSuccess());
  }

  @Test
  void shouldThrowExceptionForUnsupportedChannel() {
    NotificationHandler handler = new NotificationHandler();

    Notification notification = Notification.custom(
        new NotificationId("test-id"),
        NotificationChannel.UNKNOWN,  // 未サポート
        new Recipient("token"),
        "Test message"
    );

    assertThrows(
        UnsupportedNotificationChannelException.class,
        () -> handler.send(notification));
  }
}
```

---

## チェックリスト

Plugin実装前に以下を確認：

### Pluginインターフェース定義
- [ ] `supported{Type}()`メソッド定義（PluginLoaderがキー判定に使用）
- [ ] ビジネスロジックメソッド定義

### Plugin実装
- [ ] インターフェース実装
- [ ] `supported{Type}()`メソッド実装
- [ ] 各Plugin固有のロジック実装

### Pluginの使用
- [ ] **`PluginLoader.loadFromInternalModule()`静的メソッド使用**
- [ ] `Map<Type, Plugin>`で動的選択
- [ ] null チェック（未サポートチャネル対応）

### テスト
- [ ] Plugin読み込みテスト
- [ ] 各Plugin実行テスト
- [ ] 未サポートチャネルエラーテスト

---

## よくあるエラー

### エラー1: PluginLoaderインスタンス化

```java
// ❌ 間違い: インスタンス化不可
PluginLoader<NotificationSender> loader =
    new PluginLoader<>(NotificationSender.class);  // コンパイルエラー

// ✅ 正しい: 静的メソッド使用
Map<NotificationChannel, NotificationSender> senders =
    PluginLoader.loadFromInternalModule(NotificationSender.class);
```

### エラー2: `supported{Type}()`メソッドなし

```java
// ❌ 間違い: PluginLoaderがキー判定できない
public interface NotificationSender {
    // supported{Type}()メソッドがない！
    NotificationResult send(Notification notification);
}

// ✅ 正しい: supported{Type}()必須
public interface NotificationSender {
    NotificationChannel supportedChannel();  // ✅ 必須
    NotificationResult send(Notification notification);
}
```

---

## 次のステップ

✅ Plugin実装をマスターした！

### 📖 次に読むべきドキュメント

1. [外部サービス連携ガイド](./impl-17-external-integration.md) - HttpRequestExecutor使用

### 🔗 詳細情報

- [AI開発者向け: Platform - Plugin System](../content_10_ai_developer/ai-12-platform.md#plugin-system)

---

**情報源**: [PluginLoader.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/PluginLoader.java)
**最終更新**: 2025-10-12
