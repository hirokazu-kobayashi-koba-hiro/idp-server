# 外部IdPアカウント連携の設定

## このドキュメントの目的

ログイン済みのユーザーが自分のアカウントに**外部IdPのアカウントを紐付け**、RP がその外部IdPのアクセストークンで外部APIを呼べるようにすることが目標です。

仕組み（確定を分ける理由、防御の考え方、エラーの一覧）は [外部IdPアカウント連携（プロトコル）](../../content_04_protocols/protocol-09-account-linking.md) を参照してください。このドキュメントは手順だけを扱います。

### 所要時間
⏱️ **約30分**

### 前提条件
- 管理者トークンを取得済み
- 組織ID・テナントIDを取得済み
- ログイン用のテナント・クライアントが動作している（[Phase 1](../phase-1-foundation/03-tenant-setup.md)）
- 連携先の外部IdPでクライアントを登録できる

### 設定するもの

| # | 対象 | 設定 |
|---|------|------|
| 1 | 外部IdP | idp-server の連携用コールバックURLを登録 |
| 2 | フェデレーション設定 | 連携先を `federation-configurations` に登録 |
| 3 | テナント | `linked_account:*` スコープを追加 |
| 4 | クライアント（RP） | スコープ・戻り先・トークン取得を許す連携先 |
| 5 | RP の実装 | 開始 → 外部IdPへ遷移 → 確定 → 一覧・解除・トークン取得 |

---

## Step 1: 外部IdPにコールバックURLを登録

外部IdPのクライアント設定で、次のURLをリダイレクトURIに登録します。

```
https://{idp-server}/{tenant-id}/v1/linking/callback/{sso_provider}
```

- `{sso_provider}` は Step 2 で付ける連携先の名前です
- ログイン用 Federation のコールバック（`/v1/authorizations/federations/oidc/callback`）とは別のURLです
- idp-server は外部IdPへの認可リクエストに PKCE（`code_challenge_method=S256`）を必ず付けます
- 外部IdPは `response_mode=form_post` ではなく、クエリ（GET）で返す設定にしてください

e2e の例では、外部IdP役テナントのクライアント（`config/examples/e2e/tenant-1e68932e-ed4a-43e7-b412-460665e42df3/clients/clientSecretPost.json`）の `redirect_uris` に連携用コールバックを追加しています。

---

## Step 2: 連携先を登録

連携先は `federation-configurations` に `type: account-linking` で登録します。ログイン用の Federation（`type: oidc`）とは別の設定で、ログインには使われず、ログイン用の設定が連携先として使われることもありません。同じ `sso_provider` の名前を付けても構いません。`payload` の形はログイン用の OIDC の設定と同じです。

```bash
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/federation-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "account-linking",
    "sso_provider": "example-idp",
    "enabled": true,
    "payload": {
      "type": "standard",
      "provider": "standard",
      "issuer": "https://idp.example.com",
      "issuer_name": "example-idp",
      "authorization_endpoint": "https://idp.example.com/authorize",
      "token_endpoint": "https://idp.example.com/token",
      "userinfo_endpoint": "https://idp.example.com/userinfo",
      "jwks_uri": "https://idp.example.com/jwks",
      "revocation_endpoint": "https://idp.example.com/revoke",
      "client_id": "your-client-id",
      "client_secret": "your-client-secret",
      "client_authentication_type": "client_secret_post",
      "redirect_uri": "https://api.example.com/'"${TENANT_ID}"'/v1/linking/callback/example-idp",
      "scopes_supported": ["openid", "email", "offline_access"],
      "userinfo_mapping_rules": [
        { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
        { "from": "$.http_request.response_body.email", "to": "email" }
      ],
      "refresh_token_expires_in": 86400,
      "duplicate_link_policy": "reject",
      "multiple_accounts": false,
      "revoke_on_relink": false,
      "refresh_before_expiry_seconds": 30
    }
  }'
```

