# Full Stack Track（両方マスター）

## 🎯 このトラックの目標

**Control PlaneとApplication Planeの両方**を完全に習得し、システム全体を設計・実装できるようになる。

- ✅ Control Plane: 管理API実装・組織レベルAPI実装
- ✅ Application Plane: 認証フロー実装・認証方式追加
- ✅ 統合実装: 両Planeを組み合わせた新機能開発
- ✅ アーキテクチャ設計

**所要期間**: 1-2ヶ月

**前提**: [初級ラーニングパス](./01-beginner.md)完了

---

## 📅 学習スケジュール

### Week 1-2: Control Plane Track

[Control Plane Track](./02-control-plane-track.md)の全内容を実施：

#### Week 1: システムレベルAPI実装
- Day 1-2: 最初のAPI実装チュートリアル
- Day 3-5: システムレベルAPI（CRUD）実装
- Day 6-8: Repository実装
- Day 9-10: Context Creator実装

#### Week 2: 組織レベルAPI実装
- Day 11-15: 組織レベルAPI実装
- Day 16-20: 複雑なアクセス制御実装

**習得内容**:
- システムレベルAPI（CRUD）
- Repository実装（Query/Command分離）
- Context Creator実装
- 組織レベルAPI（4ステップアクセス制御）

---

### Week 3-4: Application Plane Track

[Application Plane Track](./03-application-plane-track.md)の全内容を実施：

#### Week 3: OAuth/OIDCフロー実装
- Day 21-23: Authorization Flow実装
- Day 24-25: Token Endpoint実装
- Day 26-28: 認証インタラクター実装
- Day 29-30: 新しい認証方式追加

#### Week 4: Grant Type・Federation実装
- Day 31-33: 新しいGrant Type追加
- Day 34-37: 外部IdP連携実装
- Day 38-40: CIBA実装理解

**習得内容**:
- Authorization Flow実装
- Token Endpoint実装（Grant Type追加）
- 認証インタラクター実装
- Federation実装（外部IdP連携）

---

### Week 5-6: 統合実装

#### Day 41-45: Control + Application統合機能実装
- [ ] **所要時間**: 20時間
- [ ] 両Planeを組み合わせた新機能を0から設計・実装

**実践課題: 新しい認証方式を完全実装**

```
課題: "Magic Link認証"を0から実装する

Control Plane（管理API）:
  1. Magic Link認証設定API
     POST /v1/management/tenants/{tenantId}/authentication-configurations
     {
       "type": "magic_link",
       "email_template_id": "...",
       "link_expiry_seconds": 300
     }

  2. 設定取得API
     GET /v1/management/tenants/{tenantId}/authentication-configurations/magic_link

Application Plane（認証フロー）:
  1. Magic Link送信API
     POST /{tenant-id}/v1/authentications/{auth-req-id}/magic-link/send
     {
       "email": "user@example.com"
     }

  2. Magic Link検証API
     POST /{tenant-id}/v1/authentications/{auth-req-id}/magic-link/verify
     {
       "token": "magic-link-token"
     }
```

**実装手順**:
```
1. Control Plane実装（Day 41-42）
   ├─ AuthenticationConfiguration拡張（magic_link設定）
   ├─ AuthenticationConfigRegistrationContextCreator修正
   ├─ AuthenticationConfigManagementEntryService修正
   └─ E2Eテスト（設定CRUD）

2. Application Plane実装（Day 43-45）
   ├─ MagicLinkAuthenticationInteractor実装
   │   ├─ トークン生成（UUID + HMAC署名）
   │   ├─ Email送信（EmailNotificationSender使用）
   │   └─ トークン検証（署名検証・期限チェック）
   ├─ Plugin自動ロード確認
   └─ E2Eテスト（送信・検証フロー）

3. 統合テスト（Day 45）
   ├─ Control Planeで設定作成
   ├─ Application Planeで認証実行
   └─ 全フロー通しでテスト
```

**チェックポイント**:
- [ ] Control PlaneとApplication Planeの連携を理解
- [ ] 設定の反映フローを実装できる
- [ ] 両Planeを横断した機能を設計できる

