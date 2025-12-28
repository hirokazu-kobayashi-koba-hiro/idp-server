---
sidebar_position: 22
---

# mdoc / mDL: ISO 18013-5 モバイル運転免許証

mdoc（Mobile Document）は ISO 18013-5 で定義されたモバイル運転免許証（mDL: mobile Driving License）のフォーマットです。このドキュメントでは、mdoc の仕組みと構造を解説します。

---

## 第1部: 概要編

### mdoc とは何か？

mdoc は、**モバイルデバイスで管理・提示できるデジタル身分証明書**のフォーマットです。主に運転免許証のデジタル化（mDL）に使用されますが、他の ID 文書にも適用可能です。

```
従来の運転免許証:

  ┌─────────────────────────────────────┐
  │  プラスチックカード                   │
  │                                     │
  │  [写真]  氏名: 山田太郎              │
  │          生年月日: 1990/1/1          │
  │          住所: 東京都...              │
  │          有効期限: 2029/1/1          │
  │                                     │
  │  問題:                               │
  │  - コピー・偽造リスク                 │
  │  - オンラインで使えない               │
  │  - 全情報を見せる必要がある           │
  └─────────────────────────────────────┘

mdoc (mDL):

  ┌─────────────────────────────────────┐
  │  スマートフォンアプリ                 │
  │                                     │
  │  [デジタル署名付きデータ]             │
  │                                     │
  │  メリット:                           │
  │  ✓ 暗号署名で改ざん防止              │
  │  ✓ オンライン・オフライン対応         │
  │  ✓ 選択的開示（年齢だけ等）           │
  │  ✓ NFC/BLE で近接提示               │
  └─────────────────────────────────────┘
```

### ISO 18013 シリーズ

| 規格 | 内容 |
|------|------|
| ISO 18013-1 | 運転免許証の物理的特性 |
| ISO 18013-2 | 機械可読部分の仕様 |
| ISO 18013-3 | アクセス制御とセキュリティ |
| **ISO 18013-5** | モバイル運転免許証（mDL） |
| ISO 18013-7 | オンライン検証 |

---

## 第2部: 詳細編

### mdoc の基本構造

mdoc は CBOR（Concise Binary Object Representation）でエンコードされます。

```
mdoc 構造:

┌─────────────────────────────────────────────────────────────┐
│                           mdoc                              │
├─────────────────────────────────────────────────────────────┤
│ docType: "org.iso.18013.5.1.mDL"                           │
├─────────────────────────────────────────────────────────────┤
│ issuerSigned:                                               │
│   ├── nameSpaces:                                          │
│   │     "org.iso.18013.5.1": [IssuerSignedItem, ...]       │
│   │     "org.iso.18013.5.1.aamva": [...]                   │
│   │                                                         │
│   └── issuerAuth: COSE_Sign1 (発行者の署名)                  │
├─────────────────────────────────────────────────────────────┤
│ deviceSigned: (オプション)                                   │
│   ├── nameSpaces: { ... }                                   │
│   └── deviceAuth: COSE_Sign1 または COSE_Mac0               │
└─────────────────────────────────────────────────────────────┘
```

### CBOR とは

CBOR は JSON のバイナリ版のようなフォーマットです。

```
JSON:
{
  "name": "John",
  "age": 30
}

CBOR (16進数表記):
A2                    # map(2)
   64                 # text(4)
      6E616D65        # "name"
   64                 # text(4)
      4A6F686E        # "John"
   63                 # text(3)
      616765          # "age"
   18 1E              # unsigned(30)
```

| 特徴 | JSON | CBOR |
|------|------|------|
| フォーマット | テキスト | バイナリ |
| サイズ | 大きい | コンパクト |
| パース速度 | 遅い | 速い |
| バイナリデータ | Base64 エンコード必要 | ネイティブサポート |

### 名前空間とデータ要素

mdoc はデータを名前空間で整理します。

#### 標準名前空間: org.iso.18013.5.1

