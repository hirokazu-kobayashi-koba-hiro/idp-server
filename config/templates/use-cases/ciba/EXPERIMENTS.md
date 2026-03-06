# CIBA 実験ガイド

CIBA（Client-Initiated Backchannel Authentication）の設定やリクエストパラメータを1つ変えて → 挙動がどう変わるかを手元で確認するガイドです。

CIBAはモバイルデバイスでの承認を伴うバックチャネル認証フローです。
ポーリング間隔、有効期限、ユーザーコード、認証拒否、login_hint の形式など、
設定パラメータの変更がフローの挙動にどう影響するかを確認できます。

> **前提条件**:
> 1. `setup.sh` が正常に完了していること
> 2. `verify.sh` が正常に完了していること（`device-credentials.json` が生成されている）
> 3. Mockoon FIDO-UAF モックサーバーが起動中であること

---

## 共通準備

```bash
cd config/templates/use-cases/ciba
source helpers.sh
get_admin_token
```

### helpers.sh で使える関数

#### 管理ヘルパー

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `get_admin_token` | 管理トークン取得 | `get_admin_token` |
| `update_auth_server` | 認可サーバー設定を部分変更 | `update_auth_server '.extension.backchannel_authentication_polling_interval = 15'` |
| `restore_auth_server` | 認可サーバー設定を元に戻す | `restore_auth_server` |
| `update_client` | クライアント設定を部分変更 | `update_client '.backchannel_user_code_parameter = true'` |
| `restore_client` | クライアント設定を元に戻す | `restore_client` |

#### CIBA フローヘルパー

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `ciba_request` | CIBA BC リクエスト送信 | `ciba_request --binding-message "Transfer"` |
| `ciba_poll` | トークンポーリング | `ciba_poll` |
| `device_auth_approve` | デバイス側認証承認 | `device_auth_approve` |
| `device_auth_cancel` | デバイス側認証拒否 | `device_auth_cancel` |
| `get_auth_transaction` | 認証トランザクション取得 | `get_auth_transaction` |
| `decode_jwt_payload` | JWT ペイロードデコード | `decode_jwt_payload "${TOKEN}"` |
| `get_userinfo` | UserInfo 取得 | `get_userinfo` |

> **重要**: CIBAフロー関数は `verify.sh` 実行済みが前提です。
> `device-credentials.json` が生成されていないと、デバイスID・シークレット・ユーザー情報が読み込めません。

---

## Experiment 1: ポーリング間隔を変更する

> **やりたいこと**: CIBA のポーリング間隔を変更して、BC レスポンスの `interval` が変わることを確認したい
>
> **変わる設定**: `authorization_server.extension.backchannel_authentication_polling_interval`
>
> **実装の仕組み**: `CibaRequestContext.interval()` が
> `AuthorizationServerExtensionConfiguration.backchannelAuthenticationPollingInterval()` を参照して
> BC レスポンスの `interval` 値を決定する。
>
> **注意**: `interval` はクライアントへの推奨ポーリング間隔であり、サーバー側で強制はしない。
> `slow_down` エラーは未実装のため、`interval` より短い間隔でポーリングしてもエラーにはならない。

### 1. ベースライン確認

```bash
echo "--- ベースライン: CIBA リクエスト ---"
ciba_request

echo ""
echo "interval: ${CIBA_INTERVAL}"
echo "expires_in: ${CIBA_EXPIRES_IN}"
```

> `interval` がデフォルト `5`（5秒）になるはずです。

### 2. 設定変更：ポーリング間隔を 15秒に

```bash
update_auth_server '.extension.backchannel_authentication_polling_interval = 15' \
  | jq '.result.extension.backchannel_authentication_polling_interval // .'
```

### 3. 挙動確認

```bash
echo "--- 変更後: CIBA リクエスト ---"
ciba_request

echo ""
echo "interval: ${CIBA_INTERVAL}"
```

