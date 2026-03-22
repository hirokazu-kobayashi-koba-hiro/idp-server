# E2E カバレッジ GAP 分析 & 組込み戦略

## 実施結果 (2026-03-22)

### Phase 1（基本フローのE2E化）— 完了

| ファイル | テスト数 | カバー |
|---------|---------|--------|
| `standard/standard-10-basic-auth-flow.test.js` | 3 | Discovery, 登録, Token, UserInfo, Refresh, スコープ別クレーム |
| `standard/standard-11-password-management.test.js` | 2 | パスワード変更, 管理者リセット |
| `standard/standard-12-token-introspection-m2m.test.js` | 3 | M2M client_credentials, Introspection, Revocation |
| `mfa/mfa-10-email-otp-mfa-flow.test.js` | 1 | Email MFA全フロー, amr確認 |
| `mfa/mfa-11-sms-otp-mfa-flow.test.js` | 1 | SMS MFA全フロー, amr確認 |
| `device-credential/device-credential-04` (追記) | +2箇所 | authorization_pending, CIBA UserInfo |
| `advance/advance-10-external-password-auth.test.js` | 2 | 外部認証, 認証失敗, 同一ユーザー解決 |
| eKYC: 既存14テストでカバー済み | - | 新規不要 |

### Phase 2（設定変更テスト）— 完了

| ファイル | テスト数 | カバー |
|---------|---------|--------|
| `standard/standard-20-password-policy.test.js` | 3 | B-01: min_length, require_uppercase, require_number |
| `standard/standard-21-claims-and-token-config.test.js` | 3 | B-04: claims_supported, B-05: AT期限切れ→RT復活, B-14: scope制限 |
| `standard/standard-22-grant-redirect-validation.test.js` | 2 | B-15: grant_types制限, B-16: redirect_uri検証 |
| `standard/standard-23-access-token-jwt.test.js` | 2 | B-19: JWT AT+JWKS, B-20: Token Revocation |
| `standard/standard-24-auth-timing.test.js` | 2 | B-07: 認可コード期限切れ, B-08: default_max_age |
| `standard/standard-25-client-auth-response-type.test.js` | 2 | B-17: auth_method=none, B-18: response_types制限 |
| `standard/standard-26-pkce.test.js` | 3 | B-13: PKCE正常/不正verifier/PKCE無し |
| `mfa/mfa-20-email-otp-config.test.js` | 3 | C-08: OTP期限切れ, C-09: リトライ制限, C-10: 旧コード無効化 |
| `mfa/mfa-21-auth-policy-scope-condition.test.js` | 3 | C-12: スコープ条件付きMFA |
| `ciba/ciba-20-config-effects.test.js` | 2 | B-22: CIBA期限切れ, B-25: login_hint形式 |
| `ciba/ciba-21-access-denied.test.js` | 2 | B-24: failure_conditions→access_denied |
| `ciba/ciba-22-polling-and-binding.test.js` | 2 | B-21: ポーリング間隔, B-26: binding_message |
| `ciba/ciba-23-user-code-required.test.js` | 1 | B-23: ユーザーコード必須化 |
| `advance/advance-11-custom-claims-scope-mapping.test.js` | 2 | B-11: custom_claims_scope_mapping on/off |
| `advance/advance-12-external-auth-config-effects.test.js` | 2 | B-27: 接続エラー(503), B-29: provider_id別ユーザー |

### その他の成果物

- **Mockoon**: `POST /email/send`, `POST /sms/send` エンドポイント追加
- **スキル更新**: `use-case-mfa`(OTP 2層アーキテクチャ), `spec-external-integration`(例外→ステータスコードマッピング)
- **合計**: 22スイート / 51テスト（新規19ファイル + 既存追記2箇所）

---

## 現状のテスト体制

