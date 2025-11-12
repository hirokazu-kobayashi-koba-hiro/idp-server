# Identity Verification Error Handling Tests

## 概要

このドキュメントでは、身元確認API（Identity Verification Application API）の401/404エラーハンドリングのテストケースについて説明します。

**テストファイル**: `integration-05-identity-verification-error-handling.test.js`

---

## テスト目的

以下のHTTPステータスコードが、身元確認API特有のシナリオで正しく返却されることを確認します：

- **401 Unauthorized** - アクセストークンの認証エラー
- **404 Not Found** - リソースが存在しない、または他のユーザーのリソースへのアクセス

---

## テストケース一覧

### 401 Unauthorized Tests (6テストケース)

#### 1. 初回申込みAPI - アクセストークンなし
```javascript
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/apply
// Authorization ヘッダーなし
```

**期待結果:**
```json
{
  "error": "invalid_token",
  "error_description": "The access token is invalid or expired"
}
```

**HTTPステータス:** `401`

---

#### 2. 初回申込みAPI - 無効なアクセストークン
```javascript
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/apply
Authorization: Bearer invalid-token-xyz123
```

**期待結果:**
```json
{
  "error": "invalid_token",
  "error_description": "The access token is invalid or expired"
}
```

**HTTPステータス:** `401`

---

#### 3. 後続プロセスAPI - アクセストークンなし
```javascript
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/{id}/{process}
// Authorization ヘッダーなし
```

**期待結果:** 401エラー

**重要性:** 後続プロセス（eKYC完了、審査要求等）でも認証が必須であることを確認

---

#### 4. 申込み一覧取得API - アクセストークンなし
```javascript
GET /{tenant-id}/v1/me/identity-verification/applications
// Authorization ヘッダーなし
```

**期待結果:** 401エラー

**重要性:** 読み取り専用APIでも認証が必須であることを確認

---

#### 5. 申込み削除API - アクセストークンなし
```javascript
DELETE /{tenant-id}/v1/me/identity-verification/applications/{type}/{id}
// Authorization ヘッダーなし
```

**期待結果:** 401エラー

**重要性:** 削除操作で認証が必須であることを確認

---

#### 6. 検証結果取得API - アクセストークンなし
```javascript
GET /{tenant-id}/v1/me/identity-verification/results
// Authorization ヘッダーなし
```

**期待結果:** 401エラー

**重要性:** verified_claims取得でも認証が必須であることを確認

---

### 404 Not Found Tests (7テストケース)

#### 1. 存在しない申込みIDで後続プロセス実行
```javascript
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/00000000-0000-0000-0000-000000000000/complete
Authorization: Bearer {valid_access_token}
```

**期待結果:**
```json
{
  "error": "not_found",
  "error_description": "Application not found or does not belong to this user"
}
```

**HTTPステータス:** `404`

**重要性:** 存在しないリソースへのアクセスが適切にエラーになることを確認

---

#### 2. 無効なUUID形式で後続プロセス実行
```javascript
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/not-a-valid-uuid/complete
Authorization: Bearer {valid_access_token}
```

**期待結果:** `400` または `404` エラー

**重要性:** 入力検証が適切に機能することを確認

---

#### 3. 存在しない申込みIDで削除実行
```javascript
DELETE /{tenant-id}/v1/me/identity-verification/applications/{type}/00000000-0000-0000-0000-000000000000
Authorization: Bearer {valid_access_token}
```

**期待結果:** `404` エラー

**重要性:** 削除対象が存在しない場合のエラーハンドリングを確認

---

#### 4. 他のユーザーの申込みへアクセス（最重要テスト）
```javascript
// ユーザーA: 申込み作成
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/apply
Authorization: Bearer {user_a_token}
// => application_id: xxx

// ユーザーB: ユーザーAの申込みにアクセス試行
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/xxx/complete
Authorization: Bearer {user_b_token}
```

**期待結果:**
```json
{
  "error": "not_found",
  "error_description": "Application not found or does not belong to this user"
}
```

**HTTPステータス:** `404`

**重要性:**
- **セキュリティ上最も重要なテスト**
- マルチテナンシーの分離が正しく機能していることを確認
- 他のユーザーのデータへの不正アクセスを防止

**実装詳細:**
- 403（Forbidden）ではなく404を返すことで、リソースの存在を隠蔽
- 情報漏洩対策として重要

---

#### 5. 存在しない申込みIDでフィルタリング（正常系）
```javascript
GET /{tenant-id}/v1/me/identity-verification/applications?id=00000000-0000-0000-0000-000000000000
Authorization: Bearer {valid_access_token}
```

