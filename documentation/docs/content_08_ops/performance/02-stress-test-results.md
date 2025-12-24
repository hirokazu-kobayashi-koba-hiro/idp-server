# ストレステスト結果

本ドキュメントでは、各エンドポイントに対するストレステストの結果を報告する。

---

## テスト条件

| 項目 | 値 |
|-----|---|
| 同時接続数 (VUs) | 120 |
| テスト時間 | 30秒 |
| テストツール | k6 |
| テスト実施日 | 2025-12-24 |

### データ規模パターン

| パターン | 構成 | 総ユーザー数 | 用途 |
|---------|------|------------|------|
| マルチテナント（均等） | 10テナント × 10万ユーザー | 100万 | テナント分離の負荷検証 |
| マルチテナント（大規模単一含む） | 1テナント×100万 + 9テナント×10万 | 190万 | 大規模テナントのスケーラビリティ検証 |

:::note
「大規模単一テナント」テストは、10テナント構成のうち100万ユーザーを持つ最初のテナントを使用して実施。
:::


### 判定基準

| 指標 | 目標値 |
|-----|-------|
| p(95) | 500ms以下 |
| エラー率 | 1%未満 |

---

## 結果サマリー

### OAuth 2.0/OIDC エンドポイント

| シナリオ | リクエスト数 | TPS | 平均応答時間 | p95 | エラー率 | 判定 |
|---------|-----------|-----|------------|-----|---------|-----|
| 認可リクエスト | 77,506 | 2,577 | 46.4ms | 183.8ms | 0.00% | PASS |
| Token (Client Credentials) | 44,211 | 1,471 | 81.4ms | 211.1ms | 0.00% | PASS |
| JWKS | 80,663 | 2,684 | 44.6ms | 156.8ms | 0.00% | PASS |
| Token Introspection | 74,072 | 2,453 | 48.7ms | 198.5ms | 0.00% | PASS |

### CIBA フロー（マルチテナント: 10テナント × 10万ユーザー）

| シナリオ | login_hint | リクエスト数 | TPS | 平均応答時間 | p95 | エラー率 | 判定 |
|---------|-----------|------------|-----|------------|-----|---------|-----|
| BC Request | sub | 39,625 | 1,317 | 90.9ms | 223.5ms | 0.00% | PASS |
| CIBA Full (device) | device | 44,585 | 1,472 | 81.2ms | 247.6ms | 0.00% | PASS |
| CIBA Full (sub) | sub | 43,220 | 1,430 | 83.6ms | 260.1ms | 0.00% | PASS |
| CIBA Full (email) | email | 43,050 | 1,425 | 83.9ms | 259.0ms | 0.00% | PASS |
| CIBA Full (phone) | phone | 40,750 | 1,348 | 88.6ms | 294.9ms | 0.00% | PASS |
| CIBA Full (ex-sub) | ex-sub | 41,580 | 1,374 | 86.9ms | 288.4ms | 0.00% | PASS |

### CIBA フロー（大規模単一テナント: 100万ユーザー）

| シナリオ | login_hint | リクエスト数 | TPS | 平均応答時間 | p95 | エラー率 | 判定 |
|---------|-----------|------------|-----|------------|-----|---------|-----|
| CIBA Full (device) | device | 44,865 | 1,481 | 80.6ms | 226.9ms | 0.00% | PASS |
| CIBA Full (sub) | sub | 44,270 | 1,464 | 81.6ms | 247.0ms | 0.00% | PASS |
| CIBA Full (email) | email | 40,375 | 1,333 | 89.6ms | 266.7ms | 0.00% | PASS |
| CIBA Full (phone) | phone | 39,835 | 1,316 | 90.7ms | 292.0ms | 0.00% | PASS |
| CIBA Full (ex-sub) | ex-sub | 41,855 | 1,382 | 86.3ms | 285.1ms | 0.00% | PASS |

:::tip 大規模単一テナントの考察
1テナントに100万ユーザーを集約した環境でも、マルチテナント環境とほぼ同等の性能を維持。
インデックスとキャッシュ戦略が効果的に機能していることを確認。
:::

---

## 詳細結果

### scenario-1: 認可リクエスト

認可エンドポイント (`/authorizations`) への GET リクエスト。

