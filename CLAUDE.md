# Claude Code Context - idp-server

## プロジェクト概要
- **種類**: 身元確認特化エンタープライズ・アイデンティティプラットフォーム
- **言語**: Java 21+ (Spring Boot), Gradle
- **特徴**: Hexagonal Architecture + DDD, マルチテナント, OAuth 2.0/OIDC/CIBA/FAPI準拠

## アーキテクチャ
```
Controller → UseCase (EntryService) → Core (Handler-Service-Repository) → Adapter
             ↑ control-plane APIs (契約定義のみ)
```

### 主要モジュール
- `idp-server-core` - OIDC準拠コアエンジン
- `idp-server-platform` - プラットフォーム基盤
- `idp-server-use-cases` - EntryService実装
- `idp-server-control-plane` - 管理API契約定義
- `e2e/` - 3層テスト (spec/scenario/monkey)

**📚 AI開発者向け詳細ドキュメント**: [全20モジュール詳解](documentation/docs/content_10_ai_developer/ai-01-index.md)
- 100+クラスの詳細説明・実装パターン・アンチパターン
- 実装コード引用・RFC準拠明示
- Issue #676対応（実装ガイド精度95%+達成）

## 開発コマンド
```bash
./gradlew spotlessApply  # 必須: フォーマット修正
./gradlew build && ./gradlew test
cd e2e && npm test
```

## コードレビュー（Codex AI）
**自動レビュー機能**: コミットメッセージに `@codex review` を含めることで、AIによる自動コードレビューが実行される

### コミットメッセージ例
```bash
git commit -m "実装内容の説明

@codex review

🤖 Generated with [Claude Code](https://claude.ai/code)
Co-Authored-By: Claude <noreply@anthropic.com>"
```

### レビュー観点
- **boolean演算子優先順位**: `&&` が `||` より高い優先順位であることを確認
- **複雑な条件式**: 混在演算子の正しい評価順序をチェック
- **アーキテクチャ準拠**: 層責任・パターン違反の検出
- **コード品質**: 型安全性・例外処理・テストカバレッジ

### コメント修正依頼
```bash
# 未解決のレビューコメントを修正する場合
@codex fix comments
```

## 設計原則（OIDC世界観の尊重）
- **プロトコル準拠**: OAuth 2.0/OIDC仕様への厳密準拠、標準逸脱禁止
- **型安全性**: `String`/`Map`濫用禁止、意味のある値オブジェクト優先
- **責務分離**: Handler-Service-Repository パターン
- **マルチテナント**: TenantIdentifier/OrganizationIdentifier による完全分離



## 4層アーキテクチャ詳細

