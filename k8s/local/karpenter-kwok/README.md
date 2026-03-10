# Karpenter + KWOK ローカル検証環境

## 概要

[Karpenter](https://karpenter.sh/) は Kubernetes のノードオートスケーラー。Pod のリソース要求に応じて自動的にノードを追加・削除する。

[KWOK](https://kwok.sigs.k8s.io/) (Kubernetes WithOut Kubelet) は、実際の VM やコンテナを起動せずに仮想ノードをシミュレートするツール。

この環境では、KWOK プロバイダーを使って Karpenter のノードスケーリング動作をローカルの Kind クラスタ上で検証できる。

> **⚠️ 重要: KWOK ノード上ではコンテナは実行されません**
>
> KWOK ノードには [kubelet](../../documentation/docs/content_11_learning/13-docker-kubernetes/kubernetes-architecture.md)（各ノードでコンテナを起動・管理するエージェント）が存在しないため、Pod は `Running` と**表示される**だけで、実際にはコンテナプロセスは起動していません。ネットワーク通信、ヘルスチェック、ログ出力なども動作しません。
>
> この環境で検証できるのは **Karpenter のスケジューリング判断**（ノード追加・削除・Consolidation・NodePool ポリシー）のみです。

## 前提条件

| ツール | バージョン |
|--------|-----------|
| Docker | - |
| kind | v0.20.0+ |
| kubectl | v1.28+ |
| helm | v3.12+ |
| Go | 1.22+ |

> `ko`（Go イメージビルダー）は setup.sh が自動インストールする。

## セットアップ

```bash
bash setup.sh
```

Karpenter リポジトリのクローン・ビルドを含むため、初回は数分かかる。

## 体験できること

- ワークロードのスケールアウトに伴うノードの自動追加
- ワークロードのスケールイン時のノード自動削除（Consolidation）
- NodePool によるノードプロビジョニングポリシーの制御

## 体験できないこと

- 実際のクラウドプロバイダー（AWS/GCP/Azure）との連携
- 実際のコンテナ実行（KWOK ノード上の Pod は仮想的に Running 状態になる）
- ネットワーキングやストレージの動作
- Webhook によるバリデーション

## 手動操作ガイド

### スケールアウト（ノード追加）

```bash
# デモワークロードを 5 replicas にスケール
kubectl scale deployment demo-inflate --replicas=5

# ノードの追加を監視
kubectl get nodes -w

# Pod の状態を確認
kubectl get pods -o wide
```

### スケールイン（Consolidation）

```bash
# 0 replicas に戻す
kubectl scale deployment demo-inflate --replicas=0

# ノードの削除を監視（consolidateAfter: 30s で削除）
kubectl get nodes -w
```

### NodePool の状態確認

```bash
kubectl get nodepool
kubectl describe nodepool default
```

### Karpenter ログ

```bash
kubectl -n kube-system logs -l app.kubernetes.io/name=karpenter -f
```

## 自動検証

スケールアウト → Consolidation の一連の動作を自動で検証する:

```bash
bash verify.sh
```

## クリーンアップ

```bash
bash teardown.sh
```

## ファイル構成

```
karpenter-kwok/
├── README.md              # 本ドキュメント
├── setup.sh               # セットアップスクリプト
├── teardown.sh            # クリーンアップスクリプト
├── verify.sh              # 動作検証スクリプト
├── kind-config.yaml       # Kind クラスタ設定
└── manifests/
    ├── nodepool.yaml      # NodePool + KWOKNodeClass
    └── demo-workload.yaml # スケール検証用デモ Deployment
```

## 既存環境との関係

| 項目 | 既存 (`k8s/local/`) | 本環境 |
|------|---------------------|--------|
| Kind クラスタ名 | `idp-local` | `karpenter-kwok` |
| 目的 | idp-server 動作検証 | ノードスケーリング学習 |
| スケーリング | HPA（Pod 水平） | Karpenter（ノード水平） |
| 実ワークロード | idp-server | デモ Pod |
| 同時実行 | 可能（別クラスタ） | 可能（別クラスタ） |

## トラブルシューティング

### `yq` not found

```bash
# macOS
brew install yq

# or
go install github.com/mikefarah/yq/v4@latest
```

### KWOK ノードが作成されない

1. Karpenter コントローラーのログを確認:
   ```bash
   kubectl -n kube-system logs -l app.kubernetes.io/name=karpenter -f
   ```
2. NodePool が正しく適用されているか確認:
   ```bash
   kubectl get nodepool
   ```
3. コントロールプレーンの taint が設定されているか確認:
   ```bash
   kubectl describe node | grep -A5 Taints
   ```

## Karpenter バージョン

デフォルトで `v1.8.2` を使用。変更する場合は `setup.sh` の `KARPENTER_VERSION` を編集する。

## 参考リンク

- [Karpenter 公式ドキュメント](https://karpenter.sh/)
- [KWOK プロジェクト](https://kwok.sigs.k8s.io/)
- [kubernetes-sigs/karpenter KWOK プロバイダー](https://github.com/kubernetes-sigs/karpenter/tree/main/kwok)
- [NodePool コンセプト](https://karpenter.sh/docs/concepts/nodepools/)
