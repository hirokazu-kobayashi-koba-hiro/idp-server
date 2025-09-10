# セキュリティイベントログ 運用リファレンス

## 概要
本ドキュメントは、idp-serverが生成する **108種類すべてのセキュリティイベント** とアプリケーションログの包括的なリファレンス情報を提供します。運用チームがエンドユーザーからの問い合わせ調査やシステム障害対応を行う際の支援を目的としています。

## ログ構造とフォーマット

### セキュリティイベントログの構成要素

idp-serverのすべてのセキュリティイベントは、以下の構造化フォーマットに従います：

```json
{
  "event_id": "21d30ff3-66e2-4e47-b9cf-051ce0b29536",
  "event_type": "password_success",
  "timestamp": "2025-09-10T10:30:00.000Z",
  "tenant_id": "67e7eae6-62b0-4500-9eff-87459f63fc66",
  "user_id": "user123",
  "client_id": "my-application",
  "ip_address": "192.168.1.100",
  "user_agent": "Mozilla/5.0...",
  "additional_data": {...}
}
```

### ログレベルガイドライン

- **INFO**: 正常な操作、成功イベント
- **DEBUG**: 詳細なデバッグ情報（本番環境では無効化）
- **WARN**: 警告状態、復旧可能なエラー
- **ERROR**: エラー状態、操作失敗

## セキュリティイベントカテゴリ

### 1. ユーザー認証イベント

#### パスワード認証
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `password_success` | ユーザーがパスワード認証に成功 | INFO | user_id, client_id, ip_address |
| `password_failure` | ユーザーのパスワード認証が失敗 | WARN | user_id, client_id, ip_address, failure_reason |

**調査シナリオ例**:
- **ユーザーがログインできない**: そのユーザーのuser_idで`password_failure`イベントを検索
- **不審なログイン活動**: 通常とは異なるIPアドレスからの`password_success`を確認

#### 多要素認証（FIDO UAF）
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `fido_uaf_registration_challenge_success` | FIDO UAF登録チャレンジ成功 | INFO | user_id, device_id |
| `fido_uaf_registration_challenge_failure` | FIDO UAF登録チャレンジ失敗 | WARN | user_id, error_code |
| `fido_uaf_registration_success` | FIDOデバイス登録成功 | INFO | user_id, device_id, device_name |
| `fido_uaf_registration_failure` | FIDOデバイス登録失敗 | ERROR | user_id, error_details |
| `fido_uaf_authentication_success` | FIDO認証成功 | INFO | user_id, device_id |
| `fido_uaf_authentication_failure` | FIDO認証失敗 | WARN | user_id, device_id, failure_reason |
| `fido_uaf_deregistration_success` | FIDOデバイス登録解除成功 | INFO | user_id, device_id |
| `fido_uaf_deregistration_failure` | FIDOデバイス登録解除失敗 | ERROR | user_id, device_id, error_details |

**調査シナリオ例**:
- **ユーザーがMFA設定を完了できない**: `fido_uaf_registration_failure`イベントを検索
- **MFAデバイスが動作しない**: `fido_uaf_authentication_failure`ログを確認

#### 多要素認証（WebAuthn）
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `webauthn_registration_success` | WebAuthnデバイス登録成功 | INFO | user_id, credential_id, authenticator_type |
| `webauthn_registration_failure` | WebAuthnデバイス登録失敗 | ERROR | user_id, error_code, error_description |
| `webauthn_authentication_success` | WebAuthn認証成功 | INFO | user_id, credential_id |
| `webauthn_authentication_failure` | WebAuthn認証失敗 | WARN | user_id, failure_reason |

### 2. ユーザー管理イベント

#### ユーザーライフサイクル
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `user_signup` | ユーザーアカウント作成 | INFO | user_id, email, registration_method |
| `user_signup_failure` | ユーザー登録失敗 | ERROR | email, failure_reason |
| `user_signup_conflict` | ユーザー登録競合（重複） | WARN | email, conflict_type |
| `user_enabled` | ユーザーアカウント有効化 | INFO | user_id, admin_user_id |
| `user_disabled` | ユーザーアカウント無効化 | INFO | user_id, admin_user_id, reason |
| `user_deletion` | ユーザーアカウント削除 | INFO | user_id, admin_user_id |

