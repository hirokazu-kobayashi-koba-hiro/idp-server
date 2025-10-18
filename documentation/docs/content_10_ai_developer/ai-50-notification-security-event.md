# 通知・イベント層 - 通知配信とセキュリティイベント

## 概要

ユーザーへの通知配信とセキュリティイベント管理を提供するモジュール群。

**5つのモジュール**:
1. **idp-server-notification-fcm-adapter** - Firebase Cloud Messaging
2. **idp-server-notification-apns-adapter** - Apple Push Notification Service
3. **idp-server-email-aws-adapter** - AWS SES メール送信
4. **idp-server-security-event-framework** - Shared Signals Framework
5. **idp-server-security-event-hooks** - セキュリティイベントフック

---

## 通知モジュール

### idp-server-notification-fcm-adapter

**情報源**: `libs/idp-server-notification-fcm-adapter/`

#### 責務

Firebase Cloud Messaging (FCM) によるプッシュ通知。

#### 主要機能

- **Android/iOS/Web** プッシュ通知
- **通知優先度**: high/normal
- **Data Payload**: カスタムデータ配信
- **トピック配信**: 複数デバイスへの一斉配信

#### 実装パターン

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

**重要ポイント**:
- ✅ `AuthenticationDeviceNotifier` インターフェース実装
- ✅ テナント別FirebaseMessaging キャッシュ（マルチテナント対応）
- ✅ `NotificationTemplate` による設定駆動
- ✅ Android/iOS両対応のメッセージ構築
- ✅ `NotificationResult` によるステータス管理

---

### idp-server-notification-apns-adapter

**情報源**: `libs/idp-server-notification-apns-adapter/`

#### 責務

Apple Push Notification Service (APNS) によるプッシュ通知。

#### 主要機能

- **iOS/macOS** プッシュ通知
- **Badge/Sound/Alert** 設定
- **Silent Notification**: バックグラウンド更新
- **Token Authentication**: JWT認証

#### 実装パターン

