---
sidebar_position: 7
---

# 実践: EKS + Argo CD パイプライン

## このドキュメントの目的

**GitHub Actions（CI）+ Argo CD（CD）+ Amazon EKS** を組み合わせた具体的なパイプライン構成を理解し、GitOpsベースのCI/CDを設計・運用できるようになることが目標です。

IDサービスを題材にした具体例を使用しますが、パイプライン構成自体はあらゆるSaaSに共通して適用できます。

### 前提知識

この記事は以下のドキュメントを読んでいることを前提としています。

- [CI/CDの基礎](./ci-cd-basics.md) - CI/CDの基本概念
- [パイプライン設計](./pipeline-design.md) - ステージ・ジョブの設計パターン
- [デプロイ戦略](./deployment-strategy.md) - デプロイ戦略の選択肢
- [GitOps](./gitops.md) - GitOpsの概念とエージェントの役割

---

## 全体アーキテクチャ

### リポジトリ構成

GitOpsではアプリケーションコードとKubernetesマニフェストを**別リポジトリ**に分離するのが一般的です。

```
リポジトリ構成:

  ┌──────────────────────────────────┐
  │  アプリケーションリポジトリ        │
  │  (idp-server)                    │
  │                                  │
  │  ・Javaソースコード               │
  │  ・Dockerfile                    │
  │  ・GitHub Actions ワークフロー     │
  │  ・テストコード                   │
  └──────────────┬───────────────────┘
                 │ CI が Image Tag を更新
                 ▼
  ┌──────────────────────────────────┐
  │  マニフェストリポジトリ            │
  │  (idp-server-manifests)          │
  │                                  │
  │  ・Kubernetes マニフェスト        │
  │  ・Kustomize Overlay             │
  │  ・Argo CD Application 定義      │
  └──────────────────────────────────┘
```

**なぜリポジトリを分けるのか**:

| 理由 | 説明 |
|------|------|
| 権限分離 | アプリ開発者とインフラ管理者でアクセス権を分けられる |
| コミット履歴の分離 | アプリの変更とインフラの変更が混ざらない |
| デプロイ頻度の違い | アプリは頻繁に更新、インフラ設定は低頻度 |
| ロールバックの独立性 | アプリだけ / インフラだけのロールバックが容易 |

### エンドツーエンドのフロー

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        CI/CDパイプライン全体像                            │
└─────────────────────────────────────────────────────────────────────────┘

  1. Push          2. CI              3. Publish        4. Update
  ┌──────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐
  │ 開発者 │────▶│ GitHub   │────▶│ ECR      │      │マニフェスト│
  │      │      │ Actions  │      │          │      │リポジトリ │
  │ git  │      │          │      │ Docker   │      │          │
  │ push │      │ Lint     │      │ Image    │      │ image tag│
  │      │      │ Build    │      │          │      │ を更新   │
  └──────┘      │ Test     │      └──────────┘      └────┬─────┘
                │ Push     │                             │
                └──────────┘                             │
                                                         │
  5. Sync          6. Deploy                             │
  ┌──────────┐   ┌──────────┐                            │
  │ Argo CD  │──▶│ EKS      │        Argo CD が検知 ◀────┘
  │          │   │          │
  │ Git と   │   │ Pod更新  │
  │ 差分検知 │   │ ヘルス   │
  │ 同期     │   │ チェック │
  └──────────┘   └──────────┘
```

**ステップの詳細**:

| ステップ | 実行者 | 内容 |
|----------|--------|------|
| 1. Push | 開発者 | feature ブランチからmainへマージ |
| 2. CI | GitHub Actions | Lint → Build → Test → Docker Image ビルド |
| 3. Publish | GitHub Actions | Docker Image を ECR にプッシュ |
| 4. Update | GitHub Actions | マニフェストリポジトリの image tag を更新 |
| 5. Sync | Argo CD | マニフェストリポジトリの変更を検知し、差分を計算 |
| 6. Deploy | Argo CD + EKS | Kubernetes リソースを更新、ヘルスチェック |

---

## CI: GitHub Actions ワークフロー

### ワークフローの構成

```
GitHub Actions ワークフロー:

  on: push (main branch)
  │
  ├── Job 1: lint
  │   ├── チェックアウト
  │   ├── Java セットアップ
  │   └── ./gradlew spotlessCheck
  │
  ├── Job 2: build-and-test (lint完了後)
  │   ├── チェックアウト
  │   ├── Gradle キャッシュ復元
  │   ├── ./gradlew build
  │   └── テスト結果アップロード
  │
  └── Job 3: publish (build-and-test完了後)
      ├── ECR ログイン
      ├── Docker Image ビルド & プッシュ
      └── マニフェストリポジトリの image tag 更新
