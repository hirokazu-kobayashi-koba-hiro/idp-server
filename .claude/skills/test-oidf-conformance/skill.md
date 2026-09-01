---
name: test-oidf-conformance
description: OIDF適合性テストの実行・結果分析・不合格項目の修正ワークフローを行う際に使用。FAPI 1.0 Advanced/FAPI-CIBAのOIDF認定テスト結果の分析、GAP管理、修正パターンの適用に役立つ。
---

# OIDF適合性テスト ワークフローガイド

## ドキュメント

- `documentation/requirements/fapi-1.0-gap-analysis.yaml` - GAP分析（テスト結果と修正記録）
- `documentation/requirements/fapi-1.0-advanced-op-test-mapping.md` - FAPI Advanced 63テストとRFC要件のマッピング
- `config/examples/financial-grade/oidc-test/fapi-ciba/FAPI-CIBA-test-cases.md` - FAPI-CIBAテストケース詳細
- `config/examples/financial-grade/README.md` - Financial-gradeテナントセットアップガイド

関連スキル:
- `spec-fapi` - FAPI仕様の実装方法・モジュール構成（実装時に参照）
- `test-rfc-compliance` - RFC要件抽出・Gap分析の一般的なワークフロー

## 概要

OpenID Foundation (OIDF) の適合性テストスイートを**ローカルで実行**し、不合格項目を分析・修正するためのワークフロー。`certification.openid.net` も ngrok も使わない。

実行の手順そのものは `oidc-conformance-suite/README.md`、落とし穴は `.claude/rules/oidc-conformance.md`
に書いてある。**この skill は「結果をどう読み、どう直すか」**を扱う。

### 対応状況

`run.sh` があるディレクトリが実行できるスイート。

| スイート | テストプラン | モジュール | 状態 |
|---|---|---|---|
| `oidc-conformance-suite/fapi1-advanced/` | `fapi1-advanced-final-test-plan` | 63 | ✅ 実行できる |
| `oidc-conformance-suite/fapi-ciba/` | `fapi-ciba-id1-test-plan` | 70 | ❌ デバイス承認の自動化が未実装 |
| `oidc-conformance-suite/fapi2/` | `fapi2-security-profile-final-test-plan` | 72 | ❌ テナント設定が存在しない |
| `oidc-conformance-suite/oidcc/` | `oidcc-test-plan` ほか | 55 / 38 | ❌ 設定とプランの対応づけが未決 |

テスト設定 JSON は各テナント設定に付随する（`config/examples/<setup>/oidc-test/`）。

### テスト環境構成

```
oidc-conformance-suite/fapi1-advanced/run.sh
        ↓ ランナーをコンテナ(python:3.13-alpine)で起動
conformance suite (docker / OIDF 公開イメージ)
        ↓ PAR / token / userinfo は suite が直接叩く（mTLS バックチャネル）
idp-server ── api.local.test / mtls.api.local.test
        ↑
        │ 認可エンドポイントだけはブラウザが要る。suite 内蔵の HtmlUnit は
        │ Next.js の CSR を描画できないため、外部ブラウザ API 経由で肩代わりする
        │
Playwright ドライバ (oidc-conformance-suite/driver/、常駐)
    ├── email OTP  … 管理 API から検証コードを取得
    ├── FIDO2      … CDP の仮想オーセンティケータ
    └── 同意       … callback へ遷移

Financial-grade テナント (tenant_id: c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8)
```

### 結果の読み方

| 結果 | 意味 | 対応 |
|---|---|---|
| `PASSED` | 合格 | — |
| `REVIEW` | エラーページのスクリーンショットを人が確認するテスト。**失敗ではない** | 通常は対応不要 |
| `WARNING` | 仕様上の SHOULD 違反、または suite が知らないメタデータ | 個別判断 |
| `FAILED` | 条件が失敗した | 下記の分析ワークフローへ |
| `WAITING` のままタイムアウト | **ドライバ側の問題**であることが多い | ドライバのログと `.claude/rules/oidc-conformance.md` を見る |

