# 05. コードレビューチェックリスト

## このドキュメントの目的

PR（Pull Request）作成前に、**自分でコードをレビュー**するための包括的なチェックリストです。

### 所要時間
⏱️ **約10分**（PR作成前の必須確認）

### 対象
- PR作成者（セルフレビュー）
- レビュアー（レビュー観点）

---

## 全体チェック（必須）

### ✅ Phase 0: 基本確認

- [ ] **コミットメッセージ**: 明確かつ簡潔な説明
- [ ] **Issueリンク**: `Fix #123` または `Closes #123` を含む
- [ ] **変更ファイル数**: 適切なスコープ（1機能 = 1PR）
- [ ] **未完成コード**: `TODO`コメントが残っていない（開発中マーカーのみOK）

### ✅ Phase 1: ビルド・テスト

```bash
# 1. フォーマット修正
./gradlew spotlessApply

# 2. ビルド成功
./gradlew clean build

# 3. テスト成功
./gradlew test

# 4. E2Eテスト成功（該当する場合）
cd e2e && npm test
```

- [ ] `spotlessApply` 実行済み
- [ ] ビルド成功（エラー0件）
- [ ] ユニットテスト全件パス
- [ ] E2Eテスト全件パス（該当機能）

---

## アーキテクチャチェック

### ✅ Phase 2: 層責任の遵守

#### Controller層
- [ ] HTTP ↔ DTO変換のみ（ロジック禁止）
- [ ] EntryService直接呼び出し
- [ ] 例外ハンドリングなし（ExceptionHandlerに委譲）

```java
// ✅ 正しいController
@PostMapping
public ResponseEntity<?> register(@RequestBody ClientRegistrationRequest request) {
    ClientRegistrationResponse response = clientManagementApi.register(request);
    return ResponseEntity.ok(response);
}

// ❌ 間違い: Controllerでロジック
@PostMapping
public ResponseEntity<?> register(@RequestBody ClientRegistrationRequest request) {
    if (request.getClientType().equals("PUBLIC")) {  // ❌ ビジネスロジック
        // ...
    }
}
```

#### UseCase層（EntryService）
- [ ] `@Transaction`アノテーション付与
- [ ] 読み取り専用なら`@Transaction(readOnly = true)`
- [ ] Context Creator使用（DTO → ドメインモデル変換）
- [ ] 権限チェック実装（管理APIの場合）
- [ ] Audit Log記録
- [ ] Dry Run対応（該当する場合）
- [ ] Core層Handler呼び出し（ビジネスロジックはHandlerへ）

```java
// ✅ 正しいEntryService
@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

    @Override
    public ClientManagementResponse create(ClientRegistrationRequest request) {
        // 1. Context Creator使用
        ClientRegistrationContextCreator creator = new ClientRegistrationContextCreator(...);
        ClientRegistrationContext context = creator.create();

        // 2. 権限チェック
        if (!permissions.includesAll(operator.permissionsAsSet())) {
            throw new ForbiddenException(...);
        }

        // 3. Audit Log記録
        auditLogPublisher.publish(auditLog);

        // 4. Dry Runチェック
        if (dryRun) {
            return context.toResponse();
        }

        // 5. Repository呼び出し
        clientConfigurationCommandRepository.register(tenant, context.configuration());

        return context.toResponse();
    }
}
```

#### Core層
- [ ] Handler-Service-Repository パターン遵守
- [ ] Handler: プロトコル処理・オーケストレーション
- [ ] Service: 純粋ビジネスロジック（外部依存なし）
- [ ] Repository: インターフェース定義のみ（実装はAdapter層）

#### Adapter層
- [ ] SQL実行のみ（ビジネスロジック禁止）
- [ ] データベース行 → ドメインモデル変換のみ
- [ ] ⚠️ **重要**: `"ORGANIZER".equals(tenant.type())`のような業務判定禁止

```java
// ✅ 正しいAdapter
public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    String sql = "SELECT * FROM client_configuration WHERE tenant_id = ? AND client_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), clientId.value());
    return ClientConfigurationMapper.map(row);
}

// ❌ 間違い: Adapter層でビジネスロジック
public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    String sql = "SELECT * FROM client_configuration WHERE tenant_id = ? AND client_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), clientId.value());

    // ❌ ビジネスロジック禁止
    if ("ORGANIZER".equals(tenant.type())) {
        // このような判定はCore層で行う
    }

    return ClientConfigurationMapper.map(row);
}
```