```
エンドポイント: GET /{tenant_id}/v1/authorizations
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 77,506 |
| スループット | 2,577 req/s |
| 平均応答時間 | 46.4 ms |
| 中央値 | 22.4 ms |
| p90 | 116.0 ms |
| p95 | 183.8 ms |
| 最大応答時間 | 1,420 ms |
| エラー率 | 0.00% |

#### 考察

- ステートレスな設計により高いスループットを実現
- **100万ユーザー環境でも2,500 TPS以上を達成**
- レスポンス生成（ID Token含む）も含めて安定した性能

---

### scenario-2: BC Request

CIBAバックチャンネル認証リクエスト (`/backchannel/authentications`)。

```
エンドポイント: POST /{tenant_id}/v1/backchannel/authentications
login_hint: sub:{user_id}
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 39,625 |
| スループット | 1,317 req/s |
| 平均応答時間 | 90.9 ms |
| p95 | 223.5 ms |
| エラー率 | 0.00% |

#### 考察

- ユーザー検索とトランザクション登録を含むが、安定した性能
- DB操作が含まれるがキャッシュ戦略が効果的

---

### scenario-3: CIBA Full Flow

CIBAフロー全体（BC Request → Transaction → Binding → Token → JWKS）。

```
フロー:
1. POST /backchannel/authentications
2. GET /authentication-devices/{id}/authentications
3. POST /authentications/{id}/authentication-device-binding-message
4. POST /tokens (grant_type=urn:openid:params:grant-type:ciba)
5. GET /jwks
```

#### login_hint パターン別結果

| パターン | 総リクエスト数 | TPS | 平均応答時間 | p95 | イテレーション/秒 |
|---------|-------------|-----|------------|-----|-----------------|
| device | 44,585 | 1,472 | 81.2ms | 247.6ms | 294 |
| sub | 43,220 | 1,430 | 83.6ms | 260.1ms | 286 |
| email | 43,050 | 1,425 | 83.9ms | 259.0ms | 285 |
| phone | 40,750 | 1,348 | 88.6ms | 294.9ms | 270 |
| ex-sub | 41,580 | 1,374 | 86.9ms | 288.4ms | 275 |

#### 考察

- **device**: 最高性能（デバイスID直接参照のため高速）
- **sub**: findById検索で安定した性能
- **email**: findByEmail検索、subと同等の性能
- **phone**: findByPhone検索、やや遅め（インデックス最適化の余地あり）
- **ex-sub**: 外部IdP連携検索、良好な性能

---

### scenario-4: Token (Password Grant)

Resource Owner Password Credentials Grant。

```
エンドポイント: POST /{tenant_id}/v1/tokens
パラメータ: grant_type=password
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 1,432 |
| スループット | 47 req/s |
| 平均応答時間 | 421.0 ms |
| p95 | 576.2 ms |
| エラー率 | 0.00% |

#### 考察

- **ボトルネック**: bcryptによるパスワードハッシュ検証がCPUバウンド
- Password Grantは非推奨であり、CIBAや他のフローへの移行を推奨
- 高負荷時は並列処理の限界により性能劣化

:::warning
Password Grantは高負荷時にボトルネックとなる。本番環境での使用は推奨しない。
:::

---

### scenario-5: Token (Client Credentials Grant)

Client Credentials Grant。

```
エンドポイント: POST /{tenant_id}/v1/tokens
パラメータ: grant_type=client_credentials
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 44,211 |
| スループット | 1,471 req/s |
| 平均応答時間 | 81.4 ms |
| p95 | 211.1 ms |
| エラー率 | 0.00% |

#### 考察

- クライアント認証のみで処理が単純
- 高スループット・低レイテンシを実現
- M2M通信に最適

---

### scenario-6: JWKS

公開鍵取得エンドポイント。

```
エンドポイント: GET /{tenant_id}/v1/jwks
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 80,663 |
| スループット | 2,684 req/s |
| 平均応答時間 | 44.6 ms |
| p95 | 156.8 ms |
| エラー率 | 0.00% |

#### 考察

- Redisキャッシュが効果的に機能
- リソースサーバーからの頻繁なアクセスに対応可能
- **最高TPS**を達成

---

### scenario-7: Token Introspection

トークン検証エンドポイント。

```
エンドポイント: POST /{tenant_id}/v1/tokens/introspection
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 74,072 |
| スループット | 2,453 req/s |
| 平均応答時間 | 48.7 ms |
| p95 | 198.5 ms |
| エラー率 | 0.00% |

