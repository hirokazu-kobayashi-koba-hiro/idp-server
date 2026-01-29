# OIDC/OAuth 2.0 仕様書要件インデックス

最終更新: 2026-01-29

## 概要

このディレクトリには、OpenID Connect / OAuth 2.0 関連仕様書から抽出した要件レポートが格納されています。各ファイルは `{spec-slug}-requirements.yaml` の形式で命名されています。

## 抽出済み仕様書

| 仕様書名 | ファイル | URL | 要件数 | MUST/SHALL | SHOULD | OPTIONAL | 抽出日 |
|---------|---------|-----|--------|-----------|--------|----------|--------|
| FAPI 2.0 Security Profile | [fapi-2.0-requirements.yaml](./fapi-2.0-requirements.yaml) | [Spec](https://openid.net/specs/fapi-security-profile-2_0.html) | 94 | 76 | 11 | 7 | 2026-01-29 |
| OAuth 2.0 PAR (RFC 9126) | [oauth2-par-requirements.yaml](./oauth2-par-requirements.yaml) | [RFC](https://www.rfc-editor.org/rfc/rfc9126.html) | 31 | 15 | 6 | 10 | 2026-01-29 |
| OAuth 2.0 PKCE (RFC 7636) | [oauth2-pkce-requirements.yaml](./oauth2-pkce-requirements.yaml) | [RFC](https://www.rfc-editor.org/rfc/rfc7636.html) | 28 | 14 | 7 | 4 | 2026-01-29 |

**合計**: 3仕様書、153要件

## 未抽出の重要仕様書

### 優先度: 高

- [ ] **OpenID Connect Core 1.0**
  - URL: https://openid.net/specs/openid-connect-core-1_0.html
  - 目的: OIDC基本フロー要件
  - 予想ファイル名: `oidc-core-requirements.yaml`

- [ ] **OpenID Connect Discovery 1.0**
  - URL: https://openid.net/specs/openid-connect-discovery-1_0.html
  - 目的: 自動設定検出要件
  - 予想ファイル名: `oidc-discovery-requirements.yaml`

- [x] ~~**OAuth 2.0 Pushed Authorization Requests (RFC 9126)**~~ ✅ 抽出済み
  - URL: https://www.rfc-editor.org/rfc/rfc9126.html
  - 目的: PAR要件（FAPI 2.0必須）
  - ファイル名: `oauth2-par-requirements.yaml`

- [ ] **OAuth 2.0 Demonstrating Proof of Possession (RFC 9449)**
  - URL: https://www.rfc-editor.org/rfc/rfc9449.html
  - 目的: DPoP要件（FAPI 2.0必須）
  - 予想ファイル名: `oauth2-dpop-requirements.yaml`

- [ ] **OAuth 2.0 Mutual-TLS (RFC 8705)**
  - URL: https://www.rfc-editor.org/rfc/rfc8705.html
  - 目的: MTLS要件（FAPI 2.0必須）
  - 予想ファイル名: `oauth2-mtls-requirements.yaml`

### 優先度: 中

- [x] ~~**OAuth 2.0 PKCE (RFC 7636)**~~ ✅ 抽出済み
  - URL: https://www.rfc-editor.org/rfc/rfc7636.html
  - 目的: PKCEフロー要件（FAPI 2.0必須）
  - ファイル名: `oauth2-pkce-requirements.yaml`

- [ ] **JWT Best Current Practices (RFC 8725)**
  - URL: https://www.rfc-editor.org/rfc/rfc8725.html
  - 目的: JWT署名・検証要件
  - 予想ファイル名: `jwt-bcp-requirements.yaml`

- [ ] **OAuth 2.0 Authorization Server Issuer Identification (RFC 9207)**
  - URL: https://www.rfc-editor.org/rfc/rfc9207.html
  - 目的: issパラメータ要件（FAPI 2.0必須）
  - 予想ファイル名: `oauth2-iss-requirements.yaml`

### 優先度: 低

- [ ] **OpenID Connect for Identity Assurance (IDA)**
  - URL: https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html
  - 目的: 身元確認要件
  - 予想ファイル名: `oidc-ida-requirements.yaml`

- [ ] **OpenID Connect Client-Initiated Backchannel Authentication (CIBA)**
  - URL: https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html
  - 目的: CIBAフロー要件
  - 予想ファイル名: `ciba-requirements.yaml`

- [ ] **OAuth 2.0 Token Introspection (RFC 7662)**
  - URL: https://www.rfc-editor.org/rfc/rfc7662.html
  - 目的: トークンイントロスペクション要件
  - 予想ファイル名: `oauth2-introspection-requirements.yaml`

- [ ] **OAuth 2.0 Token Revocation (RFC 7009)**
  - URL: https://www.rfc-editor.org/rfc/rfc7009.html
  - 目的: トークン失効要件
  - 予想ファイル名: `oauth2-revocation-requirements.yaml`

## 使い方

### 新しい仕様書の要件を抽出

```bash
# スキルを実行
oidc-spec-requirement-extractor \
  "<仕様書URL>" \
  "<仕様書名>" \
  "<出力ファイル名>"

# 例: OpenID Connect Core 1.0
oidc-spec-requirement-extractor \
  "https://openid.net/specs/openid-connect-core-1_0.html" \
  "OpenID Connect Core 1.0" \
  "documentation/requirements/oidc-core-requirements.yaml"
```

### 要件ファイルの構造

各YAMLファイルには以下のセクションが含まれます：

- `summary`: 要件統計（総数、MUST/SHOULD/OPTIONAL）
- `requirements_by_category`: カテゴリ別の要件一覧
- `key_findings`: 重要な発見事項（クリティカル要件、禁止事項等）
- `test_mapping`: E2Eテストへのマッピング
- `compliance_checklist`: コンプライアンスチェックリスト
- `gap_analysis_template`: ギャップ分析テンプレート

### 活用方法

1. **E2Eテスト設計**: `test_mapping` セクションを参照
2. **実装ギャップ分析**: `gap_analysis_template` を使用
3. **仕様準拠確認**: `compliance_checklist` でチェック
4. **優先順位付け**: `key_findings.critical_must_requirements` から着手

## ファイル命名規則

- OpenID Connect: `oidc-{feature}-requirements.yaml`
- OAuth 2.0 RFC: `oauth2-{feature}-requirements.yaml`
- FAPI: `fapi-{version}-requirements.yaml`
- その他: `{spec-slug}-requirements.yaml`

## メンテナンス

- 仕様書が更新された場合は、要件を再抽出
- 新しい仕様書が公開された場合は、優先度を評価して追加
- このインデックスファイルは手動で更新（将来的には自動化予定）

## 関連スキル

- **oidc-spec-requirement-extractor**: 仕様書から要件を抽出
- **e2e-test-generator-from-requirements**: 要件からE2Eテストを生成
