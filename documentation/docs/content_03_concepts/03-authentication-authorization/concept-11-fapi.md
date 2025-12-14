# FAPI（Financial-grade API）

## FAPIとは

**FAPI（Financial-grade API）** は、金融取引など高セキュリティが要求される環境でOAuth 2.0/OpenID Connectを安全に利用するための仕様です。

**目的**:
- 標準のOAuth 2.0/OIDCよりも厳格なセキュリティ要件を定義
- オープンバンキング（PSD2、Open Banking UK）等で要求される金融グレードのセキュリティを実現

**主な特徴**:
- 署名付きリクエスト・レスポンスの強制によるデータ改竄防止
- mTLS（Mutual TLS）によるトークンバインディングによるアクセストークン盗難の防御


## idp-serverのFAPI対応

### 対応状況

| プロファイル | 選択基準（用途） | idp-server対応 | 実装モジュール |
|------------|----------------|---------------|--------------|
| **FAPI 1.0 Baseline** | **読み取り専用API**<br/>残高照会、取引履歴参照等 | ✅ 対応済み | `idp-server-core-extension-fapi` |
| **FAPI 1.0 Advance** | **書き込みAPI**<br/>送金実行、口座設定変更、決済実行等 | ✅ 対応済み | `idp-server-core-extension-fapi` |
| **FAPI CIBA** | **デバイス分離認証**<br/>ATM送金認証、窓口本人確認、コールセンター認証等 | ✅ 対応済み | `idp-server-core-extension-fapi-ciba` |

**選択の原則**: 参照だけならBaseline、書き込みがあればAdvance、デバイスが分離しているならCIBA

### プロファイル適用条件

idp-serverは、**リクエストされたスコープ**に基づいてFAPIプロファイルを自動判定します。

**判定ロジック**（優先順位順）:
1. **FAPI Advance**: テナント設定の `fapiAdvanceScopes` に一致するスコープが含まれる場合
2. **FAPI Baseline**: テナント設定の `fapiBaselineScopes` に一致するスコープが含まれる場合
3. **OIDC**: スコープに `openid` が含まれる場合
4. **OAuth 2.0**: それ以外

**設定例**（テナント設定）:
```json
{
  "extension": {
    "fapi_baseline_scopes": ["read", "account"],
    "fapi_advance_scopes": ["write", "transfers", "payment_initiation"]
  }
}
```

## 3つのプロファイルの特徴

### FAPI Baseline Profile

**用途**: 読み取り専用API（残高照会、取引履歴参照等）

**主要要件**:
- 署名付きリクエストオブジェクト（PS256/ES256）
- PKCE必須
- private_key_jwt または mTLS クライアント認証

### FAPI Advance Profile

**用途**: 書き込みAPI（送金実行、口座設定変更等）

**Baselineに追加される要件**:
- PAR（Pushed Authorization Requests）必須
- JARM（JWT Secured Authorization Response）必須
- Sender-constrained アクセストークン必須（mTLS binding）
- Authorization Details サポート

### FAPI CIBA Profile

**用途**: バックチャネル認証（デバイス分離認証）

**CIBA固有の要件**:
- リクエストオブジェクト有効期限: 最大60分
- binding_message: authorization_detailsがない場合は必須
- Pushモード禁止（pollまたはpingのみ）
- Sender-constrained アクセストークン必須
- aud claim必須（Issuer URL）

**詳細ガイド**: [FAPI CIBA Profile - プロトコル仕様](../../content_04_protocols/protocol-05-fapi-ciba.md)


## mTLS（Mutual TLS）によるトークンバインディング

### トークンバインディングとは

**トークンバインディング**は、アクセストークンを**特定のクライアントに紐付ける**セキュリティ機構です。

**通常のBearer Tokenの問題点**:
- アクセストークンは「持っている者が使える」Bearer形式
- トークンが盗まれると、攻撃者が自由に使用できる
- ネットワーク盗聴、マルウェア、フィッシングで盗難リスク

**トークンバインディングによる解決**:
- アクセストークンを**クライアント証明書に紐付ける**
- トークンだけ盗んでも、証明書がなければ使用不可
- **金融取引など高セキュリティ環境で必須**の仕組み