| データ要素 | 説明 |
|-----------|------|
| `family_name` | 姓 |
| `given_name` | 名 |
| `birth_date` | 生年月日 |
| `issue_date` | 発行日 |
| `expiry_date` | 有効期限 |
| `issuing_country` | 発行国 |
| `issuing_authority` | 発行機関 |
| `document_number` | 文書番号 |
| `portrait` | 顔写真（バイナリ） |
| `driving_privileges` | 運転区分 |
| `un_distinguishing_sign` | 国際識別記号 |
| `age_over_18` | 18歳以上か |
| `age_over_21` | 21歳以上か |
| `nationality` | 国籍 |
| `resident_address` | 住所 |
| `sex` | 性別 |

#### 拡張名前空間

各国・地域で追加の名前空間を定義可能。

```
米国 (AAMVA):
  org.iso.18013.5.1.aamva
    - DHS_compliance
    - veteran_indicator
    - organ_donor
    - ...

日本:
  org.iso.18013.5.1.jp
    - (定義される場合)
```

### IssuerSignedItem

各データ要素は以下の構造で署名されます。

```
IssuerSignedItem:
┌─────────────────────────────────────┐
│ digestID: 0                         │  識別子
│ random: h'8E4D...2A1F'              │  ソルト（16バイト以上）
│ elementIdentifier: "family_name"    │  要素名
│ elementValue: "山田"                 │  値
└─────────────────────────────────────┘
```

#### 選択的開示の仕組み

```
発行時:
  発行者は各 IssuerSignedItem のダイジェスト（ハッシュ）を計算し、
  MSO（Mobile Security Object）に含めて署名

  MSO.valueDigests:
    "org.iso.18013.5.1": {
      0: h'A1B2C3...',  // family_name のダイジェスト
      1: h'D4E5F6...',  // given_name のダイジェスト
      2: h'789ABC...',  // birth_date のダイジェスト
      ...
    }

提示時:
  保持者は開示したい IssuerSignedItem のみを送信
  検証者はダイジェストを再計算して MSO と照合

  例: 年齢確認のみ
    送信: IssuerSignedItem(age_over_21: true)
    検証: ダイジェストが MSO.valueDigests[X] と一致 → 有効
```

### Mobile Security Object (MSO)

MSO は mdoc の整合性を保証する署名付きオブジェクトです。

```
MSO 構造:
{
  "version": "1.0",
  "digestAlgorithm": "SHA-256",
  "valueDigests": {
    "org.iso.18013.5.1": {
      0: h'...',   // digestID 0 のダイジェスト
      1: h'...',   // digestID 1 のダイジェスト
      ...
    }
  },
  "deviceKeyInfo": {
    "deviceKey": {
      "kty": "EC2",
      "crv": "P-256",
      "x": h'...',
      "y": h'...'
    }
  },
  "docType": "org.iso.18013.5.1.mDL",
  "validityInfo": {
    "signed": "2024-01-01T00:00:00Z",
    "validFrom": "2024-01-01T00:00:00Z",
    "validUntil": "2029-01-01T00:00:00Z"
  }
}
```

---

## 提示プロトコル

### 近接提示（NFC/BLE）

```
mdoc holder (Wallet)              mdoc verifier (Reader)
        │                                │
        │    Engagement（接続開始）        │
        │ ◄─────────────────────────────│
        │    DeviceEngagement            │
        │ ─────────────────────────────►│
        │                                │
        │    Session Establishment       │
        │ ◄────────────────────────────►│
        │                                │
        │    Device Request              │
        │ ◄─────────────────────────────│
        │    (要求するデータ要素)          │
        │                                │
        │    Device Response             │
        │ ─────────────────────────────►│
        │    (選択されたデータ要素)        │
        │                                │
```

### Device Request

検証者がどのデータを要求するかを指定。

```
DeviceRequest:
{
  "version": "1.0",
  "docRequests": [{
    "itemsRequest": {
      "docType": "org.iso.18013.5.1.mDL",
      "nameSpaces": {
        "org.iso.18013.5.1": {
          "family_name": true,
          "given_name": true,
          "age_over_21": true,
          "portrait": false
        }
      }
    },
    "readerAuth": (オプション: 検証者の署名)
  }]
}
```

### Device Response

保持者が承諾したデータのみを返す。

