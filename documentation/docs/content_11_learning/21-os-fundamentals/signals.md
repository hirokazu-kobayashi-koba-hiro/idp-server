# シグナル

**所要時間**: 25分

**前提知識**: `kill` コマンドを使ったことがある

**学べること**:
- シグナルの種類と意味
- SIGTERM vs SIGKILL
- シグナルハンドリング
- コンテナでの graceful shutdown

---

## この章で答える疑問

```
「kill -9 と kill -15 の違いは？」
「graceful shutdown って何？」
「なぜ kill -9 は最後の手段？」
「Kubernetes でどうやって終了処理する？」
```

---

## 1. シグナルとは

### 1.1 シグナルの概念

```
┌─────────────────────────────────────────────────────────────────────┐
│                    シグナル (Signal)                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  シグナル = プロセスへの非同期通知                                   │
│                                                                      │
│  用途:                                                               │
│  ├── プロセスの終了を要求                                          │
│  ├── エラーの通知（セグフォなど）                                  │
│  ├── 状態変化の通知（子プロセスの終了など）                        │
│  └── ユーザー定義の通知                                            │
│                                                                      │
│  ┌─────────┐                    ┌─────────┐                         │
│  │         │    SIGTERM         │         │                         │
│  │ プロセスA│ ─────────────────→ │ プロセスB│                         │
│  │         │                    │         │                         │
│  └─────────┘                    └─────────┘                         │
│       │                              │                               │
│       │                              ▼                               │
│       │                    ┌─────────────────┐                      │
│       │                    │ シグナルハンドラ │                      │
│       │                    │ または          │                      │
│       │                    │ デフォルト動作   │                      │
│       │                    └─────────────────┘                      │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 シグナルの送信元

```
┌─────────────────────────────────────────────────────────────────────┐
│                    シグナルはどこから来るか                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. ユーザー操作                                                     │
│     ├── Ctrl+C → SIGINT                                            │
│     ├── Ctrl+Z → SIGTSTP                                           │
│     └── Ctrl+\ → SIGQUIT                                           │
│                                                                      │
│  2. コマンド                                                         │
│     ├── kill -15 PID → SIGTERM                                     │
│     ├── kill -9 PID  → SIGKILL                                     │
│     └── kill -HUP PID → SIGHUP                                     │
│                                                                      │
│  3. カーネル（エラー検出時）                                        │
│     ├── 不正なメモリアクセス → SIGSEGV                             │
│     ├── ゼロ除算 → SIGFPE                                          │
│     └── 壊れたパイプ → SIGPIPE                                     │
│                                                                      │
│  4. システム/オーケストレーター                                     │
│     ├── systemd の停止要求 → SIGTERM                               │
│     └── Kubernetes の停止要求 → SIGTERM                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. 主要なシグナル

### 2.1 シグナル一覧

```bash
# シグナル一覧を表示
$ kill -l
 1) SIGHUP       2) SIGINT       3) SIGQUIT      4) SIGILL       5) SIGTRAP
 6) SIGABRT      7) SIGBUS       8) SIGFPE       9) SIGKILL     10) SIGUSR1
11) SIGSEGV     12) SIGUSR2     13) SIGPIPE     14) SIGALRM     15) SIGTERM
...
```

### 2.2 よく使うシグナル

