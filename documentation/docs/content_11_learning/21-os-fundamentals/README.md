# OS基礎 学習ガイド

このセクションでは、Linux実践で触れた概念の「なぜ」を深掘りします。

---

## 前提条件

```
┌─────────────────────────────────────────────────────────────────────┐
│                      このセクションを学ぶ前に                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  14-linux セクションを先に学習してください                          │
│                                                                      │
│  必要な前提知識:                                                     │
│  ・ps, top でプロセスを確認できる                                   │
│  ・free, vmstat でメモリを確認できる                                │
│  ・kill でプロセスを終了できる                                      │
│  ・lsof でファイルディスクリプタを確認できる                        │
│                                                                      │
│  「コマンドは使えるけど、何が起きているのかわからない」             │
│  → そんな方のためのセクションです                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 学習の流れ

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  Step 1: Linux実践          Step 2: OS基礎                          │
│  ────────────────           ────────────                            │
│  「動かしてみる」     →     「なぜそうなる？」                       │
│                                                                      │
│  ps, top             →     プロセスとは何か                         │
│  ps -eLf             →     スレッドとは何か                         │
│  free, vmstat        →     仮想メモリの仕組み                       │
│  lsof, ulimit        →     ファイルディスクリプタとは               │
│  iostat, strace      →     I/Oモデルの違い                          │
│  kill -15, kill -9   →     シグナルの仕組み                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 目次

| # | ドキュメント                               | 内容 | Linux実践との関連 |
|---|--------------------------------------|------|------------------|
| 01 | [OS概要](./os-overview.md)             | カーネル、ユーザー空間、システムコール | - |
| 02 | [プロセスとスレッド](./process-and-thread.md) | プロセス/スレッドの違い、コンテキストスイッチ | `ps`, `top`, `htop` |
| 03 | [メモリ管理](./memory-management.md)      | 仮想メモリ、ページング、OOM Killer | `free`, `vmstat`, `/proc/meminfo` |
| 04 | [ファイルディスクリプタ](./file-descriptors.md) | FD、ソケット、上限、枯渇問題 | `lsof`, `ulimit`, `/proc/fd` |
| 05 | [I/Oモデル](./io-models.md)             | blocking/non-blocking/async、epoll | `strace`, `iostat` |
| 06 | [シグナル](./signals.md)                 | シグナルの種類、graceful shutdown | `kill`, `trap`, systemd |
| 07 | [同期プリミティブ](./synchronization.md)     | mutex、セマフォ、デッドロック | スレッドダンプ |

---

## 各ドキュメントの概要

### 01. OS概要

**疑問**: 「カーネルって何？」「システムコールって聞くけど何？」

**学べること**:
- OSの役割と構造
- カーネル空間とユーザー空間の違い
- システムコールの仕組み
- なぜ `sudo` が必要なのか

### 02. プロセスとスレッド

**疑問**: 「`ps` で見える PID って何？」「スレッドとプロセスの違いは？」

**学べること**:
- プロセスの正体（メモリ空間、PCB）
- スレッドとプロセスの違い
- コンテキストスイッチのコスト
- Java の Platform Thread と Virtual Thread の違い

**Linux実践との関連**:
```bash
ps aux          # プロセス一覧
ps -eLf         # スレッド一覧
top -H          # スレッド表示
cat /proc/[pid]/status  # プロセス詳細
```

### 03. メモリ管理

**疑問**: 「`free` の数字の意味は？」「OOM Killer って何？」

**学べること**:
- 仮想メモリの仕組み
- ページングとスワップ
- メモリの種類（RSS, VSZ, Shared）
- OOM Killer の動作

**Linux実践との関連**:
```bash
free -h         # メモリ使用状況
vmstat 1        # メモリ統計
cat /proc/meminfo       # 詳細情報
cat /proc/[pid]/oom_score  # OOM スコア
```

### 04. ファイルディスクリプタ

**疑問**: 「`lsof` の結果の意味は？」「Too many open files って何？」

**学べること**:
- ファイルディスクリプタの正体
- ソケットもFD
- FD上限と枯渇問題
- コネクションプールとの関係

**Linux実践との関連**:
```bash
lsof -p [pid]   # プロセスのFD一覧
ls -la /proc/[pid]/fd   # FD確認
ulimit -n       # FD上限確認
ulimit -n 65535 # FD上限変更
```

### 05. I/Oモデル

**疑問**: 「同期/非同期って何？」「epoll って何？」

**学べること**:
- Blocking I/O vs Non-blocking I/O
- 同期 vs 非同期
- select, poll, epoll の違い
- なぜ Node.js や Nginx が速いのか

**Linux実践との関連**:
```bash
strace -e read,write [command]  # システムコール追跡
iostat 1        # I/O統計
```

### 06. シグナル

**疑問**: 「`kill -9` と `kill -15` の違いは？」「graceful shutdown って何？」

**学べること**:
- シグナルの種類と意味
- SIGTERM vs SIGKILL
- シグナルハンドリング
- コンテナでの graceful shutdown

**Linux実践との関連**:
```bash
kill -15 [pid]  # SIGTERM（優雅な終了要求）
kill -9 [pid]   # SIGKILL（強制終了）
kill -l         # シグナル一覧
trap 'cleanup' SIGTERM  # シグナルハンドラ
```

### 07. 同期プリミティブ

**疑問**: 「デッドロックって何？」「mutex って何？」

**学べること**:
- 競合状態（Race Condition）
- mutex、セマフォ、条件変数
- デッドロックの原因と回避
- Java の synchronized との関係

**Linux実践との関連**:
```bash
# Java スレッドダンプでデッドロック検出
jstack [pid]
```

---

## 応用: idp-server での活用

| OS概念 | idp-server での関連 |
|--------|-------------------|
| プロセス/スレッド | Virtual Threads による高並行処理 |
| メモリ管理 | JVMヒープ設定、GCチューニング |
| ファイルディスクリプタ | コネクションプール上限、ソケット管理 |
| I/Oモデル | データベース接続、HTTP通信 |
| シグナル | Kubernetes での graceful shutdown |
| 同期 | 並行アクセス制御、キャッシュ設計 |

---

## 学習パス

### 基本パス（推奨順）

```
1. OS概要
   └── 全体像を把握

2. プロセスとスレッド
   └── ps, top の理解を深める

3. メモリ管理
   └── free, vmstat の理解を深める

4. ファイルディスクリプタ
   └── lsof, ulimit の理解を深める
```

### 応用パス

```
5. I/Oモデル
   └── 高並行処理の理解

6. シグナル
   └── コンテナ運用の理解

7. 同期プリミティブ
   └── 並行プログラミングの理解
```

---

## 関連セクション

- [14-linux](../14-linux/README.md) - Linux実践（先に学ぶ）
- [20-jvm](../20-jvm/README.md) - JVM（OS概念の上に構築）
- [13-kubernetes](../13-kubernetes/README.md) - コンテナ運用

---

## 参考リソース

### 書籍
- 「詳解UNIXプログラミング」（APUE） - Stevens
- 「Linuxプログラミングインタフェース」 - Michael Kerrisk
- 「オペレーティングシステム」（通称: 恐竜本） - Silberschatz

### オンライン
- [Linux kernel documentation](https://www.kernel.org/doc/html/latest/)
- [The Linux Programming Interface](https://man7.org/tlpi/)
