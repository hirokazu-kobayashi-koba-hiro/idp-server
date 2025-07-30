# ID（ユーザー）管理

## 概要

`idp-server` はテナント単位で **ID（ユーザー）** を管理します。

ID（ユーザー）を複数テナントで所持することはできません。別テナントで作成したID（ユーザー）を利用したい場合は、フェデレーションを実施します。

---

## 登録

`idp-server` はOIDCの認可コードフローをベースに、2つのユーザー登録機能を提供します。

1. `idp-server` へのユーザー登録
2. `外部IdP`とのフェデレーションによるユーザー登録

### 登録シーケンス

#### `idp-server` へのユーザー登録

```mermaid
sequenceDiagram
    participant Client as クライアント（ユーザー操作を含む）
    participant AsView as 認可画面
    participant idp as idp-server
    Client ->> idp: 認可リクエスト（/authorizations?prompt=creat）
    idp ->> idp: 認可リクエスト検証と記録
    idp -->> Client: 302 リダイレクト(認可画面URL)
    Client -->> AsView: 認可画面の表示
    AsView ->> idp: 登録（パスワード / Passkeyなど）
    idp -->> idp: IDポリシーチェックと結果の記録
    idp ->> AsView: 登録結果のレスポンス
    AsView ->> AsView: 同意画面（consent）
    AsView ->> idp: 同意
    idp ->> idp: ユーザー登録
    idp -->> idp: 登録結果のチェック
    idp -->> idp: 認可コードの生成と記録
    idp -->> AsView: 認可コード付きリダイレクトURL（200）
    AsView -->> Client: 認可コード付きリダイレクトURLへリダイレクト
    Client ->> idp: トークンリクエスト（/tokens）
    idp ->> idp: 認可コードのチェック
    idp ->> idp: トークンの生成と記録
    idp -->> Client: アクセストークン + IDトークン + リフレッシュトークン
```

#### `外部IdP`とのフェデレーションによるユーザー登録

```mermaid
sequenceDiagram
    participant Client as クライアント（ユーザー操作を含む）
    participant AsView as 認可画面
    participant idp as idp-server
    participant ExIdp as 外部IdP
    Client ->> idp: 認可リクエスト（/authorizations?prompt=creat）
    idp ->> idp: 認可リクエスト検証と記録
    idp -->> Client: 302 リダイレクト(認可画面URL)
    Client -->> AsView: 認可画面の表示
    AsView ->> AsView: 外部IdPの選択
    AsView ->> idp: フェデレーションリクエスト
    idp ->> idp: 外部IdPへの認可リクエストのURLを生成
    idp ->> idp: セッション(state)生成と記録
    idp -->> AsView: 外部IdPへの認可リクエストのURLを返却
    AsView -->> ExIdp: 認可リクエストのURLへ遷移
    ExIdp ->> ExIdp: 認証
    ExIdp -->> AsView: リダイレクト（認可コード付き）
    AsView ->> idp: フェデレーションコールバックに外部IdPからのパラメータをリクエスト
    idp -->> idp: stateからセッションを取得
    idp -->> ExIdp: トークンリクエスト
    ExIdp -->> idp: トークンレスポンス
    idp -->> ExIdp: Userinfoリクエスト
    ExIdp -->> idp: Userinfoレスポンス
    idp ->> idp: Userinfoをベースにidpユーザーへのデータマッピング
    idp -->> idp: IDポリシーチェックと結果の記録
    idp ->> AsView: コールバックのレスポンス
    AsView ->> AsView: 同意画面（スキップ可）
    AsView ->> idp: 同意
    idp -->> idp: 登録結果のチェック
    idp ->> idp: ユーザー登録
    idp -->> idp: 認可コードの生成と記録
    idp -->> AsView: 認可コード付きリダイレクトURL（200）
    AsView -->> Client: 認可コード付きリダイレクトURLへリダイレクト
    Client ->> idp: トークンリクエスト（/tokens）
    idp ->> idp: 認可コードのチェック
    idp ->> idp: トークンの生成と記録
    idp -->> Client: アクセストークン + IDトークン + リフレッシュトークン
```