```

### ワークフロー定義の例

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  ECR_REGISTRY: 123456789012.dkr.ecr.ap-northeast-1.amazonaws.com
  ECR_REPOSITORY: idp-server
  MANIFEST_REPO: myorg/idp-server-manifests

jobs:
  # ─────────────────────────────────────────
  # Job 1: 静的解析
  # ─────────────────────────────────────────
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: gradle/actions/setup-gradle@v4

      - run: ./gradlew spotlessCheck

  # ─────────────────────────────────────────
  # Job 2: ビルド & テスト
  # ─────────────────────────────────────────
  build-and-test:
    needs: lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: gradle/actions/setup-gradle@v4

      - run: ./gradlew build

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: "**/build/reports/tests/"

  # ─────────────────────────────────────────
  # Job 3: イメージ公開 & マニフェスト更新
  # ─────────────────────────────────────────
  publish:
    if: github.ref == 'refs/heads/main'
    needs: build-and-test
    runs-on: ubuntu-latest
    permissions:
      id-token: write   # OIDC で AWS 認証
      contents: read
    steps:
      # AWS 認証（OIDC 推奨）
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/github-actions-role
          aws-region: ap-northeast-1

      # ECR ログイン
      - uses: aws-actions/amazon-ecr-login@v2
        id: ecr-login

      # アプリケーションコードのチェックアウト
      - uses: actions/checkout@v4

      # Docker Image ビルド & プッシュ
      - name: Build and push Docker image
        env:
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

      # マニフェストリポジトリの image tag を更新
      - name: Update manifest repository
        env:
          IMAGE_TAG: ${{ github.sha }}
          GH_TOKEN: ${{ secrets.MANIFEST_REPO_TOKEN }}
        run: |
          git clone https://x-access-token:${GH_TOKEN}@github.com/${MANIFEST_REPO}.git manifests
          cd manifests
          # Kustomize の image tag を更新
          cd overlays/production
          kustomize edit set image $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          # コミット & プッシュ
          git config user.name "github-actions"
          git config user.email "github-actions@github.com"
          git add .
          git commit -m "chore: update idp-server image to $IMAGE_TAG"
          git push
```

### Pull Request 時の挙動

mainブランチへのPush時とPull Request時で実行するジョブを分けます。

```
Pull Request 時:
  lint → build-and-test              (Image公開はスキップ)

main Push 時:
  lint → build-and-test → publish    (Image公開 & マニフェスト更新)
```

`publish` ジョブの `if: github.ref == 'refs/heads/main'` により、Pull Requestではイメージ公開がスキップされます。

---

## マニフェストリポジトリの構成

### ディレクトリ構造

Kustomize を使って環境ごとの差分を管理します。

```
idp-server-manifests/
├── base/                          # 共通定義
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   └── hpa.yaml                   # Horizontal Pod Autoscaler
│
├── overlays/                      # 環境別差分
│   ├── staging/
│   │   ├── kustomization.yaml     # staging固有のパッチ
│   │   ├── namespace.yaml
│   │   └── patches/
│   │       ├── replicas.yaml      # replicas: 2
│   │       └── resources.yaml     # CPU/Memory制限（小）
│   │
│   └── production/
│       ├── kustomization.yaml     # production固有のパッチ
│       ├── namespace.yaml
│       └── patches/
│           ├── replicas.yaml      # replicas: 4
│           └── resources.yaml     # CPU/Memory制限（大）
│
└── argocd/                        # Argo CD Application 定義
    ├── staging.yaml
    └── production.yaml
```

### base/deployment.yaml の例

```yaml
# base/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
  labels:
    app: idp-server
spec:
  selector:
    matchLabels:
      app: idp-server
  template:
    metadata:
      labels:
        app: idp-server
    spec:
      containers:
        - name: idp-server
          image: 123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/idp-server
          # ↑ tag は Kustomize が上書き
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: kubernetes
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
          resources:
            requests:
              cpu: 500m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
```

### overlays/production/kustomization.yaml の例

```yaml
# overlays/production/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: idp-production

resources:
  - ../../base
  - namespace.yaml

patches:
  - path: patches/replicas.yaml
  - path: patches/resources.yaml

images:
  - name: 123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/idp-server
    newTag: abc1234def   # ← CI が自動更新する箇所
```

---

## CD: Argo CD の設定

### Argo CD Application 定義

Argo CD がマニフェストリポジトリを監視し、EKSクラスターに同期する設定です。