1. **Controller層**: HTTP ↔ DTO変換のみ（ロジック禁止）
   - 📖 [詳細: adapters.md - Spring Boot統合](documentation/docs/content_10_ai_developer/adapters.md#idp-server-springboot-adapter---spring-boot統合)

2. **UseCase層**: `{Domain}{Action}EntryService` - オーケストレーション専用
   - 📖 [詳細: use-cases.md - EntryService 10フェーズ](documentation/docs/content_10_ai_developer/ai-10-use-cases.md#entryserviceの10フェーズ)

3. **Core層**: Handler-Service-Repository - OIDC仕様準拠・ドメインロジック
   - 📖 [詳細: core.md - 全9ドメイン](documentation/docs/content_10_ai_developer/ai-11-core.md#主要ドメイン)

4. **Adapter層**: Repository - 永続化カプセル化（ドメインロジック禁止）
   - 📖 [詳細: adapters.md - DataSource-SqlExecutor](documentation/docs/content_10_ai_developer/adapters.md#datasource---sqlexecutor-パターン)

## Handler-Service-Repository パターン
- **Handler**: `{Domain}{Action}Handler` - プロトコル処理・オーケストレーション
- **Service**: `{Domain}{Action}Service` - 純粋ビジネスロジック
- **Repository**: `{Entity}QueryRepository`/`{Entity}CommandRepository` - データアクセス抽象化

📖 [詳細実装パターン: core.md](documentation/docs/content_10_ai_developer/ai-11-core.md#handler-service-repository-パターン)

## 検証・エラーハンドリング

### Result-Exception Hybrid パターン
**原則**: Validator/Verifierは例外をthrow、Handlerで catch して Result に変換、EntryServiceでHTTPレスポンスに変換

```java
// ❌ 誤り: ResultオブジェクトをService層で返す
public TenantRequestValidationResult validate() { ... }

// ✅ 正解: void でthrowする（UserManagementパターン）
public void validate() {
  if (!result.isValid()) {
    throw new InvalidRequestException("...", errors);
  }
}
```

### エラーハンドリングフロー（3層）

#### 1. Service層: Validator/Verifierが例外をthrow
```java
// TenantCreationService.execute()
new TenantRequestValidator(request, dryRun).validate(); // throws InvalidRequestException
tenantManagementVerifier.verify(context);               // throws InvalidRequestException
```

#### 2. Handler層: ManagementApiExceptionをcatchしてResultに変換
```java
// TenantManagementHandler.handle()
try {
  return executeService(...);
} catch (ManagementApiException e) {
  return TenantManagementResult.error(tenant, e); // Result化
}
```

#### 3. EntryService層: ResultをHTTPレスポンスに変換
```java
// TenantManagementEntryService.create()
TenantManagementResult result = handler.handle(...);

if (result.hasException()) {
  AuditLog auditLog = AuditLogCreator.createOnError(...);
  auditLogPublisher.publish(auditLog);
  return result.toResponse(dryRun); // 400/403/404等の適切なHTTPステータス
}
```

### Validator/Verifier実装パターン

#### Validator: 入力形式チェック
```java
public class TenantRequestValidator {
  public void validate() {
    // スキーマ検証
    JsonSchemaValidationResult result = validator.validate(json);
    throwExceptionIfInvalid(result);
  }

  void throwExceptionIfInvalid(JsonSchemaValidationResult result) {
    if (!result.isValid()) {
      throw new InvalidRequestException("...", result.errors());
    }
  }
}
```

#### Verifier: ビジネスルール検証
```java
public class TenantManagementVerifier {
  public void verify(Context context) {
    VerificationResult result = tenantVerifier.verify(context.newTenant());
    throwExceptionIfInvalid(result);
  }

  void throwExceptionIfInvalid(VerificationResult result) {
    if (!result.isValid()) {
      throw new InvalidRequestException("...", result.errors());
    }
  }
}
```

### 例外-HTTPステータスマッピング
```java
// TenantManagementResult.toResponse()
private TenantManagementStatus mapExceptionToStatus(ManagementApiException e) {
  if (e instanceof InvalidRequestException) {
      return TenantManagementStatus.INVALID_REQUEST;      // 400
  } else if (exception instanceof OrganizationAccessDeniedException) {
      return TenantManagementStatus.FORBIDDEN; // 403
  } else if (e instanceof PermissionDeniedException) {
    return TenantManagementStatus.FORBIDDEN;            // 403
  } else if (e instanceof ResourceNotFoundException) {
    return TenantManagementStatus.NOT_FOUND;            // 404
  }
  return TenantManagementStatus.SERVER_ERROR;           // 500
}
```

### 重要な設計判断
- **throwしない返り値パターンは廃止**: `TenantRequestValidationResult`、`TenantManagementVerificationResult`のような返り値型は使用しない
- **UserManagement統一**: `UserRegistrationRequestValidator`、`UserRegistrationVerifier`と同じパターンに統一
- **例外の利点**: トランザクションロールバック、AuditLog記録、適切なHTTPステータス返却が一貫して可能

## 重要な実装パターン

### Repository パターン
- **Tenant第一引数**: 全メソッドで `Tenant` が第一引数（マルチテナント分離）
  - **例外**: `OrganizationRepository`のみ（組織はテナントより上位概念）
- **Query/Command分離**: `{Entity}QueryRepository` / `{Entity}CommandRepository`
- **命名規則**: `get()`必須存在, `find()`任意存在, `is/has/can`判定メソッド
- 📖 [詳細: core.md - Repository](documentation/docs/content_10_ai_developer/ai-11-core.md#3-repository---データアクセス抽象化)

### Plugin 拡張パターン
- **Map<Type, Service>**: `Map<GrantType, OAuthTokenCreationService>` で動的選択
- **PluginLoader**: **静的メソッドAPI** - `PluginLoader.loadFromInternalModule(Class)`
  - ❌ **誤り**: `new PluginLoader<>(Class)` はインスタンス化不可
  - ✅ **正解**: `PluginLoader.loadFromInternalModule(Class)` 静的メソッド
- **Factory パターン**: `{Feature}Factory` → `{Feature}` 生成
- 📖 [詳細: platform.md - Plugin System](documentation/docs/content_10_ai_developer/ai-12-platform.md#plugin-system)

### Context Creator パターン
- **定義場所**: `idp-server-control-plane` モジュール
- **使用場所**: `idp-server-use-cases` モジュール（EntryService）
- **責務**: リクエストDTO → ドメインモデル変換
- **命名**: `{Entity}{Operation}ContextCreator` → `{Entity}{Operation}Context`
- 📖 [詳細: control-plane.md - Context Creator](documentation/docs/content_10_ai_developer/ai-13-control-plane.md#context-creator-パターン)

### JsonConverter パターン
- **defaultInstance()**: キャメルケース維持（`clientId`）
- **snakeCaseInstance()**: スネークケース変換（`client_id`）
- **用途**: Context Creator, Repository（JSONB列）, Cache, HTTP通信
- 📖 [詳細: platform.md - JsonConverter](documentation/docs/content_10_ai_developer/ai-12-platform.md#json-シリアライズ・デシリアライズ)

### TenantAttributes パターン
- **optValueAsBoolean(key, default)**: デフォルト値付きOptional取得
- **optValueAsString(key, default)**: 文字列取得
- 📖 [詳細: platform.md - TenantAttributes](documentation/docs/content_10_ai_developer/ai-12-platform.md#tenantattributes---テナント固有設定)

## 🚨 アンチパターン（絶対禁止）
- **Util濫用**: 共通ロジックをUtilに逃がす
- **Map濫用**: `Map<String, Object>` ではなく専用クラス使用
- **DTO肥大化**: DTOにドメインロジック含有禁止
- **永続化層ロジック**: データソース層でのビジネスロジック実行禁止

## ⚠️ レイヤー責任違反の重要教訓
**データソース層でのビジネスロジック実行は絶対禁止**
- ❌ データソース層で`"ORGANIZER".equals(tenant.type())`のような業務判定
- ✅ ドメインオブジェクトに業務ロジック配置し、データソース層は委譲のみ
- **原則**: データソース層=SELECT/INSERT/UPDATE/DELETE、ドメイン層=業務ルール

## 組織レベルAPI設計
**Control-Plane**: 契約定義層（`idp-server-control-plane`）
**実装**: EntryService（`idp-server-use-cases`）

**システムレベル vs 組織レベル**:
```java
// システムレベル: method(TenantIdentifier, User, ...)
// 組織レベル: method(OrganizationIdentifier, TenantIdentifier, User, ...)
```

**組織アクセス制御フロー**:
1. 組織メンバーシップ検証
2. テナントアクセス検証
3. 組織-テナント関係検証
4. 権限検証

### 🚨 組織レベルAPI特有の重要パターン

#### Organization渡しパターン（NullPointerException回避）

**問題**: 組織レベルAPIでは `operator.currentOrganizationIdentifier()` が null を返す

```java
// ❌ 誤り: システムレベルと同じServiceを共有
public class TenantCreationService {
  public TenantManagementResult execute(...) {
    // システムレベルでは動作するが、組織レベルではNPE
    OrganizationIdentifier orgId = operator.currentOrganizationIdentifier(); // null!
    Organization org = organizationRepository.get(orgId); // NPE!
  }
}
```

**理由**: 組織レベルAPIでは、Organizationは既にHandlerで取得済み（URLパスパラメータから）

```java
// OrgTenantManagementHandler.handle()
Organization organization = organizationRepository.get(organizationIdentifier); // 既に取得済み
```

**解決策**: 専用Request Wrapperで明示的にOrganizationを渡す

```java
// ✅ 正解: 組織レベル専用のRequest Wrapper
public record OrgTenantCreationRequest(
  Organization organization,  // Handlerで取得済みのOrganizationを渡す
  TenantRequest tenantRequest
) {}

// ✅ 正解: 組織レベル専用のService
public class OrgTenantCreationService
    implements TenantManagementService<OrgTenantCreationRequest> {

  public TenantManagementResult execute(..., OrgTenantCreationRequest request, ...) {
    Organization organization = request.organization(); // Wrapperから取得
    // operator.currentOrganizationIdentifier()は使わない
  }
}

// Handler側: WrapperでOrganizationを渡す
if ("create".equals(method)) {
  serviceRequest = new OrgTenantCreationRequest(organization, request);
}
```

#### パターン適用判断

| 操作 | システムレベル | 組織レベル | 判断基準 |
|------|--------------|-----------|---------|
| `create` | `TenantCreationService` | `OrgTenantCreationService` + `OrgTenantCreationRequest` | Organizationが必要 |
| `findList` | `TenantFindListService` | `OrgTenantFindListService` | 取得対象テナント範囲が異なる |
| `get` | `TenantFindService` | `TenantFindService` (共有) | テナント単体取得のみ |
| `update` | `TenantUpdateService` | `TenantUpdateService` (共有) | `TenantUpdateRequest`で十分 |
| `delete` | `TenantDeletionService` | `TenantDeletionService` (共有) | テナント単体削除のみ |

**原則**: Organizationオブジェクトが必要な操作、またはスコープが異なる操作のみ専用Serviceを作成


## 🎯 AI開発者向け重要情報

### 必須理解事項
- **設計思想**: 「OIDC世界観の尊重」= RFC厳密準拠、標準逸脱禁止
- **アーキテクチャ**: Control-Plane=契約定義、UseCase=実装、Core=ドメインロジック
- **型安全性**: 値オブジェクト優先、`String`/`Map`濫用禁止
- **マルチテナント**: 全Repository操作で`Tenant`第一引数（OrganizationRepositoryは除く）

## 🚨 Java defaultメソッド実装の重要教訓
**問題**: インターフェースに完璧な`default`メソッドがあるのに、実装クラスで不要なオーバーライド

### ❌ 典型的失敗パターン
```java
// インターフェース: 完璧な標準実装
default AdminPermissions getRequiredPermissions(String method) { ... }

// 実装クラス: 不要な重複実装
@Override
public AdminPermissions getRequiredPermissions(String method) { ... } // ← 不要！
```

### ✅ 正しいパターン
```java
// 実装クラス: defaultメソッドをそのまま使用（実装不要）
public class EntryService implements ManagementApi {
  // getRequiredPermissionsは実装不要！
}
```

### 🛡️ 再発防止
- **実装前確認**: インターフェースに`default`メソッドがあるか？
- **基本原則**: `default`メソッドがある = 標準実装で十分
- **オーバーライド条件**: 本当にカスタマイズが必要な場合のみ

## Authentication Configuration 重要要件

### 🆔 ID形式: UUID必須
```javascript
import { v4 as uuidv4 } from "uuid";
const configId = uuidv4(); // 必須: UUIDv4形式
```

### 📝 レスポンス構造
```javascript
{
  "dry_run": false,
  "result": { // ← 注意: resultフィールド内に格納
    "id": "uuid",
    "type": "password", // 注意: 重複不可（テストではUUID使用）
    ...
  }
}
```

### 🏷️ 組織レベルAPI命名
```java
// ✅ 正しい: TenantIdentifier tenantIdentifier
// ❌ 間違い: TenantIdentifier adminTenant（誤解を招く）
```

## 🚨 組織レベルAPI実装の重要注意事項

### ❌ 致命的誤解（絶対回避）
1. **「組織レベル = システムレベルの簡易版」** → 実際はより複雑（+組織アクセス制御）
2. **Context Creator軽視** → TODOコメントやメッセージ返却で済ませる
3. **Audit Log手抜き** → `createOnRead()`で統一してエラー回避

### ✅ 絶対ルール
1. **システムレベル実装を完全理解**してから開始
2. **既存組織レベルAPI**（`OrgUserManagementEntryService`等）をパターン参考
3. **Context Creator必須使用**（`AuthenticationConfigRegistrationContextCreator`）
4. **適切なAudit Log**作成（create/update/delete別）

### ⚠️ 品質基準
- **Red Flag**: TODOコメント、適当実装、Context Creator未使用
- **Green Flag**: システムレベルと同じパターン、適切なAudit Log
- **教訓**: 「コンパイルが通る ≠ 正しい実装」

## Javadoc要件

### 📋 品質基準
- **RFC準拠明示**: 仕様書章番号・引用
- **使用例提供**: `<pre>{@code}` でコード例
- **相互参照**: `@see` による関連クラス・メソッドリンク
- **全パラメータ**: `@param`/`@return`の意味ある説明

### 🎯 実装対象（Issue #409）
1. `OrganizationAdminPermissions` - 権限管理コンテナ
2. `OrganizationAccessVerifier` - 4ステップアクセス制御検証
3. `AssignedTenants.tenantIdentifiers()` - テナントID抽出
4. `OrganizationRepository.findAssignment()` - 組織-テナント関係検索

### ✅ 品質確認
```bash
./gradlew javadoc  # HTML生成エラーなしを確認
```

## 🚨 「Conversation compacted」対処法

### 💀 危険状態: 技術詳細コンテキスト大幅喪失
**compacted検出 → 即座実装停止**

### 🛡️ 緊急対処フロー
1. **実装即停止** - 「とりあえず」続行は絶対禁止
2. **型システム再確認** - 権限型・レスポンス型・識別子型の整合性
3. **参考実装再分析** - システムレベル vs 組織レベル差異確認
4. **Context Creator確認** - 対象ドメイン用Creator存在・使用方法

### ✅ 実装再開条件
- [ ] 型システム完全理解（権限・レスポンス・識別子）
- [ ] 参考実装選択根拠説明可能
- [ ] アクセス制御4ステップ理解
- [ ] 曖昧理解（「たぶん」「適当に」）の排除

### ❌ 実装継続危険シグナル
- エラー依存判断（コンパイルエラーで正誤判定）
- パターン混在（システム・組織レベル混同）
- TODOコメント・適当実装・Context Creator未使用

**教訓**: 「不確実な実装より確実な設計確認を優先」

## 🚨 想像ドキュメント作成防止の重要教訓

### 💀 今回の重大な失敗事例（Issue #426 deployment.md）
**問題**: 実際のコードを確認せずに想像でドキュメントを作成 → 大量の誤情報

#### ❌ 具体的な誤り
1. **テーブル名誤り**: `tenants/users/clients` → 実際は `tenant/idp_user/client_configuration`
2. **組織関係誤解**: `tenants.organization_id`列想定 → 実際は `organization_tenants` 中間テーブル
3. **RLS複雑化**: 組織レベル複雑ポリシー想定 → 実際はシンプルなテナント分離のみ
4. **存在しないユーザー**: `idp_admin_user`作成指示 → 実際は `idp_app_user` のみ
5. **不要な設定**: 管理API用DataSource設定 → 実際は不要

### 🛡️ 絶対必須の事前確認手順

#### Phase 1: 実装確認（ドキュメント作成前必須）
```bash
# データベース関連ドキュメント作成時
echo "=== 事前調査必須コマンド ==="

# 1. DDL確認（30秒）
find . -name "*.sql" -path "*/postgresql/*" | head -5
grep "CREATE TABLE" libs/idp-server-database/postgresql/V1_0_0__init_lib.sql | head -10

# 2. ユーザー作成スクリプト確認（30秒）
find . -name "*user*.sql" -path "*/operation/*"
cat libs/idp-server-database/postgresql/operation/app_user.sql

# 3. RLS設定確認（1分）
grep -A 5 "ROW LEVEL SECURITY" libs/idp-server-database/postgresql/V1_0_0__init_lib.sql

# 4. 設定ファイル確認（1分）
find . -name "*.properties" -o -name "*.yml" | grep -v node_modules | head -5
```

#### Phase 2: 情報源の明記（必須）
```markdown
## 各セクション冒頭に必ず記載
**情報源**: `/libs/idp-server-database/postgresql/V1_0_0__init_lib.sql:11-24`
**確認日**: 2024-01-15
**確認方法**: `grep "CREATE TABLE tenant" V1_0_0__init_lib.sql`
**注意**: テーブル名は `tenant`（`tenants` ではない）
```

#### Phase 3: 不明点の明示（必須）
```markdown
❓ **要確認**: この設定値は推測です。実際の値は以下で確認：
`find . -name "*.properties" | xargs grep redis.password`

⚠️ **実装依存**: この手順は実際のアプリケーション実装により異なる可能性があります。
```

### 🎯 実践的防止ルール

#### ✅ ドキュメント作成時の必須行動
1. **コードファーストの原則**: 必ずソースコードを先に確認
2. **情報源記録**: 参照ファイル・確認方法を明記
3. **段階的確認**: テーブル名→設定値→手順の順で段階的に確認
4. **不明点明示**: 推測・仮定を明確に区別

#### ❌ 絶対禁止行動
1. **想像優先**: 「たぶんこうだろう」でドキュメント作成
2. **一般論適用**: 「Spring Bootなら通常は...」で推測
3. **確認省略**: 「時間がないから後で確認」
4. **エラー無視**: 確認コマンドのエラーを放置

### 🔍 品質チェック自動化

#### ドキュメント公開前チェックスクリプト
```bash
#!/bin/bash
# doc-validation.sh

echo "=== ドキュメント品質チェック ==="

# テーブル名検証
DOC_TABLES=$(grep -o "CREATE TABLE [a-zA-Z_]*" documentation/**.md)
ACTUAL_TABLES=$(grep "CREATE TABLE" $(find . -name "*.sql"))

# 設定値検証
DOC_CONFIGS=$(grep -o "SPRING_[A-Z_]*" documentation/**.md)

# ファイルパス検証
grep -o "/[a-zA-Z0-9/_.-]*\.(sql\|properties\|yml)" documentation/**.md | while read path; do
    [ ! -f "$path" ] && echo "❌ ファイル不存在: $path"
done
```

### 📚 学習リソース優先順位

#### コードベース理解の正しい順序
1. **DDL/スキーマ**: `V1_0_0__init_lib.sql` → テーブル構造理解
2. **設定例**: `operation/*.sql`, `docker-compose.yml` → 実際の設定値
3. **アプリケーション実装**: `*Repository.java`, `*Service.java` → 使用パターン
4. **テストコード**: `*test.js` → 期待動作確認

### 🚨 危険信号 - 即座にドキュメント作成停止
- 「まあ、こんな感じだろう」思考
- ファイル確認コマンドを実行していない
- エラーメッセージを無視している
- 他プロジェクトの経験で補完している

### ✅ 成功パターン - 安全なドキュメント作成
- 各記述に対応するソースコードが特定できる
- 確認コマンドが正常終了している
- 不明点が明確に分離されている
- 情報源が明記されている

**重要教訓**: 「ドキュメント作成は調査タスクであり、創作タスクではない」

---

## 身元確認申込み機能（Identity Verification Application）

### 🎯 アーキテクチャ概要
```
Control Plane API → Template登録 → 動的API生成 → HttpRequestExecutor → 外部サービス
```

### 📋 フロー設計
1. **Template登録**: Control Plane APIでテンプレートを事前登録
2. **動的ルーティング**: `{verification-type}/{process}` でAPIが動的生成
3. **7フェーズ処理**: Request → Pre Hook → Execution → Post Hook → Transition → Store → Response
4. **外部連携**: ExecutionフェーズでHttpRequestExecutorが外部APIを呼び出し

### 🛠️ API構造

#### Management API（設定用）
```
POST /v1/management/organizations/{orgId}/tenants/{tenantId}/identity-verification-configurations
```

#### 動的生成API（実行用）
```
POST /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{process}
※リソースオーナーのアクセストークン必須
```

#### コールバックAPI（外部サービス用）
```
POST /{tenant-id}/internal/v1/identity-verification/callback/{verification-type}/{process}
```

### ⚙️ HttpRequestExecutor統合

#### Execution設定例
```json
{
  "processes": {
    "external_verification": {
      "execution": {
        "type": "http_request",
        "http_request": {
          "url": "https://external-service.com/verify",
          "method": "POST",
          "auth_type": "oauth2",
          "retry_configuration": {
            "max_retries": 3,
            "retryable_status_codes": [502, 503, 504],
            "idempotency_required": true,
            "backoff_delays": ["PT1S", "PT2S", "PT4S"]
          }
        }
      }
    }
  }
}
```

### 🧪 テスト戦略

#### E2Eテストの正しいアプローチ
1. **Management API**でリトライ設定付きconfigurationを作成
2. **リソースオーナートークン**取得
3. **動的生成API**でidentity verification実行
4. **外部サービス503エラー**でリトライ動作を検証
5. **レスポンス時間**でリトライ実行を確認

#### ❌ 誤ったテストアプローチ
- 直接MockoonのAPIを呼び出す（HttpRequestExecutorを経由しない）
- Basic認証を使用（リソースオーナートークンが必要）
- Management APIを使わずに設定なしでテスト

### 🔍 重要ポイント

#### データ形式統一
- **スネークケース**: `http_request` (toMapメソッドで正しく出力)
- **UUID必須**: configuration IDは必ずUUIDv4形式

#### マッピングルール
- **JSONPath**: `$.request_body.field` でデータ参照
- **静的値**: `static_value` で固定値設定
- **ネスト対応**: `to: "nested.field"` でオブジェクト構築

#### 認証パターン
- **OAuth2**: `auth_type: "oauth2"` + `oauth_authorization`設定
- **HMAC**: `auth_type: "hmac_sha256"` + `hmac_authentication`設定
- **なし**: `auth_type: "none"`

### 🚨 実装時の注意事項

#### 設定検証
- テンプレート登録後に取得APIで設定内容確認必須
- `retry_configuration`が正しく保存されているか検証

#### エラーハンドリング
- 外部サービスエラーは適切にマッピングして内部処理
- ステータス遷移（approved/rejected/cancelled）の条件設定

#### パフォーマンス
- リトライ動作はレスポンス時間で間接的に検証
- 過度なリトライでタイムアウトしないよう上限設定

---

## 🔧 実装品質向上のための規約 (Issue #398対応)

### 🚨 メソッドチェーン（デメテルの法則）違反禁止
```java
// ❌ 悪い例: 内部構造への依存
if (failedResult.status().isSuccess()) {
if (user.profile().email().domain().equals("example.com")) {

// ✅ 良い例: 適切なカプセル化
if (failedResult.isSuccess()) {
if (user.hasEmailDomain("example.com")) {
```

**原則**: オブジェクトは隣接する（直接保持する）オブジェクトとのみ会話する

### 🚨 自明なコメント禁止
```java
// ❌ 削除すべきコメント
// Extract execution context from the failed result
SecurityEventHookExecutionContext context = extractExecutionContext(failedResult);

// ❌ 削除すべきコメント
// Build HttpRequest for SSF transmission
HttpRequest httpRequest = createSsfRequest(endpoint, token);

// ✅ 価値のあるコメント
// SSF specification requires secevent+jwt content type
.header("Content-Type", "application/secevent+jwt")

// OAuth authentication is optional for SSF transmission
if (transmissionConfig.oauthAuthorization() != null) {
```

**原則**: 「何をしているか」ではなく「なぜそうするか」を説明

### 🔄 複雑メソッドのリファクタリング原則
```java
// ❌ 悪い例: メイン処理が詳細構築に埋もれる
private SecurityEventHookResult send(...) {
  // 50行の詳細なresult構築コード
  Map<String, Object> executionResult = new HashMap<>();
  // request情報
  Map<String, Object> request = new HashMap<>();
  request.put("endpoint", endpoint);
  // ... 30行続く

  // メイン処理が見えない
  if (httpResult.isSuccess()) {
    return success(...);
  }
}

// ✅ 良い例: メイン処理が明確
private SecurityEventHookResult send(...) {
  HttpRequest httpRequest = createSsfRequest(endpoint, token);
  HttpRequestResult result = executeRequest(httpRequest, config);
  Map<String, Object> details = createExecutionDetails(...);

  if (result.isSuccess()) {
    return SecurityEventHookResult.successWithContext(...);
  }
}
```

**原則**: メイン処理フローを明確にし、詳細構築は別メソッドに分離

### 🗑️ 重複データ排除
```java
// ❌ 悪い例: 重複情報の保存
{
  "hook_execution_context": {...},
  "original_security_event": {...}, // 重複データ
  "execution_result": {...}
}

// ✅ 良い例: 重複排除
{
  "hook_execution_context": {...},
  "execution_result": {...}
}
```

**原則**: 同じ情報を複数箇所に保存しない

### 📊 ステータス情報の適切な配置
```java
// ❌ 悪い例: ステータス情報が分散
{
  "execution_result": {
    "status": "SUCCESS",
    "http_status_code": 200,  // 詳細レベルの情報
    "execution_details": {...}
  }
}

// ✅ 良い例: 階層的な情報配置
{
  "execution_result": {
    "status": "SUCCESS",
    "execution_details": {
      "http_status_code": 200,  // 詳細内に配置
      "request": {...},
      "response": {...}
    }
  }
}
```

**原則**: 情報は適切な抽象レベルに配置する

### 🧩 判定メソッドの追加推奨
```java
// SecurityEventHookResultクラスに追加すべきメソッド
public boolean isSuccess() {
    return status.isSuccess();
}

public boolean isFailure() {
    return status.isFailure();
}

public boolean isAlreadySuccessful() {
    return isSuccess();
}
```

**原則**: ドット記法チェーンを避けるため、適切な判定メソッドを提供

### 💾 executionDurationMsの用途明確化
- **パフォーマンス監視**: Hook実行時間の測定・SLA監視
- **リトライ判定**: タイムアウト系エラーの識別
- **デバッグ支援**: 実行遅延の原因調査
- **リトライ戦略**: 実行時間に基づくbackoff調整

**原則**: 各フィールドの存在理由と用途を明確に文書化

### 🛡️ TODO実装の扱い
```java
// ❌ 本番リリース時に残してはいけない
throw new UnsupportedOperationException("実装予定");

// ✅ 開発中の一時的なマーカーとしてのみ使用
// TODO: Issue #XXX - SecurityEvent再構築実装
```

**原則**: TODOは開発中の一時的なマーカーのみ。本番では完全実装必須

---

## 現在の状況
- **ステータス**: Clean（コミット可能変更なし）
- **最新コミット**: Implement comprehensive security event logging system
- **完了済み**:
  - #292 (SecurityEvent拡張)
  - #401 (FIDO-UAFリセット)
  - #676 (AI開発者向け知識ベース作成) ✅ **NEW**
- **進行中**: なし

## AI開発者向けリソース

### 📚 詳細ドキュメント
[AI開発者向けモジュールガイド](documentation/docs/content_10_ai_developer/ai-01-index.md) - 全20モジュール詳解
- **core.md**: 全9ドメイン（OAuth, Token, Identity, Authentication等）
- **platform.md**: マルチテナント、JsonConverter、PluginLoader
- **use-cases.md**: EntryService 10フェーズパターン
- **control-plane.md**: API契約、権限37種、Context Creator
- **adapters.md**: Redis, Flyway, PostgreSQL/MySQL, ExceptionHandler
- **extensions.md**: CIBA, FAPI, IDA, PKCE, VC
- **authentication-federation.md**: 認証インタラクター、WebAuthn、Federation
- **notification-security-event.md**: FCM, APNS, Email, SSF, Security Event Hooks

### 🎯 実装時のチェックリスト
- [ ] Tenant第一引数パターン（OrganizationRepository除く）
- [ ] PluginLoader静的メソッド使用（`loadFromInternalModule()`）
- [ ] Context Creator必須実装（TODOコメント禁止）
- [ ] defaultメソッドの不要なオーバーライド回避
- [ ] JsonConverter.snakeCaseInstance()使用（DTO変換時）
- [ ] EntryService 10フェーズ遵守
- [ ] Audit Log適切化（create/update/delete別）
- [ ] **Validator/Verifier は void validate()/verify() でthrow**（Resultオブジェクト返却禁止）
- [ ] **組織レベルAPIでOrganization必要な場合は専用Request Wrapper + 専用Service作成**
- [ ] **Handler層でManagementApiExceptionをcatchしてResultに変換**
- [ ] **EntryService層でresult.toResponse()でHTTPステータス適切化（throw禁止）**