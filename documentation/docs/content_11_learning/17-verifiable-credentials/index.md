---
sidebar_position: 0
---

# Verifiable Credentials 学習ガイド

このディレクトリには、Verifiable Credentials（検証可能なクレデンシャル）に関する学習ドキュメントが含まれています。

---

## 概要

Verifiable Credentials（VC）は、物理的な証明書（運転免許証、卒業証明書、資格証明書など）をデジタル化し、暗号技術によって検証可能にした仕組みです。

```
物理的な証明書の課題:

  ┌─────────────────────────────────────────────────────────┐
  │  運転免許証（プラスチックカード）                        │
  │                                                         │
  │  ✗ コピー・偽造が可能                                   │
  │  ✗ オンラインで使えない                                 │
  │  ✗ 必要以上の情報を見せる必要がある（住所、生年月日等）  │
  │  ✗ 発行元に問い合わせないと有効性を確認できない          │
  └─────────────────────────────────────────────────────────┘

Verifiable Credentials:

  ┌─────────────────────────────────────────────────────────┐
  │  デジタル運転免許証（Verifiable Credential）            │
  │                                                         │
  │  ✓ 暗号署名により改ざん検知可能                         │
  │  ✓ オンラインで提示可能                                 │
  │  ✓ 選択的開示（年齢だけ、名前だけ等）                   │
  │  ✓ 発行元に問い合わせずに検証可能                       │
  └─────────────────────────────────────────────────────────┘
```

---

## 目次

### はじめに読む

| ドキュメント | 内容 | おすすめの人 |
|-------------|------|-------------|
| [5分でわかるVC](./vc-introduction.md) | VC の基本概念を5分で理解 | 全員（まずここから） |
| [ストーリーで学ぶVC](./vc-story.md) | 具体的なシナリオで理解を深める | 具体例で理解したい人 |
| [用語集](./glossary.md) | 専門用語の解説 | 用語がわからない時に参照 |

### 基礎

| ドキュメント | 内容 |
|-------------|------|
| [vc-data-model](./vc-data-model.md) | W3C Verifiable Credentials Data Model |
| [did](./did.md) | Decentralized Identifiers（DID） |

### フォーマット

| ドキュメント | 内容 |
|-------------|------|
| [vc-formats](./vc-formats.md) | VC フォーマット比較（JWT, JSON-LD, SD-JWT, mdoc） |
| [sd-jwt](./sd-jwt.md) | SD-JWT（Selective Disclosure JWT） |
| [mdoc](./mdoc.md) | mdoc / mDL（ISO 18013-5） |

### トピック

| ドキュメント | 内容 |
|-------------|------|
| [vc-and-blockchain](./vc-and-blockchain.md) | VC とブロックチェーンの関係 |
| [vc-challenges](./vc-challenges.md) | VC の課題と批判 |
| [vc-timeline](./vc-timeline.md) | VC の歴史と今後の展望 |

---

## 主要な概念

### 三者モデル

```
Verifiable Credentials の基本的な三者モデル:

  ┌──────────────┐
  │    Issuer    │  発行者
  │   (発行者)    │  - 大学、政府機関、企業など
  │              │  - Credential を発行・署名
  └──────┬───────┘
         │
         │ 発行
         ▼
  ┌──────────────┐
  │    Holder    │  保持者
  │   (保持者)    │  - 個人（ユーザー）
  │              │  - Wallet で Credential を管理
  └──────┬───────┘
         │
         │ 提示
         ▼
  ┌──────────────┐
  │   Verifier   │  検証者
  │   (検証者)    │  - サービス提供者
  │              │  - Credential を検証
  └──────────────┘
```

### 用語

| 用語 | 説明 |
|------|------|
| Verifiable Credential (VC) | 発行者が署名したクレデンシャル |
| Verifiable Presentation (VP) | 検証者に提示するためのコンテナ |
| Holder | クレデンシャルを保持する主体（通常は個人） |
| Issuer | クレデンシャルを発行する主体 |
| Verifier | クレデンシャルを検証する主体 |
| Wallet | Holder が VC を管理するアプリケーション |
| DID | 分散型識別子（Decentralized Identifier） |

---

## 主要な仕様

### W3C 仕様

| 仕様 | 説明 |
|------|------|
| [VC Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/) | VC のデータモデル |
| [DID Core](https://www.w3.org/TR/did-core/) | 分散型識別子 |
| [DID Resolution](https://w3c-ccg.github.io/did-resolution/) | DID の解決方法 |

### IETF 仕様

| 仕様 | 説明 |
|------|------|
| [SD-JWT](https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/) | 選択的開示 JWT |
| [SD-JWT VC](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/) | SD-JWT ベースの VC |

### ISO 仕様

| 仕様 | 説明 |
|------|------|
| ISO/IEC 18013-5 | モバイル運転免許証（mDL） |
| ISO/IEC 23220 | mdoc 一般仕様 |

### OpenID 仕様

| 仕様 | 説明 |
|------|------|
| [OID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) | VC の発行プロトコル |
| [OID4VP](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html) | VC の提示プロトコル |
| [SIOP v2](https://openid.net/specs/openid-connect-self-issued-v2-1_0.html) | Self-Issued OP |

---

## 学習パス

### 初めての方

```
1. 5分でわかるVC
   └── VC とは何かを理解

2. ストーリーで学ぶVC
   └── 具体的なユースケースで理解を深める

3. 用語集
   └── わからない用語が出てきたら参照
```

### 入門者向け

```
1. vc-data-model
   └── VC の基本構造を理解

2. did
   └── DID の概念を理解

3. vc-formats
   └── 各フォーマットの違いを把握
```

### 実装者向け

```
1. sd-jwt
   └── 選択的開示の仕組みを深掘り

2. mdoc
   └── ISO 標準の mDL フォーマットを理解

3. OID4VCI/OID4VP
   └── OAuth/OIDC RFC 学習ガイド参照
```

---

## ユースケース

| ユースケース | 発行者 | VC の種類 |
|-------------|--------|----------|
| 運転免許証 | 行政機関 | mDL（モバイル運転免許証） |
| 学歴証明 | 大学 | 学位証明書 |
| 資格証明 | 資格認定機関 | 資格証明書 |
| 従業員証明 | 企業 | 在籍証明書 |
| 年齢確認 | 行政機関 | 年齢証明（21歳以上等） |
| 医療資格 | 医療機関 | 医師免許証 |
| 金融 KYC | 銀行 | 本人確認済み証明 |

---

## 参考リンク

- [W3C Verifiable Credentials](https://www.w3.org/TR/vc-data-model-2.0/)
- [W3C DID Core](https://www.w3.org/TR/did-core/)
- [OpenID for Verifiable Credentials](https://openid.net/sg/openid4vc/)
- [IETF SD-JWT](https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/)
- [ISO 18013-5 mDL](https://www.iso.org/standard/69084.html)
