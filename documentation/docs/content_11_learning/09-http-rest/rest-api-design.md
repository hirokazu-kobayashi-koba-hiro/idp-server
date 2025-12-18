# RESTful API設計の基礎

## このドキュメントの目的

**REST（Representational State Transfer）** の基本原則を理解し、API設計の考え方を把握することが目標です。

---

## RESTとは

**REST**:
- Webの設計原則に基づいたアーキテクチャスタイル
- リソース指向
- HTTPメソッドを活用
- ステートレス

**考案者**: Roy Fielding（HTTPの主要設計者）、2000年の博士論文

---

## RESTの基本原則

### 1. リソース指向

**リソース**: URLで識別可能な情報

```
リソースの例:
- ユーザー: /users/123
- 記事: /articles/456
- コメント: /articles/456/comments/789
```

**❌ 動詞ベース**（非REST）:
```
/getUser?id=123
/createArticle
/deleteComment?id=789
```

**✅ 名詞ベース**（REST）:
```
GET /users/123
POST /articles
DELETE /comments/789
```

---

### 2. HTTPメソッドで操作を表現

**CRUD操作とHTTPメソッドの対応**:

| 操作 | HTTPメソッド | URL例 |
|------|------------|-------|
| **Create（作成）** | POST | `POST /users` |
| **Read（取得）** | GET | `GET /users/123` |
| **Update（更新）** | PUT/PATCH | `PUT /users/123` |
| **Delete（削除）** | DELETE | `DELETE /users/123` |

---

### 3. ステートレス

**ステートレス**: サーバーがクライアントの状態を保持しない

```
✅ ステートレス:
  - 各リクエストが完結
  - 必要な情報は全てリクエストに含む
  - サーバーはセッションを保存しない

❌ ステートフル:
  - サーバーがセッション情報を保存
  - 前のリクエストの状態に依存
```

**メリット**:
- スケールアウトが容易
- サーバーを追加するだけで負荷分散

---

## RESTful API設計の例

### ユーザー管理API

```
GET    /users          - ユーザー一覧取得
GET    /users/123      - 特定ユーザー取得
POST   /users          - ユーザー作成
PUT    /users/123      - ユーザー更新（完全置換）
PATCH  /users/123      - ユーザー部分更新
DELETE /users/123      - ユーザー削除
```

### 階層的なリソース

```
GET    /users/123/articles           - ユーザー123の記事一覧
POST   /users/123/articles           - ユーザー123の記事作成
GET    /articles/456/comments        - 記事456のコメント一覧
POST   /articles/456/comments        - 記事456にコメント作成
```

---

## べき等性（Idempotency）

### べき等とは

**べき等**: 同じ操作を複数回実行しても、結果が同じ

```
例: DELETE /users/123

1回目: ユーザー123を削除 → 成功（204 No Content）
2回目: ユーザー123を削除 → 既に存在しない（404 Not Found）
3回目: ユーザー123を削除 → 既に存在しない（404 Not Found）

結果は同じ（ユーザー123は存在しない状態）
```

### HTTPメソッドのべき等性

| メソッド | べき等 | 説明 |
|---------|--------|------|
| **GET** | ✅ Yes | 何度実行しても同じデータ |
| **PUT** | ✅ Yes | 何度実行しても同じ状態 |
| **DELETE** | ✅ Yes | 何度実行しても削除済み状態 |
| **POST** | ❌ No | 実行するたびに新しいリソース作成 |
| **PATCH** | ⚠️ 実装による | 設計次第 |

### なぜべき等性が重要か

**理由**: ネットワークエラー時の再試行

```
シナリオ:
1. クライアント → DELETE /users/123
2. サーバー: 削除処理実行、レスポンス送信
3. ネットワークエラー: レスポンスがクライアントに届かない
4. クライアント: タイムアウト、削除されたか不明
5. クライアント: 再試行（DELETE /users/123）
6. サーバー: 404 Not Found（既に削除済み）

べき等なので安全に再試行可能
```

**非べき等の場合（POST）**:
- 再試行すると重複作成のリスク
- 対策: Idempotency-Key ヘッダー使用

---

## OAuth 2.0/OIDCはRESTful？

### RESTfulな部分

**UserInfo Endpoint**:
```
GET /userinfo HTTP/1.1
Authorization: Bearer {access_token}
```

- リソース指向: ユーザー情報
- GETで取得
- ステートレス

---

### RESTfulでない部分

**Token Endpoint**:
```
POST /token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=...
```

**非RESTfulな理由**:
- リソース指向ではない（トークン"取得"なのにPOST）
- RPC（Remote Procedure Call）スタイル

**なぜ非RESTful？**:
- OAuth 2.0はRFC 6749で設計（REST必須ではない）
- セキュリティ重視（POST = ボディで機密情報送信）
- GET = URLにパラメータ → ログに残るリスク

**結論**: OAuth 2.0は完全なRESTではなく、**RESTfulなAPI + RPC的な部分の混在**

---

## まとめ

### 学んだこと

- ✅ RESTはリソース指向のアーキテクチャスタイル
- ✅ HTTPメソッドで操作を表現（GET/POST/PUT/DELETE）
- ✅ ステートレス（サーバーが状態を保持しない）
- ✅ べき等性の重要性（安全な再試行）
- ✅ OAuth 2.0は完全なRESTではない

### RESTful API設計の基本

1. **リソースを名詞で表現**（動詞を使わない）
2. **HTTPメソッドを適切に使う**（CRUD操作）
3. **ステートレスにする**（各リクエストが完結）
4. **べき等性を考慮**（安全な再試行）

### 次に読むべきドキュメント

1. [OAuth 2.0の基礎](../02-oauth-fundamentals/oauth-oidc-why-needed.md) - OAuth 2.0を学ぶ

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
