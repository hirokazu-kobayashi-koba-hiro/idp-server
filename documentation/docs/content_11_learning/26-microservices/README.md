---
sidebar_position: 0
---

# マイクロサービスアーキテクチャ

---

## 概要

マイクロサービスアーキテクチャは、大規模システムを小さな独立したサービス群として構築するアプローチです。本セクションでは、マイクロサービスの設計、実装、運用に関する知識を体系的に学習します。

---

## 学習コンテンツ

### [1. マイクロサービスの基礎](./01-microservices-fundamentals.md)
- マイクロサービスの定義と特徴
- モノリシック vs マイクロサービスの比較
- 主要なアーキテクチャパターン（API Gateway、BFF、Service Mesh）
- データ管理（Database per Service、Saga、CQRS）
- サービス分割戦略（DDD、境界づけられたコンテキスト）
- いつマイクロサービスを選ぶべきか
- Modular Monolith（中間解）

**所要時間**: 60分
**難易度**: ⭐⭐⭐ 中級

---

### [2. サービス間通信](./02-service-communication.md)
- 同期通信（REST、gRPC、GraphQL）
- 非同期通信（メッセージキュー、Pub/Sub）
- サービス間認証（mTLS、JWT）
- サーキットブレーカー、リトライパターン
- タイムアウト設定

**所要時間**: 45分
**難易度**: ⭐⭐⭐ 中級

---

### [3. データ管理](./03-data-management.md)
- Database per Service原則
- 分散トランザクション（Saga）
- CQRS パターン
- 結果整合性
- データ複製戦略

**所要時間**: 45分
**難易度**: ⭐⭐⭐⭐ 上級

---

### [4. 観測性](./04-observability.md)
- 3つの柱（ログ、メトリクス、トレーシング）
- ELK Stack、Prometheus + Grafana
- 分散トレーシング（OpenTelemetry）
- SLI/SLO/SLA

**所要時間**: 50分
**難易度**: ⭐⭐⭐ 中級

---

### [5. マイクロサービスとKubernetes](./05-microservices-on-kubernetes.md)
- Deployment、Service、Ingress
- サービスディスカバリ
- スケーリング戦略（HPA、VPA）
- デプロイ戦略（Rolling、Blue-Green、Canary）
- 設定管理（ConfigMap、Secret）
- リソース管理
- Service Mesh（Istio）

**所要時間**: 75分
**難易度**: ⭐⭐⭐⭐ 上級

---

### [6. 移行戦略](./06-migration-strategy.md)
- モノリスからマイクロサービスへの移行
- Strangler Fig パターン
- 段階的移行の実践手順
- データ移行戦略
- リスク管理（パフォーマンス、整合性、組織）
- 移行の判断基準（いつ移行すべきか）
- Modular Monolith（中間解）
- 逆方向の移行（マイクロサービス→モノリス）
- 失敗パターンと成功事例

**所要時間**: 60分
**難易度**: ⭐⭐⭐⭐ 上級

---

### [7. トレードオフ分析](./07-tradeoffs.md)
- トレードオフの全体像（得るもの vs 失うもの）
- パフォーマンス（レイテンシ < 1μs vs 5-50ms、N+1問題）
- データ整合性（ACIDトランザクション vs Saga、実装複雑度10-20倍）
- システム複雑性（開発、デバッグ、テスト）
- 運用コスト（インフラ、人件費）
- 開発速度（初期 vs 継続的）
- 定量的な比較（小規模 vs 大規模、規模によるクロスポイント）
- コスト・ベネフィット分析フレームワーク
- 実際の失敗例と成功パターン

**所要時間**: 60分
**難易度**: ⭐⭐⭐⭐ 上級

---

## 前提知識

- 基本的なアーキテクチャパターン
- REST API の理解
- データベースの基礎
- Docker/Kubernetes の基礎知識

---

## 参考リソース

### 書籍
- [Building Microservices (Sam Newman)](https://samnewman.io/books/building_microservices/)
- [Microservices Patterns (Chris Richardson)](https://microservices.io/book)

### オンラインリソース
- [Microservices.io](https://microservices.io/) - パターンカタログ
- [Martin Fowler - Microservices Guide](https://martinfowler.com/microservices/)

---

**最終更新**: 2026-01-24
**難易度範囲**: ⭐⭐⭐ 中級 ～ ⭐⭐⭐⭐ 上級
