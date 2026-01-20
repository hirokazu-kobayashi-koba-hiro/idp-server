---
sidebar_position: 99
---

# FIDO2/WebAuthn FAQ・トラブルシューティング

---

## 概要

FIDO2/WebAuthn実装時によく遭遇する問題とその解決方法をFAQ形式でまとめています。

---

## FAQ

### Q1: Touch IDが認証時に表示されない（QRコードのみ表示される）

**症状**:
- Passkey登録は成功した
- 認証時にTouch IDオプションが表示されない
- 「Use your phone or tablet」とQRコードのみ表示される

**原因と解決方法**:

#### 原因1: rpIdの不一致

**問題**: 登録時と認証時で`rpId`が異なる

```
登録時: rpId = "local.dev"
認証時: rpId = "auth.local.dev" (オリジンからのデフォルト値)
```

**解決方法**: 認証チャレンジのレスポンスに`rpId`を明示的に含める

```json
// サーバーレスポンス
{
  "challenge": "...",
  "rpId": "local.dev",  // または rp.id でネスト
  "allowCredentials": [...]
}
```

```javascript
// フロントエンド
const publicKeyOptions = {
  challenge: base64UrlToBuffer(challenge),
  rpId: response.rpId || response.rp?.id,  // 両方の形式に対応
  allowCredentials: [...]
};
```

**ポイント**:
- `rpId`を省略すると、ブラウザは現在のオリジンのドメインを使用
- サブドメインデプロイ（`auth.local.dev`）では親ドメイン（`local.dev`）を明示的に指定する必要がある
- 登録時と認証時で同じ`rpId`を使用すること

---

#### 原因2: transportsが含まれていない

**問題**: `allowCredentials`に`transports`が含まれていない

```json
// NG: transportsなし
{
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "credential_id_base64url"
    }
  ]
}

// OK: transportsあり
{
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "credential_id_base64url",
      "transports": ["internal", "hybrid"]
    }
  ]
}
```

**解決方法**:
1. 登録時に`transports`をデータベースに保存
2. 認証時に`allowCredentials`に`transports`を含める

**transportsの値と意味**:

| 値 | 説明 | 例 |
|----|------|-----|
| `internal` | プラットフォーム認証器 | Touch ID, Face ID, Windows Hello |
| `hybrid` | クロスデバイス（QRコード経由） | スマホのPasskey |
| `usb` | USB接続 | YubiKey等のセキュリティキー |
| `nfc` | NFC接続 | NFCセキュリティキー |
| `ble` | Bluetooth接続 | Bluetoothセキュリティキー |

**なぜ`transports`が重要か**:
- ブラウザは`transports`を参考に適切な認証器UIを表示
- `internal`が含まれていればTouch ID/Face IDを優先表示
- 省略すると、ブラウザはすべての通信方式を試行（UXが悪化）

---

### Q2: authenticatorAttachmentとtransportsの違いは？

**authenticatorAttachment** - 登録時に使用

| 値 | 説明 | 用途 |
|----|------|------|
| `platform` | デバイス内蔵認証器のみ許可 | Touch ID, Face ID, Windows Hello |
| `cross-platform` | 外部認証器のみ許可 | セキュリティキー、スマホ |
| 省略/null | 制約なし | どちらでもOK |

```javascript
// 登録時のオプション
const createOptions = {
  publicKey: {
    authenticatorSelection: {
      authenticatorAttachment: "platform",  // Touch IDのみ許可
      userVerification: "required"
    }
  }
};
```

**transports** - 認証時に使用

| 値 | 説明 |
|----|------|
| `internal` | プラットフォーム認証器経由 |
| `hybrid` | クロスデバイス経由 |
| `usb` | USB経由 |
| `nfc` | NFC経由 |
| `ble` | Bluetooth経由 |

```javascript
// 認証時のオプション
const getOptions = {
  publicKey: {
    allowCredentials: [{
      type: "public-key",
      id: credentialId,
      transports: ["internal", "hybrid"]  // 通信方式のヒント
    }]
  }
};
```

**まとめ**:
- `authenticatorAttachment`: 登録時に「どの種類の認証器を使うか」を制限
- `transports`: 認証時に「どの通信方式で認証器に接続するか」のヒント

---

### Q3: サブドメインデプロイでのrpId設定

**シナリオ**:
```
API:  https://api.local.dev
認証: https://auth.local.dev
Web:  https://sample.local.dev
```

**正しいrpId設定**:

```json
{
  "rp": {
    "id": "local.dev",
    "name": "My Service"
  }
}
```

**ルール**:
1. `rpId`は現在のオリジンと同じか、その親ドメインである必要がある
2. `auth.local.dev`では`local.dev`または`auth.local.dev`を使用可能
3. 複数サブドメインで同じPasskeyを使うなら、共通の親ドメインを使用

**NGパターン**:
```
オリジン: https://auth.local.dev
rpId: "other.local.dev"  // NG: 兄弟ドメインは不可
rpId: "dev"              // NG: 有効なeTLD+1ではない
```

**OKパターン**:
```
オリジン: https://auth.local.dev
rpId: "local.dev"        // OK: 親ドメイン
rpId: "auth.local.dev"   // OK: 完全一致
```

---

## デバッグ手順

### 1. ブラウザの開発者ツールでレスポンスを確認

```
Network → fido2-authentication-challenge → Response
```

確認項目:
- [ ] `rpId`または`rp.id`が含まれているか
- [ ] `allowCredentials`に`transports`が含まれているか
- [ ] Credential IDが正しいか

### 2. サーバーログを確認

```bash
# transportsのパース状況
grep "parseTransports" logs/app.log

# allowCredentialsの生成
grep "allowCredentials" logs/app.log
```

### 3. データベースを確認

```sql
SELECT id, username, rp_id, transports
FROM webauthn_credentials
WHERE tenant_id = 'your-tenant-id';
```

確認項目:
- [ ] `rp_id`が期待値と一致するか
- [ ] `transports`が正しく保存されているか

---

## 参考リソース

- [W3C WebAuthn Level 2 - rpId](https://www.w3.org/TR/webauthn-2/#relying-party-identifier)
- [W3C WebAuthn Level 2 - transports](https://www.w3.org/TR/webauthn-2/#dom-publickeycredentialdescriptor-transports)
- [MDN - Web Authentication API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Authentication_API)

---

## 関連ドキュメント

- [FIDO2 認証フローとインターフェース詳細](fido2-authentication-flow-interface.md)
- [FIDO2 登録フローとインターフェース詳細](fido2-registration-flow-interface.md)
- [FIDO2・パスキー・Discoverable Credential](fido2-passkey-discoverable-credential.md)
