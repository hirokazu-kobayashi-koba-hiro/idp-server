# GitHub Issues List - idp-server

## Open Issues (47件)

### 最近作成 (2025-10-16 ~ 2025-10-18)
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #731 | Add comprehensive database schema documentation | documentation, enhancement | 2025-10-18 |
| #729 | preferred_username uniqueness issue with multiple identity providers | bug, enhancement | 2025-10-18 |
| #728 | Refactor authentication_device_rule from AuthenticationPolicy to Tenant-level configuration | enhancement | 2025-10-18 |
| #716 | [Enhancement] Support HTTP 200 error response pattern in HttpRequestExecutor | - | 2025-10-16 |
| #715 | [Security] Implement account lockout mechanism | - | 2025-10-16 |
| #714 | [Security] Implement security headers in HTTP responses | - | 2025-10-16 |
| #713 | [Security] Upgrade Spring Boot to 3.4.5+ | - | 2025-10-16 |
| #712 | [Security] Implement SSRF protection in HttpRequestExecutor | - | 2025-10-16 |
| #711 | [Security] Implement rate limiting for OAuth endpoints | - | 2025-10-16 |
| #710 | [Security] Fix CORS Origin validation vulnerability | - | 2025-10-16 |
| #709 | [Security] Update Docker base images to Alpine-based secure images | - | 2025-10-16 |

### ドキュメント関連
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #731 | Add comprehensive database schema documentation | documentation, enhancement | 2025-10-18 |
| #688 | How-toドキュメントの整合性チェックと改善 | documentation | 2025-10-14 |
| #661 | GDPR Compliance Verification Checklist | documentation | 2025-10-09 |
| #591 | content_06_developer-guide/developer-guide: 欠けている開発者ガイドドキュメント（20件以上） | - | 2025-10-02 |

### セキュリティ関連
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #715 | [Security] Implement account lockout mechanism | - | 2025-10-16 |
| #714 | [Security] Implement security headers in HTTP responses | - | 2025-10-16 |
| #713 | [Security] Upgrade Spring Boot to 3.4.5+ | - | 2025-10-16 |
| #712 | [Security] Implement SSRF protection in HttpRequestExecutor | - | 2025-10-16 |
| #711 | [Security] Implement rate limiting for OAuth endpoints | - | 2025-10-16 |
| #710 | [Security] Fix CORS Origin validation vulnerability | - | 2025-10-16 |
| #709 | [Security] Update Docker base images to Alpine-based secure images | - | 2025-10-16 |
| #641 | 脆弱性診断_20251009 | - | 2025-10-09 |
| #638 | ログアウト時にトークンリボーク(Revocation)が実行されていない - セキュリティリスク | - | 2025-10-09 |
| #247 | Vault導入による機密情報管理の強化提案 | - | 2025-07-25 |

### リファクタリング・アーキテクチャ
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #728 | Refactor authentication_device_rule from AuthenticationPolicy to Tenant-level configuration | enhancement | 2025-10-18 |
| #635 | Refactor: Extract common preferred_username auto-assignment logic | - | 2025-10-08 |
| #557 | Enhance Plugin System: Adopt Keycloak-inspired unified Factory pattern for all extensions | - | 2025-10-01 |
| #453 | Separate responsibilities between JsonSchema validation and business rule verification | - | 2025-09-16 |
| #428 | DI Architecture and Application Initialization Improvements | enhancement | 2025-09-09 |
| #420 | コントロールプレーン機能のアプリケーション本体からの分離検討 | - | 2025-09-08 |
| #419 | セキュリティイベントフックのPub/Sub型アーキテクチャへの改善 | - | 2025-09-08 |
| #418 | イベントログ永続化方式の選択を設定で切り替え可能にする | - | 2025-09-08 |
| #417 | SSO用のセッション共有単位を設定ベースにする | - | 2025-09-08 |
| #415 | 単体テスト実装方針策定とロジック見直しの実施 | - | 2025-09-08 |
| #414 | TODO撲滅運動を開始する | - | 2025-09-08 |

