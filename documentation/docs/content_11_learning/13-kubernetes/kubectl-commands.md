# kubectl コマンドリファレンス

日常的に使用するkubectlコマンドのクイックリファレンスです。

---

## 目次

1. [基本操作](#基本操作)
2. [リソース操作](#リソース操作)
3. [Pod操作](#pod操作)
4. [デバッグ](#デバッグ)
5. [設定管理](#設定管理)
6. [スケーリング](#スケーリング)
7. [ラベルとセレクター](#ラベルとセレクター)
8. [便利なワンライナー](#便利なワンライナー)

---

## 基本操作

### クラスター情報

```bash
# クラスター情報
kubectl cluster-info

# ノード一覧
kubectl get nodes
kubectl get nodes -o wide

# ノード詳細
kubectl describe node node-name

# コンポーネントの状態
kubectl get componentstatuses

# APIリソース一覧
kubectl api-resources

# APIバージョン
kubectl api-versions
```

### コンテキスト管理

```bash
# 現在のコンテキスト
kubectl config current-context

# コンテキスト一覧
kubectl config get-contexts

# コンテキスト切り替え
kubectl config use-context my-cluster

# Namespace設定
kubectl config set-context --current --namespace=production
```

---

## リソース操作

### 取得（get）

```bash
# 基本
kubectl get pods
kubectl get deployments
kubectl get services
kubectl get all

# Namespace指定
kubectl get pods -n production
kubectl get pods --all-namespaces
kubectl get pods -A  # 短縮形

# 出力形式
kubectl get pods -o wide          # 詳細情報
kubectl get pods -o yaml          # YAML形式
kubectl get pods -o json          # JSON形式
kubectl get pods -o name          # 名前のみ

# カスタム列
kubectl get pods -o custom-columns=NAME:.metadata.name,STATUS:.status.phase

# ソート
kubectl get pods --sort-by=.metadata.creationTimestamp
```

### 詳細（describe）

```bash
# リソースの詳細
kubectl describe pod pod-name
kubectl describe deployment deployment-name
kubectl describe node node-name

# イベントの確認
kubectl describe pod pod-name | grep -A 20 Events
```

### 作成・適用（create/apply）

```bash
# マニフェストから作成
kubectl create -f manifest.yaml

# 適用（作成または更新）
kubectl apply -f manifest.yaml
kubectl apply -f directory/
kubectl apply -f https://example.com/manifest.yaml

# ドライラン
kubectl apply -f manifest.yaml --dry-run=client
kubectl apply -f manifest.yaml --dry-run=server

# 差分確認
kubectl diff -f manifest.yaml
```

### 削除（delete）

```bash
# リソース削除
kubectl delete pod pod-name
kubectl delete -f manifest.yaml

# 強制削除
kubectl delete pod pod-name --force --grace-period=0

# ラベルセレクターで削除
kubectl delete pods -l app=my-app

# Namespace内の全Pod削除
kubectl delete pods --all -n my-namespace
```

### 編集（edit）

```bash
# エディタで編集
kubectl edit deployment my-deployment
KUBE_EDITOR="code --wait" kubectl edit deployment my-deployment
```

### パッチ（patch）

```bash
# JSONパッチ
kubectl patch deployment my-deployment -p '{"spec":{"replicas":5}}'

# 戦略的マージパッチ
kubectl patch deployment my-deployment --type=merge -p '{"spec":{"template":{"spec":{"containers":[{"name":"app","image":"new-image"}]}}}}'

# JSONパッチ（配列操作）
kubectl patch deployment my-deployment --type='json' -p='[{"op":"replace","path":"/spec/replicas","value":3}]'
```

---

## Pod操作

### ログ

```bash
# ログ表示
kubectl logs pod-name

# コンテナ指定
kubectl logs pod-name -c container-name

# フォロー
kubectl logs -f pod-name

# 末尾N行
kubectl logs --tail=100 pod-name

# 時間範囲
kubectl logs --since=1h pod-name
kubectl logs --since-time=2024-01-01T00:00:00Z pod-name

# 前のコンテナ
kubectl logs pod-name --previous

# 複数Pod
kubectl logs -l app=my-app --all-containers
```

### 実行（exec）

```bash
# コマンド実行
kubectl exec pod-name -- ls -la

# シェル接続
kubectl exec -it pod-name -- /bin/sh
kubectl exec -it pod-name -- /bin/bash

# コンテナ指定
kubectl exec -it pod-name -c container-name -- /bin/sh
```

### ポートフォワード

```bash
# ローカルポートをPodに転送
kubectl port-forward pod-name 8080:80

# Serviceに転送
kubectl port-forward svc/my-service 8080:80

# バックグラウンドで実行
kubectl port-forward pod-name 8080:80 &

# アドレス指定
kubectl port-forward --address 0.0.0.0 pod-name 8080:80
```

### ファイルコピー

```bash
# ローカル→Pod
kubectl cp local-file.txt pod-name:/path/in/container

# Pod→ローカル
kubectl cp pod-name:/path/in/container local-file.txt

# コンテナ指定
kubectl cp local-file.txt pod-name:/path -c container-name
```

---

## デバッグ

### リソース使用状況

```bash
# ノードのリソース
kubectl top nodes

# Podのリソース
kubectl top pods
kubectl top pods --containers
kubectl top pods -l app=my-app
```

### イベント

```bash
# イベント一覧
kubectl get events
kubectl get events --sort-by=.lastTimestamp

# Namespace指定
kubectl get events -n production

# 特定リソースのイベント
kubectl get events --field-selector involvedObject.name=pod-name
```

### デバッグコンテナ

```bash
# デバッグコンテナを追加
kubectl debug pod-name -it --image=busybox

# 既存コンテナをコピーしてデバッグ
kubectl debug pod-name -it --copy-to=debug-pod --container=my-container

# ノードにデバッグPodを作成
kubectl debug node/node-name -it --image=ubuntu
```

### トラブルシューティング

```bash
# Podが起動しない原因
kubectl describe pod pod-name
kubectl logs pod-name --previous

# Serviceに接続できない
kubectl get endpoints service-name
kubectl describe service service-name

# DNSの確認
kubectl run dns-test --image=busybox:1.28 --rm -it --restart=Never -- nslookup kubernetes
```

---

## 設定管理

### ConfigMap

```bash
# 作成
kubectl create configmap my-config \
  --from-literal=key1=value1 \
  --from-literal=key2=value2

# ファイルから
kubectl create configmap my-config --from-file=config.properties

# 確認
kubectl get configmap my-config -o yaml

# 値の取得
kubectl get configmap my-config -o jsonpath='{.data.key1}'
```

### Secret

```bash
# 作成
kubectl create secret generic my-secret \
  --from-literal=username=admin \
  --from-literal=password=secret

# TLS Secret
kubectl create secret tls my-tls \
  --cert=cert.pem \
  --key=key.pem

# 確認（Base64デコード）
kubectl get secret my-secret -o jsonpath='{.data.password}' | base64 -d
```

---

## スケーリング

### レプリカ数変更

```bash
# スケール
kubectl scale deployment my-deployment --replicas=5

# 条件付きスケール
kubectl scale deployment my-deployment --replicas=5 --current-replicas=3
```

### HPA

```bash
# HPA確認
kubectl get hpa
kubectl describe hpa my-hpa

# 手動作成
kubectl autoscale deployment my-deployment --min=2 --max=10 --cpu-percent=70
```

### ロールアウト

```bash
# ステータス
kubectl rollout status deployment/my-deployment

# 履歴
kubectl rollout history deployment/my-deployment
kubectl rollout history deployment/my-deployment --revision=2

# ロールバック
kubectl rollout undo deployment/my-deployment
kubectl rollout undo deployment/my-deployment --to-revision=2

# 一時停止/再開
kubectl rollout pause deployment/my-deployment
kubectl rollout resume deployment/my-deployment

# 再起動
kubectl rollout restart deployment/my-deployment
```

---

## ラベルとセレクター

### ラベル操作

```bash
# ラベル追加
kubectl label pod pod-name app=my-app

# ラベル更新
kubectl label pod pod-name app=new-app --overwrite

# ラベル削除
kubectl label pod pod-name app-

# ラベル確認
kubectl get pods --show-labels
```

### セレクター

```bash
# 等価
kubectl get pods -l app=my-app

# 不等価
kubectl get pods -l app!=my-app

# セット
kubectl get pods -l 'app in (app1, app2)'
kubectl get pods -l 'app notin (app1, app2)'

# 複数条件
kubectl get pods -l app=my-app,version=v1
```

---

## 便利なワンライナー

### 状態確認

```bash
# 全NamespaceのPod状態
kubectl get pods -A -o wide

# 問題のあるPod
kubectl get pods -A | grep -v Running
kubectl get pods -A --field-selector=status.phase!=Running

# 再起動が多いPod
kubectl get pods -A -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.containerStatuses[0].restartCount}{"\n"}{end}' | sort -k2 -rn

# リソース使用率が高いPod
kubectl top pods -A --sort-by=cpu
kubectl top pods -A --sort-by=memory
```

### クリーンアップ

```bash
# 完了したPodを削除
kubectl delete pods --field-selector=status.phase==Succeeded

# 失敗したPodを削除
kubectl delete pods --field-selector=status.phase==Failed

# Evictedなpodを削除
kubectl get pods -A | grep Evicted | awk '{print $2 " -n " $1}' | xargs -L1 kubectl delete pod
```

### 一括操作

```bash
# 全Deploymentを再起動
kubectl get deployments -o name | xargs -I {} kubectl rollout restart {}

# 全Podのイメージを確認
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].image}{"\n"}{end}'

# 特定イメージを使用するPodを検索
kubectl get pods -A -o jsonpath='{range .items[*]}{.metadata.namespace}{"\t"}{.metadata.name}{"\t"}{.spec.containers[*].image}{"\n"}{end}' | grep "image-name"
```

### JSONPath

```bash
# Pod IPアドレス
kubectl get pods -o jsonpath='{.items[*].status.podIP}'

# ノードのアドレス
kubectl get nodes -o jsonpath='{.items[*].status.addresses[?(@.type=="InternalIP")].address}'

# Secretのデコード
kubectl get secret my-secret -o jsonpath='{.data.password}' | base64 -d

# 特定条件のPod名
kubectl get pods -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}'
```

---

## クイックリファレンス表

### リソース操作

| コマンド | 説明 |
|---------|------|
| `kubectl get` | リソース一覧 |
| `kubectl describe` | リソース詳細 |
| `kubectl create` | リソース作成 |
| `kubectl apply` | 作成または更新 |
| `kubectl delete` | リソース削除 |
| `kubectl edit` | エディタで編集 |
| `kubectl patch` | 部分更新 |

### Pod操作

| コマンド | 説明 |
|---------|------|
| `kubectl logs` | ログ表示 |
| `kubectl exec` | コマンド実行 |
| `kubectl port-forward` | ポート転送 |
| `kubectl cp` | ファイルコピー |
| `kubectl debug` | デバッグコンテナ |

### デプロイメント

| コマンド | 説明 |
|---------|------|
| `kubectl scale` | スケール変更 |
| `kubectl rollout status` | ロールアウト状態 |
| `kubectl rollout undo` | ロールバック |
| `kubectl rollout restart` | 再起動 |

### 出力オプション

| オプション | 説明 |
|-----------|------|
| `-o wide` | 詳細情報 |
| `-o yaml` | YAML形式 |
| `-o json` | JSON形式 |
| `-o name` | 名前のみ |
| `-o jsonpath` | JSONPath |

---

## 参考リソース

- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
- [kubectl Reference](https://kubernetes.io/docs/reference/kubectl/)
- [JSONPath Support](https://kubernetes.io/docs/reference/kubectl/jsonpath/)
