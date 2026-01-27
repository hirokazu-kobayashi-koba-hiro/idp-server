# 身元確認/eKYC

[← ユースケース一覧に戻る](./quickstart-03-common-use-cases.md)

---

## できること

外部eKYCサービスと連携して、本人確認の申込みフローを管理し、確認済み情報（verified_claims）を発行します。

- **申込みフローの管理**: 申込み → eKYC実施 → 審査 → 結果登録の一連の流れ
- **外部eKYCサービス連携**: 専門事業者のサービスと連携
- **確認済み情報の発行**: IDトークン・アクセストークンにverified_claimsを含める
- **申込み状況の追跡**: ステータス管理（申込み中、審査中、承認、否認等）

---

## 導入時に決めること

### 1. 身元確認の実施方法

| 選択肢 | 説明 | 向いているケース |
|-------|------|----------------|
| **idp-server経由で申込み** | アプリ → idp-server → eKYCサービスの全フロー管理 | 新規構築、統合管理したい |
| **eKYC結果のみ登録** | 別システムのeKYC結果をidp-serverに登録 | 既存eKYCシステムあり |

**idp-serverでの設定**:
- 申込みフローの場合: テンプレートで申込みプロセスを定義
- 結果登録の場合: 登録用APIの設定

### 2. 申込みプロセスの設計

外部eKYCサービスのAPI仕様に合わせて、申込みの流れを定義します。

| 決めること | 選択肢の例 |
|-----------|-----------|
| **プロセスの数** | 1ステップ（申込みのみ）、複数ステップ（申込み → 書類提出 → 完了） |
| **プロセスの順序** | 自由、順序固定（依存関係あり） |
| **リトライ可否** | 再実行可能、1回のみ |

**典型的なプロセス例**:
```
1. apply（基本情報入力）
2. request-ekyc（書類撮影・提出）
3. complete-ekyc（完了確認）
4. callback-examination（審査中通知、外部からのコールバック）
5. callback-result（審査結果通知、外部からのコールバック）
```

**idp-serverでの設定**:
- テンプレートでプロセスを定義
- プロセス依存関係（required_processes）を設定
- 各プロセスのリトライ可否（allow_retry）を設定

### 3. 外部eKYCサービスとの連携

| 決めること | 選択肢の例 |
|-----------|-----------|
| **連携タイミング** | 各プロセスで都度連携、最後にまとめて連携 |
| **データ送信範囲** | 全データ送信、必要な項目のみ |
| **認証方式** | OAuth 2.0、HMAC認証、Basic認証 |

**idp-serverでの設定**:
- 各プロセスのexecutionで外部APIエンドポイントを設定
- リクエスト/レスポンスのマッピングルールを定義
- 認証情報（OAuth、HMAC等）を設定

### 4. ステータス遷移の条件

| 決めること | 選択肢の例 |
|-----------|-----------|
| **承認条件** | 外部サービスのレスポンスで判定、特定フィールドの値で判定 |
| **否認条件** | 外部サービスのレスポンスで判定 |
| **キャンセル条件** | ユーザー操作のみ、一定期間放置で自動キャンセル |

**idp-serverでの設定**:
- 各プロセスのtransitionで遷移条件を定義
- 条件式（JSONPath、比較演算子）を設定

### 5. verified_claimsへの変換

外部eKYCサービスから返却されたデータを、OIDC4IDA標準のverified_claims形式に変換します。

| 決めること | 選択肢の例 |
|-----------|-----------|
| **保存する情報** | 氏名・生年月日・住所全て、最小限のみ（氏名・生年月日） |
| **trust_framework** | jp_aml、eidas、独自の値 |
| **evidence情報** | 保存する、保存しない |

**idp-serverでの設定**:
- resultセクションでverified_claims_mapping_rulesを定義
- 外部サービスのレスポンスからverified_claims形式へのマッピング

### 6. 確認済み情報の活用

| 使い方 | 実現方法 |
|-------|---------|
| **IDトークンに含める** | claimsパラメータで要求 |
| **アクセストークンに含める** | verified_claims:xxxスコープで要求 |
| **特定機能を本人確認必須にする** | required_identity_verification_scopesで制御 |

**idp-serverでの設定**:
- テナント設定でverified_claims出力を有効化
- required_identity_verification_scopesを設定

---

## 関連ドキュメント

- [How-to: 身元確認申込み導入ガイド](../content_05_how-to/phase-4-extensions/identity-verification/01-guide.md)
- [How-to: 身元確認申込み](../content_05_how-to/phase-4-extensions/identity-verification/02-application.md)
- [How-to: 身元確認結果登録](../content_05_how-to/phase-4-extensions/identity-verification/03-registration.md)
- [Concept: 身元確認済みID](../content_03_concepts/05-advanced-id/concept-01-id-verified.md)

---

**最終更新**: 2026-01-27
