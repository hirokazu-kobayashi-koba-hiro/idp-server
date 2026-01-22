---
sidebar_position: 9
---

# AAGUID - 認証器のモデル識別子

---

## 概要

**AAGUID（Authenticator Attestation Globally Unique Identifier）** は、WebAuthn/FIDO2 における認証器（Authenticator）のモデルを識別するための128ビットUUIDです。

**このドキュメントで学べること**:
- AAGUID の役割と用途
- AAGUID の取得タイミングと場所
- FIDO Metadata Service（MDS）との連携
- 主要な認証器の AAGUID 一覧
- エンタープライズでの活用パターン

---

## AAGUID とは

### 基本概念

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AAGUID の位置づけ                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  認証器のモデル（製品ライン）                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  YubiKey 5 NFC                                                       │   │
│  │  AAGUID: ee882879-721c-4913-9775-3dfcce97072a                       │   │
│  │                                                                       │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │   │
│  │  │ 個体 A   │  │ 個体 B   │  │ 個体 C   │  │ 個体 D   │  ...       │   │
│  │  │ (同じ    │  │ (同じ    │  │ (同じ    │  │ (同じ    │            │   │
│  │  │  AAGUID) │  │  AAGUID) │  │  AAGUID) │  │  AAGUID) │            │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  【特徴】                                                                    │
│  - 同じモデル・ファームウェアの認証器は同じ AAGUID を共有                    │
│  - 個々の認証器を識別するものではない（それは credentialId）                  │
│  - 製造元が FIDO Alliance に登録して取得                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### AAGUID の特性

| 特性 | 説明 |
|-----|------|
| **形式** | 128ビット UUID（RFC 4122） |
| **発行者** | 認証器の製造元 |
| **一意性** | 製造元 + モデル + ファームウェアバージョン で一意 |
| **用途** | 認証器の種類を識別、メタデータ参照のキー |
| **プライバシー** | 個体識別ではないためプライバシー影響は限定的 |

---

## AAGUID の取得タイミング

### authenticatorData の構造

AAGUID は登録（Registration）時の `authenticatorData` に含まれます。

```
authenticatorData の構造（attestedCredentialData あり）:

┌─────────────────────────────────────────────────────────────────────────────┐
│                         authenticatorData                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┬───────┬───────────┬────────────────────────────────────┐ │
│  │  rpIdHash    │ flags │ signCount │     attestedCredentialData         │ │
│  │  (32 bytes)  │ (1)   │ (4 bytes) │     (variable)                     │ │
│  └──────────────┴───────┴───────────┴────────────────────────────────────┘ │
│                                              │                              │
│                                              ▼                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                    attestedCredentialData                             │ │
│  ├───────────────────────────────────────────────────────────────────────┤ │
│  │  ┌──────────┬─────────────────────┬───────────────┬─────────────────┐ │ │
│  │  │  AAGUID  │ credentialIdLength  │ credentialId  │  publicKey      │ │ │
│  │  │(16 bytes)│     (2 bytes)       │  (variable)   │   (COSE)        │ │ │
│  │  └──────────┴─────────────────────┴───────────────┴─────────────────┘ │ │
│  │       ▲                                                               │ │
│  │       │                                                               │ │
│  │       └─── ここに AAGUID が含まれる                                    │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### flags の詳細

```
flags (1 byte) のビット構成:

┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
│ bit7│ bit6│ bit5│ bit4│ bit3│ bit2│ bit1│ bit0│
│ ED  │ AT  │ 0   │ BS  │ BE  │ 0   │ UV  │ UP  │
└─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
  │     │           │     │           │     │
  │     │           │     │           │     └─ User Present (常に 1)
  │     │           │     │           └─── User Verified (PIN/生体認証)
  │     │           │     └─── Backup Eligible (Level 3)
  │     │           └─── Backup State (Level 3)
  │     └─── Attested credential data included
  │         (1 の場合、AAGUID 等が含まれる)
  └─── Extension data included