承認してトークンを取得する場合:

```bash
device_auth_approve
ciba_poll
```

### 4. 期待結果

| タイミング | `interval` | 意味 |
|-----------|-----------|------|
| 変更前 | `5` | デフォルト 5秒間隔 |
| 変更後 | `15` | 15秒間隔に変更 |

> **CIBA 仕様との関係**: OpenID Connect CIBA Core 1.0 では、クライアントは `interval` 値以上の間隔でポーリングすべきと定めている。
> `interval` より短い間隔でポーリングした場合、仕様上は `slow_down` エラーを返すべきだが、
> 現在の実装ではこのエラーは未対応。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 2: リクエスト有効期限を短くして期限切れを確認する

> **やりたいこと**: CIBA リクエストの有効期限を短くして、認証完了前に期限切れになる様子を確認したい
>
> **変わる設定**: `authorization_server.extension.backchannel_authentication_request_expires_in`
>
> **実装の仕組み**: `CibaRequestContext.expiresIn()` が
> `AuthorizationServerExtensionConfiguration.backchannelAuthenticationRequestExpiresIn()` を参照。
> トークンポーリング時に `CibaGrantBaseVerifier.throwExceptionIfExpired()` が
> `CibaGrant.isExpire()` で有効期限をチェックし、期限切れなら `expired_token` エラーを返す。

### 1. ベースライン確認

```bash
echo "--- ベースライン: CIBA リクエスト ---"
ciba_request

echo ""
echo "expires_in: ${CIBA_EXPIRES_IN}"
```

> `expires_in` がデフォルト `120`（120秒）になるはずです。

### 2. 設定変更：有効期限を 10秒に

```bash
update_auth_server '.extension.backchannel_authentication_request_expires_in = 10' \
  | jq '.result.extension.backchannel_authentication_request_expires_in // .'
```

### 3. 挙動確認：即座にポーリング → authorization_pending

```bash
echo "--- CIBA リクエスト（10秒有効期限） ---"
ciba_request

echo ""
echo "expires_in: ${CIBA_EXPIRES_IN}"

echo ""
echo "--- 即座にポーリング（認証前 → authorization_pending） ---"
ciba_poll
```

### 4. 挙動確認：12秒待ってからポーリング → expired_token

```bash
echo "--- 12秒待機... ---"
sleep 12

echo "--- 期限切れ後にポーリング ---"
ciba_poll
```

### 5. 期待結果

| タイミング | ポーリング結果 | 理由 |
|-----------|-------------|------|
| 即座 | `authorization_pending` | 認証が完了していない |
| 12秒後 | `expired_token` | リクエスト有効期限（10秒）超過 |

> **認可コードの有効期限（authorization_code_valid_duration）との違い**:
> - `backchannel_authentication_request_expires_in`: CIBA リクエスト全体の有効期限（認証完了まで）
> - `authorization_code_valid_duration`: コード発行後のトークン交換までの有効期限
>
> **実運用での意味**: 有効期限を短くすると、ユーザーが承認を放置した場合に
> 早くクリーンアップされる。ただし短すぎるとユーザーが承認操作する前に期限切れになる。

### 6. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 3: ユーザーコードを必須にする

> **やりたいこと**: CIBA リクエストにユーザーコード（パスワード）を必須にして、
> 送らない場合のエラーと、正しいパスワードを送った場合の成功を確認したい
>
> **変わる設定**:
> - 認可サーバー: `extension.required_backchannel_auth_user_code = true`
> - 認可サーバー: `backchannel_user_code_parameter_supported = true`
> - クライアント: `backchannel_user_code_parameter = true`
>
> **実装の仕組み**:
> 1. `CibaRequestBaseVerifier.throwExceptionIfNotContainsUserCode()` が
>    `requiredBackchannelAuthUserCode = true` かつ `user_code` 未送信でエラー
> 2. `UserCodeAsPasswordVerifier` が `backchannelAuthUserCodeType = "password"` の場合、
>    `user_code` の値をユーザーのパスワードと照合する
> 3. `CibaRequestContext.isSupportedUserCode()` は認可サーバーの
>    `backchannelUserCodeParameterSupported` とクライアントの `backchannelUserCodeParameter`
>    の両方が `true` の場合のみ有効

