# セキュリティイベント クイックリファレンスガイド

## 最頻出ユーザーサポートシナリオ

### 🔑 パスワード関連の問題
```bash
# ユーザーがログインできないという報告
grep "password_failure" /var/log/idp-server.log | grep "user_id:USER123"

# ログ出力例:
# 2025-09-10 10:30:00 WARN [UserEventCreator] password_failure: user_id=user123, client_id=my-app, ip=192.168.1.100, failure_reason=invalid_password
```

### 📱 MFA関連の問題  
```bash
# FIDO UAFデバイスの問題
grep -E "(fido_uaf_.*_failure|webauthn_.*_failure)" /var/log/idp-server.log | grep "user_id:USER123"

# ログ出力例:
# 2025-09-10 10:35:00 ERROR [UserEventCreator] fido_uaf_registration_failure: user_id=user123, device_id=dev456, error=device_not_supported
```

### ✉️ メール認証の問題
```bash
# メール認証の問題
grep -E "email_verification_.*_(failure|success)" /var/log/idp-server.log | grep "user_id:USER123"

# ログ出力例:
# 2025-09-10 10:40:00 ERROR [UserEventCreator] email_verification_failure: user_id=user123, email=u***@example.com, error=smtp_timeout
```

### 🔗 OAuth/連携の問題
```bash
# OAuth認可の問題
grep -E "(oauth_.*_failure|federation_failure)" /var/log/idp-server.log | grep "client_id:CLIENT123"

# ログ出力例:
# 2025-09-10 10:45:00 ERROR [UserEventCreator] oauth_authorize_failure: user_id=user123, client_id=client123, error=invalid_scope
```

## よく使うログ検索コマンド

### ユーザーIDで検索
```bash
grep "user_id:USER123" /var/log/idp-server.log | tail -20
```

### 時間範囲で検索（過去1時間）
```bash
grep "$(date -d '1 hour ago' '+%Y-%m-%d %H'):" /var/log/idp-server.log
```

### 失敗イベントのみ
```bash
grep -E "_(failure|failed)" /var/log/idp-server.log | tail -20
```

### 特定ユーザーの成功イベント
```bash
grep "user_id:USER123" /var/log/idp-server.log | grep -E "_(success|successful)"
```

## アラートパターン

### 🚨 重大な問題
- 同一IPからの複数`password_failure`: **ブルートフォース攻撃の可能性**
- `user_deletion`イベント: **データ損失リスク** 
- `server_delete`イベント: **設定損失**

### ⚠️ 警告パターン
- 高い`federation_failure`発生率: **外部IdP接続問題**
- `*_registration_failure`の急増: **システム/デバイス互換性問題**
- 異常なログイン時間/場所: **アカウント侵害の可能性**

## イベントカテゴリクイックマップ

| 問題カテゴリ | 検索パターン | よくある原因 |
|-------------|-------------|-------------|
| **ログイン問題** | `password_failure\|user_disabled\|user_lock` | パスワード間違い、アカウントロック、無効化ユーザー |
| **MFA問題** | `fido_uaf_.*_failure\|webauthn_.*_failure` | デバイス互換性、ネットワーク問題、ユーザーエラー |
| **メール問題** | `email_verification_.*_failure` | SMTP問題、無効メール、ネットワークタイムアウト |
| **OAuthエラー** | `oauth_.*_failure\|authorize_failure` | 無効スコープ、クライアント設定ミス、トークン問題 |
| **連携** | `federation_failure` | 外部IdPダウン、設定エラー、ネットワーク |

## ログフォーマット例

### 典型的な成功イベント
```
2025-09-10 10:30:00.123 INFO [UserEventCreator] password_success: user_id=abc123, user_sub=1234567890, client_id=my-app, ip_address=192.168.1.100, session_id=sess_xyz789
```

### 典型的な失敗イベント  
```
2025-09-10 10:30:05.456 WARN [UserEventCreator] password_failure: user_id=abc123, client_id=my-app, ip_address=192.168.1.100, failure_reason=invalid_password, attempt_count=3
```

### デバイス登録イベント
```
2025-09-10 10:35:00.789 INFO [UserEventCreator] fido_uaf_registration_success: user_id=abc123, device_id=dev456, device_name=iPhone_Touch_ID, authenticator_type=fingerprint
```

