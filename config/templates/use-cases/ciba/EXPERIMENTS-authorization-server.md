
# CIBA 認可サーバー設定 実験ガイド
認可サーバー（`authorization_server`）の汎用設定を1つ変えて → CIBA トークンレスポンスへの影響を確認するガイドです。
EXPERIMENTS.md の続編として、CIBA 固有設定の未カバー分 + 汎用設定が CIBA フローに与える影響に焦点を当てています。

> **前提条件**:
> 1. `setup.sh` が正常に完了していること
> 2. `verify.sh` が正常に完了していること（`device-credentials.json` が生成されている）
> 3. Mockoon FIDO-UAF モックサーバーが起動中であること

### EXPERIMENTS.md との棲み分け

| ファイル | 対象 |
|---------|------|
| `EXPERIMENTS.md` | CIBA 固有設定（`backchannel_authentication_polling_interval` 等）+ リクエストパラメータ + ポリシー |
| `EXPERIMENTS-authorization-server.md`（本ガイド） | CIBA 固有の未カバー設定 + 汎用認可サーバー設定が CIBA トークンに与える影響 |

---

## 共通準備

```bash
cd config/templates/use-cases/ciba
source helpers.sh
get_admin_token
```

---

## Experiment 1: Access Token の有効期限を変える

> **やりたいこと**: Access Token の有効期限を短くして、CIBA トークンレスポンスへの影響を確認したい
>
> **変わる設定**: `authorization_server.extension.access_token_duration`
>
> **実装の仕組み**: `AccessTokenCreator` が `now + access_token_duration` で `exp` を計算。
> CIBA poll レスポンスの `expires_in` も同じ値が使われる。

### 1. ベースライン確認

```bash
echo "--- ベースライン: CIBA フロー ---"
ciba_request
device_auth_approve
ciba_poll

echo ""
echo "--- Access Token の有効期限 ---"
decode_jwt_payload "${CIBA_ACCESS_TOKEN}" | jq '{exp, iat, exp_minus_iat: (.exp - .iat)}'

echo ""
echo "--- レスポンスの expires_in ---"
echo "${POLL_RESPONSE}" | jq '.expires_in'
```

> `exp - iat` と `expires_in` がデフォルト `3600`（1時間）になるはずです。

### 2. 設定変更：Access Token を 30秒に

```bash
update_auth_server '.extension.access_token_duration = 30' \
  | jq '.result.extension.access_token_duration // .'
```

### 3. 挙動確認

```bash
echo "--- 変更後: CIBA フロー ---"
ciba_request
device_auth_approve
ciba_poll

echo ""
echo "--- Access Token の有効期限 ---"
decode_jwt_payload "${CIBA_ACCESS_TOKEN}" | jq '{exp, iat, exp_minus_iat: (.exp - .iat)}'

echo ""
echo "--- レスポンスの expires_in ---"
echo "${POLL_RESPONSE}" | jq '.expires_in'
```

### 4. 期待結果

| タイミング | AT `exp - iat` | `expires_in` | 意味 |
|-----------|---------------|-------------|------|
| 変更前 | `3600` | `3600` | デフォルト 1時間 |
| 変更後 | `30` | `30` | 30秒に短縮 |

> **補足**: Access Token の期限が短いと、クライアントは Refresh Token で頻繁にトークンを更新する必要がある。
> CIBA のユースケースでは、バックグラウンド処理用の AT は長め（数時間）にすることが多い。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 2: ID Token の有効期限を変える

> **やりたいこと**: ID Token の有効期限を短くして、CIBA トークンレスポンスへの影響を確認したい
>
> **変わる設定**: `authorization_server.extension.id_token_duration`
>
> **実装の仕組み**: `IdTokenCreator` が `now + id_token_duration` で `exp` を計算。
> Access Token とは独立した有効期限。

### 1. ベースライン確認

```bash
echo "--- ベースライン: CIBA フロー ---"
ciba_request
device_auth_approve
ciba_poll

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')
echo ""
echo "--- ID Token の有効期限 ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{exp, iat, exp_minus_iat: (.exp - .iat)}'
```

> `exp - iat` がデフォルト `3600`（1時間）になるはずです。

### 2. 設定変更：ID Token を 30秒に

```bash
update_auth_server '.extension.id_token_duration = 30' \
  | jq '.result.extension.id_token_duration // .'
```

### 3. 挙動確認

