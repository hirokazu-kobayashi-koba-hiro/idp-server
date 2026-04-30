# OAuth 2.0 DPoP (RFC 9449) Gap Analysis

| 項目 | 内容 |
|------|------|
| 分析日 | 2026-04-30 |
| 対象ブランチ | `feat/dpop-rfc9449` |
| 対象仕様 | RFC 9449 (OAuth 2.0 Demonstrating Proof of Possession) |
| 認定基準 | OIDF Conformance Suite `fapi2-security-profile-final-dpop-negative-tests` |
| 関連要件 | [oauth2-dpop-requirements.yaml](./oauth2-dpop-requirements.yaml) |

---

## 1. サマリ

### 1.1 RFC 9449 要件カバレッジ

| カテゴリ | 全要件 | ✅対応 | ⚠️部分 | ❌未対応 | 🚫該当外 | 対応率 |
|---------|--------|--------|--------|---------|---------|--------|
| MUST/SHALL | 30 | 22 | 2 | 6 | 0 | 73% |
| MUST NOT | 8 | 6 | 1 | 1 | 0 | 75% |
| SHOULD | 12 | 4 | 3 | 5 | 0 | 33% |
| OPTIONAL | 17 | 3 | 1 | 3 | 10 | 24% |
| **全体** | **67** | **35** | **7** | **15** | **10** | **52%** |

### 1.2 OIDF Conformance Suite 観点での認定可否

`fapi2-security-profile-final` テストプランに含まれる DPoP 関連テスト 11 件のうち：
- **必須通過**（FAILURE 扱い）: 9 件 → 5 件対応・4 件要対応
- **推奨**（WARNING 扱い）: 2 件（jti リプレイ拒否、URI正規化）

**結論**: FAPI 2.0 SP Final 認定取得には Authorization Code Binding (`dpop_jkt`) と PAR + DPoP 統合の対応が必須。jti リプレイ検出と nonce 機構は WARNING 扱いのため認定上は後回し可能。

---

## 2. セクション別実装状況

### 2.1 §4.2 DPoP Header & Payload Structure

| 要件 | レベル | 状況 | 実装箇所 / 備考 |
|------|--------|------|----------------|
| JOSE Header に typ, alg, jwk 必須 | MUST | ✅ | `DPoPProofVerifier.java:137-154, 229-233` |
| typ = `dpop+jwt` | MUST | ✅ | `DPoPProofVerifier.java:194-200` |
| Payload に jti, htm, htu, iat 必須 | MUST | ✅ | `DPoPProofVerifier.java:269-282` |
| アクセストークン使用時に ath 必須 | MUST | ✅ | `DPoPProofVerifier.java:171-173, 375-385` |
| サーバー nonce 提供時 nonce 必須 | MUST | ❌ | nonce 検証ロジックなし |
| alg ≠ none かつ非対称 | MUST NOT | ✅ | `DPoPProofVerifier.java:208-222` |
| jwk に秘密鍵を含めない | MUST NOT | ✅ | `DPoPProofVerifier.java:240-245` |

### 2.2 §4.3 DPoP Proof Validation (12-Check)

| Check | 要件 | レベル | 状況 | 実装箇所 |
|-------|------|--------|------|---------|
| 1 | 単一 DPoP ヘッダ | MUST | ⚠️ | Spring Boot adapter で複数ヘッダ判定なし |
| 2 | well-formed JWT | MUST | ✅ | `DPoPProofVerifier.java:130-135` |
| 3 | 必須クレーム存在 | MUST | ✅ | `DPoPProofVerifier.java:269-282` |
| 4 | typ = `dpop+jwt` | MUST | ✅ | `DPoPProofVerifier.java:194-200` |
| 5 | 非対称 alg | MUST | ✅ | `DPoPProofVerifier.java:208-222` |
| 6 | 署名検証 | MUST | ✅ | `DPoPProofVerifier.java:253-262` |
| 7 | jwk 秘密鍵なし | MUST | ✅ | `DPoPProofVerifier.java:240-245` |
| 8 | htm 一致 | MUST | ✅ | `DPoPProofVerifier.java:291-299` |
| 9 | htu 一致（query/fragment除外） | MUST | ✅ | `DPoPProofVerifier.java:309-341` |
| 10 | nonce 一致（提供時） | MUST | ❌ | 未実装 |
| 11 | iat 時間窓内（既定 5 分） | MUST | ✅ | `DPoPProofVerifier.java:352-364` |
| 12 | jti リプレイ拒否 | SHOULD | ❌ | `DPoPProofVerifier.java:175-178` に "not implemented" コメント |

**注**: §4.3 のチェックリスト原文に jti 専用項目はない。§11.1 で "servers can store the jti value" と記載されており、レベルは MAY/SHOULD 相当。