| レイヤー | ファイル数 | 役割 |
|---------|----------|------|
| **verify.sh** (12本) | 各ユースケースに1本 | setup.sh後の基本フロー検証。curlベース。手動/自動両用 |
| **E2E Jest** (31本) | `e2e/src/tests/usecase/` | Issue起因の回帰テスト、高度な組合せテスト |
| **VERIFY.md** (12本) | ドキュメント | verify.shの手順書（手動確認ガイド） |
| **EXPERIMENTS** (8本) | ドキュメント | 設定変更×挙動確認の実験ガイド |
| **VERIFY-CONFIG-CHANGES** (4本) | ドキュメント | 設定値変更の動作確認手順書 |

### テスト体制の課題

1. **verify.sh** は基本フローをカバーするが、CI/CDに統合されていない（setup.sh依存）
2. **E2E Jest** はIssue回帰テストに偏り、ユースケース横断の基本フロー検証が薄い
3. **EXPERIMENTS/VERIFY-CONFIG-CHANGES** は完全に手動（自動化されていない）

---

## GAP一覧

### 凡例

- **verify.sh**: ✅ カバー / ❌ 未カバー
- **E2E Jest**: ✅ カバー / ⚠️ 部分的 / ❌ 未カバー
- **優先度**: P0(必須) / P1(重要) / P2(あると良い) / P3(Nice-to-have)

---

### GAP-A: 基本認証フロー（verify.shでカバー済み → E2E化候補）

verify.shで検証済みだが、E2E Jestに移植すべきもの。
CI/CDで常時回帰テストできるようになる。

| ID | ユースケース | verify.shカバー内容 | E2E Jest | 優先度 |
|----|------------|-------------------|----------|--------|
| A-01 | **login-password-only 基本フロー** | Discovery→登録→認証→同意→Token→UserInfo→Refresh | ❌ | P0 |
| A-02 | **login-password-only パスワード変更** | `POST /v1/me/password/change` + 変更後ログイン | ❌ | P1 |
| A-03 | **login-password-only 管理者パスワードリセット** | Management API `PUT /users/{sub}/password` + リセット後ログイン | ❌ | P1 |
| A-04 | **mfa-email MFAフロー** | Email OTPチャレンジ→検証コード取得→OTP検証→パスワード認証→Token(amr確認) | ❌ | P0 |
| A-05 | **mfa-email パスワードリセット** | password:resetスコープ→Email認証のみ→パスワードリセット | ⚠️ `mfa-01`(部分的) | P1 |
| A-06 | **mfa-sms MFAフロー** | SMS OTPチャレンジ→検証コード取得→OTP検証→パスワード認証→Token(amr確認) | ❌ | P0 |
| A-07 | **ciba 全フロー** | 登録→FIDO-UAF登録→CIBA BC Request→poll(pending)→デバイス認証→Token→UserInfo | ❌ | P0 |
| A-08 | **ekyc 身元確認フロー** | Apply→Evaluate Result→ステータス確認→結果取得 | ❌ | P1 |
| A-09 | **ekyc verified_claims** | claimsパラメータ付き認可→ID Tokenにverified_claims含む | ❌ | P1 |
| A-10 | **external-password-auth フロー** | 外部サービス疎通→外部認証パスワード認証→Token→UserInfo(マッピング確認) | ❌ | P1 |
| A-11 | **external-password-auth 認証失敗** | 不正パスワード→401 | ❌ | P1 |
| A-12 | **enterprise Webhook/SSFフック** | フック登録→認証でイベント発火→Mock受信確認→フック削除 | ⚠️ `advance-02`(Webhook複数のみ) | P1 |
| A-13 | **enterprise セキュリティイベント永続化** | イベント一覧/フィルタ/詳細、フック実行結果、テナント統計 | ⚠️ `standard-01`(統計のみ) | P1 |
| A-14 | **id-service-migration 全フロー** | 外部パスワード認証+デバイスマッピング→CIBA(email/device hint)→binding_message→FIDO-UAF | ❌ | P1 |
| A-15 | **third-party M2M** | client_credentials Grant→Token Introspection(active:true) | ❌ | P1 |
| A-16 | **third-party Web Client** | 認可コードフロー(client_secret_basic)→Introspection→Refresh | ❌ | P1 |
| A-17 | **financial-grade FAPI設定検証** | Discovery: mTLS, require_signed_request_object, PAR, JARM, CIBA, FAPIスコープ | ⚠️ `financial-grade-00`(部分的) | P1 |

