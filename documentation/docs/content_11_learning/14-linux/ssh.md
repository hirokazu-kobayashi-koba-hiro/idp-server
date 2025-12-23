# SSH

SSHによるリモート接続、鍵認証、トンネリングの基礎を学びます。

---

## 目次

1. [SSHとは](#sshとは)
2. [基本的な接続](#基本的な接続)
3. [鍵認証](#鍵認証)
4. [SSH設定](#ssh設定)
5. [ポートフォワーディング](#ポートフォワーディング)
6. [実践的な使い方](#実践的な使い方)

---

## SSHとは

### 概要

**SSH（Secure Shell）は、ネットワーク経由で安全にリモートマシンに接続するためのプロトコル**です。

```
┌─────────────────────────────────────────────────────────────┐
│                      SSHの仕組み                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────┐                      ┌────────────┐        │
│  │  クライアント │                      │   サーバー  │        │
│  │  (自分のPC)  │                      │ (リモート)  │        │
│  └──────┬─────┘                      └──────┬─────┘        │
│         │                                    │              │
│         │  1. 接続要求 (TCP 22番ポート)      │              │
│         │ ─────────────────────────────────► │              │
│         │                                    │              │
│         │  2. サーバーの公開鍵を送信          │              │
│         │ ◄───────────────────────────────── │              │
│         │                                    │              │
│         │  3. 暗号化された通信路を確立        │              │
│         │ ◄────────────────────────────────► │              │
│         │                                    │              │
│         │  4. 認証（パスワード or 鍵）        │              │
│         │ ─────────────────────────────────► │              │
│         │                                    │              │
│         │  5. シェルセッション開始            │              │
│         │ ◄────────────────────────────────► │              │
│         │                                    │              │
│         │     すべての通信が暗号化される       │              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### なぜSSHが必要か

```
┌─────────────────────────────────────────────────────────────┐
│                SSHが解決する問題                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【昔: telnet, rsh】                                        │
│  - 通信が平文（暗号化されていない）                          │
│  - パスワードが盗聴される可能性                              │
│  - なりすましが可能                                         │
│                                                              │
│  【今: SSH】                                                │
│  - すべての通信が暗号化                                     │
│  - 鍵認証でパスワード不要                                   │
│  - サーバーの正当性を検証                                   │
│  - ポートフォワーディングで他のサービスも保護               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### SSHの用途

| 用途 | 説明 |
|------|------|
| リモートログイン | サーバーにログインしてコマンド実行 |
| ファイル転送 | scp, sftp でファイルをコピー |
| ポートフォワーディング | トンネリングで他のサービスを保護 |
| Git | GitHub/GitLabへのセキュアな接続 |
| 踏み台 | 多段SSH、ProxyJump |

---

## 基本的な接続

### 接続コマンド

```bash
# 基本形
ssh username@hostname

# 例
ssh user@192.168.1.100
ssh ubuntu@ec2-xx-xx-xx-xx.compute.amazonaws.com

# ポート指定（デフォルトは22）
ssh -p 2222 user@hostname

# 詳細出力（デバッグ用）
ssh -v user@hostname
ssh -vv user@hostname   # より詳細
```

### 初回接続時の確認

```
┌─────────────────────────────────────────────────────────────┐
│                 初回接続時のフィンガープリント                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  $ ssh user@server.example.com                              │
│                                                              │
│  The authenticity of host 'server.example.com' can't be     │
│  established.                                                │
│  ED25519 key fingerprint is SHA256:xxxxxxxxxxxxxxxxxxx.     │
│  Are you sure you want to continue connecting (yes/no)?     │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  これは「このサーバーを信頼しますか？」という確認     │    │
│  │                                                     │    │
│  │  - 初回のみ表示される                               │    │
│  │  - yes と答えると ~/.ssh/known_hosts に記録         │    │
│  │  - 次回からは確認なしで接続                         │    │
│  │  - サーバーが変わると警告が出る（中間者攻撃の可能性）│    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### コマンドの実行

```bash
# リモートでコマンドを実行して終了
ssh user@hostname "ls -la"
ssh user@hostname "df -h && free -m"

# 複数コマンド
ssh user@hostname << 'EOF'
cd /var/log
tail -100 syslog
EOF
```

---

## 鍵認証

### なぜ鍵認証を使うのか

```
┌─────────────────────────────────────────────────────────────┐
│              パスワード認証 vs 鍵認証                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【パスワード認証】                                          │
│  - 簡単だが安全性が低い                                     │
│  - ブルートフォース攻撃のリスク                             │
│  - 毎回入力が必要                                           │
│  - パスワードがサーバーに送信される                         │
│                                                              │
│  【鍵認証】                                                  │
│  - 非常に安全（事実上解読不可能）                           │
│  - パスワードを送信しない                                   │
│  - 自動化が容易                                             │
│  - 鍵を持っていないと接続できない                           │
│                                                              │
│                                                              │
│  仕組み:                                                    │
│  ┌───────────────┐              ┌───────────────┐          │
│  │  秘密鍵        │              │  公開鍵        │          │
│  │  (自分だけ)    │              │  (サーバーに)  │          │
│  │  ~/.ssh/id_ed25519           │  ~/.ssh/authorized_keys  │
│  └───────────────┘              └───────────────┘          │
│         │                              │                    │
│         │  秘密鍵で署名 ──────────────► 公開鍵で検証        │
│         │                              │                    │
│         └──────────────────────────────┘                    │
│           秘密鍵を持っている = 本人と証明                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 鍵の生成

```bash
# ED25519（推奨）
ssh-keygen -t ed25519 -C "your_email@example.com"

# RSA（互換性が必要な場合）
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"

# 対話形式
# Generating public/private ed25519 key pair.
# Enter file in which to save the key (/home/user/.ssh/id_ed25519): [Enter]
# Enter passphrase (empty for no passphrase): [パスフレーズを入力]
# Enter same passphrase again: [再入力]
```

```
┌─────────────────────────────────────────────────────────────┐
│                    鍵の種類                                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ED25519（推奨）                                             │
│  - 最新の楕円曲線暗号                                       │
│  - 高速で安全                                               │
│  - 鍵が短い                                                 │
│                                                              │
│  RSA                                                        │
│  - 広くサポートされている                                   │
│  - 4096ビット以上を推奨                                     │
│  - 古いシステムとの互換性                                   │
│                                                              │
│  ECDSA                                                      │
│  - 楕円曲線暗号                                             │
│  - ED25519の方が推奨                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 公開鍵をサーバーに登録

```bash
# 方法1: ssh-copy-id（推奨）
ssh-copy-id user@hostname
ssh-copy-id -i ~/.ssh/id_ed25519.pub user@hostname

# 方法2: 手動でコピー
cat ~/.ssh/id_ed25519.pub | ssh user@hostname "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"

# 方法3: 公開鍵の内容をコピペ
cat ~/.ssh/id_ed25519.pub
# 出力をサーバーの ~/.ssh/authorized_keys に追記
```

### 鍵ファイルのパーミッション

```bash
# 正しいパーミッション（重要！）
chmod 700 ~/.ssh
chmod 600 ~/.ssh/id_ed25519        # 秘密鍵
chmod 644 ~/.ssh/id_ed25519.pub    # 公開鍵
chmod 600 ~/.ssh/authorized_keys
chmod 644 ~/.ssh/known_hosts
chmod 600 ~/.ssh/config
```

```
┌─────────────────────────────────────────────────────────────┐
│                パーミッションエラー                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  よくあるエラー:                                             │
│  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@   │
│  @         WARNING: UNPROTECTED PRIVATE KEY FILE!          │
│  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@   │
│  Permissions 0644 for '/home/user/.ssh/id_ed25519' are too  │
│  open.                                                       │
│                                                              │
│  原因: 秘密鍵が他のユーザーから読める状態になっている        │
│  解決: chmod 600 ~/.ssh/id_ed25519                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ssh-agent

パスフレーズを毎回入力しないための仕組みです。

```bash
# ssh-agent を起動
eval "$(ssh-agent -s)"

# 鍵を登録
ssh-add ~/.ssh/id_ed25519

# 登録されている鍵を確認
ssh-add -l

# macOS: キーチェーンに保存
ssh-add --apple-use-keychain ~/.ssh/id_ed25519
```

---

## SSH設定

### ~/.ssh/config

接続設定をファイルに保存して、簡単に接続できるようにします。

```bash
# ~/.ssh/config

# 基本的な設定
Host myserver
    HostName 192.168.1.100
    User ubuntu
    Port 22
    IdentityFile ~/.ssh/id_ed25519

# AWS EC2
Host aws-prod
    HostName ec2-xx-xx-xx-xx.ap-northeast-1.compute.amazonaws.com
    User ec2-user
    IdentityFile ~/.ssh/aws-key.pem

# 踏み台経由（ProxyJump）
Host internal-server
    HostName 10.0.0.100
    User admin
    ProxyJump bastion

Host bastion
    HostName bastion.example.com
    User ubuntu
    IdentityFile ~/.ssh/bastion-key.pem

# ワイルドカード
Host *.example.com
    User deploy
    IdentityFile ~/.ssh/deploy-key

# 全ホスト共通設定
Host *
    ServerAliveInterval 60
    ServerAliveCountMax 3
    AddKeysToAgent yes
```

### 使用例

```bash
# 設定後は短い名前で接続可能
ssh myserver
ssh aws-prod
ssh internal-server   # 自動的に踏み台経由

# scp も同様
scp file.txt myserver:/tmp/
```

### 主な設定オプション

| オプション | 説明 |
|-----------|------|
| HostName | 実際のホスト名/IP |
| User | ユーザー名 |
| Port | ポート番号 |
| IdentityFile | 秘密鍵のパス |
| ProxyJump | 踏み台サーバー |
| ServerAliveInterval | キープアライブ間隔（秒） |
| ForwardAgent | ssh-agent転送 |
| LocalForward | ローカルポートフォワード |

---

## ポートフォワーディング

### ローカルポートフォワーディング

ローカルのポートをリモートサーバー経由で転送します。

```
┌─────────────────────────────────────────────────────────────┐
│              ローカルポートフォワーディング                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ssh -L 8080:localhost:80 user@server                       │
│                                                              │
│  ┌──────────┐          ┌──────────┐          ┌──────────┐ │
│  │ ブラウザ │          │  SSH     │          │ Webサーバー│ │
│  │          │          │ トンネル  │          │          │ │
│  └────┬─────┘          └────┬─────┘          └────┬─────┘ │
│       │                     │                     │        │
│       │ localhost:8080      │                     │        │
│       │ ──────────────────► │ ────────────────► │        │
│       │                     │   localhost:80     │        │
│       │                     │                     │        │
│                                                              │
│  使用例:                                                    │
│  - リモートのWebサーバーにアクセス                          │
│  - ファイアウォール内のサービスにアクセス                   │
│  - データベースに安全に接続                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```bash
# 構文: ssh -L [ローカルポート]:[リモートホスト]:[リモートポート] user@sshserver

# リモートのWebサーバーにアクセス
ssh -L 8080:localhost:80 user@server
# → http://localhost:8080 でアクセス

# リモートのMySQLに接続
ssh -L 3306:localhost:3306 user@server
# → mysql -h 127.0.0.1 -P 3306 で接続

# リモートネットワーク内の別サーバーにアクセス
ssh -L 5432:db-server:5432 user@bastion
# → psql -h localhost -p 5432 で db-server に接続

# バックグラウンドで実行
ssh -fNL 8080:localhost:80 user@server
# -f: バックグラウンド
# -N: コマンド実行しない
```

### リモートポートフォワーディング

リモートのポートをローカルに転送します（逆方向）。

```
┌─────────────────────────────────────────────────────────────┐
│              リモートポートフォワーディング                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ssh -R 8080:localhost:3000 user@server                     │
│                                                              │
│  ┌──────────┐          ┌──────────┐          ┌──────────┐ │
│  │ ローカル │          │  SSH     │          │ リモート  │ │
│  │ 開発サーバー│          │ トンネル  │          │ サーバー  │ │
│  │ :3000    │          │          │          │ :8080    │ │
│  └────┬─────┘          └────┬─────┘          └────┬─────┘ │
│       │                     │                     │        │
│       │ ◄─────────────────  │ ◄────────────────  │        │
│       │   localhost:3000    │    server:8080     │        │
│       │                     │                     │        │
│                                                              │
│  使用例:                                                    │
│  - ローカル開発サーバーを外部に公開                         │
│  - NAT内のマシンにアクセス                                  │
│  - Webhook のテスト                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```bash
# 構文: ssh -R [リモートポート]:[ローカルホスト]:[ローカルポート] user@server

# ローカルの開発サーバーをリモートから見れるようにする
ssh -R 8080:localhost:3000 user@server
# → server:8080 にアクセスすると localhost:3000 に転送
```

### ダイナミックポートフォワーディング（SOCKSプロキシ）

```bash
# SOCKSプロキシとして使用
ssh -D 1080 user@server

# ブラウザのプロキシ設定で SOCKS5 localhost:1080 を指定
# → すべての通信がSSHサーバー経由になる
```

---

## 実践的な使い方

### ファイル転送

```bash
# scp でファイルコピー
scp file.txt user@server:/tmp/
scp user@server:/var/log/app.log ./
scp -r directory/ user@server:/home/user/

# rsync（大量のファイル、差分転送）
rsync -avz ./src/ user@server:/var/www/
rsync -avz --delete ./src/ user@server:/var/www/  # 削除も同期

# sftp（対話的）
sftp user@server
sftp> put file.txt
sftp> get remote-file.txt
sftp> ls
sftp> cd /var/log
sftp> exit
```

### 踏み台サーバー（多段SSH）

```bash
# ProxyJump（推奨）
ssh -J bastion user@internal-server

# ~/.ssh/config での設定
Host internal-*
    ProxyJump bastion

# 古い方法（ProxyCommand）
ssh -o ProxyCommand="ssh -W %h:%p bastion" user@internal-server
```

```
┌─────────────────────────────────────────────────────────────┐
│                    踏み台サーバー                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  インターネット     DMZ          内部ネットワーク            │
│                                                              │
│  ┌──────┐      ┌──────┐       ┌──────────┐               │
│  │ 自分  │ ──► │踏み台 │ ──►  │内部サーバー│               │
│  │      │      │(公開) │       │ (非公開)  │               │
│  └──────┘      └──────┘       └──────────┘               │
│                                                              │
│  内部サーバーは直接アクセスできない                         │
│  踏み台を経由することでセキュリティを確保                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### GitHubへのSSH接続

```bash
# 鍵を生成
ssh-keygen -t ed25519 -C "your_email@example.com"

# 公開鍵をGitHubに登録
cat ~/.ssh/id_ed25519.pub
# → GitHub Settings > SSH and GPG keys > New SSH key

# 接続テスト
ssh -T git@github.com
# Hi username! You've successfully authenticated...

# ~/.ssh/config
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519
```

### Docker / Kubernetes での使用

```bash
# Docker ホストにSSH接続
docker context create remote --docker "host=ssh://user@remote-host"
docker context use remote

# Kubernetes クラスターへのポートフォワード
ssh -L 6443:localhost:6443 user@k8s-master
# → kubectl がローカルから操作可能に
```

### トラブルシューティング

```bash
# 詳細ログを出力
ssh -vvv user@server

# よくある問題と解決策
```

| 問題 | 原因 | 解決策 |
|------|------|--------|
| Permission denied | 鍵が登録されていない | ssh-copy-id で登録 |
| Connection refused | sshd が起動していない | systemctl start sshd |
| Connection timed out | ファイアウォール | ポート22を開放 |
| Host key verification failed | サーバーが変わった | known_hosts から削除 |

```bash
# known_hosts からホストを削除
ssh-keygen -R hostname

# パーミッションの確認
ls -la ~/.ssh/

# sshd の設定確認（サーバー側）
sudo sshd -t  # 設定テスト
sudo cat /etc/ssh/sshd_config
```

---

## まとめ

### 重要なコマンド

| コマンド | 説明 |
|---------|------|
| ssh user@host | リモート接続 |
| ssh-keygen | 鍵ペアを生成 |
| ssh-copy-id | 公開鍵をサーバーに登録 |
| ssh-add | ssh-agentに鍵を追加 |
| scp | ファイルをコピー |
| ssh -L | ローカルポートフォワード |
| ssh -J | 踏み台経由で接続 |

### ベストプラクティス

- パスワード認証より鍵認証を使う
- ED25519 鍵を使う（RSAより安全で高速）
- パスフレーズを設定する
- ~/.ssh/config を活用する
- 秘密鍵のパーミッションは 600

### 次のステップ

- [ユーザーと権限](users-permissions.md) - パーミッション管理
- [ネットワーク基礎](networking-basics.md) - TCP/IP
- [iptables](iptables-firewall.md) - ファイアウォール

---

## 参考リソース

- [OpenSSH Manual](https://www.openssh.com/manual.html)
- [SSH Academy](https://www.ssh.com/academy/ssh)
- [GitHub SSH Documentation](https://docs.github.com/en/authentication/connecting-to-github-with-ssh)