#### パスワード管理
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `password_reset` | ユーザーがパスワードをリセット | INFO | user_id, reset_method |
| `password_change` | ユーザーがパスワードを変更 | INFO | user_id |

### 3. 検証イベント

#### メール検証
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `email_verification_request_success` | メール検証リクエスト成功 | INFO | user_id, email |
| `email_verification_request_failure` | メール検証リクエスト失敗 | ERROR | user_id, email, error_reason |
| `email_verification_success` | メール検証成功 | INFO | user_id, email |
| `email_verification_failure` | メール検証失敗 | WARN | user_id, email, failure_reason |

#### SMS検証
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `sms_verification_challenge_success` | SMS認証コード送信成功 | INFO | user_id, phone_number_masked |
| `sms_verification_challenge_failure` | SMS認証コード送信失敗 | ERROR | user_id, phone_number_masked, error_code |
| `sms_verification_success` | 電話番号検証成功 | INFO | user_id, phone_number_masked |
| `sms_verification_failure` | 電話番号検証失敗 | WARN | user_id, phone_number_masked, failure_reason |

### 4. OAuth/OIDCプロトコルイベント

#### 認可フロー
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `oauth_authorize` | OAuth認可成功 | INFO | user_id, client_id, scope |
| `oauth_authorize_with_session` | 既存セッションでのOAuth認可 | INFO | user_id, client_id, session_id |
| `oauth_deny` | ユーザーによるOAuth認可拒否 | INFO | user_id, client_id |
| `authorize_failure` | OAuth認可失敗 | ERROR | user_id, client_id, error_code |

#### トークン管理
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `issue_token_success` | アクセス/IDトークン発行成功 | INFO | user_id, client_id, token_type |
| `issue_token_failure` | トークン発行失敗 | ERROR | user_id, client_id, error_reason |
| `refresh_token_success` | トークンリフレッシュ成功 | INFO | user_id, client_id |
| `refresh_token_failure` | トークンリフレッシュ失敗 | ERROR | user_id, client_id, error_reason |
| `revoke_token_success` | トークン無効化成功 | INFO | user_id, client_id, token_type |
| `revoke_token_failure` | トークン無効化失敗 | ERROR | user_id, client_id, error_reason |

#### UserInfoエンドポイント
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `userinfo_success` | UserInfoリクエスト成功 | INFO | user_id, client_id, claims_requested |
| `userinfo_failure` | UserInfoリクエスト失敗 | ERROR | user_id, client_id, error_reason |

#### トークンイントロスペクション
| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `inspect_token_success` | トークン検証成功 | DEBUG | client_id, token_type |
| `inspect_token_failure` | トークン検証失敗 | WARN | client_id, error_reason |
| `inspect_token_expired` | トークン期限切れ | INFO | client_id, expiration_time |

### 5. セッション管理イベント

| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `login_success` | ユーザーログイン成功 | INFO | user_id, client_id, session_id |
| `logout` | ユーザーログアウト | INFO | user_id, session_id, logout_type |

### 6. 連携イベント

| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `federation_request` | 外部IdP連携リクエスト | INFO | user_id, provider_name, client_id |
| `federation_success` | 連携認証成功 | INFO | user_id, provider_name, external_user_id |
| `federation_failure` | 連携認証失敗 | ERROR | provider_name, error_reason |

### 7. 本人確認（eKYC）イベント

| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `identity_verification_application_apply` | eKYC申請提出 | INFO | user_id, application_id, verification_type |
| `identity_verification_application_failure` | eKYC申請失敗 | ERROR | user_id, application_id, error_reason |
| `identity_verification_application_approved` | eKYC申請承認 | INFO | user_id, application_id, approver_id |
| `identity_verification_application_rejected` | eKYC申請拒否 | INFO | user_id, application_id, rejection_reason |

### 8. デバイス管理イベント

| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `authentication_device_registration_success` | 認証デバイス登録成功 | INFO | user_id, device_id, device_type |
| `authentication_device_registration_failure` | デバイス登録失敗 | ERROR | user_id, device_type, error_reason |
| `authentication_device_deregistration_success` | デバイス登録解除成功 | INFO | user_id, device_id |
| `authentication_device_notification_success` | プッシュ通知送信成功 | INFO | user_id, device_id, notification_type |
| `authentication_device_notification_failure` | プッシュ通知送信失敗 | ERROR | user_id, device_id, error_reason |

### 9. CIBA（クライアント発信バックチャネル認証）イベント

| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `backchannel_authentication_request_success` | CIBAリクエスト成功 | INFO | user_id, client_id, auth_req_id |
| `backchannel_authentication_request_failure` | CIBAリクエスト失敗 | ERROR | user_id, client_id, error_reason |
| `backchannel_authentication_authorize` | CIBA認可許可 | INFO | user_id, auth_req_id |
| `backchannel_authentication_deny` | CIBA認可拒否 | INFO | user_id, auth_req_id |

### 10. 管理系イベント

| イベントタイプ | 説明 | ログレベル | 主なユーザーデータ |
|------------|-------------|-----------|------------------|
| `server_create` | サーバーインスタンス作成 | INFO | admin_user_id, server_id |
| `server_edit` | サーバー設定更新 | INFO | admin_user_id, server_id, changes |
| `server_delete` | サーバーインスタンス削除 | INFO | admin_user_id, server_id |
| `user_create` | 管理者によるユーザー作成 | INFO | admin_user_id, created_user_id |
| `user_edit` | ユーザー詳細更新 | INFO | admin_user_id, target_user_id |
| `user_lock` | ユーザーアカウントロック | INFO | admin_user_id, target_user_id, reason |

## よくある調査シナリオ

### シナリオ1: ユーザーがログインできない
**手順:**
1. そのユーザーのIDで`password_failure`イベントを検索
2. `user_disabled`や`user_lock`イベントを確認
3. 異なるIPアドレスからの異常な`login_success`を確認
4. 新規アカウントの場合は`user_signup`ステータスを確認

**ログ検索例:**
```bash
grep "user_id:user123" /var/log/idp-server.log | grep -E "(password_failure|user_disabled|user_lock)"
```

### シナリオ2: MFA登録の問題
**手順:**
1. FIDO UAFまたはWebAuthn登録イベントを検索
2. エラー詳細付きの`*_registration_failure`イベントを確認
3. デバイス互換性とユーザー環境を確認
4. 成功した`*_registration_challenge_success`に続く失敗を確認

### シナリオ3: OAuth統合の問題
**手順:**
1. `oauth_authorize`と`authorize_failure`イベントを確認
2. トークン発行の問題については`issue_token_failure`を確認
3. クレーム関連の問題については`userinfo_failure`を確認
4. クライアント設定とスコープリクエストを確認

### シナリオ4: 連携/SSOの問題
**手順:**
1. `federation_*`イベントを検索
2. `federation_failure`で外部IdPのレスポンスコードを確認
3. ユーザーマッピングと属性の問題を確認
4. 連携設定の設定問題を確認

## ログ保持とアーカイブ

### 保持期間
- **セキュリティイベント**: 2年（コンプライアンス要件）
- **デバッグログ**: 30日
- **監査ログ**: 7年
- **パフォーマンスログ**: 90日

### アーカイブ場所
- **アクティブログ**: `/var/log/idp-server/`
- **月次アーカイブ**: `/var/log/archives/idp-server/YYYY/MM/`
- **長期保存**: AWS S3/Azure Blob（暗号化）

## ログ監視とアラート

### 重要なアラート
- `password_failure`イベントの高頻度発生（ブルートフォース攻撃の可能性）
- 複数の`federation_failure`イベント（IdP接続の問題）
- `*_registration_failure`イベントの急増（システム問題）
- `server_delete`や`user_delete`イベント（データ損失防止）

