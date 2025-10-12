# idp-server-email-aws-adapter

## モジュール概要

**情報源**: `libs/idp-server-email-aws-adapter/`
**確認日**: 2025-10-12

### 責務

AWS SES (Simple Email Service) によるメール送信。

### 主要機能

- **HTML/Text** メール
- **添付ファイル**: 添付ファイル送信
- **テンプレート**: SESテンプレート使用
- **バウンス/苦情** ハンドリング

## EmailSender インターフェース・実装

**情報源**: [AwsEmailSender.java:29-82](../../libs/idp-server-email-aws-adapter/src/main/java/org/idp/server/emai/aws/adapter/AwsEmailSender.java#L29-L82)

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

## 重要ポイント

- ✅ AWS SDK v2使用（`software.amazon.awssdk`）
- ✅ `EmailSenderConfiguration`による設定駆動
- ✅ `EmailSendResult`による結果管理
- ✅ リージョン・認証情報の設定可能

## Plugin登録

```
# META-INF/services/org.idp.server.platform.notification.email.EmailSender
org.idp.server.emai.aws.adapter.AwsEmailSender
```

## 関連ドキュメント

- [通知・イベント層統合ドキュメント](./ai-50-notification-security-event.md) - メール送信を含む全通知モジュール
- [idp-server-notification-fcm-adapter](./ai-51-notification-fcm.md) - Firebase Cloud Messaging
- [idp-server-notification-apns-adapter](./ai-52-notification-apns.md) - Apple Push Notification

---

**情報源**:
- `libs/idp-server-email-aws-adapter/`配下の実装コード
- [AwsEmailSender.java](../../libs/idp-server-email-aws-adapter/src/main/java/org/idp/server/emai/aws/adapter/AwsEmailSender.java)

**最終更新**: 2025-10-12