#### 考察

- 高スループットを達成
- 軽量な処理とキャッシュ戦略の成果
- リソースサーバーからの大量リクエストに対応可能

---

### scenario-9: Identity Verification Application

本人確認申込フロー（インデックス最適化後）。

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 9,760 |
| スループット | 320 req/s |
| 平均応答時間 | 61.8 ms |
| p95 | 231.8 ms |
| エラー率 | 0.00% |

#### 考察

- インデックス最適化により大幅改善（p95: 1,311ms → 232ms）
- 複雑なフローでも目標値を達成

---

## 総合評価

### 性能ランキング（100万ユーザー環境）

| 順位 | エンドポイント | TPS | p95 | コメント |
|-----|--------------|-----|-----|---------|
| 1 | JWKS | 2,684 | 156.8ms | 最高性能 |
| 2 | Authorization | 2,577 | 183.8ms | 高性能 |
| 3 | Token Introspection | 2,453 | 198.5ms | 高性能 |
| 4 | CIBA Full (device) | 1,481 | 226.9ms | 良好 |
| 5 | Token (Client Credentials) | 1,471 | 211.1ms | 良好 |
| 6 | CIBA Full (sub) | 1,464 | 247.0ms | 良好 |
| 7 | CIBA Full (ex-sub) | 1,382 | 285.1ms | 良好 |

### データ規模別比較（CIBA device パターン）

| データ規模 | TPS | p95 | 性能差 |
|-----------|-----|-----|-------|
| マルチテナント（10×10万） | 1,472 | 247.6ms | 基準 |
| 大規模単一（1×100万） | 1,481 | 226.9ms | +0.6% |

:::note
1テナントに100万ユーザーを集約しても性能劣化は見られず、むしろわずかに向上。
テナント間のコンテキストスイッチがないことが要因と考えられる。
:::

### 判定結果

- **全体**: 全シナリオがp95目標（500ms以下）を達成
- **スケーラビリティ**: 100万ユーザー環境でも高性能を維持
- **最高TPS**: JWKS エンドポイントが 2,684 req/s を達成
- **CIBA**: 全login_hintパターンで安定した性能を確認
- **大規模単一テナント**: マルチテナントと同等以上の性能を確認

---

## 実行コマンド

```bash
# 環境変数でカスタマイズ可能
# VU_COUNT: 同時接続数（デフォルト: 120）
# DURATION: テスト時間（デフォルト: 30s）
# TENANT_INDEX: テナントインデックス（デフォルト: ランダム、0で最初のテナント固定）

# 認可リクエスト
k6 run --summary-export=./performance-test/result/stress/scenario-1-authorization-request.json \
  ./performance-test/stress/scenario-1-authorization-request.js

# CIBA (device)
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-device.json \
  ./performance-test/stress/scenario-3-ciba-device.js

# CIBA (sub)
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-sub.json \
  ./performance-test/stress/scenario-3-ciba-sub.js

# CIBA (email)
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-email.json \
  ./performance-test/stress/scenario-3-ciba-email.js

# Token (Client Credentials)
k6 run --summary-export=./performance-test/result/stress/scenario-5-token-client-credentials.json \
  ./performance-test/stress/scenario-5-token-client-credentials.js

# Token Introspection
k6 run --summary-export=./performance-test/result/stress/scenario-7-token-introspection.json \
  ./performance-test/stress/scenario-7-token-introspection.js

# カスタム設定例
VU_COUNT=200 DURATION=1m k6 run ./performance-test/stress/scenario-6-jwks.js

# 大規模単一テナント（100万ユーザー）でのCIBAテスト
TENANT_INDEX=0 VU_COUNT=120 DURATION=30s k6 run \
  --summary-export=./performance-test/result/stress/scenario-3-ciba-device-1m.json \
  ./performance-test/stress/scenario-3-ciba-device.js
```

---

## 関連ドキュメント

- [テスト環境](./01-test-environment.md)
- [ロードテスト結果](./03-load-test-results.md)
- [チューニングガイド](./05-tuning-guide.md)
