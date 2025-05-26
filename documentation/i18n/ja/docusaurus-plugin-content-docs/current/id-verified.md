# 身元確認済みID

## 概要

idp-server は、OpenID Connect for Identity Assurance (OIDC4IDA) に準拠した 身元確認済みのID連携 をサポートしています。

OIDC4IDA によって、クライアント（Relying Party, RP）は IDトークンや UserInfo から 検証済みの本人情報（verified claims） を取得することができます。

idp-server では以下のような用途に対応できます：

* 銀行・金融サービスでの「本人確認済みログイン」
* eKYC 結果を元にした段階的アクセス制御


## 身元確認方法

`idp-server` は OIDC4IDAの機能を利用するに当たり身元確認方法を2つ用意しています。

1. 身元確認の申込みから利用するパターン
2. 身元確認結果を直接登録するパターン

### 身元確認の申込みから利用するパターン

* `idp-server` 経由で外部身元確認サービスへの申込みを実施し、身元確認済みの審査結果をidp-serverに登録します。

#### ステップ

1. アプリから身元確認の申込みを行い、idp-server経由で外部身元確認サービスに連携する
2. アプリでeKYCの実施し、idp-server経由で結果を外部身元確認サービスに連携する
3. 外部身元確認サービスでの審査
4. idp-serverが外部身元確認サービスから審査結果を受信する
5. 審査結果に応じて、ユーザーの検証済みクレームを更新する

※ 外部身元確認サービスのAPI仕様に合わせて、柔軟に `idp-server` の各申込のプロセスの設定を設定することができます。

#### シーケンス

```mermaid
sequenceDiagram
    participant App as 🧑‍💻 アプリ
    participant IdP as 🔐 idp-server
    participant EKYC as 🛰 外部身元確認サービス

    Note over App,EKYC: eKYC連携フロー

    App->>IdP: 身元確認の申込み
    IdP->>EKYC: 外部身元確認サービスへ申込み情報連携
    EKYC->>EKYC: 申込みデータを記録
    EKYC-->>IdP: 申込みIDを返却
    IdP->>IdP: 設定に応じて最小限の申込みデータを記録
    IdP-->>App: 申込みIDなどを返却
    
    App->>App: eKYCの実施
    App->>IdP: eKYC結果の送信（例：本人確認書類＋顔写真）
    IdP->>EKYC: eKYC結果を中継
    EKYC->>EKYC: eKYC結果を記録
    EKYC-->>IdP: 申込みIDを返却
    IdP->>IdP: 設定に応じて最小限のデータを記録
    IdP-->>App: 申込みIDなどを返却
    
    EKYC->>EKYC: 審査処理

    EKYC-->>IdP: 審査結果を通知（非同期コールバック）
    IdP->>IdP: 審査結果に応じてユーザーの検証済みクレームを更新する
    IdP-->>App: 結果通知 or データ取得（成功/失敗/保留）

    Note over IdP: 審査結果に応じてIDトークンへ<br/>verified_claims等を反映できる

```

### 身元確認結果を直接登録するパターン

外部身元確認サービスで実施した身元確認済みのデータを直接 `idp-server` に登録することができます。

#### ステップ

1. 外部身元確認サービスの審査結果をidp-serverに送信する
2. ユーザーの検証済みクレームを更新する

#### シーケンス

```mermaid
sequenceDiagram
    participant App as 🧑‍💻 アプリ
    participant IdP as 🔐 idp-server
    participant EKYC as 🛰 外部身元確認サービス

    EKYC-->>IdP: 審査結果を通知
    IdP->>IdP: ユーザーの検証済みクレームを更新する
    IdP-->>App: 結果通知 or データ取得（成功/失敗/保留）

    Note over IdP: IDトークンへ<br/>verified_claims等を反映できる

```

## 検証済みクレームの取得

`idp-server` は検証済みクレームを取得する方法を2つ用意しています。

1. OIDC4IDAの標準フローで、IDトークンに設定する方法
2. idp-server独自仕様で、アクセストークンに設定する方法

### OIDC4IDAの標準フローで、IDトークンに設定する方法