**期待結果:**
```json
{
  "list": [],
  "total_count": 0,
  "limit": 20,
  "offset": 0
}
```

**HTTPステータス:** `200`（404ではない）

**重要性:** リストAPIは空のリストを返すべき（404ではない）

---

#### 6. 存在しない検証結果IDでフィルタリング（正常系）
```javascript
GET /{tenant-id}/v1/me/identity-verification/results?id=00000000-0000-0000-0000-000000000000
Authorization: Bearer {valid_access_token}
```

**期待結果:** 空のリスト（`200 OK`）

**重要性:** 検証結果の取得でもリストAPIの挙動を確認

---

#### 7. 401と404の優先順位テスト
```javascript
POST /{tenant-id}/v1/me/identity-verification/applications/{type}/00000000-0000-0000-0000-000000000000/complete
// Authorization ヘッダーなし（401の条件）
// 存在しない申込みID（404の条件）
```

**期待結果:** `401` エラー（404ではない）

**重要性:**
- 認証エラー（401）が認可エラー（404）より優先されることを確認
- セキュリティのベストプラクティス（認証前にリソースの存在を確認しない）

---

## テスト実行方法

### 単体実行
```bash
cd e2e
npm test -- integration-05-identity-verification-error-handling.test.js
```

### 統合テストスイート実行
```bash
cd e2e
npm test -- --testPathPattern=integration/ida
```

---

## 期待される動作

### 401 Unauthorized
- アクセストークンが欠落している場合
- アクセストークンが無効な場合
- アクセストークンの有効期限が切れている場合

### 404 Not Found
- 指定された申込みIDが存在しない場合
- 申込みが他のユーザーに属している場合（セキュリティ上403ではなく404を返す）
- リソースが削除済みの場合

### 200 OK（空のリスト）
- リスト取得APIで該当データが見つからない場合
- フィルタリング条件に一致するデータがない場合

---

## セキュリティ考慮事項

### 1. 情報漏洩の防止
他のユーザーの申込みにアクセスした場合、`403 Forbidden`ではなく`404 Not Found`を返すことで、申込みの存在を隠蔽します。

**理由:**
- 403を返すと「リソースは存在するが、アクセス権限がない」ことが分かる
- 404を返すことで「リソースが存在しないか、アクセス権限がない」ことが分かり、情報漏洩を防止

### 2. 認証と認可の順序
認証（401）は認可（404）より先にチェックされるべきです。

**理由:**
- 認証されていないユーザーに対してリソースの存在情報を漏らさない
- セキュリティのベストプラクティス

### 3. マルチテナント分離
テナント間、ユーザー間でのデータ分離が正しく機能することを確認します。

---

## トラブルシューティング

### テストが失敗する場合

#### 401エラーが期待通り返らない
- アクセストークン検証ミドルウェアが正しく設定されているか確認
- エンドポイントが認証を必須としているか確認

#### 404エラーが期待通り返らない
- リポジトリ層でユーザーIDによるフィルタリングが実装されているか確認
- 申込みが他のユーザーに属している場合の処理を確認

#### 403が返される場合
- 404を返すべき箇所で403を返している可能性
- セキュリティ上の問題があるため修正が必要

---

## 関連ドキュメント

- [API仕様 - リソースオーナー用API](../../../documentation/docs/content_07_reference/api-reference/api-resource-owner-ja.md)
- [身元確認申込みガイド](../../../documentation/docs/content_05_how-to/how-to-16-identity-verification-application.md)
- [マルチテナント分離テスト](../../security/multi_tenant_isolation.test.js)

---

## テストカバレッジ

このテストにより、以下のエラーハンドリングがカバーされます：

| エンドポイント | 401 | 404 | 備考 |
|--------------|-----|-----|------|
| POST /applications/{type}/{process} | ✅ | ✅ | 初回申込み |
| POST /applications/{type}/{id}/{process} | ✅ | ✅ | 後続プロセス |
| GET /applications | ✅ | N/A | 一覧取得（404は該当なし） |
| DELETE /applications/{type}/{id} | ✅ | ✅ | 削除 |
| GET /results | ✅ | N/A | 検証結果取得 |

---

## まとめ

このテストスイートにより、身元確認APIの基本的なエラーハンドリング（401/404）が正しく機能することを確認できます。

特に**他のユーザーの申込みへのアクセス**テストは、マルチテナント環境でのセキュリティ確保において最も重要なテストです。
