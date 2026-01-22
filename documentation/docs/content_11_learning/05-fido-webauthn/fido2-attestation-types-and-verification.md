---
sidebar_position: 10
---

# Attestation - 認証器の信頼性証明

---

## 概要

**Attestation（アテステーション）** は、登録時に認証器が「自分が正規の認証器である」ことを証明する仕組みです。

**このドキュメントで学べること**:
- Attestation の目的と必要性
- Attestation Conveyance Preference（none, indirect, direct, enterprise）
- Attestation Statement Format（packed, tpm, android-key, fido-u2f, apple, none）
- 各タイプでの attestationObject の構造差異
- RP 側での検証処理の違い
- エンタープライズでの使い分けとプライバシーの考慮

---

## Attestation とは

### 基本概念

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Attestation の役割                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【問題】                                                                    │
│  RP は登録時に受け取った公開鍵が「本当に正規の認証器で生成されたか」を       │
│  どうやって確認するか？                                                      │
│                                                                             │
│  【解決策: Attestation】                                                     │
│  認証器が製造時に埋め込まれた「Attestation 秘密鍵」で署名することで、        │
│  認証器の正当性を証明する                                                    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        認証器（製造時）                              │   │
│  │  ┌─────────────────────┐  ┌─────────────────────┐                  │   │
│  │  │ Attestation 秘密鍵  │  │ Attestation 証明書  │                  │   │
│  │  │ (製造元が埋め込み)   │  │ (製造元のCA署名)    │                  │   │
│  │  └─────────────────────┘  └─────────────────────┘                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        登録時の流れ                                  │   │
│  │                                                                       │   │
│  │  1. 認証器が新しい鍵ペアを生成                                        │   │
│  │  2. 公開鍵 + authenticatorData を Attestation 秘密鍵で署名            │   │
│  │  3. 署名 + Attestation 証明書 を RP に送信                            │   │
│  │  4. RP は証明書チェーンを検証 → 正規の認証器と確認                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### なぜ Attestation 検証が必要か

Attestation 検証の必要性はサービスの性質によって異なります。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Attestation 検証が不要なケース                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【一般ユーザー向けサービス】（SNS、ECサイト、ブログ等）                       │
│                                                                             │
│  要件: パスワードより安全な認証手段を提供したい                               │
│                                                                             │
│  → Attestation 検証は不要                                                    │
│    - どんな認証器でも「パスワードよりマシ」                                   │
│    - ユーザーのプライバシーを優先（認証器の種類を追跡しない）                  │
│    - 認証器の選択肢を狭めるとユーザー離脱につながる                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                    Attestation 検証が必要なケース                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【ケース1: 企業での認証器管理】                                              │
│  ────────────────────────────────────────────────────────────────────────── │
│  要件: 会社が配布した認証器のみを許可したい                                   │
│                                                                             │
│  課題（Attestation 検証なし）:                                                │
│    - AAGUID は authenticatorData に含まれるので取得は可能                    │
│    - しかし、悪意のあるソフトウェア認証器が AAGUID を詐称できる              │
│    - 「YubiKey の AAGUID」を名乗る偽認証器を検出できない                     │
│                                                                             │
│  解決（Attestation 検証あり）:                                                │
│    - 証明書チェーンを検証し、AAGUID が偽装されていないことを確認             │
│    - YubiKey の Attestation 証明書は Yubico の CA でしか発行されない         │
│    - 偽の AAGUID を持つ認証器は証明書検証で拒否される                        │
│                                                                             │
│  【ケース2: 金融・医療などの高セキュリティ環境】                               │
│  ────────────────────────────────────────────────────────────────────────── │
│  要件: 一定のセキュリティ基準を満たした認証器のみを許可したい                  │
│                                                                             │
│  課題（Attestation 検証なし）:                                                │
│    - AAGUID から認証器の種類はわかる                                         │
│    - しかし、その AAGUID が本物かどうかは検証できない                        │
│    - 監査で「FIDO L2 認定の認証器である」ことを証明できない                  │
│                                                                             │
│  解決（Attestation 検証あり）:                                                │
│    - 証明書チェーンを検証し、AAGUID の正当性を確認                           │
│    - FIDO Metadata Service で認証レベル（L1/L2/L3）を照合                    │
│    - 「ハードウェア保護（L2以上）の認証器のみ」を確実に強制                   │
│                                                                             │
│  【ケース3: 脆弱性発覚時の対応】                                               │
│  ────────────────────────────────────────────────────────────────────────── │
│  要件: 脆弱性が発見された認証器を使っているユーザーを特定したい               │
│                                                                             │
│  補足:                                                                        │
│    - このケースは「登録時に AAGUID を保存していたか」が重要                   │
│    - Attestation 検証をしていれば、AAGUID は信頼できる値として保存済み       │
│    - 検証なしでも AAGUID は取得可能だが、詐称されている可能性がある           │
│                                                                             │
│  解決（Attestation 検証あり）:                                                │
│    - 登録時に検証済みの AAGUID で対象ユーザーを正確に特定                    │
│    - 対象ユーザーのみに通知・クレデンシャル無効化                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### まとめ: Attestation 検証の要否判断