## データ構造

### ユーザーID（`sub`）

- 各ユーザーには一意（UUID）な `ユーザーID`（`sub`）が割り当てられます。
- テナントごとにID空間を分離する（tenant_id + sub）

### 外部ID（`provider_id`）とのマッピング

- 外部IdP（Google, AzureADなど）との連携時には `provider_id` に基づく一意性を保持
- 内部IDと外部IDをペアで管理し、**Federated Account Mapping** を実現

---

### ユーザー属性

| 項目                               | 型       | 説明                                          |
|----------------------------------|---------|---------------------------------------------|
| `sub`                            | string  | Subject - Issuer における End-User の識別子         |
| `provider_id`                    | string  | 外部IdPと連携した場合のID識別子                          |
| `external_user_id`               | string  | 外部IdPのユーザーID(sub)                           |
| `external_user_original_payload` | object  | 外部IdPのユーザークレーム   （JSONオブジェクト）               |
| `name`                           | string  | End-User の表示用フルネーム。肩書きや称号 (suffix) を含むこともある |
| `given_name`                     | string  | 名（Given Name）                               |
| `family_name`                    | string  | 姓（Family Name）                              |
| `middle_name`                    | string  | ミドルネーム                                      |
| `nickname`                       | string  | ニックネーム                                      |
| `preferred_username`             | string  | End-User の選好するユーザー名（例：janedoe）              |
| `profile`                        | string  | プロフィールページのURL                               |
| `picture`                        | string  | プロフィール画像のURL                                |
| `website`                        | string  | End-User のWebサイトURL                         |
| `email`                          | string  | End-User の選好するEmailアドレス                     |
| `email_verified`                 | boolean | Emailアドレスが検証済みかどうか                          |
| `gender`                         | string  | 性別（例：male, female）                          |
| `birthdate`                      | string  | 生年月日（例：1990-01-01）                          |
| `zoneinfo`                       | string  | タイムゾーン情報                                    |
| `locale`                         | string  | ロケール（例：ja-JP）                               |
| `phone_number`                   | string  | 電話番号（E.164形式が推奨）                            |
| `phone_number_verified`          | boolean | 電話番号が検証済みかどうか                               |
| `address`                        | object  | 郵送先住所（JSONオブジェクト）                           |
| `status`                         | string  | アカウントの状態（ACTIVE, LOCKEDなど）                  |
| `custom_properties`              | object  | カスタムのユーザークレーム（JSONオブジェクト）                   |
| `credentials`                    | object  | 資格情報   （JSON配列）                             |
| `hashed_password`                | object  | ハッシュ化済みのパスワード                               |
| `authentication_devices`         | object  | FIDO認証などが実施可能な認証デバイス（JSON配列）                |
| `verified_claims`                | object  | 身元確認済みのクレーム                                 |
| `updated_at`                     | number  | 最終更新日時（UNIXタイムスタンプ）                         |

---

### ステータス

| ステータス                            | 説明                                           |
|----------------------------------|----------------------------------------------|
| `UNREGISTERED`                   | アカウントが未作成の状態（初回アクセスや一時的ID）                   |
| `REGISTERED`                     | 登録済だが、メールアドレスなど連絡先が未確認の状態                    |
| `IDENTITY_VERIFICATION_REQUIRED` | サービス利用にあたり本人確認が必要な状態                         |
| `IDENTITY_VERIFIED`              | eKYCなどの本人確認が完了した状態                           |
| `LOCKED`                         | 連続ログイン失敗などにより一時的にロックされた状態（MFA再認証や管理者解除が必要）   |
| `DISABLED`                       | ユーザー自身が無効にした状態                               |
| `DELETED_PENDING`                | 削除予定状態（一定期間後に完全削除が実行される）                     |

#### ステータス遷移例

```plaintext
UNREGISTERED
   ↓ 登録
REGISTERED
   ↓ 身元確認完了
IDENTITY_VERIFIED
```

##   

## 削除

ユーザー削除は物理的にデータを削除します。

以下のデータを削除：

* 発行済みトークン／認可情報（grant, token）
* 認証情報（FIDO, Password 等）
