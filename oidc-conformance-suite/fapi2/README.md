# FAPI 2.0 Security Profile Final

| | |
|---|---|
| 状態 | ❌ 未配線（`run.sh` なし） |
| テストプラン | `fapi2-security-profile-final-test-plan` |
| モジュール数 | 72 |
| テナント | **存在しない** |
| テスト設定 | `config/examples/financial-grade-2.0/oidc-test/fapi2/{private_key_jwt,mtls}.json`（存在する） |
| ブラウザ操作 | 必要と思われる（未検証） |

プランは設定ファイルの `description` が「FAPI 2.0 Security Profile Final test configuration」
と明記しているため `fapi2-security-profile-final-test-plan` で確定。
（suite には `fapi2-message-signing-final-test-plan` もあるが、そちらは 90 モジュールの別プラン）

## 何が足りないか

**テナントそのもの。** `config/examples/financial-grade-2.0/` には `oidc-test/` しか無い。

```
config/examples/financial-grade-2.0/
└── oidc-test/
    └── fapi2/
        ├── private_key_jwt.json
        └── mtls.json
```

FAPI 1.0 側にある `setup.sh` / `financial-tenant.json` / `financial-client.json` /
`authentication-policy/` に相当するものが無いため、テスト対象の認可サーバーが立たない。

テスト設定 JSON の `alias` は `idp-server-fapi2-private_key_jwt` で、クライアント ID や
discovery URL は書かれている。それらに一致するテナントを作る設定一式が要る。

## variants（未確定）

```
fapi_profile, authorization_request_type, client_auth_type, grant_management, openid, sender_constrain
```

FAPI 1.0 とはキーの構成が大きく違う（`sender_constrain` や `grant_management` は FAPI 2.0 固有）。
配線する際は suite の `/api/plan/available` で確認すること。
