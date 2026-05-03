# fapi2-tenant mTLS Client Certificates

OIDF Conformance Suite の FAPI 2.0 SP Final テストプラン (sender_constrain=mtls) を実行するために
fapi2-tenant に登録している `tls_client_auth` クライアント用の鍵 / 証明書一式。

## ファイル

| ファイル | 用途 |
|---|---|
| `tls-client-auth.pem` | 1st client 証明書 (subject DN: `CN=fapi2-tls-client,O=FAPI 2.0 Certification,C=JP`) |
| `tls-client-auth-key.pem` | 1st client EC P-256 秘密鍵 |
| `tls-client-auth-2.pem` | 2nd client 証明書 (subject DN: `CN=fapi2-tls-client-2,O=FAPI 2.0 Certification,C=JP`) |
| `tls-client-auth-2-key.pem` | 2nd client EC P-256 秘密鍵 |

## 発行元

開発用 CA (`docker/nginx/ca.crt` / `ca.key`) で署名。nginx の `client-ca-bundle.pem` に
ルート CA がバンドル済みなので追加設定なしで mTLS 認証が成立する。

## 再生成手順

CA を入れ替えたり期限切れを起こした場合は以下で再生成:

```bash
CERT_SUBJECT_DN="CN=fapi2-tls-client,O=FAPI 2.0 Certification,C=JP" \
CERT_SAN_DNS="fapi2-tls-client.example.com" \
ORGANIZATION_NAME="fapi2-mtls-client-1" \
bash config/templates/use-cases/financial-grade-2.0/generate-certs.sh

CERT_SUBJECT_DN="CN=fapi2-tls-client-2,O=FAPI 2.0 Certification,C=JP" \
CERT_SAN_DNS="fapi2-tls-client-2.example.com" \
ORGANIZATION_NAME="fapi2-mtls-client-2" \
bash config/templates/use-cases/financial-grade-2.0/generate-certs.sh
```

生成物は `config/generated/fapi2-mtls-client-{1,2}/certs/` に出るので、本ディレクトリに
コピーする (filename は本 README の表に揃える)。

再生成後は OIDF テスト config (`config/examples/financial-grade-2.0/oidc-test/fapi2/mtls.json`)
の `mtls.cert` / `mtls.key` / `mtls2.cert` / `mtls2.key` も更新する必要がある。

## 関連設定

- `config/examples/e2e/fapi2-tenant/clients/tlsClientAuth.json` — `tls_client_auth_subject_dn` で
  `CN=fapi2-tls-client,...` と紐付け
- `config/examples/e2e/fapi2-tenant/clients/tlsClientAuth2.json` — 2nd client
- `config/examples/financial-grade-2.0/oidc-test/fapi2/mtls.json` — OIDF テストプランの入力 JSON