| フィールド | 説明 |
|-----------|------|
| `sso_provider` | 連携先の名前。RP が `/link/{provider}` に指定し、クライアントの `linking_providers` / `linking_token_providers` にも書く |
| `payload.provider` | `standard` |
| `payload.issuer` / `jwks_uri` | 外部IdPが ID Token を返すときの検証に使う（`iss` の一致と署名）。`openid` を要求するなら必須 |
| `payload.authorization_endpoint` / `token_endpoint` | 必須 |
| `payload.userinfo_endpoint` | 任意。設定すると `preferred_username` のマッピング（無ければ `email`）が一覧の `federated_username` になり、ID Token が無いときは `external_user_id` が外部アカウントの識別子になる。同じ連携先を複数つなぐなら、ユーザーが見分けられるように設定する |
| `payload.client_authentication_type` | `client_secret_basic` なら Basic 認証、それ以外はボディに `client_secret` を入れる。`private_key_jwt` などには対応していない。トークン交換・リフレッシュ・失効で共通 |
| `payload.redirect_uri` | Step 1 で登録したコールバックURLと同じ値 |
| `payload.scopes_supported` | RP が `link` で `scope` を省略したときに外部IdPへ要求するスコープ。RP はこの範囲でしか指定できない。`openid` を入れた連携先では、RP の `scope` にも `openid` が要る |
| `payload.revocation_endpoint` | 任意。解除とユーザー削除のときに保管トークンを失効させる先。無ければ失効させずに連携だけを消す |
| `payload.refresh_token_expires_in` | 保管するリフレッシュトークンの有効期間（秒）。外部IdPの実際の期限に合わせる。未設定なら期限は不明として扱い、アクセストークンが切れるたびにリフレッシュを試す |
| `payload.duplicate_link_policy` | 同じ外部アカウントが別ユーザーに連携済みのとき。`reject`（既定、409）/ `allow` |
| `payload.multiple_accounts` | 1 人がこの連携先のアカウントを複数連携できるか。`false`（既定）なら 1 件で、もう一度連携すると更新し、別のアカウントは 409（先に解除）。`true` なら連携するたびに追加（同じ外部アカウントは更新）。`alias` はどちらも `{provider}-{乱数}` |
| `payload.revoke_on_relink` | 連携し直したとき、入れ替わる前の保管トークンを `revocation_endpoint` で失効させるか。`false`（既定）なら失効させない。失効でアプリへの許可ごと取り消す外部IdP（Google など）で `true` にすると、新しいトークンまで止まるので使わない |
| `payload.refresh_before_expiry_seconds` | 保管トークンの取得で、アクセストークンの期限まで何秒を切ったら更新してから返すか。既定 30、0〜3600。外部IdPのアクセストークンの有効期間以上にすると、取り出すたびに更新する |

ログイン用の設定例にある `store_credentials` と `access_token_expires_in` は、連携では読みません。アクセストークンの期限は外部IdPのトークン応答の `expires_in` で決まります。