### Sender-constrained Access Tokensの仕組み

mTLSによるトークンバインディングは、**証明書のサムプリント（指紋）** をアクセストークンに埋め込むことで実現します。

**証明書サムプリント（cnf:x5t#S256）**:
```
1. クライアント証明書のSHA-256ハッシュを計算
2. Base64エンコードしてサムプリントを生成
3. アクセストークン（JWT）の cnf クレームに格納

{
  "iss": "https://idp.example.com",
  "sub": "user123",
  "cnf": {
    "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2"  ← 証明書サムプリント
  }
}
```

**検証の仕組み**:
- API呼び出し時、サーバーはクライアント証明書とトークン内サムプリントを照合
- 一致した場合のみAPIアクセスを許可
- トークンだけ盗んでも、証明書がなければ検証失敗

### 防御できる攻撃

mTLSトークンバインディングにより、以下の攻撃を防御できます：

| 攻撃シナリオ | 通常のBearer Token | mTLSバインディング |
|:---|:---:|:---:|
| **トークン盗難** | ❌ 盗んだトークンで自由にアクセス可能 | ✅ 証明書がないため使用不可 |
| **ネットワーク盗聴** | ❌ HTTPSでも盗聴後に使用可能 | ✅ 証明書がないため使用不可 |
| **マルウェア** | ❌ トークンをコピーして外部送信 | ✅ 証明書の秘密鍵がなければ無効 |
| **リプレイ攻撃** | ❌ トークン有効期限内は再利用可能 | ✅ 証明書がないため再利用不可 |

**重要**: FAPI Advance/CIBAでは、このSender-constrained Access Tokensが**必須要件**です。

### システム全体アーキテクチャ

idp-serverは、mTLSトークンバインディングを実現するために、**3層のアーキテクチャ**で責務を分離する設計を想定しています。

```
┌─────────────────────────────────────────────────────────────────┐
│                        クライアント層                            │
│                                                                 │
│  ┌─────────────────┐              ┌──────────────────┐        │
│  │  アプリケーション  │    HTTPS     │   BFF Server     │        │
│  │ Web/iOS/Android │─────────────▶│ (証明書管理担当)  │        │
│  └─────────────────┘              └──────────────────┘        │
│                                            │                    │
└────────────────────────────────────────────┼────────────────────┘
                                             │ mTLS
                                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         インフラ層                               │
│              役割: mTLS終端・証明書検証                          │
│                                                                 │
│                  ┌────────────────────┐                        │
│                  │  Webサーバー/LB     │                        │
│                  │  nginx/AWS ALB     │                        │
│                  └────────────────────┘                        │
│                            │                                    │
└────────────────────────────┼────────────────────────────────────┘
                             │ HTTP + 証明書情報（HTTPヘッダー）
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     アプリケーション層                            │
│          役割: FAPI要件検証・トークン発行                         │
│                                                                 │
│                  ┌────────────────────┐                        │
│                  │    idp-server      │                        │
│                  │   Spring Boot      │                        │
│                  └────────────────────┘                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**各層の役割（トークンバインディングにおける責務）**:

- **クライアント層**:
  - アプリケーション（Web/iOS/Android）は通常のHTTPS通信を行う
  - BFF Serverがクライアント証明書を保持し、mTLS接続を担当
  - **トークンバインディングへの貢献**: クライアント証明書の秘密鍵を安全に管理
  - **注意**: ネイティブアプリでの証明書管理はセキュリティリスクがあるため、BFFパターンが必須

- **インフラ層**:
  - nginx/AWS ALBがmTLS終端を行い、クライアント証明書の真正性を検証
  - 検証済み証明書をPEMエンコードし、HTTPヘッダー（`X-SSL-Cert`等）で転送
  - **トークンバインディングへの貢献**: 証明書の検証とアプリケーション層への安全な転送

- **アプリケーション層**:
  - idp-serverがHTTPヘッダーから証明書情報を取得
  - 証明書サムプリント（SHA-256ハッシュ）を計算
  - `cnf:x5t#S256`クレームとしてアクセストークン（JWT）に埋め込む
  - **トークンバインディングへの貢献**: サムプリント計算とトークンへの埋め込み

**idp-serverの役割と前提**:

