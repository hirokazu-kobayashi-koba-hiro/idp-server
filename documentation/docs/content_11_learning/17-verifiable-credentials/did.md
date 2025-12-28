---
sidebar_position: 11
---

# DID: Decentralized Identifiers（分散型識別子）

DID（Decentralized Identifier）は、中央集権的な登録機関に依存しない新しい識別子の仕様です。このドキュメントでは、DID の概念と仕組みを解説します。

---

## 第1部: 概要編

### DID とは何か？

DID は、**分散型で検証可能な識別子**です。従来の識別子（メールアドレス、電話番号など）とは異なり、発行者に依存せず、本人が管理できます。

```
従来の識別子:

  メールアドレス: user@gmail.com
    └── Google が管理・発行
    └── Google がサービスを停止すると使えなくなる
    └── Google が利用規約で禁止すると使えなくなる

  電話番号: 090-1234-5678
    └── 通信キャリアが管理・発行
    └── MNP で変更可能だが、キャリアへの依存は残る

DID:

  did:example:123456789abcdefghi
    └── 本人が生成・管理
    └── 特定の事業者に依存しない
    └── 暗号技術で本人を証明可能
```

### なぜ DID が必要なのか？

| 課題 | 従来の識別子 | DID |
|------|-------------|-----|
| 所有権 | 発行者が所有 | 本人が所有 |
| 可搬性 | サービス間で移行困難 | どこでも使用可能 |
| プライバシー | 追跡されやすい | 用途別に複数作成可能 |
| 検証 | 発行者に問い合わせ必要 | 暗号技術で自己証明 |
| 永続性 | 発行者次第 | 分散台帳等で永続化 |

### DID の構造

```
         did:example:123456789abcdefghi
         ─┬─ ───┬─── ────────┬────────
          │     │            │
          │     │            └── DID 固有識別子
          │     │                (Method-Specific Identifier)
          │     │
          │     └── DID メソッド
          │         (どのシステムで管理するか)
          │
          └── スキーム（常に "did"）
```

---

## 第2部: 詳細編

### DID Document

DID を解決すると、**DID Document** が返されます。これは DID に関連付けられたメタデータを含む JSON-LD ドキュメントです。

```json
{
  "@context": [
    "https://www.w3.org/ns/did/v1",
    "https://w3id.org/security/suites/ed25519-2020/v1"
  ],
  "id": "did:example:123456789abcdefghi",
  "authentication": [{
    "id": "did:example:123456789abcdefghi#keys-1",
    "type": "Ed25519VerificationKey2020",
    "controller": "did:example:123456789abcdefghi",
    "publicKeyMultibase": "zH3C2AVvLMv6gmMNam3uVAjZpfkcJCwDwnZn6z3wXmqPV"
  }],
  "assertionMethod": [{
    "id": "did:example:123456789abcdefghi#keys-2",
    "type": "Ed25519VerificationKey2020",
    "controller": "did:example:123456789abcdefghi",
    "publicKeyMultibase": "z4BWwfeqdp1obQptLLMvPNgBw48p7og1ie6Hf9p5nTpNN"
  }],
  "service": [{
    "id": "did:example:123456789abcdefghi#service-1",
    "type": "LinkedDomains",
    "serviceEndpoint": "https://example.com"
  }]
}
```

### DID Document の主要セクション

| セクション | 説明 |
|-----------|------|
| `id` | DID 自身 |
| `controller` | この DID Document を管理できる DID |
| `verificationMethod` | 公開鍵のリスト |
| `authentication` | 認証に使用できる鍵 |
| `assertionMethod` | 署名（主張）に使用できる鍵 |
| `keyAgreement` | 鍵交換に使用できる鍵 |
| `capabilityInvocation` | 権限実行に使用できる鍵 |
| `capabilityDelegation` | 権限委譲に使用できる鍵 |
| `service` | サービスエンドポイント |

### Verification Method（検証メソッド）

DID に関連付けられた公開鍵を定義します。

```json
{
  "verificationMethod": [{
    "id": "did:example:123#key-1",
    "type": "JsonWebKey2020",
    "controller": "did:example:123",
    "publicKeyJwk": {
      "kty": "EC",
      "crv": "P-256",
      "x": "WKn-ZIGevcwGFOMJ0GeEei2Rew...",
      "y": "y77t-RvAHRKTsSGdIYUfweuOvw..."
    }
  }]
}
```

### Verification Relationships（検証関係）

