---
name: spec-enterprise
description: idp-serverのエンタープライズ機能の全体像を把握する際に使用。組織ガバナンス、法規制、監査、スケールの4観点で提供・未提供機能を整理し、今後の拡張方針の判断に役立つ。
---

# エンタープライズ機能

## エンタープライズとは

「技術的に動く」ではなく**「組織・法規制・監査・スケールに耐える」**。

| 観点 | 「技術的に動く」 | 「エンタープライズ」 |
|------|-----------------|-------------------|
| **組織** | 1人の管理者が全部見る | 部門別の委任管理、承認フロー、職掌分離 |
| **法規制** | 機能がある | 証跡が残る、説明できる、監査に耐える |
| **監査** | ログが出る | いつ誰が何をなぜ変えたか追跡可能 |
| **スケール** | 動く | 数千テナント、数百万ユーザーで安定運用 |

---

## 1. 組織ガバナンス

idp-serverは Organization → Tenant → User/Role/Permission の階層で、マルチテナント環境の組織管理を提供する。

### 提供している機能

| 機能 | 説明 | 関連スキル |
|------|------|-----------|
| マルチテナント分離 | テナント間のデータ・設定の完全分離。PostgreSQL RLSによるDB層強制 | `ops-tenant-config` |
| 組織階層管理 | Organization → Tenant → User/Role の階層構造 | `dev-control-plane` |
| RBAC | ロール・パーミッションによるアクセス制御 | `spec-rbac`, `dev-control-plane` |
| 委任管理 | 組織管理者への管理権限委譲（Organization-level API） | `spec-rbac`, `dev-control-plane` |
| 委任管理の粒度制御 | Resource×Action単位の細粒度権限（73種類）+ ワイルドカード委譲 | `spec-rbac` |

#### 委任管理の粒度制御 詳細

`idp:resource:action` 形式で73種類の権限を定義（ワイルドカード含む）。AWS IAMスタイルのワイルドカードマッチング（`idp:*`, `idp:user:*`）により段階的な権限委譲が可能。`OrganizationAccessVerifier` による4段階の組織スコープ検証で、マルチテナント環境での安全な委任管理を実現。

**詳細は `spec-rbac` スキルを参照。**

### 未提供の機能

| 機能 | 説明 |
|------|------|
| リソースID単位の制限 | 「特定クライアントのみ編集可」等の個別リソースへのアクセス制限 |
| MemberRoleの活用 | OWNER/EDITOR/VIEWER等の組織内ロールと権限体系の紐付け |
| 設定変更の承認フロー | 4-eyes principle、maker-checker（変更申請→承認→適用） |
| 職掌分離の強制 | 設定者と承認者の分離を技術的に強制 |
| 設定変更のロールバック | 任意の時点への設定巻き戻し |
| 設定のバージョン管理 | 変更履歴の世代管理 |
| 環境間プロモーション | dev → staging → prod の設定昇格フロー |

---

## 2. 法規制・コンプライアンス

idp-serverは金融規制（FAPI）と身元確認（OIDC4IDA）に特化した規制対応機能を提供する。

### 提供している機能

| 機能 | 説明 | 関連スキル |
|------|------|-----------|
| eKYC/身元確認 | OIDC4IDA準拠の外部eKYCサービス連携 | `spec-identity-verification`, `use-case-ekyc` |
| verified_claims | 身元確認済み属性の発行・管理 | `spec-identity-verification` |
| FAPI準拠 | FAPI 1.0 Baseline/Advanced、mTLS、PAR、JARM | `spec-fapi`, `use-case-financial-grade` |
| FAPI CIBA | 金融グレードのデバイス分離認証 | `spec-ciba`, `use-case-ciba` |
| 同意管理 | OAuth同意の取得・記録 | `spec-grant` |
| Verifiable Credentials | OID4VCI準拠のCredential発行 | `spec-verifiable-credentials` |
| PII制御 | セキュリティイベントログのPII出力制御（include_user_pii等） | `spec-security-event` |

### 未提供の機能

