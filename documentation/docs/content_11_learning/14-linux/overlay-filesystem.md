# OverlayFS

コンテナイメージのレイヤー構造を実現するOverlayFSについて学びます。

---

## 目次

1. [OverlayFSとは](#overlayfsとは)
2. [レイヤーの仕組み](#レイヤーの仕組み)
3. [Copy-on-Write](#copy-on-write)
4. [OverlayFSの操作](#overlayfsの操作)
5. [Dockerとレイヤー](#dockerとレイヤー)
6. [ストレージドライバ](#ストレージドライバ)

---

## OverlayFSとは

### OverlayFSの概念

```
┌─────────────────────────────────────────────────────────────┐
│                   OverlayFS の構造                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ユーザーから見える統合ビュー (merged)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  /merged                                             │   │
│  │  ├── file1.txt  (from lower)                        │   │
│  │  ├── file2.txt  (from lower, modified in upper)     │   │
│  │  ├── file3.txt  (new in upper)                      │   │
│  │  └── dir/                                           │   │
│  └─────────────────────────────────────────────────────┘   │
│         ▲                                                   │
│         │ 重ね合わせ                                        │
│  ┌──────┴──────┐                                           │
│  │             │                                            │
│  ▼             ▼                                            │
│  Upper (RW)    Lower (RO)                                   │
│  ┌───────────┐ ┌───────────┐                               │
│  │ file2.txt │ │ file1.txt │                               │
│  │ file3.txt │ │ file2.txt │                               │
│  │ .wh.file4 │ │ file4.txt │ ← whiteout で削除扱い        │
│  └───────────┘ │ dir/      │                               │
│                └───────────┘                               │
│                                                              │
│  Upper: 読み書き可能（変更がここに書き込まれる）             │
│  Lower: 読み取り専用（元のデータ、複数レイヤー可）           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### なぜOverlayFSが重要か

| 利点 | 説明 |
|-----|------|
| 効率的なストレージ | ベースイメージを共有できる |
| 高速な起動 | イメージ全体をコピー不要 |
| レイヤーキャッシュ | 変更がないレイヤーは再利用 |
| イミュータブル | 下位レイヤーは変更されない |

---

## レイヤーの仕組み

### コンテナイメージのレイヤー

```
┌─────────────────────────────────────────────────────────────┐
│                 Docker イメージのレイヤー                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Dockerfile:                                                 │
│  FROM ubuntu:22.04                    → Layer 1 (Base)      │
│  RUN apt-get update                   → Layer 2             │
│  RUN apt-get install -y nginx         → Layer 3             │
│  COPY app/ /var/www/html/             → Layer 4             │
│                                                              │
│  イメージ構造:                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Layer 4: /var/www/html/ (app files)                │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │  Layer 3: /usr/sbin/nginx, /etc/nginx/...           │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │  Layer 2: /var/lib/apt/... (updates)                │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │  Layer 1: Ubuntu base (/, /bin, /lib, ...)          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  各レイヤーは独立したtarアーカイブ                           │
│  同じベースを使うイメージはLayer 1を共有                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### レイヤーの共有

```
┌─────────────────────────────────────────────────────────────┐
│                  レイヤーの共有と効率化                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Image A          Image B          Image C                  │
│  ┌───────┐       ┌───────┐       ┌───────┐                │
│  │Layer 4│       │Layer 4│       │Layer 3│                │
│  │(App A)│       │(App B)│       │(App C)│                │
│  └───┬───┘       └───┬───┘       └───┬───┘                │
│      │               │               │                     │
│      └───────┬───────┘               │                     │
│              │                       │                     │
│         ┌────┴────┐                  │                     │
│         │ Layer 3 │                  │                     │
│         │ (nginx) │◄─────────────────┘                     │
│         └────┬────┘                                        │
│              │                                              │
│         ┌────┴────┐                                        │
│         │ Layer 2 │                                        │
│         │(apt upd)│                                        │
│         └────┬────┘                                        │
│              │                                              │
│         ┌────┴────┐                                        │
│         │ Layer 1 │                                        │
│         │(ubuntu) │  ← すべてのイメージで共有               │
│         └─────────┘                                        │
│                                                              │
│  ディスク使用量 = Layer1 + Layer2 + Layer3 + LayerA + B + C │
│  （共有レイヤーは1回分のみ）                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Copy-on-Write

### CoWの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                   Copy-on-Write (CoW)                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 読み取り（下位レイヤーから）                             │
│     ┌─────────┐                                            │
│     │ Upper   │  ← ファイルなし                            │
│     ├─────────┤                                            │
│     │ Lower   │  ← file.txt を読む                         │
│     └─────────┘                                            │
│                                                              │
│  2. 書き込み（コピーしてから修正）                           │
│     ┌─────────┐                                            │
│     │ Upper   │  ← file.txt をコピー＆修正                 │
│     ├─────────┤                                            │
│     │ Lower   │  ← 元の file.txt は変更されない            │
│     └─────────┘                                            │
│                                                              │
│  3. 削除（whiteoutファイル作成）                             │
│     ┌─────────┐                                            │
│     │ Upper   │  ← .wh.file.txt を作成                     │
│     ├─────────┤     （削除マーカー）                        │
│     │ Lower   │  ← file.txt は残っている                   │
│     └─────────┘                                            │
│                                                              │
│  Merged ビューでは file.txt は見えない                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### CoWの利点と注意点

| 項目 | 内容 |
|-----|------|
| 利点 | 下位レイヤーが変更されない（イミュータブル） |
| 利点 | 起動が高速（コピー不要） |
| 注意点 | 大きなファイルの編集はコピーが発生 |
| 注意点 | 頻繁な書き込みはUpperに蓄積 |

---

## OverlayFSの操作

### 基本的なマウント

```bash
# ディレクトリ準備
mkdir -p /tmp/overlay/{lower,upper,work,merged}

# lowerに初期ファイル作成
echo "original content" > /tmp/overlay/lower/file.txt
echo "another file" > /tmp/overlay/lower/another.txt

# OverlayFSマウント
sudo mount -t overlay overlay \
  -o lowerdir=/tmp/overlay/lower,upperdir=/tmp/overlay/upper,workdir=/tmp/overlay/work \
  /tmp/overlay/merged

# 統合ビュー確認
ls /tmp/overlay/merged
# file.txt  another.txt

cat /tmp/overlay/merged/file.txt
# original content
```

### 書き込み操作

```bash
# ファイル修正（CoW発生）
echo "modified content" > /tmp/overlay/merged/file.txt

# upperにコピーされている
ls /tmp/overlay/upper
# file.txt

cat /tmp/overlay/upper/file.txt
# modified content

# lowerは変更されていない
cat /tmp/overlay/lower/file.txt
# original content

# 新規ファイル作成
echo "new file" > /tmp/overlay/merged/new.txt

# upperに作成される
ls /tmp/overlay/upper
# file.txt  new.txt
```

### 削除操作（whiteout）

```bash
# ファイル削除
rm /tmp/overlay/merged/another.txt

# upperにwhiteoutファイル
ls -la /tmp/overlay/upper
# c--------- .wh.another.txt

# mergedでは見えない
ls /tmp/overlay/merged
# file.txt  new.txt

# lowerには残っている
ls /tmp/overlay/lower
# another.txt  file.txt
```

### 複数レイヤー

```bash
# 複数のlowerレイヤー
mkdir -p /tmp/overlay/{lower1,lower2,lower3}

echo "base" > /tmp/overlay/lower1/file.txt
echo "override" > /tmp/overlay/lower2/file.txt
touch /tmp/overlay/lower3/new.txt

# マウント（右が下位）
sudo mount -t overlay overlay \
  -o lowerdir=/tmp/overlay/lower3:/tmp/overlay/lower2:/tmp/overlay/lower1,upperdir=/tmp/overlay/upper,workdir=/tmp/overlay/work \
  /tmp/overlay/merged

# lower2のfileが見える（上位が優先）
cat /tmp/overlay/merged/file.txt
# override
```

### クリーンアップ

```bash
# アンマウント
sudo umount /tmp/overlay/merged

# ディレクトリ削除
rm -rf /tmp/overlay
```

---

## Dockerとレイヤー

### イメージレイヤーの確認

```bash
# イメージのレイヤー情報
docker history nginx

# 出力例
# IMAGE          CREATED       CREATED BY                                      SIZE
# 605c77e624dd   2 weeks ago   CMD ["nginx" "-g" "daemon off;"]                0B
# <missing>      2 weeks ago   STOPSIGNAL SIGQUIT                              0B
# <missing>      2 weeks ago   EXPOSE 80                                       0B
# <missing>      2 weeks ago   ENTRYPOINT ["/docker-entrypoint.sh"]            0B
# <missing>      2 weeks ago   COPY 30-tune-worker-processes.sh /docker-ent…   4.62kB
# <missing>      2 weeks ago   COPY 20-envsubst-on-templates.sh /docker-ent…   3.02kB
# <missing>      2 weeks ago   COPY 15-local-resolvers.envsh /docker-entryp…   298B
# <missing>      2 weeks ago   COPY 10-listen-on-ipv6-by-default.sh /docker…   2.12kB
# ...

# 詳細情報
docker inspect nginx | jq '.[0].RootFS'
```

### コンテナレイヤー

```bash
# コンテナ起動
docker run -d --name test nginx

# コンテナのマウント情報
docker inspect test | jq '.[0].GraphDriver'

# 出力例（overlay2）
# {
#   "Data": {
#     "LowerDir": "/var/lib/docker/overlay2/xxx/diff:/var/lib/docker/overlay2/yyy/diff:...",
#     "MergedDir": "/var/lib/docker/overlay2/zzz/merged",
#     "UpperDir": "/var/lib/docker/overlay2/zzz/diff",
#     "WorkDir": "/var/lib/docker/overlay2/zzz/work"
#   },
#   "Name": "overlay2"
# }

# コンテナ内でファイル変更
docker exec test touch /tmp/newfile
docker exec test rm /etc/nginx/nginx.conf

# UpperDirに変更が記録される
sudo ls /var/lib/docker/overlay2/zzz/diff/tmp
# newfile

sudo ls -la /var/lib/docker/overlay2/zzz/diff/etc/nginx/
# c--------- .wh.nginx.conf  ← whiteout

# クリーンアップ
docker rm -f test
```

### レイヤーのディスク使用量

```bash
# イメージのサイズ
docker images

# 詳細なディスク使用量
docker system df
docker system df -v

# レイヤーの場所
sudo du -sh /var/lib/docker/overlay2/*
```

---

## ストレージドライバ

### 主なストレージドライバ

| ドライバ | 説明 | 推奨環境 |
|---------|------|---------|
| overlay2 | OverlayFS使用、推奨 | Linux 4.0+ |
| devicemapper | ブロックレベル | RHEL/CentOS（古い） |
| btrfs | Btrfsスナップショット | Btrfsファイルシステム |
| zfs | ZFSクローン | ZFSファイルシステム |
| vfs | コピー（非効率） | テスト用 |

### ストレージドライバの確認

```bash
# 現在のストレージドライバ
docker info | grep "Storage Driver"
# Storage Driver: overlay2

# Docker設定で指定
cat /etc/docker/daemon.json
# {
#   "storage-driver": "overlay2"
# }
```

### パフォーマンス最適化

```bash
# レイヤー数を減らす（マルチステージビルド）
# 悪い例
FROM ubuntu
RUN apt-get update
RUN apt-get install -y nginx
RUN apt-get clean

# 良い例
FROM ubuntu
RUN apt-get update && \
    apt-get install -y nginx && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# ボリュームを使用（頻繁な書き込み）
docker run -v /data:/app/data myapp

# tmpfsを使用（一時ファイル）
docker run --tmpfs /tmp myapp
```

---

## まとめ

### OverlayFSの構成要素

| 要素 | 説明 |
|-----|------|
| Lower | 読み取り専用レイヤー（複数可） |
| Upper | 読み書き可能レイヤー |
| Work | 作業用ディレクトリ |
| Merged | 統合ビュー |

### 重要なポイント

- コンテナイメージはレイヤーの積み重ね
- 書き込みはCopy-on-Writeで処理
- 削除はwhiteoutファイルで表現
- 同じベースイメージはレイヤーを共有
- 頻繁な書き込みにはボリュームを使用

### 次のステップ

- [Namespaces](namespaces.md) - プロセスの分離
- [Cgroups](cgroups.md) - リソース制限
- [Docker基礎](../13-kubernetes/container-basics.md) - コンテナの基本

---

## 参考リソース

- [OverlayFS Documentation](https://docs.kernel.org/filesystems/overlayfs.html)
- [Docker Storage Drivers](https://docs.docker.com/storage/storagedriver/)
- [Deep Dive into Docker Overlay Networking](https://docs.docker.com/network/drivers/overlay/)
