# 認証デバイス通知

このドキュメントは、ユーザーが登録済みの認証デバイスに対して認証リクエストを送信する `authentication-device-notification`
方式の `概要`・`設定`・`利用方法` について説明します。

---

## 概要

`authentication-device-notification`
は、Push型の認証確認処理を実現するためのインタラクション方式です。ユーザーに紐づいたデバイス情報（例：モバイルアプリやFIDO認証器など）に対して、リクエストを送信し、ユーザー側での認可操作をトリガーする役割を持ちます。

このインタラクションの主な用途は、以下の通り：

* 多要素認証（MFA）の一部として、事前に登録された認証デバイスへの通知
* ユーザー確認（confirmation）やトランザクション認証のトリガー

この方式単体では検証処理（ユーザー応答）は行わず、別途 `fido-uaf`など 他のデバイス認証と組み合わせて使います。

---

## 設定

通知チャネルの設定はテナント単位で定義されます。

サポートされる通知チャネル:
- **FCM** (Firebase Cloud Messaging) - Android/iOS/Web向けプッシュ通知
- **APNS** (Apple Push Notification Service) - iOS向けプッシュ通知

複数チャネルを同時設定可能で、デバイスタイプに応じて自動的に使い分けられます。

### トップレベル設定項目

| 項目            | 内容                                       |
|---------------|--------------------------------------------|
| `id`          | 設定ID（UUID形式）                              |
| `type`        | `authentication-device-notification` 固定    |
| `interactions` | インタラクション定義                               |

### インタラクション設定

`interactions.authentication-device-notification.execution` で通知実行方法を定義します。

| 項目         | 内容                                               |
|------------|--------------------------------------------------|
| `function` | 実行関数（`multi_channel_notification`など）            |
| `details`  | チャネル別の詳細設定（キーはチャネル名`fcm`/`apns`、値は各チャネルの設定） |

### FCM詳細設定項目 (`details.fcm`)

| 項目           | 内容                                                                  |
|--------------|---------------------------------------------------------------------|
| `templates`  | 通知テンプレート群（`transaction`, `authentication`, `default`）              |
| `credential` | FCMサービスへの認証クレデンシャル（JSON文字列形式）                                      |

#### テンプレート構造

| 項目       | 内容                         |
|----------|----------------------------|
| `sender` | 送信元URL（App IDとして使用）         |
| `title`  | 通知タイトル（ユーザーに表示）            |
| `body`   | 通知本文（ユーザーに表示）              |

### APNS詳細設定項目 (`details.apns`)

| 項目           | 内容                                    |
|--------------|---------------------------------------|
| `templates`  | 通知テンプレート群（FCMと同じ構造）                  |
| `key_id`     | Apple Developer Keyの識別子              |
| `team_id`    | Apple Developer Team ID               |
| `bundle_id`  | アプリケーションのBundle ID                   |
| `key_content` | APNS認証用秘密鍵（PEM形式）                    |
| `production` | 本番環境フラグ（`true`: 本番、`false`: Sandbox） |

### FCM + APNS 複数チャネル設定例

```json
{
  "id": "d06b2c01-e33c-454e-96b1-8cbea339fa65",
  "type": "authentication-device-notification",
  "interactions": {
    "authentication-device-notification": {
      "execution": {
        "function": "multi_channel_notification",
        "details": {
          "fcm": {
            "templates": {
              "transaction": {
                "sender": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
                "title": "ログイン要求",
                "body": "あなたのアカウントに対する認証要求があります。承認しますか？"
              },
              "authentication": {
                "sender": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
                "title": "認証リクエスト",
                "body": "認証を完了してください。"
              },
              "default": {
                "sender": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
                "title": "通知",
                "body": "アカウントに関する通知があります。"
              }
            },
            "credential": "{\"type\":\"service_account\",\"project_id\":\"your-project\",...}"
          },
          "apns": {
            "templates": {
              "transaction": {
                "sender": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
                "title": "ログイン要求",
                "body": "あなたのアカウントに対する認証要求があります。承認しますか？"
              },
              "authentication": {
                "sender": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
                "title": "認証リクエスト",
                "body": "認証を完了してください。"
              },
              "default": {
                "sender": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
                "title": "通知",
                "body": "アカウントに関する通知があります。"
              }
            },
            "key_id": "ABC123DEFG",
            "team_id": "XYZ987WXYZ",
            "bundle_id": "com.example.app",
            "key_content": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
            "production": false
          }
        }
      }
    }
  }
}
```

---

## 利用方法

この認証方式は以下の2つのパターンで実行されます：

### 1. 明示的なリクエストによる実行

認可フロー中に以下のエンドポイントへPOSTリクエストを送信することで通知を発火できます：

```http
POST /v1/authorizations/{id}/authentication-device-notification
```

### 2. CIBAフローでの自動実行

CIBA（Client Initiated Backchannel Authentication）フローでは、バックチャネル認証リクエストの受信後、ユーザー特定が完了すると自動的にこの通知処理が実行されます。

**CIBAフロー実行シーケンス：**
1. クライアントがバックチャネル認証リクエストを送信
2. サーバーがユーザーを特定
3. **優先順位が最も高い認証デバイスへ自動的にプッシュ通知を送信** ← この処理
4. ユーザーが認証デバイス上で認証操作を実行（FIDO-UAF等）
5. 認証結果を `authentication-device` インタラクションで検証

**送信データ:**
- `sender`: 送信元URL（App ID）
- `title`: 通知タイトル
- `body`: 通知本文

**対象デバイス:**
- ユーザーに登録された認証デバイスのうち、優先順位（priority）が最も高いデバイスに送信されます

詳細は [CIBA + FIDO-UAF フロー](../../content_05_how-to/ciba-flow-fido-uaf.md) を参照してください。

---

## 備考

* 通知処理自体には認証結果の判定ロジックは含まれません（通知送信のみ）
* 通知先のデバイスは、ユーザー情報に登録された主デバイスとして取得されます
* この処理の次に `authentication-device` インタラクションでユーザーの認可アクションを検証します
* CIBAフローでは設定に基づいて自動的に実行されるため、明示的なリクエストは不要です