公開鍵の用途を定義します。

```
┌─────────────────────────────────────────────────────────────┐
│                     Verification Method                      │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  id: "did:example:123#key-1"                           │ │
│  │  type: "Ed25519VerificationKey2020"                    │ │
│  │  publicKeyMultibase: "z..."                            │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│authentication │    │assertionMethod│    │ keyAgreement  │
│               │    │               │    │               │
│ 認証用        │    │ 署名用        │    │ 暗号化用      │
│ (ログイン等)   │    │ (VC発行等)    │    │ (鍵交換)      │
└───────────────┘    └───────────────┘    └───────────────┘
```

### Service Endpoints

DID に関連するサービスを定義します。

```json
{
  "service": [{
    "id": "did:example:123#messaging",
    "type": "DIDCommMessaging",
    "serviceEndpoint": "https://example.com/didcomm"
  }, {
    "id": "did:example:123#vc-api",
    "type": "CredentialService",
    "serviceEndpoint": "https://example.com/vc"
  }]
}
```

---

## DID メソッド

DID メソッドは、特定のシステムやネットワークでの DID の作成・解決・更新・削除方法を定義します。

### 主要な DID メソッド

| メソッド | 説明 | 特徴 |
|---------|------|------|
| `did:web` | Web ドメインベース | 実装が簡単、ドメイン所有者への信頼が必要 |
| `did:key` | 公開鍵から導出 | 解決不要、更新不可、シンプル |
| `did:ion` | Bitcoin ベース | 分散性が高い、Microsoft が開発 |
| `did:ethr` | Ethereum ベース | スマートコントラクト利用 |
| `did:jwk` | JWK から導出 | did:key の JWK 版 |
| `did:pkh` | ブロックチェーンアドレス | 既存のウォレットを活用 |

### did:web

```
did:web:example.com
  → https://example.com/.well-known/did.json

did:web:example.com:user:alice
  → https://example.com/user/alice/did.json
```

**メリット**:
- 既存の Web インフラを活用
- DNS で信頼性を確保
- 実装が容易

**デメリット**:
- ドメイン所有者への信頼が必要
- ドメインが失われると DID も無効

### did:key

```
did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
       └────────────────────────────┬─────────────────────────┘
                                    │
                          公開鍵のマルチベースエンコード
```

**メリット**:
- 解決プロセス不要（DID 自体が公開鍵）
- 即座に生成可能
- 完全に分散型

**デメリット**:
- 鍵の更新・失効ができない
- 長期利用には不向き

### did:jwk

```
did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6Ii4uLiIsInkiOiIuLi4ifQ
       └──────────────────────────────┬────────────────────────────────┘
                                      │
                               JWK の Base64URL エンコード
```

did:key と同様のコンセプトですが、JWK 形式を使用します。

---

## DID 解決（DID Resolution）

### そもそも「解決」とは？

DID 解決とは、**DID から公開鍵などの情報を取得すること**です。

```
なぜ解決が必要か？

  あなたは VC（Verifiable Credential）を受け取りました:

    ┌─────────────────────────────────────┐
    │  Verifiable Credential              │
    │                                     │
    │  issuer: did:web:university.edu    │ ← 発行者の DID
    │  内容: 「山田太郎は卒業生です」     │
    │  署名: xxxxxxxxxxxxxxxxxxxxxxx      │ ← 本当に発行者の署名？
    └─────────────────────────────────────┘

  この VC が本物かを確認するには:
    1. issuer の DID から公開鍵を取得  ← これが「DID 解決」
    2. 取得した公開鍵で署名を検証
    3. 一致すれば本物！
```

つまり、**DID は「住所」、DID Document は「住所から届く情報」**のようなものです。

---

### 具体例: did:web の解決

**did:web** は、Web サーバーから DID Document を取得します。

```
Step 1: DID を見る

  did:web:university.edu
      ↓
  「university.edu というドメインに聞けばいい」

Step 2: DID Document を取得

  HTTP GET https://university.edu/.well-known/did.json
                ↓
  ┌─────────────────────────────────────────────┐
  │ {                                           │
  │   "id": "did:web:university.edu",           │
  │   "verificationMethod": [{                  │
  │     "id": "did:web:university.edu#key-1",   │
  │     "publicKeyJwk": {                       │
  │       "kty": "EC",                          │
  │       "crv": "P-256",                       │
  │       "x": "abc...",                        │  ← 公開鍵を取得！
  │       "y": "xyz..."                         │
  │     }                                       │
  │   }]                                        │
  │ }                                           │
  └─────────────────────────────────────────────┘

Step 3: 公開鍵で署名を検証

  VC の署名を、取得した公開鍵で検証
    → 一致すれば「university.edu が発行した本物の VC」と確認できる
```

