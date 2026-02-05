# FIDO2/WebAuthn アテステーション検証

## このドキュメントの目的

**FIDO2登録時のアテステーション検証を設定する**ことが目標です。

### 学べること

- アテステーション検証とは何か
- 3つの検証モード（なし、TrustStore、FIDO MDS）
- プラットフォーム認証器の制限事項

### 所要時間
⏱️ **約15分**

### 前提条件
- [パスキー登録](./01-registration.md)が完了していること
- アテステーションの概念を理解していること

---

## アテステーション検証とは

アテステーション（Attestation）は、FIDO2登録時に認証器が「自分が本物である」ことを証明する仕組みです。

```
認証器                              idp-server
  │                                    │
  │──[公開鍵 + アテステーション署名]──>│
  │                                    │
  │         検証: この署名は信頼できる │
  │              認証器から来たものか？ │
  │                                    │
```

### なぜアテステーション検証が必要か

| ユースケース | 理由 |
|:---|:---|
| **企業セキュリティ** | 承認されたセキュリティキーのみ許可 |
| **規制要件** | 特定の認証レベル（FIDO認定）が必要 |
| **リスク管理** | 脆弱性が報告された認証器をブロック |

---

## 3つの検証モード

idp-serverは3つのアテステーション検証モードをサポートします。

```
┌─────────────────────────────────────────────────────────────┐
│                    検証モードの優先順位                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. FIDO MDS（mds.enabled=true）                            │
│     ├── FIDOアライアンスのメタデータサービスを使用           │
│     └── 認証器の信頼性を自動で検証                          │
│                                                             │
│  2. TrustStore（trustStorePath設定）                        │
│     ├── 静的な信頼アンカーで検証                            │
│     └── 特定のベンダー証明書のみ信頼                        │
│                                                             │
│  3. なし（デフォルト）                                       │
│     └── 証明書チェーン検証をスキップ                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## モード1: 検証なし（デフォルト）

アテステーション検証を行いません。開発環境や、あらゆる認証器を許可する場合に使用します。

### 設定

```json
{
  "fido2-registration": {
    "execution": {
      "function": "webauthn4j_registration",
      "details": {
        "rp_id": "example.com",
        "origin": "https://example.com",
        "attestation_preference": "none"
      }
    }
  }
}
```

### 特徴

| 項目 | 内容 |
|:---|:---|
| **メリット** | 設定が簡単、すべての認証器を許可 |
| **デメリット** | 認証器の信頼性を検証できない |
| **推奨環境** | 開発、テスト、一般向けサービス |

---

## モード2: TrustStore検証

特定のベンダーの認証器のみを許可する場合に使用します。

### 設定

```json
{
  "fido2-registration": {
    "execution": {
      "function": "webauthn4j_registration",
      "details": {
        "rp_id": "example.com",
        "origin": "https://example.com",
        "attestation_preference": "direct",
        "trust_store_path": "/path/to/truststore.p12",
        "trust_store_password": "password",
        "trust_store_type": "PKCS12"
      }
    }
  }
}
```

### TrustStoreの作成

YubiKeyの証明書をTrustStoreに追加する例:

```bash
# YubiKeyルート証明書をダウンロード
curl -o yubico-root.pem https://developers.yubico.com/U2F/yubico-u2f-ca-certs.txt

# PKCS12形式のTrustStoreを作成
keytool -importcert \
  -alias yubico-root \
  -file yubico-root.pem \
  -keystore truststore.p12 \
  -storetype PKCS12 \
  -storepass password