```bash
echo "--- 変更後: CIBA フロー ---"
ciba_request
device_auth_approve
ciba_poll

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')
echo ""
echo "--- ID Token の有効期限 ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{exp, iat, exp_minus_iat: (.exp - .iat)}'
```

### 4. 期待結果

| タイミング | IDT `exp - iat` | 意味 |
|-----------|----------------|------|
| 変更前 | `3600` | デフォルト 1時間 |
| 変更後 | `30` | 30秒に短縮 |

> **AT と IDT の有効期限の独立性**: AT は有効でも IDT は期限切れになりうる。
> CIBA のユースケースでは IDT はクライアントが認証結果を確認するために使い、
> 確認後は AT のみを使い続けるため、IDT は短めでもよいケースが多い。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 3: Refresh Token 戦略を変える

> **やりたいこと**: リフレッシュトークンのローテーションと期限延長の挙動を CIBA フローで確認したい
>
> **変わる設定**: `extension.refresh_token_strategy` + `extension.rotate_refresh_token`
>
> **実装の仕組み**: `RefreshTokenCreatable` が以下のパターンで動作する。
>
> | strategy | rotate | トークン値 | 有効期限 |
> |----------|--------|-----------|---------|
> | EXTENDS | true | **新しい** | **延長**（now + duration） |
> | EXTENDS | false | 同じ | **延長**（now + duration） |
> | FIXED | true | **新しい** | 同じ（初回発行時のまま） |
> | FIXED | false | 同じ | 同じ（初回発行時のまま） |

> **注意**: パターンB は sleep 50 + sleep 30 で合計80秒以上の待機が発生します。
> 時間がない場合はパターンA と C だけでも十分に挙動差を確認できます。

### 1. パターンA: FIXED + rotate=true（デフォルト相当）

```bash
# refresh_token_duration を短く（60秒）して観察しやすくする
update_auth_server '
  .extension.refresh_token_duration = 60 |
  .extension.refresh_token_strategy = "FIXED" |
  .extension.rotate_refresh_token = true
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

echo "--- CIBA フローで RT を取得 ---"
ciba_request
device_auth_approve
ciba_poll

ORIGINAL_RT=$(echo "${POLL_RESPONSE}" | jq -r '.refresh_token')
echo "初回 RT: ${ORIGINAL_RT:0:30}..."

sleep 3

echo "--- リフレッシュ実行 ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${ORIGINAL_RT}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_RT=$(echo "${REFRESH_RESPONSE}" | jq -r '.refresh_token')
echo "新 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"
```

### 2. パターンB: EXTENDS + rotate=true（スライディングウィンドウ）

```bash
update_auth_server '
  .extension.refresh_token_duration = 60 |
  .extension.refresh_token_strategy = "EXTENDS" |
  .extension.rotate_refresh_token = true
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

echo "--- CIBA フローで RT を取得 ---"
ciba_request
device_auth_approve
ciba_poll

ORIGINAL_RT=$(echo "${POLL_RESPONSE}" | jq -r '.refresh_token')
echo "初回 RT: ${ORIGINAL_RT:0:30}..."

# 50秒待って、初回の60秒期限に近づいた状態でリフレッシュ
echo "--- 50秒待機（FIXED なら残り10秒しかない）... ---"
sleep 50

echo "--- リフレッシュ実行（EXTENDS なので期限が延長される） ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${ORIGINAL_RT}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_RT=$(echo "${REFRESH_RESPONSE}" | jq -r '.refresh_token')
echo "新 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"
```

```shell
# さらに30秒待ってリフレッシュ（FIXED だったら期限切れだが EXTENDS なら有効）
echo "--- さらに30秒待機... ---"
sleep 30

echo "--- 2回目のリフレッシュ（EXTENDS なのでまだ有効） ---"
REFRESH_RESPONSE2=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${NEW_RT}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

echo "${REFRESH_RESPONSE2}" | jq '{token_type, expires_in, error, error_description}'
```

### 3. パターンC: FIXED + rotate=false（トークン再利用）

```bash
update_auth_server '
  .extension.refresh_token_duration = 60 |
  .extension.refresh_token_strategy = "FIXED" |
  .extension.rotate_refresh_token = false
' | jq '.result.extension | {refresh_token_duration, refresh_token_strategy, rotate_refresh_token}'

echo "--- CIBA フローで RT を取得 ---"
ciba_request
device_auth_approve
ciba_poll

ORIGINAL_RT=$(echo "${POLL_RESPONSE}" | jq -r '.refresh_token')
echo "初回 RT: ${ORIGINAL_RT:0:30}..."
```

