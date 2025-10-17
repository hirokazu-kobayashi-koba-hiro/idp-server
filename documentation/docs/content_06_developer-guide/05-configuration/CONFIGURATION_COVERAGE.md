# 設定ドキュメント網羅性チェック

## 設定ファイルの網羅状況

### ✅ ドキュメント作成済み

| カテゴリ | ファイル数 | ドキュメント | ステータス |
|---------|----------|------------|-----------|
| **Tenant** | 1 | [tenant.md](./tenant.md) | ✅ 完了 |
| **Client** | 4 | [client.md](./client.md) | ✅ 完了 |
| **Authentication** | 4 | [authn/*.md](./authn/) | ✅ 既存8種類 |
| **Authentication Policy** | 5 | [authentication-policy.md](./authentication-policy.md) | ✅ 完了 |
| **Federation** | 2 | [federation.md](./federation.md) | ⚠️ 要改善 |
| **Identity Verification** | 3 | [identity-verification.md](./identity-verification.md) | ⚠️ 要改善 |
| **Security Event Hook** | 2 | [security-event-hook.md](./security-event-hook.md) | ⚠️ 要改善 |

---

## 実設定ファイル理解度チェック表

### Tenant設定（tenant.json）

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| テナント基本情報 | ✅ | ✅ | OK |
| Authorization Server設定 | ✅ | ✅ | OK |
| scopes_supported | ✅ | ✅ | カスタムスコープ説明あり |
| extension設定 | ✅ | ✅ | トークン有効期限説明あり |

**総合評価**: ✅ **十分に理解可能**

---

### Client設定（clients/*.json）

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報（client_id等） | ✅ | ✅ | OK |
| redirect_uris | ✅ | ✅ | OK |
| grant_types, response_types | ✅ | ✅ | OK |
| CIBA extension設定 | ✅ | ✅ | OK |
| client_id_alias | ❌ | ❌ | **説明不足** |

**問題点**:
- `client_id_alias`の説明がない（実ファイル: bank-app.json:3）

**総合評価**: ⚠️ **ほぼ理解可能（一部補足必要）**

---

### Authentication設定

#### authentication/fido-uaf/strongauth-fido.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報（id, type） | ✅ | ✅ | OK |
| interactions構造 | ✅ | ✅ | OK |
| http_request設定 | ✅ | ✅ | OK |
| header_mapping_rules | ✅ | ✅ | OK |
| body_mapping_rules | ✅ | ✅ | OK |
| response.body_mapping_rules | ✅ | ✅ | OK |

**総合評価**: ✅ **十分に理解可能**

---

#### authentication/authentication-device/push.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報 | ✅ | ✅ | authentication-device.md参照 |
| プッシュ通知設定 | ✅ | ✅ | OK |

**総合評価**: ✅ **十分に理解可能**

---

#### authentication/pin/strongauth-pin.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| PIN認証設定 | ❌ | ❌ | **PINガイド未作成** |

**問題点**: PIN認証の詳細ガイドが存在しない

**総合評価**: ❌ **理解困難（ガイド不足）**

---

#### authentication/external-token/open-api.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| External Token検証 | ❌ | ❌ | **ガイド未作成** |

**問題点**: External Token認証の説明が一切ない

**総合評価**: ❌ **理解困難（ガイド不足）**

---

### Authentication Policy設定

#### authentication-policy/oauth.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報 | ✅ | ✅ | OK |
| available_methods | ✅ | ✅ | OK |
| acr_mapping_rules | ✅ | ✅ | OK |
| success_conditions | ✅ | ✅ | OK |
| failure_conditions | ✅ | ✅ | OK |
| lock_conditions | ✅ | ✅ | OK |

**総合評価**: ✅ **十分に理解可能**

---

#### authentication-policy/fido-uaf-registration.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| flow="fido-uaf-registration" | ❌ | ❌ | **flowの種類説明不足** |

**問題点**:
- `flow`に指定可能な値の説明がない（oauth, ciba, fido-uaf-registration等）

**総合評価**: ⚠️ **部分的に理解可能（flow種類の補足必要）**

---

### Federation設定

#### federation/oidc/power-direct.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報（issuer等） | ✅ | ✅ | OK |
| userinfo_endpoint | ✅ | ✅ | OK |
| **userinfo_execution.http_requests** | ❌ | ❌ | **説明不足** |
| userinfo_mapping_rules | ⚠️ | ⚠️ | 基本は理解可能 |
| `$.userinfo_execution_http_requests[0]`参照 | ❌ | ❌ | **説明不足** |
| store_credentials | ✅ | ✅ | OK |

**問題点**:
1. `userinfo_execution.http_requests`配列での複数API連続実行の説明がない
2. `userinfo_mapping_rules`で`$.userinfo_execution_http_requests[0].response_body`を参照する方法が不明確

**総合評価**: ❌ **理解困難（複雑な機能の説明不足）**

---

### Identity Verification設定

#### identity-verification/auth-face.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報（id, type） | ✅ | ✅ | OK |
| processes構造 | ✅ | ✅ | OK |
| request.schema | ❌ | ❌ | **説明不足** |
| **pre_hook.additional_parameters** | ❌ | ❌ | **説明なし** |
| execution.http_request | ✅ | ✅ | OK |
| store.application_details_mapping_rules | ❌ | ❌ | **説明不足** |
| transition条件 | ✅ | ✅ | OK |
| `$.pre_hook_additional_parameters[0]`参照 | ❌ | ❌ | **説明なし** |

**問題点**:
1. `request.schema`（JSONSchema検証）の説明がない
2. `pre_hook.additional_parameters`で事前にAPIを呼び出す仕組みの説明がない
3. `$.pre_hook_additional_parameters[0].response_body`を後続で参照する方法が不明確
4. `store.application_details_mapping_rules`の用途が不明

**総合評価**: ❌ **理解困難（高度な機能の説明不足）**

---

### Security Event Hook設定

#### security-event-hook/ssf.json

| 設定項目 | ドキュメントで説明 | 実ファイルで理解可能 | 備考 |
|---------|----------------|------------------|------|
| 基本情報（id, type） | ✅ | ✅ | OK |
| triggers | ✅ | ✅ | OK |
| metadata | ⚠️ | ⚠️ | 基本は理解可能 |
| **events.\{event-type\}.execution** | ❌ | ❌ | **構造説明不足** |
| oauth_authorization.cache設定 | ❌ | ❌ | **説明なし** |
| security_event_token_additional_payload_mapping_rules | ❌ | ❌ | **説明なし** |

**問題点**:
1. `events`セクションの深いネスト構造が不明確
2. OAuth 2.0認証のキャッシュ設定（`cache_enabled`, `cache_ttl_seconds`）の説明がない
3. `security_event_token_additional_payload_mapping_rules`の用途が不明

**総合評価**: ❌ **理解困難（SSF特有機能の説明不足）**

---

## 追加すべきドキュメント

### 🔴 優先度: 高

| ドキュメント | 理由 | 対象ファイル |
|------------|------|-------------|
| **pin.md** | PIN認証の説明が全くない | `authentication/pin/strongauth-pin.json` |
| **external-token.md** | External Token認証の説明がない | `authentication/external-token/open-api.json` |

### 🟡 優先度: 中

| セクション | 追加すべき内容 | 対象ドキュメント |
|-----------|--------------|----------------|
| **userinfo_execution詳細** | 複数API連続実行、結果参照方法 | `federation.md` |
| **pre_hook詳細** | additional_parameters、結果参照方法 | `identity-verification.md` |
| **SSF events構造** | イベント別設定、ネスト構造 | `security-event-hook.md` |

### 🟢 優先度: 低

| 項目 | 追加すべき内容 |
|-----|--------------|
| **flow種類** | oauth, ciba, fido-uaf-registration等の完全リスト |
| **request.schema** | JSONSchema検証の説明 |
| **OAuth cache設定** | cache_enabled, cache_ttl_seconds詳細 |
| **client_id_alias** | エイリアスの用途説明 |

---

## 理解度チェックリスト（開発者向け）

### ✅ 現状で理解可能な設定

- [ ] tenant.json - テナント・Authorization Server設定
- [ ] clients/bank-app.json - 基本的なクライアント設定
- [ ] authentication/fido-uaf/strongauth-fido.json - FIDO-UAF認証設定
- [ ] authentication-policy/oauth.json - OAuth認証ポリシー
- [ ] security-event-hook/webhook設定（ドキュメント例）

### ⚠️ 部分的に理解可能（補足が必要）

- [ ] federation/oidc/power-direct.json
  - 基本設定は理解可能
  - `userinfo_execution.http_requests`の詳細が不明
  - 複数APIの結果を統合する方法が不明

- [ ] authentication-policy/fido-uaf-registration.json
  - 基本構造は理解可能
  - `flow`の種類が不明

### ❌ 理解困難（追加ドキュメント必須）

- [ ] authentication/pin/strongauth-pin.json
  - PIN認証の説明が全くない

- [ ] authentication/external-token/open-api.json
  - External Token認証の説明がない

- [ ] identity-verification/auth-face.json
  - `request.schema`の意味が不明
  - `pre_hook.additional_parameters`の仕組みが不明
  - `$.pre_hook_additional_parameters[0]`の参照方法が不明

- [ ] security-event-hook/ssf.json
  - `events`セクションの構造が複雑すぎて理解困難
  - イベントタイプごとの個別設定方法が不明
  - OAuth認証のキャッシュ設定が不明

---

## 推奨アクション

### Phase 1: 必須ドキュメント作成（即座に対応）

1. **authn/pin.md** - PIN認証ガイド
   - StrongAuth API連携
   - PIN登録・検証フロー

2. **authn/external-token.md** - External Token認証ガイド
   - 外部トークン検証の仕組み
   - Open API連携

### Phase 2: 既存ドキュメント改善（重要）

3. **federation.md**に追加:
   - `userinfo_execution.http_requests`の詳細
   - 複数API連続実行パターン
   - `$.userinfo_execution_http_requests[N]`の参照方法

4. **identity-verification.md**に追加:
   - `request.schema`（JSONSchema検証）の説明
   - `pre_hook.additional_parameters`の詳細
   - `$.pre_hook_additional_parameters[N]`の参照方法
   - `store.application_details_mapping_rules`の用途

5. **security-event-hook.md**に追加:
   - SSF `events`セクションの構造詳細
   - イベントタイプ別設定例
   - OAuth 2.0認証のキャッシュ設定

### Phase 3: 補足情報追加（時間があれば）

6. **authentication-policy.md**に追加:
   - `flow`に指定可能な値の完全リスト
   - flow別のポリシー例

7. **client.md**に追加:
   - `client_id_alias`の用途説明

---

## 具体例での検証

### 検証1: bank-app.json（Client設定）

**質問**: 「CIBA対応のWebアプリケーションクライアントを設定したい」

**現状のドキュメントで理解できるか**:
- ✅ 基本的な設定項目は理解可能
- ✅ CIBA extension設定は理解可能
- ⚠️ `client_id_alias`は理解不能

**結論**: **ほぼ理解可能**

---

### 検証2: power-direct.json（Federation設定）

**質問**: 「外部IdPから複数のAPIを呼び出してUserInfoを構築したい」

**現状のドキュメントで理解できるか**:
- ✅ 基本的なOIDC連携は理解可能
- ❌ `userinfo_execution.http_requests`配列の使い方が不明
- ❌ `$.userinfo_execution_http_requests[0].response_body`の参照方法が不明
- ❌ 複数APIの結果を統合する方法が不明

**結論**: **理解困難（詳細説明が必要）**

---

### 検証3: auth-face.json（Identity Verification設定）

**質問**: 「顔認証を開始する前に別のAPIで情報を取得したい」

**現状のドキュメントで理解できるか**:
- ✅ 基本的なprocess構造は理解可能
- ❌ `pre_hook.additional_parameters`の仕組みが不明
- ❌ `$.pre_hook_additional_parameters[0].response_body`の参照方法が不明
- ❌ `request.schema`でのバリデーション方法が不明

**結論**: **理解困難（高度な機能の説明が必要）**

---

### 検証4: ssf.json（Security Event Hook設定）

**質問**: 「異なるイベントタイプごとに別々のSSF設定をしたい」

**現状のドキュメントで理解できるか**:
- ✅ 基本的なtriggers設定は理解可能
- ❌ `events.{event-type}`の構造が不明
- ❌ イベントタイプごとに異なるURL/認証を設定する方法が不明
- ❌ OAuth認証のキャッシュ設定が不明

**結論**: **理解困難（SSF特有機能の説明が必要）**

---

## 優先度別の改善計画

### 🔴 Phase 1: 必須（1-2日）

1. `authn/pin.md`作成
2. `authn/external-token.md`作成

### 🟡 Phase 2: 重要（3-5日）

3. `federation.md`改善 - userinfo_execution詳細追加
4. `identity-verification.md`改善 - pre_hook/request.schema詳細追加
5. `security-event-hook.md`改善 - SSF events構造詳細追加

### 🟢 Phase 3: 補足（時間があれば）

6. `authentication-policy.md`補足 - flow種類リスト
7. `client.md`補足 - client_id_alias説明

---

**作成日**: 2025-10-13