```
┌─────────────────────────────────────────────────────────────────────┐
│                    重要なシグナル                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────┬────────┬─────────────────────────────────────────────┐  │
│  │ 番号   │ 名前    │ 説明                                        │  │
│  ├────────┼────────┼─────────────────────────────────────────────┤  │
│  │ 1      │ SIGHUP │ 端末の切断、設定リロードにも使用            │  │
│  │ 2      │ SIGINT │ 割り込み (Ctrl+C)                          │  │
│  │ 3      │ SIGQUIT│ 終了 + コアダンプ (Ctrl+\)                 │  │
│  │ 9      │ SIGKILL│ 強制終了（捕捉不可）                       │  │
│  │ 15     │ SIGTERM│ 終了要求（デフォルト）                     │  │
│  │ 10     │ SIGUSR1│ ユーザー定義1                              │  │
│  │ 12     │ SIGUSR2│ ユーザー定義2                              │  │
│  │ 11     │ SIGSEGV│ セグメンテーション違反                     │  │
│  │ 13     │ SIGPIPE│ 壊れたパイプへの書き込み                   │  │
│  │ 17     │ SIGCHLD│ 子プロセスの状態変化                       │  │
│  │ 19     │ SIGSTOP│ 一時停止（捕捉不可）                       │  │
│  │ 18     │ SIGCONT│ 再開                                       │  │
│  └────────┴────────┴─────────────────────────────────────────────┘  │
│                                                                      │
│  ※ 番号は Linux の場合。OS によって異なる場合あり                   │
│  ※ 名前で指定するのが安全（kill -TERM PID）                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 SIGTERM vs SIGKILL

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SIGTERM vs SIGKILL                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  SIGTERM (15) - 優雅な終了要求                                      │
│  ──────────────────────────────                                      │
│  ├── 「終了してください」というお願い                               │
│  ├── プロセスは捕捉してハンドリング可能                            │
│  ├── クリーンアップ処理を実行できる                                │
│  │   ├── 開いているファイルを閉じる                                │
│  │   ├── DBコネクションをクローズ                                  │
│  │   ├── 処理中のリクエストを完了                                  │
│  │   └── ログを書き出す                                            │
│  └── 無視することも可能（行儀悪いが）                              │
│                                                                      │
│  SIGKILL (9) - 強制終了                                             │
│  ─────────────────────────                                           │
│  ├── 「今すぐ死ね」という命令                                       │
│  ├── プロセスは捕捉できない                                        │
│  ├── カーネルが直接プロセスを終了                                  │
│  ├── クリーンアップ処理は実行されない                              │
│  │   ├── ファイルが中途半端な状態に                                │
│  │   ├── DBコネクションがリークする可能性                          │
│  │   └── データが失われる可能性                                    │
│  └── 最後の手段として使う                                          │
│                                                                      │
│  推奨手順:                                                           │
│  1. kill -TERM PID (または kill PID)                                │
│  2. 数秒〜数十秒待つ                                                │
│  3. まだ生きていたら kill -KILL PID                                 │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. シグナルハンドリング

### 3.1 シグナルの処理方法

```
┌─────────────────────────────────────────────────────────────────────┐
│                    シグナルを受け取った時の動作                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. デフォルト動作                                                   │
│     └── シグナルごとに決まった動作（終了、無視、停止など）          │
│                                                                      │
│  2. ハンドラを登録                                                   │
│     └── ユーザー定義の関数を実行                                    │
│                                                                      │
│  3. 無視                                                             │
│     └── SIG_IGN を設定してシグナルを無視                           │
│                                                                      │
│  ※ SIGKILL と SIGSTOP は捕捉も無視もできない                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 シェルスクリプトでの trap

```bash
#!/bin/bash

# SIGTERM を受け取った時の処理
cleanup() {
    echo "クリーンアップ処理を実行中..."
    # 一時ファイルの削除
    rm -f /tmp/myapp.lock
    # ログの書き出し
    echo "正常終了" >> /var/log/myapp.log
    exit 0
}

# シグナルハンドラを登録
trap cleanup SIGTERM SIGINT

# メイン処理
echo "アプリケーション起動"
while true; do
    # 処理
    sleep 1
done
```

### 3.3 Java でのハンドリング

```java
// Java でのシグナルハンドリング
public class App {
    public static void main(String[] args) {
        // シャットダウンフックを登録
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("シャットダウンフック実行中...");

            // クリーンアップ処理
            closeConnections();
            flushLogs();
            releaseResources();

            System.out.println("クリーンアップ完了");
        }));

        // アプリケーションのメイン処理
        startApplication();
    }
}

// Spring Boot の場合
@Component
public class GracefulShutdown implements DisposableBean {

    @Override
    public void destroy() throws Exception {
        // Bean 破棄時のクリーンアップ
        System.out.println("Spring Bean のクリーンアップ");
    }
}

// または @PreDestroy
@Component
public class MyService {

    @PreDestroy
    public void cleanup() {
        System.out.println("サービスのクリーンアップ");
    }
}
```

---

## 4. Graceful Shutdown

