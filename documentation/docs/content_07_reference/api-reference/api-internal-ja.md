# 内部API仕様（身元確認コールバック）

外部の身元確認サービスが idp-server に審査結果を返すための API です。

---

## 概要

| API | パス | 用途 |
|---|---|---|
| コールバック（申込みID指定） | `POST /{tenant-id}/internal/v1/identity-verification/callback/{verification-type}/{id}/{process}` | 申込みIDで申込みを特定して、process を実行する |
| コールバック（ボディで特定） | `POST /{tenant-id}/internal/v1/identity-verification/callback/{verification-type}/{process}` | リクエストボディの値で申込みを特定して、process を実行する |
| 直接登録 | `POST /{tenant-id}/internal/v1/identity-verification/results/{verification-type}/registration` | 申込みを経ずに身元確認結果を登録する（[身元確認データ登録](../../content_05_how-to/phase-4-extensions/identity-verification/03-registration.md)） |

コールバックで実行する process は、Control Plane API で登録した身元確認設定（`processes`）で定義します。

:::warning インターネットに公開しない
`/{tenant-id}/internal/...` は、外部の身元確認サービスとの間だけで使う API です。ロードバランサーやネットワークの設定で、インターネットから届かないようにしてください。`request.basic_auth` は追加の防御で、公開しない前提の代わりにはなりません。
:::

---

## パスパラメータ

| パラメータ | 説明 | 例 |
|---|---|---|
| `tenant-id` | テナントID | `67e7eae6-62b0-4500-9eff-87459f63fc66` |
| `verification-type` | 身元確認設定の `type` | `investment-account-opening` |
| `id` | 申込みID（申込み API の応答の `id`） | `550e8400-e29b-41d4-a716-446655440000` |
| `process` | 身元確認設定の `processes` のキー | `callback-result` |

---

## 実行できる process

コールバックから実行できるのは、次の process です（[process を呼べる入口](../../content_05_how-to/phase-4-extensions/identity-verification/02-application.md#process-caller)）。

| `caller` | コールバックから |
|---|---|
| `external_service` | 実行できる |
| `end_user` | 404 |
| 省略 | 実行できる |

それ以外の process を呼ぶと、登録されていない process と同じ 404 になります。

---

## 申込みの特定

- **申込みID指定**: パスの `{id}` で申込みを特定します。
- **ボディで特定**: 身元確認設定の `common.callback_application_id_param` に書いたキーの値を、リクエストボディから読みます。その値と、申込み時に `store.application_details_mapping_rules` で同じキーに保存した値が一致する申込みを使います。

```json
{
  "common": {
    "callback_application_id_param": "application_id"
  },
  "processes": {
    "apply": {
      "store": {
        "application_details_mapping_rules": [
          { "from": "$.response_body.application_id", "to": "application_id" }
        ]
      }
    }
  }
}
```

この設定では、申込み時に外部サービスが返した `application_id` を保存し、コールバックのボディの `application_id` で申込みを特定します。一致する申込みが無いとき、または複数あるときは 404 です。

---

## 認証

process の `request.basic_auth` に `username` と `password` を両方書くと、その process のコールバックで Basic 認証を確認します。照合は定数時間で行います。

```json
"callback-result": {
  "caller": "external_service",
  "request": {
    "basic_auth": { "username": "ekyc-vendor", "password": "..." }
  }
}
```

- `request.basic_auth` を書いていない process は、認証を確認しません。
- Basic 認証のほかに、コールバックを受け取る側で確認する認証方式はありません。`execution.http_request` の `oauth_authorization` / `hmac_authentication` は、idp-server が外部 API を呼び出すときの設定です。

---

## 処理

コールバックは、申込み API と同じフェーズ（request → pre_hook → execution → transition → store → response）で処理します。

- `execution` を省略すると `no_action` になり、外部 API は呼びません。受け取ったリクエストボディは、`transition` や `store` から `$.request_body` で参照できます。
- `transition` の条件に一致しなければ、申込みのステータスは `examination_processing` になります。
- 承認（`approved`）・却下（`rejected`）・取り消し（`cancelled`）・期限切れ（`expired`）になった申込みに同じコールバックを再送しても、ステータスは変わらず、承認時の処理（身元確認結果の登録・ユーザーの更新・イベントの発行）も繰り返しません。`pre_hook` と `execution`（外部 API の呼び出しを含む）、`application_details` の更新、process の実行回数の記録は、再送でも行います。
- `dependencies.allow_retry: false` と `pre_hook.verifications` の `process_sequence` を設定した process は、2 回目の実行が 400（`pre_hook_validation_failed`）になります。

---

## 応答

### 成功（200）

process の `response.body_mapping_rules` で組み立てたボディを返します。

### エラー

| ステータス | `error` | 発生条件 |
|---|---|---|
| 400 | `invalid_request` | `request.schema` に合わない。`request.basic_auth` を設定した process で、Basic 認証が無い・一致しない |
| 400 | `pre_hook_validation_failed` | `pre_hook.verifications` が失敗した |
| 404 | `invalid_request` | `verification-type` の身元確認設定が無い、または無効（`enabled: false`）。process が無い、またはコールバックから実行できない。申込みが見つからない、または申込みの種別がパスの `verification-type` と違う |
| 4xx / 5xx | `execution_failed` | `execution.http_request` で呼び出した外部 API が失敗した。外部 API の状態コードを返す（idp-server が扱わないコードは、4xx なら 400、それ以外は 500） |
| 400 | `external_service_error` | `pre_hook.additional_parameters` の `http_request` が失敗し、処理を止めた |
| 4xx / 5xx | `sso_credentials_error` | `pre_hook.additional_parameters` の `sso_credentials` が失敗し、処理を止めた（トークンの取得が HTTP エラーのときはその状態コード、それ以外は 400） |

```json
{
  "error": "invalid_request",
  "error_description": "identity verification callback is invalid.",
  "error_messages": [
    "The identity verification request unmatch a basic authentication"
  ]
}
```

---

## 例

```bash
curl -X POST "https://idp-server.example.com/{tenant-id}/internal/v1/identity-verification/callback/investment-account-opening/550e8400-e29b-41d4-a716-446655440000/callback-result" \
  -u "ekyc-vendor:${CALLBACK_PASSWORD}" \
  -H "Content-Type: application/json" \
  -d '{
    "application_id": "ext-12345",
    "verification_result": "approved"
  }'
```

---

## 関連ドキュメント

- [身元確認申込み](../../content_05_how-to/phase-4-extensions/identity-verification/02-application.md)
- [身元確認データ登録](../../content_05_how-to/phase-4-extensions/identity-verification/03-registration.md)
- [リソースオーナー用API仕様](./api-resource-owner-ja.md)
- [身元確認設定管理API仕様](/docs/content_07_reference/cp-identity-verification-api-ja/)
