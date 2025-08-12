# ユーザー削除設計ポリシー

## 🧹 基本的な削除方針

| カテゴリ     | ストラテジー    | 説明                              |
|----------|-----------|---------------------------------|
| コアデータ    | 物理削除      | 例: `idp_user`, `idp_user_roles` |
| ログ・履歴    | 論理削除 / 保持 | 監査ログ、同意履歴、申請履歴は保持               |
| 外部サービス連携 | 非同期削除     | FIDO、VCはフック経由で処理                |

---

## ✅ 物理削除対象のテーブル

| テーブル名                          | 説明                       |
|--------------------------------|--------------------------|
| `idp_user`                     | ユーザーのメインレコード             |
| `idp_user_roles`               | ユーザーのロール割当               |
| `idp_user_permission_override` | ユーザー固有の権限オーバーライド         |
| `oauth_token`                  | 発行されたアクセストークン／リフレッシュトークン |
| `authorization_code_grant`     | 認可コードの情報                 |
| `authentication_transaction`   | 認証トランザクションの記録（user_id付き） |
| `authentication_interactions`  | MFAやPasskeyなどの認証ステップの履歴  |
| `federation_sso_session`       | フェデレーションログインセッション        |
| `ciba_grant`                   | CIBAの認証グラント情報            |

---

## 🔄 論理削除やフラグ更新対象のテーブル

| テーブル名                              | アクション                           | 説明         |
|------------------------------------|---------------------------------|------------|
| `authorization_granted`            | `revoked_at` を設定                | ユーザーの同意記録  |
| `identity_verification_application` | `status = 'deleted'` を設定        | eKYC申請履歴   |
| `identity_verification_result`     | `source = 'deleted_user'` などを設定 | 身元確認結果     |
| `verifiable_credential_transaction` | `status = 'revoked'` に変更        | VC発行・取消の記録 |

---

## 🕵️‍♀️ 監査・ログデータ（削除しない）

| テーブル名                         | 説明        | ストラテジー        |
|-------------------------------|-----------|---------------|
| `security_event`              | 各種操作の監査ログ | 履歴保持のためそのまま保持 |
| `security_event_hook_results` | フックの実行結果  | 履歴保持のためそのまま保持 |

---

## ✋ 外部サービスとの連携（非同期）

| 対象       | 方法                             |
|----------|--------------------------------|
| FIDOサーバー | `delete_account` フックイベントをエンキュー |
| VC発行者    | VC取消イベントをフック経由で非同期送信           |

---

## 🚦 推奨される削除シーケンス

1. `authentication_interactions`
2. `authentication_transaction`
3. `idp_user_roles`
4. `idp_user_permission_override`
5. `oauth_token`
6. `authorization_code_grant`
7. `ciba_grant`
8. `federation_sso_session`
9. 各種論理削除（revoked、status設定など）
10. `idp_user` の物理削除
11. `security_event` に監査エントリ追加（または匿名化）
12. 外部サービスに `delete_account` フックを送信
