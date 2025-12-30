# OAuth 2.0 Grant Management

Grant Management は、ユーザーが付与した認可（Grant）を照会・管理するための API を定義した仕様です。

---

## 目次

1. [第1部: 概要編](#第1部-概要編)
2. [第2部: 詳細編](#第2部-詳細編)
3. [第3部: セキュリティ](#第3部-セキュリティ)

---

## 第1部: 概要編

### Grant Management とは？

Grant Management は、エンドユーザーと RP が認可サーバーで管理される**認可（Grant）を照会・更新・削除**できる API です。

```
Grant のライフサイクル:

  ┌────────────┐      認可リクエスト      ┌────────────┐
  │     RP     │ ──────────────────────► │     AS     │
  │            │ ◄────────────────────── │            │
  └────────────┘     Grant 作成           └────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │    Grant    │
                   │ - sub       │
                   │ - client_id │
                   │ - scope     │
                   │ - claims    │
                   └─────────────┘
                          │
       ┌──────────────────┼──────────────────┐
       ▼                  ▼                  ▼
    照会(GET)          更新(PATCH)        削除(DELETE)
```

### なぜ Grant Management が必要なのか？

#### 背景と歴史

従来のOAuth 2.0では、ユーザーが一度認可を付与すると、その後の管理が困難でした：

| 課題 | 従来の方法 | Grant Management による解決 |
|------|----------|----------------------------|
| 認可状況の確認 | ASの独自UIのみ | 標準APIで照会可能 |
| 認可の取り消し | ASの独自UIのみ | DELETE APIで取り消し |
| 段階的な認可 | 毎回新規認可 | 既存Grantを更新 |
| GDPR対応 | 個別実装 | 標準APIでユーザーに制御権 |
| RP側からの確認 | 不可能 | GET APIで状況確認可能 |
| 監査ログ | 個別実装 | 標準APIで統一的なログ |

Grant Managementは2020年頃からFAPI 2.0の一部として策定が始まり、ユーザーのプライバシーと自律性を重視した設計となっています。

#### 脅威モデル

Grant Management が対処する主な脅威：

1. **不正なGrant操作**
   ```
   攻撃シナリオ:
   1. 攻撃者が別ユーザーのGrant IDを推測
   2. 攻撃者がGrant情報を照会または削除を試みる
   3. 成功すればユーザーのプライバシー侵害

   対策:
   - Grant IDに十分なエントロピー（128ビット以上）
   - アクセス制御（subject検証）
   - 監査ログ
   ```

2. **Grant情報の漏洩**
   ```
   攻撃シナリオ:
   1. ネットワーク盗聴でGrant情報を取得
   2. 攻撃者がユーザーの認可状況を把握

   対策:
   - TLS必須
   - 機密情報の最小化
   - レスポンスの適切なキャッシュ制御
   ```

3. **GDPR違反**
   ```
   リスク:
   - ユーザーが自分のデータを管理できない
   - 忘れられる権利が実現できない

   対策:
   - Grant Management APIでユーザーに制御権を付与
   - データエクスポート機能
   - 削除の完全性保証
   ```

### 主要なユースケース

#### ユースケース1: ユーザー向けダッシュボード

```
シナリオ:
  ユーザーが「連携アプリ管理」画面を表示

表示内容:
  1. 銀行アプリ
     - 権限: 口座情報の閲覧
     - 付与日: 2024-01-15
     - 最終利用: 2024-01-20
     - [取り消し]ボタン

  2. 家計簿アプリ
     - 権限: 取引履歴の閲覧
     - 付与日: 2024-02-01
     - 最終利用: 2024-02-05
     - [取り消し]ボタン

実装:
  GET /grants で一覧取得
  DELETE /grants/{grant_id} で取り消し
```

#### ユースケース2: 段階的な認可

```
シナリオ:
  ユーザーが銀行アプリを使用中、決済機能を初めて使う

フロー:
  1. 初回ログイン
     scope=openid profile accounts:read
     → Grant A 作成

  2. 決済機能を使う（後日）
     - アプリ: 「決済権限が必要です」
     - grant_id=A
     - grant_management_action=merge
     - scope=openid profile accounts:read payments:write

  3. AS: ユーザーに追加同意を求める
     「銀行アプリが決済権限を要求しています」

  4. 同意後、Grant A が更新
     scope=openid profile accounts:read payments:write

メリット:
  - 必要な時に必要な権限のみ
  - ユーザー体験の向上
  - セキュリティの向上（最小権限の原則）
```

#### ユースケース3: RPによる状況確認

```
シナリオ:
  ユーザーが「家計簿アプリで銀行口座が同期できない」と問い合わせ

RPの対応:
  GET /grants/{grant_id}

  レスポンス:
  {
    "grant_id": "...",
    "status": "active",
    "scope": "openid accounts:read"
  }

  → scopeが正しいことを確認
  → 別の原因を調査
```

#### ユースケース4: GDPR対応（データポータビリティ）

```
シナリオ:
  ユーザーが「自分のデータをエクスポートしたい」と要求

フロー:
  1. GET /grants でユーザーのすべてのGrantを取得
  2. 各Grantの詳細情報をJSON形式でエクスポート
  3. ユーザーにダウンロード提供

データ例:
{
  "user_id": "user-123",
  "export_date": "2024-01-20T10:00:00Z",
  "grants": [
    {
      "grant_id": "PTRWWMo_YsGxl17r6MBj5",
      "client_name": "Banking App",
      "scope": "openid accounts:read",
      "created_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

### 規制対応

#### GDPR（一般データ保護規則）

| GDPR要件 | Grant Management による実現 |
|---------|---------------------------|
| **アクセス権** | GET /grants でユーザーが自分のGrantを照会 |
| **訂正権** | PATCH /grants でGrantを更新（scope変更） |
| **削除権（忘れられる権利）** | DELETE /grants でGrantを削除 |
| **データポータビリティ権** | GET /grants でデータをエクスポート |
| **透明性** | Grantの詳細情報（scope, claims等）を提供 |

#### その他の規制

| 規制 | 地域 | Grant Management の役割 |
|------|------|----------------------|
| **CCPA** | カリフォルニア州 | ユーザーのデータアクセス権・削除権を保証 |
| **LGPD** | ブラジル | GDPRと同様の権利を実現 |
| **POPIA** | 南アフリカ | データ主体の権利を実現 |

---

## 第2部: 詳細編

### Grant ID

Grant はユニークな識別子（Grant ID）で識別されます。

```
トークンレスポンスに Grant ID が含まれる:

{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "grant_id": "PTRWWMo_YsGxl17r6MBj5"
}
```

Grant ID の要件:
- **ユニークであること**: 全システムで一意
- **推測不可能であること**: 128ビット以上のエントロピー
- **URL Safe であること**: Base64URL エンコード
- **長期間有効**: Grantの有効期限まで（通常6ヶ月〜1年）
- **不変であること**: 一度発行されたら変更されない

Grant ID の生成例:


### Grant の照会（GET）

#### 単一 Grant の取得

リクエスト:

```http
GET /grants/PTRWWMo_YsGxl17r6MBj5 HTTP/1.1
Host: auth.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

レスポンス:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
Pragma: no-cache

{
  "grant_id": "PTRWWMo_YsGxl17r6MBj5",
  "status": "active",
  "client_id": "s6BhdRkqt3",
  "client_name": "Banking App",
  "client_uri": "https://bank.example.com",
  "client_logo_uri": "https://bank.example.com/logo.png",
  "created_at": "2024-01-01T00:00:00Z",
  "updated_at": "2024-01-15T00:00:00Z",
  "expires_at": "2024-07-01T00:00:00Z",
  "last_used_at": "2024-01-20T10:30:00Z",
  "scope": "openid profile email accounts:read",
  "claims": {
    "userinfo": {
      "name": null,
      "email": {
        "essential": true
      }
    }
  },
  "authorization_details": [
    {
      "type": "account_information",
      "actions": ["read"],
      "accounts": [
        {
          "iban": "DE89370400440532013000"
        }
      ]
    }
  ]
}
```

フィールドの詳細：

| フィールド | 必須 | 説明 | 型 |
|-----------|------|------|----|
| `grant_id` | ✅ | Grant の一意識別子 | String |
| `status` | ✅ | Grant の状態 | `active`, `revoked`, `expired` |
| `client_id` | ✅ | クライアント ID | String |
| `client_name` | △ | クライアント名（表示用） | String |
| `client_uri` | △ | クライアントのURL | String (URI) |
| `client_logo_uri` | △ | クライアントのロゴ | String (URI) |
| `created_at` | ✅ | Grant 作成日時 | String (ISO 8601) |
| `updated_at` | ✅ | Grant 更新日時 | String (ISO 8601) |
| `expires_at` | △ | Grant 有効期限 | String (ISO 8601) |
| `last_used_at` | △ | 最終利用日時 | String (ISO 8601) |
| `scope` | ✅ | 付与されたスコープ | String (space-separated) |
| `claims` | △ | 付与されたクレーム | Object |
| `authorization_details` | △ | RAR（Rich Authorization Requests） | Array |

#### Grant 一覧の取得

リクエスト:

```http
GET /grants?status=active&limit=10&offset=0 HTTP/1.1
Host: auth.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

レスポンス:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
Pragma: no-cache

{
  "grants": [
    {
      "grant_id": "PTRWWMo_YsGxl17r6MBj5",
      "client_id": "s6BhdRkqt3",
      "client_name": "Banking App",
      "client_uri": "https://bank.example.com",
      "client_logo_uri": "https://bank.example.com/logo.png",
      "status": "active",
      "scope": "openid profile email accounts:read",
      "created_at": "2024-01-01T00:00:00Z",
      "last_used_at": "2024-01-20T10:30:00Z"
    },
    {
      "grant_id": "abc123def456",
      "client_id": "another_client",
      "client_name": "Payment App",
      "client_uri": "https://payment.example.com",
      "client_logo_uri": "https://payment.example.com/logo.png",
      "status": "active",
      "scope": "openid payment",
      "created_at": "2024-01-10T00:00:00Z",
      "last_used_at": "2024-01-19T15:45:00Z"
    }
  ],
  "total": 2,
  "offset": 0,
  "limit": 10
}
```

クエリパラメータ:

| パラメータ | 説明 | デフォルト | 例 |
|-----------|------|-----------|-----|
| `client_id` | 特定クライアントのGrantのみ | なし | `s6BhdRkqt3` |
| `status` | active, revoked, expired | `active` | `active` |
| `offset` | ページネーション（開始位置） | `0` | `0` |
| `limit` | 取得件数 | `10` | `10` |
| `sort` | ソート順 | `created_at:desc` | `last_used_at:desc` |

ソートオプション:
- `created_at:asc` / `created_at:desc` - 作成日時順
- `updated_at:asc` / `updated_at:desc` - 更新日時順
- `last_used_at:asc` / `last_used_at:desc` - 最終利用日時順
- `expires_at:asc` / `expires_at:desc` - 有効期限順

### Grant の更新（認可の拡張）

既存の Grant に新しいスコープを追加する場合、PAR で `grant_id` と `grant_management_action` を指定します。

リクエスト:

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_id=PTRWWMo_YsGxl17r6MBj5
&grant_management_action=merge
&scope=openid profile email accounts:read payments:write
&client_id=s6BhdRkqt3
&redirect_uri=https://client.example.com/callback
&state=af0ifjsldkj
&nonce=n-0S6_WzA2Mj
&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
&code_challenge_method=S256
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJQUzI1NiIs...
```

Grant Management アクション:

| アクション | 説明 | 用途 | 既存Grantの扱い |
|-----------|------|------|---------------|
| `create` | 新しい Grant を作成（デフォルト） | 初回認可 | 無視（新規作成） |
| `replace` | 既存の Grant を置換 | 権限の完全な変更 | 完全に上書き |
| `merge` | 既存の Grant に追加 | 段階的な認可拡張 | スコープを追加 |

#### create（新規作成）

```
動作:
  - 既存のGrantは無視
  - 常に新しいGrant IDを生成
  - 新しいGrantを作成

使用例:
  - 初回認可
  - 完全に別の認可が必要な場合
```

#### replace（置換）

```
動作:
  - 既存のGrantを完全に上書き
  - Grant IDは変わらない
  - scopeを完全に置き換え

使用例:
  - 権限を減らす場合
  - 権限を完全に変更する場合

例:
  既存: scope=openid profile accounts:read payments:write
  新規: scope=openid profile accounts:read
  結果: scope=openid profile accounts:read
  （payments:writeが削除される）
```

#### merge（マージ）

```
動作:
  - 既存のGrantにスコープを追加
  - Grant IDは変わらない
  - scopeは和集合

使用例:
  - 段階的な権限追加
  - 機能追加時の権限拡張

例:
  既存: scope=openid profile accounts:read
  新規: scope=openid profile accounts:read payments:write
  結果: scope=openid profile accounts:read payments:write
  （payments:writeが追加される）
```

### Grant の削除（DELETE）

リクエスト:

```http
DELETE /grants/PTRWWMo_YsGxl17r6MBj5 HTTP/1.1
Host: auth.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

レスポンス:

```http
HTTP/1.1 204 No Content
```

**削除の効果（カスケード削除）**:

```
Grant削除時の影響範囲:

1. Grantの状態変更
   status: active → revoked

2. アクセストークンの無効化
   - 発行済みのすべてのアクセストークンが即座に無効化
   - APIアクセス時に401エラー

3. リフレッシュトークンの無効化
   - 発行済みのすべてのリフレッシュトークンが無効化
   - トークン更新時に400エラー（invalid_grant）

4. 認可コードの無効化（未使用の場合）
   - 未使用の認可コードが無効化
   - トークン取得時に400エラー

5. セッションの無効化（オプション）
   - SSOセッションも無効化する実装もある
```

**注意事項**:

```
削除の不可逆性:
  - 削除は即座に反映される
  - 元に戻すことはできない
  - 再度認可が必要な場合は、新規の認可フローを実行

監査ログ:
  - 削除操作は監査ログに記録すべき
  - 誰が、いつ、どのGrantを削除したか

通知:
  - ユーザーへの通知（メール等）を検討
  - RPへの通知（webhook等）を検討
```

### Grant のライフサイクル

```
Grantのライフサイクル:

  ┌──────────┐
  │  作成    │ ← 認可フロー完了時
  └────┬─────┘
       │
       ▼
  ┌──────────┐
  │  active  │ ← 通常の状態
  └────┬─────┘
       │
       ├──────────────┐
       │              │
       ▼              ▼
  ┌──────────┐  ┌──────────┐
  │ expired  │  │ revoked  │
  └──────────┘  └──────────┘
       │              │
       └──────┬───────┘
              ▼
        （完全削除）
```

状態の詳細:

| 状態 | 説明 | トークン発行 | API アクセス | 削除可能 |
|------|------|------------|-------------|---------|
| `active` | 有効 | ✅ | ✅ | ✅ |
| `expired` | 期限切れ | ❌ | ❌ | ✅ |
| `revoked` | 取り消し済み | ❌ | ❌ | ✅（論理削除済み） |

### ディスカバリーメタデータ


メタデータフィールド:

| フィールド | 説明 |
|-----------|------|
| `grant_management_endpoint` | Grant Management API のベース URL |
| `grant_management_actions_supported` | サポートされるアクション（create, replace, merge） |
| `grant_management_grant_types_supported` | Grant Management対象のgrant_type |

---

## 第3部: セキュリティ

### セキュリティ考慮事項

#### 1. アクセス制御

**必須の検証:**

```
Grant操作時の検証項目:

1. 認証検証
   - アクセストークンの署名検証
   - 有効期限の確認
   - スコープの確認（grant_management等）

2. 認可検証
   - tenant_id一致確認（マルチテナント分離）
   - subject一致確認（ユーザー権限）
   - grant_id所有権の確認

3. Grant操作の制限
   - 読み取り: 自分のGrantのみ
   - 更新/削除: 自分のGrantのみ
   - 他ユーザーのGrantは操作不可
```

**実装例:**
```python
def validate_grant_access(grant_id, access_token):
    grant = get_grant(grant_id)
    token = decode_token(access_token)

    # tenant_id検証
    if grant.tenant_id != token.tenant_id:
        raise ForbiddenError("Tenant mismatch")

    # subject検証
    if grant.subject != token.sub:
        raise ForbiddenError("Not authorized")

    return grant
```

#### 2. レート制限

**推奨設定:**

| 操作 | レート制限 | 期間 | 理由 |
|------|----------|------|------|
| GET /grants | 100リクエスト | 1分 | DoS防止 |
| GET /grants/{id} | 300リクエスト | 1分 | 詳細照会の頻度が高い |
| DELETE /grants/{id} | 10リクエスト | 1分 | 誤操作防止、悪用防止 |

**実装のポイント:**
- IPアドレスとユーザーIDの両方で制限
- エラー時は429 Too Many Requests
- Retry-Afterヘッダーで次回試行可能時刻を通知

#### 3. 監査ログとモニタリング

**記録すべき項目:**

```
監査ログフォーマット:

{
  "timestamp": "2024-01-20T10:30:00Z",
  "event_type": "grant.deleted",
  "grant_id": "PTRWWMo_YsGxl17r6MBj5",
  "actor": {
    "subject": "user-123",
    "tenant_id": "tenant-456",
    "ip_address": "192.168.1.100"
  },
  "grant_details": {
    "client_id": "s6BhdRkqt3",
    "scope": "openid accounts:read",
    "created_at": "2024-01-01T00:00:00Z"
  },
  "result": "success"
}
```

**モニタリング指標:**
- Grant作成・削除の頻度
- エラー率
- レスポンス時間
- アクセス制御違反の試行

#### 4. GDPR対応

**GDPR要件の実装:**

1. **アクセス権（Right to Access）**
   - GET /grants でユーザーのすべてのGrantを取得
   - JSON形式でエクスポート

2. **削除権（Right to Erasure）**
   - DELETE /grants でGrant削除
   - カスケード削除（トークン、セッション）
   - 監査ログは保持（法的要件）

3. **データポータビリティ権**
   - GET /grants でデータ取得
   - JSON形式で提供

4. **透明性**
   - Grantの詳細情報を提供
   - scope, claims, authorization_details

### 脅威モデルと攻撃シナリオ

#### 1. Grant ID推測攻撃

**攻撃シナリオ:**
```
1. 攻撃者がGrant IDのパターンを推測
2. 総当たりでGrant情報を取得試行
3. 他ユーザーのGrant情報が漏洩
```

**対策:**
- Grant IDに十分なエントロピー（128ビット以上）
- UUID v4等の推測不可能な値を使用
- レート制限
- アクセス制御の厳格化

#### 2. Grant情報の漏洩

**攻撃シナリオ:**
```
1. ネットワーク盗聴でGrant情報を取得
2. ユーザーの認可状況を把握
3. プライバシー侵害
```

**対策:**
- TLS必須
- レスポンスのキャッシュ制御（no-store）
- 機密情報の最小化

#### 3. 不正なGrant削除

**攻撃シナリオ:**
```
1. 攻撃者が盗んだアクセストークンでGrant削除
2. ユーザーのサービス利用が不可に
3. サービス妨害
```

**対策:**
- 強固な認証（アクセストークン）
- 削除前の確認（オプション）
- 監査ログで追跡可能に
- 通知機能（メール等）

### よくあるGrant Management要件違反とエラー

#### 違反1: アクセス制御の未実装

**エラー:** `forbidden` (403)
**セキュリティリスク:** 権限昇格、他ユーザーのGrant情報漏洩、プライバシー侵害
**Grant Management要件:** Grant操作時、リクエストのsubjectとGrantのsubjectが一致すること、tenant_idが一致することを検証しなければならない
**対策:** アクセストークンから抽出したsubjectとtenant_idをGrantレコードの値と照合し、不一致の場合は403エラーを返す

#### 違反2: Grant ID の推測可能性

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** Grant ID推測攻撃、総当たり攻撃による情報漏洩
**Grant Management要件:** Grant IDは推測不可能な値（128ビット以上のエントロピー）でなければならない
**対策:** `secrets.token_urlsafe()`やUUID v4等の暗号学的に安全な乱数生成器を使用し、予測可能なパターン（user_id + timestamp等）を避ける

#### 違反3: カスケード削除の未実装

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** Grant削除後もトークンが有効で不正利用可能、認可の不完全な取り消し
**Grant Management要件:** Grant削除時、関連するすべてのトークン（アクセストークン、リフレッシュトークン）とセッションを無効化しなければならない
**対策:** Grant削除時にトランザクションで関連トークンとセッションを同時に削除し、完全な無効化を保証する

### セキュリティベストプラクティス

#### 1. Grant IDの生成

**推奨事項:**
- 128ビット以上のエントロピー
- secrets.token_urlsafe()等の暗号学的に安全な乱数生成器
- UUID v4も可

#### 2. マルチテナント分離

**推奨事項:**
- すべてのクエリでtenant_idをフィルタ
- インデックスに tenant_id を含める
- Row Level Security（PostgreSQL）の活用

#### 3. 監査ログの保持

**推奨事項:**
- すべてのGrant操作をログに記録
- ログは改ざん不可能な形式で保存
- 法的要件に応じた保持期間（例: 7年）

#### 4. GDPR対応

**推奨事項:**
- データエクスポート機能の実装
- 削除の完全性保証（カスケード削除）
- 透明性の確保（Grant詳細情報）


### セキュリティ理解度チェック

この章を学習した後、以下を理解できているか確認してください：

#### 認可サーバー（AS）

□ Grant IDに128ビット以上のエントロピーが必要な理由と、推測攻撃のリスクを説明できる

<details>
<summary>解答例</summary>

**推測攻撃のリスク（エントロピー不足）**:

攻撃シナリオ:
1. 攻撃者がGrant IDのパターンを推測
   - 例: user_id + timestamp（予測可能）
2. 総当たりでGrant情報を取得試行
3. 他ユーザーのGrant情報が漏洩
4. プライバシー侵害、認可の不正削除

**対策（128ビット以上のエントロピー）**:

1. **暗号学的に安全な乱数生成器**
   - Python: `secrets.token_urlsafe(16)`
   - Java: `SecureRandom`
   - UUID v4も可（122ビットのランダム性）

2. **予測可能なパターンを避ける**
   - ❌ user_id + timestamp
   - ❌ 連番
   - ✅ 完全にランダムな値

3. **推測不可能性**
   - 128ビット = 2^128 = 約3.4×10^38 通り
   - 総当たり攻撃が実質不可能

**実装例**:
```python
import secrets
grant_id = secrets.token_urlsafe(16)  # 128ビット
```
</details>

□ アクセス制御（tenant_id、subject検証）が必須な理由と、検証漏れのリスクを説明できる

<details>
<summary>解答例</summary>

**検証漏れのリスク**:

攻撃シナリオ:
1. 攻撃者が正規ユーザーのGrant IDを入手
2. 自分のアクセストークンでGET /grants/{grant_id}
3. 検証なしの場合、他ユーザーのGrant情報を取得
4. プライバシー侵害、権限昇格

**必須の検証項目**:

1. **tenant_id検証（マルチテナント分離）**
   ```python
   if grant.tenant_id != token.tenant_id:
       raise ForbiddenError("Tenant mismatch")
   ```
   - 理由: 異なるテナントのデータにアクセスさせない
   - リスク: テナント間のデータ漏洩

2. **subject検証（ユーザー権限）**
   ```python
   if grant.subject != token.sub:
       raise ForbiddenError("Not authorized")
   ```
   - 理由: 自分のGrantのみアクセス可能
   - リスク: 他ユーザーのGrant操作

**実装のポイント**:
- すべてのGrant操作で必須
- データベースクエリにもフィルタを追加
- Row Level Security（PostgreSQL）の活用
</details>

□ Grant削除時のカスケード削除が必要な理由と、削除すべきリソースを説明できる

<details>
<summary>解答例</summary>

**カスケード削除の目的**:
Grant削除時、関連するすべてのリソースを無効化し、認可の完全な取り消しを保証する

**削除すべきリソース**:

1. **Grantの状態変更**
   - status: active → revoked

2. **アクセストークンの無効化**
   - 発行済みのすべてのアクセストークンを無効化
   - APIアクセス時に401エラー

3. **リフレッシュトークンの無効化**
   - 発行済みのすべてのリフレッシュトークンを無効化
   - トークン更新時に400エラー（invalid_grant）

4. **認可コードの無効化（未使用の場合）**
   - 未使用の認可コードを無効化

5. **セッションの無効化（オプション）**
   - SSOセッションも無効化する実装もある

**カスケード削除なしのリスク**:

攻撃シナリオ:
1. ユーザーがGrant削除
2. カスケード削除が未実装
3. アクセストークンが有効なまま
4. 攻撃者が盗んだトークンで引き続きAPIアクセス可能
5. 認可の取り消しが不完全

**実装のポイント**:
- トランザクションで一括削除
- 削除の完全性を保証
- 監査ログに記録
- ユーザーへの通知（メール等）
</details>

□ Grant Management APIのレート制限が必要な理由と、推奨設定を説明できる

<details>
<summary>解答例</summary>

**レート制限が必要な理由**:

1. **DoS攻撃防止**
   - 大量のGET /grants リクエストでサーバー負荷増大
   - サービスの可用性低下

2. **Grant ID推測攻撃の防止**
   - 総当たりでGrant IDを推測する攻撃を制限
   - 短時間での大量試行を防止

3. **誤操作防止**
   - 短時間での大量削除を防止
   - ユーザーの誤操作を制限

**推奨設定**:

| 操作 | レート制限 | 期間 | 理由 |
|------|----------|------|------|
| GET /grants | 100リクエスト | 1分 | DoS防止 |
| GET /grants/{id} | 300リクエスト | 1分 | 詳細照会の頻度が高い |
| DELETE /grants/{id} | 10リクエスト | 1分 | 誤操作防止、悪用防止 |

**実装のポイント**:
- IPアドレスとユーザーIDの両方で制限
- エラー時は429 Too Many Requests
- Retry-Afterヘッダーで次回試行可能時刻を通知

**実装例（Redis）**:
```python
key = f"rate_limit:{user_id}:grants:get"
count = redis.incr(key)
redis.expire(key, 60)  # 1分
if count > 100:
    raise TooManyRequestsError("Rate limit exceeded")
```
</details>

□ GDPRとGrant Managementの関係、および対応する権利を説明できる

<details>
<summary>解答例</summary>

**GDPR（一般データ保護規則）との対応**:

| GDPR要件 | Grant Management による実現 | APIエンドポイント |
|---------|---------------------------|-----------------|
| **アクセス権** | ユーザーが自分のGrantを照会 | GET /grants |
| **訂正権** | Grantを更新（scope変更） | PATCH /grants（grant_management_action=merge/replace） |
| **削除権（忘れられる権利）** | Grantを削除 | DELETE /grants/{id} |
| **データポータビリティ権** | データをエクスポート | GET /grants（JSON形式） |
| **透明性** | Grantの詳細情報を提供 | GET /grants/{id} |

**具体的な実装**:

1. **アクセス権（Right to Access）**
   ```http
   GET /grants?status=active&limit=100
   ```
   - ユーザーのすべてのGrantを取得
   - client_name、scope、作成日時等を含む

2. **削除権（Right to Erasure）**
   ```http
   DELETE /grants/{grant_id}
   ```
   - カスケード削除（トークン、セッション）
   - 監査ログは保持（法的要件）

3. **データポータビリティ権**
   ```json
   {
     "user_id": "user-123",
     "export_date": "2024-01-20T10:00:00Z",
     "grants": [...]
   }
   ```
   - JSON形式でデータ提供
   - 機械可読形式

**GDPR対応のメリット**:
- ユーザーのプライバシー保護
- 法的コンプライアンス
- ユーザーの信頼向上
</details>

□ 段階的な認可（grant_management_action=merge）の仕組みと、ユーザー体験上のメリットを説明できる

<details>
<summary>解答例</summary>

**段階的な認可の仕組み**:

シナリオ: 家計簿アプリの利用

1. **初回認可（残高照会のみ）**
   ```
   scope=openid accounts:read
   → Grant A 作成
   ```

2. **決済機能追加時（後日）**
   ```
   grant_id=A
   grant_management_action=merge
   scope=openid accounts:read payments:write
   ```

3. **AS: 追加の同意のみ求める**
   - 既存のscope（accounts:read）は再同意不要
   - 新しいscope（payments:write）のみ同意

4. **Grant A 更新**
   ```
   scope=openid accounts:read payments:write
   ```

**grant_management_actionの種類**:

| アクション | 動作 | 使用例 |
|-----------|------|--------|
| `create` | 新しいGrant作成 | 初回認可 |
| `replace` | 既存Grantを完全に置換 | 権限の完全な変更 |
| `merge` | 既存Grantにスコープを追加 | 段階的な権限拡張 |

**ユーザー体験上のメリット**:

1. **最小限の同意**
   - 必要な時に必要な権限のみ
   - 初回に大量の権限を要求しない

2. **セキュリティの向上**
   - 最小権限の原則
   - 不要な権限を付与しない

3. **管理が容易**
   - Grantの一覧・取り消しが簡単
   - どのアプリにどの権限を付与したか明確

**実装のポイント**:
- 既存のGrant IDを指定
- ASが差分のみ同意を求める
- Grant IDは変わらない
</details>

---

## 参考リンク

- [OAuth 2.0 Grant Management](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-grant-management)
- [FAPI 2.0 Security Profile](https://openid.bitbucket.io/fapi/fapi-2_0-security-profile.html)
- [GDPR - 一般データ保護規則](https://gdpr-info.eu/)

---

## まとめ

Grant Management は、ユーザーに認可の制御権を与え、プライバシーとセキュリティを向上させる重要な仕様です。

### 主要な利点

1. **ユーザーの自律性**
   - 認可の照会・取り消しが可能
   - GDPR等の規制対応

2. **段階的な認可**
   - 必要な時に必要な権限のみ
   - ユーザー体験の向上

3. **運用の改善**
   - RPが認可状況を確認可能
   - トラブルシューティングが容易

4. **セキュリティ**
   - 不要な認可を削除可能
   - 監査ログで追跡可能

### 実装時の重要ポイント

- Grant IDに十分なエントロピー
- アクセス制御の徹底（tenant_id, subject検証）
- ページネーションとフィルタリング
- 監査ログとモニタリング
- マルチテナント対応
- GDPR対応（データエクスポート、削除）

Grant Management を適切に実装することで、ユーザーのプライバシーを保護しつつ、柔軟な認可管理を実現できます。