---

#### Day 46-50: Plugin実装・外部サービス連携
- [ ] **所要時間**: 20時間
- [ ] [Plugin実装ガイド](../04-implementation-guides/impl-12-plugin-implementation.md)を実施
- [ ] [外部サービス連携ガイド](../04-implementation-guides/impl-17-external-integration.md)を実施

**実践課題: 外部SMS送信サービス連携**

```java
// SMS送信Plugin実装
public class TwilioSmsSender implements SmsSender {

    private final HttpRequestExecutor httpRequestExecutor;

    @Override
    public SmsChannel supportedChannel() {
        return SmsChannel.TWILIO;
    }

    @Override
    public SmsResult send(SmsMessage message, SmsConfiguration config) {
        // HttpRequestExecutor使用
        HttpRequest httpRequest = HttpRequest.builder()
            .url(config.getApiEndpoint())
            .method(HttpMethod.POST)
            .body(createTwilioRequest(message))
            .build();

        OAuth2Configuration oAuth2Config = OAuth2Configuration.builder()
            .tokenEndpoint(config.getTokenEndpoint())
            .clientId(config.getClientId())
            .clientSecret(config.getClientSecret())
            .build();

        RetryConfiguration retryConfig = RetryConfiguration.builder()
            .maxRetries(3)
            .retryableStatusCodes(Set.of(502, 503, 504))
            .idempotencyRequired(true)
            .build();

        HttpRequestExecutionConfig executionConfig = HttpRequestExecutionConfig.builder()
            .httpRequest(httpRequest)
            .oAuthAuthorization(oAuth2Config)
            .retryConfiguration(retryConfig)
            .build();

        HttpRequestResult result = httpRequestExecutor.execute(executionConfig);

        if (result.isSuccess()) {
            return SmsResult.success(message.to());
        }
        return SmsResult.failure(result.errorMessage());
    }
}
```

**チェックリスト**:
- [ ] Plugin実装
- [ ] HttpRequestExecutor使用
- [ ] OAuth 2.0認証設定
- [ ] リトライ設定（冪等性保証）
- [ ] エラーハンドリング
- [ ] Mockoonを使ったE2Eテスト

**チェックポイント**:
- [ ] HttpRequestExecutorの全機能を使いこなせる
- [ ] 冪等性の重要性を理解している
- [ ] リトライ戦略を設計できる

---

### Week 7-8: アーキテクチャ設計

#### Day 51-60: 大規模機能を0から設計・実装
- [ ] **所要時間**: 40時間
- [ ] 新規機能を0から設計・実装

**実践課題: 以下のいずれかを実装**

1. **新しいOAuth拡張仕様対応**
   ```
   例: RAR (Rich Authorization Requests - RFC 9396)

   Control Plane:
     - Authorization Details設定API

   Application Plane:
     - authorization_detailsパラメータ対応
     - Authorization Detailsの検証・保存
     - Access Tokenにauthorization_details含める
   ```

2. **新しいフェデレーション方式**
   ```
   例: SAML 2.0連携

   Control Plane:
     - SAML設定API（Entity ID、証明書、属性マッピング）

   Application Plane:
     - SAML Request生成
     - SAML Response検証
     - 属性マッピング
   ```

3. **段階的認証（Step-up Authentication）**
   ```
   Control Plane:
     - 段階的認証ポリシー設定API
     - ACRレベル設定

   Application Plane:
     - ACRレベル判定
     - 追加認証要求
     - ACR値返却
   ```

**設計ドキュメント作成**:
```markdown
1. 要件定義
   - 機能要件（何を実現するか）
   - 非機能要件（パフォーマンス・セキュリティ）

2. アーキテクチャ設計
   - Control Plane設計（API契約・EntryService・Repository）
   - Application Plane設計（Handler・Service・Interactor）
   - データモデル設計（テーブル設計・DDL）

3. API設計
   - エンドポイント一覧
   - Request/Response仕様
   - エラーコード定義

4. セキュリティ設計
   - 認証・認可方式
   - 脆弱性対策
   - 監査ログ

5. テスト計画
   - ユニットテスト
   - 統合テスト
   - E2Eテスト（正常系・異常系）
```