登録と更新のときに中身を確かめ、必須の項目が無い、`redirect_uri` がこの連携先のコールバックでない、`duplicate_link_policy` の値が違う、`multiple_accounts` / `revoke_on_relink` が真偽値でない、`refresh_before_expiry_seconds` が 0〜3600 の整数でない、`openid` を入れたのに `issuer` / `jwks_uri` が無い、といった設定は `400` になります。`type` と `sso_provider` は後から変えられません（`400`）。連携が 1 件でも残っている間は、この設定の削除は `409` になります（`dry_run=true` でも同じ）。応答の `linked_external_accounts` が残っている連携の数です。先に [管理APIでの確認・解除](#管理apiでの確認解除) で連携を解除してください。連携先を消すときは、まず `enabled: false` にして新しい連携を止めてから、残った連携を解除し、最後に削除します。無効にしないまま進めると、解除と削除の間に新しい連携ができることがあります。

**実例**:
- `config/examples/standard-oidc-web-app/federation-account-linking.json` - サンプルアプリ用
- `config/examples/e2e/test-tenant/federation/account-linking/account-linking.json` - OIDC の連携先
- `config/examples/e2e/test-tenant/federation/account-linking/account-linking-oauth2.json` - OAuth 2.0 だけの連携先（`openid` も userinfo も無い）。外部アカウントを識別できないので、連携するたびに新しい連携として増えます

---

## Step 3: テナントにスコープを追加

認可サーバー設定の `scopes_supported` に3つのスコープを追加します。

```json
{
  "scopes_supported": [
    "openid", "profile", "email",
    "linked_account:read",
    "linked_account:manage",
    "linked_account:read_token"
  ]
}
```

| スコープ | 許すこと |
|---------|---------|
| `linked_account:read` | 連携の一覧 |
| `linked_account:manage` | 連携の開始・確定・解除と一覧 |
| `linked_account:read_token` | 保管トークンの取得（上の2つとは独立） |

既存の設定にこの3つを足して `PUT /v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authorization-server` で更新します。例は `config/examples/standard-oidc-web-app/public-tenant.json` です。

確認すること:
- **`issuer` が `https://{host}/{tenant-id}` の形であること**。RP に返す `start_url` は `{issuer}/v1/linking/start` で組み立てます
- テナントの `session_config.cookie_same_site` を `Strict` にしないこと。`/linking/start` は idp-server のログインセッションの Cookie で操作者を確認します。サンプルの `strict` 構成（`config/examples/standard-oidc-web-app/switch-auth-view.sh strict`）は連携が通らない構成の再現です

---

## Step 4: クライアントを設定

RP のクライアント設定に次を追加します。クライアントの更新（`PUT .../clients/{client-id}`）は設定全体の置き換えなので、既存の設定に足した全体を送ってください。

```json
{
  "scope": "openid profile email linked_account:read linked_account:manage linked_account:read_token",
  "token_endpoint_auth_method": "client_secret_basic",
  "extension": {
    "linking_return_uris": ["https://rp.example.com/linking/callback"],
    "linking_providers": ["example-idp"],
    "linking_token_providers": ["example-idp"]
  }
}
```

| フィールド | 説明 |
|-----------|------|
| `scope` | Step 3 のスコープ。テナントとクライアントの両方に無いと付与されず、`/me` の連携APIは `403 insufficient_scope` になる |
| `extension.linking_return_uris` | 連携が終わったあとにブラウザを戻す先。`link` の `redirect_uri` と**完全一致**で照合する。認可の `redirect_uris` とは別 |
| `extension.linking_providers` | 連携の開始を許す連携先（`sso_provider`）。書いていない連携先への `link` は `403` |
| `extension.linking_token_providers` | 保管トークンの取得を許す連携先（`sso_provider`）。書いていない連携先のトークンは取り出せない（`403 unauthorized_client`） |
| `extension.linking_max_auth_age` | `/linking/start` がそのまま受け入れるログインの新しさ（秒）。古ければその場でログインし直させる。`0` で毎回。省略時は `300` |
| `extension.linking_acr_values` | 連携に普段より強い認証を求めるときの acr（例：FIDO だけのポリシーに当てた値）。ログインがこれに当たらなければログインし直させ、そのログインに `acr_values` として載せる。省略時は制限なし |
| `token_endpoint_auth_method` | 保管トークンを取得するなら、クライアント認証のある方式にする（`attest_jwt_client_auth` も可）。`none` は拒否される |

RP は認可リクエストの `scope` にもこれらを含め、ユーザーのアクセストークンに付与させます。

**実例**: `config/examples/standard-oidc-web-app/public-client.json`、`config/examples/e2e/test-tenant/clients/clientSecretPost.json`

---

## Step 5: RP を実装する

API の入出力は [リソースオーナー向けAPI（OpenAPI）](../../../openapi/swagger-resource-owner-ja.yaml) を参照してください。動く実装は `sample-web/src/app/api/linked-accounts/` と `sample-web/src/app/(authenticated)/linked-accounts/` にあります。

### 5.1 連携を開始する

ユーザーのアクセストークン（`linked_account:manage`）で呼びます。

```bash
curl -X POST "${IDP_SERVER_URL}/${TENANT_ID}/v1/me/linked-external-accounts/link/example-idp" \
  -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "redirect_uri": "https://rp.example.com/linking/callback",
    "scope": "openid email offline_access"
  }'
```

```json
{
  "start_url": "https://api.example.com/{tenant-id}/v1/linking/start?state=...",
  "state": "...",
  "expires_in": 900
}
```

- `redirect_uri` は必須で、Step 4 の `linking_return_uris` のどれかと一致させる
- `scope` は省略すると連携先の `scopes_supported` になる
- 連携は `expires_in`（900秒）以内に終える

### 5.2 `start_url` へトップレベル遷移する

ブラウザを `start_url` へ**トップレベル遷移**させます（`location.href` か、サーバーからの 302）。iframe や `fetch` では Cookie が保存・送信されず成立しません。ポップアップは問題ありません。

idp-server はログインセッションで操作者を確認してから外部IdPへ送り、外部IdPから戻るとトークンを交換して預かり、Step 4 の戻り先へ 302 します。

ブラウザにログインが無いか、ログインがクライアントの `linking_max_auth_age`（既定 300 秒）より古いとき、または `linking_acr_values` を設定していてログインの acr がどれにも当たらないときは、idp-server がその場で認証画面へ送ります。ログインできるのは連携を始めた本人だけで、ログインが終わると外部IdPへ進みます。RP 側で何かする必要はありません。ネイティブアプリは `start_url` を ASWebAuthenticationSession や Custom Tabs で開いてください（ephemeral でも構いません。WebView は使わないでください）。

### 5.3 戻り先で結果を処理する

| 戻り先のクエリ | RP がすること |
|---------------|--------------|
| `linking=done&state=...` | 連携を確定する（下記） |
| `linking=failed&error=...&state=...` | エラーを表示する。この連携セッションは削除済みなので、やり直すときは 5.1 から |
| `linking=failed&error=login_required`（`state` なし） | 連携の途中のログインが取り消されたか、ブラウザにログインを残せなかった。5.1 からやり直す |

`error` は `access_denied` / `login_required` / `temporarily_unavailable` / `server_error` のどれかです。

`linking=done` のときは、**連携を始めたのと同じクライアント**のユーザーのアクセストークンで確定します。

```bash
curl -X POST "${IDP_SERVER_URL}/${TENANT_ID}/v1/me/linked-external-accounts/complete" \
  -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{ "state": "..." }'
```

`201` で連携（`alias`、`provider`、`federated_username`、`scope`、各有効期限など）が返ります。`alias` が以降の操作のキーです。

### 5.4 一覧と解除

```bash
# 一覧（linked_account:read か linked_account:manage）
curl "${IDP_SERVER_URL}/${TENANT_ID}/v1/me/linked-external-accounts" \
  -H "Authorization: Bearer ${USER_ACCESS_TOKEN}"

# 解除（linked_account:manage）。成功は 204
curl -X DELETE "${IDP_SERVER_URL}/${TENANT_ID}/v1/me/linked-external-accounts/${ALIAS}" \
  -H "Authorization: Bearer ${USER_ACCESS_TOKEN}"
```

一覧は `{ "list": [...] }` で、トークンは含みません。解除すると、`revocation_endpoint` があれば外部IdPで保管トークンを失効させてから連携を消します。

### 5.5 保管トークンを取得する

**RP のサーバー側から**、クライアント認証を付けて呼びます。ユーザーのアクセストークン（`linked_account:read_token` 付き、同じクライアントに発行されたもの）を `token` で渡します。

```bash
curl -X POST "${IDP_SERVER_URL}/${TENANT_ID}/v1/linked-external-accounts/${ALIAS}/token" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=${USER_ACCESS_TOKEN}"
```

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "expires_in": 3599
}
```

返るのは外部IdPのアクセストークンだけです。期限が近ければ idp-server が保管しているリフレッシュトークンで更新してから返します。取得したトークンはブラウザに渡さず、サーバー側で外部APIの呼び出しに使ってください。

外部APIが期限前のトークンを `401` で断ったときは、`force_refresh=true` を付けて取り直します。期限に関わらず更新して返します。付けるたびに外部IdPを呼ぶので、断られたときだけ付けてください。

```bash
curl -X POST "${IDP_SERVER_URL}/${TENANT_ID}/v1/linked-external-accounts/${ALIAS}/token" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=${USER_ACCESS_TOKEN}" \
  -d "force_refresh=true"
