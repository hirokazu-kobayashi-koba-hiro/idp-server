# Linux 学習ガイド

コンテナ技術（Docker/Kubernetes）を理解するために必要なLinuxの基礎知識を学ぶドキュメント集です。

---

## 学習の目的

- コンテナ技術の基盤となるLinuxを理解する
- サーバー運用に必要な基本操作を習得する
- トラブルシューティング能力を身につける

---

## 学習ロードマップ

```
┌─────────────────────────────────────────────────────────────┐
│                    学習の流れ                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  入門 (初めての方)                                          │
│  └── Linux 入門                                             │
│      └── ターミナルの基本、最初のコマンド                   │
│                                                              │
│  基礎 (必須知識)                                            │
│  ├── Linux 基礎                                             │
│  │   └── シェル、ディストリビューション                     │
│  ├── ファイルシステム                                       │
│  │   └── ディレクトリ構造、マウント                        │
│  ├── プロセス管理                                           │
│  │   └── プロセス、シグナル、デーモン                      │
│  └── ユーザーと権限                                         │
│      └── パーミッション、sudo                               │
│                                                              │
│  ネットワーク                                               │
│  ├── ネットワーク基礎                                       │
│  │   └── TCP/IP、ソケット、DNS                             │
│  ├── ファイアウォール                                       │
│  │   └── iptables、nftables                                │
│  └── トラブルシューティング                                 │
│      └── 診断ツール、問題解決                              │
│                                                              │
│  コンテナ技術の基盤                                         │
│  ├── Namespaces                                             │
│  │   └── プロセス・ネットワーク・マウントの分離             │
│  ├── Cgroups                                                │
│  │   └── CPU・メモリ・I/Oの制限                           │
│  └── OverlayFS                                              │
│      └── コンテナイメージのレイヤー構造                    │
│                                                              │
│  運用知識                                                   │
│  ├── systemd                                                │
│  │   └── サービス管理                                      │
│  ├── ログ管理                                               │
│  │   └── syslog、journald                                  │
│  └── パフォーマンス監視                                     │
│      └── CPU、メモリ、ディスク、ネットワーク               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## ドキュメント一覧

### 入門

| ドキュメント | 説明 | 所要時間 |
|-------------|------|---------|
| [Linux 入門](linux-introduction.md) | ターミナルの基本、最初のコマンド | 30分 |

### 基礎

| ドキュメント | 説明 | 所要時間 |
|-------------|------|---------|
| [Linux 基礎](linux-basics.md) | カーネル、シェル、環境変数、仮想メモリ | 60分 |
| [ファイルシステム](filesystem.md) | ディレクトリ構造、マウント、inode | 45分 |
| [プロセス管理](process-management.md) | プロセス、シグナル、デーモン | 45分 |
| [ユーザーと権限](users-permissions.md) | パーミッション、sudo | 45分 |
| [シェルスクリプト](shell-scripting.md) | 変数、条件分岐、ループ、関数 | 45分 |
| [シェルスクリプト応用例](shell-scripting-examples.md) | CSV読み込み、APIテスト、ログ出力 | 30分 |

### ネットワーク

| ドキュメント | 説明 | 所要時間 |
|-------------|------|---------|
| [ネットワーク基礎](networking-basics.md) | TCP/IP、ソケット、DNS | 60分 |
| [SSH](ssh.md) | リモート接続、鍵認証、トンネリング | 45分 |
| [iptables/ファイアウォール](iptables-firewall.md) | パケットフィルタリング | 45分 |
| [ネットワークトラブルシューティング](network-troubleshooting.md) | 診断ツール | 45分 |

### コンテナ技術の基盤

| ドキュメント | 説明 | 所要時間 |
|-------------|------|---------|
| [Namespaces](namespaces.md) | プロセス・ネットワーク・マウントの分離 | 60分 |
| [Cgroups](cgroups.md) | リソース制限 | 45分 |
| [OverlayFS](overlay-filesystem.md) | コンテナのファイルシステム | 45分 |

### 運用

| ドキュメント | 説明 | 所要時間 |
|-------------|------|---------|
| [systemd](systemd.md) | サービス管理 | 45分 |
| [ログ管理](logging.md) | syslog、journald | 45分 |
| [パフォーマンス監視](performance.md) | リソース監視 | 45分 |

### リファレンス

| ドキュメント | 説明 |
|-------------|------|
| [コマンドリファレンス](linux-commands.md) | よく使うコマンド一覧 |

---

## 推奨学習パス

### パス1: コンテナを理解したい方（推奨）

```
1. Linux 入門
2. Linux 基礎
3. ファイルシステム
4. プロセス管理
5. Namespaces ★
6. Cgroups ★
7. OverlayFS ★
```

★ コンテナ技術の核心部分

### パス2: サーバー運用を学びたい方

```
1. Linux 入門
2. Linux 基礎
3. ユーザーと権限
4. systemd
5. ログ管理
6. パフォーマンス監視
```

### パス3: ネットワークを学びたい方

```
1. ネットワーク基礎
2. iptables/ファイアウォール
3. ネットワークトラブルシューティング
4. Namespaces（Network Namespace）
```

---

## 学べること

### Docker を理解するために

| Linux の概念 | Docker での活用 |
|-------------|----------------|
| Namespaces | コンテナの分離 |
| Cgroups | --memory, --cpus オプション |
| OverlayFS | イメージレイヤー |
| プロセス | PID 1、シグナル処理 |
| ネットワーク | docker network |

### Kubernetes を理解するために

| Linux の概念 | Kubernetes での活用 |
|-------------|-------------------|
| Namespaces | Pod のネットワーク分離 |
| Cgroups | Resource Limits/Requests |
| iptables | Service、NetworkPolicy |
| systemd | kubelet の管理 |

---

## 実践環境の準備

### ローカルでの学習

```bash
# Docker で Linux 環境を用意
docker run -it ubuntu:22.04 /bin/bash

# または Vagrant
vagrant init ubuntu/jammy64
vagrant up
vagrant ssh
```

### クラウドでの学習

- AWS EC2（無料枠あり）
- GCP Compute Engine（無料枠あり）
- Azure Virtual Machines

---

## 次のステップ

Linux の基礎を学んだ後は、以下のドキュメントに進みましょう：

- [Kubernetes/Container 学習](../13-kubernetes/README.md) - Docker、Kubernetesの実践

---

## 参考リソース

- [Linux Documentation Project](https://tldp.org/)
- [ArchWiki](https://wiki.archlinux.org/)
- [Linux Journey](https://linuxjourney.com/)
- [Linux man pages](https://man7.org/linux/man-pages/)
