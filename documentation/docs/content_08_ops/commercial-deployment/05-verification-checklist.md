# 検証・テストチェックリスト

idp-server の商用デプロイメント後の包括的な検証・テスト手順について説明します。機能テスト、セキュリティテスト、パフォーマンステスト、コンプライアンステストを含みます。

---

## 🧪 機能テスト

### OAuth 2.0 / OpenID Connect 基本フロー

#### 認可コードフロー (Authorization Code Flow)
```bash
# 1. 認可リクエスト
AUTHORIZATION_URL="$SERVER_URL/$TENANT_ID/oauth/authorize"
CLIENT_ID="webapp-spa"
REDIRECT_URI="https://app.sample-corp.com/callback"
STATE=$(openssl rand -hex 16)
NONCE=$(openssl rand -hex 16)
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-43)
CODE_CHALLENGE=$(echo -n $CODE_VERIFIER | openssl dgst -sha256 -binary | base64 | tr -d "=+/" | cut -c1-43)

echo "認可URL: $AUTHORIZATION_URL?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&scope=openid%20profile%20email&state=$STATE&nonce=$NONCE&code_challenge=$CODE_CHALLENGE&code_challenge_method=S256"

# 2. トークンリクエスト (認可コード取得後)
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=$AUTHORIZATION_CODE" \
  -d "redirect_uri=$REDIRECT_URI" \
  -d "client_id=$CLIENT_ID" \
  -d "code_verifier=$CODE_VERIFIER"

# 3. UserInfo エンドポイント
curl -X GET "$SERVER_URL/$TENANT_ID/oauth/userinfo" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

#### クライアントクレデンシャルフロー
```bash
# M2M 認証テスト
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n '$CLIENT_ID:$CLIENT_SECRET' | base64)" \
  -d "grant_type=client_credentials" \
  -d "scope=api:read api:write"
```

#### リフレッシュトークンフロー
```bash
# リフレッシュトークンによるアクセストークン更新
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN" \
  -d "client_id=$CLIENT_ID"
```

### FAPI (Financial-grade API) テスト

#### FAPI Baseline テスト
```bash
# FAPI Baseline 準拠リクエスト
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n '$FAPI_CLIENT_ID:$FAPI_CLIENT_SECRET' | base64)" \
  -d "grant_type=authorization_code" \
  -d "code=$AUTHORIZATION_CODE" \
  -d "redirect_uri=$REDIRECT_URI" \
  -d "code_verifier=$CODE_VERIFIER"

# mTLS 証明書認証テスト
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  --cert client.crt \
  --key client.key \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "scope=accounts:read"
```

### CIBA (Client Initiated Backchannel Authentication) テスト

```bash
# 1. CIBA 認証リクエスト
CIBA_REQUEST=$(curl -X POST "$SERVER_URL/$TENANT_ID/oauth/ciba" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n '$CLIENT_ID:$CLIENT_SECRET' | base64)" \
  -d "scope=openid profile" \
  -d "login_hint=user@sample-corp.com" \
  -d "binding_message=BINDING123")

AUTH_REQ_ID=$(echo $CIBA_REQUEST | jq -r '.auth_req_id')

# 2. ポーリングによるトークン取得
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n '$CLIENT_ID:$CLIENT_SECRET' | base64)" \
  -d "grant_type=urn:openid:params:grant-type:ciba" \
  -d "auth_req_id=$AUTH_REQ_ID"
```

### MFA・認証デバイステスト

#### WebAuthn/FIDO2 テスト
```bash
# 1. WebAuthn 登録開始
curl -X POST "$SERVER_URL/v1/users/$USER_ID/mfa/webauthn/registration/start" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
    "authenticator_selection": {
      "authenticator_attachment": "platform",
      "user_verification": "required"
    }
  }'

# 2. WebAuthn 認証テスト
curl -X POST "$SERVER_URL/v1/users/$USER_ID/mfa/webauthn/authentication/start" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

#### TOTP (Time-based OTP) テスト
```bash
# TOTP 登録
curl -X POST "$SERVER_URL/v1/users/$USER_ID/mfa/totp/registration" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json"

# TOTP 認証
curl -X POST "$SERVER_URL/v1/users/$USER_ID/mfa/totp/verify" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{
    "code": "123456"
  }'
```