---

### GAP-B: EXPERIMENTS実験（完全未カバー → E2E化候補）

設定変更→挙動変化の検証。動的にテナント/認可サーバー/クライアント設定を変更してテスト。

#### B-1: テナント設定系

| ID | 実験テーマ | 元ファイル | 優先度 |
|----|----------|----------|--------|
| B-01 | パスワードポリシー強化(min_length, require_uppercase等) | login-password-only/EXPERIMENTS-basics Exp1 | P1 |
| B-02 | セッション有効期限(timeout_seconds) | login-password-only/EXPERIMENTS-basics Exp5 | P2 |
| B-03 | identity_unique_key_type(EMAIL vs EXTERNAL_USER_ID) | external-password-auth/EXPERIMENTS Exp12 | P2 |

#### B-2: 認可サーバー設定系

| ID | 実験テーマ | 元ファイル | 優先度 |
|----|----------|----------|--------|
| B-04 | claims_supported制御 → UserInfo返却値変化 | login-password-only/EXPERIMENTS-basics Exp3 | P1 |
| B-05 | AT有効期限短縮 → 期限切れ401 → RT復活 | login-password-only/EXPERIMENTS-basics Exp4 | P1 |
| B-06 | ID Token有効期限(id_token_duration) | login-password-only/EXPERIMENTS-auth-server Exp1 | P2 |
| B-07 | 認可コード有効期限(authorization_code_valid_duration) | login-password-only/EXPERIMENTS-auth-server Exp2 | P2 |
| B-08 | default_max_age → 再認証要求 | login-password-only/EXPERIMENTS-auth-server Exp3 | P2 |
| B-09 | 認可リクエスト有効期限 | login-password-only/EXPERIMENTS-auth-server Exp5 | P2 |
| B-10 | id_token_strict_mode | login-password-only/EXPERIMENTS-auth-server Exp6 | P1 |
| B-11 | custom_claims_scope_mapping | login-password-only/EXPERIMENTS-auth-server Exp10, ciba/EXPERIMENTS-auth-server Exp5 | P1 |
| B-12 | scopes_supported は Discovery表示専用 | login-password-only/EXPERIMENTS-auth-server Exp4 | P3 |
| B-13 | PKCE(code_challenge_methods_supported) | login-password-only/EXPERIMENTS-auth-server Exp8 | P2 |

#### B-3: クライアント設定系

| ID | 実験テーマ | 元ファイル | 優先度 |
|----|----------|----------|--------|
| B-14 | クライアントscope制限 → UserInfoからemail消える | login-password-only/EXPERIMENTS-client Exp1 | P1 |
| B-15 | grant_types制限(refresh_token除外) | login-password-only/EXPERIMENTS-client Exp2 | P1 |
| B-16 | redirect_uri検証 | login-password-only/EXPERIMENTS-client Exp3 | P1 |
| B-17 | クライアント認証方式変更(none+サーバー整合) | login-password-only/EXPERIMENTS-client Exp4 | P2 |
| B-18 | response_types制限 | login-password-only/EXPERIMENTS-client Exp8 | P2 |
| B-19 | AT type(opaque→JWT) | third-party/EXPERIMENTS Exp6 | P1 |
| B-20 | Token Revocation + Introspection | third-party/EXPERIMENTS Exp7 | P1 |

#### B-4: CIBA設定系

