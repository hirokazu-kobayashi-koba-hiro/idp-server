---
sidebar_position: 20
---

# VC フォーマット比較

Verifiable Credentials は複数のフォーマットで表現できます。このドキュメントでは、主要なフォーマット（JWT、JSON-LD、SD-JWT、mdoc）を比較し、それぞれの特徴とユースケースを解説します。

---

## 第1部: 概要編

### なぜ複数のフォーマットがあるのか？

VC は様々なユースケースで使用されるため、それぞれの要件に適したフォーマットが必要です。

```
ユースケースごとの要件:

  Web アプリ向け          モバイルウォレット向け      政府 ID 向け
  ┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────┐
  │ JWT が既に普及   │    │ 選択的開示が必要     │    │ ISO 標準準拠    │
  │ 相互運用性重視   │    │ プライバシー保護     │    │ オフライン検証   │
  │ 既存インフラ活用 │    │ サイズ効率           │    │ 厳格なセキュリティ│
  └─────────────────┘    └─────────────────────┘    └─────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
      JWT VC                  SD-JWT VC                 mdoc
```

### 主要フォーマットの概要

| フォーマット | 標準化組織 | 特徴 |
|-------------|-----------|------|
| JWT VC | W3C + IETF | JWT ベース、広く普及 |
| JSON-LD VC | W3C | セマンティック Web、RDF 互換 |
| SD-JWT VC | IETF | 選択的開示、プライバシー保護 |
| mdoc (mDL) | ISO | モバイル運転免許証、CBOR ベース |

---

## 第2部: 詳細編

### JWT VC

JWT（JSON Web Token）形式で表現された VC。OAuth/OIDC エコシステムとの親和性が高い。

#### 構造

```
eyJhbGciOiJFUzI1NiIsInR5cCI6InZjK3NkLWp3dCJ9.
eyJpc3MiOiJkaWQ6ZXhhbXBsZTp1bml2ZXJzaXR5IiwidmMiOnsiQGNvbnRleHQiOlsi...
K0FXa3VzTVJMazB4LXUxR3ZHZHlSSmNEMVhCdEpwWFE
└────────────────┬─────────────────┘.└────────────┬─────────────────┘.└───┬───┘
              Header                          Payload                 Signature
```

#### ペイロード例

```json
{
  "iss": "did:example:university",
  "sub": "did:example:student123",
  "iat": 1704067200,
  "exp": 1735689600,
  "vc": {
    "@context": [
      "https://www.w3.org/ns/credentials/v2"
    ],
    "type": ["VerifiableCredential", "UniversityDegreeCredential"],
    "credentialSubject": {
      "id": "did:example:student123",
      "degree": {
        "type": "BachelorDegree",
        "name": "Bachelor of Science"
      }
    }
  }
}
```

#### 特徴

| メリット | デメリット |
|---------|-----------|
| 既存の JWT ライブラリを活用 | 選択的開示が困難 |
| OAuth/OIDC との統合が容易 | 全クレームが開示される |
| コンパクトなサイズ | セマンティックな処理が限定的 |
| 広く理解されている | 特定クレームだけの開示不可 |

---

### JSON-LD VC

JSON-LD（Linked Data）形式の VC。セマンティック Web 技術を活用。

#### 構造

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://www.w3.org/ns/credentials/examples/v2"
  ],
  "id": "https://university.example/credentials/3732",
  "type": ["VerifiableCredential", "UniversityDegreeCredential"],
  "issuer": "did:example:university",
  "validFrom": "2024-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "did:example:student123",
    "degree": {
      "type": "BachelorDegree",
      "name": "Bachelor of Science in Computer Science"
    }
  },
  "proof": {
    "type": "DataIntegrityProof",
    "cryptosuite": "eddsa-rdfc-2022",
    "created": "2024-01-01T00:00:00Z",
    "verificationMethod": "did:example:university#key-1",
    "proofPurpose": "assertionMethod",
    "proofValue": "z3FXQjecWufY46...yTe5m"
  }
}
```

#### 特徴

| メリット | デメリット |
|---------|-----------|
| セマンティックな相互運用性 | 処理が複雑 |
| 拡張性が高い | ライブラリが限られる |
| RDF グラフとして処理可能 | サイズが大きくなりがち |
| 標準化された語彙 | JSON-LD 処理の学習コスト |

---

### SD-JWT VC

選択的開示（Selective Disclosure）をサポートする JWT VC。

#### 構造

```
<Issuer-signed JWT>~<Disclosure 1>~<Disclosure 2>~...~<Key Binding JWT>

eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL...~
WyJfc2QiLCJuYW1lIiwiSm9obiBEb2UiXQ~
WyJfc2QiLCJiaXJ0aERhdGUiLCIxOTkwLTAxLTAxIl0~
eyJhbGciOiJFUzI1NiJ9.eyJub25jZSI6Ii4uLiJ9.xxx
```

#### 動作原理

```
発行時:
  発行者 ────► 保持者
           │
           │ SD-JWT（全ての開示可能クレームを含む）
           │ + 各クレームの Disclosure
           │
           ▼
         ┌─────────────────────────────────────┐
         │ JWT: hash(Disclosure1), hash(D2)... │
         │ Disclosure1: ["salt", "name", "John"]│
         │ Disclosure2: ["salt", "age", 30]    │
         │ ...                                  │
         └─────────────────────────────────────┘

提示時:
  保持者 ────► 検証者
           │
           │ SD-JWT + 選択した Disclosure のみ
           │
           ▼
         ┌─────────────────────────────────────┐
         │ JWT: hash(Disclosure1), hash(D2)... │
         │ Disclosure1: ["salt", "name", "John"]│
         │ （Disclosure2 は含めない）           │
         └─────────────────────────────────────┘
           │
           │ 検証者は name のみ知る
           │ age は開示されない
           ▼
