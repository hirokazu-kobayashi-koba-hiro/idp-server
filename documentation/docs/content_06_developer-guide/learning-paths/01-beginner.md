# 初級ラーニングパス（1-2週間）

## 🎯 目標

既存機能の理解・簡単なバグ修正ができるようになる。

### 習得スキル
- ✅ 4層アーキテクチャの理解
- ✅ 既存コードの読解
- ✅ 簡単なバグ修正
- ✅ E2Eテスト実行

---

## 📅 学習スケジュール

### Week 1: アーキテクチャ理解

#### Day 1: 環境構築・アーキテクチャ概要
- [ ] **所要時間**: 2時間
- [ ] 環境構築（Java 21, Gradle, PostgreSQL）
- [ ] [01. アーキテクチャ概要](../01-getting-started/01-architecture-overview.md)を読む
- [ ] ビルド・テスト実行
  ```bash
  ./gradlew spotlessApply
  ./gradlew build
  ./gradlew test
  ```

**チェックポイント**:
- [ ] Controller/UseCase/Core/Adapter層の役割を説明できる
- [ ] **Control PlaneとApplication Planeの違いを説明できる**（architecture-overview.mdに含まれる）
- [ ] ビルド・テストが成功する

---

#### Day 2-3: 共通パターン理解
- [ ] **所要時間**: 4時間
- [ ] [03. 共通実装パターン](../06-patterns/common-patterns.md)を読む
- [ ] Repository/Context Creator/Handler-Serviceパターンを理解

**実践課題**:
```java
// 既存コードから以下を見つける
1. Repository第一引数がTenantになっているか確認
2. Context Creatorの使用例を見つける
3. Handler-Serviceパターンの実装例を見つける
```

**チェックポイント**:
- `ClientManagementEntryService`のコードを読んで処理フローを説明できる
- Repository命名規則（`get()` vs `find()`）を説明できる

---

#### Day 4-5: トラブルシューティング・コードレビュー
- [ ] **所要時間**: 3時間
- [ ] [04. トラブルシューティング](../07-troubleshooting/common-errors.md)を読む
- [ ] [05. コードレビューチェックリスト](../08-reference/code-review-checklist.md)を読む

**実践課題**:
```bash
# 意図的にエラーを起こして解決する
1. spotlessCheckを失敗させる → spotlessApplyで修正
2. 存在しないテナントIDでAPIを呼ぶ → TenantNotFoundExceptionを確認
3. 権限のないトークンでAPIを呼ぶ → 403エラーを確認
```

**チェックポイント**:
- よくあるエラー5つを即座に解決できる
- PR前のチェックリスト項目を理解している

---

### Week 2: 既存コード理解・バグ修正

#### Day 6-7: 既存機能コードリーディング
- [ ] **所要時間**: 6時間
- [ ] システムレベルAPI実装を読む
  - `ClientManagementEntryService`
  - `UserManagementEntryService`
  - `RoleManagementEntryService`

**実践課題**:
```
以下の処理フローを図解する：
1. クライアント作成APIのリクエスト → レスポンスまでの流れ
2. 権限チェックがどこで行われるか
3. Audit Logがいつ記録されるか
```

**チェックポイント**:
- Handler-Serviceパターン（EntryService/Handler/Service/Repositoryの責務分担）を説明できる
- 権限チェック・Audit Log記録のタイミングを理解している

---

#### Day 8-9: 簡単なバグ修正
- [ ] **所要時間**: 8時間
- [ ] Good First Issue を選ぶ（例: タイポ修正、ログ追加、バリデーション追加）
- [ ] バグ修正PRを作成

**実践課題**:
```
以下のバグを修正する練習：
1. エラーメッセージのタイポ修正
2. 不足しているログ出力追加
3. バリデーションルール追加（例: メールアドレス形式チェック）
```

**チェックリスト**:
- [ ] `./gradlew spotlessApply`実行
- [ ] `./gradlew build`成功
- [ ] `./gradlew test`全件パス
- [ ] [05. コードレビューチェックリスト](../08-reference/code-review-checklist.md)確認
- [ ] PR作成（コミットメッセージに`@codex review`含む）

**チェックポイント**:
- 既存コードを壊さずにバグ修正できる
- PRが初回レビューで承認される

---

#### Day 10: E2Eテスト実行・デバッグ
- [ ] **所要時間**: 4時間
- [ ] E2Eテスト実行
  ```bash
  cd e2e
  npm install
  npm test
  ```