| ID | 実験テーマ | 元ファイル | 優先度 |
|----|----------|----------|--------|
| B-21 | CIBAポーリング間隔(interval) | ciba/EXPERIMENTS Exp1 | P2 |
| B-22 | CIBAリクエスト有効期限(expired_token) | ciba/EXPERIMENTS Exp2 | P1 |
| B-23 | CIBAユーザーコード必須化 | ciba/EXPERIMENTS Exp3 | P2 |
| B-24 | CIBA認証拒否(failure_conditions→access_denied) | ciba/EXPERIMENTS Exp4 | P1 |
| B-25 | CIBA login_hint形式(device:/sub:/email:) | ciba/EXPERIMENTS Exp5 | P1 |
| B-26 | CIBA binding_message伝達 | ciba/EXPERIMENTS Exp7 | P2 |

#### B-5: 外部認証連携系

| ID | 実験テーマ | 元ファイル | 優先度 |
|----|----------|----------|--------|
| B-27 | 外部認証URL変更 → 接続エラー | external-password-auth/EXPERIMENTS Exp3 | P2 |
| B-28 | マッピングルール変更 → UserInfo値変化 | external-password-auth/EXPERIMENTS Exp4 | P1 |
| B-29 | provider_id変更 → 同一メールで別ユーザー | external-password-auth/EXPERIMENTS Exp8 | P2 |
| B-30 | body_mapping_rulesフィールド名変更 | external-password-auth/EXPERIMENTS Exp9 | P2 |
| B-31 | http_requests複数APIチェーン | external-password-auth/EXPERIMENTS-http-requests Exp1 | P1 |

---

### GAP-C: VERIFY-CONFIG-CHANGES（完全未カバー → E2E化候補）

| ID | パターン | 元ファイル | 優先度 |
|----|---------|----------|--------|
| C-01 | eKYC: no_action→http_request切替 | ekyc/VERIFY-CONFIG-CHANGES パターン1 | P2 |
| C-02 | eKYC: request.schemaバリデーション | ekyc/VERIFY-CONFIG-CHANGES パターン2 | P1 |
| C-03 | eKYC: transition条件(eq/in/AND/OR) | ekyc/VERIFY-CONFIG-CHANGES パターン3 | P1 |
| C-04 | eKYC: verified_claimsマッピング変更 | ekyc/VERIFY-CONFIG-CHANGES パターン4 | P2 |
| C-05 | eKYC: 多段プロセス(依存関係/pre_hook) | ekyc/VERIFY-CONFIG-CHANGES パターン5 | P1 |
| C-06 | eKYC: リトライ/response_resolve_configs | ekyc/VERIFY-CONFIG-CHANGES パターン6 | P2 |
| C-07 | eKYC: マッピング関数(trim/case/replace等) | ekyc/VERIFY-CONFIG-CHANGES-ADVANCED | P2 |
| C-08 | MFA-Email: OTP有効期間(expire_seconds) | mfa-email/VERIFY-CONFIG-CHANGES パターン1 | P1 |
| C-09 | MFA-Email: OTPリトライ制限 | mfa-email/VERIFY-CONFIG-CHANGES パターン2 | P1 |
| C-10 | MFA-Email: OTP再送信+旧コード無効化 | mfa-email/VERIFY-CONFIG-CHANGES パターン3 | P1 |
| C-11 | MFA-Email: 外部サービス切替(http_request) | mfa-email/VERIFY-CONFIG-CHANGES パターン5 | P2 |
| C-12 | MFA-Email: 認証ポリシー(ステップ順序/スコープ条件/AND→OR) | mfa-email/VERIFY-CONFIG-CHANGES パターン6 | P1 |
| C-13 | MFA-Email: 登録スキーマ変更(必須項目/minLength) | mfa-email/VERIFY-CONFIG-CHANGES パターン7 | P2 |
| C-14 | MFA-SMS: OTP再送信+旧コード無効化 | mfa-sms/VERIFY-CONFIG-CHANGES パターン1 | P1 |
| C-15 | MFA-SMS: 外部SMSサービス設定変更 | mfa-sms/VERIFY-CONFIG-CHANGES パターン2 | P2 |
| C-16 | MFA-SMS: 認証ポリシー(ステップ順序/スコープ条件/AND→OR) | mfa-sms/VERIFY-CONFIG-CHANGES パターン3 | P1 |