`FAILED` / タイムアウトを見たら、**まず idp-server の問題かドライバの問題かを切り分ける**。
実績として、初回の通し実行で出た 7 件の異常のうち 5 件はドライバ側だった。

## 不合格テスト分析ワークフロー

### Step 1: テスト名からRFC要件を特定

テスト名を `documentation/requirements/fapi-1.0-advanced-op-test-mapping.md` で検索し、対応するRFC/仕様条項を特定する。

**FAPI Advanced テスト名パターン**:
```
fapi1-advanced-final-{テスト識別子}
```

**FAPI-CIBA テスト名パターン**:
```
fapi-ciba-id1-{テスト識別子}
```

### Step 2: 影響するVerifier/Handlerを特定

テスト識別子から修正対象のコードを絞り込む:

| テスト識別子パターン | 主な関連クラス | モジュール |
|---------------------|---------------|----------|
| `ensure-request-object-*` | `RequestObjectVerifier`, `RequestObjectVerifyable` | `idp-server-core` |
| `ensure-matching-*`, `ensure-response-type-*` | `FapiAdvanceVerifier`, `FapiBaselineVerifier` | `idp-server-core-extension-fapi` |
| `ensure-jarm-*`, `ensure-response-mode-*` | `JarmVerifier`, `AuthorizationResponseCreator`, `AuthorizationErrorResponseCreator` | `idp-server-core` |
| `par-*` | `OAuthRequestHandler.handlePushedRequest()`, `PushedRequestUriPatternContextCreator` | `idp-server-core` |
| `ensure-pkce-*`, `par-*-pkce-*` | `FapiAdvanceVerifier`, `FapiBaselineVerifier` | `idp-server-core-extension-fapi` |
| `ensure-mtls-*` | `TlsClientAuthAuthenticator`, `SelfSignedTlsClientAuthAuthenticator` | `idp-server-core-extension-fapi` |
| `ensure-client-assertion-*` | `ClientAuthenticationJwtValidatable` | `idp-server-core` |
| `ensure-signed-*-with-RS256-*` | アルゴリズム検証ロジック | `idp-server-core-extension-fapi` |
| `access-token-type-*` | `AuthorizationHeaderType` | `idp-server-core` |
| `refresh-token` | `RefreshTokenGrantService`, `RefreshTokenVerifier` | `idp-server-core` |
| `user-rejects-*` | `AuthorizationDenyHandler` | `idp-server-core` |

**FAPI-CIBA固有**:

| テスト識別子パターン | 主な関連クラス | モジュール |
|---------------------|---------------|----------|
| `ensure-*-backchannel-*` | `CibaRequestHandler`, `FapiCibaVerifier` | `idp-server-core-extension-fapi-ciba` |
| `ensure-request-object-*` (CIBA) | `CibaRequestObjectVerifier` | `idp-server-core-extension-ciba` |
| `*-notification-endpoint-*` | `CibaPingNotificationService` | `idp-server-core-extension-ciba` |

### Step 3: 検証チェーンの確認

FAPI Advanced の認可リクエスト検証は以下のチェーンで実行される:

```
OAuthRequestVerifier
  ├── OAuthRequestBaseVerifier (共通検証)
  │     ├── scope検証
  │     ├── redirect_uri検証
  │     └── response_type検証
  │
  ├── Extension Verifier (プロファイル別)
  │     ├── [FAPI_BASELINE] FapiBaselineVerifier
  │     │     ├── response_type=code only
  │     │     ├── PKCE S256必須
  │     │     ├── state必須
  │     │     └── nonce必須 (openid scope時)
  │     │
  │     ├── [FAPI_ADVANCE] FapiAdvanceVerifier
  │     │     ├── FapiBaselineVerifier委譲 ← 重要: Baseline要件を継承
  │     │     ├── PAR必須
  │     │     ├── JARM必須
  │     │     ├── Request Object署名検証
  │     │     ├── exp/nbf 60分検証
  │     │     ├── aud検証
  │     │     ├── クライアント認証方式制限
  │     │     └── PAR時PKCE S256検証
  │     │
  │     └── [FAPI_CIBA] FapiCibaVerifier
  │
  └── RequestObjectVerifier (Request Object固有)
        ├── JWT署名検証
        ├── exp/nbf/aud/iss検証
        └── jti検証 (存在時のみ)
```