`idp-server` は、[OIDC4IDA Core 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html) に基づき、IDトークン内に `verified_claims` を含めることができます。

このフローでは、OpenID Connect 標準の `claims` パラメーターを利用して、クライアントが取得したい **verified claims（検証済みの本人情報）** をリクエスト時に指定します。

#### 例：claims パラメータを使った認可リクエスト

```json
{
  "userinfo": {
    "verified_claims": {
      "verification": {
        "trust_framework": { "value": "jp-trust" }
      },
      "claims": {
        "name": null,
        "birthdate": null
      }
    }
  }
}
```

この JSON を `claims` パラメータとしてエンコードし、認可リクエストに含めることで、  
`idp-server` は以下のような条件を満たす場合に `verified_claims` を `id_token` に埋め込みます：

#### 埋め込み条件

- リクエストに `claims` パラメータが含まれていること
- 対象ユーザーの身元確認が完了しており、`verified_claims` に該当のclaimを所持していること


#### IDトークン出力例

jwt
```
eyJraWQiOiJpZF90b2tlbl9uZXh0YXV0aCIsImFsZyI6IlJTMjU2In0.eyJhdF9oYXNoIjoiTEZHNk4zMEM0UjFTSkxiUHZ5el9CdyIsInN1YiI6IjNlYzA1NWE4LTgwMDAtNDRhMi04Njc3LWU3MGViZmY0MTRlMiIsInpvbmVpbmZvIjoiamEtanAiLCJ2ZXJpZmllZF9jbGFpbXMiOnsiY2xhaW1zIjp7ImJpcnRoZGF0ZSI6IjE5NzYtMDMtMTEiLCJhZGRyZXNzIjp7ImNvdW50cnkiOiJVSyIsInN0cmVldF9hZGRyZXNzIjoiMTIyIEJ1cm5zIENyZXNjZW50IiwibG9jYWxpdHkiOiJFZGluYnVyZ2giLCJwb3N0YWxfY29kZSI6IkVIMSA5R1AifSwiZ2l2ZW5fbmFtZSI6IlNhcmFoIiwiZmFtaWx5X25hbWUiOiJNZXJlZHl0aCJ9LCJ2ZXJpZmljYXRpb24iOnsiZXZpZGVuY2UiOlt7ImNoZWNrX2RldGFpbHMiOlt7ImNoZWNrX21ldGhvZCI6ImtidiIsIm9yZ2FuaXphdGlvbiI6IlRoZUNyZWRpdEJ1cmVhdSIsInR4biI6ImtidjEtaGY5MzRobjA5MjM0bmcwM2pqMyJ9XSwicmVjb3JkIjp7InNvdXJjZSI6eyJuYW1lIjoiVGhlQ3JlZGl0QnVyZWF1In0sInR5cGUiOiJtb3J0Z2FnZV9hY2NvdW50In0sInRpbWUiOiIyMDIxLTA0LTA5VDE0OjEyWiIsInR5cGUiOiJlbGVjdHJvbmljX3JlY29yZCJ9LHsiY2hlY2tfZGV0YWlscyI6W3siY2hlY2tfbWV0aG9kIjoia2J2Iiwib3JnYW5pemF0aW9uIjoiT3BlbkJhbmtpbmdUUFAiLCJ0eG4iOiJrYnYyLW5tMGYyM3U5NDU5ZmozOHU1ajYifV0sInJlY29yZCI6eyJzb3VyY2UiOnsibmFtZSI6IlRoZUJhbmsifSwidHlwZSI6ImJhbmtfYWNjb3VudCJ9LCJ0aW1lIjoiMjAyMS0wNC0wOVQxNDoxMloiLCJ0eXBlIjoiZWxlY3Ryb25pY19yZWNvcmQifV0sInRydXN0X2ZyYW1ld29yayI6ImVpZGFzIn19LCJiaXJ0aGRhdGUiOiIyMDAwLTAyLTAyIiwiZ2VuZGVyIjoib3RoZXIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvNjdlN2VhZTYtNjJiMC00NTAwLTllZmYtODc0NTlmNjNmYzY2IiwicHJlZmVycmVkX3VzZXJuYW1lIjoiaWNoaXJvIiwibG9jYWxlIjoibG9jYWxlIiwidXBkYXRlZF9hdCI6MTc0ODE1MjgwMCwiYXV0aF90aW1lIjoxNzQ4MzEyNjU5LCJuaWNrbmFtZSI6Iml0byIsImV4cCI6MTc0ODMxNjI1OSwiaWF0IjoxNzQ4MzEyNjU5LCJlbWFpbCI6Iml0by5pY2hpcm9AZ21haWwuY29tIiwid2Vic2l0ZSI6Imh0dHBzOi8vZXhhbXBsZS5jb20iLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsInByb2ZpbGUiOiJodHRwczovL2V4YW1wbGUuY29tL3Byb2ZpbGVzLzEyMyIsInBob25lX251bWJlcl92ZXJpZmllZCI6ZmFsc2UsImdpdmVuX25hbWUiOiJpY2hpcm8iLCJtaWRkbGVfbmFtZSI6Im1hYyIsInBpY3R1cmUiOiJodHRwczovL2V4YW1wbGUuY29tL3BpY3R1cmVzLzEyMyIsImF1ZCI6ImNsaWVudFNlY3JldFBvc3QiLCJjX2hhc2giOiJvdWVfNExEaTZWRGtYdzVmWVBaWjJBIiwic19oYXNoIjoiLWdhU2JmRXE3RU5XaVExSVI5UV9lUSIsIm5hbWUiOiJpdG8gaWNoaXJvIiwicGhvbmVfbnVtYmVyIjoiMDkwMTIzNDU2NzgiLCJmYW1pbHlfbmFtZSI6Iml0byJ9.SiuW5c7aNJcVAQTc6hVFOFH_fHeo_4L8ntX8ht-UcG1r1n-T4GMgxP4taZSEatoDjoxAzgMWvHhgYbz5LZ53v1hOlguBvf4-UoIuYpvPrSNTCDu8TjUD4sU6Fx1bZdBpt5mHkcE1U-ljrJWsaHGzcDIsJHh6RzLixCl5vUKjUXB-aeiu_yPlMsbBAaXsIoEqpxITGc1k8QvYa7UotGDl88v14OCY6WucqPg1aweZj3aXuuMx35mU9kVaavz0BnWuYfZzfU8LFmUDfo5CLwKubkc70a988pxK_o8EVt_UxHKR1qMoOmWlKs-_SRhpQEG-qdS8HH3sWLFbur5s-hG6VA
```