### 1. 設定変更：ユーザーコードを必須に

認可サーバーとクライアントの両方を変更する必要がある。

```bash
# 認可サーバー: user_code サポート + 必須化
update_auth_server '
  .backchannel_user_code_parameter_supported = true |
  .extension.required_backchannel_auth_user_code = true
' | jq '{
  backchannel_user_code_parameter_supported: .result.backchannel_user_code_parameter_supported,
  required: .result.extension.required_backchannel_auth_user_code
}'

# クライアント: user_code パラメータ有効化
update_client '.backchannel_user_code_parameter = true' \
  | jq '{backchannel_user_code_parameter: .result.backchannel_user_code_parameter}'
```

### 2. 挙動確認：user_code なし → エラー

```bash
echo "--- user_code なしで CIBA リクエスト ---"
ciba_request
```

### 3. 挙動確認：user_code にパスワードを送信 → 成功

```bash
echo "--- user_code にパスワードを送信 ---"
ciba_request --user-code "${USER_PASSWORD}"

echo ""
echo "Auth Request ID: ${AUTH_REQ_ID}"
```

承認してトークンを取得:

```bash
device_auth_approve
ciba_poll
```

### 4. 期待結果

| 条件 | 結果 | エラー |
|------|------|--------|
| `user_code` なし | 400 エラー | `missing_user_code` |
| `user_code` に正しいパスワード | 200 OK | — |
| `user_code` に間違ったパスワード | 400 エラー | `invalid_user_code` |

> **ユースケース**: ユーザーコードは、CIBA リクエストを送信したクライアントが
> 本当にユーザーの意図を持っているかを追加検証する仕組み。
> `password` タイプの場合、ユーザーがクライアント端末でパスワードを入力し、
> それがバックチャネルリクエストに含められる。

### 5. 元に戻す

```bash
restore_client
restore_auth_server
```

---

## Experiment 4: 認証を拒否する（access_denied）と failure_conditions の役割

> **やりたいこと**: デバイス側で認証を拒否した場合の挙動を確認し、
> `failure_conditions` の有無で結果がどう変わるかを比較したい
>
> **変わる設定**: CIBA 認証ポリシーの `failure_conditions`
>
> **実装の仕組み**:
> 1. `AuthenticationCancelInteractor` が `operationType=DENY`, `status=SUCCESS` のインタラクション結果を返す
> 2. `AuthenticationTransaction.isFailure()` → `MfaConditionEvaluator.isFailureSatisfied()` で判定
> 3. 失敗判定が `true` の場合、`CibaDenyHandler.handle()` が CIBA グラントのステータスを `access_denied` に更新
> 4. 次回ポーリング時に `CibaGrantBaseVerifier.throwExceptionIfAccessDenied()` がエラーを返す
>
> **ポイント**: `isFailureSatisfied()` は `failure_conditions` が定義されていない場合、
> `config.exists()` で `false` を返し、deny インタラクションの判定（`containsDenyInteraction()`）に到達しない。
> ```java
> // MfaConditionEvaluator.isFailureSatisfied()
> if (!config.exists() || !results.exists()) {  // ← failure_conditions 未定義だとここで return
>   return false;
> }
> if (results.containsDenyInteraction()) {       // ← 到達しない
>   return true;
> }
> ```
> この実験では、`failure_conditions` なし→あり の順に試して挙動差を確認する。

### 1. failure_conditions なしで拒否を試す

まず、デフォルト状態（`failure_conditions` 未定義）でキャンセルした場合の挙動を確認する。

