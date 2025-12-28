---
sidebar_position: 3
---

# Verifiable Credentials 用語集

VC 関連の専門用語を解説します。用語がわからなくなった時に参照してください。

---

## 基本用語

### Verifiable Credential（VC）

**読み方:** ベリファイアブル・クレデンシャル

**意味:** デジタル署名が付いた検証可能な証明書。発行者に問い合わせなくても、署名を検証することで本物かどうか確認できる。

**例:** デジタル運転免許証、デジタル卒業証明書

```
┌─────────────────────────────────┐
│  Verifiable Credential          │
│                                 │
│  内容: 「山田太郎は21歳以上」    │
│  発行者: 公安委員会              │
│  署名: ✅ (発行者のデジタル署名) │
└─────────────────────────────────┘
```

---

### Verifiable Presentation（VP）

**読み方:** ベリファイアブル・プレゼンテーション

**意味:** 1つ以上の VC を検証者に提示するためのコンテナ。保持者の署名が付く。

**例:** 複数の VC をまとめて提示する時に使う

```
┌─────────────────────────────────────────┐
│  Verifiable Presentation                │
│                                         │
│  ┌─────────────┐  ┌─────────────┐      │
│  │ VC: 運転免許 │  │ VC: 卒業証明│      │
│  └─────────────┘  └─────────────┘      │
│                                         │
│  提示者: 山田太郎                        │
│  署名: ✅ (提示者のデジタル署名)         │
└─────────────────────────────────────────┘
```

---

### Issuer（発行者）

**読み方:** イシュアー

**意味:** VC を発行する主体。政府、大学、企業など信頼できる機関。

**例:**
- 運転免許証 → 公安委員会
- 卒業証明書 → 大学
- 従業員証 → 企業

---

### Holder（保持者）

**読み方:** ホルダー

**意味:** VC を持っている人。通常は個人。VC をウォレットアプリで管理する。

**例:** あなた自身

---

### Verifier（検証者）

**読み方:** ベリファイヤー

**意味:** VC の提示を受けて検証する主体。サービス提供者など。

**例:**
- 居酒屋（年齢確認）
- 採用企業（学歴確認）
- 銀行（本人確認）

---

### Wallet（ウォレット）

**読み方:** ウォレット

**意味:** VC を保管・管理するアプリケーション。スマートフォンアプリが一般的。

**例:** スマホの Wallet アプリ、認証アプリ

```
┌─────────────────────────────────┐
│  🆔 Wallet アプリ              │
│                                 │
│  📜 運転免許証                  │
│  📜 卒業証明書                  │
│  📜 社員証                      │
│  📜 資格証明書                  │
│                                 │
└─────────────────────────────────┘
```

---

## 識別子関連

### DID（Decentralized Identifier）

**読み方:** ディー・アイ・ディー、分散型識別子

**意味:** 中央機関に依存しない識別子。自分で生成・管理できる。

**例:** `did:web:example.com`, `did:key:z6MkhaXgBZD...`

**従来の識別子との違い:**

| 識別子 | 管理者 | 例 |
|--------|--------|-----|
| メールアドレス | メールプロバイダ | user@gmail.com |
| 電話番号 | 通信キャリア | 090-xxxx-xxxx |
| DID | 本人 | did:example:123 |

---

### DID Document

**読み方:** ディー・アイ・ディー・ドキュメント

**意味:** DID に紐づくメタデータ。公開鍵やサービスエンドポイントを含む。

```json
{
  "id": "did:example:123",
  "authentication": [{
    "id": "did:example:123#key-1",
    "publicKeyJwk": { ... }
  }]
}
```

---

### DID Method

**読み方:** ディー・アイ・ディー・メソッド

**意味:** DID の作成・解決方法を定義する仕様。

**主なメソッド:**

| メソッド | 説明 |
|---------|------|
| `did:web` | Web ドメインを使用 |
| `did:key` | 公開鍵から導出 |
| `did:ion` | Bitcoin ベース |
| `did:ethr` | Ethereum ベース |

