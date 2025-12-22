# systemd

Linuxのサービス管理システムであるsystemdについて学びます。

---

## 目次

1. [systemdとは](#systemdとは)
2. [systemctlコマンド](#systemctlコマンド)
3. [Unitファイル](#unitファイル)
4. [サービスの作成](#サービスの作成)
5. [ターゲットとランレベル](#ターゲットとランレベル)
6. [journalctlでログ確認](#journalctlでログ確認)
7. [コンテナとsystemd](#コンテナとsystemd)

---

## systemdとは

### systemdの概要

```
┌─────────────────────────────────────────────────────────────┐
│                    systemd の構成                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  カーネル起動                                                │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  systemd (PID 1)                                     │   │
│  │                                                      │   │
│  │  ・サービス管理 (起動、停止、再起動)                  │   │
│  │  ・依存関係の解決                                     │   │
│  │  ・並列起動による高速ブート                          │   │
│  │  ・ログ管理 (journald)                               │   │
│  │  ・ソケットアクティベーション                        │   │
│  │  ・タイマー (cron代替)                               │   │
│  │                                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│      │              │              │                        │
│      ▼              ▼              ▼                        │
│  ┌────────┐   ┌────────┐   ┌────────┐                     │
│  │ nginx  │   │ sshd   │   │postgres│                     │
│  │.service│   │.service│   │.service│                     │
│  └────────┘   └────────┘   └────────┘                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 従来のinitシステムとの比較

| 特徴 | SysVinit | systemd |
|------|----------|---------|
| 起動方式 | シーケンシャル | 並列 |
| 設定 | シェルスクリプト | Unitファイル |
| 依存関係 | 手動管理 | 自動解決 |
| ログ | 分散 | 統合(journald) |
| ソケット | 手動 | アクティベーション |

---

## systemctlコマンド

### サービスの操作

```bash
# サービスの状態確認
systemctl status nginx
systemctl status nginx.service

# サービスの起動・停止・再起動
sudo systemctl start nginx
sudo systemctl stop nginx
sudo systemctl restart nginx
sudo systemctl reload nginx      # 設定再読み込み（プロセス継続）

# 自動起動の有効化・無効化
sudo systemctl enable nginx      # 起動時に自動起動
sudo systemctl disable nginx     # 自動起動を無効化
sudo systemctl enable --now nginx  # 有効化と起動を同時に

# サービスが有効か確認
systemctl is-enabled nginx
systemctl is-active nginx
```

### サービス一覧

```bash
# すべてのサービス
systemctl list-units --type=service

# 実行中のサービスのみ
systemctl list-units --type=service --state=running

# 失敗したサービス
systemctl list-units --type=service --state=failed

# 有効なサービス（自動起動）
systemctl list-unit-files --type=service --state=enabled
```

### 依存関係の確認

```bash
# 依存しているUnit
systemctl list-dependencies nginx

# 逆依存（このUnitに依存しているもの）
systemctl list-dependencies nginx --reverse

# ツリー表示
systemctl list-dependencies --all nginx
```

### システム全体の操作

```bash
# システム再起動
sudo systemctl reboot

# シャットダウン
sudo systemctl poweroff

# サービスのデーモン再読み込み
sudo systemctl daemon-reload

# 全体の状態確認
systemctl status
```

---

## Unitファイル

### Unitの種類

| 種類 | 拡張子 | 説明 |
|-----|--------|------|
| Service | .service | デーモン、サービス |
| Socket | .socket | ソケットアクティベーション |
| Target | .target | Unitのグループ化 |
| Timer | .timer | スケジュール実行 |
| Mount | .mount | ファイルシステムマウント |
| Path | .path | パス監視 |

### Unitファイルの場所

```bash
# システムUnit（パッケージ提供）
/usr/lib/systemd/system/

# システムUnit（管理者設定）
/etc/systemd/system/

# ユーザーUnit
~/.config/systemd/user/

# Unitファイルの場所を確認
systemctl show nginx -p FragmentPath
```

### Unitファイルの構造

```ini
# /etc/systemd/system/myapp.service

[Unit]
Description=My Application Service
Documentation=https://example.com/docs
After=network.target postgresql.service
Requires=postgresql.service
Wants=redis.service

[Service]
Type=simple
User=appuser
Group=appgroup
WorkingDirectory=/opt/myapp
Environment=NODE_ENV=production
EnvironmentFile=/etc/myapp/env
ExecStart=/opt/myapp/bin/start
ExecStop=/opt/myapp/bin/stop
ExecReload=/bin/kill -HUP $MAINPID
Restart=on-failure
RestartSec=5
TimeoutStartSec=30
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
```

### セクションの説明

```
┌─────────────────────────────────────────────────────────────┐
│                 Unitファイルのセクション                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  [Unit] セクション                                           │
│  ├── Description  説明                                      │
│  ├── After        このUnit以降に起動                        │
│  ├── Before       このUnit以前に起動                        │
│  ├── Requires     必須の依存関係（失敗すると起動しない）     │
│  ├── Wants        推奨の依存関係（失敗しても起動）           │
│  └── Conflicts    排他関係                                  │
│                                                              │
│  [Service] セクション                                        │
│  ├── Type         simple, forking, oneshot, notify          │
│  ├── ExecStart    起動コマンド                              │
│  ├── ExecStop     停止コマンド                              │
│  ├── ExecReload   リロードコマンド                          │
│  ├── Restart      再起動条件 (always, on-failure, etc.)     │
│  ├── RestartSec   再起動までの待機時間                      │
│  ├── User/Group   実行ユーザー/グループ                     │
│  └── Environment  環境変数                                  │
│                                                              │
│  [Install] セクション                                        │
│  ├── WantedBy     enable時のターゲット                      │
│  └── RequiredBy   enable時の必須ターゲット                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Serviceのタイプ

| Type | 説明 |
|------|------|
| simple | デフォルト。ExecStartがメインプロセス |
| forking | デーモン化するプロセス（バックグラウンド） |
| oneshot | 起動して終了するタスク |
| notify | sd_notify()で準備完了を通知 |
| idle | 他のジョブ完了後に実行 |

---

## サービスの作成

### シンプルなサービス

```bash
# アプリケーション準備
sudo mkdir -p /opt/myapp
cat << 'EOF' | sudo tee /opt/myapp/app.sh
#!/bin/bash
while true; do
    echo "$(date): Running..." >> /var/log/myapp.log
    sleep 10
done
EOF
sudo chmod +x /opt/myapp/app.sh

# Unitファイル作成
cat << 'EOF' | sudo tee /etc/systemd/system/myapp.service
[Unit]
Description=My Application
After=network.target

[Service]
Type=simple
ExecStart=/opt/myapp/app.sh
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# サービス登録と起動
sudo systemctl daemon-reload
sudo systemctl enable --now myapp
sudo systemctl status myapp
```

### 環境変数付きサービス

```bash
# 環境変数ファイル
cat << 'EOF' | sudo tee /etc/myapp/env
DATABASE_URL=postgresql://localhost/mydb
API_KEY=secret123
EOF

# Unitファイル
cat << 'EOF' | sudo tee /etc/systemd/system/myapp.service
[Unit]
Description=My Application with Environment
After=network.target postgresql.service

[Service]
Type=simple
User=appuser
EnvironmentFile=/etc/myapp/env
WorkingDirectory=/opt/myapp
ExecStart=/opt/myapp/bin/start
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### タイマー（cronの代替）

```bash
# サービスUnit（実行内容）
cat << 'EOF' | sudo tee /etc/systemd/system/backup.service
[Unit]
Description=Daily Backup

[Service]
Type=oneshot
ExecStart=/opt/scripts/backup.sh
EOF

# タイマーUnit
cat << 'EOF' | sudo tee /etc/systemd/system/backup.timer
[Unit]
Description=Run backup daily

[Timer]
OnCalendar=daily
Persistent=true

[Install]
WantedBy=timers.target
EOF

# タイマー有効化
sudo systemctl daemon-reload
sudo systemctl enable --now backup.timer

# タイマー一覧
systemctl list-timers
```

---

## ターゲットとランレベル

### ターゲットの概念

```
┌─────────────────────────────────────────────────────────────┐
│                    ターゲットの階層                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  graphical.target (旧 runlevel 5)                           │
│      │                                                      │
│      ▼                                                      │
│  multi-user.target (旧 runlevel 3)                          │
│      │                                                      │
│      ├── nginx.service                                      │
│      ├── sshd.service                                       │
│      ├── postgresql.service                                 │
│      │                                                      │
│      ▼                                                      │
│  basic.target                                               │
│      │                                                      │
│      ▼                                                      │
│  sysinit.target                                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ランレベルとの対応

| ランレベル | ターゲット | 説明 |
|-----------|-----------|------|
| 0 | poweroff.target | シャットダウン |
| 1 | rescue.target | シングルユーザー |
| 3 | multi-user.target | マルチユーザー（CLI） |
| 5 | graphical.target | マルチユーザー（GUI） |
| 6 | reboot.target | 再起動 |

### ターゲットの操作

```bash
# 現在のターゲット
systemctl get-default

# デフォルトターゲット変更
sudo systemctl set-default multi-user.target

# ターゲット切り替え
sudo systemctl isolate rescue.target

# ターゲットに含まれるUnit
systemctl list-dependencies multi-user.target
```

---

## journalctlでログ確認

### 基本的な使い方

```bash
# すべてのログ
journalctl

# 最新のログ（逆順）
journalctl -r

# 末尾をフォロー
journalctl -f

# 今回の起動以降のログ
journalctl -b

# 前回起動のログ
journalctl -b -1
```

### フィルタリング

```bash
# 特定のサービス
journalctl -u nginx
journalctl -u nginx -u postgresql

# 優先度でフィルタ
journalctl -p err         # error以上
journalctl -p warning     # warning以上

# 時間でフィルタ
journalctl --since "1 hour ago"
journalctl --since "2024-01-15 10:00" --until "2024-01-15 12:00"
journalctl --since today

# プロセスでフィルタ
journalctl _PID=1234
journalctl _UID=1000

# カーネルメッセージ
journalctl -k
```

### 出力形式

```bash
# JSON形式
journalctl -o json
journalctl -o json-pretty

# 詳細表示
journalctl -o verbose

# 行数制限
journalctl -n 100

# ページャーなし
journalctl --no-pager
```

### ディスク使用量管理

```bash
# 使用量確認
journalctl --disk-usage

# 古いログを削除
sudo journalctl --vacuum-time=7d    # 7日以上古いログ削除
sudo journalctl --vacuum-size=1G    # 1GB以下に削減

# 設定ファイル
cat /etc/systemd/journald.conf
```

---

## コンテナとsystemd

### Docker内でのsystemd

```dockerfile
# systemdを使用するコンテナ
FROM ubuntu:22.04

RUN apt-get update && apt-get install -y systemd

# systemd用の設定
ENV container docker
STOPSIGNAL SIGRTMIN+3

# 不要なサービスを無効化
RUN rm -f /lib/systemd/system/multi-user.target.wants/* \
    /etc/systemd/system/*.wants/* \
    /lib/systemd/system/local-fs.target.wants/* \
    /lib/systemd/system/sockets.target.wants/*udev* \
    /lib/systemd/system/sockets.target.wants/*initctl* \
    /lib/systemd/system/sysinit.target.wants/systemd-tmpfiles-setup* \
    /lib/systemd/system/systemd-update-utmp*

VOLUME ["/sys/fs/cgroup"]
CMD ["/lib/systemd/systemd"]
```

### Dockerでsystemdコンテナを実行

```bash
# cgroup v2 の場合
docker run -d --name systemd-container \
  --privileged \
  --cgroupns=host \
  -v /sys/fs/cgroup:/sys/fs/cgroup:rw \
  systemd-image

# コンテナ内でsystemctl
docker exec systemd-container systemctl status
```

### Kubernetesでのsystemd

コンテナではsystemdを使わず、Kubernetesのマニフェストで直接アプリケーションを起動するのが一般的です。

```yaml
# Kubernetes では systemd は通常使わない
apiVersion: v1
kind: Pod
metadata:
  name: myapp
spec:
  containers:
  - name: app
    image: myapp:latest
    command: ["/app/bin/start"]  # 直接実行
```

---

## まとめ

### 主要なsystemctlコマンド

| コマンド | 説明 |
|---------|------|
| systemctl status | 状態確認 |
| systemctl start/stop | 起動/停止 |
| systemctl enable/disable | 自動起動設定 |
| systemctl restart | 再起動 |
| systemctl daemon-reload | Unit再読み込み |

### 主要なjournalctlコマンド

| コマンド | 説明 |
|---------|------|
| journalctl -u SERVICE | サービスのログ |
| journalctl -f | リアルタイム表示 |
| journalctl -p LEVEL | 優先度フィルタ |
| journalctl --since | 時間フィルタ |

### 次のステップ

- [ログ管理](logging.md) - より詳しいログ管理
- [パフォーマンス](performance.md) - システム監視

---

## 参考リソース

- [systemd Documentation](https://systemd.io/)
- [Arch Wiki - systemd](https://wiki.archlinux.org/title/systemd)
- [Red Hat - systemd Guide](https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/8/html/configuring_basic_system_settings/introduction-to-systemd_configuring-basic-system-settings)