```

| 応答 | RP がすること |
|------|--------------|
| `400 relink_required` | リフレッシュできない（`force_refresh=true` で更新できなかったときも）。5.1 から連携し直してもらう |
| `400 provider_unavailable` | 連携先が削除・無効化された。連携し直しも失敗するので、連携先が使えないことをユーザーに伝える |
| `502 temporarily_unavailable` | 外部IdPが一時的に応答しない。時間をおいて再試行 |

ユーザートークンが DPoP・mTLS・クライアントインスタンス（ABCA）に縛られているときは、その証明も一緒に出します。DPoP なら `DPoP` ヘッダーの proof（`htu` はこの API の URL、`ath` は `token` のハッシュ）、mTLS なら同じクライアント証明書、ABCA ならトークンを受け取ったインスタンスの鍵でのクライアント認証です。合わなければ `401 invalid_token`。ABCA の縛りを照合するのはこの API だけで、`/me` 系の API（連携・一覧・解除）が照合するのは DPoP と mTLS だけです。`/me` 系でも送り手を縛るなら、ABCA に DPoP を組み合わせます。

---

## Step 6: 運用

### 管理APIでの確認・解除

| メソッド | パス | 権限 |
|---------|------|------|
| GET | `/v1/management/organizations/{organization-id}/tenants/{tenant-id}/users/{user-id}/linked-external-accounts` | `idp:linked-account:read` |
| GET | `.../users/{user-id}/linked-external-accounts/{alias}` | `idp:linked-account:read` |
| DELETE | `.../users/{user-id}/linked-external-accounts/{alias}` | `idp:linked-account:delete` |

システムレベルは `/v1/management/tenants/{tenant-id}/users/{user-id}/linked-external-accounts` です。解除は `dry_run=true` で事前確認できます。

ユーザーを指定せずにテナント全体を見るときは `.../tenants/{tenant-id}/linked-external-accounts` を使います。`provider`・`user_id`・`from`・`to` で絞り込み、`total_count` で件数を返します。連携先を消したり名前を変えたりする前に、その連携先の連携が残っていないかを確かめます。

```bash
# 連携先 example-idp に残っている連携の数（total_count）
curl "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/linked-external-accounts?provider=example-idp&limit=1" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

