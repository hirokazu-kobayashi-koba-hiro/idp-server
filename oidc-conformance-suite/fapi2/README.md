# FAPI 2.0 Security Profile Final

| | |
|---|---|
| 状態 | ✅ 配線済み（`run.sh` あり） |
| テストプラン | `fapi2-security-profile-final-test-plan` |
| モジュール数 | 56 |
| テナント | `config/examples/financial-grade-2.0` |
| テスト設定 | `config/examples/financial-grade-2.0/oidc-test/fapi2/{private_key_jwt,mtls}.json` |
| ブラウザ操作 | 必要（`../driver/` を常駐させる） |

suite には `fapi2-message-signing-final-test-plan` もあるが、そちらは JAR/JARM を必須にする
別プロファイル（90 モジュール）。ここでは扱わない。

## variants

```
fapi2-security-profile-final-test-plan[client_auth_type=...][sender_constrain=...][fapi_profile=plain_fapi][authorization_request_type=simple][openid=openid_connect][grant_management=disabled]
```

| variant | 値 | 根拠 |
|---|---|---|
| `fapi_profile` | `plain_fapi` | 地域プロファイル（brazil / uk / ksa / cbuae / connectid）ではない |
| `authorization_request_type` | `simple` | RAR (`authorization_details`) ではなく scope ベース |
| `openid` | `openid_connect` | テスト設定の scope に `openid` が含まれる |
| `grant_management` | `disabled` | discovery に `grant_management_action_required` が無い |
| `client_auth_type` / `sender_constrain` | 下記 | 設定ファイルごとに決まる |

**2 つの設定ファイルの違いは「クライアント認証方式」ではなく `sender_constrain` の方式。**
取り違えると、証明書を持たない設定に `mtls` を当てて `ExtractMTLSCertificatesFromConfiguration`
で落ちる。

| 設定ファイル | client_auth_type | sender_constrain | 見分け方 |
|---|---|---|---|
| `private_key_jwt.json` | `private_key_jwt` | `dpop` | `dpop_signing_alg` があり `mtls` ブロックが無い |
| `mtls.json` | `mtls` | `mtls` | `mtls` / `mtls2` ブロックがあり `resourceUrl` が `mtls.api.local.test` |

## 手順

```bash
# 1. idp-server とテナント
docker compose up -d
./config/examples/financial-grade-2.0/setup.sh

# 2. suite スタック
docker compose -f oidc-conformance-suite/docker-compose.yaml up -d

# 3. ブラウザ操作ドライバ（別ターミナルで常駐。FAPI 1.0 と共用で 1 プロセスだけ）
cd oidc-conformance-suite/driver && npm install && node driver.mjs

# 4. テスト
export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
./oidc-conformance-suite/fapi2/run.sh --rerun 1:2   # happy path 1本
./oidc-conformance-suite/fapi2/run.sh --rerun 1     # dpop プランすべて
./oidc-conformance-suite/fapi2/run.sh               # dpop / mtls 両方
```

## テナントとクライアント

`config/examples/financial-grade-2.0/setup.sh` が、テンプレート
（`config/templates/use-cases/financial-grade-2.0/`）を固定 ID で呼び出して作る。
テスト設定 JSON がテナント ID とクライアント ID を直接持っているため、ID は固定でないといけない。

| リソース | ID |
|---|---|
| organization | `c1f2a3b4-d5e6-4f7a-8b9c-0d1e2f3a4b5c` |
| tenant | `c3f4a5b6-d7e8-4f9a-0b1c-2d3e4f5a6b7c` |
| 適合性テスト用クライアント | `clients/*.json`（`d1a2b3c4…` 〜 `d4a5b6c7…`） |

