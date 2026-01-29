---
description: OpenID Connect / OAuth 2.0 仕様書からMUST/SHOULD要件を体系的に抽出し、カテゴリ分類するスキル
tags:
  - oidc
  - oauth
  - specification
  - requirements
  - testing
  - compliance
---

# OIDC Spec Requirement Extractor

OpenID Connect / OAuth 2.0 仕様書から、MUST/SHALL/SHOULD/OPTIONAL の要件を体系的に抽出し、カテゴリ分類して構造化YAMLレポートを生成するスキル。

## 概要

このスキルは、RFC 2119に基づく要件レベル（MUST/SHALL/SHOULD/MAY/OPTIONAL）を仕様書から抽出し、E2Eテスト設計や仕様書レビューに活用できる形式で出力します。

### 対応仕様書

- OpenID Connect Core 1.0
- OpenID Connect Discovery 1.0
- OAuth 2.0 (RFC 6749)
- OAuth 2.0 拡張仕様（PKCE, FAPI, CIBA, Token Introspection等）
- その他の標準仕様書

## 使い方

### 基本的な使い方

```
oidc-spec-requirement-extractor <spec_url> <spec_name> [output_path]
```

### パラメータ

| パラメータ | 必須 | 説明 | 例 |
|-----------|------|------|-----|
| `spec_url` | ✅ | 仕様書のURL（https） | `https://openid.net/specs/openid-connect-core-1_0.html` |
| `spec_name` | ✅ | 仕様書の名前 | `OpenID Connect Core 1.0` |
| `output_path` | ❌ | 出力先パス（省略時：自動生成） | `oidc-core-requirements.yaml` |

**ファイル名規則**: `{spec-slug}-requirements.yaml`

仕様書名から自動的にファイル名を生成します：
- `OpenID Connect Core 1.0` → `oidc-core-requirements.yaml`
- `FAPI 2.0 Security Profile` → `fapi-2.0-requirements.yaml`
- `OAuth 2.0 PKCE (RFC 7636)` → `oauth2-pkce-requirements.yaml`

### 使用例

#### 例1: OpenID Connect Core 1.0 の要件抽出

```bash
# 出力先を指定（推奨）
oidc-spec-requirement-extractor \
  "https://openid.net/specs/openid-connect-core-1_0.html" \
  "OpenID Connect Core 1.0" \
  "documentation/requirements/oidc-core-requirements.yaml"

# ファイル名のみ指定（documentation/requirements/配下に保存）
oidc-spec-requirement-extractor \
  "https://openid.net/specs/openid-connect-core-1_0.html" \
  "OpenID Connect Core 1.0" \
  "oidc-core-requirements.yaml"
```

#### 例2: OAuth 2.0 PKCE (RFC 7636) の要件抽出

```bash
oidc-spec-requirement-extractor \
  "https://www.rfc-editor.org/rfc/rfc7636.html" \
  "OAuth 2.0 PKCE (RFC 7636)" \
  "documentation/requirements/oauth2-pkce-requirements.yaml"
```

#### 例3: OAuth 2.0 PAR (RFC 9126) の要件抽出（既に実行済み）

```bash
oidc-spec-requirement-extractor \
  "https://www.rfc-editor.org/rfc/rfc9126.html" \
  "OAuth 2.0 Pushed Authorization Requests (RFC 9126)" \
  "documentation/requirements/oauth2-par-requirements.yaml"
```

### 蓄積される要件ファイル

`documentation/requirements/` ディレクトリに以下のファイルが蓄積されます：

```
documentation/requirements/
├── README.md                            # インデックス（自動更新）
├── oidc-core-requirements.yaml          # OpenID Connect Core 1.0
├── oidc-discovery-requirements.yaml     # OpenID Connect Discovery 1.0
├── oauth2-pkce-requirements.yaml        # OAuth 2.0 PKCE (RFC 7636)
├── fapi-2.0-requirements.yaml           # FAPI 2.0 Security Profile ✅
├── oauth2-par-requirements.yaml         # OAuth 2.0 PAR (RFC 9126) ✅
├── oauth2-dpop-requirements.yaml        # OAuth 2.0 DPoP (RFC 9449)
├── oauth2-mtls-requirements.yaml        # OAuth 2.0 MTLS (RFC 8705)
├── jwt-bcp-requirements.yaml            # JWT Best Practices (RFC 8725)
└── ciba-requirements.yaml               # CIBA
```

**デフォルト出力先**: `documentation/requirements/{spec-slug}-requirements.yaml`

## 実装手順

### 1. 仕様書取得

WebFetchツールを使用して仕様書を取得します。

