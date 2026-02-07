# Claude Code Context - idp-server

## プロジェクト概要
- **種類**: 身元確認特化エンタープライズ・アイデンティティプラットフォーム
- **言語**: Java 21+ (Spring Boot), Gradle
- **特徴**: Hexagonal Architecture + DDD, マルチテナント, OAuth 2.0/OIDC/CIBA/FAPI準拠

## アーキテクチャ
```
Controller → UseCase (EntryService) → Core (Handler-Service-Repository) → Adapter
```

### 主要モジュール
| モジュール | 役割 |
|-----------|------|
| `idp-server-core` | OIDCコアエンジン |
| `idp-server-platform` | プラットフォーム基盤 |
| `idp-server-use-cases` | EntryService実装 |
| `idp-server-control-plane` | 管理API契約定義 |
| `idp-server-core-adapter` | 永続化実装 |
| `idp-server-springboot-adapter` | Spring Boot統合 |
| `e2e/` | 3層テスト (spec/scenario/monkey) |

## 開発コマンド
```bash
./gradlew spotlessApply  # 必須: フォーマット修正
./gradlew build && ./gradlew test
cd e2e && npm test
```

## E2Eテスト実行時の注意

**`npx jest` ではなく `npm test` 経由で実行すること。**

`package.json` の `test` スクリプトが `NODE_EXTRA_CA_CERTS` で mkcert のルートCAを設定している。
`npx jest` 直接実行だとこの設定が抜け、自己署名証明書の検証エラー（`UNABLE_TO_VERIFY_LEAF_SIGNATURE`）でリクエストが失敗する。

```bash
# 正しい実行方法
cd e2e && npm test -- --testPathPattern="integration-05" --testNamePattern="テスト名"

# NG: TLSエラーになる
cd e2e && npx jest src/tests/integration/...
```

## ローカル環境でのコード変更反映

**重要**: Javaコードを変更した場合、Docker imageを再ビルドしないと変更が反映されない。

```bash
# Docker imageビルド + コンテナ再起動（ビルドはDocker内で実行される）
docker compose up -d --build idp-server-1 idp-server-2
```

- Dockerfileがマルチステージビルドのため、`./gradlew bootJar`は不要
- `docker compose restart`だけでは新しいコードは反映されない
- `--build`フラグが必須

## Issue作業開始手順

1. **開発者ガイドを読む**（該当ドメイン）
   - 管理API → `content_06_developer-guide/02-control-plane/`
   - OAuth/OIDC → `content_06_developer-guide/03-application-plane/`

2. **既存コードを探す**
   ```
   Grep "関連キーワード" libs/idp-server-core/
   ```

3. **関連ファイルを読む**
   - Handler → Service → Repository の処理フロー確認
   - 既存の類似実装をパターン参考

4. **変更箇所を特定**
   - 新規作成: 値オブジェクト、Gateway、Validator等
   - 修正: Handler、Verifier、設定クラス等

## 設計原則
- **プロトコル準拠**: OAuth 2.0/OIDC仕様への厳密準拠
- **型安全性**: `String`/`Map`濫用禁止、値オブジェクト優先
- **責務分離**: Handler-Service-Repository パターン
- **マルチテナント**: 全Repository操作で`Tenant`第一引数
- **両DB対応**: PostgreSQL + MySQL両方の実装必須

## 開発者ガイド（作業開始時に参照）

### Control Plane（管理API）
| ドキュメント | 内容 |
|-------------|------|
| `content_06_developer-guide/02-control-plane/00-resource-overview.md` | リソース一覧 |
| `content_06_developer-guide/02-control-plane/02-first-api.md` | 最初のAPI実装 |
| `content_06_developer-guide/02-control-plane/03-system-level-api.md` | システムレベルAPI |
| `content_06_developer-guide/02-control-plane/04-organization-level-api.md` | 組織レベルAPI |

### Application Plane（OAuth/OIDC）
| ドキュメント | 内容 |
|-------------|------|
| `content_06_developer-guide/03-application-plane/02-authorization-flow.md` | 認可フロー |
| `content_06_developer-guide/03-application-plane/03-token-endpoint.md` | トークンエンドポイント |
| `content_06_developer-guide/03-application-plane/04-authentication.md` | 認証 |
| `content_06_developer-guide/03-application-plane/06-ciba-flow.md` | CIBAフロー |
| `content_06_developer-guide/03-application-plane/07-identity-verification.md` | 身元確認 |
| `content_06_developer-guide/03-application-plane/09-security-event.md` | セキュリティイベント |

## 詳細リファレンス（深掘り時に参照）

| トピック | ドキュメント |
|---------|-------------|
| 実装パターン詳細 | `content_10_ai_developer/ai-11-core.md` |
| EntryService | `content_10_ai_developer/ai-10-use-cases.md` |
| DB実装差異 | `content_10_ai_developer/adapters.md` |
| 商用デプロイ | `content_08_ops/commercial-deployment/` |

## 実装チェックリスト
- [ ] Tenant第一引数（OrganizationRepository除く）
- [ ] Context Creator使用（TODOコメント禁止）
- [ ] Validator/Verifier は void + throw
- [ ] 両DB実装（PostgreSQL + MySQL）
- [ ] E2Eテスト追加

## コードレビュー
コミットメッセージに `@codex review` でAI自動レビュー実行

## アンチパターン（禁止）
- Util濫用、Map濫用、DTO肥大化
- 永続化層でのビジネスロジック
- 想像でのドキュメント作成（コード確認必須）
