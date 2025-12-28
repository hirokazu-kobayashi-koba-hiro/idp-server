---
sidebar_position: 10
---

# W3C Verifiable Credentials Data Model

このドキュメントでは、VC のデータ構造を「最小限の VC」から始めて段階的に理解していきます。

---

## まず結論：VC は「署名付き JSON」

VC の本質はシンプルです。

```
VC = 主張（クレーム）+ 発行者情報 + デジタル署名

例: 「山田太郎は21歳以上である」という主張
    + 「公安委員会が発行した」という情報
    + 公安委員会のデジタル署名
```

これを JSON で表現したものが VC Data Model です。

---

## 最小限の VC から理解する

### Step 1: 最もシンプルな VC

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiableCredential"],
  "issuer": "did:example:issuer",
  "credentialSubject": {
    "id": "did:example:holder",
    "ageOver21": true
  }
}
```

たった4つのフィールドで VC になります。

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  @context ─────► 「これは VC です」という宣言               │
│                                                             │
│  type ─────────► 「検証可能なクレデンシャル」という種類     │
│                                                             │
│  issuer ───────► 「did:example:issuer が発行した」          │
│                                                             │
│  credentialSubject                                          │
│    └── id ─────► 「did:example:holder について」            │
│    └── ageOver21 ► 「21歳以上である」という主張              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Step 2: 署名を追加

署名がないと「検証可能」になりません。

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiableCredential"],
  "issuer": "did:example:issuer",
  "credentialSubject": {
    "id": "did:example:holder",
    "ageOver21": true
  },
  "proof": {
    "type": "DataIntegrityProof",
    "verificationMethod": "did:example:issuer#key-1",
    "proofValue": "z3FXQjecWufY46..."
  }
}
```

```
proof が追加されたことで:

  検証者 ────► issuer の公開鍵を取得
          ────► proofValue を検証
          ────► 「本当に issuer が発行したか」を確認できる！
```

### Step 3: 有効期限を追加

いつからいつまで有効かを指定します。

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiableCredential"],
  "issuer": "did:example:issuer",
  "validFrom": "2024-01-01T00:00:00Z",
  "validUntil": "2029-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "did:example:holder",
    "ageOver21": true
  },
  "proof": { ... }
}
```

```
有効期間のチェック:

  validFrom ──────────────────────── validUntil
      │                                  │
      │    ← この期間内なら有効 →         │
      │                                  │
  2024/1/1                           2029/1/1

  検証時:
    現在時刻が validFrom より後 かつ validUntil より前
    → 有効！
```

### Step 4: 失効確認を追加

「途中で無効にしたい」場合に必要です。

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiableCredential"],
  "issuer": "did:example:issuer",
  "validFrom": "2024-01-01T00:00:00Z",
  "validUntil": "2029-01-01T00:00:00Z",
  "credentialSubject": {
    "id": "did:example:holder",
    "ageOver21": true
  },
  "credentialStatus": {
    "type": "BitstringStatusListEntry",
    "statusListCredential": "https://issuer.example/status/1",
    "statusListIndex": "12345"
  },
  "proof": { ... }
}
```

```
なぜ失効確認が必要？

  例: スマホを紛失した場合

  1. 保持者が発行者に連絡
  2. 発行者が status list を更新
     status[12345] = 1 (失効)
  3. 検証者がチェック
     → 「失効している」と判定
```

---

## 各フィールドの詳細

### @context：「これは何か」を定義

**なぜ必要？**

JSON だけでは `ageOver21` が何を意味するかわかりません。

```json
// これだけ見ても意味不明
{ "ageOver21": true }

// @context があると意味が定義される
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "ageOver21": true
}
// → 「W3C の VC 仕様における ageOver21」という意味
```