一覧の `id` で、1 件の取得（`GET .../linked-external-accounts/{id}`）と解除（`DELETE .../linked-external-accounts/{id}`）ができます。

`idp:*` を持つロールには権限が含まれます。権限を列挙しているロールで使うときは、既存のテナントには権限のレコードが無いので、先に作ってからロールに足します。

```bash
# 1. 権限を作る（read / delete それぞれ）
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/permissions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{ "name": "idp:linked-account:read", "description": "Read linked external accounts" }'
```

2. 応答の `result.id` を、ロールの更新（`PUT .../roles/{role-id}`）の `permissions` に足す。`permissions` は権限の `id` の一覧で、送った内容で置き換わるので、既存の権限の `id` も含めて送る

### 連携先の不調を見つける

保管トークンの取り出しのイベント（`external_account_token_*`、`external_account_relink_required`）は、details の `account_linking` に連携先（`provider`）と、断った理由（`error`）を持ちます。

```json
{ "account_linking": { "provider": "example-idp", "error": "relink_required" } }
```

| `error` | 意味 | `provider` |
|---|---|---|
| `relink_required` | リフレッシュトークンが期限切れか、連携先に断られた。ユーザーに連携し直してもらう必要がある | あり |
| `temporarily_unavailable` | 連携先がリフレッシュに 5xx か 429 を返した、または届かない | あり |
| `provider_unavailable` | 連携先の設定が削除・無効化された | あり |
| `unauthorized_client` | クライアントに許可されていない連携先か、ユーザーのトークンが別のクライアントのもの | 連携先の判定ならあり |
| `invalid_client` / `invalid_token` / `insufficient_scope` / `invalid_request` / `not_found` | クライアントやユーザーのトークンの問題 | なし |

連携先ごとに `external_account_relink_required` を数えると、連携先が許可をまとめて取り消したときに気づけます。`temporarily_unavailable` が続くなら連携先の障害です。`provider` の無い失敗が続くなら、連携先ではなくクライアントの問題です。管理API の `security-events` では、`details.account_linking.provider=<連携先>` で絞り込めます。

### 期限切れの連携セッションを消す

確定されずに期限を過ぎた連携セッションは、期限切れデータの一括削除 `POST /v1/admin/operations/delete-expired-data` で消えます。応答の内訳は `deleted.account_linking_session` です。定期実行（Cron 等）に組み込んでください（[運用の概念](../../content_03_concepts/07-operations/concept-04-operations.md)）。

### 暗号化の鍵

保管トークンは、テナントごとのデータ鍵を鍵暗号化鍵（`ENCRYPTION_KEYS`、無ければ `ENCRYPTION_KEY`）で包む 2 段の鍵で暗号化します。追加の設定なしで動きます。テナントのデータ鍵は、管理API の `GET .../tenants/{tenant-id}/data-keys`（`idp:data-key:read`）で一覧でき、`POST .../data-keys/rotate`（`idp:data-key:rotate`）で新しい世代を作れます。鍵の交換と確認は [データ鍵と鍵暗号化鍵のローテーション](../../content_08_ops/commercial-deployment/07-data-key-rotation.md) を参照してください。

### ユーザー・テナントの削除と連携先の無効化

- ユーザーを削除すると、そのユーザーの連携と連携セッションも削除され、保管トークンは外部IdPで失効されます（ベストエフォート）
- テナントを削除するときも、削除の前にテナントの全ての連携のトークンを失効させます。連携 1 件ごとに外部IdPを呼ぶので、連携が多いテナントは削除の応答まで時間がかかります
- 連携先の設定を `enabled: false` にすると新しい連携は始められなくなりますが、解除やユーザー削除のときの失効はそのまま行われます