```javascript
// WebFetch with detailed extraction prompt
WebFetch({
  url: spec_url,
  prompt: `
    Extract ALL requirements from this ${spec_name} specification.
    For each requirement:
    1. Extract the exact requirement text containing MUST, SHALL, SHOULD, MAY, or OPTIONAL
    2. Identify the section number (e.g., "3.1.2.1")
    3. Categorize by topic (e.g., "Authentication Request", "Token Request", "UserInfo", "ID Token", etc.)
    4. Distinguish between:
       - MUST/SHALL requirements (mandatory)
       - SHOULD requirements (recommended)
       - MAY/OPTIONAL requirements (optional)

    Provide a comprehensive list in structured format with:
    - Section: [section number]
    - Category: [category name]
    - Type: [MUST/SHALL | SHOULD | MAY/OPTIONAL]
    - Requirement: [requirement text]
    - Rationale: [brief explanation]
  `
})
```

### 2. 要件の分類

抽出した要件を以下の観点で分類します：

#### 要件レベル（RFC 2119準拠）

- **MUST / SHALL**: 必須要件（実装必須）
- **SHOULD**: 推奨要件（特別な理由がない限り実装すべき）
- **MAY / OPTIONAL**: 任意要件（実装は任意）
- **MUST NOT / SHALL NOT**: 禁止事項

#### カテゴリ分類（仕様書に応じて調整）

OpenID Connect Core 1.0 の場合：

- **ID Token**: トークン構造、クレーム、署名・暗号化
- **Authorization Code Flow**: 認可エンドポイント、トークンエンドポイント
- **Implicit Flow**: フラグメント応答、nonce要件
- **Hybrid Flow**: 複合フロー
- **ID Token Validation**: 検証ルール
- **Claims & UserInfo**: クレーム取得
- **Request Objects**: JWT要求パラメータ
- **Client Authentication**: クライアント認証方式
- **Security**: TLS、エントロピー、CSRF対策
- **Privacy**: PII取り扱い

### 3. 構造化YAMLレポート生成

以下の形式でYAMLレポートを生成します：

```yaml
spec_name: "OpenID Connect Core 1.0"
spec_url: "https://openid.net/specs/openid-connect-core-1_0.html"
extraction_timestamp: "2026-01-29T13:35:03"
extractor_version: "1.0.0"

summary:
  total_requirements: 175
  must_shall_requirements: 135
  should_requirements: 25
  optional_requirements: 15

sections_covered:
  - "Section 1: Introduction & Fundamentals"
  - "Section 2: ID Token"
  - "Section 3.1: Authorization Code Flow"
  # ... 他のセクション

requirements_by_category:
  id_token:
    must_shall:
      - section: "2"
        requirement: "ID Tokens must be signed using JWS"
        rationale: "Provides authentication and integrity"

    should:
      - section: "2"
        requirement: "ID Tokens should not use x5u header"
        rationale: "Use Discovery instead"

  authorization_code_flow:
    must_shall:
      - section: "3.1.2"
        requirement: "Communication must utilize TLS"
        rationale: "Security requirement"
    # ... 他の要件

key_findings:
  critical_must_requirements:
    - "TLS 1.2+ must be used for all communications"
    - "ID Tokens must be signed using JWS"
    # ... 他のクリティカル要件

  security_critical:
    - "CSRF and Clickjacking protection must be employed"
    # ... 他のセキュリティ要件

  flow_differences:
    authorization_code:
      - "response_type: code"
      - "nonce: OPTIONAL"
    implicit:
      - "response_type: id_token or id_token token"
      - "nonce: REQUIRED"
    hybrid:
      - "response_type: code id_token, code token, or code id_token token"
      - "nonce: REQUIRED (conditional)"

test_mapping:
  e2e_tests:
    must_requirements: "必須テストケース"
    should_requirements: "推奨テストケース"
    security_requirements: "negative テストケース"
```

### 4. エラーハンドリング

以下のエラーケースに対応します：

```yaml
error_handling:
  - case: "仕様書URLが無効"
    action: "URLの形式を確認し、httpsスキームを使用"
    message: "Invalid spec URL. Please provide a valid https URL."

  - case: "仕様書が取得できない"
    action: "ネットワーク接続を確認し、URLの存在を確認"
    message: "Failed to fetch specification. Please check URL and network connection."

  - case: "要件が見つからない"
    action: "仕様書の形式を確認し、プロンプトを調整"
    message: "No requirements found. The specification may use non-standard terminology."

  - case: "出力パスが無効"
    action: "ディレクトリの存在を確認し、書き込み権限を確認"
    message: "Invalid output path. Please check directory and permissions."
```

## 出力形式

### YAMLレポート構造

```
requirements_report.yaml
├── spec_name          # 仕様書名
├── spec_url           # 仕様書URL
├── extraction_timestamp  # 抽出日時
├── summary            # 要件統計
├── sections_covered   # カバーしたセクション
├── requirements_by_category  # カテゴリ別要件
│   ├── [category_name]
│   │   ├── must_shall    # 必須要件
│   │   ├── should        # 推奨要件
│   │   └── optional      # 任意要件
├── key_findings       # 重要な発見事項
└── test_mapping       # テストマッピング
```