**ルール:**
- 最初は必ず `https://www.w3.org/ns/credentials/v2`
- 追加の語彙を使う場合は配列で追加

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://example.gov/credentials/driver-license/v1"
  ]
}
```

---

### type：クレデンシャルの種類

**なぜ必要？**

「これはどんな種類のクレデンシャルか」を示します。

```json
{
  "type": ["VerifiableCredential", "UniversityDegreeCredential"]
}
```

```
type の意味:

  VerifiableCredential ───► 必須。「これは VC です」
  UniversityDegreeCredential ► 追加。「大学の学位証明です」

検証者は type を見て:
  「この VC は大学の学位証明だな」
  「必要なフィールドが含まれているはず」
  と判断できる
```

---

### issuer：誰が発行したか

**文字列形式:**

```json
{
  "issuer": "did:example:university"
}
```

**オブジェクト形式（追加情報付き）:**

```json
{
  "issuer": {
    "id": "did:example:university",
    "name": "東京大学"
  }
}
```

```
issuer の検証:

  1. issuer の DID を解決
  2. DID Document から公開鍵を取得
  3. proof の署名を検証
  4. 「本当にこの issuer が署名したか」を確認
```

**信頼の問題:**

```
issuer が「東京大学」を名乗っているからといって
本当に東京大学とは限らない！

→ 検証者は「信頼できる issuer のリスト」を持つ必要がある
→ または「信頼できるルート」からの証明書チェーン
```

---

### credentialSubject：誰について、何を主張するか

**シンプルな例:**

```json
{
  "credentialSubject": {
    "id": "did:example:holder",
    "ageOver21": true
  }
}
```

```
credentialSubject の構造:

  id ──────► 「誰について」の主張か
  その他 ──► 「何を」主張するか

  この例:
    「did:example:holder は 21歳以上である」
```

**複雑な例（学位証明）:**

```json
{
  "credentialSubject": {
    "id": "did:example:graduate",
    "name": "山田花子",
    "degree": {
      "type": "BachelorDegree",
      "name": "工学士（情報工学）",
      "college": "工学部"
    },
    "graduationDate": "2024-03-25"
  }
}
```

**id がない場合:**

```json
{
  "credentialSubject": {
    "productId": "ABC-123",
    "manufacturer": "Example Corp",
    "certifiedSafe": true
  }
}
```

→ 「製品 ABC-123 は安全基準を満たしている」という主張

---

### proof：署名（最も重要）

**なぜ最も重要？**

proof がなければ「誰でも作れる JSON」に過ぎません。

```json
{
  "proof": {
    "type": "DataIntegrityProof",
    "cryptosuite": "eddsa-rdfc-2022",
    "created": "2024-01-01T00:00:00Z",
    "verificationMethod": "did:example:issuer#key-1",
    "proofPurpose": "assertionMethod",
    "proofValue": "z3FXQjecWufY46yTe5m..."
  }
}
```

**各フィールドの意味:**

```
┌─────────────────────────────────────────────────────────────┐
│  type ──────────► 署名の種類（DataIntegrityProof）         │
│                                                             │
│  cryptosuite ──► 暗号アルゴリズム（eddsa-rdfc-2022）       │
│                   EdDSA + RDF 正規化                        │
│                                                             │
│  created ──────► 署名した日時                               │
│                                                             │
│  verificationMethod                                         │
│        └───────► 検証に使う公開鍵の場所                     │
│                   「did:example:issuer の #key-1」          │
│                                                             │
│  proofPurpose ─► 署名の目的（assertionMethod = 主張）      │
│                                                             │
│  proofValue ───► 実際の署名値（Base58 等でエンコード）      │
└─────────────────────────────────────────────────────────────┘
```

**検証の流れ:**

```
1. verificationMethod の DID を解決
   did:example:issuer → DID Document を取得

2. DID Document から公開鍵を探す
   #key-1 → { "publicKeyJwk": { ... } }

3. cryptosuite を確認
   eddsa-rdfc-2022 → EdDSA + RDF 正規化を使用

4. VC 本体を正規化してハッシュ
   JSON → 正規化 → SHA-256

