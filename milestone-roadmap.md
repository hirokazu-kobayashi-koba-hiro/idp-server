# Milestone Roadmap - idp-server

## リリース戦略

### v0.9.0 - Stable (2025-12月末 / 2026-Q1初旬)
**目的**: 本番運用可能な安定版リリース
**期間**: 2-3ヶ月

### v1.0.0 - GA (General Availability) (2026-Q1末)
**目的**: 正式版リリース（機能完全性とエンタープライズ対応）
**期間**: v0.9.0リリース後 1-2ヶ月

### v1.1.0 - Enhancement (2026-Q2)
**目的**: 機能拡張とユーザー体験向上
**期間**: v1.0.0リリース後 2-3ヶ月

---

## 🎯 v0.9.0 - Stable (最優先)

### 必須要件: セキュリティ修正（ブロッカー）
**目的**: 本番運用に耐える最低限のセキュリティレベル確保

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #713 | [Security] Upgrade Spring Boot to 3.4.5+ | 🔴 Critical | CVE対応必須 |
| #710 | [Security] Fix CORS Origin validation vulnerability | 🔴 Critical | セキュリティホール |
| #712 | [Security] Implement SSRF protection in HttpRequestExecutor | 🔴 Critical | 外部API連携のセキュリティ |
| #638 | ログアウト時にトークンリボーク(Revocation)が実行されていない | 🔴 Critical | セキュリティリスク |
| #734 | **[Security] Verify Row Level Security (RLS) completeness** | 🔴 Critical | **マルチテナント分離の根幹** |
| #735 | **[Security] Verify complete removal of default credentials** | 🔴 Critical | **デフォルト認証情報排除確認** |

**想定工数**: 3-4週間

---

### 必須要件: 致命的バグ修正
**目的**: システム安定性の確保

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #729 | preferred_username uniqueness issue with multiple identity providers | 🔴 Critical | データ整合性の問題 |
| #532 | Fix audit log retry mechanism infinite loop and CPU consumption | 🔴 Critical | システムリソース枯渇リスク |

**想定工数**: 1-2週間

---

### 必須要件: 最低限のドキュメント整備
**目的**: 運用・保守が可能なレベルのドキュメント提供

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #731 | Add comprehensive database schema documentation | 🟠 High | DB運用に必須 |
| #688 | How-toドキュメントの整合性チェックと改善 | 🟠 High | ユーザーが使えるように |

**想定工数**: 2週間

---

### 推奨: セキュリティ強化（可能なら含める）
**目的**: より安全な本番運用

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #709 | [Security] Update Docker base images to Alpine-based secure images | 🟡 Medium | セキュアなベースイメージ |
| #714 | [Security] Implement security headers in HTTP responses | 🟡 Medium | セキュリティベストプラクティス |
| #736 | **[Security] Implement session fixation attack prevention** | 🟠 High | **セッション固定攻撃対策** |

**想定工数**: 1-2週間

---

### 推奨: パフォーマンス改善
**目的**: 本番運用時の性能担保

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #529 | 監査ログ発行タイミングの改善 & 実行結果の記録 | 🟡 Medium | パフォーマンス改善 |
| #524 | 身元確認申込みAPIの性能劣化の懸念 | 🟡 Medium | パフォーマンス担保 |

**想定工数**: 1-2週間

---

### 推奨: 運用・品質保証
**目的**: 本番運用の安全性・品質担保

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #737 | **[Operations] Define and document database migration strategy** | 🟠 High | **DBマイグレーション運用戦略** |
| #738 | **[Testing] Improve E2E test coverage for core use cases** | 🟠 High | **E2Eテスト網羅性向上** |

**想定工数**: 2-3週間

---

**v0.9.0 合計想定工数**: 9-13週間（2-3ヶ月）
**必須Issue**: 8件（セキュリティ6件、バグ2件）
**推奨Issue**: 7件（セキュリティ1件、パフォーマンス2件、運用2件、ドキュメント2件）

---

## 🚀 v1.0.0 - GA (General Availability)