```shell
echo "--- リフレッシュ実行 ---"
REFRESH_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${ORIGINAL_RT}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

NEW_RT=$(echo "${REFRESH_RESPONSE}" | jq -r '.refresh_token')
echo "リフレッシュ後 RT: ${NEW_RT:0:30}..."
echo "トークン値が変わった: $([ "${ORIGINAL_RT}" != "${NEW_RT}" ] && echo 'YES' || echo 'NO')"
```

### 4. 期待結果まとめ

| パターン | strategy | rotate | トークン値変化 | 有効期限延長 | ユースケース |
|---------|----------|--------|-------------|------------|------------|
| A | FIXED | true | **変わる** | しない | 一般的（トークン固定攻撃防止） |
| B | EXTENDS | true | **変わる** | **する** | アクティブユーザーに無期限セッション |
| C | FIXED | false | 変わらない | しない | シンプル（トークン再利用） |

> **セキュリティ推奨**:
> - `rotate_refresh_token = true`: トークン漏洩時の被害を限定（古いトークンが無効化される）
> - `FIXED` 戦略: リフレッシュチェーンに上限を設ける（初回発行から一定時間で必ず期限切れ）
> - `EXTENDS` 戦略: ユーザーがアクティブなら無期限に延長（利便性重視だがリスクもある）
>
> **CIBA での意味**: CIBA はバックグラウンド処理での利用が多いため、
> `EXTENDS + rotate=true` でアクティブな処理中は自動延長、というパターンがよく使われる。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 4: id_token_strict_mode で ID Token のクレームを制限する

> **やりたいこと**: ID Token に含まれるクレームを厳格に制限して、CIBA フローでの影響を確認したい
>
> **変わる設定**: `authorization_server.extension.id_token_strict_mode`
>
> **実装の仕組み**:
> - `false`（デフォルト）: scope に `profile` があれば `name` 等が ID Token に含まれる
> - `true`: scope ベースのクレームが ID Token から除外される
>
> **UserInfo への影響**: 標準クレーム（`profile`, `email` スコープ）には影響しない。
> ただし `claims:*` プレフィックスのカスタムクレームは UserInfo からも除外される
> （`UserinfoScopeMappingCustomClaimsCreator.shouldCreate()` が `isIdTokenStrictMode()` をチェックするため）。
>
> **CIBA での注意**: CIBA では認可リクエストに `claims` パラメータを送る仕組みがないため、
> strict mode を有効にすると ID Token は常に最小限のクレーム（`sub`, `iss`, `aud` 等）のみになる。

### 1. ベースライン確認（strict mode OFF）

```bash
echo "--- strict_mode=false: CIBA フロー ---"
ciba_request --scope "openid profile email"
device_auth_approve
ciba_poll

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')

echo ""
echo "--- ID Token のクレーム ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{sub, name, email, given_name, family_name}'

echo ""
echo "--- UserInfo ---"
get_userinfo | jq '{sub, name, email}'
```

> ID Token に `name`, `email` 等が含まれるはずです。

### 2. 設定変更：strict mode ON

```bash
update_auth_server '.extension.id_token_strict_mode = true' \
  | jq '.result.extension.id_token_strict_mode // .'
```

### 3. 挙動確認

```bash
echo "--- strict_mode=true: CIBA フロー ---"
ciba_request --scope "openid profile email"
device_auth_approve
ciba_poll

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')

echo ""
echo "--- ID Token のクレーム（profile/email が消えるはず） ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{sub, name, email, given_name, family_name}'

echo ""
echo "--- UserInfo（標準クレームは影響なし） ---"
get_userinfo | jq '{sub, name, email}'
```

### 4. 期待結果

| 条件 | ID Token `name` | ID Token `email` | UserInfo |
|------|-----------------|------------------|----------|
| strict=false（デフォルト） | あり | あり | あり |
| strict=true | `null` | `null` | 標準クレームは影響なし（`claims:*` カスタムクレームは除外される） |

