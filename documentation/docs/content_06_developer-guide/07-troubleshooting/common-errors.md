# 04. トラブルシューティング

## このドキュメントの目的

開発中によく遭遇するエラーと、その**即座解決方法**を提供します。

### 所要時間
⏱️ **約15分**（参照用ドキュメント - 必要時に該当箇所を参照）

---

## エラーカテゴリ

| カテゴリ | 説明 |
|---------|------|
| [ビルドエラー](#ビルドエラー) | コンパイル失敗・依存関係エラー |
| [実行時エラー](#実行時エラー) | NullPointerException・ClassCastException等 |
| [データベースエラー](#データベースエラー) | SQL実行失敗・トランザクションエラー |
| [認証・認可エラー](#認証認可エラー) | 403 Forbidden・401 Unauthorized |
| [テストエラー](#テストエラー) | E2Eテスト失敗 |

---

## ビルドエラー

### エラー1: `spotlessCheck` 失敗

**エラーメッセージ**:
```
> Task :libs:idp-server-use-cases:spotlessJavaCheck FAILED
The following files had format violations:
  libs/idp-server-use-cases/src/main/java/...
```

**原因**: コードフォーマットが規約に準拠していない

**解決策**:
```bash
./gradlew spotlessApply
```

**予防策**: コミット前に必ず実行

---

### エラー2: `Cannot resolve symbol`

**エラーメッセージ**:
```java
error: cannot find symbol
  symbol:   class TenantQueryRepository
  location: package org.idp.server.platform.multi_tenancy.tenant
```

**原因**: 依存モジュールがbuild.gradleに追加されていない

**解決策**:
```groovy
// libs/idp-server-use-cases/build.gradle
dependencies {
    implementation project(':libs:idp-server-platform')
    implementation project(':libs:idp-server-core')
    implementation project(':libs:idp-server-control-plane')
}
```

**確認コマンド**:
```bash
./gradlew :libs:idp-server-use-cases:dependencies
```

---

### エラー3: Circular Dependency（循環依存）

**エラーメッセージ**:
```
Circular dependency between the following tasks:
:libs:idp-server-core:compileJava
:libs:idp-server-use-cases:compileJava
```

**原因**: モジュール間の依存関係が循環している

**解決策**: アーキテクチャの依存方向を修正

```
✅ 正しい依存方向:
Controller → UseCase → Core → Adapter

❌ 間違い（循環依存）:
Core → UseCase → Core
```

**修正方法**: 共通コードを`idp-server-platform`に移動

---

## 実行時エラー

### エラー4: `NullPointerException in AuditLogPublisher`

**エラーメッセージ**:
```
java.lang.NullPointerException: Cannot invoke "RequestAttributes.toMap()" because "requestAttributes" is null
  at AuditLogCreator.create(AuditLogCreator.java:45)
```

**原因**: `RequestAttributes`がControllerから渡されていない

**解決策**: Controllerで`@RequestAttribute`を追加

```java
@GetMapping("/{tenantId}")
public ResponseEntity<?> get(
        @PathVariable("tenantId") String tenantId,
        @AuthenticationPrincipal User operator,
        @RequestAttribute OAuthToken oAuthToken,
        @RequestAttribute RequestAttributes requestAttributes) {  // ✅ 追加
    // ...
}
```

---

### エラー5: `TenantNotFoundException`

**エラーメッセージ**:
```
org.idp.server.platform.multi_tenancy.tenant.TenantNotFoundException:
  Tenant not found: 18ffff8d-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

**原因**: 存在しないテナントIDを指定している

**解決策**:

#### 開発環境
```bash
# テナント作成
curl -X POST http://localhost:8080/v1/management/tenants \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-tenant",
    "display_name": "Test Tenant"
  }'
```

#### E2Eテスト
```javascript
beforeAll(async () => {
  // テナント作成
  const tenantResponse = await axios.post(
    'http://localhost:8080/v1/management/tenants',
    { name: 'test-tenant', display_name: 'Test Tenant' },
    { headers: { Authorization: `Bearer ${adminToken}` } }
  );
  tenantId = tenantResponse.data.tenant_id;
});
```

---

### エラー6: `ClassCastException: Map cannot be cast to String`

**エラーメッセージ**:
```
java.lang.ClassCastException: class java.util.HashMap cannot be cast to class java.lang.String
  at ClientConfigurationMapper.map(ClientConfigurationMapper.java:23)
```

**原因**: JSONB列のデータ型誤り

**解決策**: JsonConverterを使用

```java
// ❌ 間違い: 直接キャスト
String metadata = (String) row.get("metadata");

// ✅ 正しい: JsonConverter使用
JsonConverter converter = JsonConverter.snakeCaseInstance();
Map<String, Object> metadata = converter.read((String) row.get("metadata"));
```

---

## データベースエラー

### エラー7: `PSQLException: relation "xxx" does not exist`

**エラーメッセージ**:
```
org.postgresql.util.PSQLException: ERROR: relation "client_configuration" does not exist
```

**原因**: データベースマイグレーションが未実行

**解決策**:
```bash
# Flywayマイグレーション実行
./gradlew flywayMigrate

# または、アプリケーション起動時に自動実行される
./gradlew bootRun
```

**確認コマンド**:
```bash
# 適用済みマイグレーション確認
./gradlew flywayInfo
```

---

### エラー8: `TransactionRequiredException`

**エラーメッセージ**:
```
org.idp.server.platform.datasource.TransactionRequiredException:
  Transaction is required for this operation
```

**原因**: EntryServiceに`@Transaction`アノテーションがない

**解決策**:
```java
// ❌ 間違い: @Transactionなし
public class ClientManagementEntryService implements ClientManagementApi {
    // ...
}

// ✅ 正しい: @Transaction付与
@Transaction
public class ClientManagementEntryService implements ClientManagementApi {
    // ...
}
```

---

### エラー9: Row Level Security (RLS) エラー

**エラーメッセージ**:
```
org.postgresql.util.PSQLException: ERROR: new row violates row-level security policy for table "client_configuration"
```

**原因**: `app.tenant_id`が設定されていない状態でINSERT/UPDATE

**解決策**: `TransactionManager.setTenantId()`を使用

```java
// ✅ 正しい: Repository実装でTenant設定
@Override
public void register(Tenant tenant, ClientConfiguration configuration) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = "INSERT INTO client_configuration (tenant_id, client_id, ...) VALUES (?, ?, ...)";
    sqlExecutor.execute(sql, tenant.identifier().value(), configuration.clientId().value(), ...);
}
```

---

## 認証・認可エラー

### エラー10: `403 Forbidden` - Permission Denied

**エラーメッセージ**:
```json
{
  "error": "access_denied",
  "error_description": "permission denied required permission [tenant:write], but [tenant:read]"
}
```

**原因**: 必要な権限がないトークンでAPIを呼び出している

**解決策**:

#### トークン取得時にスコープ指定
```bash
curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=admin-client" \
  -d "client_secret=admin-secret" \
  -d "scope=tenant:read tenant:write"  # ✅ 必要なスコープ
```

#### E2Eテスト
```javascript
const tokenResponse = await axios.post('http://localhost:8080/oauth/token', {
  grant_type: 'client_credentials',
  client_id: 'admin-client',
  client_secret: 'admin-secret',
  scope: 'tenant:write'  // ✅ 必要なスコープ
});
```

---

### エラー11: `401 Unauthorized` - Invalid Token

**エラーメッセージ**:
```json
{
  "error": "invalid_token",
  "error_description": "The access token provided is expired, revoked, malformed, or invalid"
}
```

**原因**: トークンが期限切れ・不正

**解決策**:

#### トークン再取得
```bash
# 新しいトークン取得
TOKEN=$(curl -X POST http://localhost:8080/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=admin-client" \
  -d "client_secret=admin-secret" \
  -d "scope=tenant:read" \
  | jq -r '.access_token')
```

#### トークン有効期限確認
```bash
# JWTデコード（https://jwt.io/ で確認）
echo $TOKEN | cut -d. -f2 | base64 -d | jq .
```

---

## テストエラー

### エラー12: E2Eテスト `ECONNREFUSED`

**エラーメッセージ**:
```
Error: connect ECONNREFUSED 127.0.0.1:8080
```

**原因**: idp-serverが起動していない

**解決策**:
```bash
# 別ターミナルでサーバー起動
./gradlew bootRun

# 起動確認
curl http://localhost:8080/health
```

---

### エラー13: E2Eテスト `Timeout`

**エラーメッセージ**:
```
Error: Timeout of 5000ms exceeded. For async tests and hooks, ensure "done()" is called
```

**原因**: API応答が遅い・処理が完了しない

**解決策**:

#### タイムアウト延長
```javascript
test('should create client', async () => {
  // タイムアウト延長
  jest.setTimeout(30000);  // 30秒

  const response = await axios.post(...);
  expect(response.status).toBe(200);
});
```

#### ログ確認
```bash
# アプリケーションログで原因調査
tail -f logs/application.log
```

---

## よくあるアンチパターン

### ❌ パターン1: Repository第一引数にTenantなし

```java
// ❌ 間違い
clientRepository.get(clientId);

// ✅ 正しい
clientRepository.get(tenant, clientId);
```

**エラー**: コンパイルエラー（シグネチャ不一致）

---

### ❌ パターン2: Context Creator未使用

```java
// ❌ 間違い: EntryServiceでDTO直接変換
ClientConfiguration configuration = new ClientConfiguration(
    new RequestedClientId(request.getClientId()),
    // ... 直接変換
);

// ✅ 正しい: Context Creator使用
ClientRegistrationContextCreator creator =
    new ClientRegistrationContextCreator(tenant, request, dryRun);
ClientRegistrationContext context = creator.create();
```

**エラー**: レビューで指摘される（アーキテクチャ違反）

---

### ❌ パターン3: Adapter層でビジネスロジック

```java
// ❌ 間違い
public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    String sql = "SELECT * FROM client_configuration WHERE tenant_id = ? AND client_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), clientId.value());

    // ❌ ビジネスロジック禁止
    if ("ORGANIZER".equals(tenant.type())) {
        // ...
    }

    return ClientConfigurationMapper.map(row);
}

// ✅ 正しい: Adapter層はSQLのみ
public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    String sql = "SELECT * FROM client_configuration WHERE tenant_id = ? AND client_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), clientId.value());
    return ClientConfigurationMapper.map(row);
}
```

**エラー**: レビューで指摘される（レイヤー責任違反）

---

## デバッグツール

### ログレベル変更

```properties
# application.properties
logging.level.org.idp.server=DEBUG
logging.level.org.springframework.jdbc=DEBUG
```

### SQLログ出力

```properties
# application.properties
logging.level.org.idp.server.platform.datasource.SqlExecutor=DEBUG
```

### トランザクションログ

```properties
# application.properties
logging.level.org.springframework.transaction=DEBUG
```

---

## 緊急時のチェックリスト

問題が解決しない場合、以下を順番に確認：

- [ ] `./gradlew spotlessApply` 実行済み
- [ ] `./gradlew clean build` 成功
- [ ] データベースマイグレーション済み（`flywayInfo`で確認）
- [ ] サーバー起動成功（`http://localhost:8080/health` で確認）
- [ ] トークン有効期限内（JWTデコードで確認）
- [ ] 必要な権限を持つトークン（スコープ確認）
- [ ] ログファイル確認（`logs/application.log`）

---

## 次のステップ

✅ よくあるエラーの解決方法を理解した！

### 📖 次に読むべきドキュメント

1. [05. コードレビューチェックリスト](./05-code-review-checklist.md) - PR前の確認項目

### 🔗 詳細情報

- [Error Handling詳細](./error-handling/) - エラー設計の詳細

---

**情報源**: 実装経験・Issue修正履歴・レビューコメント
**最終更新**: 2025-10-12