---

## 選択的開示関連

### Selective Disclosure（選択的開示）

**読み方:** セレクティブ・ディスクロージャー

**意味:** VC に含まれる情報のうち、必要なものだけを開示する機能。

**例:**

```
運転免許証の VC に含まれる情報:
  - 氏名
  - 住所
  - 生年月日
  - 免許種別
  - 21歳以上か

年齢確認時に開示する情報:
  - 21歳以上か ← これだけ！
```

---

### SD-JWT

**読み方:** エス・ディー・ジェイ・ダブリュー・ティー

**意味:** Selective Disclosure for JWT。選択的開示機能を持つ JWT。

**特徴:**
- JWT をベースにしている
- 各クレームを個別に開示/非開示にできる
- IETF で標準化中

---

### Disclosure

**読み方:** ディスクロージャー

**意味:** SD-JWT で、開示するクレームの元データ。

```
SD-JWT の構成:
  <JWT>~<Disclosure 1>~<Disclosure 2>~...

Disclosure の中身:
  ["ソルト", "クレーム名", "値"]
  例: ["abc123", "name", "山田太郎"]
```

---

## フォーマット関連

### JWT（JSON Web Token）

**読み方:** ジョット

**意味:** JSON 形式のデータを署名付きでエンコードしたトークン。

```
eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwcy...
└──────┬──────────┘.└──────────┬──────────┘
     Header                 Payload
                               + Signature
```

---

### JSON-LD

**読み方:** ジェイソン・エル・ディー

**意味:** JSON for Linked Data。JSON にセマンティック（意味論的）な情報を追加した形式。

**特徴:**
- `@context` でデータの意味を定義
- 相互運用性が高い

```json
{
  "@context": ["https://www.w3.org/ns/credentials/v2"],
  "type": ["VerifiableCredential"],
  ...
}
```

---

### mdoc / mDL

**読み方:** エム・ドック / エム・ディー・エル

**意味:**
- **mdoc:** Mobile Document。ISO で定義されたモバイル ID フォーマット
- **mDL:** mobile Driving License。mdoc の運転免許証版

**特徴:**
- CBOR（バイナリ）形式でコンパクト
- ISO 18013-5 で標準化
- NFC/BLE での近接通信に対応

---

### CBOR

**読み方:** シーボー

**意味:** Concise Binary Object Representation。JSON のバイナリ版のようなフォーマット。

**特徴:**
- コンパクト
- パース（解析）が高速
- バイナリデータをネイティブサポート

---

### COSE

**読み方:** コーズ

**意味:** CBOR Object Signing and Encryption。CBOR データの署名・暗号化仕様。

**関係:** JWT : JWS = mdoc : COSE

---

## 署名関連

### Digital Signature（デジタル署名）

**読み方:** デジタル・シグネチャー

**意味:** 秘密鍵を使ってデータに署名する技術。公開鍵で検証可能。

```
署名の作成:
  データ + 秘密鍵 → 署名

署名の検証:
  データ + 署名 + 公開鍵 → 有効/無効
```

---

### Public Key / Private Key（公開鍵/秘密鍵）

**読み方:** パブリック・キー / プライベート・キー

**意味:**
- **公開鍵:** 誰でも知ってよい鍵。署名の検証に使う
- **秘密鍵:** 本人だけが知る鍵。署名の作成に使う

---

### Proof

**読み方:** プルーフ

**意味:** VC/VP の署名部分。

```json
{
  "proof": {
    "type": "DataIntegrityProof",
    "created": "2024-01-01T00:00:00Z",
    "verificationMethod": "did:example:issuer#key-1",
    "proofValue": "z3FXQjecWufY46..."
  }
}
```

---

## プロトコル関連

### OID4VCI

**読み方:** オー・アイ・ディー・フォー・ブイ・シー・アイ

**正式名称:** OpenID for Verifiable Credential Issuance

**意味:** VC を発行するためのプロトコル。

