# Issue: Application Plane実装とドキュメントの乖離修正

## 🎯 概要

Application Plane（OAuth/OIDC認証フロー）のドキュメントに、実装と乖離している記述が複数見つかりました。開発者が実際の動作を理解する上で重要な部分のため、実装に合わせて修正しました。

## 📋 発見された問題点

### 1. Authorization Code の `used` フラグに関する誤解

**ドキュメント記載（修正前）**:
```markdown
# 2. used=true の場合 → 最初からやり直し
```

**実装の実態**:
- `used` フラグは存在しない
- **使用後即削除する設計**を採用（`AuthorizationCodeGrantService.java:199`）
- 再使用試行時は「レコード不存在」でエラー

**影響**:
- デバッグ時に `SELECT code, used, expires_at` を実行してもエラー（`used`列が存在しない）
- エラーメッセージ `"not found authorization code."` の真の意味を理解できない

**修正内容**:
- エラーメッセージを実装準拠に変更
- `used`フラグではなく「削除による実装」を明記
- デバッグSQLから`used`列を削除
- 設計意図（セキュリティ理由で状態を区別しない）を追加

---

### 2. 主要フローのエンドポイント不正確

**ドキュメント記載（修正前）**:
```markdown
GET /oauth/authorize?response_type=code&client_id=xxx
POST /oauth/token
POST /bc-authorize
```

**実装の実態**:
```java
GET /{tenant-id}/v1/authorizations?response_type=code&...
POST /{tenant-id}/v1/tokens
POST /{tenant-id}/v1/backchannel/authentications
```

**影響**:
- curlコマンドをコピペしても動作しない
- テナントIDの必要性が理解できない
- 実際のAPIパスと対応付けられない

**修正内容**:
- 全エンドポイントを実装準拠に修正
- `{tenant-id}` プレースホルダーを明記
- 実装ファイルへのリンクを追加

---

### 3. フロー詳細の不足

**ドキュメント記載（修正前）**:
```markdown
3. [idp-server] ログイン画面表示
   - パスワード入力
   - SMS OTP
   - FIDO2認証
```

**実装の実態**:
- 各認証方式には専用エンドポイントが存在
- `POST /{tenant-id}/v1/authentications/{auth-request-id}` で統一
- 実際のレスポンス構造（JSON）が重要

**影響**:
- 認証APIの呼び出し方が不明確
- レスポンス検証時に何を期待すべきか分からない
- トークン発行の内部処理（検証・生成・削除）が見えない

**修正内容**:
- 各ステップに実際のエンドポイントを追加
- レスポンスJSONの例を追加
- トークン発行の内部処理フロー（7ステップ）を明記
- 詳細ドキュメントへのリンクを追加

---

## 📁 修正ファイル

### 1. `03-token-flow.md`（+24/-10行）
- エラー2の説明を実装準拠に修正
- エラーメッセージ: `"authorization code has already been used"` → `"not found authorization code."`
- 実装詳細セクション追加（削除による設計）
- デバッグSQL修正（`used`列削除）

### 2. `01-overview.md`（+161/-33行）

#### Authorization Code Flow
- 実装リンク追加（OAuthV1Api.java + TokenV1Api.java）
- PAR（Pushed Authorization Request）手順追加
- 認証エンドポイント明記
- トークン発行の詳細処理フロー（検証→生成→削除）
- 実際のレスポンスJSON例
- UserInfo取得ステップ追加
- 詳細ドキュメントへのリンク

#### Client Credentials Flow
- 実装リンク追加（TokenV1Api.java）
- 用途説明（マイクロサービス間通信等）
- ユーザーコンテキストなしの理由
- 処理フロー詳細
- 実際のレスポンスJSON例

#### CIBA Flow
- 実装リンク追加（CibaV1Api.java + TokenV1Api.java）
- 用途説明（バックチャネル認証）
- auth_req_id有効期限（5分）
- プッシュ通知詳細（FCM/APNS/SMS）
- Poll Mode vs Ping Mode
- ポーリング中のレスポンス例

---

## ✅ 改善効果

### 開発者体験向上
- ✅ 実装とドキュメントが完全一致
- ✅ curlコマンドをそのまま使える
- ✅ エラーメッセージで検索して到達できる
- ✅ デバッグ時に正しいSQLを実行できる

### 学習効率向上
- ✅ 各ステップの詳細ドキュメントへ誘導
- ✅ ソースコードへ直接ジャンプ可能
- ✅ 内部処理の理解を深められる
- ✅ 設計意図が明確

---

## 🔍 検証方法

### 1. エンドポイント確認
```bash
# ドキュメント記載のエンドポイントが実際に存在するか
grep -r "/{tenant-id}/v1/authorizations" libs/idp-server-springboot-adapter/
grep -r "/{tenant-id}/v1/tokens" libs/idp-server-springboot-adapter/
grep -r "/{tenant-id}/v1/backchannel/authentications" libs/idp-server-springboot-adapter/
```

### 2. エラーメッセージ確認
```bash
# ドキュメント記載のエラーメッセージが実装に存在するか
grep -r "not found authorization code" libs/idp-server-core/
```

### 3. Authorization Code削除確認
```bash
# Authorization Code削除処理の存在確認
grep -r "authorizationCodeGrantRepository.delete" libs/idp-server-core/
```

---

## 📚 関連Issue

- #676 - AI開発者向け知識ベース作成（完了）
- #680 - Developer Guide整備（進行中）
- #426 - 想像ドキュメント作成防止の教訓（deployment.md問題）

---

## 🚨 今後の改善提案

### 1. ドキュメント自動検証
実装とドキュメントの乖離を自動検出するスクリプトの作成：
```bash
#!/bin/bash
# doc-validation.sh

# エンドポイント検証
DOC_ENDPOINTS=$(grep -o "POST /{tenant-id}/v1/[a-z/-]*" documentation/**.md)
ACTUAL_ENDPOINTS=$(grep -r "@PostMapping" libs/idp-server-springboot-adapter/)

# エラーメッセージ検証
DOC_ERRORS=$(grep -o '"error_description": "[^"]*"' documentation/**.md)
ACTUAL_ERRORS=$(grep -r 'TokenBadRequestException.*".*"' libs/)
```

### 2. 実装ファーストの原則
ドキュメント作成時の必須手順（CLAUDE.md記載済み）:
1. **コードファーストの原則**: 必ずソースコードを先に確認
2. **情報源記録**: 参照ファイル・確認方法を明記
3. **段階的確認**: エンドポイント→レスポンス→エラーの順で確認
4. **不明点明示**: 推測・仮定を明確に区別

---

## 📝 チェックリスト

- [x] エラーメッセージを実装準拠に修正
- [x] エンドポイントを実装準拠に修正
- [x] レスポンスJSON例を追加
- [x] 実装ファイルへのリンクを追加
- [x] 設計意図の説明を追加
- [x] デバッグSQLを修正
- [x] 詳細ドキュメントへのリンクを追加
- [ ] spotlessApply実行
- [ ] git commit
- [ ] PR作成

---

**確認日**: 2025-10-13
**修正者**: Claude Code
**レビュー**: 必要