### 必須要件: エンタープライズセキュリティ
**目的**: エンタープライズ環境で求められるセキュリティレベル

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #715 | [Security] Implement account lockout mechanism | 🔴 Critical | ブルートフォース対策 |
| #711 | [Security] Implement rate limiting for OAuth endpoints | 🔴 Critical | DoS対策 |
| #661 | GDPR Compliance Verification Checklist | 🟠 High | コンプライアンス対応 |
| #247 | Vault導入による機密情報管理の強化提案 | 🟠 High | エンタープライズ要件 |

**想定工数**: 3-4週間

---

### 必須要件: 運用必須機能
**目的**: 本番運用に必要な管理機能

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #416 | トークン管理APIの設計・実装 | 🔴 Critical | トークンライフサイクル管理 |
| #663 | Add Authorization Grant Management APIs | 🟠 High | 認可管理機能 |
| #687 | テナント作成時にデフォルト認証設定を自動生成 | 🟠 High | 運用効率化 |
| #741 | **[Feature] Implement password change API for resource owners** | 🟠 High | **ユーザーパスワード変更機能** |
| #742 | **[Feature] Add API to unassign roles from users** | 🟡 Medium | **ロール割り当て解除API** |

**想定工数**: 4-6週間

---

### 必須要件: ドキュメント完全性
**目的**: GAリリースに必要な完全なドキュメント

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #591 | content_06_developer-guide: 欠けている開発者ガイドドキュメント（20件以上） | 🔴 Critical | 開発者体験 |

**想定工数**: 3-4週間

---

### 推奨: エラーハンドリング標準化
**目的**: API品質の統一

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #454 | Standardize error response formats across all APIs | 🟡 Medium | API一貫性 |
| #544 | 外部APIレスポンスとステータスコードのマッピング方針決定 | 🟡 Medium | エラーハンドリング標準化 |

**想定工数**: 2週間

---

### 推奨: 重要リファクタリング
**目的**: コード品質と保守性向上

| # | タイトル | 優先度 | 理由 |
|---|---------|--------|------|
| #728 | Refactor authentication_device_rule from AuthenticationPolicy to Tenant-level | 🟡 Medium | 設計改善 |
| #635 | Refactor: Extract common preferred_username auto-assignment logic | 🟡 Medium | コード重複解消 |
| #453 | Separate JsonSchema validation and business rule verification | 🟡 Medium | 責務分離 |

**想定工数**: 2-3週間

---

**v1.0.0 合計想定工数**: 13-17週間（3-4ヶ月）
**ただしv0.9.0と並行作業可能な部分あり**
**必須Issue**: 8件
**推奨Issue**: 5件

---

## ✨ v1.1.0 - Enhancement (GA後の改善)

### 機能拡張
**目的**: ユーザー価値向上

| # | タイトル | カテゴリ | 優先度 |
|---|---------|---------|--------|
| #716 | Support HTTP 200 error response pattern in HttpRequestExecutor | 外部連携 | 🟢 Low |
| #550 | 身元確認申し込みAPIに順序性を担保する仕組み追加 | 身元確認 | 🟢 Low |
| #541 | セキュリティイベント検索APIの検索条件拡張 | セキュリティイベント | 🟢 Low |
| #519 | 認証デバイスの部分更新APIでスコープ制御追加 | 認証 | 🟢 Low |
| #467 | マッパーシステム: バリデーション関数実装 | マッピング | 🟢 Low |
| #466 | マッパーシステム: 数値演算関数実装 | マッピング | 🟢 Low |
| #448 | ユーザー招待機能の正式実装 | ユーザー管理 | 🟢 Low |
| #441 | テナント統計データ集計機能及びAPI | 分析 | 🟢 Low |
| #439 | 暗号化キーローテーション機能実装 | セキュリティ | 🟡 Medium |
| #395 | RARで通知テンプレ&チャンネル動的切替 | 通知 | 🟢 Low |
| #298 | 認証処理でpre_hook/post_hook/store/response定義可能に | 認証拡張 | 🟢 Low |
| #265 | コントロールプレーン各種一覧APIでCSV出力機能 | 運用 | 🟢 Low |

---

### アーキテクチャ改善
**目的**: 長期的な保守性向上