```

#### 特徴

| メリット | デメリット |
|---------|-----------|
| 選択的開示が可能 | JWT より複雑 |
| プライバシー保護 | 新しい仕様で普及途上 |
| JWT エコシステムを活用 | 開示数に応じてサイズ増加 |
| Key Binding でホルダー証明 | 実装の成熟度 |

---

### mdoc（mDL）

ISO 18013-5 で定義されたモバイル運転免許証フォーマット。

#### 構造

CBOR（Concise Binary Object Representation）を使用。

```
mdoc 構造:

  ┌─────────────────────────────────────────┐
  │                 mdoc                     │
  │  ┌───────────────────────────────────┐  │
  │  │           docType                  │  │
  │  │  "org.iso.18013.5.1.mDL"          │  │
  │  └───────────────────────────────────┘  │
  │  ┌───────────────────────────────────┐  │
  │  │          issuerSigned             │  │
  │  │  ┌─────────────────────────────┐  │  │
  │  │  │     nameSpaces               │  │  │
  │  │  │  "org.iso.18013.5.1": [...]  │  │  │
  │  │  └─────────────────────────────┘  │  │
  │  │  ┌─────────────────────────────┐  │  │
  │  │  │     issuerAuth              │  │  │
  │  │  │  (COSE signature)           │  │  │
  │  │  └─────────────────────────────┘  │  │
  │  └───────────────────────────────────┘  │
  └─────────────────────────────────────────┘
```

#### データ要素の例

```
nameSpaces:
  "org.iso.18013.5.1":
    - family_name: "Doe"
    - given_name: "John"
    - birth_date: "1990-01-15"
    - issue_date: "2024-01-01"
    - expiry_date: "2029-01-01"
    - issuing_country: "US"
    - issuing_authority: "DMV"
    - document_number: "DL123456789"
    - portrait: (バイナリデータ)
    - driving_privileges: [...]
    - age_over_21: true
```

#### 特徴

| メリット | デメリット |
|---------|-----------|
| 選択的開示をサポート | 複雑な仕様 |
| オフライン検証可能 | CBOR/COSE の知識が必要 |
| NFC/BLE で近接通信 | 政府 ID 向け（汎用性は低い） |
| バイナリで効率的 | 実装難易度が高い |
| ISO 標準で相互運用性 | エコシステムが限定的 |

---

## フォーマット比較表

| 観点 | JWT VC | JSON-LD VC | SD-JWT VC | mdoc |
|------|--------|------------|-----------|------|
| **署名形式** | JWS | Data Integrity | JWS | COSE |
| **エンコード** | Base64URL | JSON | Base64URL | CBOR |
| **選択的開示** | ❌ | △（BBS+） | ✅ | ✅ |
| **オフライン検証** | ✅ | ✅ | ✅ | ✅ |
| **サイズ効率** | ○ | △ | ○ | ◎ |
| **実装の容易さ** | ◎ | △ | ○ | △ |
| **エコシステム** | 広い | 限定的 | 成長中 | 政府 ID |
| **標準化状況** | 確立 | 確立 | 進行中 | 確立 |

### サイズ比較（同じ内容の場合）

```
概算サイズ比較（同一クレーム）:

JWT VC:        ~500 bytes
JSON-LD VC:    ~800 bytes
SD-JWT VC:     ~700 bytes (全開示時)
               ~400 bytes (部分開示時)
mdoc:          ~400 bytes (CBOR の効率性)
```

---

## ユースケース別の推奨

### Web アプリケーション

```
推奨: JWT VC または SD-JWT VC

理由:
- OAuth/OIDC との統合が容易
- 既存の JWT ライブラリを活用可能
- OID4VCI/OID4VP との親和性
```

### モバイルウォレット

```
推奨: SD-JWT VC

理由:
- 選択的開示でプライバシー保護
- QR コードに収まるサイズ
- Key Binding でホルダー証明
```

### 政府発行 ID

```
推奨: mdoc (mDL)

理由:
- ISO 標準で国際的な相互運用性
- オフライン検証
- 厳格なセキュリティ要件
- NFC/BLE での近接提示
```

### エンタープライズ統合

```
推奨: JSON-LD VC

理由:
- セマンティックな相互運用性
- 標準化された語彙
- RDF グラフとしての処理
```

---

## OID4VCI/OID4VP でのサポート

| フォーマット | OID4VCI | OID4VP |
|-------------|---------|--------|
| JWT VC | `jwt_vc_json` | ✅ |
| JSON-LD VC | `ldp_vc` | ✅ |
| SD-JWT VC | `vc+sd-jwt` | ✅ |
| mdoc | `mso_mdoc` | ✅ |

### Credential Format Identifier

```json
{
  "format": "vc+sd-jwt",
  "credential_definition": {
    "type": ["VerifiableCredential", "UniversityDegreeCredential"]
  }
}
```

---

## 選択のガイドライン

```
フォーマット選択のフローチャート:

                      開始
                        │
                        ▼
              選択的開示が必要？
                 │          │
                Yes         No
                 │          │
                 ▼          ▼
          政府 ID 用途？   JWT VC
             │    │
            Yes   No
             │    │
             ▼    ▼
          mdoc   SD-JWT VC

※ セマンティック処理が必要な場合は JSON-LD VC も検討
```

---

## 参考リンク

- [W3C VC Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/)
- [W3C VC Implementation Guidelines](https://www.w3.org/TR/vc-imp-guide/)
- [SD-JWT VC (IETF)](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/)
- [ISO 18013-5 mDL](https://www.iso.org/standard/69084.html)
- [OpenID for Verifiable Credentials](https://openid.net/sg/openid4vc/)
