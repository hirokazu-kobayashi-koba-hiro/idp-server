# セキュリティイベントログ設定例

## 基本設定

### デフォルト設定（シンプルログ）
```yaml
# TenantAttributes での設定
security_event_log_format: "simple"                        # シンプルテキスト形式
security_event_log_stage: "processed"                      # 処理完了時のみログ出力
security_event_debug_logging: false                        # デバッグログ無効
```

### 本番環境推奨設定（JSON構造化ログ）
```yaml
# 可観測性プラットフォーム向け設定
security_event_log_format: "structured_json"               # JSON形式
security_event_log_stage: "processed"                      # 処理完了時のみ
security_event_debug_logging: false                        # デバッグログ無効

# 出力項目制御
security_event_log_include_user_id: true                   # ユーザーID含める
security_event_log_include_user_ex_sub: true               # 外部ユーザーID含める
security_event_log_include_client_id: true                 # クライアントID含める  
security_event_log_include_ip: true                        # IPアドレス含める
security_event_log_include_user_agent: false               # UserAgentは除外
security_event_log_include_detail: false                   # 詳細情報は除外

# セキュリティ・機密情報保護
security_event_log_detail_scrub_keys: "authorization,cookie,set-cookie,proxy-authorization,password,secret,token,refresh_token,access_token,id_token"

# サービス識別
security_event_log_service_name: "idp-server-prod"         # サービス名
security_event_log_custom_tags: "environment:production,region:ap-northeast-1"
```

### 開発環境設定（詳細ログ）
```yaml
# 開発・デバッグ向け設定
security_event_log_format: "structured_json"               # JSON形式
security_event_log_stage: "both"                           # 受信時・処理時両方
security_event_debug_logging: true                         # デバッグログ有効

# すべての項目を出力
security_event_log_include_user_id: true
security_event_log_include_user_ex_sub: true               # 外部ユーザーIDも出力
security_event_log_include_client_id: true  
security_event_log_include_ip: true
security_event_log_include_user_agent: true                # 開発では有効
security_event_log_include_detail: true                    # 詳細情報も含める

# 開発環境識別
security_event_log_service_name: "idp-server-dev"
security_event_log_custom_tags: "environment:development,developer:kobayashi"
```

## プラットフォーム別最適化

### Datadog向け設定
```yaml
security_event_log_format: "structured_json"
security_event_log_service_name: "idp-server"
security_event_log_custom_tags: "env:prod,team:auth,version:1.0.0"
security_event_log_tracing_enabled: true                   # APM連携用
security_event_log_include_trace_context: true             # トレースID含める
```

### ELK Stack向け設定
```yaml
security_event_log_format: "structured_json"
security_event_log_service_name: "idp-server"
# Logstashでのパースが容易な形式
security_event_log_include_user_agent: true
security_event_log_include_detail: true
```

### Splunk向け設定
```yaml
security_event_log_format: "key_value"                     # Key-Value形式
security_event_log_service_name: "idp-server"
# Splunkでの検索最適化
security_event_log_include_ip: true
security_event_log_include_user_id: true
```

### Grafana Loki向け設定
```yaml
security_event_log_format: "simple"                        # シンプル形式
# Loki用のラベル最小化
security_event_log_include_user_agent: false
security_event_log_include_detail: false
```

## ログレベル別出力例

### INFO レベル（成功イベント）
```json
{
  "event_type": "password_success",
  "timestamp": "2025-09-10T10:30:00.123Z",
  "tenant_id": "67e7eae6-62b0-4500-9eff-87459f63fc66",
  "user_id": "user123",
  "user_ex_sub": "external_user_12345",
  "client_id": "my-app",
  "ip_address": "192.168.1.100",
  "tags": ["authentication", "success"],
  "stage": "processed",
  "hooks_executed": 1,
  "processing_time_ms": 45
}
```

### WARN レベル（失敗イベント）
```json
{
  "event_type": "password_failure", 
  "timestamp": "2025-09-10T10:30:05.456Z",
  "tenant_id": "67e7eae6-62b0-4500-9eff-87459f63fc66",
  "user_id": "user123",
  "user_ex_sub": "external_user_12345",
  "client_id": "my-app",
  "ip_address": "192.168.1.100",
  "tags": ["authentication", "failure"],
  "stage": "processed",
  "hooks_executed": 2,
  "processing_time_ms": 67
}
```