| # | タイトル | カテゴリ | 優先度 |
|---|---------|---------|--------|
| #557 | Plugin System: Keycloak-inspired unified Factory pattern | アーキテクチャ | 🟡 Medium |
| #428 | DI Architecture and Application Initialization Improvements | アーキテクチャ | 🟡 Medium |
| #420 | コントロールプレーン機能のアプリケーション本体からの分離 | アーキテクチャ | 🟢 Low |
| #419 | セキュリティイベントフックのPub/Sub型アーキテクチャ改善 | アーキテクチャ | 🟢 Low |
| #418 | イベントログ永続化方式を設定で切替可能に | 設定 | 🟢 Low |
| #417 | SSO用セッション共有単位を設定ベースに | 設定 | 🟢 Low |
| #415 | 単体テスト実装方針策定とロジック見直し | テスト | 🟡 Medium |
| #414 | TODO撲滅運動 | コード品質 | 🟢 Low |

---

### 将来対応（v1.1.0以降で検討）
**目的**: 長期ロードマップ

| # | タイトル | カテゴリ | 優先度 |
|---|---------|---------|--------|
| #596 | Support to JDK25 | プラットフォーム | 🔵 Future |
| #641 | 脆弱性診断_20251009 | セキュリティ診断 | 🟡 Medium (診断結果による) |

---

## 📊 バージョン別サマリー

| バージョン | 必須Issue | 推奨Issue | 想定工数 | リリース目標 |
|-----------|----------|----------|---------|------------|
| **v0.9.0 - Stable** | 8件 | 7件 | 9-13週間 | 2025-12-31 |
| **v1.0.0 - GA** | 10件 | 5件 | 14-19週間 | 2026-03-31 |
| **v1.1.0 - Enhancement** | 20件 | - | 12-16週間 | 2026-06-30 |

---

## 🎯 クリティカルパス

### Phase 1: v0.9.0準備 (Week 1-10)
1. **Week 1-3**: セキュリティ修正4件 (#713, #710, #712, #638)
2. **Week 4-5**: 致命的バグ修正2件 (#729, #532)
3. **Week 6-7**: ドキュメント整備2件 (#731, #688)
4. **Week 8-9**: セキュリティ強化2件 (#709, #714) ※並行可能
5. **Week 9-10**: パフォーマンス改善2件 (#529, #524) ※並行可能

**v0.9.0 リリース**: Week 10末 (2025-12-31目標)

---

### Phase 2: v1.0.0準備 (Week 11-24)
v0.9.0リリース後、以下を並行実施:

1. **Week 11-14**: エンタープライズセキュリティ4件 (#715, #711, #661, #247)
2. **Week 15-18**: 運用必須機能3件 (#416, #663, #687)
3. **Week 19-22**: ドキュメント完全性1件 (#591)
4. **Week 20-24**: エラーハンドリング・リファクタリング5件 ※並行可能

**v1.0.0 GA リリース**: Week 24末 (2026-03-31目標)

---

### Phase 3: v1.1.0準備 (Week 25-40)
v1.0.0リリース後、機能拡張と長期改善を実施

**v1.1.0 リリース**: Week 40末 (2026-06-30目標)

---

## ✅ リリース判定基準

### v0.9.0 - Stable
- [ ] セキュリティ修正4件すべて完了
- [ ] 致命的バグ2件すべて完了
- [ ] DB運用ドキュメント完備
- [ ] How-toドキュメント整合性確認完了
- [ ] E2Eテスト全Pass
- [ ] 本番環境での動作確認完了

### v1.0.0 - GA
- [ ] v0.9.0の全要件満たす
- [ ] エンタープライズセキュリティ要件満たす
- [ ] トークン管理API実装完了
- [ ] 認可管理API実装完了
- [ ] 開発者ガイド完全版完成
- [ ] GDPR準拠確認完了
- [ ] パフォーマンステスト完了（目標スループット達成）

### v1.1.0 - Enhancement
- [ ] v1.0.0の全要件満たす
- [ ] 機能拡張12件のうち80%以上完了
- [ ] アーキテクチャ改善計画策定完了
- [ ] 次期バージョンロードマップ作成

---

**最終更新**: 2025-10-18
**作成者**: Claude Code