```

### 設定パラメータ

| パラメータ | 説明 | デフォルト |
|:---|:---|:---|
| `trust_store_path` | TrustStoreファイルのパス | なし |
| `trust_store_password` | パスワード | なし |
| `trust_store_type` | ストアタイプ | `PKCS12` |

### 特徴

| 項目 | 内容 |
|:---|:---|
| **メリット** | 特定のベンダーのみ許可、静的な管理 |
| **デメリット** | 証明書の手動管理が必要 |
| **推奨環境** | 企業内、特定認証器のみ許可 |

---

## モード3: FIDO Metadata Service（MDS）

FIDOアライアンスが提供するメタデータサービスを使用して、認証器の信頼性を自動で検証します。

### 設定

```json
{
  "fido2-registration": {
    "execution": {
      "function": "webauthn4j_registration",
      "details": {
        "rp_id": "example.com",
        "origin": "https://example.com",
        "attestation_preference": "direct",
        "mds": {
          "enabled": true,
          "cache_ttl_seconds": 86400
        }
      }
    }
  }
}
```

### 設定パラメータ

| パラメータ | 説明 | デフォルト |
|:---|:---|:---|
| `mds.enabled` | MDSを有効化 | `false` |
| `mds.cache_ttl_seconds` | キャッシュTTL（秒） | `86400`（24時間） |

### FIDO MDSの仕組み

```
┌───────────────────────────────────────────────────────────────┐
│  FIDO Metadata Service (MDS)                                  │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  FIDO Alliance ──[BLOB]──> idp-server                         │
│       │                        │                              │
│       │                        ├── AAGUID → メタデータ検索    │
│       │                        ├── 認証器ステータス確認        │
│       │                        └── 証明書チェーン検証          │
│       │                                                       │
│  メタデータに含まれる情報:                                     │
│  - 認証器名、ベンダー                                         │
│  - FIDO認定レベル                                             │
│  - セキュリティステータス（脆弱性情報）                       │
│  - 信頼アンカー証明書                                         │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 特徴

| 項目 | 内容 |
|:---|:---|
| **メリット** | 自動更新、脆弱性情報を含む |
| **デメリット** | ネットワーク接続が必要 |
| **推奨環境** | 本番環境、高セキュリティ要件 |

---

## プラットフォーム認証器の制限

### 重要な注意事項

**macOS Touch ID / iOS Face ID などのプラットフォーム認証器は、`attestation_preference: "direct"` を指定しても `"none"` アテステーションを返すことがあります。**

これはプライバシー保護のための仕様です。

```
┌─────────────────────────────────────────────────────────────┐
│  アテステーション要求と実際の応答                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  セキュリティキー（YubiKey等）:                              │
│    要求: direct → 応答: packed（証明書チェーン付き）        │
│                                                             │
│  プラットフォーム認証器（Touch ID等）:                       │
│    要求: direct → 応答: none（証明書チェーンなし）← 仕様！  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 対応方法

| 要件 | 推奨設定 |
|:---|:---|
| すべての認証器を許可 | `attestation_preference: "none"` |
| セキュリティキーのみ許可 | `attestation_preference: "direct"` + MDS + アプリケーションレベルで検証 |
| プラットフォーム認証器も許可 | `attestation_preference: "direct"` で登録し、`"none"` 応答も許可 |

---

## attestation_preferenceの設定

| 値 | 説明 | ユースケース |
|:---|:---|:---|
| `none` | アテステーションを要求しない | 一般向けサービス |
| `indirect` | 匿名化されたアテステーション | プライバシー重視 |
| `direct` | 完全なアテステーション | 企業向け、高セキュリティ |
| `enterprise` | エンタープライズアテステーション | 企業内限定 |

---

## ログの確認

アテステーション検証のログを確認するには、DEBUGレベルを有効にします。

```
# 正常な検証
webauthn4j attestation verification succeeded: format=packed, aaguid=xxx

# プラットフォーム認証器の場合
webauthn4j attestation: requested 'direct' but received 'none'.
Platform authenticators (Touch ID/Face ID) may not support attestation.

# MDS検証
MDS BLOB configured, using MetadataBLOBBasedTrustAnchorRepository

# TrustStore検証
TrustStore configured, certificate chain verification is enabled
```

---

## 推奨設定

### 一般向けサービス

```json
{
  "attestation_preference": "none"
}
```

### 企業向けサービス（FIDO MDS使用）

```json
{
  "attestation_preference": "direct",
  "mds": {
    "enabled": true,
    "cache_ttl_seconds": 86400
  }
}
```

### 特定ベンダー限定

```json
{
  "attestation_preference": "direct",
  "trust_store_path": "/path/to/truststore.p12",
  "trust_store_password": "password"
}
```

---

## 関連ドキュメント

- [パスキー登録](./01-registration.md) - 登録設定
- [パスキー認証](./02-authentication.md) - 認証API
- [パスワードレス認証](../../../content_03_concepts/03-authentication-authorization/concept-07-passwordless.md) - 概念説明

---

**最終更新**: 2025-01-25