```
Wallet ────────► Issuer
         │
         │ OID4VCI
         │
         ▼
      VC を取得
```

---

### OID4VP

**読み方:** オー・アイ・ディー・フォー・ブイ・ピー

**正式名称:** OpenID for Verifiable Presentations

**意味:** VC を提示・検証するためのプロトコル。

```
Wallet ────────► Verifier
         │
         │ OID4VP
         │
         ▼
      VC を検証
```

---

### SIOP v2

**読み方:** エス・アイ・オー・ピー・バージョン・ツー

**正式名称:** Self-Issued OpenID Provider v2

**意味:** 自己発行の ID トークンを使った認証プロトコル。

---

## 失効関連

### Revocation（失効）

**読み方:** レボケーション

**意味:** 発行済みの VC を無効にすること。

**例:** スマホを紛失した時に VC を失効させる

---

### Status List

**読み方:** ステータス・リスト

**意味:** VC の失効状態を管理するリスト。ビットマップ形式で効率的に管理。

```
Status List:
  [0, 0, 1, 0, 0, 1, 0, ...]
       ↑        ↑
    失効済み  失効済み
```

---

## 認証関連

### Key Binding

**読み方:** キー・バインディング

**意味:** VC/VP が特定の鍵（デバイス）に紐づいていることを証明する仕組み。

**目的:** 盗まれた VC を他のデバイスで使えないようにする

---

### Holder Binding

**読み方:** ホルダー・バインディング

**意味:** VC が特定の保持者に紐づいていることを証明する仕組み。

---

## 規格・標準

### W3C

**読み方:** ダブリュー・スリー・シー

**正式名称:** World Wide Web Consortium

**役割:** VC Data Model、DID Core などを標準化

---

### IETF

**読み方:** アイ・イー・ティー・エフ

**正式名称:** Internet Engineering Task Force

**役割:** SD-JWT、OAuth 関連仕様を標準化

---

### ISO

**読み方:** アイ・エス・オー

**正式名称:** International Organization for Standardization

**役割:** mdoc/mDL（ISO 18013-5）を標準化

---

### OpenID Foundation

**読み方:** オープン・アイディー・ファウンデーション

**役割:** OID4VCI、OID4VP、SIOP v2 を標準化

---

## 関連用語

### eIDAS 2.0

**読み方:** イー・アイダス・ツー・ポイント・オー

**意味:** EU のデジタル ID 規則。EU Digital Identity Wallet で VC を使用。

---

### SSI（Self-Sovereign Identity）

**読み方:** エス・エス・アイ、自己主権型アイデンティティ

**意味:** 個人が自分の ID を自ら管理する考え方。VC/DID はその実現手段。

---

### Decentralized Identity

**読み方:** ディセントラライズド・アイデンティティ、分散型アイデンティティ

**意味:** 中央機関に依存しない ID システム。SSI とほぼ同義。

---

### Wallet as a Service

**読み方:** ウォレット・アズ・ア・サービス

**意味:** ウォレット機能をクラウドサービスとして提供するモデル。

---

## 略語一覧

| 略語 | 正式名称 | 意味 |
|------|----------|------|
| VC | Verifiable Credential | 検証可能なクレデンシャル |
| VP | Verifiable Presentation | 検証可能なプレゼンテーション |
| DID | Decentralized Identifier | 分散型識別子 |
| SD-JWT | Selective Disclosure JWT | 選択的開示 JWT |
| mDL | mobile Driving License | モバイル運転免許証 |
| OID4VCI | OpenID for Verifiable Credential Issuance | VC 発行プロトコル |
| OID4VP | OpenID for Verifiable Presentations | VC 提示プロトコル |
| SIOP | Self-Issued OpenID Provider | 自己発行 OP |
| SSI | Self-Sovereign Identity | 自己主権型アイデンティティ |
| KYC | Know Your Customer | 本人確認 |
| CBOR | Concise Binary Object Representation | バイナリ形式 |
| COSE | CBOR Object Signing and Encryption | CBOR 署名/暗号化 |