**チェックポイント**:
- [ ] アーキテクチャ設計ドキュメントを作成できる
- [ ] 層責任を遵守した設計ができる
- [ ] レビューで設計変更が少ない（3回以内）

---

#### Day 61-70: 実装・レビュー・リファクタリング
- [ ] **所要時間**: 40時間
- [ ] 設計した機能を実装
- [ ] レビュー対応・リファクタリング

**チェックポイント**:
- [ ] 大規模機能を0から実装・マージできる
- [ ] レビューコメントが少ない（20件以下）
- [ ] パフォーマンス・セキュリティを考慮した実装

---

## 📚 必読ドキュメント

| 優先度 | ドキュメント | 所要時間 |
|-------|------------|---------|
| 🔴 必須 | [Control Plane Track](./02-control-plane-track.md) | - |
| 🔴 必須 | [Application Plane Track](./03-application-plane-track.md) | - |
| 🔴 必須 | [Plugin実装ガイド](../04-implementation-guides/impl-12-plugin-implementation.md) | 30分 |
| 🔴 必須 | [外部サービス連携ガイド](../04-implementation-guides/impl-17-external-integration.md) | 30分 |
| 🔴 必須 | [AI開発者向け: 全モジュールガイド](../content_10_ai_developer/ai-01-index.md) | 180分 |

---

## ✅ 完了判定基準

以下をすべて達成したらFull Stack Trackクリア：

### 知識面
- [ ] Control PlaneとApplication Planeの違いを説明できる
- [ ] 両Planeの連携方法を説明できる
- [ ] 設定の反映フロー（Control → Application）を説明できる
- [ ] OAuth 2.0/OIDC仕様を深く理解している

### 設計面
- [ ] アーキテクチャ設計ドキュメントを作成できる
- [ ] 層責任を遵守した設計ができる
- [ ] パフォーマンス・セキュリティを考慮した設計ができる
- [ ] 両Planeを横断した機能を設計できる

### 実践面
- [ ] Control Plane: システムレベル・組織レベルAPIを実装・マージした
- [ ] Application Plane: 認証フロー・認証インタラクターを実装・マージした
- [ ] 両Planeを統合した新機能を実装・マージした
- [ ] E2Eテスト（統合テスト含む）を作成し、全件パスした
- [ ] レビューコメントが20件以下

### リーダーシップ
- [ ] 新規開発者のコードレビューができる
- [ ] 設計相談に乗れる
- [ ] アーキテクチャ改善提案ができる

---

## 🚀 次のステップ：技術リーダーへ

Full Stack Track完了後は**技術リーダー**として活躍：

### アーキテクチャ改善提案
- システム全体のパフォーマンス改善
- セキュリティ強化提案
- 新しいアーキテクチャパターン導入

### 新規モジュール設計
- 新しいOAuth拡張仕様対応の設計
- マイクロサービス分割の検討
- 新しいデータストア導入の検討

### 技術選定・評価
- 新しいライブラリ・フレームワークの評価
- パフォーマンステスト実施・分析
- セキュリティ監査

### チームメンバーのメンタリング
- 新規開発者のオンボーディング
- コードレビュー
- 設計レビュー
- 技術相談対応

---

## 💡 Full Stack開発のヒント

### Control PlaneとApplication Planeの連携パターン

#### パターン1: 設定 → 動作変更
```
Control Plane:
  POST /v1/management/tenants/{tenantId}/authentication-configurations
  {
    "type": "password",
    "min_length": 12,
    "require_uppercase": true,
    "require_number": true
  }

Application Plane:
  設定を読み込んでパスワード検証ルール変更
  → PasswordAuthenticationInteractor.authenticate()
     └─ AuthenticationConfiguration.getPasswordPolicy()
```

#### パターン2: 動的API生成
```
Control Plane:
  POST /v1/management/.../identity-verification-configurations
  {
    "verification_type": "driver_license",
    "processes": {
      "external_verification": { ... }
    }
  }

Application Plane:
  動的にAPIエンドポイント生成
  POST /{tenant-id}/v1/me/identity-verification/applications/driver_license/external_verification
  → Configuration読み込み → HttpRequestExecutor実行
```