---

### 具体例: did:key の解決

**did:key** は、**解決プロセスが不要**です。なぜなら、DID 自体に公開鍵が含まれているから。

```
did:key の構造:

  did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
              └───────────────────────┬────────────────────────┘
                                      │
              この部分が公開鍵を Base58 エンコードしたもの

解決方法:

  1. DID の z6Mk... 部分をデコード
  2. 公開鍵が直接得られる

  → HTTP 通信不要！
  → どこにも問い合わせない
  → 完全にオフラインで解決可能
```

**比較:**

| 項目 | did:web | did:key |
|------|---------|---------|
| 解決方法 | HTTP で取得 | デコードするだけ |
| ネットワーク | 必要 | 不要 |
| 鍵の更新 | 可能 | 不可能（DID 自体が鍵） |
| 用途 | 組織の永続的な識別子 | 一時的な鍵交換など |

---

### 解決の流れ（全体図）

```
VC の署名を検証したい場合:

  ┌─────────────┐
  │    VC       │
  │             │
  │ issuer:     │──────┐
  │  did:web:   │      │
  │  example.com│      │
  └─────────────┘      │
                       ▼
              ┌───────────────────┐
              │    DID Resolver    │
              │                   │
              │ 1. DID メソッドを確認 │
              │    → did:web      │
              │                   │
              │ 2. メソッド固有の解決 │
              │    → HTTPで取得    │
              └─────────┬─────────┘
                        │
                        ▼
              ┌───────────────────┐
              │  example.com      │
              │  /.well-known/    │
              │  did.json         │
              └─────────┬─────────┘
                        │
                        ▼
              ┌───────────────────┐
              │  DID Document     │
              │                   │
              │  公開鍵が含まれる  │
              └─────────┬─────────┘
                        │
                        ▼
              ┌───────────────────┐
              │  署名検証！        │
              │                   │
              │  公開鍵で VC の   │
              │  署名を検証       │
              └───────────────────┘
```

---

### DID Resolution Result（応答の形式）

解決の結果は、以下の3つを含みます：

```json
{
  "didDocument": {
    "id": "did:web:example.com",
    "verificationMethod": [{
      "id": "did:web:example.com#key-1",
      "publicKeyJwk": { ... }
    }]
  },
  "didResolutionMetadata": {
    "contentType": "application/did+ld+json"
  },
  "didDocumentMetadata": {
    "created": "2024-01-01T00:00:00Z",
    "updated": "2024-01-15T00:00:00Z"
  }
}
```

| 項目 | 何がわかる？ |
|------|------------|
| `didDocument` | 公開鍵やサービスエンドポイント |
| `didResolutionMetadata` | 解決が成功したか、エラーの場合は理由 |
| `didDocumentMetadata` | いつ作成されたか、最終更新日など |

---

### Universal Resolver（ユニバーサル・リゾルバー）

「様々な DID メソッドを解決できる共通サービス」です。

```
Universal Resolver:

  ┌─────────────────────────────────────────────┐
  │                                             │
  │  入力: どんな DID でもOK                     │
  │                                             │
  │    did:web:example.com                     │
  │    did:key:z6Mk...                         │
  │    did:ethr:0x...                          │
  │    did:ion:...                             │
  │                                             │
  └──────────────────┬──────────────────────────┘
                     │
                     ▼
  ┌─────────────────────────────────────────────┐
  │  内部で適切なドライバーを選択               │
  │                                             │
  │  did:web  → Web ドライバー                  │
  │  did:key  → デコード処理                    │
  │  did:ethr → Ethereum ドライバー             │
  │  did:ion  → Bitcoin ドライバー              │
  └──────────────────┬──────────────────────────┘
                     │
                     ▼
  ┌─────────────────────────────────────────────┐
  │  出力: DID Document                         │
  └─────────────────────────────────────────────┘
```

**公開デモサービス:**

```bash
# ブラウザやcurlで以下にアクセス
https://dev.uniresolver.io/1.0/identifiers/did:web:example.com

# 結果: DID Document が JSON で返される
```