```bash
echo "--- 現在の CIBA ポリシーの failure_conditions ---"
curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_CIBA_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.policies[0].failure_conditions'
```

> `{}` または `null` のはずです。

```bash
echo "--- CIBA リクエスト ---"
ciba_request

echo ""
echo "--- デバイス側で認証を拒否 ---"
device_auth_cancel

echo ""
echo "--- 拒否後にポーリング ---"
ciba_poll
```

> `access_denied` ではなく **`authorization_pending` のまま**になります。
> キャンセル操作自体は成功しているが、`failure_conditions` が未定義のため
> `isFailureSatisfied()` が `false` を返し、`CibaDenyHandler` が呼ばれない。

### 2. failure_conditions を追加

```bash
# 現在のポリシーを保存（復元用）
CIBA_POLICY_JSON=$(curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_CIBA_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

# failure_conditions を追加（cancel の success_count >= 1 で失敗判定）
UPDATED_CIBA_POLICY=$(echo "${CIBA_POLICY_JSON}" | jq '.policies[0].failure_conditions = {
  "any_of": [
    [
      {
        "path": "$.authentication-cancel.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}')

curl -s -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_CIBA_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${UPDATED_CIBA_POLICY}" | jq '.policies[0].failure_conditions'
```

### 3. failure_conditions ありで拒否を試す

```bash
echo "--- CIBA リクエスト ---"
ciba_request

echo ""
echo "--- 認証前にポーリング ---"
ciba_poll

echo ""
echo "--- デバイス側で認証を拒否 ---"
device_auth_cancel

echo ""
echo "--- 拒否後にポーリング ---"
ciba_poll
```

### 4. 期待結果

| `failure_conditions` | 操作 | ポーリング結果 | 理由 |
|---------------------|------|-------------|------|
| **未定義**（デフォルト） | cancel | `authorization_pending` | `isFailureSatisfied()` が `config.exists()` で `false` → deny 判定に到達しない |
| **定義あり** | ポーリング（認証前） | `authorization_pending` | まだ認証が完了していない |
| **定義あり** | cancel → ポーリング | `access_denied` | `failure_conditions` を評価 → deny 検出 → `CibaDenyHandler` 実行 |

> **認証ポリシーの `failure_conditions` の役割**:
> `success_conditions` が「認証成功」を判定するように、`failure_conditions` は「認証失敗・拒否」を判定する。
> CIBA フローでは、この判定結果が `CibaDenyHandler` の呼び出しを制御する。
> `failure_conditions` が未定義の場合、ポリシーベースの失敗判定が機能しないため、
> キャンセルしても CIBA グラントのステータスが更新されない。
>
> **Ping/Push モードの場合**: `CibaDenyHandler.notifyErrorIfRequired()` が
> クライアントのコールバック URI にエラーを通知する。
> Poll モード（本ユースケース）では通知は行われず、クライアントはポーリングでエラーを検知する。

### 5. 元に戻す

```bash
curl -s -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_CIBA_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CIBA_POLICY_JSON}" | jq '.policies[0].failure_conditions'

echo "CIBA authentication policy restored."
```

---

## Experiment 5: login_hint の形式を変える

> **やりたいこと**: CIBA リクエストの `login_hint` にさまざまな形式を使って、ユーザー解決の挙動を確認したい
>
> **変わる設定**: なし（リクエストパラメータの変更）
>
> **実装の仕組み**: `LoginHintResolver.resolve()` が `login_hint` のプレフィックスで分岐する。
>
> | プレフィックス | 解決方法 |
> |--------------|---------|
> | `device:` | デバイスIDでユーザーを検索 |
> | `sub:` | ユーザーIDで検索 |
> | `email:` | メールアドレスで検索 |
> | `phone:` | 電話番号で検索 |
> | `ex-sub:` | 外部IdPのsubjectで検索 |
>
> `idp:` サフィックスは IdP プロバイダーの指定（省略時は `idp-server`）。