---

## 組込み戦略

### Phase 1: 基本フローのE2E化（P0）— 最優先

verify.shでカバー済みの基本フローをE2E Jestに移植する。
これにより、CI/CDパイプラインで常時回帰テストが可能になる。

**新規テストファイル案:**

```
e2e/src/tests/usecase/
├── standard/
│   ├── standard-10-basic-auth-flow.test.js          ← A-01: 基本認可コードフロー
│   ├── standard-11-password-management.test.js       ← A-02, A-03: パスワード変更/リセット
│   └── standard-12-token-introspection-m2m.test.js   ← A-15, A-16: M2M + Introspection
├── mfa/
│   ├── mfa-10-email-otp-mfa-flow.test.js             ← A-04: Email MFAフロー全体
│   └── mfa-11-sms-otp-mfa-flow.test.js               ← A-06: SMS MFAフロー全体
├── ciba/
│   └── ciba-10-full-ciba-poll-flow.test.js            ← A-07: CIBA全フロー(poll)
└── advance/
    ├── advance-10-ekyc-identity-verification.test.js  ← A-08, A-09: eKYC + verified_claims
    └── advance-11-external-password-auth.test.js      ← A-10, A-11: 外部パスワード認証
```

**見積り:** 8ファイル、各100-300行

**前提条件:**
- テスト用テナント設定にEmail OTP(no-action), SMS OTP(no-action), CIBA, eKYCの設定が含まれること
- Management APIでverification_code取得が可能なこと

---

### Phase 2: 設定変更テストのE2E化（P1）— 中期

EXPERIMENTS/VERIFY-CONFIG-CHANGESの中でP1のものを自動化する。
テスト内で Management API を使って設定を動的に変更→検証→復元するパターン。

**新規テストファイル案:**

```
e2e/src/tests/usecase/
├── standard/
│   ├── standard-20-password-policy.test.js            ← B-01: パスワードポリシー
│   ├── standard-21-claims-supported.test.js           ← B-04: claims_supported
│   ├── standard-22-token-expiration.test.js           ← B-05: AT期限切れ→401→RT復活
│   ├── standard-23-scope-filtering.test.js            ← B-14: クライアントscope制限
│   ├── standard-24-grant-type-restriction.test.js     ← B-15: grant_types制限
│   ├── standard-25-redirect-uri-validation.test.js    ← B-16: redirect_uri検証
│   ├── standard-26-access-token-jwt.test.js           ← B-19: AT opaque→JWT
│   ├── standard-27-token-revocation.test.js           ← B-20: Token Revocation
│   └── standard-28-id-token-strict-mode.test.js       ← B-10: strict mode
├── mfa/
│   ├── mfa-20-email-otp-expiration.test.js            ← C-08: OTP有効期間
│   ├── mfa-21-email-otp-retry-limit.test.js           ← C-09: リトライ制限
│   ├── mfa-22-otp-resend-invalidation.test.js         ← C-10, C-14: 旧コード無効化
│   └── mfa-23-auth-policy-scope-condition.test.js     ← C-12, C-16: スコープ条件付きMFA
├── ciba/
│   ├── ciba-20-expired-token.test.js                  ← B-22: CIBA期限切れ
│   ├── ciba-21-access-denied.test.js                  ← B-24: 認証拒否
│   └── ciba-22-login-hint-formats.test.js             ← B-25: login_hint形式
├── advance/
│   ├── advance-20-ekyc-schema-validation.test.js      ← C-02: スキーマバリデーション
│   ├── advance-21-ekyc-transition-conditions.test.js   ← C-03: transition条件
│   ├── advance-22-ekyc-multi-step-process.test.js     ← C-05: 多段プロセス
│   ├── advance-23-mapping-rules.test.js               ← B-28, B-31: マッピング/チェーン
│   └── advance-24-custom-claims-scope-mapping.test.js ← B-11: custom_claims_scope_mapping
```