### 機能追加・改善
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #716 | [Enhancement] Support HTTP 200 error response pattern in HttpRequestExecutor | - | 2025-10-16 |
| #687 | テナント作成時にデフォルト認証設定を自動生成 | - | 2025-10-13 |
| #663 | Add Authorization Grant Management APIs (Resource Owner, Organization-level, System-level) | enhancement | 2025-10-09 |
| #550 | [改善]身元確認申し込みのAPIに順序性を担保する仕組みを追加する | - | 2025-10-01 |
| #544 | [仕様検討]外部APIのレスポンスのステータスコードとidp-serverが返却するステータスコードのマッピング方針を決める必要がある | - | 2025-09-30 |
| #541 | [改善]セキュリティイベント検索 & セキュリティイベントフック APIの検索条件の拡張 | - | 2025-09-30 |
| #519 | [改善]認証デバイスの部分更新APIでスコープの制御を追加する | - | 2025-09-26 |
| #467 | マッパーシステム: バリデーション関数の実装 (メール形式、正規表現チェック) | - | 2025-09-21 |
| #466 | マッパーシステム: 数値演算関数の実装 (四則演算、丸め処理) | - | 2025-09-21 |
| #448 | [改善]ユーザー招待機能が仮実装になっているので正式実装する | - | 2025-09-16 |
| #441 | [改善]テナントに関する統計データを集計する機能及びAPI | - | 2025-09-13 |
| #439 | 暗号化キーローテーション機能の実装 | enhancement | 2025-09-13 |
| #416 | トークン管理APIの設計・実装 | - | 2025-09-08 |
| #395 | [改善] RAR(`authorization_details`)で通知テンプレ&チャンネルを動的切替できるようにする | - | 2025-09-06 |
| #298 | [改善]認証処理でリクエストのバリデーション、pre_hook、post_hook、store、responseを定義できるようにする | - | 2025-08-11 |
| #265 | [改善]コントロールプレーンの各種一覧APIでCSV出力機能を追加する | - | 2025-07-30 |

### バグ
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #729 | preferred_username uniqueness issue with multiple identity providers | bug, enhancement | 2025-10-18 |
| #532 | Fix audit log retry mechanism infinite loop and CPU consumption | - | 2025-09-27 |

### 性能・パフォーマンス
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #529 | 監査ログ発行タイミングの改善 (Audit Log Publishing Timing Improvements) & 実行結果の記録 | - | 2025-09-27 |
| #524 | 身元確認申込みAPIの性能劣化の懸念 | - | 2025-09-26 |

### エラーハンドリング
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #454 | Standardize error response formats across all APIs | - | 2025-09-16 |

### プラットフォーム対応
| # | タイトル | ラベル | 作成日 |
|---|---------|--------|--------|
| #596 | Support to JDK25 | - | 2025-10-03 |

---

## カテゴリ別サマリー

| カテゴリ | 件数 | 優先度の考慮 |
|---------|------|------------|
| セキュリティ | 10 | ⚠️ **最優先** |
| ドキュメント | 4 | 🔵 高 |
| リファクタリング・アーキテクチャ | 11 | 🟢 中 |
| 機能追加・改善 | 16 | 🟢 中 |
| バグ | 2 | 🔴 高 |
| 性能・パフォーマンス | 2 | 🟡 中～高 |
| エラーハンドリング | 1 | 🟢 中 |
| プラットフォーム対応 | 1 | 🟡 低～中 |

---

## 推奨マイルストーン案

### 🛡️ v1.1 - Security & Compliance (最優先)
- すべてのセキュリティ関連Issue (#709-#715, #638, #641, #661, #247)
- 期限: 2025-Q2

### 📚 v1.2 - Documentation
- ドキュメント関連Issue (#731, #688, #591)
- 期限: 2025-Q2

### 🐛 v1.3 - Bug Fixes & Stability
- バグ修正 (#729, #532)
- 性能改善 (#529, #524)
- エラーハンドリング (#454)
- 期限: 2025-Q2

### 🔧 v1.4 - Refactoring & Architecture
- リファクタリング関連 (#728, #635, #557, #453, #428, #420, #419, #418, #417, #415, #414)
- 期限: 2025-Q3

### ✨ v1.5 - Features
- 機能追加・改善 (#716, #687, #663, #550, #544, #541, #519, #467, #466, #448, #441, #439, #416, #395, #298, #265)
- 期限: 2025-Q3-Q4

### 🚀 v2.0 - Future
- プラットフォーム対応 (#596)
- 期限: 2025-Q4

---

**合計Open Issues**: 47件
**最終更新**: 2025-10-18
