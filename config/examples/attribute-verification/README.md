# Attribute Verification Example

認証の途中で利用者を確認する `attribute-verification` を、認可画面（app-view）から試せるサンプル設定です。
パスワードでサインインしたあと、次の 2 つの確認をステップとして順に行います。

| ステップ | インタラクション | 内容 |
|---|---|---|
| 1 | パスワード | 利用者を確定する |
| 2 | `identity-verified`（条件） | アカウントが身元確認済み（`IDENTITY_VERIFIED`）か。入力は不要で、満たさなければ `identity_verification_required` |
| 3 | `kba`（入力の照合） | 生年月日と電話番号の下 4 桁を、登録値と照合する |

設定の詳細は [属性照合](../../../documentation/docs/content_06_developer-guide/05-configuration/authn/attribute-verification.md) を参照してください。

## ファイル構成

```
attribute-verification/
├── README.md
├── VERIFY.md                                       # ブラウザでの動作確認手順
├── onboarding-request.json                         # Organization + Organizer Tenant + Admin User + Client
├── public-tenant-request.json                      # Public Tenant（認可画面は https://auth.local.test）
├── authentication-config-initial-registration.json # ユーザー登録スキーマ
├── authentication-config-attribute-verification.json # 属性照合（identity-verified / kba）
├── authentication-policy-oauth.json                # パスワード → identity-verified → kba
├── client-request.json                             # アプリケーションクライアント
├── users.json                                      # テストユーザー（身元確認済み・未確認）
├── setup.sh / verify.sh / update.sh / delete.sh
```

## 使い方

```bash
cd config/examples/attribute-verification
./setup.sh    # リソースとテストユーザーを作成
./verify.sh   # curl で動作確認
./delete.sh   # テストユーザーを含めて削除
```

`setup.sh` は作成済みだと失敗します。やり直すときは先に `./delete.sh` を実行してください。
テストユーザーは `users.json` に定義しています。`status_after_creation` は管理 API の項目ではなく、
`setup.sh` が作成後に `PATCH` で設定するステータスです。

| ユーザー | ステータス | 結果 |
|---|---|---|
| `verified@attr-verify.example.com` | `IDENTITY_VERIFIED` | 3 ステップを通ってサインインできる |
| `unverified@attr-verify.example.com` | `REGISTERED` | パスワードの直後に、身元確認が必要と表示される |

## リソース

| リソース | 値 |
|---|---|
| Public Tenant ID | `77777777-0006-7777-7777-777777777777` |
| Client ID | `77777777-0007-7777-7777-777777777777` |
| Redirect URI | `http://localhost:3000/callback` |