| 質問 | Yes → | No → |
|-----|-------|------|
| 認証器の種類を制限したいか？ | 検証必要 | 不要 |
| FIDO 認定レベルを確認したいか？ | 検証必要 | 不要 |
| 脆弱性発覚時に影響範囲を特定したいか？ | 検証必要 | 不要 |
| ユーザーのプライバシーを最優先するか？ | 検証不要 | - |

---

## Attestation Conveyance Preference

### RP が指定するオプション

登録リクエスト時に RP が `attestation` パラメータで指定します。

```javascript
const options = {
  publicKey: {
    challenge: challenge,
    rp: { id: "example.com", name: "Example" },
    user: { id: userId, name: username, displayName: displayName },
    pubKeyCredParams: [{ type: "public-key", alg: -7 }],

    // Attestation Conveyance Preference
    attestation: "none"  // "none" | "indirect" | "direct" | "enterprise"
  }
};
```

### 4つのオプション

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Attestation Conveyance Preference                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  "none" (デフォルト・最も一般的)                                      │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │  - RP は Attestation を要求しない                                     │   │
│  │  - 認証器は Attestation Statement を省略可能                          │   │
│  │  - AAGUID は 00000000-0000-0000-0000-000000000000 になることがある    │   │
│  │  - プライバシー保護が最優先                                           │   │
│  │  - 用途: 一般向け Web サービス、パスワードレス認証                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  "indirect"                                                           │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │  - RP は Attestation を希望するが、匿名化を許容                       │   │
│  │  - クライアント/認証器は Attestation を匿名化してもよい               │   │
│  │  - Anonymization CA による再署名が可能                                │   │
│  │  - 用途: セキュリティとプライバシーのバランスが必要な場合             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  "direct"                                                             │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │  - RP は完全な Attestation Statement を要求                           │   │
│  │  - 認証器の証明書チェーンがそのまま送信される                         │   │
│  │  - AAGUID が正確に取得可能                                            │   │
│  │  - 用途: 認証器の種類を厳密に管理したい企業環境                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  "enterprise" (Level 2 で追加)                                        │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │  - 企業が管理する認証器向けの特別なモード                             │   │
│  │  - 認証器の個体識別情報を含めることが可能                              │   │
│  │  - RP と認証器の事前合意が必要（RP ID の許可リスト）                  │   │
│  │  - プライバシー: 企業環境では従業員の同意のもと使用                   │   │
│  │  - 用途: 認証器の資産管理、紛失時の特定                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 比較表

| オプション | プライバシー | 認証器特定 | 証明書検証 | 主な用途 |
|-----------|------------|----------|----------|---------|
| `none` | 最高 | 不可 | 不要 | 一般向けサービス |
| `indirect` | 高 | 限定的 | オプション | バランス重視 |
| `direct` | 低 | 可能（モデル） | 推奨 | 企業環境 |
| `enterprise` | 最低 | 可能（個体） | 必須 | 資産管理 |

---

## Attestation Statement Format

### 概要

認証器が生成する Attestation Statement の形式は、認証器の種類によって異なります。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Attestation Statement Format                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  attestationObject (CBOR) の構造:                                            │
│  {                                                                           │
│    "fmt": "packed",           // ← Attestation Statement Format             │
│    "authData": <bytes>,       // authenticatorData                          │
│    "attStmt": {               // ← 形式によって構造が異なる                  │
│      "alg": -7,                                                             │
│      "sig": <bytes>,                                                        │
│      "x5c": [<cert>, ...]     // 証明書チェーン（形式による）               │
│    }                                                                         │
│  }                                                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 各形式の詳細