**PAR経由のリクエストの場合**:
- PARエンドポイントで全検証を実行済み
- 認可エンドポイントではJWT検証をスキップ（JoseContextが空のため）
- JARM・response_type・sender-constrained・クライアント認証方式は再検証

### Step 4: 修正の実施

1. 該当Verifier/Handlerを修正
2. `./gradlew spotlessApply` でフォーマット修正
3. `./gradlew build` でコンパイル確認

### Step 5: GAP管理ドキュメント更新

`documentation/requirements/fapi-1.0-gap-analysis.yaml` に追記:

```yaml
  - id: "GAP-XXX"                         # 次の連番
    title: "簡潔なタイトル"
    priority: "P0"                         # P0:適合性テスト不合格, P1:テスト不足, P2:改善
    type: "bug"                            # bug / test / enhancement
    category: "authorization_server"       # authorization_server / resource_server / security_algorithms
    spec_reference: "RFC/仕様のセクション番号"
    requirement: |
      仕様の要件文（原文英語が望ましい）
    current_behavior: |
      現在の動作と問題点
    expected_behavior: |
      期待される正しい動作
    conformance_test_result: "FAILURE - テスト名と失敗理由"
    fix_status: "FIXED"                    # OPEN / IN_PROGRESS / FIXED
    fix_description: |
      修正内容の詳細
    affected_files:
      - path: "修正ファイルのパス"
        action: "modified"
        detail: "修正内容"
    e2e_test_needed: false                 # OIDF適合性テストで検証できる場合はfalse
    e2e_test_description: "OIDF適合性テストで検証"
```

**GAP番号の採番**: `fapi-1.0-gap-analysis.yaml` の `issue_candidates` セクション末尾のGAP-XXXから次番号を使用。

### Step 6: 修正後の確認チェックリスト

```
□ ./gradlew spotlessApply 実行
□ ./gradlew build 成功
□ private_key_jwt テストプランで該当テスト合格
□ tls_client_auth テストプランで該当テスト合格
□ gap-analysis.yaml を更新（fix_status: FIXED）
□ 既存E2Eテストが壊れていないことを確認 (cd e2e && npm test)
□ コミットメッセージに GAP-XXX を含める
```

## テストの実行

手順の詳細は `oidc-conformance-suite/README.md`。ここは要点だけ。

```bash
# 1. idp-server とテナント
docker compose up -d
cd config/examples/financial-grade && ./setup.sh

# 2. suite スタック
docker compose -f oidc-conformance-suite/docker-compose.yaml up -d

# 3. ブラウザ操作ドライバ（別ターミナルで常駐。1 プロセスだけ）
cd oidc-conformance-suite/driver && npm install && node driver.mjs

# 4. テスト
export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
./oidc-conformance-suite/fapi1-advanced/run.sh --rerun 1:2   # happy path 1 本
./oidc-conformance-suite/fapi1-advanced/run.sh --rerun 1     # private_key_jwt 全部
./oidc-conformance-suite/fapi1-advanced/run.sh               # 両方式
```

**実行前に必ず `pgrep -f "[d]river.mjs"` でドライバが 1 つだけか確認する。**
複数動いていると URL を取り合い、原因の分かりにくい失敗になる（`.claude/rules/oidc-conformance.md`）。

### コード変更後の反映

```bash
# Docker imageを再ビルド（必須 - restartだけでは反映されない）
docker compose up -d --build idp-server-1 idp-server-2
```

### 結果の確認

```bash
# suite の UI（dev プロファイルなのでログイン不要）
open https://localhost:8443/plans.html
```

エクスポートは `oidc-conformance-suite/results/` に HTML / JSON / zip で出る。
API から直接読むこともできる。

```bash
curl -sk https://localhost:8443/api/info/{testId}   # status / result
curl -sk https://localhost:8443/api/log/{testId}    # 全条件のログ。失敗理由はここ
```

