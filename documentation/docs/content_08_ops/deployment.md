# デプロイ方法

`idp-server` は、Docker イメージとして動作するように設計されています。  
Dockerfile をビルドして得られる `.jar` 実行型アプリケーションを含んだイメージを、以下のような環境にデプロイして利用できます。

---

## ✅ 開発用途（推奨環境）

- Docker
- docker compose（例: PostgreSQL / Redis と組み合わせたローカル開発）

---

## 🚀 本番運用（推奨環境）

- Kubernetes (K8s)
- AWS ECS / GCP GKE / Azure AKS
- Docker Swarm（小規模用途）

---

## 🛠️ Dockerイメージのビルド手順

```bash
docker build -t idp-server:latest .
```

## 注意点

- 本番では Secrets や ConfigMap を使って環境変数やクレデンシャルを管理することを強く推奨します
- 永続化ストレージ（PostgreSQL/Redis等）は外部サービスを使用してください