**見積り:** 20ファイル、各50-200行

**テストパターン:**
```javascript
// 共通パターン: 設定変更→検証→復元
describe("Setting: claims_supported", () => {
  let originalConfig;

  beforeAll(async () => {
    originalConfig = await getAuthServerConfig();
  });

  afterAll(async () => {
    await updateAuthServerConfig(originalConfig); // 復元
  });

  it("should exclude email from UserInfo when removed from claims_supported", async () => {
    await updateAuthServerConfig({
      ...originalConfig,
      claims_supported: ["sub", "iss", "auth_time"]
    });
    // 認可フロー実行 → UserInfo取得 → emailがnullであることを検証
  });
});
```

---

### Phase 3: 高度な設定変更テスト（P2-P3）— 長期

EXPERIMENTS-http-requests、VERIFY-CONFIG-CHANGES-ADVANCEDのマッピング関数テスト等。
Mock Serverの管理が必要なため、テストインフラ整備が先。

**対象:** B-06〜B-09, B-12〜B-13, B-17〜B-18, B-21, B-23, B-26〜B-27, B-29〜B-30, C-01, C-04, C-06〜C-07, C-11, C-13, C-15

---

## 実装順序の提案

```
Phase 1 (P0: 基本フロー) ← 最優先
  │
  ├── Week 1-2: standard-10 (基本認可コードフロー)
  │             mfa-10 (Email MFAフロー)
  │             mfa-11 (SMS MFAフロー)
  │
  ├── Week 3-4: ciba-10 (CIBA全フロー)
  │             standard-11 (パスワード変更/リセット)
  │             standard-12 (M2M + Introspection)
  │
  └── Week 5-6: advance-10 (eKYC)
               advance-11 (外部パスワード認証)
               A-12〜A-14 (enterprise, id-service-migration)

Phase 2 (P1: 設定変更テスト)
  │
  ├── Month 2: standard-20〜25 (テナント/クライアント設定)
  │            mfa-20〜23 (OTP/ポリシー設定)
  │
  └── Month 3: ciba-20〜22 (CIBA設定)
               advance-20〜24 (eKYC/マッピング設定)

Phase 3 (P2-P3: 高度な設定変更)
  └── Month 4+: Mock Server基盤整備 → 残りの実験テスト
```

---

## 前提条件・技術課題

### Phase 1 で必要なもの

| 課題 | 対応方針 |
|------|---------|
| Email OTP検証コード取得 | Management API(`authentication-interactions/{id}/email-authentication-challenge`)を使う。既にmfa-01で実績あり |
| SMS OTP検証コード取得 | 同上(`sms-authentication-challenge`) |
| FIDO-UAF Mock | 既存のMockoon設定を流用（ciba/financial-gradeテストで実績あり） |
| eKYC身元確認設定 | テスト用テナントにidentity-verification-configurationを事前登録 |
| 外部パスワード認証Mock | Docker Compose内でmock-serverを起動（既存のmock-server.jsを流用） |

### Phase 2 で必要なもの

| 課題 | 対応方針 |
|------|---------|
| 設定の動的変更+復元 | Management API(PUT)で設定変更、afterAllで元の設定に復元 |
| テスト間の独立性 | 各テストで新規ユーザーを作成、設定変更はdescribeスコープでafterAllに復元 |
| sleep依存の排除 | AT期限切れテスト等はsleep必要だが、短時間(10-15秒)に設定して最小化 |

---

## 期待効果

| 指標 | 現状 | Phase 1後 | Phase 2後 |
|------|------|----------|----------|
| VERIFY.md基本フローのE2Eカバー率 | ~13% | ~80% | ~90% |
| EXPERIMENTS設定実験のE2Eカバー率 | ~8% | ~8% | ~60% |
| CONFIG-CHANGESのE2Eカバー率 | 0% | 0% | ~50% |
| CI/CDでの回帰検出力 | 低 | 中 | 高 |