### 2.3 §5 Token Request & Response

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| トークン要求時 DPoP proof 必須（DPoP使用時） | MUST | ✅ | 全 GrantService 対応 |
| token_type に `DPoP` 設定 | MUST | ✅ | `AccessTokenBuilder`, `AccessTokenPayloadBuilder` |
| DPoP proof 公開鍵がトークンバインド鍵と一致 | MUST | ✅ | `AccessTokenCreator` |
| `use_dpop_nonce` エラー応答 | SHOULD | ❌ | `TokenRequestErrorHandler` に該当エラーコードなし |
| `invalid_dpop_proof` エラー | OPTIONAL | ✅ | `TokenRequestErrorHandler.java:50-60` |
| DPoP proof なしでも Bearer 発行可 | OPTIONAL | ✅ | `DPoPProofVerifier.java:76-88` |

### 2.4 §5.8 Refresh Token Binding

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| パブリッククライアントの RT を DPoP 鍵にバインド | MUST | ✅ | `RefreshTokenGrantService.java:88-90` |
| RT 使用時に同じ DPoP 鍵の proof 必須 | MUST | ✅ | `RefreshTokenDPoPBindingVerifier.java:43-63` |

### 2.5 §6 Public Key Confirmation

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| RS が DPoP-bound トークン識別可能 | MUST | ✅ | `AccessToken.hasDPoPBinding()` |
| JWT access token の cnf.jkt | MUST | ✅ | `AccessTokenPayloadBuilder.java:100-115` |
| Introspection レスポンス token_type=DPoP | MUST | ✅ | `DPoPBindingVerifier` |

### 2.6 §7 Protected Resource Access

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| DPoP proof + access token 必須 | MUST | ✅ | `UserinfoVerifier`, `DPoPBindingVerifier` |
| ath クレーム検証 | MUST | ✅ | `DPoPBindingVerifier.java:81` |
| 公開鍵がトークンバインド鍵と一致 | MUST | ✅ | `DPoPBindingVerifier.java:90-96` |
| 検証失敗時 401 | MUST | ✅ | `ProtectedResourceApiFilter.java:91-93` |
| DPoP-bound token の Bearer 利用拒否 | MUST NOT | ✅ | `DPoPBindingVerifier` |
| Proxy-Authenticate での DPoP 拒否 | MUST NOT | ❌ | 未対応 |
| WWW-Authenticate `algs` パラメータ | SHOULD | ❌ | 未対応 |

### 2.7 §7.3 Idempotency

| 要件 | レベル | 状況 | 備考 |
|------|--------|------|------|
| jti リプレイ検出 | SHOULD（§11.1 "can store"） | ❌ | `DPoPProofVerifier.java:175-178` に "not implemented" |

### 2.8 §8 / §9 Nonce Mechanism

| 要件 | レベル | 状況 |
|------|--------|------|
| AS Nonce 値の予測不可能性 | MUST | ❌ |
| 単一 DPoP-Nonce レスポンスヘッダ | MUST NOT (複数禁止) | ❌ |
| nonce downgrade 拒否 | MUST NOT | ❌ |
| DPoP-Nonce レスポンスのキャッシュ制御 | SHOULD | ❌ |
| RS Nonce | OPTIONAL | ❌ |

### 2.9 §10 Authorization Code Binding

| 要件 | レベル | 状況 |
|------|--------|------|
| Authorization Request の `dpop_jkt` パラメータ | OPTIONAL（FAPI 2.0 では実質必須） | ❌ |
| Token Request で `dpop_jkt` と DPoP proof JKT 一致検証 | MUST（dpop_jkt 使用時） | ❌ |
| `dpop_jkt` と DPoP header の競合チェック | MUST NOT | ❌ |

### 2.10 §10.1 PAR Integration

| 要件 | レベル | 状況 |
|------|--------|------|
| PAR で `dpop_jkt` と DPoP header 両対応 | MUST | ❌ |

### 2.11 §11 Security Considerations

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| DPoP proof の限定時間受容 | MUST | ✅ | 既定 5 分 (`DPoPProofVerifier.java:352-364`) |

---

## 3. OIDF Conformance Suite テスト対応

`/Users/hirokazu.kobayashi/work/conformance-suite/src/main/java/net/openid/conformance/fapi2spfinal/` 配下のテスト 11 件と要件のマッピング。

### 3.1 必須テスト（FAILURE 扱い）

| OIDF テスト | 検証内容 | idp-server 対応 |
|------------|---------|----------------|
| `FAPI2SPFinalDpopNegativeTests` | 必須クレーム欠落、typ/alg/jwk 不正、署名不正、htm/htu 不一致、ath 不一致、cnf.jkt 不一致、複数ヘッダ拒否、Bearer ダウングレード拒否 等 | ✅ ほぼ対応（複数ヘッダ拒否は要確認） |
| `FAPI2SPFinalCheckDpopProofNbfExp` | nbf/exp 検証 | ✅ iat 時間窓で代替 |
| `FAPI2SPFinalEnsureDpopProofWithIat10SecondsAfterSucceeds` | iat 10秒後を許容 | ✅ |
| `FAPI2SPFinalEnsureDpopProofWithIat10SecondsBeforeSucceeds` | iat 10秒前を許容 | ✅ |
| `FAPI2SPFinalEnsureMismatchedDpopJktFails` | dpop_jkt 不一致拒否 | ❌ |
| `FAPI2SPFinalEnsureTokenEndpointFailsWithMismatchedDpopJkt` | Token endpoint で dpop_jkt 不一致拒否 | ❌ |
| `FAPI2SPFinalEnsureTokenEndpointFailsWithMismatchedDpopProofJkt` | Token endpoint で proof JKT 不一致拒否 | ✅ |
| `FAPI2SPFinalEnsureDpopAuthCodeBindingSuccess` | Authorization Code Binding (`dpop_jkt`) | ❌ |
| `FAPI2SPFinalEnsureDpopProofAtParEndpointBindingSuccess` | PAR エンドポイントでの DPoP proof | ❌ |
| `FAPI2SPFinalClientTestRSDpopAuthSchemeCaseInsenstivity` | DPoP auth スキーム大小文字非依存 | ⚠️ 要確認 |
| `FAPI2SPFinalClientTestHappyPathNoDpopNonce` | nonce なしハッピーパス | ✅ |