---

## 🔐 セキュリティテスト

### 認証セキュリティテスト

#### パスワードポリシー検証
```bash
# 弱いパスワードの拒否テスト
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "username": "test_weak_password",
    "email": "test@example.com",
    "password": "123456"
  }'
# 期待結果: 400 Bad Request (パスワードポリシー違反)

# 最小長度違反テスト
curl -X POST "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" \
  --data '{
    "username": "test_short_password",
    "email": "test@example.com",
    "password": "Abc1!"
  }'
# 期待結果: 400 Bad Request (最小長度違反)
```

#### ブルートフォース攻撃対策テスト
```bash
# 連続ログイン失敗テスト
for i in {1..6}; do
  curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "username=test_user" \
    -d "password=wrong_password" \
    -d "client_id=$CLIENT_ID"
  echo "Attempt $i"
done
# 期待結果: 5回目以降はアカウントロック
```

#### CSRF・状態パラメータ検証
```bash
# 状態パラメータなしでの認可リクエスト
curl -X GET "$SERVER_URL/$TENANT_ID/oauth/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI"
# 期待結果: エラー (state パラメータ必須)

# 無効な state パラメータでのコールバック
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=$AUTHORIZATION_CODE" \
  -d "redirect_uri=$REDIRECT_URI" \
  -d "client_id=$CLIENT_ID" \
  -d "state=invalid_state"
# 期待結果: エラー (state 不一致)
```

### 認可・アクセス制御テスト

#### スコープ制限テスト
```bash
# 許可されていないスコープリクエスト
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n '$CLIENT_ID:$CLIENT_SECRET' | base64)" \
  -d "grant_type=client_credentials" \
  -d "scope=admin:write"
# 期待結果: エラー (スコープ未許可)

# リソースアクセス権限テスト
curl -X GET "$SERVER_URL/v1/admin/tenants" \
  -H "Authorization: Bearer $LIMITED_ACCESS_TOKEN"
# 期待結果: 403 Forbidden (権限不足)
```

#### テナント分離テスト
```bash
# 他テナントのリソースアクセス試行
curl -X GET "$SERVER_URL/v1/admin/tenants/$OTHER_TENANT_ID/users" \
  -H "Authorization: Bearer $TENANT_A_ADMIN_TOKEN"
# 期待結果: 403 Forbidden (テナント分離)
```

### 暗号化・トークン検証

#### JWT 署名検証
```bash
# 改ざんされた JWT の検証
TAMPERED_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.TAMPERED_PAYLOAD.INVALID_SIGNATURE"

curl -X GET "$SERVER_URL/$TENANT_ID/oauth/userinfo" \
  -H "Authorization: Bearer $TAMPERED_TOKEN"
# 期待結果: 401 Unauthorized (署名検証失敗)
```

---

## ⚡ パフォーマンステスト

### 負荷テスト (Apache Bench)

#### 認証エンドポイント負荷テスト
```bash
# 認証リクエスト負荷テスト
ab -n 1000 -c 10 \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -p token_request.txt \
  "$SERVER_URL/$TENANT_ID/oauth/token"

# token_request.txt の内容例:
# grant_type=client_credentials&client_id=test-client&client_secret=test-secret&scope=api:read
```

#### UserInfo エンドポイント負荷テスト
```bash
# UserInfo エンドポイント負荷テスト
ab -n 1000 -c 20 \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$SERVER_URL/$TENANT_ID/oauth/userinfo"
```

### K6 パフォーマンステスト

#### OAuth フロー総合テスト
```javascript
// oauth-performance-test.js
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 200 },
    { duration: '5m', target: 200 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  // クライアントクレデンシャル認証
  let tokenResponse = http.post(`${__ENV.SERVER_URL}/${__ENV.TENANT_ID}/oauth/token`, {
    grant_type: 'client_credentials',
    client_id: __ENV.CLIENT_ID,
    client_secret: __ENV.CLIENT_SECRET,
    scope: 'api:read',
  });

  check(tokenResponse, {
    'token status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}
```