### FAPI-CIBA（未対応）

CIBA はブラウザを使わないため現在のドライバでは動かせない。デバイス認証の承認を
自動化する仕組みが別途要る。現状は `config/examples/financial-grade/ciba-device-auth.sh`
を手で叩く運用。詳細は `oidc-conformance-suite/fapi-ciba/README.md`。

## よくある不合格パターンと対処法

### パターン1: Baseline要件の継承漏れ

**症状**: FAPI Advanced固有の検証はあるが、Baseline由来の検証が漏れている
**原因**: `FapiAdvanceVerifier` が `FapiBaselineVerifier` への委譲呼び出しを忘れている
**対処**: `FapiAdvanceVerifier.verify()` の先頭で `fapiBaselineVerifier.verify()` を呼ぶ
**実例**: GAP-021（nonce/state検証の漏れ）

### パターン2: エラーレスポンスのRFC準拠

**症状**: テストスイートが期待するerrorコード（`invalid_request`, `invalid_request_object`等）と異なるエラーが返る
**原因**: エラーコードがRFC規定と一致していない、または検証の実行順序により想定と異なるverifierがエラーを返す
**対処**: 各RFCのエラーコード定義を確認し、適切なエラーコードで例外をスロー
**実例**: GAP-006（scopeなしRequest Objectで`invalid_scope`ではなく`invalid_request_object`を返すべき）

### パターン3: JARM応答の不正

**症状**: JARMレスポンスが期待通りに生成されない
**原因A**: エラー応答がJARMでラップされるべきでない場合にラップされている
**原因B**: JARM応答にstateクレームが欠落している
**原因C**: リダイレクトURIにクエリパラメータがある場合の区切り文字が不正
**対処**:
  - A: `context.isJwtMode()`（自動判定）ではなく `context.responseMode().isJwtMode()`（明示指定）を使用
  - B: `JarmResponseCreator` でエラー時もstateを含める
  - C: リダイレクトURIに既に`?`がある場合は`&`で連結
**実例**: GAP-008（JARM不要なエラーがJARMラップ）, GAP-021（stateクレーム欠落、クエリパラメータ区切り）

### パターン4: PAR固有の検証問題

**症状**: PAR経由のリクエストで検証が過剰 or 不足
**原因**: PARエンドポイントと認可エンドポイントで検証が重複/不足
**対処**: PARの2段階検証アーキテクチャを理解し、各段階で何を検証すべきか整理
  - PARエンドポイント: Request Object署名、exp/nbf/aud、PKCE等の全検証
  - 認可エンドポイント: PAR経由の場合はJWT検証スキップ、JARM/response_type等は再検証
**実例**: GAP-001（PAR時PKCE S256未検証）, GAP-016（request_uriクライアントバインド未検証）

### パターン5: alg:none / RS256 の未拒否

**症状**: FAPI禁止のアルゴリズムが受け入れられてしまう
**原因**: アルゴリズム制限チェックがFAPIレイヤーで実装されていない
**対処**: `FapiAdvanceVerifier` でアルゴリズムチェックを追加（PS256/ES256のみ許可）
**実例**: GAP-007（alg:none受け入れ）

### パターン6: private_key_jwt と tls_client_auth で挙動が異なる

**症状**: 一方のテストプランでは合格するが他方で不合格
**原因**: クライアント認証方式によって異なるコードパスが実行される
**対処**: 必ず両方のテストプランで確認する。特にクライアント認証関連のVerifierは方式ごとに分岐がある
**確認**: `ClientAuthenticationJwtValidatable` (private_key_jwt), `TlsClientAuthAuthenticator` (tls_client_auth)

### パターン7: Request Objectクレームの必須/任意の判断ミス

**症状**: 任意のクレームを必須として拒否している、または必須のクレームを検証していない
**原因**: FAPI 1.0 と FAPI 2.0 で要件が異なる場合がある（例: jtiはFAPI 1.0では任意、2.0では必須）
**対処**: 対象のFAPIバージョンの仕様を正確に確認し、クレーム検証ロジックを修正
**実例**: GAP-005（jtiクレームをFAPI 1.0で必須にしていた）