### 4.1 Graceful Shutdown とは

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Graceful Shutdown（優雅な終了）                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  目的: データを失わず、クライアントに影響を与えずに終了             │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                  Graceful Shutdown の流れ                    │    │
│  │                                                              │    │
│  │  1. SIGTERM を受信                                           │    │
│  │     │                                                        │    │
│  │     ▼                                                        │    │
│  │  2. 新しいリクエストの受付を停止                             │    │
│  │     ├── ロードバランサーへの登録解除                        │    │
│  │     └── リスニングソケットを閉じる                          │    │
│  │     │                                                        │    │
│  │     ▼                                                        │    │
│  │  3. 処理中のリクエストを完了                                 │    │
│  │     └── タイムアウト付きで待機                              │    │
│  │     │                                                        │    │
│  │     ▼                                                        │    │
│  │  4. リソースのクリーンアップ                                 │    │
│  │     ├── DB コネクションをクローズ                           │    │
│  │     ├── キャッシュをフラッシュ                              │    │
│  │     └── ログを書き出し                                      │    │
│  │     │                                                        │    │
│  │     ▼                                                        │    │
│  │  5. プロセス終了                                             │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 Spring Boot での設定

```yaml
# application.yml
server:
  shutdown: graceful  # Graceful Shutdown を有効化

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # シャットダウンのタイムアウト
```

```java
// カスタムの graceful shutdown ロジック
@Component
public class GracefulShutdownHandler {

    private final ExecutorService executor;
    private final DataSource dataSource;

    @PreDestroy
    public void shutdown() {
        log.info("Graceful shutdown 開始");

        // 1. Executor の新規タスク受付停止
        executor.shutdown();

        try {
            // 2. 実行中のタスク完了を待機（最大30秒）
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("タスクが完了しませんでした。強制終了します。");
                executor.shutdownNow();
            }

            // 3. DB コネクションをクローズ
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }

        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Graceful shutdown 完了");
    }
}
```

---

## 5. コンテナとシグナル

### 5.1 Docker でのシグナル

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Docker とシグナル                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  docker stop コンテナ名                                              │
│  ├── 1. SIGTERM をコンテナの PID 1 に送信                          │
│  ├── 2. 10秒待機（デフォルト、-t で変更可能）                      │
│  └── 3. まだ生きていれば SIGKILL                                   │
│                                                                      │
│  注意: シグナルは PID 1 にのみ送られる                              │
│                                                                      │
│  ❌ 問題のある Dockerfile:                                          │
│  FROM openjdk:21                                                     │
│  CMD java -jar app.jar                                               │
│  # → シェル経由で起動されるため、java が PID 1 にならない           │
│  # → シグナルがシェルに送られ、java に届かない                      │
│                                                                      │
│  ✅ 正しい Dockerfile:                                               │
│  FROM openjdk:21                                                     │
│  ENTRYPOINT ["java", "-jar", "app.jar"]                              │
│  # → java が PID 1 になり、シグナルを直接受け取る                   │
│                                                                      │
│  または exec 形式:                                                   │
│  CMD ["java", "-jar", "app.jar"]                                     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 Kubernetes でのシグナル

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Kubernetes の Pod 終了フロー                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Pod の削除が要求されると:                                          │
│                                                                      │
│  1. Pod が Terminating 状態に                                       │
│     │                                                                │
│     ▼                                                                │
│  2. Endpoints から削除（新しいトラフィックが来なくなる）            │
│     │                                                                │
│     ▼                                                                │
│  3. preStop フックを実行（設定されている場合）                      │
│     │                                                                │
│     ▼                                                                │
│  4. SIGTERM をコンテナに送信                                        │
│     │                                                                │
│     ▼                                                                │
│  5. terminationGracePeriodSeconds 待機（デフォルト30秒）            │
│     │                                                                │
│     ▼                                                                │
│  6. まだ生きていれば SIGKILL                                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.3 Kubernetes の設定例

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: myapp
spec:
  terminationGracePeriodSeconds: 60  # 終了猶予時間を60秒に

  containers:
  - name: app
    image: myapp:latest

    lifecycle:
      preStop:
        exec:
          # SIGTERM の前に実行される
          # ロードバランサーからの登録解除を待つなど
          command: ["sh", "-c", "sleep 5"]

    # ヘルスチェック
    readinessProbe:
      httpGet:
        path: /health/ready
        port: 8080
      # シャットダウン開始時に false を返すようにする
      # → 新しいトラフィックが来なくなる
