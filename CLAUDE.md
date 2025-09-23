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
2. **UseCase層**: `{Domain}{Action}EntryService` - オーケストレーション専用
3. **Core層**: Handler-Service-Repository - OIDC仕様準拠・ドメインロジック
4. **Adapter層**: Repository - 永続化カプセル化（ドメインロジック禁止）

## Handler-Service-Repository パターン
- **Handler**: `{Domain}{Action}Handler` - プロトコル処理・オーケストレーション
- **Service**: `{Domain}{Action}Service` - 純粋ビジネスロジック
- **Repository**: `{Entity}QueryRepository`/`{Entity}CommandRepository` - データアクセス抽象化

## 検証・エラーハンドリング
- **Validator**: 入力形式チェック → `{Operation}BadRequestException`
- **Verifier**: ビジネスルール検証 → `OAuthRedirectableBadRequestException`
- **例外**: `throwExceptionIf{Condition}()` パターン、OAuth標準エラーコード

## 重要な実装パターン
- **Repository**: 全メソッドで `Tenant` が第一引数（マルチテナント分離）。OrganizationRepositoryは除く。
- **Extension**: `Map<GrantType, Service>` + Plugin インターフェース
- **設定**: `TenantAttributes.optValueAsBoolean(key, default)` パターン
- **命名**: `get()`必須存在, `find()`任意存在, `is/has/can`判定メソッド

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
POST /v1/management/organizations/{orgId}/tenants/{tenantId}/identity-verification-configs
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

## 現在の状況
- **ステータス**: Clean（コミット可能変更なし）
- **最新コミット**: Implement comprehensive security event logging system
- **完了済み**: #292 (SecurityEvent拡張), #401 (FIDO-UAFリセット)
- **進行中**: Issue #409 組織レベルテナント管理API（実装完了、Javadoc準備中）