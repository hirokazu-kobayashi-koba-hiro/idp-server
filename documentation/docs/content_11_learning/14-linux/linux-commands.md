# Linux コマンドリファレンス

よく使うLinuxコマンドのクイックリファレンスです。

---

## 目次

1. [ファイル操作](#ファイル操作)
2. [テキスト処理](#テキスト処理)
3. [プロセス管理](#プロセス管理)
4. [システム情報](#システム情報)
5. [ネットワーク](#ネットワーク)
6. [ユーザー・権限](#ユーザー権限)
7. [圧縮・アーカイブ](#圧縮アーカイブ)
8. [その他便利コマンド](#その他便利コマンド)

---

## ファイル操作

### ナビゲーション

```bash
pwd                    # 現在のディレクトリ
cd /path               # 移動
cd ..                  # 親ディレクトリ
cd ~                   # ホーム
cd -                   # 直前のディレクトリ
```

### 一覧表示

```bash
ls                     # 一覧
ls -l                  # 詳細表示
ls -la                 # 隠しファイル含む
ls -lh                 # サイズを読みやすく
ls -lt                 # 更新日時順
ls -lS                 # サイズ順
ls -R                  # 再帰的に表示
```

### ファイル操作

```bash
touch file.txt         # 空ファイル作成
cp src dst             # コピー
cp -r dir1 dir2        # ディレクトリコピー
mv old new             # 移動/名前変更
rm file.txt            # 削除
rm -r dir              # ディレクトリ削除
mkdir dir              # ディレクトリ作成
mkdir -p a/b/c         # 親も作成
rmdir dir              # 空ディレクトリ削除
```

### ファイル検索

```bash
find /path -name "*.txt"           # 名前で検索
find /path -type f -mtime -7       # 7日以内に更新
find /path -type f -size +100M     # 100MB以上
find /path -type f -exec cmd {} \; # 見つけたファイルにコマンド実行
locate filename                    # 高速検索（要updatedb）
which cmd                          # コマンドのパス
```

### リンク

```bash
ln src hardlink        # ハードリンク
ln -s src symlink      # シンボリックリンク
readlink symlink       # リンク先確認
readlink -f symlink    # 絶対パスで確認
```

---

## テキスト処理

### ファイル表示

```bash
cat file.txt           # 全内容表示
head file.txt          # 先頭10行
head -n 20 file.txt    # 先頭20行
tail file.txt          # 末尾10行
tail -n 20 file.txt    # 末尾20行
tail -f file.txt       # リアルタイム監視
less file.txt          # ページング表示
```

### 検索

```bash
grep "pattern" file.txt        # パターン検索
grep -i "pattern" file.txt     # 大文字小文字無視
grep -r "pattern" /path        # 再帰検索
grep -n "pattern" file.txt     # 行番号表示
grep -v "pattern" file.txt     # 不一致行
grep -c "pattern" file.txt     # マッチ数
grep -E "regex" file.txt       # 拡張正規表現
grep -A 3 -B 3 "pattern" file  # 前後3行
```

### テキスト加工

```bash
cut -d: -f1 /etc/passwd        # 区切り文字で列抽出
cut -c1-10 file.txt            # 文字位置で抽出
sort file.txt                  # ソート
sort -n file.txt               # 数値ソート
sort -r file.txt               # 逆順
sort -u file.txt               # 重複削除
uniq file.txt                  # 重複削除（要ソート）
uniq -c file.txt               # カウント付き
wc file.txt                    # 行数/単語数/バイト数
wc -l file.txt                 # 行数のみ
```

### sed / awk

```bash
# sed - ストリームエディタ
sed 's/old/new/' file.txt          # 置換（最初のみ）
sed 's/old/new/g' file.txt         # 置換（全て）
sed -i 's/old/new/g' file.txt      # ファイル直接編集
sed '5d' file.txt                  # 5行目削除
sed '1,10d' file.txt               # 1-10行目削除
sed -n '5,10p' file.txt            # 5-10行目表示

# awk - テキスト処理
awk '{print $1}' file.txt          # 1列目
awk -F: '{print $1}' /etc/passwd   # 区切り文字指定
awk 'NR==5' file.txt               # 5行目
awk '$3 > 100' file.txt            # 3列目が100より大きい行
awk '{sum+=$1} END {print sum}'    # 合計
```

### 比較

```bash
diff file1.txt file2.txt       # 差分表示
diff -u file1.txt file2.txt    # unified形式
comm file1.txt file2.txt       # 共通行/固有行
```

---

## プロセス管理

### プロセス表示

```bash
ps                     # 現在のセッション
ps aux                 # 全プロセス
ps -ef                 # 全プロセス（別形式）
ps aux --sort=-%cpu    # CPU使用率順
ps aux --sort=-%mem    # メモリ使用率順
pstree                 # ツリー表示
pgrep nginx            # プロセス名で検索
```

### プロセス制御

```bash
kill PID               # SIGTERM送信
kill -9 PID            # SIGKILL送信
kill -HUP PID          # SIGHUP送信
killall nginx          # 名前で終了
pkill -f "pattern"     # パターンで終了
```

### バックグラウンド実行

```bash
cmd &                  # バックグラウンド実行
nohup cmd &            # ログアウト後も継続
jobs                   # ジョブ一覧
fg                     # フォアグラウンドに
bg                     # バックグラウンドで再開
```

### リアルタイム監視

```bash
top                    # プロセス監視
htop                   # より見やすい表示
watch -n 1 cmd         # 1秒ごとにコマンド実行
```

---

## システム情報

### システム

```bash
uname -a               # カーネル情報
hostname               # ホスト名
uptime                 # 稼働時間
date                   # 日時
cal                    # カレンダー
```

### リソース

```bash
free -h                # メモリ使用量
df -h                  # ディスク使用量
du -sh /path           # ディレクトリサイズ
du -h --max-depth=1    # 1階層のサイズ
lsblk                  # ブロックデバイス
```

### ハードウェア

```bash
cat /proc/cpuinfo      # CPU情報
cat /proc/meminfo      # メモリ情報
lscpu                  # CPU詳細
lspci                  # PCIデバイス
lsusb                  # USBデバイス
```

---

## ネットワーク

### 接続確認

```bash
ping host              # 疎通確認
ping -c 5 host         # 5回で終了
traceroute host        # 経路確認
curl url               # HTTP取得
curl -I url            # ヘッダーのみ
wget url               # ダウンロード
```

### 設定確認

```bash
ip addr                # IPアドレス
ip route               # ルーティング
ip link                # インターフェース
ss -tuln               # リスニングポート
ss -tan                # TCP接続
netstat -tuln          # リスニングポート（旧）
```

### DNS

```bash
dig domain             # DNS問い合わせ
dig +short domain      # IPのみ
nslookup domain        # DNS問い合わせ
host domain            # DNS問い合わせ
cat /etc/resolv.conf   # DNSサーバー設定
cat /etc/hosts         # ホストファイル
```

---

## ユーザー・権限

### ユーザー情報

```bash
whoami                 # 現在のユーザー
id                     # UID/GID
groups                 # 所属グループ
who                    # ログインユーザー
last                   # ログイン履歴
```

### 権限変更

```bash
chmod 755 file         # 権限設定
chmod +x file          # 実行権限追加
chmod -R 755 dir       # 再帰的に変更
chown user file        # 所有者変更
chown user:group file  # 所有者とグループ
chown -R user:group dir # 再帰的に変更
```

### sudo

```bash
sudo cmd               # root権限で実行
sudo -u user cmd       # 別ユーザーで実行
sudo -i                # rootシェル
sudo -l                # 許可されているコマンド
```

---

## 圧縮・アーカイブ

### tar

```bash
# 作成
tar cvf archive.tar files      # アーカイブ作成
tar czvf archive.tar.gz files  # gzip圧縮
tar cjvf archive.tar.bz2 files # bzip2圧縮

# 展開
tar xvf archive.tar            # 展開
tar xzvf archive.tar.gz        # gzip展開
tar xjvf archive.tar.bz2       # bzip2展開
tar xvf archive.tar -C /path   # 指定ディレクトリに展開

# 確認
tar tvf archive.tar            # 内容確認
```

### gzip / bzip2 / xz

```bash
gzip file              # gzip圧縮 → file.gz
gunzip file.gz         # gzip展開
bzip2 file             # bzip2圧縮 → file.bz2
bunzip2 file.bz2       # bzip2展開
xz file                # xz圧縮 → file.xz
unxz file.xz           # xz展開
```

### zip

```bash
zip archive.zip files  # zip作成
zip -r archive.zip dir # ディレクトリをzip
unzip archive.zip      # 展開
unzip -l archive.zip   # 内容確認
```

---

## その他便利コマンド

### 入出力リダイレクト

```bash
cmd > file             # 標準出力をファイルに（上書き）
cmd >> file            # 追記
cmd 2> file            # エラー出力をファイルに
cmd > file 2>&1        # 両方をファイルに
cmd < file             # ファイルを標準入力に
cmd1 | cmd2            # パイプ
```

### 環境変数

```bash
echo $PATH             # PATHを表示
export VAR=value       # 環境変数設定
env                    # 全環境変数
printenv               # 全環境変数
```

### 履歴

```bash
history                # コマンド履歴
history | grep cmd     # 履歴検索
!123                   # 履歴番号123を実行
!!                     # 直前のコマンドを実行
!$                     # 直前のコマンドの最後の引数
Ctrl + R               # 履歴検索（インタラクティブ）
```

### エイリアス

```bash
alias ll='ls -la'      # エイリアス設定
alias                  # エイリアス一覧
unalias ll             # エイリアス削除
```

### xargs

```bash
# 標準入力を引数に変換
find . -name "*.txt" | xargs grep "pattern"
cat files.txt | xargs -I {} cp {} /backup/
ls | xargs -n 1 echo
```

### その他

```bash
echo "text"            # 文字列出力
printf "%s\n" "text"   # フォーマット出力
tee file.txt           # 出力を画面とファイルに
time cmd               # 実行時間計測
sleep 5                # 5秒待機
clear                  # 画面クリア
reset                  # ターミナルリセット
```

---

## クイックリファレンス表

### ファイル操作

| コマンド | 説明 |
|---------|------|
| ls -la | 詳細一覧 |
| cd | 移動 |
| cp -r | コピー |
| mv | 移動/名前変更 |
| rm -r | 削除 |
| find | 検索 |

### テキスト処理

| コマンド | 説明 |
|---------|------|
| cat | 表示 |
| grep | 検索 |
| sed | 置換 |
| awk | 列処理 |
| sort | ソート |
| uniq | 重複削除 |

### システム

| コマンド | 説明 |
|---------|------|
| ps aux | プロセス一覧 |
| top | リアルタイム監視 |
| free -h | メモリ |
| df -h | ディスク |
| kill | プロセス終了 |

### ネットワーク

| コマンド | 説明 |
|---------|------|
| ip addr | IPアドレス |
| ss -tuln | ポート確認 |
| ping | 疎通確認 |
| curl | HTTP取得 |
| dig | DNS問い合わせ |

---

## 参考リソース

- [Linux man pages](https://man7.org/linux/man-pages/)
- [tldr pages](https://tldr.sh/) - 簡潔なコマンド例
- [explainshell](https://explainshell.com/) - コマンドの解説