| 機能 | 説明 |
|------|------|
| GDPR削除要求 | Right to be forgotten対応（ユーザーデータの完全削除） |
| データ保持ポリシー | 期間指定の自動削除・アーカイブ |
| データ所在地制御 | リージョン制約（データレジデンシー） |
| 規制準拠レポート | 定期レポート自動生成 |
| 同意の取消・履歴 | 同意変更の追跡可能性 |

---

## 3. 監査・説明責任

idp-serverは2つの独立した記録体系を持つ。**監査ログ**（管理操作の追跡）と**セキュリティイベント**（認証・認可フローの記録）は別のテーブル・別の仕組みで動作する。

### 監査ログ（Audit Log） — Control Plane

管理API操作の「誰が何をいつどう変えたか」を記録する。`audit_log` テーブルに保存。

| 機能 | 説明 | 関連スキル |
|------|------|-----------|
| 設定変更の差分記録 | 変更前/変更後の両方を保存するdiff記録（`before`/`after` ペイロード） | `dev-control-plane` |
| 監査ログ取得API | Management APIからの監査ログ取得・検索 | `dev-control-plane` |
| テナント統計 | テナント単位のメトリクス収集 | `ops-deployment` |

### セキュリティイベント（Security Event） — Application Plane

OAuth/OIDCフローの「認証・認可で何が起きたか」を記録する。`security_event` テーブルに保存。

| 機能 | 説明 | 関連スキル |
|------|------|-----------|
| セキュリティイベント記録 | 認可フロー、トークン発行、ログアウト、CIBA等のイベント記録 | `spec-security-event` |
| セキュリティイベントフック | イベント駆動のフック連携（Slack/Email/Webhook/Datadog/SSF） | `spec-security-event` |
| Shared Signals Framework | リアルタイムセキュリティイベントストリーミング | `spec-security-event` |
| パーティショニング | 日単位RANGE、90日保持。pg_partman / MySQL Event Schedulerで自動管理 | `spec-security-event` |
| アーカイブ | 90日超過データをarchiveスキーマにDETACH → 外部エクスポート（stub実装） | `spec-security-event` |

### データ保持の違い

| | security_event | audit_log |
|---|:---:|:---:|
| パーティショニング | 日単位（90日保持） | なし |
| アーカイブ | 自動（pg_partman / Event Scheduler） | なし |
| 保持方針 | 90日後に削除 | 永続保存（コンプライアンス要件） |

### 未提供の機能

| 機能 | 対象 | 説明 |
|------|------|------|
| 変更理由の記録 | 監査ログ | なぜ変更したか（チケット番号・変更理由の記録） |
| 改ざん不能ログ | 両方 | append-only、署名付きログ |
| 監査ログの長期保存戦略 | 監査ログ | 永続保存テーブルの肥大化対策（外部アーカイブ等） |
| 定期監査レポート | 両方 | 監査人向けレポート自動生成 |

---

## 4. スケール・運用耐性

idp-serverはPostgreSQL RLSによるDB層のテナント分離と、両DB対応による柔軟なデプロイを提供する。

### 提供している機能

| 機能 | 説明 | 関連スキル |
|------|------|-----------|
| PostgreSQL RLS | DB層でのテナント分離強制（FORCE ROW LEVEL SECURITY） | `dev-database` |
| 両DB対応 | PostgreSQL / MySQL | `dev-database` |
| ヘルスチェック | サービス死活監視エンドポイント | `ops-deployment` |
| テナント統計 | メトリクス収集 | `ops-deployment` |
| 期限切れデータ削除 | 不要データの自動クリーンアップ | `ops-deployment` |

### 未提供の機能

| 機能 | 説明 |
|------|------|
| 障害の影響局所化 | blast radius制御（障害時の影響範囲の限定） |
| バックアップ/リストア | 設定・データの世代管理 |
| キャパシティプランニング | 使用量予測・アラート |

---

## 今後の拡張方針

| 優先度 | 機能 | 理由 |
|:------:|------|------|
| 1 | 変更理由の記録 | 監査ログに reason フィールド追加。低コスト高効果 |
| 2 | 委任管理の粒度制御 | MemberRoleと権限体系の紐付け等 |
| 3 | GDPR削除要求 | eKYC/verified_claimsとセットで規制対応の訴求力大 |

$ARGUMENTS
