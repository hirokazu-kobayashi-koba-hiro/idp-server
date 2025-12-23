# ストレステスト結果

本ドキュメントでは、各エンドポイントに対するストレステストの結果を報告する。

---

## テスト条件

| 項目 | 値 |
|-----|---|
| 同時接続数 (VUs) | 120 |
| テスト時間 | 30秒 |
| テストツール | k6 v1.0.0 |

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
| 認可リクエスト | 43,064 | 1,433 | 83.6ms | 238.4ms | 0.00% | PASS |
| Token (Client Credentials) | 45,896 | 1,527 | 78.5ms | 159.9ms | 0.00% | PASS |
| Token (Password) | 1,432 | 47 | 421.0ms | 576.2ms | 0.00% | FAIL |
| JWKS | 44,315 | 1,476 | 81.2ms | 106.5ms | 0.00% | PASS |
| Token Introspection | 89,909 | 2,994 | 40.0ms | 130.8ms | 0.00% | PASS |

### CIBA フロー

| シナリオ | リクエスト数 | TPS | 平均応答時間 | p95 | エラー率 | 判定 |
|---------|-----------|-----|------------|-----|---------|-----|
| BC Request | 40,722 | 1,355 | 88.4ms | 208.3ms | 0.00% | PASS |
| CIBA Full (device) | 34,635 | 1,135 | 104.9ms | 305.3ms | 0.00% | PASS |
| CIBA Full (sub) | - | - | - | - | - | - |
| CIBA Full (email) | - | - | - | - | - | - |
| CIBA Full (phone) | - | - | - | - | - | - |

### その他

| シナリオ | リクエスト数 | TPS | 平均応答時間 | p95 | エラー率 | 判定 |
|---------|-----------|-----|------------|-----|---------|-----|
| Authentication Device | - | 2,054 | - | 148.4ms | 0.00% | PASS |
| Identity Verification | - | 321 | 61.8ms | 231.8ms | 0.00% | PASS |

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
| 総リクエスト数 | 43,064 |
| スループット | 1,433 req/s |
| 平均応答時間 | 83.6 ms |
| 中央値 | 58.7 ms |
| p90 | 174.3 ms |
| p95 | 238.4 ms |
| 最大応答時間 | 1,501.7 ms |
| エラー率 | 0.00% |

#### 考察

- ステートレスな設計により高いスループットを実現
- レスポンス生成（ID Token含む）も含めて安定した性能
- 1,400 TPS以上を維持し、目標を大幅に上回る

---

### scenario-2: BC Request

CIBAバックチャンネル認証リクエスト (`/backchannel/authentications`)。

```
エンドポイント: POST /{tenant_id}/v1/backchannel/authentications
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 40,722 |
| スループット | 1,355 req/s |
| 平均応答時間 | 88.4 ms |
| p95 | 208.3 ms |
| エラー率 | 0.00% |

#### 考察

- ユーザー検索とトランザクション登録を含むが、安定した性能
- DB操作が含まれるがキャッシュ戦略が効果的

---

### scenario-3: CIBA Full Flow (device)

CIBAフロー全体（BC Request → Transaction → Binding → Token → JWKS）。

```
フロー:
1. POST /backchannel/authentications
2. GET /authentication-devices/{id}/authentications
3. POST /authentications/{id}/authentication-device-binding-message
4. POST /tokens (grant_type=urn:openid:params:grant-type:ciba)
5. GET /jwks
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 34,635 |
| イテレーション数 | 6,927 |
| イテレーション/秒 | 227 |
| 平均応答時間 | 104.9 ms |
| p95 | 305.3 ms |
| イテレーション時間 (avg) | 525.0 ms |
| イテレーション時間 (p95) | 905.3 ms |
| エラー率 | 0.00% |

#### 考察

- 5ステップのフロー全体でp95が1秒以内
- 各ステップの処理が効率的に連携
- 実運用で十分な性能を確保

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
| 総リクエスト数 | 45,896 |
| スループット | 1,527 req/s |
| 平均応答時間 | 78.5 ms |
| p95 | 159.9 ms |
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
| スループット | 1,476 req/s |
| 平均応答時間 | 81.2 ms |
| p95 | 106.5 ms |
| エラー率 | 0.00% |

#### 考察

- Redisキャッシュが効果的に機能
- リソースサーバーからの頻繁なアクセスに対応可能

---

### scenario-7: Token Introspection

トークン検証エンドポイント。

```
エンドポイント: POST /{tenant_id}/v1/tokens/introspection
```

#### 結果

| 指標 | 値 |
|-----|---|
| 総リクエスト数 | 89,909 |
| スループット | 2,994 req/s |
| 平均応答時間 | 40.0 ms |
| p95 | 130.8 ms |
| エラー率 | 0.00% |

#### 考察

- **最高スループット**を達成
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

### 性能ランキング

| 順位 | エンドポイント | TPS | コメント |
|-----|--------------|-----|---------|
| 1 | Token Introspection | 2,994 | 最高性能 |
| 2 | Authentication Device | 2,054 | 高性能 |
| 3 | Token (Client Credentials) | 1,527 | 良好 |
| 4 | JWKS | 1,476 | 良好 |
| 5 | Authorization | 1,433 | 良好 |
| 6 | BC Request | 1,355 | 良好 |
| 7 | CIBA Full Flow | 1,135 | 合格 |
| 8 | Identity Verification | 320 | 許容範囲 |
| 9 | Token (Password) | 47 | 要改善 |

### 判定結果

- **全体**: 9シナリオ中8シナリオがp95目標を達成
- **ボトルネック**: Password Grant のみ性能要件未達
- **推奨**: Password Grant は CIBA または Authorization Code Flow への移行を推奨

---

## 実行コマンド

```bash
# 認可リクエスト
k6 run --summary-export=./performance-test/result/stress/scenario-1-authorization-request.json \
  ./performance-test/stress/scenario-1-authorization-request.js

# CIBA (device)
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-device.json \
  ./performance-test/stress/scenario-3-ciba-device.js

# Token (Client Credentials)
k6 run --summary-export=./performance-test/result/stress/scenario-5-token-client-credentials.json \
  ./performance-test/stress/scenario-5-token-client-credentials.js

# Token Introspection
k6 run --summary-export=./performance-test/result/stress/scenario-7-token-introspection.json \
  ./performance-test/stress/scenario-7-token-introspection.js
```

---

## 関連ドキュメント

- [テスト環境](./01-test-environment.md)
- [ロードテスト結果](./03-load-test-results.md)
- [チューニングガイド](./05-tuning-guide.md)
