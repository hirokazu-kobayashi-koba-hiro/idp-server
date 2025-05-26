# コントリビュートガイド

**idp-server** は拡張性・現代的なプロトコル対応・開発者の自由度を重視したオープンソースの Identity Provider です。  
このプロジェクトへのフィードバック・バグ報告・プルリクエストを歓迎します。  
このガイドは、効果的かつ礼儀正しい貢献の方法を説明します。

---

## 🧭 はじめに

- 大きな変更やアーキテクチャに関わる提案は、まず [GitHub Discussions](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/discussions)
で相談してください。
- バグ修正や誤字の修正など小さな変更は、Issue を立ててそのまま PR を送っても問題ありません。

---

## ✅ プルリクエストチェックリスト

円滑なレビューのために、以下のガイドラインに沿ってください：

1. 💬 重要な変更には事前に GitHub Discussions での議論があること
2. 🐛 対応する GitHub Issue があること
3. 🎯 1 PR につき 1 機能 / 1 バグ修正
4. 📆 コミットは単一コミットであること（`git rebase` を使用）
5. 📘 コミットメッセージは Issue に紐づき、形式に従っていること（例：`Closes #123`）
6. 🔍 関連コード以外は変更しない
7. 🧪 該当する場合はテストコードを含める
8. 📓 ドキュメントの更新が含まれていること（必要な場合）

---

## 🌿 ブランチ命名規則

以下の命名ルールに従ってください（kebab-case を使用）：

```bash
feat/short-description-of-change
fix/bug-description
docs/update-readme
refactor/module-name
test/add-coverage-for-x
```

### 例

| 種別     | ブランチ名                               |
|--------|-------------------------------------|
| 機能追加   | `feat/access-token-jwe-support`     |
| バグ修正   | `fix/refresh-token-expiry-check`    |
| ドキュメント | `docs/contributing-guide-update`    |
| リファクタ  | `refactor/token-service-cleanup`    |
| テスト    | `test/token-introspection-endpoint` |

---

## 🔧 コミットの squash 方法

複数コミットを 1 つにまとめるには：

```bash
git rebase -i origin/main
```

- 最初の commit を `pick`
- 以降は `squash` または `s`
- 保存後、必要に応じてコミットメッセージを編集
- 最後に push：

```bash
git push --force-with-lease
```

---

## 🥪 テスト

- 既存のテストスタイルに従って追加してください
- 新しいモック・テストライブラリの導入は要相談
- `./gradlew test` で通ること

---

## 📚 ドキュメント

- 公開APIや挙動に関わる変更は [Docusaurus ドキュメント](https://your-docusaurus-site.com) を更新してください
- 可能であれば API ペイロードや構成例も含めてください

---

## 📅 コミットメッセージの書き方

```text
Add support for XYZ feature

必要に応じて詳細な説明。

Closes #123
```

複数行メッセージを書くには：

```bash
git commit -m "Add support for XYZ feature" -m "Closes #123"
```

---

## 🔒 DCO（Developer's Certificate of Origin）

すべてのコントリビューターは、自分のコードが Apache License 2.0 の元で提供可能であることを保証してください。

各コミットに signoff を付けてください：

```bash
git commit --signoff
```

これにより以下のような行が付加されます：

```
Signed-off-by: Your Name <your@email.com>
```

---

## 🗂️ 作業する課題の見つけ方

以下のラベルがついた Issue を参照してください：

- [`good first issue`](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/labels/good%20first%20issue)
- [`help wanted`](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/labels/help%20wanted)

迷ったら GitHub Discussions でアイデアを共有してください。

---

## 💬 コミュニケーション

[GitHub Discussions](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/discussions) または Issue にて気軽にご連絡ください。

強くて自由な ID プラットフォームを一緒に育ててくれてありがとう！