### ERROR レベル（重大イベント）
```json
{
  "event_type": "user_deletion",
  "timestamp": "2025-09-10T10:35:00.789Z", 
  "tenant_id": "67e7eae6-62b0-4500-9eff-87459f63fc66",
  "user_id": "user123",
  "tags": ["user_management"],
  "stage": "processed",
  "hooks_executed": 3,
  "processing_time_ms": 234
}
```

## ログ無効化設定

### 完全無効化
```yaml
security_event_log_format: "disabled"                      # ログ出力無効
```

### ステージ別制御
```yaml
security_event_log_format: "simple"
security_event_log_stage: "processed"                      # 受信時ログは無効
security_event_debug_logging: false                        # デバッグログ無効
```

## パフォーマンス考慮事項

### 高負荷環境向け最小構成
```yaml
# 最小限の出力で性能優先
security_event_log_format: "simple"
security_event_log_stage: "processed"
security_event_log_include_user_id: true
security_event_log_include_client_id: false                # 必要最小限に絞る
security_event_log_include_ip: false
security_event_log_include_user_agent: false
security_event_log_include_detail: false
```

### ログ容量削減設定
```yaml
# 重要なイベントのみログ出力
security_event_log_format: "structured_json"
# 失敗イベントのみログ出力する場合は追加フィルタリング実装が必要
security_event_log_include_detail: false                   # 詳細情報除外でサイズ削減
security_event_log_include_user_agent: false               # 長いUserAgent除外
```

## 設定の適用方法

### テナント別設定
```java
// 管理画面またはAPI経由で設定
PUT /api/v1/tenants/{tenant_id}/attributes
{
  "security_event_log_format": "structured_json",
  "security_event_log_stage": "processed",
  "security_event_log_include_ip": true
}
```

### デフォルト設定
```yaml
# application.yml
idp:
  tenants:
    default:
      attributes:
        security_event_log_format: "simple"
        security_event_log_stage: "processed"
```

## セキュリティ考慮事項

### 機密情報のスクラブ設定

セキュリティイベントの詳細情報（`event_detail`）には機密情報が含まれる場合があります。本番環境では必ず機密情報をスクラブする設定を有効にしてください。

#### 推奨スクラブキー設定
```yaml
# 本番環境で必須の設定
security_event_log_detail_scrub_keys: "authorization,cookie,set-cookie,proxy-authorization,password,secret,token,refresh_token,access_token,id_token,client_secret,api_key,bearer"
```

#### スクラブされる機密情報
- **認証ヘッダー**: `Authorization`, `Proxy-Authorization`  
- **Cookieデータ**: `Cookie`, `Set-Cookie`
- **トークン類**: `access_token`, `refresh_token`, `id_token`
- **秘密鍵**: `password`, `secret`, `client_secret`, `api_key`
- **その他**: `bearer` トークン等

#### スクラブ例
```json
// スクラブ前
{
  "event_detail": {
    "headers": {
      "authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "cookie": "session=abc123; auth=xyz789"
    }
  }
}

// スクラブ後  
{
  "event_detail": {
    "headers": {
      "authorization": "[SCRUBBED]",
      "cookie": "[SCRUBBED]"
    }
  }
}
```

### コンプライアンス対応

#### GDPR/CCPA準拠
- 個人識別情報（PII）の適切なマスキング
- データ保持期間の設定
- 忘れられる権利への対応

#### SOC2/ISO27001準拠
- 機密情報の完全なスクラブ
- 監査ログの完全性保証
- アクセス制御の実装

## トラブルシューティング

### ログが出力されない場合
1. `security_event_log_format` が `"disabled"` になっていないか確認
2. ログレベルが適切に設定されているか確認（INFO以上）
3. テナント設定が正しく反映されているか確認

### ログ容量が多すぎる場合
1. `security_event_log_stage` を `"processed"` のみに設定
2. `security_event_debug_logging` を `false` に設定
3. 不要な項目の include 設定を `false` に変更
4. `security_event_log_include_detail` を `false` に設定

### 機密情報が漏洩している場合
1. `security_event_log_detail_scrub_keys` の設定確認
2. スクラブキーのリストに不足がないか確認
3. 既存ログファイルの調査・削除検討

### JSON形式でログが出力されない場合
1. `JsonNodeWrapper` の動作確認
2. `security_event_log_format` が `"structured_json"` に設定されているか確認
3. ログ出力でエラーが発生していないか確認（フォールバック動作）