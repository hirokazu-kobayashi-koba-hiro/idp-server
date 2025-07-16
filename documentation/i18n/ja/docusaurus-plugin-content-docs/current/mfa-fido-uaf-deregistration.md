# FIDO-UAF 解除フロー

このドキュメントでは、`fido-uaf` を用いた認証デバイス登録の一連の流れを解説します。

---

## 🧭 全体の流れ

1. ログイン
2. MFA FIDO-UAF解除要求
3. FIDO-UAF解除リクエスト
4. UserInfoで認証デバイスの登録状況を確認する

---

## 🔁 シーケンス図（Mermaid）

```mermaid
sequenceDiagram
    participant App
    participant IdP
    participant FIDO as FIDO Server
    note over App, IdP: 1. ログイン。認可コードフローなどでアクセストークンを取得する
    App ->> IdP: 2. POST {tenant-id}/v1/me/mfa/fido-uaf-deregistration
    IdP -->> App: 200 OK (transaction_id)
    App ->> IdP: 3. POST {tenant-id}/v1/authentications/{id}/fido-uaf-deregistration
    IdP ->> FIDO: 解除リクエスト
    FIDO -->> IdP: 解除レスポンス
    IdP -->> App: 200 OK
    App ->> IdP: 4. GET /userinfo
    IdP -->> App: 200 OK (authentication_devices)

```

---

## 1. ログイン

[認可コードフロー](authorization-code-flow.md)を参照。

## 2. FIDO-UAF解除開始リクエスト

```http
POST {tenant-id}/v1/me/mfa/fido-uaf-deregistration
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "device_id": "UUID"
}
```

### リクエストボディのパラメータ説明

| パラメータ名      | 必須 | 説明        |
|-------------|----|-----------|
| `device_id` | ✅️ | 認証デバイスID。 |

* レスポンス `200 OK`

```json
{
  "id": "UUID"
}
```

レスポンスの `id` はFIDO-UAFチャレンジ・FIDO UAF登録APIのPathに指定する

---

## 3. FIDO-UAFチャレンジ

```http
POST {tenant-id}/v1/authentications/{id}/fido-uaf-deregistration

{
 FIDOサーバーのAPI仕様に沿ったパラメータを指定する
}
```

* レスポンス `200 OK`

```
{
  FIDOサーバーのAPI仕様に沿ったパラメータ
}
```

## 4. UserInfoでデバイス登録を確認

指定した認証デバイスがないことを、Userinfoで確認できます。

```http
GET /{tenant}/v1/userinfo
Authorization: Bearer {access_token}
```

```
{
  "sub": "user-id",
  "authentication_devices": [
    ...
  ]
}
```