【重要】
AT フラグが 1 の場合のみ attestedCredentialData（AAGUID を含む）が存在
```

### 認証時との違い

| タイミング | AAGUID | attestedCredentialData |
|----------|--------|----------------------|
| **登録時** | 含まれる | 含まれる（AT=1） |
| **認証時** | 含まれない | 含まれない（AT=0） |

認証時は credentialId で認証器を特定するため、AAGUID は不要です。

---

## FIDO Metadata Service (MDS)

### MDS とは

FIDO Metadata Service は、FIDO Alliance が運営する認証器メタデータのリポジトリです。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FIDO Metadata Service (MDS)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     MDS エンドポイント                               │   │
│  │            https://mds3.fidoalliance.org/                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                │                                            │
│                                ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     メタデータ BLOB                                   │   │
│  │  (JWT 形式、FIDO Alliance が署名)                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                │                                            │
│                                ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  entries: [                                                          │   │
│  │    {                                                                 │   │
│  │      aaguid: "ee882879-721c-4913-9775-3dfcce97072a",                │   │
│  │      metadataStatement: {                                            │   │
│  │        description: "YubiKey 5 Series with NFC",                    │   │
│  │        authenticatorVersion: 328966,                                 │   │
│  │        protocolFamily: "fido2",                                      │   │
│  │        schema: 3,                                                    │   │
│  │        upv: [{ major: 1, minor: 0 }, { major: 1, minor: 1 }],       │   │
│  │        authenticationAlgorithms: ["secp256r1_ecdsa_sha256_raw"],    │   │
│  │        publicKeyAlgAndEncodings: ["cose"],                          │   │
│  │        attestationTypes: ["basic_full"],                             │   │
│  │        userVerificationDetails: [[...], ...],                       │   │
│  │        keyProtection: ["hardware", "secure_element"],               │   │
│  │        matcherProtection: ["on_chip"],                              │   │
│  │        cryptoStrength: 128,                                          │   │
│  │        attachmentHint: ["external", "wired", "wireless", "nfc"],    │   │
│  │        tcDisplay: [],                                                │   │
│  │        attestationRootCertificates: ["MIID..."],                    │   │
│  │        icon: "data:image/png;base64,..."                            │   │
│  │      },                                                              │   │
│  │      statusReports: [                                                │   │
│  │        { status: "FIDO_CERTIFIED_L1", effectiveDate: "2024-01-15" } │   │
│  │      ]                                                               │   │
│  │    },                                                                │   │
│  │    ...                                                               │   │
│  │  ]                                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### MDS で取得できる情報

| カテゴリ | フィールド | 説明 |
|---------|----------|------|
| **基本情報** | description | 認証器の説明 |
| | icon | アイコン（Base64 PNG） |
| | protocolFamily | fido2, u2f, uaf |
| **セキュリティ** | keyProtection | hardware, secure_element, tee |
| | matcherProtection | software, tee, on_chip |
| | cryptoStrength | 暗号強度（ビット数） |
| **認証レベル** | statusReports | FIDO_CERTIFIED_L1/L2/L3 |
| **脆弱性** | statusReports | REVOKED, USER_VERIFICATION_BYPASS 等 |
| **Attestation** | attestationTypes | basic_full, basic_surrogate, attca |
| | attestationRootCertificates | ルート証明書 |

### MDS の活用例

```java
// AAGUID から認証器情報を取得
MetadataStatement metadata = mdsRepository.findByAaguid(aaguid);

// 認証器の認証レベルを確認
AuthenticatorStatus status = metadata.getLatestStatus();
if (status == AuthenticatorStatus.FIDO_CERTIFIED_L2) {
    // L2 認定の認証器のみ許可
    allowRegistration();
}