payload
```json
{
  "at_hash": "LFG6N30C4R1SJLbPvyz_Bw",
  "sub": "3ec055a8-8000-44a2-8677-e70ebff414e2",
  "zoneinfo": "ja-jp",
  "verified_claims": {
    "claims": {
      "birthdate": "1976-03-11",
      "address": {
        "country": "UK",
        "street_address": "122 Burns Crescent",
        "locality": "Edinburgh",
        "postal_code": "EH1 9GP"
      },
      "given_name": "Sarah",
      "family_name": "Meredyth"
    },
    "verification": {
      "evidence": [
        {
          "check_details": [
            {
              "check_method": "kbv",
              "organization": "TheCreditBureau",
              "txn": "kbv1-hf934hn09234ng03jj3"
            }
          ],
          "record": {
            "source": {
              "name": "TheCreditBureau"
            },
            "type": "mortgage_account"
          },
          "time": "2021-04-09T14:12Z",
          "type": "electronic_record"
        },
        {
          "check_details": [
            {
              "check_method": "kbv",
              "organization": "OpenBankingTPP",
              "txn": "kbv2-nm0f23u9459fj38u5j6"
            }
          ],
          "record": {
            "source": {
              "name": "TheBank"
            },
            "type": "bank_account"
          },
          "time": "2021-04-09T14:12Z",
          "type": "electronic_record"
        }
      ],
      "trust_framework": "eidas"
    }
  },
  "birthdate": "2000-02-02",
  "gender": "other",
  "iss": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
  "preferred_username": "ichiro",
  "locale": "locale",
  "updated_at": 1748152800,
  "auth_time": 1748312659,
  "nickname": "ito",
  "exp": 1748316259,
  "iat": 1748312659,
  "email": "ito.ichiro@gmail.com",
  "website": "https://example.com",
  "email_verified": false,
  "profile": "https://example.com/profiles/123",
  "phone_number_verified": false,
  "given_name": "ichiro",
  "middle_name": "mac",
  "picture": "https://example.com/pictures/123",
  "aud": "clientSecretPost",
  "c_hash": "oue_4LDi6VDkXw5fYPZZ2A",
  "s_hash": "-gaSbfEq7ENWiQ1IR9Q_eQ",
  "name": "ito ichiro",
  "phone_number": "09012345678",
  "family_name": "ito"
}
```