```yaml
# argocd/production.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: idp-server-production
  namespace: argocd
spec:
  project: default

  # ソース: マニフェストリポジトリ
  source:
    repoURL: https://github.com/myorg/idp-server-manifests.git
    targetRevision: main
    path: overlays/production

  # デプロイ先: EKSクラスター
  destination:
    server: https://kubernetes.default.svc
    namespace: idp-production

  # 同期ポリシー
  syncPolicy:
    automated:
      prune: true        # Git から削除されたリソースをクラスターからも削除
      selfHeal: true     # 手動変更をGitの状態に自動修正
    syncOptions:
      - CreateNamespace=true
    retry:
      limit: 3
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 1m
```

### 同期ポリシーの選択肢

| 設定 | 説明 | 推奨 |
|------|------|------|
| `automated` | Gitの変更を自動で同期 | Staging: 有効, Production: 要検討 |
| `prune: true` | Gitから削除したリソースをクラスターからも削除 | 有効（不要リソースの放置防止） |
| `selfHeal: true` | 手動変更を自動でGitの状態に戻す | 有効（ドリフト防止） |
| 手動同期 | UIまたはCLIで明示的に同期 | Production で安全重視の場合 |

**IDサービスの場合**: Staging は `automated` で自動同期し、Production は手動同期（Argo CD UIで承認後に同期）とするケースが多いです。

```
Staging:  Git push → 自動同期 → 即座にデプロイ（高速なフィードバック）
Production: Git push → Argo CD が差分表示 → 運用者が確認 → 手動同期
```

---

## 環境昇格フロー

### Staging → Production の昇格

```
環境昇格フロー:

  ┌──────────────────────────────────────────────────────────────┐
  │ アプリケーションリポジトリ (idp-server)                       │
  │                                                              │
  │  feature ──PR──▶ main ──CI──▶ ECR (Image: abc1234)           │
  └──────────────────────────────────────────────────────────────┘
                                      │
                                      │ CIがimage tagを更新
                                      ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ マニフェストリポジトリ (idp-server-manifests)                 │
  │                                                              │
  │  Step 1: CI が staging の image tag を更新                    │
  │  overlays/staging/kustomization.yaml → newTag: abc1234       │
  │          │                                                   │
  │          │ Argo CD 自動同期                                   │
  │          ▼                                                   │
  │  Step 2: Staging で動作確認                                   │
  │          │                                                   │
  │          │ 手動 or 自動で PR 作成                              │
  │          ▼                                                   │
  │  Step 3: production の image tag を更新する PR                 │
  │  overlays/production/kustomization.yaml → newTag: abc1234    │
  │          │                                                   │
  │          │ PR レビュー & マージ                                │
  │          ▼                                                   │
  │  Step 4: Argo CD が production を同期                         │
  └──────────────────────────────────────────────────────────────┘
```

### 昇格方式の比較

| 方式 | 流れ | 適用場面 |
|------|------|---------|
| 自動昇格 | Staging同期成功 → 自動でProduction更新 | 高頻度リリース、成熟したテスト基盤 |
| PR昇格 | Staging確認後、PRでProduction更新 | 承認フローが必要、監査要件あり |
| 手動同期 | Staging確認後、Argo CD UIで手動同期 | 最も慎重、初期導入時 |

**IDサービスの場合**: 認証基盤はセキュリティ影響が大きいため、**PR昇格**が推奨です。PRにはimage tagの変更だけが含まれるため、レビューは軽量ですが監査証跡が残ります。

---

## Argo CD によるデプロイ監視

### Sync Status と Health Status

Argo CDは2つのステータスでアプリケーションの状態を管理します。

```
Sync Status（Gitとの同期状態）:

  Synced      ── Gitの状態とクラスターの状態が一致
  OutOfSync   ── Gitの状態とクラスターの状態が不一致
  Unknown     ── 状態を判定できない

Health Status（アプリケーションの健全性）:

  Healthy     ── すべてのリソースが正常
  Progressing ── デプロイ進行中
  Degraded    ── 一部のリソースに問題あり
  Suspended   ── 一時停止中
  Missing     ── リソースが存在しない
```

### Argo CD ダッシュボードで確認できること

```
Argo CD UI:

  ┌──────────────────────────────────────────────────────────┐
  │ idp-server-production                                    │
  │                                                          │
  │ Sync: Synced ✓    Health: Healthy ✓                      │
  │ Current Image: idp-server:abc1234                        │
  │ Last Sync: 2026-03-04 10:30:00                          │
  │                                                          │
  │ Resources:                                               │
  │ ┌────────────────────────────────────────────┐           │
  │ │ Deployment/idp-server  ✓ Healthy           │           │
  │ │ ├── ReplicaSet/idp-server-abc1234  ✓       │           │
  │ │ │   ├── Pod/idp-server-abc1234-x1  ✓ Running│          │
  │ │ │   ├── Pod/idp-server-abc1234-x2  ✓ Running│          │
  │ │ │   ├── Pod/idp-server-abc1234-x3  ✓ Running│          │
  │ │ │   └── Pod/idp-server-abc1234-x4  ✓ Running│          │
  │ │ Service/idp-server  ✓ Healthy              │           │
  │ │ Ingress/idp-server  ✓ Healthy              │           │
  │ │ HPA/idp-server  ✓ Healthy                  │           │
  │ └────────────────────────────────────────────┘           │
  └──────────────────────────────────────────────────────────┘
```