### 各要件のフォーマット

```yaml
- section: "3.1.2.1"
  requirement: "OpenID Connect requests must contain openid scope value"
  rationale: "If absent, behavior is unspecified"
  test_implication: "必須テストケース：scope=openidの検証"
```

## 活用シーン

### 1. E2Eテスト設計

```
要件抽出 → テストケース生成 → E2Eテスト実装
```

- MUST要件 → 必須テストケース
- SHOULD要件 → 推奨テストケース
- セキュリティ要件 → negative テストケース

### 2. 仕様書レビュー

```
要件抽出 → 実装との比較 → ギャップ分析
```

- 実装済み要件の確認
- 未実装要件の特定
- 優先度付け

### 3. RFC準拠チェック

```
要件抽出 → 自動チェック → 準拠レポート
```

- 必須要件の準拠確認
- 推奨要件の実装状況確認
- 準拠レベルの評価

### 4. ドキュメント作成

```
要件抽出 → カテゴリ分類 → ドキュメント生成
```

- 実装ガイド作成
- テスト戦略ドキュメント作成
- コンプライアンスレポート作成

## 拡張性

### カスタムカテゴリ定義

仕様書に応じてカテゴリを定義できます：

```yaml
custom_categories:
  # OAuth 2.0 の場合
  - "Token Endpoint"
  - "Authorization Endpoint"
  - "Client Authentication"

  # FAPI の場合
  - "FAPI Baseline"
  - "FAPI Advanced"
  - "JARM (JWT Secured Authorization Response Mode)"

  # CIBA の場合
  - "Backchannel Authentication"
  - "Polling Mode"
  - "Push Mode"
```

### 出力フォーマットのカスタマイズ

```yaml
output_formats:
  - yaml    # デフォルト
  - json    # JSON形式
  - md      # Markdown形式
  - csv     # CSV形式（スプレッドシート用）
```

## 制限事項

1. **仕様書の形式**: HTML形式の仕様書を想定（PDF等は未対応）
2. **言語**: 英語の仕様書を想定（多言語対応は今後の拡張）
3. **要件表記**: RFC 2119準拠の表記を想定（MUST/SHOULD等）

## トラブルシューティング

### 問題: 要件が正しく抽出されない

**原因**: 仕様書が標準的なRFC 2119表記を使用していない

**解決策**: プロンプトを調整して、仕様書特有の表記に対応

```
例: "is required" → MUST
    "is recommended" → SHOULD
    "is optional" → MAY
```

### 問題: カテゴリ分類が不適切

**原因**: 仕様書の構造が想定と異なる

**解決策**: カスタムカテゴリを定義して再実行

### 問題: 要件数が少ない

**原因**: WebFetchの結果が truncate された

**解決策**: セクションごとに分割して抽出し、マージ

## インデックスファイル

要件ファイルの一覧は `documentation/requirements/README.md` で管理されます。

### インデックスの構造

```markdown
# OIDC/OAuth 2.0 仕様書要件インデックス

最終更新: 2026-01-29

## 抽出済み仕様書

| 仕様書名 | ファイル | 要件数 | MUST | SHOULD | 抽出日 |
|---------|---------|--------|------|--------|--------|
| FAPI 2.0 Security Profile | fapi-2.0-requirements.yaml | 94 | 76 | 11 | 2026-01-29 |
| OAuth 2.0 PAR (RFC 9126) | oauth2-par-requirements.yaml | 31 | 15 | 6 | 2026-01-29 |

## 未抽出の重要仕様書

- [ ] OpenID Connect Discovery 1.0
- [ ] OAuth 2.0 DPoP (RFC 9449)
- [ ] OAuth 2.0 MTLS (RFC 8705)
```

新しい要件ファイルを生成したら、手動でREADME.mdを更新してください（将来的には自動化予定）。

## バージョン履歴

- **v1.1.0** (2026-01-29): ファイル管理改善
  - プロジェクトルートへの保存
  - 自動ファイル名生成
  - インデックスファイル自動更新

- **v1.0.0** (2026-01-29): 初版リリース
  - OpenID Connect Core 1.0 要件抽出対応
  - 構造化YAMLレポート出力
  - カテゴリ分類機能

## ライセンス

このスキルはMITライセンスの下で公開されています。

## 貢献

改善提案やバグ報告は、GitHubのIssueで受け付けています。

## 関連スキル

- `e2e-test-generator`: E2Eテストケース生成
- `spec-compliance-checker`: 仕様準拠チェック
- `rfc-reader`: RFC自動読み込み・要約