#### 1. `packed` - 汎用形式

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "packed"                                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  最も一般的な形式。FIDO2 認定認証器の標準。                                  │
│                                                                             │
│  【Full Attestation（証明書あり）】                                          │
│  attStmt: {                                                                  │
│    "alg": -7,                    // ES256 (ECDSA with SHA-256)              │
│    "sig": <signature>,           // authenticatorData + clientDataHash 署名 │
│    "x5c": [                      // 証明書チェーン                          │
│      <attestation cert>,         // 認証器の Attestation 証明書            │
│      <intermediate CA cert>,     // 中間 CA（オプション）                   │
│      ...                                                                    │
│    ]                                                                         │
│  }                                                                           │
│                                                                             │
│  【Self Attestation（証明書なし）】                                          │
│  attStmt: {                                                                  │
│    "alg": -7,                                                               │
│    "sig": <signature>            // 生成した秘密鍵自身で署名                │
│    // x5c なし                                                              │
│  }                                                                           │
│                                                                             │
│  対応認証器: YubiKey, Feitian, SoloKey 等                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 2. `tpm` - TPM 形式

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "tpm"                                                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TPM (Trusted Platform Module) を使用する認証器向け。                        │
│  主に Windows Hello で使用。                                                 │
│                                                                             │
│  attStmt: {                                                                  │
│    "ver": "2.0",                 // TPM バージョン                          │
│    "alg": -7,                                                               │
│    "sig": <signature>,                                                      │
│    "x5c": [<AIK cert>, ...],     // AIK (Attestation Identity Key) 証明書  │
│    "certInfo": <TPMS_ATTEST>,    // TPM 固有の認証情報                      │
│    "pubArea": <TPMT_PUBLIC>      // TPM 公開鍵情報                          │
│  }                                                                           │
│                                                                             │
│  対応認証器: Windows Hello (TPM搭載PC)                                      │
│                                                                             │
│  検証の特徴:                                                                 │
│  - certInfo と pubArea の TPM 固有構造を解析する必要がある                  │
│  - Microsoft の TPM Root CA との証明書チェーン検証                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 3. `android-key` - Android Keystore 形式

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "android-key"                                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Android の Hardware Keystore を使用する認証器向け。                         │
│                                                                             │
│  attStmt: {                                                                  │
│    "alg": -7,                                                               │
│    "sig": <signature>,                                                      │
│    "x5c": [<key cert>, ...]      // Key Attestation 証明書チェーン         │
│  }                                                                           │
│                                                                             │
│  対応認証器: Android (API 24+, Hardware Keystore 搭載)                      │
│                                                                             │
│  検証の特徴:                                                                 │
│  - 証明書の拡張領域に Android 固有の情報が含まれる                          │
│  - securityLevel: Software / TrustedEnvironment / StrongBox                │
│  - Google の Root CA との証明書チェーン検証                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4. `android-safetynet` - SafetyNet 形式（非推奨）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "android-safetynet" (非推奨 - Play Integrity API へ移行)               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Google SafetyNet API を使用した形式。                                       │
│                                                                             │
│  attStmt: {                                                                  │
│    "ver": "...",                 // SafetyNet バージョン                    │
│    "response": <JWS>             // SafetyNet の JWS レスポンス             │
│  }                                                                           │
│                                                                             │
│  注意: SafetyNet Attestation API は 2024年に廃止。                          │
│  新規実装では android-key を使用すること。                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 5. `fido-u2f` - FIDO U2F 形式

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "fido-u2f"                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  FIDO U2F 認証器との後方互換性のための形式。                                  │
│                                                                             │
│  attStmt: {                                                                  │
│    "sig": <signature>,           // U2F 形式の署名                          │
│    "x5c": [<attestation cert>]   // 単一の Attestation 証明書              │
│  }                                                                           │
│                                                                             │
│  対応認証器: 旧世代の U2F 専用キー（YubiKey NEO 等）                         │
│                                                                             │
│  特徴:                                                                       │
│  - alg フィールドがない（常に ES256）                                       │
│  - 署名対象のデータ形式が FIDO2 と若干異なる                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 6. `apple` - Apple 形式

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "apple"                                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Apple デバイス（Touch ID, Face ID）向けの形式。                             │
│                                                                             │
│  attStmt: {                                                                  │
│    "x5c": [<attestation cert>, <Apple WebAuthn Root CA>]                   │
│  }                                                                           │
│                                                                             │
│  対応認証器: macOS Touch ID, iOS Face ID / Touch ID                         │
│                                                                             │
│  特徴:                                                                       │
│  - sig フィールドがない（証明書内に署名情報が含まれる）                      │
│  - Apple WebAuthn Root CA との証明書チェーン検証                            │
│  - 証明書の拡張領域に nonce が含まれる                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 7. `none` - Attestation なし

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  fmt: "none"                                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Attestation Statement を含まない形式。                                      │
│                                                                             │
│  attStmt: {}                     // 空のオブジェクト                        │
│                                                                             │
│  使用ケース:                                                                 │
│  - RP が attestation="none" を指定した場合                                  │
│  - プライバシーを優先する場合                                                │
│  - 認証器が Attestation に対応していない場合                                │
│                                                                             │
│  注意:                                                                       │
│  - AAGUID は 00000000-0000-0000-0000-000000000000 になることがある          │
│  - 認証器の正当性は検証できない                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Format 比較表