#### パターン3: Plugin動的切り替え
```
Control Plane:
  Federation設定でプロバイダー選択
  {
    "provider": "azure_ad",
    "client_id": "...",
    "client_secret": "..."
  }

Application Plane:
  設定に基づいてPlugin選択
  → SsoExecutors.get(SsoProvider.AZURE_AD)
     └─ AzureAdSsoExecutor実行
```

---

### 統合実装のベストプラクティス

#### 1. 設定の検証タイミング
```java
// Control Plane（設定作成時）
// ✅ 設定の形式チェックのみ
AuthenticationConfigValidator validator = new AuthenticationConfigValidator(config);
validator.validate();  // JSON形式・必須フィールドのみチェック

// Application Plane（実行時）
// ✅ 実際の動作検証
AuthenticationInteractor interactor = interactors.get(config.type());
if (!interactor.canAuthenticate(config)) {
    throw new AuthenticationConfigurationException("Invalid configuration");
}
```

**理由**: 設定時は形式のみ、実行時に実際の動作を検証

#### 2. 設定キャッシュ戦略
```java
// ✅ Application Planeでキャッシュ
@Cacheable(value = "authentication-config", key = "#tenant.value() + ':' + #type")
public AuthenticationConfiguration getConfig(Tenant tenant, String type) {
    return authenticationConfigQueryRepository.get(tenant, new AuthenticationType(type));
}

// Control Planeで設定更新時にキャッシュクリア
@CacheEvict(value = "authentication-config", key = "#tenant.value() + ':' + #type")
public void updateConfig(Tenant tenant, String type, AuthenticationConfiguration config) {
    authenticationConfigCommandRepository.update(tenant, type, config);
}
```

**理由**: Application Planeの高速化、Control Planeでの即時反映

---

### セキュリティ考慮事項

#### 1. Control Planeでの設定検証
```java
// ✅ 危険な設定を拒否
if (config.getPasswordMinLength() < 8) {
    throw new InvalidConfigurationException("Password minimum length must be at least 8");
}

if (config.getTokenExpirySeconds() > 86400) {
    throw new InvalidConfigurationException("Token expiry must not exceed 24 hours");
}
```

#### 2. Application Planeでのフォールバック
```java
// ✅ 設定が無効な場合のデフォルト動作
AuthenticationConfiguration config = authenticationConfigRepository.find(tenant, type);
if (!config.exists() || !config.isEnabled()) {
    // デフォルト設定を使用
    config = AuthenticationConfiguration.getDefault(type);
}
```

---

### パフォーマンス最適化

#### 1. 設定の事前ロード
```java
// Application起動時に全テナントの設定をキャッシュ
@PostConstruct
public void preloadConfigurations() {
    List<Tenant> tenants = tenantQueryRepository.findAll();
    for (Tenant tenant : tenants) {
        authenticationConfigService.loadAllConfigs(tenant);
    }
}
```

#### 2. バルク操作
```java
// Control Planeで複数設定を一括作成
POST /v1/management/tenants/{tenantId}/authentication-configurations/bulk
{
  "configurations": [
    { "type": "password", ... },
    { "type": "sms", ... },
    { "type": "email", ... }
  ]
}
```

---

## 🔗 関連リソース

- [AI開発者向け: 全モジュールガイド](../content_10_ai_developer/ai-01-index.md)
- [AI開発者向け: Lessons Learned](../content_10_ai_developer/ai-02-lessons-learned.md)
- [開発者ガイドTOC](../DEVELOPER_GUIDE_TOC.md)
- [Control Plane概念](../../content_03_concepts/01-foundation/concept-02-control-plane.md)

---

**最終更新**: 2025-10-13
**対象**: Full Stack開発者・技術リーダー候補（1-2ヶ月）
**習得スキル**: Control Plane、Application Plane、統合実装、アーキテクチャ設計、Plugin実装、外部サービス連携