### 3.2 WARNING 扱いテスト（認定への影響なし）

| OIDF テスト | 検証内容 | idp-server 対応 |
|------------|---------|----------------|
| jti リプレイ（`FAPI2SPFinalDpopNegativeTests` 内 248-255行） | 同一 jti 2回目使用で拒否されるか | ❌ |
| URI 正規化（`FAPI2SPFinalDpopNegativeTests` 内 282-290行） | scheme/host case-insensitive、デフォルトポート除外 | ✅ `UriWrapper` で対応済み |

**重要な発見**: `FAPI2SPFinalDpopNegativeTests.java:252-255` で jti 再利用拒否は `shouldFail=false` で WARNING 扱い：

```java
eventLog.startBlock("DPoP reuse, Second use of the same jti, this 'should' fail");
callResourceEndpointSteps(... FixedJtiClaim ..., false, false, "DPOP-7.1");
                                                       ^^^^^ shouldFail=false → WARNING
```

OIDF認定としては jti リプレイ未対応でも FAPI 2.0 SP Final 認定取得可能。

---

## 4. ギャップ優先度（OIDF 認定基準で再整理）

### 4.1 P0: FAPI 2.0 SP Final 認定取得に必須

| ID | 項目 | RFC 9449 | OIDF テスト |
|----|------|----------|-------------|
| GAP-DPOP-001 | Authorization Code Binding (`dpop_jkt`) | §10 | `FAPI2SPFinalEnsureDpopAuthCodeBindingSuccess`, `EnsureMismatchedDpopJktFails` |
| GAP-DPOP-002 | PAR + DPoP 統合 | §10.1 | `FAPI2SPFinalEnsureDpopProofAtParEndpointBindingSuccess` |
| GAP-DPOP-003 | 複数 DPoP ヘッダ検出 | §4.3 Check 1 | `FAPI2SPFinalDpopNegativeTests` (Multiple proofs) |
| GAP-DPOP-004 | DPoP auth スキーム大小文字非依存（要確認） | §7.1 | `FAPI2SPFinalClientTestRSDpopAuthSchemeCaseInsenstivity` |

### 4.2 P1: SHOULD 要件、認定 WARNING 扱い

| ID | 項目 | RFC 9449 | 備考 |
|----|------|----------|------|
| GAP-DPOP-005 | WWW-Authenticate `algs` パラメータ | §7.1 | Resource Server 応答強化 |
| GAP-DPOP-006 | `use_dpop_nonce` エラーコード | §8 | nonce 機構実装時 |
| GAP-DPOP-007 | jti リプレイ検出 | §11.1 | セキュリティ強化 |

### 4.3 P2: OPTIONAL/任意

| ID | 項目 | RFC 9449 | 備考 |
|----|------|----------|------|
| GAP-DPOP-008 | AS Nonce 機構 | §8 | Nonce による追加リプレイ対策 |
| GAP-DPOP-009 | RS Nonce 機構 | §9 | Resource Server 側 |
| GAP-DPOP-010 | Proxy-Authenticate ヘッダ拒否 | §7 | 通常運用で発生しない |

---

## 5. 推奨アクション

1. **Sprint N（FAPI 2.0 認定向け）**: GAP-DPOP-001〜004
   - `dpop_jkt` Authorization Request パラメータ追加
   - PAR エンドポイントでの DPoP proof 検証
   - 複数 DPoP ヘッダ検出
   - DPoP auth スキーム大小文字非依存対応確認

2. **Sprint N+1（セキュリティ強化）**: GAP-DPOP-005〜007
   - WWW-Authenticate ヘッダ拡充
   - jti リプレイ検出（Redis/インメモリ）

3. **将来検討**: GAP-DPOP-008〜010
   - Nonce 機構（AS / RS）

---

## 6. 参考文献

- RFC 9449: <https://www.rfc-editor.org/rfc/rfc9449.html>
- FAPI 2.0 Security Profile: <https://openid.net/specs/fapi-security-profile-2_0.html>
- OIDF Conformance Suite: <https://gitlab.com/openid/conformance-suite>
- 既存要件抽出: [oauth2-dpop-requirements.yaml](./oauth2-dpop-requirements.yaml)