---

## 実装パターンチェック

### ✅ Phase 3: Repository パターン

- [ ] **Tenant第一引数**: 全メソッドで`Tenant`が第一引数
  - **例外**: `OrganizationRepository`のみ
- [ ] **Query/Command分離**: 読み取りと書き込みで別インターフェース
- [ ] **命名規則**: `get()`必須存在, `find()`任意存在, `is/has/can`判定メソッド

```java
// ✅ 正しいRepository
public interface ClientConfigurationQueryRepository {
    ClientConfiguration get(Tenant tenant, RequestedClientId clientId);  // 必須存在
    Optional<ClientConfiguration> find(Tenant tenant, RequestedClientId clientId);  // 任意存在
    boolean exists(Tenant tenant, RequestedClientId clientId);  // 判定
}

// ❌ 間違い: Tenantがない
public interface ClientConfigurationQueryRepository {
    ClientConfiguration get(RequestedClientId clientId);  // ❌
}
```

### ✅ Phase 4: Context Creator パターン

- [ ] **定義場所**: `idp-server-control-plane` モジュール
- [ ] **使用場所**: EntryService（UseCase層）
- [ ] **命名**: `{Entity}{Operation}ContextCreator`
- [ ] **責務**: リクエストDTO → ドメインモデル変換のみ

```java
// ✅ 正しい使用
ClientRegistrationContextCreator creator =
    new ClientRegistrationContextCreator(tenant, request, dryRun);
ClientRegistrationContext context = creator.create();

// ❌ 間違い: EntryServiceでDTO直接変換
ClientConfiguration configuration = new ClientConfiguration(
    new RequestedClientId(request.getClientId()),
    // ... 直接変換
);
```

### ✅ Phase 5: Plugin パターン

- [ ] **PluginLoader静的メソッド**: `PluginLoader.loadFromInternalModule(Class)`
- [ ] **インスタンス化禁止**: `new PluginLoader<>(Class)` は使用不可

```java
// ✅ 正しい
Map<GrantType, OAuthTokenCreationService> services =
    PluginLoader.loadFromInternalModule(OAuthTokenCreationService.class);

// ❌ 間違い
PluginLoader<OAuthTokenCreationService> loader =
    new PluginLoader<>(OAuthTokenCreationService.class);  // コンパイルエラー
```

### ✅ Phase 6: JsonConverter パターン

- [ ] **適切なインスタンス選択**:
  - `defaultInstance()`: キャメルケース維持
  - `snakeCaseInstance()`: スネークケース変換

```java
// ✅ DTO変換: スネークケース
JsonConverter converter = JsonConverter.snakeCaseInstance();

// ✅ Cache: キャメルケース
JsonConverter converter = JsonConverter.defaultInstance();
```

---

## コード品質チェック

### ✅ Phase 7: 型安全性

- [ ] **値オブジェクト優先**: `String`/`Map`濫用禁止
- [ ] **型の意味**: `TenantIdentifier`, `ClientId`等の専用型使用
- [ ] **null安全**: `Optional`適切使用

```java
// ✅ 正しい: 値オブジェクト
TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
RequestedClientId clientId = new RequestedClientId(request.getClientId());

// ❌ 間違い: String濫用
String tenantId = request.getTenantId();
String clientId = request.getClientId();
```

### ✅ Phase 8: 例外ハンドリング

- [ ] **適切な例外型**: `{'{Operation}BadRequestException'}`, `ForbiddenException`等
- [ ] **OAuth標準エラーコード**: `invalid_request`, `access_denied`等
- [ ] **throwExceptionIf\{Condition\}()**: パターン使用

```java
// ✅ 正しい例外
if (clientConfiguration == null) {
    throw new ClientNotFoundException(clientId);
}

if (!permissions.includesAll(operator.permissionsAsSet())) {
    throw new ForbiddenException("Permission denied");
}
```

### ✅ Phase 9: Javadoc

- [ ] **RFC準拠明示**: 仕様書章番号・引用
- [ ] **使用例提供**: `<pre>{@code}` でコード例
- [ ] **相互参照**: `@see` による関連クラス・メソッドリンク
- [ ] **全パラメータ**: `@param`/`@return`の意味ある説明

```java
/**
 * 認可コード発行
 *
 * <p>RFC 6749 Section 4.1.2 - Authorization Code Grant
 *
 * @param user リソースオーナー
 * @param authorizationRequest 認可リクエスト
 * @return 認可コード
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2">RFC 6749 Section 4.1.2</a>
 */
public AuthorizationCode issue(User user, AuthorizationRequest authorizationRequest) {
    // ...
}
```

