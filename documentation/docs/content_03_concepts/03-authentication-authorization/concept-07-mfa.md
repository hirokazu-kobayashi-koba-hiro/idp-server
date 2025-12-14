# 多要素認証（MFA）

idp-serverにおける多要素認証（MFA）の管理と制御について説明します。

## idp-serverでサポートするMFA手段

| MFA手段 | 種類 | 特徴 |
|:---|:---|:---|
| **パスワード** | 知識要素 | 基本的な認証方式 |
| **Email OTP** | 所持要素 | メールで送信されるワンタイムコード |
| **SMS OTP** | 所持要素 | SMSで送信されるワンタイムコード |
| **TOTP** | 所持要素 | 時間ベースのワンタイムパスワード |
| **FIDO2/WebAuthn** | 生体要素 | 指紋・顔認証、セキュリティキー |
| **FIDO-UAF** | 生体要素 | モバイル生体認証 |

## MFA登録のタイミング

idp-serverでは、以下のタイミングでMFA手段を登録できます。

### 1. 初回ログイン時

新規ユーザーが初めてログインする際にMFA手段の登録を促します。

### 2. ログイン後の設定画面

ユーザーがログイン後、任意のタイミングでMFA手段を追加・削除できます。

### 3. 認証フロー中

認証ポリシーによりMFAが要求された際、まだ登録していない場合は認証フロー中に登録を促します。

## 認証ポリシーとの連携

MFAは認証ポリシーと連携して動作します。

### 適用条件によるMFA要求

認証ポリシーの適用条件に応じて、MFAを動的に要求します：

- **スコープベース**: 機密スコープ（`verified_claims`）にはMFA必須
- **ACRベース**: 特定のACR値（`urn:mace:incommon:iap:gold`）にはMFA必須
- **クライアントベース**: モバイルアプリには生体認証を優先

### ステップアップ認証

既にログイン済みのユーザーに対して、操作の重要度に応じて追加のMFA認証を要求します。

**シナリオ例**:
1. ユーザーがパスワードでログイン（通常操作可能）
2. 送金操作を試みる
3. システムが追加でFIDO2認証を要求
4. FIDO2認証成功後、送金可能になる

## MFAの評価

認証ポリシーは、MFAの成功条件を定義します。

### OR条件

いずれかの認証方式が成功すれば認証完了。

- パスワード認証 **または** FIDO2認証

### AND条件（多要素認証）

複数の認証方式すべてが成功して初めて認証完了。

- パスワード認証 **かつ** SMS認証

## セキュリティ考慮事項

### MFA手段のバックアップ

主要なMFA手段が使えなくなった場合に備えて、バックアップ手段の登録を推奨します。

### フィッシング耐性

FIDO2/WebAuthnは、フィッシング攻撃に対して耐性があります。SMSやEmailのOTPは、フィッシングのリスクがあるため、重要な操作にはFIDO2を推奨します。

## 関連ドキュメント

- [認証ポリシー](concept-06-authentication-policy.md) - MFA要求の制御
- [トークン管理](../04-tokens-claims/concept-13-token-management.md) - MFA後のトークン発行
- [FIDO/WebAuthnとパスワードレス認証](../basic/basic-16-fido2-webauthn-passwordless.md) - FIDO2/WebAuthnの基礎
- [FIDO2 Passkeyと検出可能なクレデンシャル](../basic/basic-17-fido2-passkey-discoverable-credential.md) - Passkeyの仕組み

## 参考仕様

- [NIST SP 800-63B - Digital Identity Guidelines: Authentication](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [FIDO2 WebAuthn Specification](https://www.w3.org/TR/webauthn/)
- [RFC 6238 - TOTP: Time-Based One-Time Password Algorithm](https://datatracker.ietf.org/doc/html/rfc6238)