```
DeviceResponse:
{
  "version": "1.0",
  "documents": [{
    "docType": "org.iso.18013.5.1.mDL",
    "issuerSigned": {
      "nameSpaces": {
        "org.iso.18013.5.1": [
          IssuerSignedItem(family_name),
          IssuerSignedItem(given_name),
          IssuerSignedItem(age_over_21)
        ]
      },
      "issuerAuth": COSE_Sign1(MSO)
    },
    "deviceSigned": {
      "nameSpaces": {},
      "deviceAuth": COSE_Sign1 or COSE_Mac0
    }
  }],
  "status": 0
}
```

### オンライン提示（ISO 18013-7）

```
                                  OID4VP を使用
        │                                │
Wallet  │ ◄────── Authorization Request ─│ Verifier
        │         (Presentation Definition)
        │                                │
        │ ────── Authorization Response ►│
        │         (vp_token: mdoc)        │
        │                                │
```

---

## 検証プロセス

### 検証ステップ

```
1. issuerAuth の検証
   ├── COSE 署名を検証
   ├── 発行者証明書チェーンを検証
   └── 証明書が信頼できる CA にチェーンするか

2. MSO の検証
   ├── docType が一致するか
   ├── validityInfo が有効期間内か
   └── deviceKey が含まれているか

3. IssuerSignedItem の検証
   ├── 各要素のダイジェストを計算
   └── MSO.valueDigests と一致するか

4. deviceAuth の検証（提示時）
   ├── deviceKey で署名を検証
   └── セッションへのバインディング確認

5. データ要素の使用
   └── 検証済みの要素のみを信頼
```

### 失効確認

mdoc は失効確認のためのメカニズムを持ちます。

| 方式 | 説明 |
|------|------|
| Certificate Revocation List (CRL) | 従来の PKI 方式 |
| OCSP | オンライン証明書状態確認 |
| Status List | VC と同様のビットリスト方式 |

---

## セキュリティ機能

### デバイス認証

mdoc は保持者のデバイスにバインドされます。

```
deviceKey:
  発行時に mdoc に埋め込まれる公開鍵
  対応する秘密鍵はデバイスの Secure Element に保管

提示時:
  deviceAuth でセッションに署名
  → この mdoc がこのデバイスのものであることを証明
```

### Reader Authentication

検証者（Reader）の認証も可能。

```
readerAuth:
  検証者が自身の証明書で要求に署名
  → ユーザーは誰がデータを要求しているか確認可能
  → 信頼できる検証者のみにデータを開示
```

---

## mdoc と SD-JWT の比較

| 観点 | mdoc | SD-JWT |
|------|------|--------|
| 標準化 | ISO（国際標準） | IETF（インターネット標準） |
| エンコード | CBOR（バイナリ） | Base64URL（テキスト） |
| 署名 | COSE | JWS |
| 主な用途 | 政府 ID、運転免許証 | 汎用 VC |
| 近接通信 | NFC/BLE 対応 | QR コード等 |
| オフライン | ✅ 完全対応 | ✅ 対応 |
| 選択的開示 | ダイジェスト方式 | Disclosure 方式 |
| エコシステム | 政府・自動車業界 | Web・IT 業界 |

---

## 実装状況

### プラットフォーム対応

| プラットフォーム | 対応状況 |
|----------------|----------|
| iOS | Identity Credential API (iOS 16+) |
| Android | IdentityCredential API (Android 11+) |

### 採用事例

| 国・地域 | 状況 |
|---------|------|
| 米国（複数州） | 運用中または試験中 |
| EU | eIDAS 2.0 で採用予定 |
| 韓国 | 運用中 |
| オーストラリア | 試験中 |

---

## 参考リンク

- [ISO 18013-5](https://www.iso.org/standard/69084.html)
- [ISO 18013-7 (Online Presentation)](https://www.iso.org/standard/82772.html)
- [ISO 23220 (Generic mdoc)](https://www.iso.org/standard/74910.html)
- [OpenID for Verifiable Presentations](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [Google Identity Credential](https://developer.android.com/identity/identity-credential)
- [Apple Identity Credential](https://developer.apple.com/documentation/passkit/wallet)