5. 署名を検証
   proofValue + 公開鍵 + ハッシュ → 有効/無効
```

---

## Verifiable Presentation（VP）

### VP が必要な理由

```
VC だけだと問題がある:

  1. 複数の VC をまとめて提示したい
     → VP で束ねる

  2. 「この VC の持ち主が提示している」証明が必要
     → VP に holder の署名を付ける

  3. リプレイ攻撃を防ぎたい
     → challenge と domain を使う
```

### VP の構造

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiablePresentation"],
  "holder": "did:example:holder",
  "verifiableCredential": [
    { /* VC 1: 運転免許証 */ },
    { /* VC 2: 卒業証明書 */ }
  ],
  "proof": {
    "type": "DataIntegrityProof",
    "verificationMethod": "did:example:holder#key-1",
    "challenge": "abc123xyz",
    "domain": "https://verifier.example.com",
    "proofValue": "z4jArnPwJy..."
  }
}
```

```
VP の構造:

┌─────────────────────────────────────────────────────────────┐
│  Verifiable Presentation                                    │
│                                                             │
│  holder ────────► 「did:example:holder が提示」             │
│                                                             │
│  verifiableCredential                                       │
│  ┌─────────────┐  ┌─────────────┐                          │
│  │     VC 1    │  │     VC 2    │                          │
│  │  (運転免許) │  │  (卒業証明) │                          │
│  └─────────────┘  └─────────────┘                          │
│                                                             │
│  proof ─────────► holder の署名                             │
│    challenge ──► 検証者が発行したランダム値                 │
│    domain ─────► 検証者のドメイン                           │
└─────────────────────────────────────────────────────────────┘
```

### challenge と domain

**リプレイ攻撃とは？**

```
攻撃シナリオ（challenge なし）:

  1. 悪意ある検証者 A が VP を受け取る
  2. A が VP をコピー
  3. A が別のサービス B に VP を提示
  4. B は「holder からの提示」と信じてしまう

  → holder が意図しないサービスに情報が渡る
```

**challenge と domain で防ぐ:**

```
検証者 B                              Wallet
    │                                    │
    │  「VP をください」                 │
    │  challenge="xyz789"                │
    │  domain="verifier-b.example.com"  │
    │ ──────────────────────────────────►│
    │                                    │
    │                   VP を作成        │
    │                   challenge="xyz789"
    │                   domain="verifier-b.example.com"
    │                   + holder の署名  │
    │                                    │
    │  VP                                │
    │ ◄──────────────────────────────────│

検証者 B のチェック:
  ✅ challenge が自分の発行したもの
  ✅ domain が自分のドメイン
  ✅ holder の署名が有効
  → 本人からの正当な提示と確認！

攻撃者が古い VP を再利用しても:
  ❌ challenge が違う
  → 拒否される
```

---

## 検証プロセス（実際の流れ）

### VC の検証

```
┌─────────────────────────────────────────────────────────────┐
│  検証者が VC を受け取った時の処理                            │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  1. JSON として正しいか？                                    │
│     ├── パースできる？                                      │
│     └── 必須フィールド（@context, type, issuer,            │
│         credentialSubject）がある？                         │
│                                                             │
│     ❌ → エラー: 不正な形式                                 │
└─────────────────────────────────────────────────────────────┘
                         │ ✅
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  2. issuer は信頼できるか？                                 │
│     ├── 信頼できる発行者リストに含まれている？              │
│     └── または信頼チェーンが有効？                          │
│                                                             │
│     ❌ → エラー: 信頼できない発行者                         │
└─────────────────────────────────────────────────────────────┘
                         │ ✅
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  3. proof（署名）は有効か？                                 │
│     ├── verificationMethod から公開鍵を取得                 │
│     ├── 署名アルゴリズムを確認                              │
│     └── 署名を検証                                          │
│                                                             │
│     ❌ → エラー: 署名が無効（改ざんまたは偽造）             │
└─────────────────────────────────────────────────────────────┘
                         │ ✅
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 有効期間内か？                                          │
│     ├── 現在時刻 >= validFrom ?                            │
│     └── 現在時刻 <= validUntil ?                           │
│                                                             │
│     ❌ → エラー: 期限切れ or まだ有効ではない               │
└─────────────────────────────────────────────────────────────┘
                         │ ✅
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 失効していないか？                                      │
│     ├── credentialStatus がある場合                         │
│     └── status list を取得して確認                          │
│                                                             │
│     ❌ → エラー: 失効済み                                   │
└─────────────────────────────────────────────────────────────┘
                         │ ✅
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  ✅ 検証成功！                                              │
│     → credentialSubject の内容を信頼できる                  │
└─────────────────────────────────────────────────────────────┘
```