### OAuth認可イベント
```
2025-09-10 10:40:00.321 INFO [UserEventCreator] oauth_authorize: user_id=abc123, client_id=my-app, scope="openid profile email", redirect_uri=https://app.example.com/callback
```

## 緊急時対応コマンド

### ユーザーの全失敗イベントを検索（過去24時間）
```bash
grep "user_id:USER123" /var/log/idp-server.log | \
grep "$(date '+%Y-%m-%d')" | \
grep -E "_(failure|failed|error)" | \
sort -k1,2
```

### システム正常性チェック（エラー率）
```bash
# 過去1時間の総イベント数
TOTAL=$(grep "$(date -d '1 hour ago' '+%Y-%m-%d %H'):" /var/log/idp-server.log | wc -l)

# 過去1時間の失敗イベント数  
FAILED=$(grep "$(date -d '1 hour ago' '+%Y-%m-%d %H'):" /var/log/idp-server.log | grep -E "_(failure|failed)" | wc -l)

echo "エラー率: $(($FAILED * 100 / $TOTAL))% ($FAILED/$TOTAL)"
```

### セキュリティインシデント対応
```bash
# 不審な活動パターンを確認
grep -E "(password_failure|login_success)" /var/log/idp-server.log | \
grep "ip_address:" | \
awk '{print $NF}' | sort | uniq -c | sort -nr | head -10
```

## よくあるエラーメッセージと対処法

### パスワード認証エラー
| エラーメッセージ | 意味 | 対処法 |
|--------------|-----|-------|
| `invalid_password` | パスワードが間違っている | ユーザーにパスワード確認を促す |
| `account_locked` | アカウントがロックされている | 管理者によるアカウント解除が必要 |
| `account_disabled` | アカウントが無効化されている | 管理者による有効化が必要 |

### MFA関連エラー
| エラーメッセージ | 意味 | 対処法 |
|--------------|-----|-------|
| `device_not_supported` | デバイスがサポート対象外 | 対応デバイスリスト確認 |
| `registration_timeout` | 登録時にタイムアウト | ネットワーク状況確認、再試行 |
| `invalid_signature` | 署名検証失敗 | デバイスの再登録が必要 |

### OAuth/連携エラー
| エラーメッセージ | 意味 | 対処法 |
|--------------|-----|-------|
| `invalid_scope` | 無効なスコープリクエスト | クライアント設定確認 |
| `redirect_uri_mismatch` | リダイレクトURI不一致 | クライアント登録情報確認 |
| `provider_unavailable` | 外部IdPが利用不可 | 外部サービス状況確認 |

## サポート手順テンプレート

### 1. ログイン問題の調査手順
1. **基本情報収集**
   - ユーザーID、発生時刻、使用ブラウザ
   - エラーメッセージのスクリーンショット

2. **ログ確認**
   ```bash
   grep "user_id:${USER_ID}" /var/log/idp-server.log | grep "$(date '+%Y-%m-%d')"
   ```

3. **状況判断**
   - `password_failure`: パスワードエラー → パスワードリセット案内
   - `user_disabled`: アカウント無効 → 管理者対応依頼
   - `user_lock`: アカウントロック → 解除手続き案内

### 2. MFA設定問題の調査手順
1. **デバイス情報確認**
   - OS、ブラウザ、デバイスタイプ

2. **登録ログ確認**
   ```bash
   grep "user_id:${USER_ID}" /var/log/idp-server.log | grep -E "registration_(success|failure)"
   ```

3. **エラー内容に応じた案内**
   - デバイス非対応 → 対応デバイス案内
   - ネットワークエラー → 環境確認依頼

## 日次チェック項目

### 毎日確認すべき項目
- [ ] 過去24時間のエラー率（5%以下が正常）
- [ ] 異常なIPからのアクセス数
- [ ] `federation_failure`の発生回数
- [ ] 新規ユーザー登録数とエラー率

### 週次確認項目  
- [ ] ログローテーション正常動作
- [ ] ディスク容量使用率
- [ ] 長期トレンド分析

---

**💡 プロTip**: ユーザーサポート通話中は `tail -f /var/log/idp-server.log | grep "user_id:USER123"` でリアルタイム監視

**🔧 ログローテーション**: ログは日次でローテーション。過去データは `/var/log/archives/idp-server/` を確認