### 1. device: 形式（デフォルト）

```bash
echo "--- device: 形式 ---"
ciba_request --login-hint "device:${DEVICE_ID},idp:idp-server"
echo "結果: auth_req_id=${AUTH_REQ_ID}"
```

承認してクリーンアップ:

```bash
device_auth_approve
ciba_poll > /dev/null
```

### 2. sub: 形式

```bash
echo "--- sub: 形式 ---"
ciba_request --login-hint "sub:${USER_SUB}"
echo "結果: auth_req_id=${AUTH_REQ_ID}"
```

```bash
device_auth_approve
ciba_poll > /dev/null
```

### 3. email: 形式

```bash
echo "--- email: 形式 ---"
ciba_request --login-hint "email:${USER_EMAIL}"
echo "結果: auth_req_id=${AUTH_REQ_ID}"
```

```bash
device_auth_approve
ciba_poll > /dev/null
```

### 4. 無効なデバイスID → エラー

```bash
echo "--- 無効な device ID ---"
ciba_request --login-hint "device:invalid-device-id,idp:idp-server"
```

### 5. 期待結果

| login_hint 形式 | 結果 | 理由 |
|----------------|------|------|
| `device:${DEVICE_ID},idp:idp-server` | 200 OK | デバイスIDでユーザー解決 |
| `sub:${USER_SUB}` | 200 OK | ユーザーID（sub）で解決 |
| `email:${USER_EMAIL}` | 200 OK | メールアドレスで解決 |
| `device:invalid-device-id,idp:idp-server` | 400 エラー | ユーザーが見つからない |

> **`idp:` サフィックスの省略**: `PrefixMatcher.extractHints()` は `idp:` が省略された場合、
> デフォルトで `idp-server` を使用する。明示的に指定する必要があるのは外部 IdP と連携する場合のみ。

---

## Experiment 6: requested_expiry でクライアント指定有効期限

> **やりたいこと**: クライアントが CIBA リクエストに `requested_expiry` を指定して、
> サーバーデフォルトよりも短い有効期限を設定できることを確認したい
>
> **変わる設定**: なし（リクエストパラメータの変更）
>
> **実装の仕組み**: `CibaRequestContext.expiresIn()` で `requested_expiry` が設定されている場合、
> サーバーの `backchannelAuthenticationRequestExpiresIn` よりも優先される。
>
> ```java
> public ExpiresIn expiresIn() {
>   if (backchannelAuthenticationRequest.hasRequestedExpiry()) {
>     return new ExpiresIn(backchannelAuthenticationRequest.requestedExpiry().toIntValue());
>   }
>   return new ExpiresIn(
>       authorizationServerConfiguration.backchannelAuthenticationRequestExpiresIn());
> }
> ```

### 1. ベースライン確認（サーバーデフォルト）

```bash
echo "--- ベースライン: サーバーデフォルトの有効期限 ---"
ciba_request

echo ""
echo "expires_in: ${CIBA_EXPIRES_IN}"
```

> サーバーデフォルトの `120` が返るはずです。

### 2. requested_expiry を指定

```bash
echo "--- requested_expiry=30 ---"
ciba_request --requested-expiry 30

echo ""
echo "expires_in: ${CIBA_EXPIRES_IN}"
```

承認してトークンを取得:

```bash
device_auth_approve
ciba_poll
```

### 3. 期待結果

| パラメータ | `expires_in` | 理由 |
|-----------|-------------|------|
| なし（デフォルト） | `120` | サーバー設定値 |
| `requested_expiry=30` | `30` | クライアント指定値が優先 |

> **注意**: `requested_expiry` はサーバーのデフォルト値を**超える**こともできる。
> サーバー側で上限を設けていないため、クライアントが任意の値を指定可能。
>
> **ユースケース**: ユーザーの承認操作が素早いと分かっているケースでは、
> 短い有効期限を指定してセキュリティを高められる。

