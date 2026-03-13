---
paths:
  - "config/templates/**/*.json"
  - "config/examples/**/*.json"
---

# 設定JSON作成のルール

## `claims_supported` は必須
- `authorization_server` に `claims_supported` がないと、UserInfo/ID Token が `sub` しか返さない
- `config/templates/tenant-template.json` をリファレンスとして参照すること

## リファレンスからの削減方式で作る
- 設定JSONを新規作成する際、ゼロから積み上げるのではなく、`config/templates/tenant-template.json` をベースにコピーし、不要なものを削る方式にすること
- 差分だけでなく共通基盤設定（claims_supported, claims_parameter_supported等）の漏れがないかリファレンスとdiffして確認する

## `custom_claims_scope_mapping` の注意
- `claims:*` プレフィックス付きスコープ（例: `claims:authentication_devices`）を使う場合、`authorization_server.extension` に `"custom_claims_scope_mapping": true` が必要
- ないと `ScopeMappingCustomClaimsCreator` が動作せず、カスタムクレームが含まれない