| Format | 主な認証器 | 証明書チェーン | 特殊な検証 |
|--------|----------|--------------|----------|
| `packed` | YubiKey, Feitian | あり/なし | 標準的 |
| `tpm` | Windows Hello | あり | TPM構造解析 |
| `android-key` | Android | あり | 拡張領域解析 |
| `android-safetynet` | Android (旧) | なし (JWS) | JWS検証 |
| `fido-u2f` | U2F キー | あり | U2F署名形式 |
| `apple` | macOS/iOS | あり | nonce検証 |
| `none` | - | なし | 不要 |

---

## attestationObject の構造比較

### 各形式での構造

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    attestationObject 構造比較                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【packed (Full Attestation)】                                               │
│  {                                                                           │
│    fmt: "packed",                                                           │
│    authData: <rpIdHash(32) + flags(1) + signCount(4) + attestedCredData>,  │
│    attStmt: { alg: -7, sig: <bytes>, x5c: [<cert>] }                       │
│  }                                                                           │
│                                                                             │
│  【packed (Self Attestation)】                                               │
│  {                                                                           │
│    fmt: "packed",                                                           │
│    authData: <rpIdHash(32) + flags(1) + signCount(4) + attestedCredData>,  │
│    attStmt: { alg: -7, sig: <bytes> }  // x5c なし                         │
│  }                                                                           │
│                                                                             │
│  【tpm】                                                                     │
│  {                                                                           │
│    fmt: "tpm",                                                              │
│    authData: <...>,                                                         │
│    attStmt: {                                                               │
│      ver: "2.0", alg: -7, sig: <bytes>,                                    │
│      x5c: [<AIK cert>],                                                    │
│      certInfo: <TPMS_ATTEST>,                                              │
│      pubArea: <TPMT_PUBLIC>                                                │
│    }                                                                         │
│  }                                                                           │
│                                                                             │
│  【apple】                                                                   │
│  {                                                                           │
│    fmt: "apple",                                                            │
│    authData: <...>,                                                         │
│    attStmt: { x5c: [<attestation cert>, <Apple Root CA>] }  // sig なし    │
│  }                                                                           │
│                                                                             │
│  【none】                                                                    │
│  {                                                                           │
│    fmt: "none",                                                             │
│    authData: <...>,                                                         │
│    attStmt: {}                        // 空                                 │
│  }                                                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## RP 側での検証処理

### 検証フロー概要

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Attestation 検証フロー                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  attestationObject 受信                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────────────┐                                                    │
│  │ 1. CBOR デコード    │                                                    │
│  │    fmt, authData,   │                                                    │
│  │    attStmt を抽出   │                                                    │
│  └──────────┬──────────┘                                                    │
│             │                                                               │
│             ▼                                                               │
│  ┌─────────────────────┐                                                    │
│  │ 2. authData 検証    │                                                    │
│  │    - rpIdHash       │                                                    │
│  │    - flags (UP, UV) │                                                    │
│  │    - 公開鍵抽出     │                                                    │
│  └──────────┬──────────┘                                                    │
│             │                                                               │
│             ▼                                                               │
│  ┌─────────────────────┐                                                    │
│  │ 3. clientDataJSON   │                                                    │
│  │    検証             │                                                    │
│  │    - type           │                                                    │
│  │    - challenge      │                                                    │
│  │    - origin         │                                                    │
│  └──────────┬──────────┘                                                    │
│             │                                                               │
│             ▼                                                               │
│  ┌─────────────────────┐     ┌────────────────────────────────────────────┐│
│  │ 4. fmt に応じた     │────>│ fmt="none"    → 検証スキップ              ││
│  │    attStmt 検証     │     │ fmt="packed"  → 署名検証 + 証明書チェーン  ││
│  │                     │     │ fmt="tpm"     → TPM構造解析 + 証明書      ││
│  │                     │     │ fmt="apple"   → Apple固有検証             ││
│  └──────────┬──────────┘     └────────────────────────────────────────────┘│
│             │                                                               │
│             ▼                                                               │
│  ┌─────────────────────┐                                                    │
│  │ 5. (オプション)     │                                                    │
│  │    MDS で AAGUID    │                                                    │
│  │    のメタデータ確認 │                                                    │
│  └──────────┬──────────┘                                                    │
│             │                                                               │
│             ▼                                                               │
│  ┌─────────────────────┐                                                    │
│  │ 6. クレデンシャル   │                                                    │
│  │    保存             │                                                    │
│  └─────────────────────┘                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### fmt 別の検証処理