---

## Experiment 7: binding_message を確認する

> **やりたいこと**: CIBA リクエストに `binding_message` を含めて、
> デバイス側の認証トランザクションに反映されることを確認したい
>
> **変わる設定**: なし（リクエストパラメータの変更）
>
> **実装の仕組み**: `BackchannelAuthenticationRequest.bindingMessage()` に保存され、
> 認証トランザクション取得時にデバイス側に伝達される。
> これにより、デバイスのユーザーに「何を承認しようとしているか」のコンテキストを表示できる。

### 1. binding_message なしで CIBA リクエスト

```bash
echo "--- binding_message なし ---"
ciba_request
```

認証トランザクションを確認:

```bash
echo "--- 認証トランザクション（binding_message なし） ---"
get_auth_transaction | jq '.list[0] | {id, flow, binding_message: .context.binding_message}'
```

承認してクリーンアップ:

```bash
device_auth_approve
ciba_poll > /dev/null
```

### 2. binding_message ありで CIBA リクエスト

```bash
echo "--- binding_message あり ---"
ciba_request --binding-message "Transfer 100 USD"
```

認証トランザクションを確認:

```bash
echo "--- 認証トランザクション（binding_message あり） ---"
get_auth_transaction | jq '.list[0] | {id, flow, binding_message: .context.binding_message}'
```

承認してトークンを取得:

```bash
device_auth_approve
ciba_poll
```

### 3. 期待結果

| パラメータ | トランザクションの `binding_message` | 意味 |
|-----------|-------------------------------------|------|
| なし | `null` or 空 | コンテキストなし |
| `Transfer 100 USD` | `Transfer 100 USD` | 送金操作の承認コンテキスト |

> **CIBA 仕様での役割**: `binding_message` は Consumption Device（クライアント端末）と
> Authentication Device（承認端末）の間でトランザクションを紐づけるための文字列。
> ユーザーは承認端末で「Transfer 100 USD を承認しますか？」のようなメッセージを確認できる。
>
> **セキュリティ上の意味**: `binding_message` があることで、ユーザーは
> 「何に対して承認しているのか」を確認できる。これにより、
> 意図しないトランザクションへの承認（ソーシャルエンジニアリング攻撃）を防げる。

---

## 実験一覧

| # | やりたいこと | 変わるもの | 確認できること |
|---|------------|-----------|--------------|
| 1 | ポーリング間隔を変えたい | `backchannel_authentication_polling_interval` | BC レスポンスの `interval` が変わる |
| 2 | 有効期限を短くしたい | `backchannel_authentication_request_expires_in` | 期限超過で `expired_token` |
| 3 | ユーザーコードを必須にしたい | `required_backchannel_auth_user_code` + `backchannel_user_code_parameter` | user_code なしで `missing_user_code` |
| 4 | 認証を拒否したい | CIBA ポリシー `failure_conditions` + cancel 操作 | ポーリングで `access_denied` |
| 5 | login_hint の形式を試したい | リクエストパラメータ | `device:`, `sub:`, `email:` 各形式の動作 |
| 6 | クライアント指定有効期限 | リクエストパラメータ `requested_expiry` | `expires_in` がクライアント指定値になる |
| 7 | binding_message の伝達 | リクエストパラメータ `binding_message` | 認証トランザクションに含まれる |

### 設定変更系 vs パラメータ変更系

| 種別 | 実験 | 復元操作 |
|------|------|---------|
| 認可サーバー設定変更 | Exp 1, 2 | `restore_auth_server` |
| 認可サーバー + クライアント設定変更 | Exp 3 | `restore_client` + `restore_auth_server` |
| CIBA ポリシー変更 + フロー操作 | Exp 4 | ポリシー復元 |
| リクエストパラメータのみ | Exp 5, 6, 7 | 不要 |
