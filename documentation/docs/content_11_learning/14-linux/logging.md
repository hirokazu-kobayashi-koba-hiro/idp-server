# ログ管理

Linuxのログシステム、syslog、journald、ログの分析方法について学びます。

---

## 目次

1. [Linuxのログシステム](#linuxのログシステム)
2. [重要なログファイル](#重要なログファイル)
3. [syslogとrsyslog](#syslogとrsyslog)
4. [journald](#journald)
5. [ログの検索と分析](#ログの検索と分析)
6. [ログローテーション](#ログローテーション)
7. [コンテナのログ](#コンテナのログ)

---

## Linuxのログシステム

### ログシステムの構造

```
┌─────────────────────────────────────────────────────────────┐
│                   ログシステムの構成                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  アプリケーション                                            │
│      │                                                      │
│      ├── 標準出力/標準エラー ─────────────────┐              │
│      │                                       │              │
│      ├── syslog() システムコール ──┐          │              │
│      │                            │          │              │
│      └── 直接ファイル書き込み      │          │              │
│          (nginx, apache)          │          │              │
│                                   │          │              │
│                                   ▼          ▼              │
│  ┌─────────────────┐      ┌─────────────────┐              │
│  │    rsyslogd     │      │    journald     │              │
│  │                 │◄────►│                 │              │
│  │  /var/log/*     │      │  Binary Journal │              │
│  └─────────────────┘      └─────────────────┘              │
│                                                              │
│  多くのディストリビューションでは両方が連携                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ログの優先度（Priority）

| レベル | 名前 | 説明 |
|-------|------|------|
| 0 | emerg | システム使用不可 |
| 1 | alert | 即時対応が必要 |
| 2 | crit | 危機的状況 |
| 3 | err | エラー |
| 4 | warning | 警告 |
| 5 | notice | 正常だが注意 |
| 6 | info | 情報 |
| 7 | debug | デバッグ情報 |

### ファシリティ（Facility）

| 名前 | 説明 |
|------|------|
| kern | カーネルメッセージ |
| user | ユーザープログラム |
| mail | メールシステム |
| daemon | システムデーモン |
| auth | 認証・セキュリティ |
| syslog | syslogd自身 |
| local0-7 | ローカル用途 |

---

## 重要なログファイル

### 主なログファイル

```bash
# システムログ
/var/log/syslog        # Debian/Ubuntu: 全般的なシステムログ
/var/log/messages      # RHEL/CentOS: 全般的なシステムログ

# 認証ログ
/var/log/auth.log      # Debian/Ubuntu: 認証関連
/var/log/secure        # RHEL/CentOS: 認証関連

# カーネルログ
/var/log/kern.log      # カーネルメッセージ
/var/log/dmesg         # ブート時のカーネルメッセージ

# アプリケーションログ
/var/log/nginx/        # Nginx
/var/log/apache2/      # Apache (Debian)
/var/log/httpd/        # Apache (RHEL)
/var/log/mysql/        # MySQL
/var/log/postgresql/   # PostgreSQL
```

### ログファイルの確認

```bash
# リアルタイム監視
tail -f /var/log/syslog
tail -f /var/log/auth.log

# 複数ファイルを同時監視
tail -f /var/log/syslog /var/log/auth.log

# 最新100行
tail -n 100 /var/log/syslog

# 特定パターンを検索
grep "error" /var/log/syslog
grep -i "failed" /var/log/auth.log

# 時間範囲でフィルタ（awkを使用）
awk '/Dec 23 10:00/,/Dec 23 11:00/' /var/log/syslog
```

---

## syslogとrsyslog

### rsyslogの設定

```bash
# 設定ファイル
/etc/rsyslog.conf
/etc/rsyslog.d/*.conf

# 設定例
cat /etc/rsyslog.conf

# ルールの形式: facility.priority  action
# auth,authpriv.*     /var/log/auth.log
# *.*                 /var/log/syslog
# kern.*              /var/log/kern.log
```

### カスタム設定の追加

```bash
# /etc/rsyslog.d/50-myapp.conf
# local0のログを専用ファイルに出力
local0.*  /var/log/myapp.log

# 設定反映
sudo systemctl restart rsyslog
```

### アプリケーションからの送信

```bash
# loggerコマンドでsyslogに送信
logger "This is a test message"
logger -p local0.info "Application started"
logger -t myapp "Custom tag message"

# 確認
tail /var/log/syslog
```

### リモートログ送信

```bash
# /etc/rsyslog.conf

# UDP送信
*.* @logserver.example.com:514

# TCP送信（信頼性が高い）
*.* @@logserver.example.com:514

# リモートログ受信側の設定
# module(load="imudp")
# input(type="imudp" port="514")
```

---

## journald

### journalctlの基本

```bash
# すべてのログ
journalctl

# ページャーなしで出力
journalctl --no-pager

# 最新から表示
journalctl -r

# リアルタイムフォロー
journalctl -f
```

### フィルタリング

```bash
# サービス別
journalctl -u nginx
journalctl -u nginx -u postgresql

# 優先度別
journalctl -p err                    # error以上
journalctl -p warning..err           # warning〜error

# 時間別
journalctl --since "2024-01-15"
journalctl --since "1 hour ago"
journalctl --since "10:00" --until "12:00"
journalctl --since today
journalctl --since yesterday

# ブート別
journalctl -b                        # 今回のブート
journalctl -b -1                     # 前回のブート
journalctl --list-boots              # ブート一覧

# ユーザー/プロセス別
journalctl _UID=1000
journalctl _PID=1234
journalctl _COMM=nginx
```

### 出力形式

```bash
# JSON形式
journalctl -o json
journalctl -o json-pretty

# 詳細表示
journalctl -o verbose

# 短い形式（タイムスタンプなし）
journalctl -o short

# 行数指定
journalctl -n 50
```

### カーネルログ

```bash
# カーネルメッセージ
journalctl -k
journalctl --dmesg

# dmesgコマンドも利用可能
dmesg
dmesg | tail -20
dmesg -T                # タイムスタンプを読みやすく
```

### journald設定

```bash
# 設定ファイル
cat /etc/systemd/journald.conf

# 主な設定項目
# Storage=persistent    # 永続保存
# Compress=yes          # 圧縮
# SystemMaxUse=500M     # 最大サイズ
# MaxRetentionSec=1month # 保存期間

# ディスク使用量確認
journalctl --disk-usage

# 古いログを削除
sudo journalctl --vacuum-time=7d    # 7日より古いログ
sudo journalctl --vacuum-size=500M  # 500MB以下に
```

---

## ログの検索と分析

### grepでの検索

```bash
# 基本検索
grep "error" /var/log/syslog

# 大文字小文字を無視
grep -i "error" /var/log/syslog

# 行番号付き
grep -n "error" /var/log/syslog

# 前後の行も表示
grep -A 3 -B 3 "error" /var/log/syslog

# 複数パターン
grep -E "error|warning|fail" /var/log/syslog

# 否定マッチ
grep -v "debug" /var/log/syslog

# 再帰的検索
grep -r "error" /var/log/
```

### awkでの分析

```bash
# 特定フィールドの抽出
awk '{print $1, $2, $3, $5}' /var/log/syslog

# エラーのカウント
awk '/error/ {count++} END {print count}' /var/log/syslog

# IPアドレスごとのアクセス数（Nginx）
awk '{print $1}' /var/log/nginx/access.log | sort | uniq -c | sort -rn | head -10

# HTTPステータスコード集計
awk '{print $9}' /var/log/nginx/access.log | sort | uniq -c | sort -rn
```

### lessでの閲覧

```bash
# ページング表示
less /var/log/syslog

# 操作
# /pattern  前方検索
# ?pattern  後方検索
# n         次のマッチ
# N         前のマッチ
# G         ファイル末尾
# g         ファイル先頭
# q         終了

# リアルタイムフォロー
less +F /var/log/syslog
# Ctrl+C で通常モードに戻る
# F でフォローモードに戻る
```

### 複合的な分析

```bash
# 直近1時間のエラー数
journalctl --since "1 hour ago" -p err | wc -l

# SSHログイン失敗のIPアドレス
grep "Failed password" /var/log/auth.log | awk '{print $(NF-3)}' | sort | uniq -c | sort -rn

# 時間帯別のアクセス数
awk '{print $4}' /var/log/nginx/access.log | cut -d: -f2 | sort | uniq -c

# エラーの種類別カウント
journalctl -p err --no-pager | awk '{print $5}' | sort | uniq -c | sort -rn
```

---

## ログローテーション

### logrotateの設定

```bash
# メイン設定
cat /etc/logrotate.conf

# アプリケーション別設定
ls /etc/logrotate.d/
cat /etc/logrotate.d/nginx
```

### 設定例

```bash
# /etc/logrotate.d/myapp
/var/log/myapp/*.log {
    daily               # 毎日ローテート
    rotate 7            # 7世代保持
    compress            # 圧縮
    delaycompress       # 次回から圧縮
    missingok           # ファイルがなくてもエラーにしない
    notifempty          # 空ファイルはローテートしない
    create 0640 root adm  # 新ファイルの権限
    sharedscripts       # スクリプトを1回だけ実行
    postrotate          # ローテート後に実行
        systemctl reload myapp > /dev/null 2>&1 || true
    endscript
}
```

### 手動実行

```bash
# ドライラン（実際には実行しない）
sudo logrotate -d /etc/logrotate.conf

# 強制実行
sudo logrotate -f /etc/logrotate.conf

# 特定の設定のみ
sudo logrotate -f /etc/logrotate.d/nginx

# 状態ファイル
cat /var/lib/logrotate/status
```

---

## コンテナのログ

### Dockerのログ

```bash
# コンテナのログ表示
docker logs container-name

# フォロー
docker logs -f container-name

# タイムスタンプ付き
docker logs -t container-name

# 最新100行
docker logs --tail 100 container-name

# 時間指定
docker logs --since "2024-01-15T10:00:00" container-name
docker logs --since 1h container-name
```

### Dockerのログドライバ

```bash
# 現在のログドライバ確認
docker info | grep "Logging Driver"

# コンテナ起動時に指定
docker run --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 nginx

# daemon.jsonで全体設定
cat /etc/docker/daemon.json
# {
#   "log-driver": "json-file",
#   "log-opts": {
#     "max-size": "10m",
#     "max-file": "3"
#   }
# }

# ログファイルの場所
/var/lib/docker/containers/<container-id>/<container-id>-json.log
```

### Kubernetesのログ

```bash
# Podのログ
kubectl logs pod-name
kubectl logs pod-name -c container-name  # 特定コンテナ

# フォロー
kubectl logs -f pod-name

# 前回のコンテナのログ
kubectl logs pod-name --previous

# ラベルで複数Pod
kubectl logs -l app=nginx

# すべてのコンテナ
kubectl logs pod-name --all-containers

# 時間指定
kubectl logs pod-name --since=1h
kubectl logs pod-name --since-time="2024-01-15T10:00:00Z"
```

### 集中ログ管理

```
┌─────────────────────────────────────────────────────────────┐
│               集中ログ管理のアーキテクチャ                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  コンテナ/Pod                                                │
│  ┌─────┐ ┌─────┐ ┌─────┐                                   │
│  │App 1│ │App 2│ │App 3│                                   │
│  └──┬──┘ └──┬──┘ └──┬──┘                                   │
│     │       │       │                                       │
│     └───────┼───────┘                                       │
│             │                                               │
│             ▼                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ログコレクター (Fluentd/Fluent Bit/Vector)          │   │
│  │  - DaemonSet として各ノードで実行                    │   │
│  │  - /var/log/containers/*.log を収集                 │   │
│  └─────────────────────────────────────────────────────┘   │
│             │                                               │
│             ▼                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ログストレージ                                      │   │
│  │  - Elasticsearch                                    │   │
│  │  - Loki                                             │   │
│  │  - CloudWatch Logs                                  │   │
│  └─────────────────────────────────────────────────────┘   │
│             │                                               │
│             ▼                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  可視化                                              │   │
│  │  - Kibana                                           │   │
│  │  - Grafana                                          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## まとめ

### 主要なログファイル

| ファイル | 内容 |
|---------|------|
| /var/log/syslog | 一般的なシステムログ |
| /var/log/auth.log | 認証ログ |
| /var/log/kern.log | カーネルログ |
| /var/log/nginx/ | Nginxログ |

### 主要なコマンド

| コマンド | 用途 |
|---------|------|
| journalctl | systemdログの閲覧 |
| tail -f | リアルタイム監視 |
| grep | パターン検索 |
| awk | テキスト処理・集計 |
| logrotate | ログローテーション |

### 次のステップ

- [パフォーマンス](performance.md) - システム監視
- [systemd](systemd.md) - journalctlの詳細

---

## 参考リソース

- [rsyslog Documentation](https://www.rsyslog.com/doc/)
- [systemd-journald](https://www.freedesktop.org/software/systemd/man/systemd-journald.service.html)
- [Kubernetes Logging](https://kubernetes.io/docs/concepts/cluster-administration/logging/)