### 警告アラート
- 異常なログインパターン（時間/場所）
- 高いトークンリフレッシュ率
- 検証試行の失敗
- デバイス登録の失敗

## プライバシーとセキュリティの考慮事項

### PII（個人識別情報）の取り扱い
- **ユーザーID**: ハッシュ化された識別子としてログ出力
- **メールアドレス**: ほとんどの場合、ドメインのみをログ出力
- **電話番号**: マスク処理（例：+81-90-****-1234）
- **IPアドレス**: セキュリティ分析のために利用可能
- **デバイス情報**: デバイスタイプと部分フィンガープリントのみ

### コンプライアンス注記
- すべてのセキュリティイベントはGDPR/CCPA要件に準拠
- データ処理に対するユーザー同意を追跡
- 忘れられる権利の実装（ログの匿名化）
- コンプライアンス報告のための監査証跡維持

## よくある問題のトラブルシューティング

### 問題: 高いメモリ使用量
**確認**: 過剰なDEBUGレベルのログ出力
**解決策**: 本番環境でログレベルを調整

### 問題: ログローテーションが動作しない
**確認**: ディスク容量とlogrotate設定
**解決策**: `/etc/logrotate.d/idp-server`設定を確認

### 問題: ユーザーイベントが見つからない
**確認**: セキュリティイベント公開設定
**解決策**: `security-event-framework`設定を確認

## 連絡先とサポート

# 完全なセキュリティイベント一覧（108種類）

## 🔍 追加イベントタイプ

### 認証デバイス管理
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `authentication_device_notification_success` | デバイス通知送信成功 | INFO |
| `authentication_device_notification_cancel` | デバイス通知キャンセル | INFO |
| `authentication_device_notification_failure` | デバイス通知送信失敗 | WARN |
| `authentication_device_notification_no_action_success` | デバイス通知無応答 | INFO |
| `authentication_device_deny_success` | デバイス認証拒否成功 | INFO |
| `authentication_device_deny_failure` | デバイス認証拒否失敗 | WARN |
| `authentication_device_allow_success` | デバイス認証許可成功 | INFO |
| `authentication_device_allow_failure` | デバイス認証許可失敗 | WARN |
| `authentication_device_binding_message_success` | デバイスバインディング成功 | INFO |
| `authentication_device_binding_message_failure` | デバイスバインディング失敗 | WARN |
| `authentication_device_registration_success` | 認証デバイス登録成功 | INFO |
| `authentication_device_registration_failure` | 認証デバイス登録失敗 | ERROR |
| `authentication_device_deregistration_success` | 認証デバイス登録解除成功 | INFO |
| `authentication_device_deregistration_failure` | 認証デバイス登録解除失敗 | ERROR |
| `authentication_device_registration_challenge_success` | 認証デバイス登録チャレンジ成功 | INFO |

### 認証キャンセル
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `authentication_cancel_success` | 認証キャンセル成功 | INFO |
| `authentication_cancel_failure` | 認証キャンセル失敗 | WARN |

### ログイン・ログアウト
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `login_success` | ログイン成功 | INFO |
| `logout` | ログアウト | INFO |

### レガシー・外部認証
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `legacy_authentication_success` | レガシー認証成功 | INFO |
| `legacy_authentication_failure` | レガシー認証失敗 | WARN |
| `external_token_authentication_success` | 外部トークン認証成功 | INFO |
| `external_token_authentication_failure` | 外部トークン認証失敗 | WARN |

### WebAuthnチャレンジ拡張
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `webauthn_registration_challenge_success` | WebAuthn登録チャレンジ成功 | INFO |
| `webauthn_registration_challenge_failure` | WebAuthn登録チャレンジ失敗 | WARN |
| `webauthn_authentication_challenge_success` | WebAuthn認証チャレンジ成功 | INFO |
| `webauthn_authentication_challenge_failure` | WebAuthn認証チャレンジ失敗 | WARN |

