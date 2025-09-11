# セキュリティイベント Tags リファレンス

## 概要
idp-serverのセキュリティイベントログには、検索・分析を効率化するための `tags` フィールドが自動生成されます。このドキュメントでは、tagsの生成ルールと活用方法を説明します。

## Tags の自動生成ルール

### 1. カテゴリタグ（必ず1つ生成）

各イベントタイプに基づいて、以下のカテゴリタグが自動的に付与されます：

| カテゴリタグ | 対象イベント | 説明 |
|------------|-------------|------|
| `authentication` | `password_*` | パスワード認証関連 |
| `mfa` | `fido_uaf_*`, `webauthn_*` | 多要素認証（FIDO UAF・WebAuthn） |
| `oauth` | `oauth_*` | OAuth・OIDC関連 |
| `federation` | `federation_*` | 外部IdP連携 |
| `user_management` | `user_*` | ユーザー管理操作 |
| `client_management` | `client_*` | クライアント管理操作 |
| `email` | `email_*` | メール認証・通知 |
| `sms` | `sms_*` | SMS認証・通知 |
| `other` | その他すべて | 上記以外のイベント |

### 2. 成功・失敗タグ（該当時のみ生成）

イベント名の末尾に基づいて、以下のタグが追加されます：

| タグ | 条件 | 説明 |
|-----|-----|------|
| `success` | イベント名が `*_success` で終わる | 成功イベント |
| `failure` | イベント名が `*_failure` で終わる | 失敗イベント |

## Tags の具体例

### パスワード認証成功
```json
{
  "event_type": "password_success",
  "tags": ["authentication", "success"]
}
```

### FIDO UAF登録失敗
```json
{
  "event_type": "fido_uaf_registration_failure", 
  "tags": ["mfa", "failure"]
}
```

### OAuth認可
```json
{
  "event_type": "oauth_authorize",
  "tags": ["oauth"]
}
```

### ユーザー削除
```json
{
  "event_type": "user_deletion",
  "tags": ["user_management"]
}
```

## ログ検索での活用

### カテゴリ別検索

#### MFA関連のすべてのイベント
```bash
grep '"tags":.*"mfa"' /var/log/idp-server.log
```

#### OAuth関連のすべてのイベント
```bash
grep '"tags":.*"oauth"' /var/log/idp-server.log
```

#### 認証関連のすべてのイベント
```bash
grep '"tags":.*"authentication"' /var/log/idp-server.log
```

### 成功・失敗別検索

#### すべての失敗イベント
```bash
grep '"tags":.*"failure"' /var/log/idp-server.log
```

#### すべての成功イベント
```bash
grep '"tags":.*"success"' /var/log/idp-server.log
```

### 組み合わせ検索

#### MFA関連の失敗イベントのみ
```bash
grep '"tags":.*"mfa".*"failure"\|"tags":.*"failure".*"mfa"' /var/log/idp-server.log
```

#### OAuth関連の失敗イベントのみ
```bash
grep '"tags":.*"oauth".*"failure"\|"tags":.*"failure".*"oauth"' /var/log/idp-server.log
```

#### 認証関連の成功イベントのみ
```bash
grep '"tags":.*"authentication".*"success"\|"tags":.*"success".*"authentication"' /var/log/idp-server.log
```

## 監視・アラートでの活用

### Elasticsearch/ELK Stack
```json
{
  "query": {
    "bool": {
      "must": [
        {"terms": {"tags": ["mfa", "failure"]}}
      ]
    }
  }
}
```

### Splunk
```spl
index=idp-server tags="failure" tags="oauth"
```

### Grafana Loki
```logql
{job="idp-server"} |= `"tags":["mfa","failure"]`
```

## ダッシュボードでの可視化

### カテゴリ別イベント分布
```bash
# 過去1時間のカテゴリ別集計
grep "$(date -d '1 hour ago' '+%Y-%m-%d %H'):" /var/log/idp-server.log | \
grep -o '"tags":\["[^"]*"' | \
sort | uniq -c | sort -nr
```

### エラー率計算
```bash
# MFA関連のエラー率
TOTAL_MFA=$(grep '"tags":.*"mfa"' /var/log/idp-server.log | wc -l)
FAILED_MFA=$(grep '"tags":.*"mfa".*"failure"' /var/log/idp-server.log | wc -l)
echo "MFAエラー率: $(($FAILED_MFA * 100 / $TOTAL_MFA))%"
```

## カスタムタグの追加

現在は自動生成のみですが、将来的にテナント設定でカスタムタグを追加できる予定です：

```yaml
# 将来の拡張予定
security_event_log_custom_tags: "environment:production,region:ap-northeast-1"
```

## 注意事項

- tagsは `structured_json` フォーマットでのみ生成されます
- `simple` フォーマットではtagsは出力されません  
- tagsの生成ルールは実装に依存するため、バージョンアップ時に変更される可能性があります

---

**対応実装**: `StructuredJsonLogFormatter.java` (getEventCategory メソッド)  
**最終更新**: 2025年9月11日