`clients/` の 4 つは **suite が秘密鍵と証明書を持っている**クライアント。鍵はテスト設定 JSON に
埋まっているので、サーバ側をそれに合わせる必要がある。定義は
`config/examples/e2e/fapi2-tenant/clients/` と同じ鍵・DN で、ID と alias だけ別にしてある
（`client_configuration` の主キーは `id` 単独なので client_id はテナントをまたいで一意。
e2e 側と同じ ID を使うと 409 になる）。

**既存の e2e テナント（`config/examples/e2e/fapi2-tenant/`）は使わない。** そちらは
`e2e/src/tests/spec/fapi2_0_mtls.test.js` が使っており、認証ポリシーや `ui_config` を
適合性テストの都合で変えると影響が読めないため分離している。

## scope が FAPI 2.0 プロファイルを起動する

idp-server はリクエストされた scope でプロファイルを決める
（`AuthorizationServerExtensionConfiguration.isFapi20()`）。テナント設定の

```json
"fapi20_scopes": ["write", "transfers"]
```

に含まれる scope が要求されて初めて FAPI 2.0 の検証が走る。**テスト設定の `scope` にこれが
入っていないと、PAR の PKCE 必須・client assertion の `aud` 制限・sender-constrain 必須が
すべて素通りし、「テストが落ちているのに原因は設定」という読み違えになる。**

このため両テスト設定の scope は `openid profile email write` にしてある
（`transfers` は `required_identity_verification_scopes` に入っており身元確認が必要になるので使わない）。

## 実測済みの結果

`private_key_jwt` + `sender_constrain=dpop` プランの通し実行（56 モジュール）。

| 結果 | 件数 | |
|---|---|---|
| PASSED | 47 | |
| REVIEW | 3 | エラーページのスクリーンショット提出で終わるテスト。正常 |
| WARNING | 4 | 下記 |
| SKIPPED | 1 | `ensure-signed-client-assertion-with-RS256-fails`。クライアント鍵が ES256 なので suite 自身がスキップする |
| FAILED | 1 | 下記 |

happy path（`fapi2-security-profile-final-happy-flow`）は
`FINISHED - result PASSED. 351 log entries - 239 SUCCESS 0 FAILURE, 0 WARNING, 10.6 seconds`。

`mtls` プランはまだ通していない。

### FAILED

| テスト | 要件 | 内容 |
|---|---|---|
| `refresh-token` | FAPI 2.0 SP Final **5.3.2.1-9** | リフレッシュトークンをローテーションする場合、**直前のトークンも一定時間は受け付ける**必要がある（レスポンスを受け取り損ねたクライアントの救済）。suite は新トークン取得の 30 秒後に旧トークンで再取得し 200 を期待するが、idp-server は `invalid_grant: refresh token does not exists.` を返す |

### WARNING

| テスト | 条件 | 内容 |
|---|---|---|
| `discovery-end-point-verification` | `CheckForUnexpectedParametersInServerMetadata` | discovery の `verified_claims_supported` が suite の rfc8414 スキーマに無い。OIDC4IDA の他のメタデータは登録済みなので suite 側の登録漏れ。idp-server は仕様どおり |
| `attempt-reuse-authorization-code-after-one-second` | `EnsureHttpStatusCodeIs4xx` | 認可コード再利用後に発行済みアクセストークンを失効させていない（RFC 6749 §4.1.2 の SHOULD）。FAPI 1.0 側と同じ |
| `dpop-negative-tests` | `EnsureHttpStatusCodeIs400or401` | 同じ `jti` の DPoP proof を 2 回受け付けている。`DPoPProofVerifier.java:226` に未実装と明記されている（RFC 9449 §4.3 は条件付き要件） |
| `test-claims-parameter-identity-claims` | `EnsureIdentityClaimsContainRequestedClaims` | `claims` パラメータで要求した `name` / `given_name` 等が返らない。**テストユーザーにその属性が入っていないため**で、実装の問題ではない（要求した `phone_number_verified` は返っている）。同テストの `CheckForUnexpectedClaimsInIdToken` は ID Token の `sid` を suite が知らないだけ |