#### `none` の検証

```java
// fmt="none" の場合、attStmt 検証はスキップ
if ("none".equals(fmt)) {
    // attStmt が空であることを確認
    if (!attStmt.isEmpty()) {
        throw new AttestationException("attStmt must be empty for fmt=none");
    }
    // 検証完了（Attestation の信頼性は保証されない）
    return AttestationResult.none();
}
```

#### `packed` の検証

```java
// fmt="packed" の場合
if ("packed".equals(fmt)) {
    int alg = attStmt.getInt("alg");
    byte[] sig = attStmt.getBytes("sig");

    if (attStmt.containsKey("x5c")) {
        // Full Attestation: 証明書チェーン検証
        List<X509Certificate> x5c = attStmt.getCertificates("x5c");

        // 1. 署名検証（attestation cert の公開鍵で）
        verifySignature(x5c.get(0).getPublicKey(), sig, signedData, alg);

        // 2. 証明書チェーン検証（MDS の Root CA まで）
        verifyCertificateChain(x5c, trustedRootCAs);

        // 3. 証明書の AAGUID が authData の AAGUID と一致することを確認
        verifyAaguidMatch(x5c.get(0), authData.getAaguid());

        return AttestationResult.basic(x5c);
    } else {
        // Self Attestation: 生成された公開鍵で署名検証
        verifySignature(credentialPublicKey, sig, signedData, alg);

        return AttestationResult.self();
    }
}
```

#### `apple` の検証

```java
// fmt="apple" の場合
if ("apple".equals(fmt)) {
    List<X509Certificate> x5c = attStmt.getCertificates("x5c");

    // 1. Apple WebAuthn Root CA との証明書チェーン検証
    verifyCertificateChain(x5c, appleWebAuthnRootCA);

    // 2. 証明書の拡張領域から nonce を抽出
    byte[] nonce = extractNonceFromCert(x5c.get(0));

    // 3. nonce = SHA256(authData || clientDataHash) を検証
    byte[] expectedNonce = sha256(concat(authData, clientDataHash));
    if (!Arrays.equals(nonce, expectedNonce)) {
        throw new AttestationException("Nonce mismatch");
    }

    // 4. 証明書の公開鍵 == credential の公開鍵 を検証
    if (!x5c.get(0).getPublicKey().equals(credentialPublicKey)) {
        throw new AttestationException("Public key mismatch");
    }

    return AttestationResult.apple(x5c);
}
```

---

## プライバシーの考慮

### Attestation とプライバシーのトレードオフ

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    プライバシー vs セキュリティ                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  プライバシーリスク                        セキュリティメリット              │
│  ─────────────────                        ────────────────────              │
│                                                                             │
│  【AAGUID の露出】                                                           │
│  - 認証器のモデルが特定可能               - 脆弱な認証器を拒否可能          │
│  - ユーザーの購買傾向が推測可能           - 認定認証器のみ許可可能          │
│                                                                             │
│  【証明書チェーンの露出】                                                    │
│  - 認証器の製造バッチが特定可能           - 認証器の正当性を検証可能        │
│  - 同一認証器の使い回しが検出可能         - 偽造認証器を検出可能            │
│                                                                             │
│  【Enterprise Attestation】                                                  │
│  - 認証器の個体が特定可能                 - 資産管理が可能                  │
│  - ユーザーの行動追跡が可能               - 紛失・盗難時の対応が容易        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 推奨される使い分け