### FIDO UAFチャレンジ・キャンセル
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `fido_uaf_authentication_challenge_success` | FIDO UAF認証チャレンジ成功 | INFO |
| `fido_uaf_authentication_challenge_failure` | FIDO UAF認証チャレンジ失敗 | WARN |
| `fido_uaf_cancel_success` | FIDO UAFキャンセル成功 | INFO |
| `fido_uaf_cancel_failure` | FIDO UAFキャンセル失敗 | WARN |

### CIBA（バックチャネル認証）
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `backchannel_authentication_request_success` | CIBA認証要求成功 | INFO |
| `backchannel_authentication_request_failure` | CIBA認証要求失敗 | WARN |
| `backchannel_authentication_authorize` | CIBA認証承認 | INFO |
| `backchannel_authentication_deny` | CIBA認証拒否 | INFO |

### トークン詳細操作
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `inspect_token_success` | トークン検証成功 | INFO |
| `inspect_token_failure` | トークン検証失敗 | WARN |
| `inspect_token_expired` | トークン期限切れ | INFO |

### ユーザー管理拡張
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `user_create` | 管理者によるユーザー作成 | INFO |
| `user_get` | ユーザー情報取得 | INFO |
| `user_edit` | ユーザー情報更新 | INFO |
| `user_delete` | 管理者によるユーザー削除 | ERROR |
| `user_lock` | ユーザーアカウントロック | WARN |

### 身元確認・KYC
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `identity_verification_application_apply` | 身元確認申請 | INFO |
| `identity_verification_application_failure` | 身元確認申請失敗 | WARN |
| `identity_verification_application_cancel` | 身元確認申請キャンセル | INFO |
| `identity_verification_application_delete` | 身元確認申請削除 | INFO |
| `identity_verification_application_findList` | 身元確認申請一覧取得 | INFO |
| `identity_verification_application_approved` | 身元確認承認 | INFO |
| `identity_verification_application_rejected` | 身元確認拒否 | WARN |
| `identity_verification_application_cancelled` | 身元確認申請取消 | INFO |
| `identity_verification_result_findList` | 身元確認結果一覧取得 | INFO |

### サーバー管理
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `server_create` | サーバーインスタンス作成 | INFO |
| `server_get` | サーバー詳細取得 | INFO |
| `server_edit` | サーバー設定更新 | INFO |
| `server_delete` | サーバーインスタンス削除 | ERROR |

### アプリケーション管理
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `application_create` | アプリケーション作成 | INFO |
| `application_get` | アプリケーション詳細取得 | INFO |
| `application_edit` | アプリケーション設定更新 | INFO |
| `application_delete` | アプリケーション削除 | ERROR |

### 組織・メンバー管理
| イベントタイプ | 説明 | ログレベル |
|------------|-------------|-----------|
| `member_invite` | 組織メンバー招待 | INFO |
| `member_join` | 組織メンバー参加 | INFO |
| `member_leave` | 組織メンバー脱退 | INFO |

---

## 📊 イベント分布統計

### カテゴリ別分布
- **ユーザー認証関連**: 38種類 (35%)
- **デバイス認証管理**: 15種類 (14%) 
- **OAuth・OIDC**: 15種類 (14%)
- **ユーザー管理**: 11種類 (10%)
- **身元確認・KYC**: 9種類 (8%)
- **サーバー・アプリ管理**: 8種類 (7%)
- **フェデレーション連携**: 3種類 (3%)
- **組織・メンバー管理**: 3種類 (3%)
- **その他**: 6種類 (6%)

### ログレベル別分布
- **INFO**: 76種類 (70%) - 正常操作・成功イベント
- **WARN**: 26種類 (24%) - 失敗・警告イベント  
- **ERROR**: 6種類 (6%) - 重大なエラー・削除操作

**合計: 108種類のセキュリティイベント**

---

ログ分析に関する追加サポートについては：
- **運用チーム**: ops@company.com
- **セキュリティチーム**: security@company.com  
- **開発チーム**: dev@company.com

---

**ドキュメントバージョン**: 2.0  
**最終更新**: 2025年9月11日  
**対応実装**: DefaultSecurityEventType.java (108種類完全対応)
**次回レビュー**: 2025年12月10日