## テスト名 → RFC要件 クイックリファレンス

### FAPI 1.0 Advanced — 主要テスト

| # | テスト名 | RFC/仕様要件 |
|---|---------|-------------|
| 2 | `fapi1-advanced-final` (happy path) | 5.2.2-5/6, 5.1, RFC8705-3 |
| 3 | `user-rejects-authentication` | RFC6749-4.1.2.1 |
| 9 | `ensure-response-mode-query` | 5.2.2-2 |
| 15 | `ensure-request-object-without-exp-fails` | 5.2.2-13 |
| 16 | `ensure-request-object-without-nbf-fails` | 5.2.2-17 |
| 19 | `ensure-request-object-without-nonce-fails` | Baseline 5.2.2.2 |
| 22 | `ensure-request-object-with-bad-aud-fails` | 5.2.2-15 |
| 25 | `ensure-signed-request-object-with-RS256-fails` | 8.6-1 |
| 26 | `ensure-request-object-signature-algorithm-is-not-none` | 8.6-3 |
| 31 | `ensure-response-type-code-fails` | 5.2.2-2 |
| 33 | `ensure-mtls-holder-of-key-required` | 5.2.2-5/6, RFC8705-2 |
| 34 | `ensure-authorization-code-is-bound-to-client` | RFC6749-4.1.3 |
| 42 | `refresh-token` | RFC8705-3 |
| 53-58 | `par-*` | RFC9126 |
| 59-62 | `par-*-pkce-*` / `ensure-pkce-*` | RFC7636, 5.2.2-18 |

完全なマッピングは `documentation/requirements/fapi-1.0-advanced-op-test-mapping.md` を参照。

## コミットメッセージ規約

```
fix: [簡潔な修正内容の説明] (GAP-XXX)
```

例:
```
fix: FAPI Advanced JARM/nonce/state適合性テスト不合格を修正 (GAP-021)
fix: PAR有効期限の分離とRequest Object署名検証エラーコードの修正 (GAP-020)
fix: FAPI 1.0 Advanced OIDF適合性テスト不合格項目を修正 (GAP-016〜GAP-019)
```

## トラブルシューティング

前提の確認順序と、ドライバまわりの落とし穴は `.claude/rules/oidc-conformance.md` にまとめてある。
ここは分析時に迷いやすいものだけ。

### 大量にタイムアウトする / 何も進まない
ドライバが動いていないか、複数動いている。`pgrep -f "[d]river.mjs"` で 1 つであることを確認する。
ドライバのログ（`oidc-conformance-suite/driver/driver.log`）に `⚠️ suite へのポーリング失敗`
が出ていれば suite に届いていない。

### テストスイートがローカルサーバーに接続できない
```bash
docker exec conformance-server curl -sk -o /dev/null -w '%{http_code}\n' \
  https://api.local.test/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration
```
404 ならテナントが消えている（`setup.sh` を再実行）。接続自体が失敗するなら
`docker-compose.yaml` の `extra_hosts`（`host-gateway`）と idp-server 側の 443 公開を確認する。

### テナントが消えている
`config/examples/financial-grade/` の `delete.sh` や組織削除で消える。discovery が 404 なら
`setup.sh` を再実行する。**その場合 `driver/passkey.json` も消すこと**（ユーザーごと消えている）。

### テスト設定JSONの更新忘れ
クライアント設定を変更した場合、以下の同期が必要:
1. `config/examples/financial-grade/` の各クライアントJSON
2. `config/examples/financial-grade/oidc-test/fapi/` のテスト設定JSON
3. `onboarding-request.json` のクライアント定義

### FAPI-CIBAテストがタイムアウトする
- `ciba-device-auth.sh` の実行タイミングが遅い可能性
- テスト中にデバイス認証承認を迅速に行う必要がある

### コード変更が反映されない
```bash
# restartではなく--buildが必須
docker compose up -d --build idp-server-1 idp-server-2
```