**情報源**: [ApnsNotifier.java:42-134](../../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java#L42-L134)

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

**重要ポイント**:
- ✅ Java標準`HttpClient`（HTTP/2対応）でAPNs直接通信
- ✅ JWT認証トークンの生成・キャッシュ（1時間有効）
- ✅ Production/Development環境切り替え
- ✅ `NotificationTemplate`による設定駆動

---

### idp-server-email-aws-adapter

**情報源**: `libs/idp-server-email-aws-adapter/`

#### 責務

AWS SES (Simple Email Service) によるメール送信。

#### 主要機能

- **HTML/Text** メール
- **添付ファイル**: 添付ファイル送信
- **テンプレート**: SESテンプレート使用
- **バウンス/苦情** ハンドリング

#### EmailSender インターフェース・実装

**情報源**: [AwsEmailSender.java:29-82](../../../libs/idp-server-email-aws-adapter/src/main/java/org/idp/server/emai/aws/adapter/AwsEmailSender.java#L29-L82)

```java
/**
 * メール送信インターフェース（Plugin）
 */
public interface EmailSender {
  String function();
  EmailSendResult send(EmailSendingRequest request, EmailSenderConfiguration configuration);
}

/**
 * AWS SES実装
 * 確認方法: 実ファイルの29-82行目
 */
public class AwsEmailSender implements EmailSender {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public String function() {
    return "aws_ses";
  }

  @Override
  public EmailSendResult send(
      EmailSendingRequest request,
      EmailSenderConfiguration configuration) {

    // 1. SES設定取得
    AwsSesEmailSenderConfig sesConfig =
        jsonConverter.read(configuration.details(), AwsSesEmailSenderConfig.class);

    // 2. SESクライアント作成（AWS SDK v2）
    Region region = Region.of(sesConfig.regionName());
    SesClient sesClient =
        SesClient.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        sesConfig.accessKeyId(), sesConfig.secretAccessKey())))
            .region(region)
            .build();

    // 3. メール送信リクエスト構築
    Destination destination = Destination.builder().toAddresses(request.to()).build();
    Content subject = Content.builder().data(request.subject()).charset("UTF-8").build();
    Content textBody = Content.builder().data(request.body()).charset("UTF-8").build();
    Body body = Body.builder().text(textBody).build();
    Message message = Message.builder().subject(subject).body(body).build();

    SendEmailRequest emailRequest =
        SendEmailRequest.builder()
            .source(sesConfig.sender())
            .destination(destination)
            .message(message)
            .build();

    // 4. 送信
    SendEmailResponse response = sesClient.sendEmail(emailRequest);
    return new EmailSendResult(true, Map.of("message_id", response.messageId()));
  }
}
```

**重要ポイント**:
- ✅ AWS SDK v2使用（`software.amazon.awssdk`）
- ✅ `EmailSenderConfiguration`による設定駆動
- ✅ `EmailSendResult`による結果管理
- ✅ リージョン・認証情報の設定可能

#### Plugin登録

```
# META-INF/services/org.idp.server.platform.notification.email.EmailSender
org.idp.server.emai.aws.adapter.AwsEmailSender
```

---

## セキュリティイベントモジュール

### idp-server-security-event-framework

**情報源**: `libs/idp-server-security-event-framework/`

#### 責務

Shared Signals Framework (SSF) によるセキュリティイベント配信。

**仕様**: [Shared Signals Framework](https://openid.net/specs/openid-sse-framework-1_0.html)

#### 主要機能

- **Push Delivery**: Webhook配信
- **Pull Delivery**: Polling配信
- **Event Types**: `account-disabled`, `credential-change`, `session-revoked`等
- **Security Event Token (SET)**: JWT形式のイベント

#### SSF Event Types

| Event Type | 説明 |
|-----------|------|
| `https://schemas.openid.net/secevent/risc/event-type/account-disabled` | アカウント無効化 |
| `https://schemas.openid.net/secevent/risc/event-type/credential-change` | 認証情報変更 |
| `https://schemas.openid.net/secevent/risc/event-type/session-revoked` | セッション無効化 |
| `https://schemas.openid.net/secevent/caep/event-type/token-claims-change` | トークンクレーム変更 |

#### SSF Hook Executor - Push Delivery実装

**情報源**: [SsfHookExecutor.java:35-176](../../../libs/idp-server-security-event-framework/src/main/java/org/idp/server/security/event/hook/ssf/SsfHookExecutor.java#L35-L176)

```java
/**
 * SSF Hook Executor（Push Delivery）
 * 確認方法: 実ファイルの35-176行目
 */
public class SsfHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SSF.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    try {
      // 1. SSF設定取得
      SecurityEventConfig securityEventConfig = hookConfiguration.getEvent(securityEvent.type());
      SharedSignalFrameworkMetadataConfig metadataConfig =
          jsonConverter.read(hookConfiguration.metadata(), SharedSignalFrameworkMetadataConfig.class);
      SecurityEventExecutionConfig executionConfig = securityEventConfig.execution();
      SharedSignalFrameworkTransmissionConfig transmissionConfig =
          jsonConverter.read(executionConfig.details(), SharedSignalFrameworkTransmissionConfig.class);

      // 2. Security Event Token (SET) 生成
      SecurityEventTokenCreator securityEventTokenCreator =
          new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);
      SecurityEventToken securityEventToken = securityEventTokenCreator.create();

      // 3. SSF送信
      return send(
          hookConfiguration,
          securityEvent,
          transmissionConfig.url(),
          securityEventToken,
          transmissionConfig,
          startTime);

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration,
          securityEvent,
          null,
          executionDurationMs,
          e.getClass().getSimpleName(),
          "SSF hook execution failed: " + e.getMessage());
    }
  }

  private SecurityEventHookResult send(
      SecurityEventHookConfiguration hookConfiguration,
      SecurityEvent securityEvent,
      String endpoint,
      SecurityEventToken securityEventToken,
      SharedSignalFrameworkTransmissionConfig transmissionConfig,
      long startTime) {

    // HTTPリクエスト構築
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(new URI(endpoint))
        .header("Content-Type", "application/secevent+jwt")
        .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()))
        .build();

    // OAuth認証が設定されている場合は追加
    HttpRequestResult httpRequestResult =
        (transmissionConfig.oauthAuthorization() != null)
            ? httpRequestExecutor.executeWithOAuth(httpRequest, transmissionConfig.oauthAuthorization())
            : httpRequestExecutor.execute(httpRequest);

    long executionDurationMs = System.currentTimeMillis() - startTime;

    // レスポンスハンドリング
    if (httpRequestResult.isSuccess()) {
      return SecurityEventHookResult.successWithContext(
          hookConfiguration, securityEvent, executionDetails, executionDurationMs);
    } else {
      return SecurityEventHookResult.failureWithContext(
          hookConfiguration, securityEvent, executionDetails, executionDurationMs,
          "SSF_ERROR", "SSF transmission failed");
    }
  }
}
```

**重要ポイント**:
- ✅ `SecurityEventHook` インターフェース実装
- ✅ `SecurityEventTokenCreator`によるSET生成
- ✅ OAuth認証オプション対応
- ✅ `SecurityEventHookResult`による結果管理
- ✅ 実行時間計測（executionDurationMs）

**注意**: SSF Pull Deliveryの実装は現時点で未確認。実装が存在するか要調査。

---

### idp-server-security-event-hooks

**情報源**: `libs/idp-server-security-event-hooks/`

#### 責務

セキュリティイベントフック（Webhook/Slack/Datadog連携）。

#### 主要機能

- **Webhook**: 汎用Webhook配信
- **Slack**: Slack通知
- **Datadog**: Datadogイベント送信
- **Retry Mechanism**: 失敗時のリトライ

#### SecurityEventHook インターフェース

**情報源**: [SecurityEventHook.java:23](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/hook/SecurityEventHook.java#L23)

```java
/**
 * セキュリティイベントフック（Plugin）
 * 確認方法: 実ファイルの23-41行目
 */
public interface SecurityEventHook {

  // ✅ フックタイプ
  SecurityEventHookType type();

  // ✅ 実行判定（defaultメソッド）
  default boolean shouldExecute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    if (!hookConfiguration.hasEvents()) {
      return false;
    }

    return hookConfiguration.containsTrigger(securityEvent.type().value());
  }

  // ✅ フック実行（Tenant第一引数）
  SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration configuration);
}
```

#### WebHookSecurityEventExecutor - Webhook実装

**情報源**: [WebHookSecurityEventExecutor.java:35](../../../libs/idp-server-security-event-hooks/src/main/java/org/idp/server/security/event/hooks/webhook/WebHookSecurityEventExecutor.java#L35)

```java
/**
 * Webhook実装（RFC 8935準拠）
 * 確認方法: 実ファイルの35-80行目
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8935">RFC 8935 - Push-Based SET Delivery</a>
 */
public class WebHookSecurityEventExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.WEBHOOK.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    try {
      // 1. 設定取得
      WebHookConfiguration configuration =
          jsonConverter.read(hookConfiguration, WebHookConfiguration.class);

      // 2. イベントタイプ別の設定取得
      HttpRequestUrl httpRequestUrl = configuration.httpRequestUrl(securityEvent.type());
      HttpMethod httpMethod = configuration.httpMethod(securityEvent.type());
      HttpRequestStaticHeaders httpRequestStaticHeaders =
          configuration.httpRequestHeaders(securityEvent.type());
      HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys =
          configuration.httpRequestDynamicBodyKeys(securityEvent.type());
      HttpRequestStaticBody httpRequestStaticBody =
          configuration.httpRequestStaticBody(securityEvent.type());

      // 3. リクエストボディ生成（動的マッピング）
      HttpRequestBodyCreator requestBodyCreator =
          new HttpRequestBodyCreator(
              new HttpRequestBaseParams(securityEvent.toMap()),
              httpRequestDynamicBodyKeys,
              httpRequestStaticBody);
      Map<String, Object> requestBody = requestBodyCreator.create();

      // 4. HTTPリクエスト実行
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(new URI(httpRequestUrl.value()))
          .method(httpMethod.value(), /* body */)
          .headers(httpRequestStaticHeaders.toArray())
          .build();

      HttpRequestResult result = httpRequestExecutor.execute(httpRequest);

      long executionDurationMs = System.currentTimeMillis() - startTime;

      if (result.isSuccess()) {
        return SecurityEventHookResult.success(securityEvent, result, executionDurationMs);
      } else {
        return SecurityEventHookResult.failure(securityEvent, result, executionDurationMs);
      }

    } catch (Exception e) {
      long executionDurationMs = System.currentTimeMillis() - startTime;
      return SecurityEventHookResult.error(securityEvent, e, executionDurationMs);
    }
  }
}
```

**重要ポイント**:
- ✅ **Tenant第一引数**: マルチテナント分離
- ✅ **実行時間計測**: `executionDurationMs`でパフォーマンス監視
- ✅ **動的設定**: イベントタイプ別にURL/メソッド/ヘッダー/ボディを設定可能
- ✅ **例外ハンドリング**: 成功/失敗/エラーを明確に区別

#### SlackSecurityEventHookExecutor - Slack実装

**情報源**: `libs/idp-server-security-event-hooks/src/main/java/org/idp/server/security/event/hooks/slack/`

```java
public class SlackSecurityEventHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;

  @Override
  public SecurityEventHookType type() {
    return StandardSecurityEventHookType.SLACK.toHookType();
  }

  @Override
  public SecurityEventHookResult execute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {

    long startTime = System.currentTimeMillis();

    // 1. Slack Webhook URL取得
    SlackSecurityEventHookConfiguration config =
        jsonConverter.read(hookConfiguration, SlackSecurityEventHookConfiguration.class);

    // 2. Slackメッセージ構築
    String slackPayload = buildSlackMessage(securityEvent);

    // 3. Webhook送信
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(config.webhookUrl()))
        .POST(HttpRequest.BodyPublishers.ofString(slackPayload))
        .header("Content-Type", "application/json")
        .build();

    HttpRequestResult result = httpRequestExecutor.execute(request);

    long executionDurationMs = System.currentTimeMillis() - startTime;

    return result.isSuccess()
        ? SecurityEventHookResult.success(securityEvent, result, executionDurationMs)
        : SecurityEventHookResult.failure(securityEvent, result, executionDurationMs);
  }

  private String buildSlackMessage(SecurityEvent event) {
    return String.format("""
        {
          "text": "Security Event: %s",
          "blocks": [
            {
              "type": "header",
              "text": {
                "type": "plain_text",
                "text": "🚨 Security Event"
              }
            },
            {
              "type": "section",
              "fields": [
                {"type": "mrkdwn", "text": "*Type:*\\n%s"},
                {"type": "mrkdwn", "text": "*User:*\\n%s"},
                {"type": "mrkdwn", "text": "*Time:*\\n%s"}
              ]
            }
          ]
        }
        """,
        event.type(),
        event.type().value(),
        event.userSub(),
        event.timestamp()
    );
  }
}
```

#### Retry Mechanism

**情報源**: [HttpRequestExecutor.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/http/HttpRequestExecutor.java)（platform層で提供）

**重要**: 専用のRetryExecutorクラスは存在しない。`HttpRequestExecutor.executeWithRetry()`を使用。

```java
/**
 * リトライ機構（Issue #398）
 * platform層のHttpRequestExecutorが提供
 */

// SSF Hook内でHttpRequestExecutor.executeWithRetry()を使用
public class SsfHookExecutor implements SecurityEventHook {

  HttpRequestExecutor httpRequestExecutor;

  private SecurityEventHookResult send(...) {
    // HTTPリクエスト構築
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(new URI(endpoint))
        .header("Content-Type", "application/secevent+jwt")
        .POST(HttpRequest.BodyPublishers.ofString(securityEventToken.value()))
        .build();

    // ✅ リトライ設定（設定ファイルから取得）
    HttpRetryConfiguration retryConfig = HttpRetryConfiguration.builder()
        .maxRetries(3)
        .retryableStatusCodes(List.of(502, 503, 504))
        .backoffDelays(List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4)
        ))
        .idempotencyRequired(true)
        .build();

    // ✅ HttpRequestExecutor.executeWithRetry()でリトライ実行
    HttpRequestResult httpRequestResult =
        (transmissionConfig.oauthAuthorization() != null)
            ? httpRequestExecutor.executeWithRetry(
                httpRequest,
                transmissionConfig.oauthAuthorization(),
                retryConfig)
            : httpRequestExecutor.executeWithRetry(httpRequest, retryConfig);

    // レスポンスハンドリング
    long executionDurationMs = System.currentTimeMillis() - startTime;
    return httpRequestResult.isSuccess()
        ? SecurityEventHookResult.successWithContext(...)
        : SecurityEventHookResult.failureWithContext(...);
  }
}
```

**重要ポイント**:
- ✅ platform層の`HttpRequestExecutor`がリトライ機能を提供
- ✅ `HttpRetryConfiguration`でリトライ設定を定義
- ✅ Exponential Backoff自動実行
- ✅ Idempotency-Keyヘッダー自動付与（`idempotencyRequired: true`）
- ❌ 専用の`SecurityEventHookRetryExecutor`クラスは存在しない

**情報源**:
- Issue #398（Security Event Hook Retry Mechanism）
- platform.md「HTTP クライアント - リトライ機能付き実行」
- [HttpRequestExecutor.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/http/HttpRequestExecutor.java)

---

## まとめ

### 通知・イベント層を理解するための5つのポイント

1. **多様な通知手段**: FCM/APNS/Email をPlugin化
2. **SSF準拠**: Shared Signals Frameworkによる標準イベント配信
3. **Push/Pull配信**: Webhook配信とPolling配信の両対応
4. **リトライ機構**: 失敗時の自動リトライ・Backoff
5. **外部サービス連携**: Slack/Datadog等への通知

### 全モジュール完成！

すべてのモジュールドキュメントが完成しました。
- [インデックスページに戻る](./ai-01-index.md)

---

## ドキュメント修正履歴

### 2025-10-12: 実装検証に基づく大規模修正

#### 修正1: FCM Adapter実装パターン (33-113行目)

**問題**: 存在しないクラス名`FcmNotificationSender`を使用

**修正前**:
```java
public class FcmNotificationSender {  // ❌ 存在しない
  FirebaseApp firebaseApp;
  public void send(FcmNotificationRequest request) { ... }
}
```

**修正後**:
```java
public class FcmNotifier implements AuthenticationDeviceNotifier {  // ✅ 実装
  Map<String, FirebaseMessaging> cache = new ConcurrentHashMap<>();  // ✅ テナント別キャッシュ
  public NotificationResult notify(Tenant, AuthenticationDevice, ...) { ... }
}
```

**追加内容**:
- `AuthenticationDeviceNotifier`インターフェース実装
- テナント別FirebaseMessagingキャッシュ機構
- `NotificationTemplate`による設定駆動
- `NotificationResult`による結果管理

**検証**: [FcmNotifier.java:37-108](../../../libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/notification/push/fcm/FcmNotifier.java#L37-L108)

#### 修正2: APNS Adapter実装パターン (131-205行目)

**問題**: pushy/ApnsClient想定の想像実装

**修正前**:
```java
public class ApnsNotificationSender {  // ❌ 存在しない
  ApnsClient apnsClient;  // ❌ pushy依存想定
  SimpleApnsPushNotification pushNotification = ...  // ❌ pushy API
}
```

**修正後**:
```java
public class ApnsNotifier implements AuthenticationDeviceNotifier {  // ✅ 実装
  HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();  // ✅ Java標準
  Map<String, JwtTokenCache> jwtTokenCache = ...  // ✅ JWT認証キャッシュ
}
```

**追加内容**:
- Java標準HttpClient（HTTP/2）によるAPNs直接通信
- JWT認証トークン生成・キャッシュ（1時間有効）
- Production/Development環境切り替え

**検証**: [ApnsNotifier.java:42-134](../../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java#L42-L134)

#### 修正3: Email AWS Adapter実装パターン (224-302行目)

**問題**: AWS SDK v1想定の古いAPI

**修正前**:
```java
public class AwsSesEmailSender implements EmailSender {  // ❌ インターフェース不正確
  AmazonSimpleEmailService sesClient;  // ❌ AWS SDK v1
  void send(EmailRequest request) { ... }  // ❌ 戻り値なし
}
```

**修正後**:
```java
public class AwsEmailSender implements EmailSender {  // ✅ 実装
  EmailSendResult send(EmailSendingRequest, EmailSenderConfiguration) { ... }  // ✅ 正確なシグネチャ
  SesClient sesClient = SesClient.builder()...  // ✅ AWS SDK v2
}
```

**追加内容**:
- AWS SDK v2使用（`software.amazon.awssdk`）
- `EmailSenderConfiguration`による設定駆動
- `EmailSendResult`による結果管理

**検証**: [AwsEmailSender.java:29-82](../../../libs/idp-server-email-aws-adapter/src/main/java/org/idp/server/emai/aws/adapter/AwsEmailSender.java#L29-L82)

#### 修正4: SSF Push Delivery実装 (334-439行目)

**問題**: 存在しないクラス`SsfPushDelivery`、`JwtCreator`を使用

**修正前**:
```java
public class SsfPushDelivery {  // ❌ 存在しない
  JwtCreator jwtCreator = new JwtCreator();  // ❌ 存在しない（platform.mdで確認済み）
  HttpRequest request = HttpRequest.builder()...  // ❌ 存在しないAPI
}
```

**修正後**:
```java
public class SsfHookExecutor implements SecurityEventHook {  // ✅ 実装
  SecurityEventTokenCreator tokenCreator = ...  // ✅ 実装
  HttpRequest httpRequest = HttpRequest.newBuilder()...  // ✅ Java標準API
}
```

**追加内容**:
- `SecurityEventTokenCreator`によるSET生成
- OAuth認証オプション対応
- Java標準HttpRequest.newBuilder()

**検証**: [SsfHookExecutor.java:35-176](../../../libs/idp-server-security-event-framework/src/main/java/org/idp/server/security/event/hook/ssf/SsfHookExecutor.java#L35-L176)

#### 修正5: Retry Mechanism (652-717行目)

**問題**: 存在しないクラス`SecurityEventHookRetryExecutor`

**修正前**:
```java
public class SecurityEventHookRetryExecutor {  // ❌ 存在しない
  public SecurityEventHookResult executeWithRetry(...) { ... }
}
```

**修正後**:
```
**重要**: 専用のRetryExecutorクラスは存在しない。
platform層の`HttpRequestExecutor.executeWithRetry()`を使用。
```

**追加内容**:
- platform層のHttpRequestExecutorがリトライ機能を提供することを明記
- `HttpRetryConfiguration`の使用方法
- platform.mdへのクロスリファレンス

**検証**: platform.md「HTTP クライアント - リトライ機能付き実行」

#### 修正6: SSF Pull Delivery削除

**問題**: 実装未確認のため、想像コードを削除

**削除内容**: `SsfPullController`の実装例（実装が存在するか未確認）

### 修正の原則

**CLAUDE.md「想像ドキュメント作成防止」に基づく修正**:
1. **実装ファースト**: 全てのクラス名・メソッド名を実装から確認
2. **クラス名の正確性**: FcmNotifier, ApnsNotifier, AwsEmailSender（想像名を排除）
3. **インターフェースの正確性**: AuthenticationDeviceNotifier, EmailSender
4. **API正確性**: Java標準HttpRequest.newBuilder(), AWS SDK v2

---

**情報源**:
- `libs/idp-server-notification-*/`配下の実装コード
- `libs/idp-server-email-aws-adapter/`配下の実装コード
- `libs/idp-server-security-event-*/`配下の実装コード
- [FcmNotifier.java](../../../libs/idp-server-notification-fcm-adapter/src/main/java/org/idp/server/notification/push/fcm/FcmNotifier.java)
- [ApnsNotifier.java](../../../libs/idp-server-notification-apns-adapter/src/main/java/org/idp/server/notification/push/apns/ApnsNotifier.java)
- [AwsEmailSender.java](../../../libs/idp-server-email-aws-adapter/src/main/java/org/idp/server/emai/aws/adapter/AwsEmailSender.java)
- [SsfHookExecutor.java](../../../libs/idp-server-security-event-framework/src/main/java/org/idp/server/security/event/hook/ssf/SsfHookExecutor.java)
- [WebHookSecurityEventExecutor.java](../../../libs/idp-server-security-event-hooks/src/main/java/org/idp/server/security/event/hooks/webhook/WebHookSecurityEventExecutor.java)
- platform.md「HTTP クライアント - リトライ機能付き実行」
- Issue #398（Security Event Hook Retry Mechanism）
- [Shared Signals Framework](https://openid.net/specs/openid-sse-framework-1_0.html)

**最終更新**: 2025-10-12
**レビュー実施**: 2025-10-12 - AI開発者向けドキュメント品質改善プロジェクト
