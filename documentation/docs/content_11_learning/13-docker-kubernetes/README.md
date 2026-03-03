# コンテナ/Kubernetes 学習ガイド

このディレクトリには、DockerとKubernetesに関する学習ドキュメントが含まれています。コンテナ技術の基礎から本番運用まで体系的に学べます。

---

## 目次

### Docker基礎

| ドキュメント | 内容 |
|-------------|------|
| [container-basics.md](container-basics.md) | コンテナ技術の基礎、VMとの比較、Docker概念 |
| [dockerfile-best-practices.md](dockerfile-best-practices.md) | Dockerfileの書き方、マルチステージビルド、セキュリティ |
| [docker-compose.md](docker-compose.md) | Docker Composeによる複数コンテナ管理 |
| [docker-commands.md](docker-commands.md) | Dockerコマンドリファレンス |

---

### Kubernetes基礎

| ドキュメント | 内容 |
|-------------|------|
| [kubernetes-architecture.md](kubernetes-architecture.md) | K8sアーキテクチャ、コントロールプレーン、ワーカーノード |
| [kubernetes-workloads.md](kubernetes-workloads.md) | Pod、Deployment、StatefulSet、Job |
| [kubernetes-networking.md](kubernetes-networking.md) | Service、Ingress、NetworkPolicy |
| [kubernetes-storage.md](kubernetes-storage.md) | PersistentVolume、PVC、StorageClass |

---

### Kubernetes運用

| ドキュメント | 内容 |
|-------------|------|
| [kubernetes-configuration.md](kubernetes-configuration.md) | ConfigMap、Secret、環境変数 |
| [kubernetes-scaling.md](kubernetes-scaling.md) | HPA、VPA、Cluster Autoscaler |
| [kubernetes-observability.md](kubernetes-observability.md) | ログ、メトリクス、トレーシング |
| [kubernetes-security.md](kubernetes-security.md) | RBAC、PodSecurity、Secrets管理 |

---

### コマンドリファレンス

| ドキュメント | 内容 |
|-------------|------|
| [docker-commands.md](docker-commands.md) | よく使うDockerコマンド集 |
| [kubectl-commands.md](kubectl-commands.md) | よく使うkubectlコマンド集 |

---

## 学習パス

### 初心者（コンテナ入門）

Docker の基本概念を学びます。

1. **container-basics.md** - コンテナとは何か
2. **dockerfile-best-practices.md** - イメージの作り方
3. **docker-compose.md** - 複数コンテナの管理
4. **docker-commands.md** - 基本コマンドを習得

### 中級者（Kubernetes入門）

Kubernetesの基本を学びます。

1. **kubernetes-architecture.md** - K8sの構造を理解
2. **kubernetes-workloads.md** - アプリのデプロイ方法
3. **kubernetes-networking.md** - ネットワーク構成
4. **kubernetes-storage.md** - データの永続化
5. **kubectl-commands.md** - kubectlコマンドを習得

### 上級者（本番運用）

本番環境での運用を学びます。

1. **kubernetes-configuration.md** - 設定管理のベストプラクティス
2. **kubernetes-scaling.md** - オートスケーリング
3. **kubernetes-observability.md** - 監視と可観測性
4. **kubernetes-security.md** - セキュリティ強化

### IDサービス運用者

IDサービスのデプロイと運用に必要な知識を重点的に学びます。

1. **dockerfile-best-practices.md** - Spring Bootアプリのコンテナ化
2. **kubernetes-workloads.md** - Deploymentの設定
3. **kubernetes-configuration.md** - ConfigMap/Secretの管理
4. **kubernetes-scaling.md** - HPAの設定
5. **kubernetes-security.md** - 本番セキュリティ

---

## 関連ドキュメント

- [商用デプロイガイド](../../content_08_ops/commercial-deployment/) - 本番環境へのデプロイ
- [パフォーマンステスト](../../content_08_ops/ops-02-performance-test.md) - 負荷テスト

---

## 関連リソース

### Docker
- [Docker Documentation](https://docs.docker.com/)
- [Dockerfile reference](https://docs.docker.com/engine/reference/dockerfile/)
- [Docker Compose](https://docs.docker.com/compose/)

### Kubernetes
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
- [Kubernetes Patterns](https://k8spatterns.io/)

### マネージドKubernetes
- [Amazon EKS](https://docs.aws.amazon.com/eks/)
- [Google GKE](https://cloud.google.com/kubernetes-engine/docs)
- [Azure AKS](https://docs.microsoft.com/azure/aks/)

### ツール
- [Helm](https://helm.sh/) - Kubernetesパッケージマネージャー
- [Kustomize](https://kustomize.io/) - マニフェスト管理
- [Lens](https://k8slens.dev/) - Kubernetes IDE
- [k9s](https://k9scli.io/) - ターミナルUI
