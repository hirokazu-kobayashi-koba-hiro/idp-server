# フレームワーク入門

ソフトウェアフレームワークの基礎概念から設計思想まで体系的に学ぶコンテンツです。

---

## 学習の流れ

### 一般概念

| ドキュメント | 内容 | 前提知識 |
|-------------|------|----------|
| [01. フレームワーク入門](01-framework-introduction.md) | フレームワークとは何か、ライブラリとの違い | なし |
| [02. IoC と DI](02-ioc-and-di.md) | 制御の反転、依存性注入の概念 | 01 |
| [03. 設計原則](03-design-principles.md) | CoC、DRY、関心の分離 | 01, 02 |
| [04. アーキテクチャパターン](04-architecture-patterns.md) | MVC、レイヤードアーキテクチャ等 | 01-03 |
| [05. フレームワーク自作](05-building-frameworks.md) | FW設計・実装のノウハウと注意点 | 01-04 |

### 具体技術

| ドキュメント | 内容 | 前提知識 |
|-------------|------|----------|
| [06. Java Servlet](06-java-servlet.md) | Servlet API、Filter、Listener | 01-04 |
| [07. Spring Boot](07-spring-boot.md) | 自動設定、Starter、DI、レイヤード構成 | 06, 08 |
| [08. Servletコンテナ](08-servlet-container.md) | Tomcat/Jetty/Undertow、スレッド管理、デプロイモデル | 06 |
| [09. CGI→Servlet](09-cgi-to-servlet.md) | Web動的処理の歴史、なぜServletか | なし |

---

## このセクションで学べること

### 基礎概念
- フレームワークとライブラリの本質的な違い
- Java Servletの仕組みとライフサイクル
- Filter、Listenerによる横断的処理
- Servletコンテナの役割と責務
- 制御の反転（IoC）の理解
- 依存性注入（DI）のメリットとパターン
- Spring Bootの自動設定とStarter

### 設計思想
- Convention over Configuration
- Don't Repeat Yourself (DRY)
- 関心の分離（Separation of Concerns）

### アーキテクチャ
- MVC、レイヤード、ヘキサゴナル、クリーンアーキテクチャ
- Spring Bootのレイヤード構成（Controller-Service-Repository）
- idp-server における Spring Boot の活用

### フレームワーク自作
- 自作前に考えるべきこと
- 拡張ポイントの設計
- API設計とバージョニング
- よくある失敗パターン

---

## 対象読者

- プログラミング経験があり、フレームワークを「なんとなく」使っている方
- ライブラリとフレームワークの違いを明確に説明できない方
- フレームワークの設計思想を深く理解したい方

---

## 関連コンテンツ

- [JVM基礎](../20-jvm/) - Javaランタイムの理解
- [HTTP/REST](../09-http-rest/) - Webフレームワークの基盤知識