### ロールバック

GitOps環境でのロールバックは**Gitのリバート**が基本です（詳細は[GitOps](./gitops.md)を参照）。Argo CD では以下の2つの方法が使えます。

| 方法 | 手順 | 速度 | Git整合性 | 推奨 |
|------|------|------|-----------|------|
| Git Revert | マニフェストリポジトリで `git revert HEAD` → Argo CD が自動同期 | やや遅い | 保たれる | 通常時 |
| Argo CD UI | History から前のリビジョンを選択して Rollback | 速い | 一時的に乖離 | 緊急時 |

---

## セキュリティ上の考慮点

### 認証情報の管理

| 認証情報 | 管理方法 | 補足 |
|----------|---------|------|
| AWS認証（GitHub Actions → ECR） | OIDC Federation | シークレット不要、IAM Roleで制御 |
| マニフェストリポジトリへのアクセス | GitHub App / Deploy Key | Fine-grained Permission で最小権限 |
| Argo CD → Git リポジトリ | SSH Key / GitHub App | Argo CD のシークレット管理機能 |
| Argo CD → EKS クラスター | ServiceAccount (IRSA) | Kubernetes RBACで最小権限 |
| アプリケーションのシークレット | External Secrets Operator | AWS Secrets Manager等と連携 |

### CI/CDパイプラインの権限設計

```
権限の分離:

  GitHub Actions (CI)
  ├── ECR: Push権限のみ（Pull/Delete不可）
  ├── マニフェストリポジトリ: Write権限
  └── EKSクラスター: アクセス権限なし ← 重要!

  Argo CD (CD)
  ├── マニフェストリポジトリ: Read権限のみ
  ├── EKSクラスター: 対象Namespaceのみにデプロイ権限
  └── ECR: Pull権限のみ
```

**ポイント**: CIパイプラインはクラスターへのアクセス権限を持ちません。これがGitOpsの大きなセキュリティ上の利点です。

---

## トラブルシューティング

### よくある問題と対処法

| 問題 | 原因 | 対処 |
|------|------|------|
| Argo CD が OutOfSync のまま | マニフェストの構文エラー | `argocd app diff` で差分確認 |
| Pod が CrashLoopBackOff | アプリケーションの起動失敗 | Pod ログを確認、image tag の正しさを検証 |
| Image Pull エラー | ECR認証の問題 | IAM RoleとServiceAccountの紐付けを確認 |
| 同期が遅い | Argo CDのポーリング間隔 | Webhook設定で即時検知に変更 |
| Healthy にならない | Probe設定の不備 | liveness/readinessProbeのパス・ポートを確認 |

### Argo CD のポーリング vs Webhook

```
ポーリング（デフォルト）:
  Argo CD ──3分ごとに確認──▶ Git リポジトリ
  遅延: 最大3分

Webhook（推奨）:
  Git リポジトリ ──push時に通知──▶ Argo CD
  遅延: 数秒
```

Webhook を設定すると、マニフェストリポジトリへのPush直後にArgo CDが同期を開始します。

---

## まとめ: パイプライン全体の流れ

```
開発者がコードをPush
  │
  ▼
GitHub Actions (CI)
  │
  ├── 1. Lint (spotlessCheck)
  ├── 2. Build & Test (gradlew build)
  ├── 3. Docker Build & ECR Push
  └── 4. マニフェストリポジトリの image tag 更新
         │
         ▼
マニフェストリポジトリに新しいコミット
         │
         ▼
Argo CD が変更を検知
  │
  ├── Staging: 自動同期 → デプロイ → ヘルスチェック
  │
  └── Production: 差分表示 → 手動承認 → 同期 → デプロイ → ヘルスチェック
         │
         ▼
EKS クラスターが新しいバージョンで稼働
```

---

## 次のステップ

EKS + Argo CD によるGitOpsパイプラインを理解しました。

### 次に読むべきドキュメント

1. [CI/CDにおけるテスト戦略](./testing-strategy.md) - パイプラインに組み込むテストの設計
2. [デプロイ戦略](./deployment-strategy.md) - ローリング / Canary 等の戦略をArgo CDで実現する方法

### 関連リソース

- [Docker / Kubernetes](../13-docker-kubernetes/) - Kubernetes の基礎知識

---

**最終更新**: 2026-03-04
**対象**: SaaS開発者・プラットフォームエンジニア