> **認可コードフローとの違い**: 認可コードフローでは `claims` パラメータで `essential: true` を指定すれば
> strict mode でも ID Token にクレームを含められる。しかし CIBA では `claims` パラメータが
> バックチャネル認証リクエストに含まれないため、strict mode ON = ID Token は常に最小限になる。
>
> **UserInfo のカスタムクレームへの影響**: `UserinfoScopeMappingCustomClaimsCreator.shouldCreate()` も
> `isIdTokenStrictMode()` をチェックするため、strict mode ON では `claims:authentication_devices` 等の
> カスタムクレームが UserInfo からも除外される。標準クレーム（`name`, `email` 等）は影響しない。
>
> **ユースケース**: CIBA で「ID Token は認証結果の確認のみ、ユーザー情報は UserInfo で取得」
> というパターンでは strict mode ON が適切。ただしカスタムクレームも UserInfo から消える点に注意。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 5: custom_claims_scope_mapping を無効にする

> **やりたいこと**: `claims:` プレフィックス付きスコープによるカスタムクレーム取得を無効化して影響を確認したい
>
> **変わる設定**: `authorization_server.extension.custom_claims_scope_mapping`
>
> **実装の仕組み**: `ScopeMappingCustomClaimsCreator.shouldCreate()` が
> `custom_claims_scope_mapping = true` の場合のみ動作する。
>
> ```java
> // ScopeMappingCustomClaimsCreator.java
> public boolean shouldCreate(...) {
>   if (authorizationServerConfiguration.isIdTokenStrictMode()) {
>     return false;
>   }
>   if (!authorizationServerConfiguration.enabledCustomClaimsScopeMapping()) {
>     return false;  // ← custom_claims_scope_mapping = false の場合ここで終了
>   }
>   return authorizationGrant.scopes().hasScopeMatchedPrefix(prefix);
> }
> ```
>
> **CIBA での意味**: `claims:authentication_devices` は CIBA ユースケースの代表的なカスタムクレーム。
> ユーザーに紐づく認証デバイス一覧を返す。

### 1. ベースライン確認（custom_claims_scope_mapping = true）

```bash
echo "--- custom_claims_scope_mapping=true: CIBA フロー ---"
ciba_request --scope "openid profile email claims:authentication_devices"
device_auth_approve
ciba_poll

echo ""
echo "--- UserInfo（authentication_devices が含まれるはず） ---"
get_userinfo | jq '{sub, email, authentication_devices}'

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')
echo ""
echo "--- ID Token ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{sub, authentication_devices}'
```

> `authentication_devices` にユーザーのデバイス一覧が含まれるはずです。

### 2. 設定変更：custom_claims_scope_mapping を無効化

```bash
update_auth_server '.extension.custom_claims_scope_mapping = false' \
  | jq '.result.extension.custom_claims_scope_mapping // .'
```

### 3. 挙動確認

```bash
echo "--- custom_claims_scope_mapping=false: CIBA フロー ---"
ciba_request --scope "openid profile email claims:authentication_devices"
device_auth_approve
ciba_poll

echo ""
echo "--- UserInfo（authentication_devices が消えるはず） ---"
get_userinfo | jq '{sub, email, authentication_devices}'

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')
echo ""
echo "--- ID Token ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{sub, authentication_devices}'
```

### 4. 期待結果

| 設定 | UserInfo `authentication_devices` | IDT `authentication_devices` |
|------|----------------------------------|------------------------------|
| `true`（デフォルト） | **あり** | **あり** |
| `false` | `null`（消える） | `null`（消える） |

> **影響範囲**: `custom_claims_scope_mapping` を無効にすると、`claims:` プレフィックスの
> **すべて**のカスタムクレーム（`claims:status`, `claims:roles` 等）が無効になる。
> `claims:authentication_devices` だけでなく、他のカスタムクレームにも影響する。
>
> **id_token_strict_mode との違い**: 両方とも `claims:*` カスタムクレームを IDT + UserInfo の両方から除外する
> （`ScopeMappingCustomClaimsCreator` と `UserinfoScopeMappingCustomClaimsCreator` の両方が
> `isIdTokenStrictMode()` をチェックするため）。真の差異は以下の通り:
>
> | 設定 | 標準クレーム（name, email 等） | `claims:*` カスタムクレーム |
> |------|-------------------------------|---------------------------|
> | `id_token_strict_mode = true` | IDT から除外、UserInfo は影響なし | IDT + UserInfo の両方から除外 |
> | `custom_claims_scope_mapping = false` | **影響なし** | IDT + UserInfo の両方から除外 |

### 5. 元に戻す

```bash
restore_auth_server
```

---

## Experiment 6: claims_supported を制限する

