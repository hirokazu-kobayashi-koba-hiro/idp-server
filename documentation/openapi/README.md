# openapi

## 身元確認設定からのAPI仕様書生成

身元確認（Identity Verification）設定JSONから、テナント固有の動的エンドポイントの
OpenAPI 3.0.3 仕様書を生成できる。

```shell
# 単一設定から生成（標準出力）
node config/scripts/generate-identity-verification-openapi.js \
  config/examples/e2e/test-tenant/identity/investment-account-opening.json

# 複数設定をまとめて1つの仕様書に（ファイル出力）
node config/scripts/generate-identity-verification-openapi.js \
  config/examples/e2e/test-tenant/identity/investment-account-opening.json \
  config/examples/e2e/test-tenant/identity/trust-service.json \
  -o documentation/openapi/generated/identity-verification.yaml

# JSON形式・サーバーURL指定
node config/scripts/generate-identity-verification-openapi.js <config.json> \
  --format json --server https://idp.example.com

# 実テナントIDを埋め込んだ「そのまま叩ける」仕様書を生成
node config/scripts/generate-identity-verification-openapi.js <config.json> \
  --tenant-id 67e7eae6-62b0-4500-9eff-87459f63fc66
```

設定の `processes` の各プロセスを実際のエンドポイントに展開する:

| プロセス | エンドポイント |
|---------|--------------|
| 先頭の非callbackプロセス（初回申込み） | `POST /{tenant-id}/v1/me/identity-verification/applications/{type}/{process}` |
| 2番目以降 / `required_processes` あり（継続） | `POST /{tenant-id}/v1/me/identity-verification/applications/{type}/{id}/{process}` |
| `"type": "callback"` または `request.basic_auth` あり | `POST /{tenant-id}/internal/v1/identity-verification/callback/{type}/{process}` |
| `registration` 型設定（直接登録） | `POST /{tenant-id}/internal/v1/identity-verification/results/{type}/registration` |

- リクエストボディは `request.schema` から生成（独自キーワード `store` / `respond` は除去）
- レスポンスは `response.body_mapping_rules` から導出（初回申込みのみ `id` がサーバー付与）
- pre_hook 検証・transition 条件・store 保存項目は description に自動記載
- 認証情報（basic_auth / oauth の資格情報）は仕様書に出力されない
- `--tenant-id` 指定時はパス・OAuth URL の `{tenant-id}` を実IDに置換（パラメータ定義も除去）
- `request.schema` が宣言されているのに `properties` が空のプロセスは、宣言漏れの可能性として stderr に警告（生成は継続）

# control plane schema

## authorization-server

```shell
cat ../../libs/idp-server-core/src/main/resources/schema/1.0/authorization-server.json | yq -P > schema.yaml
```

## clientAttributes

```shell
cat ../../libs/idp-server-core/src/main/resources/schema/1.0/clientAttributes.json | yq -P > schema.yaml
```

## authentication

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/authentication-config.json | yq -P > schema.yaml
```

### initial-registration

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/initial-registration/standard.json | yq -P > schema.yaml
```

### sms external

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/sms/external-authn.json | yq -P > schema.yaml
```

### email smtp

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/email/smtp.json | yq -P > schema.yaml
```

### fido-uaf external

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/fido-uaf/external-authn.json | yq -P > schema.yaml
```

### webauthn4j

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/webauthn/webauthn4j.json | yq -P > schema.yaml
```

### legacy-authn

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/legacy/standard.json | yq -P > schema.yaml
```

### authentication-device

```shell
cat ../../libs/idp-server-authentication-interactors/src/main/resources/schema/1.0/authentication/authentication-device/fcm.json | yq -P > schema.yaml
```

### identity-verification-application

```shell
cat ../../libs/idp-server-core-extension-ida/src/main/resources/schema/1.0/identity-verification.json | yq -P > schema.yaml
```