---

## 動作確認

e2e のシナリオ `e2e/src/tests/scenario/application/scenario-16-account-linking.test.js` が、連携・トークン取得・解除・ユーザー削除・管理API・失敗時の戻りを通しで確認します。

```bash
# テストデータの登録（リポジトリのルートで）
./config/scripts/e2e-test-data.sh
./config/scripts/e2e-test-tenant-data.sh -t 1e68932e-ed4a-43e7-b412-460665e42df3

# 実行（npx jest は使わない）
cd e2e && npm test -- --testPathPattern=scenario-16
```

---

## よくあるエラー

| 症状 | 原因 | 対処 |
|------|------|------|
| `link` が `400`（`redirect_uri is not registered for this client.`） | `redirect_uri` が `linking_return_uris` と一致しない | 末尾のスラッシュまで含めて一致させる |
| `/me` の連携APIが `403 insufficient_scope` | スコープがテナントかクライアントに無い、または認可リクエストで要求していない | Step 3・4 を確認し、トークンを取り直す |
| 戻り先に `error=login_required` | 連携の途中のログインを取り消した、またはログインしてもブラウザにセッションが残らない | 5.1 からやり直す。続くなら `cookie_same_site` が `Strict` でないか、セッションの保存先（Redis）が動いているか確認 |
| `start_url` を開くとエラー画面（RP に戻らない） | 連携セッションが期限切れ・開始済み（再読み込みなど）、別のブラウザや別のユーザーで開いた、または `link` の後で連携先が無効化された | 5.1 の `link` からやり直す。RP は `expires_in` を過ぎたら一覧で結果を確かめる |
| 戻り先に `error=server_error` | 外部IdPでのトークン交換・ID Token 検証・userinfo の失敗 | `client_authentication_type`・`redirect_uri`・`issuer`・`jwks_uri` を確認 |
| `complete` が `403 client_mismatch` | 連携を始めたクライアントと別のクライアントのトークンで確定した | 開始と同じクライアントのトークンを使う |
| `complete` が `400 expired_link` | 連携セッションが期限切れ（900 秒） | 5.1 からやり直す |
| `complete` が `409` | `account_already_linked`: 同じ外部アカウントが別ユーザーに連携済み（`reject`） / `another_account_linked`: この連携先の別のアカウントを連携済み（`multiple_accounts: false`） | `duplicate_link_policy` を見直す / 前の連携を解除してから、期限内なら同じ `state` で `complete` を送り直す |
| トークン取得が `404 not_found` | 保存していた `alias` の連携が解除された。別のアカウントにつなぎ直されると `alias` が変わる | 一覧を引き直して、今の `alias` を使う |
| トークン取得が `400 provider_unavailable` | 連携先が削除・無効化された | 連携先の設定を戻す。戻さないなら、その連携は使えないとユーザーに伝える |
| トークン取得が `400 relink_required` | リフレッシュトークンが無い・期限切れ・拒否された | `refresh_token_expires_in` と、外部IdPがリフレッシュトークンを返すスコープ（例: `offline_access`）を確認 |
| `link` が `403` | 連携先がクライアントの `linking_providers` に無い | Step 4 を確認 |
| `link` が `400`（scope） | 要求した `scope` が連携先の `scopes_supported` に無い | 要求を連携先の設定の範囲にする |
| `link` が `400 provider_unavailable` | `{provider}` の連携先が無い、`sso_provider` の名前が違う、または `enabled: false` | `sso_provider` と `enabled` を確認。運用で止めているなら、RP はその連携先が使えないことを利用者に伝える |
| トークン取得が `403 unauthorized_client` | 連携先が `linking_token_providers` に無い、またはユーザートークンが別クライアントに発行された | Step 4 を確認 |

---

## 関連ドキュメント

- [外部IdPアカウント連携（プロトコル）](../../content_04_protocols/protocol-09-account-linking.md) - 設計・エンドポイント・エラーの詳細
- [外部IdP連携（Federation）の設定](./01-federation-setup.md) - ログイン用の外部IdP連携
- [Federation設定ガイド](../../content_06_developer-guide/05-configuration/federation.md) - `federation-configurations` の項目

---

**難易度**: ⭐⭐⭐☆☆（中級）
**対象**: 外部IdPのトークンを使う機能をRPに組み込む管理者・開発者