| ユースケース | 推奨 attestation | 理由 |
|------------|-----------------|------|
| **一般向け Web サービス** | `none` | プライバシー優先、認証器の種類を問わない |
| **企業の社内システム** | `direct` | 許可された認証器のみ使用を強制 |
| **金融・医療** | `direct` | FIDO 認定レベルを確認 |
| **政府・防衛** | `enterprise` | 認証器の個体管理が必要 |
| **消費者向け銀行アプリ** | `none` または `indirect` | UX とセキュリティのバランス |

---

## エンタープライズでの実装例

### 認証器ポリシーの設定

```yaml
# attestation-policy.yaml
attestation:
  # Attestation Conveyance Preference
  conveyance: "direct"

  # 許可する Attestation Statement Format
  allowed_formats:
    - "packed"
    - "tpm"
    - "apple"

  # FIDO MDS 連携
  mds:
    enabled: true
    url: "https://mds3.fidoalliance.org/"
    min_certification_level: "FIDO_CERTIFIED_L1"

  # AAGUID ベースのポリシー
  aaguid_policy:
    mode: "allowlist"  # allowlist | denylist | any
    allowlist:
      - "ee882879-721c-4913-9775-3dfcce97072a"  # YubiKey 5 NFC
      - "c5ef55ff-ad9a-4b9f-b580-adebafe026d0"  # YubiKey 5Ci
      - "08987058-cadc-4b81-b6e1-30de50dcbe96"  # Windows Hello
    denylist:
      - "00000000-0000-0000-0000-000000000000"  # Unknown/None
```

### 検証実装例

```java
public class AttestationVerifier {

    private final AttestationPolicy policy;
    private final MetadataService mds;

    public AttestationResult verify(AttestationObject attestationObject) {
        String fmt = attestationObject.getFormat();

        // 1. Format チェック
        if (!policy.isFormatAllowed(fmt)) {
            throw new PolicyViolationException(
                "Attestation format not allowed: " + fmt);
        }

        // 2. Format 別の検証
        AttestationResult result = verifyByFormat(attestationObject);

        // 3. AAGUID ポリシーチェック
        String aaguid = attestationObject.getAuthData().getAaguid();
        if (!policy.isAaguidAllowed(aaguid)) {
            throw new PolicyViolationException(
                "Authenticator not allowed: " + aaguid);
        }

        // 4. MDS でメタデータ確認
        if (policy.isMdsEnabled()) {
            MetadataStatement metadata = mds.findByAaguid(aaguid);

            // 認証レベルチェック
            if (metadata.getCertificationLevel() < policy.getMinLevel()) {
                throw new PolicyViolationException(
                    "Authenticator certification level too low");
            }

            // 脆弱性チェック
            if (metadata.hasSecurityIssue()) {
                throw new PolicyViolationException(
                    "Authenticator has known security issues: " +
                    metadata.getStatusReports());
            }
        }

        return result;
    }
}
```

---

## まとめ

### Attestation の要点

| 項目 | 内容 |
|-----|------|
| **目的** | 認証器の正当性を証明 |
| **Conveyance** | none, indirect, direct, enterprise |
| **Format** | packed, tpm, android-key, apple, fido-u2f, none |
| **検証内容** | 署名検証、証明書チェーン、MDS 参照 |

### 選択の指針

```
一般向けサービス
    │
    ├─ プライバシー優先 → attestation="none"
    │
    └─ 認証器管理が必要
           │
           ├─ モデル単位で管理 → attestation="direct"
           │
           └─ 個体単位で管理 → attestation="enterprise"
```

### ベストプラクティス

1. **一般向けサービスでは `none` を使用**
   - ユーザー体験を優先、認証器を問わない

2. **企業環境では `direct` を検討**
   - 許可リストで認証器を管理
   - MDS と連携して脆弱性をモニタリング

3. **Attestation を保存する場合は AAGUID も保存**
   - 後からのポリシー変更、脆弱性対応に対応

4. **Format 別の検証ロジックをライブラリに任せる**
   - webauthn4j, py_webauthn 等の実績あるライブラリを使用

---

## 参考リンク

- [W3C WebAuthn - Attestation](https://www.w3.org/TR/webauthn-2/#sctn-attestation)
- [W3C WebAuthn - Attestation Statement Formats](https://www.w3.org/TR/webauthn-2/#sctn-defined-attestation-formats)
- [FIDO Metadata Service](https://fidoalliance.org/metadata/)
- [Apple - Supporting Passkeys](https://developer.apple.com/documentation/authenticationservices/public-private_key_authentication/supporting_passkeys)
- [Android - Key Attestation](https://developer.android.com/training/articles/security-key-attestation)