- [ ] テスト失敗時のデバッグ方法を学ぶ

**実践課題**:
```javascript
// 既存E2Eテストを読んで理解する
1. e2e/spec/management/client-management.spec.js
2. テスト前のセットアップ（トークン取得、テナント作成）
3. レスポンス検証方法
```

**チェックポイント**:
- E2Eテストの構造を理解している
- テスト失敗時にログを確認してデバッグできる

---

## 📚 必読ドキュメント

| 優先度 | ドキュメント | 所要時間 |
|-------|------------|---------|
| 🔴 必須 | [01. アーキテクチャ概要](../01-getting-started/01-architecture-overview.md) | 15分 |
| 🔴 必須 | [03. 共通実装パターン](../06-patterns/common-patterns.md) | 20分 |
| 🔴 必須 | [04. トラブルシューティング](../07-troubleshooting/common-errors.md) | 15分 |
| 🔴 必須 | [05. コードレビューチェックリスト](../08-reference/code-review-checklist.md) | 10分 |

---

## ✅ 完了判定基準

以下をすべて達成したら初級クリア：

### 知識面
- [ ] 4層アーキテクチャの役割を説明できる
- [ ] Repository/Context Creator/Handler-Serviceパターンを説明できる
- [ ] よくあるエラー5つを即座に解決できる

### 実践面
- [ ] 既存コードを読んで処理フローを図解できる
- [ ] 簡単なバグ修正PRを1つ以上作成・マージした
- [ ] E2Eテストを実行・デバッグできる

### ツール操作
- [ ] `./gradlew spotlessApply`を使いこなせる
- [ ] ビルド・テストを実行できる
- [ ] PRを作成できる（コミットメッセージに`@codex review`含む）

---

## 🚀 次のステップ：役割に応じて選択

初級クリア後は、**あなたの役割に応じて**以下から選択してください：

### 🎯 管理API実装者（Control Plane）
テナント・ユーザー・クライアント等の**管理機能を実装**する場合：

→ [Control Plane Track](./02-control-plane-track.md) - システムレベルAPI・組織レベルAPI実装

**こんな人におすすめ**:
- SaaS管理画面のバックエンドを担当
- テナント管理・権限管理APIを実装
- CRUD操作が中心

### 🔐 認証フロー実装者（Application Plane）
OAuth/OIDC認証・トークン発行・**認証方式を実装**する場合：

→ [Application Plane Track](./03-application-plane-track.md) - Authorization Flow・Token Endpoint・認証インタラクター実装

**こんな人におすすめ**:
- ログイン機能・認証フローを担当
- 新しい認証方式（Passkey/FIDO2等）を追加
- OAuth/OIDC仕様に深く関わる

### 🚀 両方マスターしたい（Full Stack）
管理機能も認証フローも**両方実装できる**ようになりたい場合：

→ [Full Stack Track](./04-full-stack-track.md) - Control Plane + Application Plane 完全習得

**こんな人におすすめ**:
- 技術リーダーを目指す
- システム全体を理解したい
- アーキテクチャ設計に関わりたい

---

## 💡 学習のヒント

### つまずきやすいポイント

#### 1. Repository第一引数の理解
```java
// ❌ 間違いやすい
clientRepository.get(clientId);

// ✅ 正しい
clientRepository.get(tenant, clientId);
```
**理由**: マルチテナント分離のため

#### 2. Context Creator未使用
```java
// ❌ EntryServiceでDTO直接変換
ClientConfiguration config = new ClientConfiguration(...);

// ✅ Context Creator使用
ClientRegistrationContext context = creator.create();
```
**理由**: 層責任の分離

#### 3. Adapter層でビジネスロジック
```java
// ❌ Adapter層でビジネス判定
if ("ORGANIZER".equals(tenant.type())) { ... }

// ✅ Adapter層はSQLのみ
String sql = "SELECT * FROM ...";
return mapper.map(row);
```
**理由**: レイヤー責任違反

---

## 🔗 関連リソース

- [AI開発者向け: Use-Cases詳細](../content_10_ai_developer/ai-10-use-cases.md)
- [AI開発者向け: Core詳細](../content_10_ai_developer/ai-11-core.md)
- [開発者ガイドTOC](../DEVELOPER_GUIDE_TOC.md)

---

**最終更新**: 2025-10-12
**対象**: 新規参画開発者（1-2週間）