```

```java
// readinessProbe に応答するコントローラー
@RestController
public class HealthController {

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @GetMapping("/health/ready")
    public ResponseEntity<String> ready() {
        if (shuttingDown.get()) {
            return ResponseEntity.status(503).body("Shutting down");
        }
        return ResponseEntity.ok("OK");
    }

    @PreDestroy
    public void shutdown() {
        // シャットダウン開始を記録
        shuttingDown.set(true);
    }
}
```

---

## 6. トラブルシューティング

### 6.1 プロセスが終了しない

```bash
# プロセスの状態を確認
$ ps aux | grep [p]rocess_name
USER    PID %CPU %MEM    VSZ   RSS TTY  STAT START   TIME COMMAND
app    1234  0.0  0.1 123456  7890 ?    S    10:00   0:01 /usr/bin/myapp

# STAT の意味
# S = sleeping (正常)
# D = uninterruptible sleep (I/O待ち、SIGKILL も効かない)
# Z = zombie (親が wait していない)

# D 状態の場合
# → I/O（ディスク、NFS など）が完了するまで待つしかない
# → ハードウェア障害の可能性も

# Z (zombie) の場合
# → 親プロセスを終了すれば消える
$ ps -o ppid= -p 1234  # 親プロセスを確認
5678
$ kill 5678  # 親を終了
```

### 6.2 シグナルが届かない

```bash
# プロセスがシグナルをブロックしているか確認
$ cat /proc/1234/status | grep -E "SigBlk|SigIgn|SigCgt"
SigBlk: 0000000000000000  # ブロック中のシグナル
SigIgn: 0000000000001000  # 無視しているシグナル
SigCgt: 0000000180014003  # ハンドラが登録されているシグナル

# ビットマスクをデコード
# 各ビットがシグナル番号に対応
```

### 6.3 systemd でのシグナル設定

```ini
# /etc/systemd/system/myapp.service
[Service]
ExecStart=/usr/bin/myapp
ExecStop=/bin/kill -TERM $MAINPID

# 終了シグナルを変更（デフォルトは SIGTERM）
KillSignal=SIGTERM

# 終了猶予時間
TimeoutStopSec=30

# 強制終了のシグナル
FinalKillSignal=SIGKILL

# プロセスグループ全体にシグナルを送る
KillMode=control-group
```

---

## 7. Linux での確認

```bash
# シグナルを送信
$ kill -TERM 1234
$ kill -15 1234    # 同じ
$ kill 1234        # SIGTERM がデフォルト

# シグナルの強制送信
$ kill -KILL 1234
$ kill -9 1234     # 同じ

# プロセスグループ全体にシグナル
$ kill -TERM -1234  # PID にマイナスをつける

# シグナルのトレース
$ strace -e signal kill -TERM 1234

# プロセスが受け取ったシグナルを確認
$ dmesg | grep "signal"
```

---

## 8. まとめ

```
┌─────────────────────────────────────────────────────────────────────┐
│                    この章で学んだこと                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. シグナルの基本                                                   │
│     ├── プロセスへの非同期通知                                     │
│     └── ユーザー、カーネル、他プロセスから送信                     │
│                                                                      │
│  2. SIGTERM vs SIGKILL                                              │
│     ├── SIGTERM: お願い、捕捉可能、クリーンアップできる            │
│     └── SIGKILL: 強制、捕捉不可、最後の手段                        │
│                                                                      │
│  3. シグナルハンドリング                                            │
│     ├── シェル: trap コマンド                                      │
│     └── Java: ShutdownHook, @PreDestroy                            │
│                                                                      │
│  4. Graceful Shutdown                                               │
│     ├── 新規受付停止 → 処理完了 → クリーンアップ                  │
│     └── Spring Boot: server.shutdown: graceful                     │
│                                                                      │
│  5. コンテナでの注意点                                              │
│     ├── シグナルは PID 1 に送られる                                │
│     ├── ENTRYPOINT の exec 形式を使う                              │
│     └── Kubernetes: terminationGracePeriodSeconds                  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 確認問題

1. SIGTERM と SIGKILL の違いを説明してください
2. Graceful Shutdown で行うべき処理は？
3. Docker で java プロセスにシグナルが届かない原因は？
4. Kubernetes で Pod が終了する際のフローは？
5. プロセスが D 状態（uninterruptible sleep）の場合、なぜ SIGKILL が効かない？

---

## 次のステップ

- [同期プリミティブ](synchronization.md) - 並行処理の制御