// 脆弱性チェック
if (status == AuthenticatorStatus.REVOKED) {
    // 失効した認証器は拒否
    throw new AuthenticatorRevokedException("Authenticator has been revoked");
}
```

---

## 主要な認証器の AAGUID 一覧

### ハードウェアキー

| 製品 | AAGUID |
|-----|--------|
| YubiKey 5 NFC | `ee882879-721c-4913-9775-3dfcce97072a` |
| YubiKey 5Ci | `c5ef55ff-ad9a-4b9f-b580-adebafe026d0` |
| YubiKey 5 Nano | `fa2b99dc-9e39-4257-8f92-4a30d23c4118` |
| YubiKey Bio | `d8522d9f-575b-4866-88a9-ba99fa02f35b` |
| Google Titan (USB-A) | `42b4fb4a-2866-43b2-9bf7-6c6669c2e5d3` |
| Feitian ePass FIDO | `3e22415d-7fdf-4ea4-8a0c-dd60c4249b9d` |

### プラットフォーム認証器

| 製品 | AAGUID |
|-----|--------|
| Windows Hello | `08987058-cadc-4b81-b6e1-30de50dcbe96` |
| macOS Touch ID | `adce0002-35bc-c60a-648b-0b25f1f05503` |
| Android | `b93fd961-f2e6-462f-b122-82002247de78` |
| Chrome on Mac | `adce0002-35bc-c60a-648b-0b25f1f05503` |
| iCloud Keychain | `dd4ec289-e01d-41c9-bb89-70fa845d4bf2` |

### 特殊な AAGUID

| 値 | 意味 |
|---|-----|
| `00000000-0000-0000-0000-000000000000` | Self Attestation（None attestation）の場合 |

---

## エンタープライズでの活用パターン

### パターン1: 認証器の許可リスト

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     認証器の許可リスト管理                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ポリシー設定:                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  allowed_aaguids:                                                    │   │
│  │    - "ee882879-721c-4913-9775-3dfcce97072a"  # YubiKey 5 NFC        │   │
│  │    - "c5ef55ff-ad9a-4b9f-b580-adebafe026d0"  # YubiKey 5Ci          │   │
│  │    - "d8522d9f-575b-4866-88a9-ba99fa02f35b"  # YubiKey Bio          │   │
│  │                                                                       │   │
│  │  # プラットフォーム認証器は禁止（フィッシング耐性の観点）              │   │
│  │  denied_aaguids:                                                     │   │
│  │    - "08987058-cadc-4b81-b6e1-30de50dcbe96"  # Windows Hello         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  登録フロー:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  1. ユーザーが認証器で登録                                            │   │
│  │  2. attestationObject から AAGUID を抽出                              │   │
│  │  3. allowed_aaguids と照合                                            │   │
│  │  4. 一致 → 登録許可 / 不一致 → 登録拒否                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### パターン2: 認証レベル要件

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     認証レベルに基づくアクセス制御                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  FIDO 認証レベル:                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Level 1 (L1): 基本的なセキュリティ要件を満たす                        │   │
│  │  Level 2 (L2): ハードウェア保護、セキュアエレメント                    │   │
│  │  Level 3 (L3): 高度なハードウェアセキュリティ                          │   │
│  │  Level 3+: 追加のセキュリティ認定                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  アクセス制御ポリシー:                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                       │   │
│  │  リソース           │ 必要な認証レベル │ 例                            │   │
│  │  ────────────────────────────────────────────────────────────────── │   │
│  │  一般業務アプリ      │ L1 以上          │ メール、チャット              │   │
│  │  機密データアクセス  │ L2 以上          │ 人事システム、財務データ       │   │
│  │  特権操作            │ L3 以上          │ 管理者操作、証明書発行        │   │
│  │                                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  実装例:                                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  // 認証時に認証器のレベルを確認                                       │   │
│  │  Credential credential = credentialRepository.get(credentialId);      │   │
│  │  String aaguid = credential.getAaguid();                              │   │
│  │  AuthenticatorStatus status = mds.getStatus(aaguid);                  │   │
│  │                                                                       │   │
│  │  if (resource.requiresLevel(Level.L2) &&                             │   │
│  │      status.getLevel() < Level.L2) {                                  │   │
│  │      throw new InsufficientAuthenticatorLevelException();             │   │
│  │  }                                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### パターン3: 脆弱性管理

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     認証器の脆弱性管理                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  MDS statusReports の status 値:                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  FIDO_CERTIFIED          │ 認定済み（正常）                          │   │
│  │  FIDO_CERTIFIED_L1       │ Level 1 認定                              │   │
│  │  FIDO_CERTIFIED_L2       │ Level 2 認定                              │   │
│  │  FIDO_CERTIFIED_L3       │ Level 3 認定                              │   │
│  │  ────────────────────────────────────────────────────────────────── │   │
│  │  ATTESTATION_KEY_COMPROMISE  │ Attestation 鍵の漏洩                  │   │
│  │  USER_VERIFICATION_BYPASS    │ UV バイパスの脆弱性                   │   │
│  │  USER_KEY_REMOTE_COMPROMISE  │ ユーザー鍵のリモート漏洩              │   │
│  │  USER_KEY_PHYSICAL_COMPROMISE│ ユーザー鍵の物理的漏洩                │   │
│  │  REVOKED                     │ 失効（使用禁止）                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  脆弱性対応フロー:                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  1. MDS を定期的に取得（日次推奨）                                     │   │
│  │  2. statusReports の変更を検知                                         │   │
│  │  3. 影響を受ける認証器を特定（AAGUID で検索）                          │   │
│  │  4. ユーザーに通知、再登録を促す                                       │   │
│  │  5. 必要に応じて既存クレデンシャルを無効化                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## DDL での AAGUID 管理

### 推奨構造

AAGUID は認証器関連のメタデータとともに JSONB で管理することを推奨します。

```sql
CREATE TABLE webauthn_credentials
(
    -- Core columns
    id TEXT PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id VARCHAR(256) NOT NULL,
    username VARCHAR(256),
    rp_id VARCHAR(256) NOT NULL,
    public_key TEXT NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,

    -- Authenticator metadata (JSONB)
    authenticator JSONB NOT NULL DEFAULT '{}',
    -- 例:
    -- {
    --   "aaguid": "ee882879-721c-4913-9775-3dfcce97072a",
    --   "transports": ["usb", "nfc"],
    --   "attachment": "cross-platform"
    -- }

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- AAGUID でのクエリ用インデックス
CREATE INDEX idx_credentials_aaguid
ON webauthn_credentials ((authenticator->>'aaguid'));
```

### クエリ例

```sql
-- 特定の AAGUID を持つクレデンシャルを検索
SELECT * FROM webauthn_credentials
WHERE authenticator->>'aaguid' = 'ee882879-721c-4913-9775-3dfcce97072a';

-- 脆弱性のある認証器（AAGUID リスト）を持つユーザーを特定
SELECT DISTINCT user_id FROM webauthn_credentials
WHERE authenticator->>'aaguid' IN (
    'vulnerable-aaguid-1',
    'vulnerable-aaguid-2'
);
```

---

## まとめ

### AAGUID の要点

| 項目 | 内容 |
|-----|------|
| **定義** | 認証器モデルを識別する128ビットUUID |
| **取得タイミング** | 登録時の authenticatorData |
| **用途** | メタデータ参照、ポリシー制御、脆弱性管理 |
| **保存推奨** | JSONB の authenticator オブジェクト内 |

### ベストプラクティス

1. **登録時に AAGUID を保存**
   - 後からの監査・ポリシー適用に必要

2. **MDS との連携**
   - 定期的に MDS を取得して最新の認証器情報を参照

3. **ポリシーの柔軟性**
   - 許可リスト/拒否リストを設定ファイルで管理

4. **脆弱性モニタリング**
   - MDS の statusReports を監視、影響範囲を迅速に特定

---

## 参考リンク

- [W3C WebAuthn - Attested Credential Data](https://www.w3.org/TR/webauthn-2/#attested-credential-data)
- [FIDO Metadata Service](https://fidoalliance.org/metadata/)
- [FIDO MDS API](https://mds3.fidoalliance.org/)
- [FIDO Authenticator Certification Levels](https://fidoalliance.org/certification/authenticator-certification-levels/)