> **やりたいこと**: `claims_supported` からクレームを除外して、CIBA のトークンレスポンスへの影響を確認したい
>
> **変わる設定**: `authorization_server.claims_supported`
>
> **実装の仕組み**: `CibaGrantFactory` が `AuthorizationServerConfiguration.claimsSupported()` を参照し、
> `GrantIdTokenClaims` と `GrantUserinfoClaims` を生成する。
> `claims_supported` に含まれないクレームは、scope で要求されていてもフィルタリングされる。
>
> ```java
> // CibaGrantFactory.java
> List<String> supportedClaims = serverConfig.claimsSupported();
> GrantIdTokenClaims grantIdTokenClaims =
>     GrantIdTokenClaims.create(scopes, responseType, supportedClaims, ...);
> GrantUserinfoClaims grantUserinfoClaims =
>     GrantUserinfoClaims.create(scopes, supportedClaims, ...);
> ```

### 1. ベースライン確認

```bash
echo "--- ベースライン: CIBA フロー ---"
ciba_request --scope "openid profile email"
device_auth_approve
ciba_poll

echo ""
echo "--- UserInfo ---"
get_userinfo | jq '{sub, name, email}'

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')
echo ""
echo "--- ID Token ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{sub, name, email}'
```

> `email` が返るはずです。

### 2. 設定変更：claims_supported から email を除外

```bash
update_auth_server '.claims_supported = [
  "sub", "iss", "auth_time", "acr",
  "name", "given_name", "family_name", "nickname",
  "preferred_username", "middle_name", "profile", "picture", "website",
  "gender", "birthdate", "zoneinfo", "locale", "updated_at",
  "address", "phone_number", "phone_number_verified",
  "authentication_devices"
]' | jq '.result.claims_supported'
```

> `email` と `email_verified` を除外しています。

### 3. 挙動確認

```bash
echo "--- claims_supported から email 除外後: CIBA フロー ---"
ciba_request --scope "openid profile email"
device_auth_approve
ciba_poll

echo ""
echo "--- UserInfo（email が null になるはず） ---"
get_userinfo | jq '{sub, name, email}'

ID_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.id_token')
echo ""
echo "--- ID Token（email が null になるはず） ---"
decode_jwt_payload "${ID_TOKEN}" | jq '{sub, name, email}'
```

### 4. 期待結果

| 設定 | UserInfo `email` | IDT `email` | UserInfo `name` |
|------|-----------------|-------------|-----------------|
| デフォルト（email 含む） | あり | あり | あり |
| email 除外 | `null` | `null` | あり（影響なし） |

> **scopes_supported との違い**:
> - `scopes_supported`: Discovery 表示のみで、実際のスコープ処理に影響しない
> - `claims_supported`: **実際のクレームフィルタリングに影響する**。
>   `GrantIdTokenClaims` と `GrantUserinfoClaims` の生成時にフィルタとして使われる。
>
> **ポイント**: `email` スコープでリクエストしても、`claims_supported` に `email` がなければ
> 返されない。これは認可コードフローでも CIBA フローでも同じ挙動。

### 5. 元に戻す

```bash
restore_auth_server
```

---

## 実験一覧

| # | やりたいこと | 変わる設定 | 確認できること |
|---|------------|-----------|--------------|
| 1 | Access Token を短くしたい | `access_token_duration` | AT の `exp` と `expires_in` の変化 |
| 2 | ID Token を短くしたい | `id_token_duration` | IDT の `exp` の変化 |
| 3 | RT のローテーション/延長 | `refresh_token_strategy` + `rotate_refresh_token` | 3パターンの挙動差 |
| 4 | ID Token を厳格にしたい | `id_token_strict_mode` | scope ベースのクレーム除外 |
| 5 | カスタムクレームを無効にしたい | `custom_claims_scope_mapping` | `claims:*` スコープの無効化 |
| 6 | claims_supported を制限したい | `claims_supported` | email 除外で UserInfo/IDT から消える |

### EXPERIMENTS.md でカバー済み（本ガイドと重複しない）

| 設定 | EXPERIMENTS.md |
|------|---------------|
| `backchannel_authentication_polling_interval` | Exp 1 |
| `backchannel_authentication_request_expires_in` | Exp 2 |
| `required_backchannel_auth_user_code` + `backchannel_user_code_parameter_supported` | Exp 3 |
| `failure_conditions`（認証ポリシー） | Exp 4 |
