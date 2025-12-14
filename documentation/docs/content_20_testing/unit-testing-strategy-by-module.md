# Unit Testing Strategy by Gradle Module

**Issue #415 対応**: 各Gradleプロジェクト単位の単体テスト戦略とガイドライン

---

## 📋 全モジュール一覧・テスト戦略

### 🎯 優先度別分類 (全20モジュール)

| Priority | Module | 責任範囲 | カバレッジ目標 | テスト対象 |
|----------|--------|----------|---------------|------------|
| **🔴 Critical** | **idp-server-core** | OAuth/OIDC プロトコル実装 | **85%** | Validator, Handler, Service層 |
| **🔴 Critical** | **idp-server-platform** | 共通基盤・暗号化 | **80%** | JOSE/JWT, JSON処理, Utils |
| **🔴 Critical** | **idp-server-use-cases** | アプリケーションユースケース | **80%** | ビジネスフロー, ドメインサービス |
| **🟡 High** | **idp-server-authentication-interactors** | 認証方式実装 | **75%** | Password, Email, WebAuthn, MFA |
| **🟡 High** | **idp-server-control-plane** | 管理API・テナント管理 | **70%** | CRUD操作, 権限制御 |
| **🟢 Medium** | **idp-server-core-extension-fapi** | FAPI拡張仕様 | **70%** | FAPI Baseline/Advanced準拠 |
| **🟢 Medium** | **idp-server-core-extension-ciba** | CIBA仕様 | **70%** | Backchannel認証フロー |
| **🟢 Medium** | **idp-server-core-extension-pkce** | PKCE仕様 | **70%** | Code Challenge/Verifier |
| **🟢 Medium** | **idp-server-core-extension-verifiable-credentials** | VC仕様 | **70%** | 証明書発行・検証 |
| **🟢 Medium** | **idp-server-core-extension-ida** | Identity Assurance | **70%** | verified_claims処理 |
| **🟢 Medium** | **idp-server-webauthn4j-adapter** | WebAuthn統合 | **60%** | FIDO2認証処理 |
| **🟢 Medium** | **idp-server-email-aws-adapter** | AWS SES統合 | **60%** | メール送信処理 |
| **🟢 Medium** | **idp-server-notification-fcm-adapter** | FCM統合 | **60%** | プッシュ通知処理 |
| **🟢 Medium** | **idp-server-notification-apns-adapter** | APNs統合 | **60%** | iOS通知処理 |
| **🟢 Medium** | **idp-server-federation-oidc** | OIDC連携 | **60%** | 外部IdP統合 |
| **🟢 Medium** | **idp-server-security-event-framework** | セキュリティイベント基盤 | **65%** | イベント処理・配信 |
| **🟢 Medium** | **idp-server-security-event-hooks** | セキュリティフック | **65%** | Webhook・通知連携 |
| **🔵 Low** | **idp-server-core-adapter** | コアアダプター | **50%** | Repository実装 |
| **🔵 Low** | **idp-server-database** | DB スキーマ・マイグレーション | **40%** | Flyway, RLS動作確認 |
| **🔵 Low** | **idp-server-springboot-adapter** | Spring Boot統合 | **50%** | REST API, DI設定 |

---

## 📈 段階的実装計画 (8週間)

### Phase 1: Foundation (Week 1-2) - 35 test classes
| Module | Test Classes | Focus Area |
|--------|-------------|------------|
| **idp-server-platform** | 15 | JOSE/JWT, JSON処理, 暗号化 |
| **idp-server-core** | 20 | OAuth/OIDCバリデーター, ハンドラー |

**Target**: Core modules reach 40% coverage

### Phase 2: Business Logic (Week 3-4) - 30 test classes
| Module | Test Classes | Focus Area |
|--------|-------------|------------|
| **idp-server-use-cases** | 12 | ユースケース, ドメインサービス |
| **idp-server-authentication-interactors** | 18 | 認証方式, MFA処理 |

**Target**: Business modules reach 60% coverage

### Phase 3: Management & Extensions (Week 5-6) - 27 test classes
| Module | Test Classes | Focus Area |
|--------|-------------|------------|
| **idp-server-control-plane** | 15 | 管理API, テナント管理 |
| **Extension modules** (FAPI, CIBA, PKCE, VC, IDA) | 12 | プロトコル拡張仕様 |

**Target**: All critical paths covered

### Phase 4: Integration & Adapters (Week 7-8) - 18 test classes
| Module | Test Classes | Focus Area |
|--------|-------------|------------|
| **Adapter modules** (WebAuthn, Email, FCM, APNs) | 10 | 外部サービス統合 |
| **Spring Boot integration** | 8 | REST API, 設定管理 |

**Target**: Overall project reaches 65-75% coverage

---

## 🎯 テスト品質基準

### English Test Naming Convention
```java
// ✅ Recommended Pattern
@Test
void should_{expected_result}_when_{condition}()

@ParameterizedTest
void should_throw_{exception}_when_{invalid_condition}()
```

### Module-Specific Test Focus

#### 🔴 Critical Modules
- **Pure unit tests** for business logic
- **Integration tests** for protocol compliance
- **Security-focused tests** for crypto functions

#### 🟡 High Priority Modules
- **Authentication flow tests** with various scenarios
- **Management API tests** with access control validation

#### 🟢 Medium Priority Modules
- **Extension protocol tests** for RFC compliance
- **Adapter integration tests** with external service mocks

#### 🔵 Low Priority Modules
- **Configuration tests** for Spring Boot integration
- **Migration tests** for database schema changes

---

## 🚀 成功指標

### カバレッジ目標 (Module Type別)
- **Critical (Core Business)**: 75-85%
- **High (Auth & Management)**: 70-80%
- **Medium (Extensions & Adapters)**: 60-70%
- **Low (Infrastructure)**: 40-60%
- **全体目標**: 65-75%

### 品質指標
- **Test Execution Time**: \<5 minutes (all unit tests)
- **Test Stability**: \>99% success rate (flaky tests \<1%)
- **Build Success Rate**: \>95%
- **CI/CD Integration**: Automated coverage reporting

### 実装工数見積もり
- **Total Test Classes**: ~110 classes
- **Total Implementation**: 8 weeks
- **Weekly Target**: 13-14 test classes
- **Daily Target**: 2-3 test classes

---

## 📊 モジュール依存関係マップ

```
🔴 idp-server-core
├── 🔴 idp-server-platform (foundation)
└── 🔴 idp-server-use-cases
    ├── 🟡 idp-server-authentication-interactors
    ├── 🟡 idp-server-control-plane
    ├── 🟢 Extension modules (FAPI, CIBA, PKCE, VC, IDA)
    ├── 🟢 Adapter modules (WebAuthn, Email, FCM, APNs)
    └── 🔵 Infrastructure modules (Database, Spring Boot)
```

**テスト戦略**: 依存関係の下位モジュールから上位に向けて段階的に実装

この戦略により、idp-serverは**全20モジュールを体系的にカバー**し、各層の責任に応じた最適なテスト品質を実現します。