1. **idp-serverはアプリケーション層のみを担当**: mTLS終端やクライアント証明書の検証は行わない
2. **前提条件**: nginx/AWS ALB等のインフラ層がmTLS終端を実施し、証明書情報を`x-ssl-cert`ヘッダーで転送済みであること
3. **実装内容**: クライアント/サーバー設定で `tls_client_certificate_bound_access_tokens: true` を検証し、FAPI要件を満たすことを保証
4. **BFFパターンが必須**: ネイティブアプリ（Web/iOS/Android）での証明書管理にはセキュリティリスク（リバースエンジニアリング、Root化/Jailbreak）があるため、BFFまたはクラウドプロキシの使用が必須

### トークンバインディングのフロー

**1. 認証フェーズ（トークン発行）**:
```
クライアント → nginx → idp-server
        (mTLS)   (HTTP + X-SSL-Cert)

1. nginx: クライアント証明書を検証
2. nginx: 証明書情報をHTTPヘッダーで転送
3. idp-server: 証明書サムプリントを計算
4. idp-server: cnf:x5t#S256 付きアクセストークンを発行
```

**2. API呼び出しフェーズ（トークン検証）**:
```
クライアント → nginx → Resource Server
        (mTLS)   (HTTP + X-SSL-Cert + Authorization)

1. nginx: クライアント証明書を検証
2. Resource Server: トークン内 cnf:x5t#S256 を抽出
3. Resource Server: 実際の証明書サムプリントを計算
4. Resource Server: 両者が一致すればアクセス許可
```

**重要**: トークンと証明書の両方が揃って初めてAPIアクセスが可能になります



### セキュリティ要件比較

| セキュリティ要件 | FAPI Baseline | FAPI Advance | FAPI CIBA |
|----------------|--------------|-------------|-----------|
| **署名付きリクエストオブジェクト** | ✅ 必須 | ✅ 必須 | ✅ 必須 |
| **署名アルゴリズム** | ✅ PS256/ES256 | ✅ PS256/ES256 | ✅ PS256/ES256 |
| **クライアント認証** | ✅ private_key_jwt/mTLS | ✅ private_key_jwt/mTLS | ✅ private_key_jwt/mTLS |
| **PKCE** | ✅ 必須 | ✅ 必須 | - |
| **トークンバインディング** | ⚠️ 推奨 | ✅ 必須（mTLS） | ✅ 必須（mTLS） |
| **PAR** | ⚠️ 推奨 | ✅ 必須 | - |
| **JARM** | - | ✅ 必須 | - |
| **Authorization Details** | - | ✅ サポート | ✅ サポート |
| **Pushモード禁止** | - | - | ✅ poll/pingのみ |
| **binding_message** | - | - | ✅ 条件付き必須 |


## まとめ

FAPIは、**金融グレードのセキュリティを実現するOAuth/OIDC拡張仕様**です。

**idp-serverのFAPI対応のポイント**:
- プラグインアーキテクチャによる柔軟な検証
- テナント設定による動的なプロファイル切り替え
- nginx/AWS ALBによるmTLS終端サポート
- 3つのプロファイル（Baseline/Advance/CIBA）完全対応

**プロファイル選択の基準**:
- 読み取り専用 → FAPI Baseline
- 書き込み → FAPI Advance
- デバイス分離認証 → FAPI CIBA

## 関連ドキュメント

- [FAPI CIBA Profile - プロトコル仕様](../../content_04_protocols/protocol-05-fapi-ciba.md)
- [CIBA + FIDO-UAF](../../content_05_how-to/how-to-12-ciba-flow-fido-uaf.md)
- [クライアント設定](../01-foundation/concept-03-client.md)
- [トークン管理](./concept-13-token-management.md)

## 参考仕様

- [FAPI 1.0 Baseline Profile](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [FAPI 1.0 Advanced Profile (Read and Write)](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [FAPI CIBA Profile](https://openid.net/specs/openid-financial-api-ciba.html)
- [RFC 9396 - OAuth 2.0 Rich Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9396)

---

**作成日**: 2025-01-15
**対象**: idp-server利用者、システムアーキテクト
**習得スキル**: FAPI概要理解、プロファイル選択、システムアーキテクチャ理解
