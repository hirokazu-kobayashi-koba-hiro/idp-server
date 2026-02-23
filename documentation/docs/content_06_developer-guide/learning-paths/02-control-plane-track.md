# Control Plane Track（管理API実装者向け）

## 🎯 このトラックの目標

**管理API（Control Plane）の実装**ができるようになる。

- システムレベルAPI実装（CRUD操作）
- Handler-Serviceパターンの理解
- Repository実装（Query/Command分離）
- ContextBuilder作成
- 組織レベルAPI実装（4ステップアクセス制御）

**所要期間**: 2-4週間

**前提**: [初級ラーニングパス](./01-beginner.md)完了

---

## 📚 学習内容

### Week 1-2: システムレベルAPI

#### 読むべきドキュメント
- [ ] [Control Plane概要](../02-control-plane/01-overview.md)
- [ ] [システムレベルAPI実装ガイド](../02-control-plane/03-system-level-api.md)
- [ ] [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md)

#### 実装の参考
実際のコードを読んで理解：
- [ClientManagementEntryService.java](../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java)
- [ClientManagementHandler.java](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/ClientManagementHandler.java)
- [ClientCreationService.java](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/ClientCreationService.java)

#### チェックリスト
- [ ] Handler-Serviceパターンを理解している
- [ ] ContextBuilderを使える（withBefore/withAfter/build）
- [ ] Audit Logを記録できる
- [ ] Dry Run対応を実装できる
- [ ] Request/Response DTO（Map&lt;String, Object&gt;ベース）を作成できる
- [ ] Repository（Query/Command分離）を実装できる

---

### Week 3-4: 組織レベルAPI

#### 読むべきドキュメント
- [ ] [組織レベルAPI実装ガイド](../02-control-plane/04-organization-level-api.md)

#### 実装の参考
- [OrgClientManagementEntryService.java](../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgClientManagementEntryService.java)
- [OrgClientManagementHandler.java](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/OrgClientManagementHandler.java)

#### チェックリスト
- [ ] システムレベルとの差分を理解している
- [ ] **Serviceは再利用**（二重開発不要）
- [ ] OrgXxxManagementHandlerを実装できる
- [ ] 4ステップアクセス制御を理解している
- [ ] OrganizationAccessVerifierを使える

---

## ✅ 完了判定基準

以下をすべて達成したらControl Plane Trackクリア：

### 知識面
- [ ] Handler-Serviceパターン（EntryService/Handler/Service/Repositoryの責務分担）を説明できる
- [ ] ContextBuilderの役割を説明できる
- [ ] Repository第一引数がTenantである理由を説明できる
- [ ] RLS（Row Level Security）の仕組みを説明できる
- [ ] 4ステップアクセス制御を説明できる

### 実践面
- [ ] 新しいシステムレベルAPIをゼロから実装できる
- [ ] Repository（PostgreSQL + MySQL両対応）を実装できる
- [ ] 組織レベルAPIに拡張できる（Handlerのみ追加）
- [ ] E2Eテストを作成できる
- [ ] PRを出してレビューを受けられる

### コード品質
- [ ] Tenant第一引数を守れる
- [ ] Context Builderを必ず使う（TODOコメント禁止）
- [ ] Validator/Verifierは void + throw
- [ ] [コードレビューチェックリスト](../08-reference/code-review-checklist.md)を完全遵守

---

## 💡 Control Plane実装のヒント

### よくあるミス

#### 1. defaultメソッドをオーバーライド

```java
// ❌ 間違い: 不要なオーバーライド
@Override
public AdminPermissions getRequiredPermissions(String method) {
    // defaultメソッドで自動計算されるため実装不要
}

// ✅ 正しい: オーバーライドしない
// （インターフェースのdefaultメソッドが自動実行される）
```

#### 2. EntryServiceに複雑な処理を書く

```java
// ❌ 間違い: EntryServiceに全部書く
@Override
public XxxManagementResponse create(...) {
    Tenant tenant = tenantQueryRepository.get(...);
    // 権限チェック
    // Validation
    // 永続化
    // ...
}

// ✅ 正しい: Handlerに委譲
@Override
public XxxManagementResponse create(...) {
    XxxManagementResult result = handler.handle(...);
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);
    return result.toResponse(dryRun);
}
```

#### 3. Audit Logを忘れる

```java
// ❌ 間違い: Audit Log記録なし
@Override
public XxxManagementResponse create(...) {
    XxxManagementResult result = handler.handle(...);
    return result.toResponse(dryRun);
}

// ✅ 正しい: 必ずAudit Log記録
@Override
public XxxManagementResponse create(...) {
    XxxManagementResult result = handler.handle(...);
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);  // ← 必須
    return result.toResponse(dryRun);
}
```

---

## 🚀 次のステップ

Control Plane Track完了後の進路：

### Application Planeも学ぶ

→ [Application Plane Track](./03-application-plane-track.md) - OAuth/OIDC認証フロー実装

**こんな人におすすめ**:
- 認証フロー実装も担当する
- OAuth/OIDCを深く理解したい

### Full Stack開発者へ

→ [Full Stack Track](./04-full-stack-track.md) - Control Plane + Application Plane 完全習得

**こんな人におすすめ**:
- 技術リーダーを目指す
- システム全体を理解したい

---

---

**最終更新**: 2025-12-18
**対象**: Control Plane実装者（2-4週間）
**習得スキル**: 管理API実装、Handler-Serviceパターン、Repository実装、4ステップアクセス制御
