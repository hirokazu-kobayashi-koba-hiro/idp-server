# FIDO2 認証設定 × 挙動確認 実験ガイド

FIDO2/WebAuthn の認証設定（`authentication-config-fido2`）のパラメータを
1つ変えて → ブラウザの WebAuthn ダイアログや認証挙動がどう変わるかを確認するガイドです。

> **前提**: `setup.sh` が正常に完了していること。
>
> **検証方法**: FIDO2 の設定はブラウザの WebAuthn API の挙動に直接影響するため、
> すべての実験で**ブラウザ操作**が必要です。
> CLI では設定変更の適用確認（`show_fido2_details`）ができます。

---

## 共通準備

```bash
cd config/templates/use-cases/passwordless-fido2
source helpers.sh

get_admin_token
```

### FIDO2 設定変更に使う関数

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `update_fido2_auth_config` | FIDO2 認証設定の details を変更 | `update_fido2_auth_config '.authenticator_attachment = "cross-platform"'` |
| `restore_fido2_auth_config` | FIDO2 認証設定を元に戻す | `restore_fido2_auth_config` |
| `show_fido2_details` | 現在の details を表示 | `show_fido2_details` |

> **仕組み**: `update_fido2_auth_config` は FIDO2 認証設定の 4 つの interaction
> （registration-challenge, registration, authentication-challenge, authentication）の
> `execution.details` すべてに同じ変更を適用します。

### 現在の設定を確認

```bash
echo "--- 現在の FIDO2 設定 ---"
show_fido2_details
```

デフォルト値:

```json
{
  "rp_id": "local.dev",
  "rp_name": "Local Dev IDP",
  "allowed_origins": ["https://auth.local.dev"],
  "authenticator_attachment": "platform",
  "require_resident_key": true,
  "user_verification_required": true,
  "user_presence_required": true,
  "attestation_preference": "direct"
}
```

---

## Experiment 1: authenticator_attachment を変更する

> **やりたいこと**: 使用できる認証器の種類を制限したい
>
> **変わる設定**: `authenticator_attachment`
>
> **背景**: WebAuthn の `authenticatorAttachment` は、ブラウザが `navigator.credentials.create()` を
> 呼ぶ際にどの種類の認証器を許可するかを指定します。

### 設定値の意味

| 値 | 意味 | 使用できる認証器 |
|----|------|---------------|
| `"platform"` | プラットフォーム認証器のみ | Touch ID, Windows Hello, Face ID |
| `"cross-platform"` | ローミング認証器のみ | USB セキュリティキー, NFC キー, BLE キー |
| `null`（未指定） | 制限なし | 上記すべて |

### 1-A. cross-platform に変更（セキュリティキー限定）

```bash
update_fido2_auth_config '.authenticator_attachment = "cross-platform"' > /dev/null
echo "--- 変更後 ---"
show_fido2_details | jq '{authenticator_attachment}'
```

#### ブラウザで確認

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-attachment&prompt=create"
```

1. ユーザー登録画面で新しいユーザーを登録
2. FIDO2 デバイス登録のプロンプトが表示される

#### 期待結果

| 設定 | ブラウザの挙動 |
|------|-------------|
| `"platform"`（デフォルト） | Touch ID / Windows Hello のプロンプトが直接表示される |
| `"cross-platform"` | 「セキュリティキーを挿入してください」等の外部デバイスプロンプトが表示される。Touch ID は選択肢に表示されない |

> **Note**: セキュリティキーを持っていない場合、登録を完了できません。
> キャンセルして元に戻してください。

### 1-B. null に変更（制限なし）

```bash
update_fido2_auth_config '.authenticator_attachment = null' > /dev/null
echo "--- 変更後 ---"
show_fido2_details | jq '{authenticator_attachment}'
```

#### 期待結果

| 設定 | ブラウザの挙動 |
|------|-------------|
| `null`（制限なし） | 「Touch ID を使用」「セキュリティキーを使用」等、複数の選択肢が表示される |

### 元に戻す

```bash
restore_fido2_auth_config
```

---

## Experiment 2: require_resident_key を変更する

> **やりたいこと**: Discoverable Credential（Passkey）の要求を変えたい
>
> **変わる設定**: `require_resident_key`
>
> **背景**: `require_resident_key = true` にすると、認証器にクレデンシャルが保存され（Discoverable Credential）、
> ユーザー名なしでログインできる「Passkey」として機能します。
> `false` にすると、サーバー側からクレデンシャル ID を提示する必要があり、
> ユーザー名入力が必須になります。

### 設定値の意味

| 値 | 意味 | ログイン時の挙動 |
|----|------|---------------|
| `true` | Discoverable Credential 必須（Passkey） | ユーザー名なしでログイン可能。認証器がアカウント一覧を表示 |
| `false` | Server-side Credential 許可 | ユーザー名入力が必要。サーバーが `allowCredentials` でクレデンシャル ID を指定 |

### 設定変更

```bash
update_fido2_auth_config '.require_resident_key = false' > /dev/null
echo "--- 変更後 ---"
show_fido2_details | jq '{require_resident_key}'
```

### ブラウザで確認

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-resident-key&prompt=create"
```