---

## よくある間違いと注意点

### 1. @context の順序

```json
// ❌ 間違い: 基本コンテキストが最初でない
{
  "@context": [
    "https://example.com/my-vocab",
    "https://www.w3.org/ns/credentials/v2"
  ]
}

// ✅ 正しい: 基本コンテキストが最初
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://example.com/my-vocab"
  ]
}
```

### 2. type に VerifiableCredential がない

```json
// ❌ 間違い
{
  "type": ["UniversityDegreeCredential"]
}

// ✅ 正しい
{
  "type": ["VerifiableCredential", "UniversityDegreeCredential"]
}
```

### 3. credentialSubject.id と holder の不一致

```json
// VC
{
  "credentialSubject": {
    "id": "did:example:alice",
    ...
  }
}

// VP
{
  "holder": "did:example:bob",  // ← alice と違う！
  "verifiableCredential": [ ... ]
}

// → 検証者は「なぜ bob が alice の VC を提示している？」と疑問に思うべき
```

### 4. 有効期限の設定

```
長すぎる有効期限のリスク:
  - validUntil: "2099-12-31" ← 75年後！

  問題:
    - 鍵が漏洩しても VC は「有効」のまま
    - 情報が古くなっても更新されない
    - 失効リストの管理が長期間必要

推奨:
  - 運転免許証: 5年程度（物理免許と同様）
  - 一時的な証明: 数時間〜数日
  - 資格証明: 1〜3年
```

---

## データモデルのバージョン

### v1.1 と v2.0 の違い

| 項目 | v1.1 | v2.0 |
|------|------|------|
| コンテキスト | `/2018/credentials/v1` | `/ns/credentials/v2` |
| 有効期間 | `issuanceDate`, `expirationDate` | `validFrom`, `validUntil` |
| 複数の subject | 配列でサポート | より明確にサポート |
| 証明 | `proof` | `DataIntegrityProof` 等 |

### 移行時の注意

```json
// v1.1 形式
{
  "@context": ["https://www.w3.org/2018/credentials/v1"],
  "issuanceDate": "2024-01-01T00:00:00Z",
  "expirationDate": "2029-01-01T00:00:00Z"
}

// v2.0 形式
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "validFrom": "2024-01-01T00:00:00Z",
  "validUntil": "2029-01-01T00:00:00Z"
}
```

---

## まとめ

```
VC Data Model のポイント:

1. 最小限の VC
   @context + type + issuer + credentialSubject

2. 検証可能にするために
   + proof（署名）

3. 実用的にするために
   + validFrom/validUntil（有効期間）
   + credentialStatus（失効確認）

4. 複数を束ねて提示
   → Verifiable Presentation

5. リプレイ攻撃を防ぐ
   → challenge + domain
```

---

## 参考リンク

- [W3C VC Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/)
- [W3C VC Data Model 1.1](https://www.w3.org/TR/vc-data-model/)
- [W3C Data Integrity](https://www.w3.org/TR/vc-data-integrity/)
- [W3C Bitstring Status List](https://www.w3.org/TR/vc-bitstring-status-list/)