```bash
# K6 テスト実行
SERVER_URL=$SERVER_URL TENANT_ID=$TENANT_ID CLIENT_ID=$CLIENT_ID CLIENT_SECRET=$CLIENT_SECRET \
  k6 run oauth-performance-test.js
```

### データベース性能テスト

#### 接続プール・クエリ性能
```bash
# データベース接続数確認
curl -X GET "$SERVER_URL/actuator/metrics/hikaricp.connections.active" \
  -H "Content-Type: application/json"

# データベースクエリ実行時間確認
curl -X GET "$SERVER_URL/actuator/metrics/spring.data.repository.invocations" \
  -H "Content-Type: application/json"
```

---

## 🧐 コンプライアンステスト

### OIDC Conformance Test

#### OIDC 仕様準拠テスト
```bash
# Discovery エンドポイント検証
curl -X GET "$SERVER_URL/$TENANT_ID/.well-known/openid_configuration" | jq

# 必須フィールド確認:
# - issuer
# - authorization_endpoint
# - token_endpoint
# - jwks_uri
# - response_types_supported
# - subject_types_supported
# - id_token_signing_alg_values_supported
```

#### JWKS エンドポイント検証
```bash
# JWKS 公開鍵確認
curl -X GET "$SERVER_URL/$TENANT_ID/.well-known/jwks.json" | jq

# JWT 署名検証 (Node.js example)
node -e "
const jose = require('jose');
const jwks = require('./jwks.json');
const token = '$ID_TOKEN';
jose.jwtVerify(token, jose.createLocalJWKSet(jwks))
  .then(result => console.log('Valid JWT:', result))
  .catch(err => console.error('Invalid JWT:', err));
"
```

### FAPI Conformance Test

#### FAPI セキュリティプロファイル検証
```bash
# FAPI Discovery メタデータ確認
curl -X GET "$SERVER_URL/$TENANT_ID/.well-known/openid_configuration" | \
  jq '.tls_client_certificate_bound_access_tokens, .require_request_uri_registration'

# mTLS エンドポイント確認
curl -X GET "$SERVER_URL/$TENANT_ID/.well-known/openid_configuration" | \
  jq '.mtls_endpoint_aliases'
```

### GDPR コンプライアンステスト

#### データ削除権 (Right to be Forgotten)
```bash
# ユーザーデータ削除リクエスト
curl -X DELETE "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users/$USER_ID?gdpr_deletion=true" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json"

# 削除確認 (404 Not Found が期待される)
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users/$USER_ID" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET"
```

#### データポータビリティ
```bash
# ユーザーデータエクスポート
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users/$USER_ID/export" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" \
  -H "Content-Type: application/json" > user_data_export.json
```

---

## 📊 監視・ログ検証

### アプリケーション監視

#### ヘルスチェック確認
```bash
# アプリケーションヘルス
curl -X GET "$SERVER_URL/actuator/health" | jq

# データベースヘルス
curl -X GET "$SERVER_URL/actuator/health/db" | jq

# Redis ヘルス
curl -X GET "$SERVER_URL/actuator/health/redis" | jq
```

#### メトリクス確認
```bash
# JVM メトリクス
curl -X GET "$SERVER_URL/actuator/metrics/jvm.memory.used" | jq

# HTTP リクエストメトリクス
curl -X GET "$SERVER_URL/actuator/metrics/http.server.requests" | jq

# カスタムメトリクス
curl -X GET "$SERVER_URL/actuator/metrics/idp.authentication.success" | jq
```

### 監査ログ検証

#### セキュリティイベントログ
```bash
# 監査ログ取得
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/audit-logs?event_type=authentication&limit=100" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET" | jq

# ログフィールド検証:
# - timestamp
# - event_type
# - user_id
# - tenant_id
# - ip_address
# - user_agent
# - outcome
```

### CloudWatch ログ確認