| 項目 | 内容 |
|------|------|
| URL | https://dev.uniresolver.io/ |
| 運営 | DIF（Decentralized Identity Foundation） |
| 対応メソッド | 50以上（did:web, did:key, did:ion, did:ethr など） |
| ソースコード | https://github.com/decentralized-identity/universal-resolver |

---

### Universal Resolver の注意点（商用利用）

**dev.uniresolver.io は開発・テスト用です。商用利用には適していません。**

```
dev.uniresolver.io の実態:

  ┌─────────────────────────────────────────────┐
  │  ⚠️ 開発・テスト用のデモサービス            │
  │                                             │
  │  ✗ SLA なし（落ちても保証なし）              │
  │  ✗ 可用性の保証なし                         │
  │  ✗ レート制限が不明確                       │
  │  ✗ サポートなし                             │
  │                                             │
  │  → 本番環境での利用は非推奨                 │
  └─────────────────────────────────────────────┘
```

**商用システムでの現実的な選択肢:**

| 方法 | 説明 | メリット / デメリット |
|------|------|----------------------|
| **自前ホスティング** | GitHub からクローンして自社運用 | 制御可能だが運用コストがかかる |
| **DID メソッドを限定** | did:web や did:key のみ対応 | シンプル、Universal Resolver 不要 |
| **商用サービス利用** | クラウドベンダーの ID サービス | SLA あり、コストがかかる |

**実際の商用実装では:**

```
多くの場合、Universal Resolver を使わない:

  did:web のみ対応する場合:
    → 単なる HTTP GET で /.well-known/did.json を取得
    → 数行のコードで実装可能
    → 外部依存なし

  did:key のみ対応する場合:
    → ライブラリで公開鍵をデコードするだけ
    → ネットワーク通信すら不要

  結論:
    対応する DID メソッドを絞れば、
    汎用 Resolver は不要になることが多い
```

**Universal Resolver が有用なケース:**

- 開発・テスト時の動作確認
- 多数の DID メソッドに対応する必要がある場合
- PoC（概念実証）段階

---

### よくある質問

**Q: 毎回解決するの？遅くない？**

A: 実際の実装ではキャッシュを使います。同じ DID を何度も解決する必要はありません。

**Q: did:key は解決不要なら、なぜ did:web を使うの？**

A: did:key は鍵を更新できません。組織が使う長期的な識別子には、鍵の更新ができる did:web が適しています。

**Q: 解決に失敗したらどうなる？**

A: VC の署名検証ができないため、その VC は「検証できない」として扱われます。

---

## DID と VC の関係

```
┌─────────────────────────────────────────────────────────────┐
│                   Verifiable Credential                     │
│                                                             │
│  issuer: did:example:university                            │
│          └──────────┬──────────┘                           │
│                     │                                       │
│                     ▼                                       │
│          ┌─────────────────────┐                           │
│          │    DID Document     │                           │
│          │                     │                           │
│          │  assertionMethod:   │                           │
│          │    #key-1 ──────────┼───► 署名検証に使用        │
│          └─────────────────────┘                           │
│                                                             │
│  credentialSubject:                                        │
│    id: did:example:holder123                               │
│        └──────────┬──────────┘                             │
│                   │                                         │
│                   ▼                                         │
│        ┌─────────────────────┐                             │
│        │    DID Document     │                             │
│        │                     │                             │
│        │  authentication:    │                             │
│        │    #key-1 ──────────┼───► VP の署名検証に使用     │
│        └─────────────────────┘                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 鍵のローテーション | 定期的に鍵を更新（did:key 以外） |
| 鍵の保護 | 秘密鍵は HSM やセキュアエンクレーブで保護 |
| メソッドの選択 | ユースケースに適した DID メソッドを選択 |
| 信頼モデル | DID メソッドの信頼モデルを理解 |
| 失効 | DID Document の更新・削除方法を確保 |

### DID メソッド別の信頼モデル

| メソッド | 信頼の根拠 |
|---------|-----------|
| `did:web` | DNS とドメイン所有者 |
| `did:key` | 暗号技術のみ（信頼不要） |
| `did:ion` | Bitcoin ネットワーク |
| `did:ethr` | Ethereum ネットワーク |

---

## 参考リンク

- [W3C DID Core](https://www.w3.org/TR/did-core/)
- [W3C DID Resolution](https://w3c-ccg.github.io/did-resolution/)
- [DID Method Registry](https://www.w3.org/TR/did-spec-registries/)
- [Universal Resolver](https://dev.uniresolver.io/)
