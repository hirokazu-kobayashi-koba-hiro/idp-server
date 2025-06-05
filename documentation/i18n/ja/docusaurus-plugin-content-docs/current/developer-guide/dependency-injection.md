# 💉 Dependency Injection アーキテクチャ

## はじめに

`idp-server` のユースケース層以下のCoreロジック では、フレームワークに依存せず、**明示的な依存性注入（DI）コンテナ**を用いて、拡張性・ポータビリティを高めています。
このガイドでは、DIの仕組み、主要なクラス、利用パターンを解説します。

---

## 🔧 主要なDIコンポーネント

### `ApplicationComponentDependencyContainer`

* 初期起動時に注入される **プリミティブまたは外部依存のインスタンス**（例: 暗号化、キャッシュ、セッション管理など）を保持
* 他のDIプロバイダの `provide()` 時に引き渡される


### `ApplicationComponentProvider<T>`

* 任意のインターフェース `T` を構築する **ファクトリーのインターフェース**
* 実装クラスは `provide(container)` を使ってインスタンスを構築する

### `ApplicationComponentContainer`

* 実際にインスタンス化されたアプリケーション内のすべての依存を保持するマップ
* `resolve(Class<T>)` によって型安全に依存を取り出せる

---

## 🔄 DIの流れ

```text
Application 起動
    ↓
ApplicationComponentDependencyContainer 構築
    ↓
PluginLoader経由で ApplicationComponentProvider をロード
    ↓
ApplicationComponentProvider.provide() 実行
    ↓
ApplicationComponentContainer にインスタンス登録
    ↓
IdpServerApplicationが EntryServiceにDIしてアプリケーション完成
```

---

## 🧩 拡張ポイント

各種プラグインローダーはこのDI機構の上に実装されており、**フレームワークレスで拡張可能**です。

| PluginLoader名                                   | 提供機能                           |
|-------------------------------------------------|--------------------------------|
| `AuthenticationDependencyContainerPluginLoader` | 認証まわりのDI（WebAuthn, SMS, FIDO等） |
| `ApplicationComponentContainerPluginLoader`     | 全体の主要リポジトリやService定義           |
| `FederationDependencyContainerPluginLoader`     | Federation（外部IdP連携）処理          |
| `UserLifecycleEventExecutorPluginLoader`        | ライフサイクルイベントごとの拡張               |

---

## ✅ DI設計のメリット

* **依存が明示的**：ブラックボックスにならず、どこからDIされたかが分かる
* **テストしやすい**：モック注入も手動で制御可能
* **拡張しやすい**：プラグインとして任意のProviderを追加可能
* **ポータブル**：Spring BootやGuiceに依存せず、任意のJava実行環境でOK

---

## 📌 Tips: エラー時の対応

依存が不足している場合、以下のような例外が発生します：

```text
ApplicationComponentMissionException: Missing datasource for type: xxx
```

この場合は、`ApplicationComponentProvider` 側の依存定義忘れや、登録漏れを確認してください。

---

## おわりに

`idp-server` は OSSとしての透明性と柔軟性を担保するに、**この明示的DIスタイルが最適**を採用しています。