### idp-server独自仕様で、アクセストークンに設定する方法

#### 概要
`idp-server` では、スコープに `verified_claims:`プレフィックス付きスコープが含まれる場合、身元確認済みの属性を
アクセストークンに含めることができます。

これは `AccessTokenSelectiveVerifiedClaimsCreator` により実現され、以下の条件で動作します：

* `id_token_strict_mode` が無効であること
* `enabled_access_token_selective_verified_claims` が有効であること
* 対象スコープが `verified_claims:` プレフィックスが含まれていること
* ユーザーが　`verified_claims:`　を所持していること

対象スコープが `verified_claims:name` の場合、ユーザーが持つverified_claimsの `name` をプロパティに設定します。

#### 例：verified_claims:xxスコープ

`scope=openid profile verified_claims:name verified_claims:birthdate`


#### アクセストークン出力例

jwt
```
eyJhbGciOiJSUzI1NiIsInR5cCI6ImF0K2p3dCIsImtpZCI6ImlkX3Rva2VuX25leHRhdXRoIn0.eyJzdWIiOiIzZWMwNTVhOC04MDAwLTQ0YTItODY3Ny1lNzBlYmZmNDE0ZTIiLCJ2ZXJpZmllZF9jbGFpbXMiOnsiZmFtaWx5X25hbWUiOiJNZXJlZHl0aCJ9LCJzY29wZSI6InBob25lIG1hbmFnZW1lbnQgb3BlbmlkIHRyYW5zZmVycyBwcm9maWxlIHZlcmlmaWVkX2NsYWltczpmYW1pbHlfbmFtZSBlbWFpbCBhY2NvdW50IiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgwLzY3ZTdlYWU2LTYyYjAtNDUwMC05ZWZmLTg3NDU5ZjYzZmM2NiIsImV4cCI6MTc0ODMxNjI1OSwiaWF0IjoxNzQ4MzEyNjU5LCJjbGllbnRfaWQiOiJjbGllbnRTZWNyZXRQb3N0IiwianRpIjoiYmIyNTA3NjEtODZiNC00ZTk0LTk0ZWUtYmU1MTZlM2NjOTE2Iiwia2V5IjoidmFsdWUifQ.O2VBgXu756jKURWAiVxsD_o865Adp8es-vZ2ueVRAK1MBJ5LiGE9qoDA1ty6xnqWEq7r3-arauIPBj2LdVJFyMmsbe7aMsR90q7D2sCZYvGTaVQlz85evMtIGCw8HQ_Co9QHpGSES7vJTtlRMG-5pSc9HHnQ0IaLWkaSuyV1jj39VK88PRWJoffGwfdXDUZnh6n7aMYdSH2Kl5TyTKdXf5ID5txa_sXNpqCVb1kFDCHA6A7_60Rok-mtXF-jofn2-CpC7XX38RIuNbM1AzFn2butJXKaGKbiQOUhhdxB0QAPfsGcKi10FmyIllkA8mZPv-5D570RC9hemkaJAfn4xQ
```

payload
```json
{
  "sub": "3ec055a8-8000-44a2-8677-e70ebff414e2",
  "verified_claims": {
    "family_name": "Meredyth"
  },
  "scope": "phone management openid transfers profile verified_claims:family_name email account",
  "iss": "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66",
  "exp": 1748316259,
  "iat": 1748312659,
  "client_id": "clientSecretPost",
  "jti": "bb250761-86b4-4e94-94ee-be516e3cc916",
  "key": "value"
}
```