---

## テストチェック

### ✅ Phase 10: ユニットテスト

- [ ] **新規クラス**: テストクラス作成
- [ ] **境界値テスト**: 正常系・異常系両方
- [ ] **モック適切使用**: Repository等の外部依存をモック

### ✅ Phase 11: E2Eテスト

- [ ] **新規API**: E2Eテスト作成（`e2e/spec/`）
- [ ] **正常系テスト**: 期待通りのレスポンス
- [ ] **異常系テスト**: エラーケース（403, 404等）

```javascript
// ✅ 正しいE2Eテスト
describe('Client Management API', () => {
  test('should create client successfully', async () => {
    const response = await axios.post('/v1/management/clients', request);
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty('client_id');
  });

  test('should return 403 when permission denied', async () => {
    try {
      await axios.post('/v1/management/clients', request, { headers: { Authorization: `Bearer ${noPermToken}` } });
      fail('Expected 403 error');
    } catch (error) {
      expect(error.response.status).toBe(403);
    }
  });
});
```

---

## セキュリティチェック

### ✅ Phase 12: 認証・認可

- [ ] **権限チェック**: 管理APIは必須
- [ ] **テナント分離**: Repositoryは必ずTenant第一引数
- [ ] **Audit Log**: 全操作を記録

### ✅ Phase 13: データ検証

- [ ] **Validator使用**: 入力形式チェック
- [ ] **Verifier使用**: ビジネスルール検証
- [ ] **SQLインジェクション対策**: PreparedStatement使用（SqlExecutor使用で自動対策）

---

## パフォーマンスチェック

### ✅ Phase 14: トランザクション

- [ ] **読み取り専用**: `@Transaction(readOnly = true)` 使用
- [ ] **適切なスコープ**: EntryServiceメソッド単位

### ✅ Phase 15: N+1問題

- [ ] **一括取得**: `findAll()`等でまとめて取得
- [ ] **JOIN使用**: 必要に応じてSQL JOINで最適化

---

## ドキュメントチェック

### ✅ Phase 16: ドキュメント更新

- [ ] **API変更**: OpenAPI仕様更新（該当する場合）
- [ ] **README更新**: 新機能説明追加

---

## Codex AI 自動レビュー

### コミットメッセージでレビュー依頼

```bash
git commit -m "実装内容の説明

@codex review

🤖 Generated with [Claude Code](https://claude.ai/code)
Co-Authored-By: Claude <noreply@anthropic.com>"
```

### レビュー観点
- **boolean演算子優先順位**: `&&` が `||` より高い優先順位
- **複雑な条件式**: 混在演算子の正しい評価順序
- **アーキテクチャ準拠**: 層責任・パターン違反の検出
- **コード品質**: 型安全性・例外処理・テストカバレッジ

---

## 最終確認

PR作成前に、以下を最終確認：

### ✅ 必須項目（すべてチェック必須）

- [ ] `./gradlew spotlessApply` 実行済み
- [ ] `./gradlew clean build` 成功
- [ ] `./gradlew test` 全件パス
- [ ] E2Eテスト全件パス（該当機能）
- [ ] Repository第一引数はTenant（OrganizationRepository除く）
- [ ] Context Creator使用（EntryService）
- [ ] Adapter層にビジネスロジックなし
- [ ] Audit Log記録（管理API）
- [ ] Javadoc作成（主要クラス・メソッド）
- [ ] コミットメッセージに`@codex review`含む

### 🚫 絶対禁止項目（1つでも該当したら修正）

- [ ] ❌ Controllerにビジネスロジック
- [ ] ❌ EntryServiceに`@Transaction`なし
- [ ] ❌ Context Creator未使用
- [ ] ❌ Adapter層でビジネスロジック
- [ ] ❌ Repository第一引数にTenantなし
- [ ] ❌ `String`/`Map`濫用（値オブジェクト未使用）
- [ ] ❌ TODOコメント残存（開発中マーカー以外）

---

## 次のステップ

✅ セルフレビュー完了！PR作成の準備が整いました。

### 🔗 PR作成後

1. レビュアーにレビュー依頼
2. Codex AIの自動レビュー結果確認
3. レビューコメント対応
4. 承認後マージ

---

**情報源**: Issue #398, レビューコメント履歴
**最終更新**: 2025-10-12