```bash
# CloudWatch ログ取得 (AWS CLI)
aws logs filter-log-events \
  --log-group-name "/ecs/idp-server" \
  --start-time $(date -d '1 hour ago' +%s)000 \
  --filter-pattern "ERROR"

# X-Ray トレース確認
aws xray get-trace-summaries \
  --time-range-type TimeRangeByStartTime \
  --start-time $(date -d '1 hour ago' +%s) \
  --end-time $(date +%s)
```

---

## 🚨 障害・復旧テスト

### データベース障害テスト

#### プライマリ障害・フェイルオーバー
```bash
# プライマリデータベース停止シミュレーション
aws rds reboot-db-instance \
  --db-instance-identifier idp-server-postgresql \
  --force-failover

# アプリケーション自動復旧確認
curl -X GET "$SERVER_URL/actuator/health/db"
```

#### 読み取り専用レプリカ障害
```bash
# レプリカ停止後のクエリ動作確認
aws rds stop-db-instance \
  --db-instance-identifier idp-server-postgresql-replica

# 読み取りクエリの主系へのフォールバック確認
curl -X GET "$SERVER_URL/v1/admin/tenants/$TENANT_ID/users?limit=10" \
  -u "$IDP_SERVER_API_KEY:$IDP_SERVER_API_SECRET"
```

### Redis 障害テスト

```bash
# Redis クラスター障害シミュレーション
aws elasticache reboot-cache-cluster \
  --cache-cluster-id idp-server-redis-001

# セッション継続性確認 (キャッシュ無効化時の動作)
curl -X GET "$SERVER_URL/$TENANT_ID/oauth/userinfo" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### ネットワーク障害テスト

#### タイムアウト・接続エラーハンドリング
```bash
# タイムアウト設定テスト (外部API)
curl -X POST "$SERVER_URL/$TENANT_ID/oauth/token" \
  --max-time 1 \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET"
```

---

## ✅ 包括的検証チェックリスト

### 機能テスト
- [ ] OAuth 2.0 認可コードフロー動作確認
- [ ] クライアントクレデンシャルフロー動作確認
- [ ] リフレッシュトークンフロー動作確認
- [ ] OIDC Discovery・UserInfo 動作確認
- [ ] FAPI Baseline・Advanced 動作確認
- [ ] CIBA フロー動作確認
- [ ] WebAuthn/FIDO2 認証動作確認
- [ ] MFA (TOTP、SMS) 動作確認

### セキュリティテスト
- [ ] パスワードポリシー強制確認
- [ ] ブルートフォース攻撃対策確認
- [ ] CSRF・状態パラメータ検証
- [ ] スコープ・権限制御確認
- [ ] テナント分離確認
- [ ] JWT 署名検証確認
- [ ] 暗号化・TLS 設定確認

### パフォーマンステスト
- [ ] 認証エンドポイント負荷テスト (目標: 95%ile < 500ms)
- [ ] UserInfo エンドポイント負荷テスト
- [ ] データベース接続プール確認
- [ ] キャッシュ性能確認
- [ ] 同時接続数テスト (目標: 1000+ TPS)

### コンプライアンステスト
- [ ] OIDC 仕様準拠確認
- [ ] FAPI セキュリティプロファイル確認
- [ ] GDPR データ削除権確認
- [ ] 監査ログ・トレーサビリティ確認
- [ ] SOC 2 統制要件確認

### 可用性・障害テスト
- [ ] データベースフェイルオーバー確認
- [ ] Redis クラスター障害確認
- [ ] ネットワーク障害・タイムアウト確認
- [ ] 自動復旧・ヘルスチェック確認
- [ ] バックアップ・復元手順確認

### 監視・運用テスト
- [ ] CloudWatch メトリクス・アラーム確認
- [ ] ログ収集・集約確認
- [ ] X-Ray 分散トレーシング確認
- [ ] セキュリティイベント通知確認
- [ ] ダッシュボード・可視化確認

---

## 🔗 関連ドキュメント

- [前提条件](./01-prerequisites.md)
- [環境変数・セキュリティパラメータ](./02-environment-variables.md)
- [AWS インフラ構築](./03-aws-infrastructure.md)
- [初期設定・ユーザー・ロール](./04-initial-configuration.md)
- [運用ガイダンス](./06-operational-guidance.md)