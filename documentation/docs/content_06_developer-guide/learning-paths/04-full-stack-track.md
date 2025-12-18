# Full Stack Track（両方マスター）

## 🎯 このトラックの目標

**Control PlaneとApplication Planeの両方**を完全に習得し、システム全体を設計・実装できるようになる。

- Control Plane: 管理API実装・組織レベルAPI実装
- Application Plane: 認証フロー実装・認証方式追加
- 統合実装: 両Planeを組み合わせた新機能開発
- アーキテクチャ設計

**前提**: [初級ラーニングパス](./01-beginner.md)完了

---

## 📚 学習内容

### Control Plane Track

[Control Plane Track](./02-control-plane-track.md)の全内容を習得：

#### 習得内容
- システムレベルAPI（CRUD）
- Repository実装（Query/Command分離）
- ContextBuilder実装
- 組織レベルAPI（4ステップアクセス制御）

---

### Application Plane Track

[Application Plane Track](./03-application-plane-track.md)の全内容を習得：

#### 習得内容
- Authorization Flow実装
- Token Endpoint実装（Grant Type追加）
- 認証インタラクター実装
- Federation実装（外部IdP連携）

---

### 統合実装

#### 読むべきドキュメント
- [ ] [Plugin実装ガイド](../04-implementation-guides/impl-12-plugin-implementation.md)
- [ ] [HttpRequestExecutor実装ガイド](../04-implementation-guides/impl-16-http-request-executor.md)
- [ ] [外部サービス連携ガイド](../04-implementation-guides/impl-17-external-integration.md)

#### 実践課題: 新しい認証方式を完全実装

Magic Link認証を0から実装する例：

**Control Plane（管理API）**:
- Magic Link認証設定API（設定CRUD）
- Email Template設定
- 有効期限設定

**Application Plane（認証フロー）**:
- MagicLinkAuthenticationInteractor実装
- トークン生成（UUID + HMAC署名）
- Email送信（EmailNotificationSender使用）
- トークン検証（署名検証・期限チェック）

#### チェックリスト
- [ ] Control PlaneとApplication Planeの連携を理解
- [ ] 設定の反映フローを実装できる
- [ ] 両Planeを横断した機能を設計できる
- [ ] Plugin自動ロード確認
- [ ] E2Eテスト作成

---

### アーキテクチャ設計

#### 読むべきドキュメント
- [ ] [AI開発者向け: 全モジュールガイド](../content_10_ai_developer/ai-01-index.md)
- [ ] [AI開発者向け: Lessons Learned](../content_10_ai_developer/ai-02-lessons-learned.md)

#### 実践課題: 大規模機能を設計・実装

以下のいずれかを0から設計・実装：

1. **新しいOAuth拡張仕様対応**
   - 例: RAR (Rich Authorization Requests - RFC 9396)
   - Control Plane: Authorization Details設定API
   - Application Plane: authorization_detailsパラメータ対応

2. **新しいフェデレーション方式**
   - 例: SAML 2.0連携
   - Control Plane: SAML設定API（Entity ID、証明書、属性マッピング）
   - Application Plane: SAML Request生成・Response検証

3. **段階的認証（Step-up Authentication）**
   - Control Plane: 段階的認証ポリシー設定API
   - Application Plane: ACRレベル判定・追加認証要求

#### チェックリスト
- [ ] アーキテクチャ設計ドキュメントを作成できる
- [ ] 層責任を遵守した設計ができる
- [ ] パフォーマンス・セキュリティを考慮した設計ができる
- [ ] 大規模機能を0から実装できる
- [ ] PRを出してレビューを受けられる

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
- [ ] Control Plane: システムレベル・組織レベルAPIを実装できる
- [ ] Application Plane: 認証フロー・認証インタラクターを実装できる
- [ ] 両Planeを統合した新機能を実装できる
- [ ] E2Eテスト（統合テスト含む）を作成できる
- [ ] PRを出してレビューを受けられる

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
Control Planeで設定を作成・更新すると、Application Planeの動作が変更される：
- 例: パスワードポリシー設定 → PasswordAuthenticationInteractorの検証ルール変更
- 設定はキャッシュされ、Application Planeで高速に読み込まれる

#### パターン2: 動的API生成
Control Planeで設定を作成すると、Application PlaneにAPIエンドポイントが動的に生成される：
- 例: 身元確認設定 → 身元確認申込みAPIが動的に生成
- HttpRequestExecutorで外部サービス連携

#### パターン3: Plugin動的切り替え
Control Planeで設定を選択すると、Application PlaneでPluginが動的に選択される：
- 例: Federation設定でプロバイダー選択 → SsoExecutorが動的に選択
- Plugin自動ロードで実装追加が容易

---

### よくあるミス

#### 1. 設定検証のタイミング

```java
// ❌ 間違い: Control Planeで実際の動作検証
public void create(AuthenticationConfiguration config) {
    // 外部サービスに接続して検証 → 遅い、失敗しやすい
    externalService.testConnection(config);
}

// ✅ 正しい: Control Planeは形式チェックのみ
public void create(AuthenticationConfiguration config) {
    // JSON形式・必須フィールドのみチェック
    validator.validate(config);
}

// Application Planeで実行時に動作検証
public AuthenticationResult authenticate(...) {
    if (!canAuthenticate(config)) {
        throw new AuthenticationConfigurationException("Invalid configuration");
    }
}
```

**理由**: 設定時は形式のみ、実行時に実際の動作を検証

#### 2. キャッシュクリア忘れ

```java
// ❌ 間違い: Control Planeで設定更新してもキャッシュクリアしない
public void updateConfig(...) {
    authenticationConfigCommandRepository.update(...);
    // キャッシュが古いまま → Application Planeで古い設定が使われる
}

// ✅ 正しい: 設定更新時にキャッシュクリア
@CacheEvict(value = "authentication-config", key = "#tenant.value() + ':' + #type")
public void updateConfig(...) {
    authenticationConfigCommandRepository.update(...);
}
```

**理由**: Application Planeの高速化のためキャッシュを使用、Control Planeでの即時反映が必要

---

## 🔗 関連リソース

- [AI開発者向け: 全モジュールガイド](../content_10_ai_developer/ai-01-index.md)
- [AI開発者向け: Lessons Learned](../content_10_ai_developer/ai-02-lessons-learned.md)
- [開発者ガイドTOC](../DEVELOPER_GUIDE_TOC.md)
- [Control Plane概念](../../content_03_concepts/01-foundation/concept-02-control-plane.md)

---

**最終更新**: 2025-12-18
**対象**: Full Stack開発者・技術リーダー候補
**習得スキル**: Control Plane、Application Plane、統合実装、アーキテクチャ設計、Plugin実装、外部サービス連携
