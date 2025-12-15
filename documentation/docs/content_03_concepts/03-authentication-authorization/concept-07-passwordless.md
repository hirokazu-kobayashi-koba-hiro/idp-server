# パスワードレス認証

---

## 前提知識

このドキュメントを理解するには、以下の基礎知識が役立ちます：

- [認証ポリシー](concept-01-authentication-policy.md) - 認証方式の設定
- [多要素認証（MFA）](concept-02-mfa.md) - 認証要素の概念
- [FIDO2/WebAuthnの基礎](../basic/basic-16-fido2-webauthn-passwordless.md) - 技術的な背景

---

## 概要

idp-serverは、**パスワードレス認証**をサポートします。

**パスワードレス認証**とは、パスワードを使用せずにユーザーを認証する方式です。生体認証やセキュリティキーなど、より安全で使いやすい認証手段を提供します。

```
ユーザー ──[生体認証/セキュリティキー]──> 認証器 ──[署名]──> idp-server ──[検証]──> 認証成功
```

idp-serverでは以下のパスワードレス認証方式に対応しています：

* **FIDO2/WebAuthn**: 生体認証、セキュリティキー
* **Passkey**: デバイス間で同期可能な認証資格情報
* **FIDO UAF**: モバイルアプリ向け生体認証（CIBA連携）

---

## なぜパスワードレス認証が必要か

### パスワードの課題

パスワード認証には多くの課題があります：

| 課題 | 内容 | 影響 |
|:---|:---|:---|
| **フィッシング** | 偽サイトでパスワードを盗まれる | アカウント乗っ取り |
| **使い回し** | 同じパスワードを複数サイトで使用 | 漏洩時の被害拡大 |
| **覚えられない** | 複雑なパスワードは記憶困難 | ユーザー体験の低下 |
| **管理コスト** | リセット対応、ポリシー管理 | 運用負荷 |

### パスワードレス認証のメリット

| メリット | 説明 |
|:---|:---|
| **フィッシング耐性** | 認証器がオリジン（ドメイン）を検証するため、偽サイトでは認証不可 |
| **利便性** | 指紋や顔認証でワンタッチ認証 |
| **セキュリティ** | 秘密鍵は認証器から出ない |
| **運用コスト削減** | パスワードリセット対応が不要 |

---

## idp-serverにおけるパスワードレス認証

### 1. FIDO2/WebAuthn

**WebAuthn**（Web Authentication API）は、W3Cが標準化したパスワードレス認証の仕様です。

```
ユーザー          ブラウザ          idp-server        認証器
   |                 |                 |                |
   |--ログインボタン->|                 |                |
   |                 |--認証開始------->|                |
   |                 |<--challenge-----|                |
   |                 |--認証要求------------------------>|
   |<--生体認証-------------------------------------|
   |--指紋/顔---------------------------------------->|
   |                 |<--署名---------------------------|
   |                 |--署名検証------>|                |
   |                 |<--認証成功------|                |
```

**対応認証器**:
- **プラットフォーム認証器**: Touch ID, Face ID, Windows Hello
- **ローミング認証器**: YubiKey, セキュリティキー

**設定方法**: [FIDO2設定ガイド](../../content_06_developer-guide/05-configuration/authn/fido2.md)

### 2. Passkey

**Passkey**は、FIDO2の拡張で、デバイス間で認証資格情報を同期できる機能です。

```
┌─────────────────────────────┐  ┌─────────────────────────────┐
│          Apple              │  │          Google             │
│                             │  │                             │
│  iPhone ──┐                 │  │  Android ──┐                │
│           ├──> iCloud       │  │            ├──> Google      │
│  Mac ─────┘    Keychain     │  │  Chrome ───┘    Password    │
│                             │  │                 Manager     │
└─────────────────────────────┘  └─────────────────────────────┘
```

**メリット**:
- デバイス紛失時も他のデバイスで認証可能
- 新しいデバイスへの移行が容易
- ユーザー体験の向上

**詳細**: [Passkeyの基礎](../basic/basic-17-fido2-passkey-discoverable-credential.md)

### 3. FIDO UAF（CIBA連携）

**FIDO UAF**（Universal Authentication Framework）は、モバイルアプリ向けの生体認証仕様です。idp-serverでは、**CIBA**（Client Initiated Backchannel Authentication）と組み合わせて使用できます。

```
クライアント      idp-server      モバイルアプリ      ユーザー
     |                |                 |                |
     |--CIBA認証----->|                 |                |
     |  リクエスト    |--プッシュ通知-->|                |
     |                |                 |--生体認証要求->|
     |                |                 |<--指紋/顔------|
     |                |<--認証完了------|                |
     |<--トークン発行-|                 |                |
```

**ユースケース**:
- コールセンターでの本人確認
- 決済承認
- 高セキュリティ操作の承認

**詳細**: [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md)

---

## 認証ポリシーとの連携

パスワードレス認証は、認証ポリシーと組み合わせて使用します。

### 段階的な導入

**推奨される導入ステップ**:
1. **Phase 1**: パスワード + FIDO2（オプション）
2. **Phase 2**: FIDO2推奨、パスワードはフォールバック
3. **Phase 3**: FIDO2のみ（パスワードレス完全移行）

### MFAとの組み合わせ

FIDO2は単独でMFAの要件を満たすことができます：

| 認証要素 | FIDO2での実現 |
|:---|:---|
| **所持** | 認証器（スマートフォン、セキュリティキー） |
| **生体** | 指紋、顔認証 |
| **知識** | PIN（オプション） |

---

## セキュリティ考慮事項

### フィッシング耐性

FIDO2/WebAuthnは設計上フィッシング耐性があります：

- **オリジン検証**: 認証器が登録時のドメインと照合
- **署名バインディング**: challengeとオリジンを含めて署名

### 認証器の紛失対応

- **複数認証器の登録**: バックアップ用の認証器を推奨
- **リカバリーフロー**: 管理者による認証器リセット機能

### 認証器の検証（Attestation）

**Attestationレベル**:
- **none**: 認証器の種類を検証しない（推奨）
- **indirect**: 匿名化された検証
- **direct**: 認証器の種類を厳密に検証

---

## 関連ドキュメント

### 基礎知識
- [FIDO2/WebAuthnの基礎](../basic/basic-16-fido2-webauthn-passwordless.md)
- [Passkeyの基礎](../basic/basic-17-fido2-passkey-discoverable-credential.md)
- [FIDO2アーキテクチャ](../basic/basic-18-fido2-architecture-rp-browser-authenticator.md)

### 設定
- [FIDO2設定](../../content_06_developer-guide/05-configuration/authn/fido2.md)
- [認証ポリシー設定](../../content_06_developer-guide/05-configuration/authentication-policy.md)

### プロトコル
- [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md) - FIDO UAF連携

---

## 参考仕様

- [Web Authentication (WebAuthn)](https://www.w3.org/TR/webauthn-2/) - W3C標準仕様
- [FIDO2 Specifications](https://fidoalliance.org/specifications/) - FIDO Alliance
- [Passkeys](https://passkeys.dev/) - Passkey開発者向けリソース