1. 新しいユーザーを登録
2. FIDO2 デバイス登録を完了
3. 別のブラウザ/シークレットウィンドウで再認証（`prompt=login`）

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-resident-key-auth&prompt=login"
```

### 期待結果

| 設定 | デバイス登録時 | 認証時 |
|------|-------------|-------|
| `true`（デフォルト） | 認証器にクレデンシャルが保存される | ユーザー名入力なしで認証器がアカウント一覧を表示 |
| `false` | クレデンシャル ID のみサーバー側で管理 | ユーザー名（email）入力後に認証器が起動 |

### 元に戻す

```bash
restore_fido2_auth_config
```

---

## Experiment 3: user_verification_required を変更する

> **やりたいこと**: 生体認証（Touch ID / PIN）の要求を変えたい
>
> **変わる設定**: `user_verification_required`
>
> **背景**: User Verification（UV）は、認証器がユーザーの本人確認を行ったかどうかを示します。
> `true` の場合、Touch ID や PIN 入力が必須になります。
> `false` の場合、認証器をタッチするだけ（User Presence）で認証が完了します。

### 設定値の意味

| 値 | 意味 | ブラウザの挙動 |
|----|------|-------------|
| `true` | User Verification 必須 | Touch ID / Face ID / PIN 入力が求められる |
| `false` | User Presence のみ | セキュリティキーの場合、タッチするだけで認証完了 |

> **`user_verification_required` と `user_presence_required` の違い**:
>
> | | User Presence (UP) | User Verification (UV) |
> |---|---|---|
> | 確認すること | 「人間がいるか」 | 「本人か」 |
> | 操作 | 認証器をタッチ | 生体認証 / PIN 入力 |
> | セキュリティレベル | 低（誰でもタッチ可能） | 高（本人のみ） |
> | 一般的な設定 | 常に `true` | ユースケースによる |

### 設定変更

```bash
update_fido2_auth_config '.user_verification_required = false' > /dev/null
echo "--- 変更後 ---"
show_fido2_details | jq '{user_verification_required, user_presence_required}'
```

### ブラウザで確認

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-uv&prompt=create"
```

1. 新しいユーザーを登録
2. FIDO2 デバイス登録時のプロンプトを観察

### 期待結果

| 設定 | Platform 認証器 (Touch ID) | Roaming 認証器 (セキュリティキー) |
|------|--------------------------|-------------------------------|
| `true`（デフォルト） | 指紋認証が求められる | PIN 入力後にタッチ |
| `false` | 指紋認証なしで完了する場合がある (*) | タッチのみで完了（PIN 不要） |

> (*) Platform 認証器の場合、OS / ブラウザの実装により `user_verification = "discouraged"` でも
> 生体認証が求められることがあります。セキュリティキーのほうが違いが明確に確認できます。

### 元に戻す

```bash
restore_fido2_auth_config
```

---

## Experiment 4: attestation_preference を変更する

> **やりたいこと**: アテステーション（認証器の真正性証明）の要求レベルを変えたい
>
> **変わる設定**: `attestation_preference`
>
> **背景**: アテステーションは、認証器が本物（正規メーカー製）であることを証明する仕組みです。
> FIDO2 デバイス登録時に認証器が返すアテステーション情報の量が変わります。

### 設定値の意味

| 値 | 意味 | ユースケース |
|----|------|-----------|
| `"none"` | アテステーション不要 | 一般的な Web サービス（プライバシー重視） |
| `"indirect"` | 匿名化されたアテステーション | プライバシーとセキュリティのバランス |
| `"direct"` | 直接アテステーション | 金融/企業向け（認証器の真正性を検証したい） |
| `"enterprise"` | エンタープライズアテステーション | 企業管理デバイスのみ許可 |

### 設定変更: direct → none

```bash
update_fido2_auth_config '.attestation_preference = "none"' > /dev/null
echo "--- 変更後 ---"
show_fido2_details | jq '{attestation_preference}'
```

### ブラウザで確認

```bash
echo "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=exp-attestation&prompt=create"
```

1. 新しいユーザーを登録
2. FIDO2 デバイス登録を完了

### 期待結果

| 設定 | ブラウザのプロンプト | サーバーが受け取るデータ |
|------|-------------------|---------------------|
| `"direct"` | ブラウザが「このサイトに認証器の情報を送信してよいか」確認を表示する場合がある | アテステーション証明書チェーン付き |
| `"none"` | 確認なしで登録が完了 | アテステーション情報なし（`fmt: "none"`） |

> **`"direct"` と `"none"` の違いが見えにくい場合**:
> Chrome DevTools の Network タブで `fido2-registration` リクエストの
> レスポンスを確認すると、アテステーション形式の違いが確認できます。

### 設定変更: none → enterprise

```bash
update_fido2_auth_config '.attestation_preference = "enterprise"' > /dev/null
echo "--- 変更後 ---"
show_fido2_details | jq '{attestation_preference}'
```

### 期待結果

| 設定 | 挙動 |
|------|------|
| `"enterprise"` | 企業管理されていない一般的な認証器では登録が失敗する可能性がある。ブラウザが「企業アテステーション」をサポートしていない場合、`"direct"` にフォールバック |

### 元に戻す

```bash
restore_fido2_auth_config
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | 使える認証器の種類を制限したい | `authenticator_attachment` | platform / cross-platform / 制限なし でプロンプトが変わる |
| 2 | Passkey（ユーザー名なしログイン）の要否を変えたい | `require_resident_key` | true で Passkey、false でユーザー名入力必須 |
| 3 | 生体認証の要否を変えたい | `user_verification_required` | true で生体認証必須、false でタッチのみ |
| 4 | アテステーション（認証器の真正性証明）を変えたい | `attestation_preference` | none / direct / enterprise でブラウザ確認やデータが変わる |

## 参考: 設定の組み合わせパターン

| ユースケース | authenticator_attachment | require_resident_key | user_verification | attestation |
|------------|------------------------|---------------------|-------------------|------------|
| 一般 Web（Passkey） | `"platform"` | `true` | `true` | `"none"` |
| 金融/企業 | `"platform"` | `true` | `true` | `"direct"` |
| セキュリティキー限定 | `"cross-platform"` | `false` | `true` | `"direct"` |
| 最大互換性 | `null` | `true` | `false` | `"none"` |
