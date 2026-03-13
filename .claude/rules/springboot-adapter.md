---
paths:
  - "libs/idp-server-springboot-adapter/src/**/*.java"
---

# Spring Boot Adapter層のルール

## Controller層にビジネスロジック禁止
- Controller（`*V1Api`）はリクエスト受信 → EntryService呼び出し → レスポンス返却のみ
- バリデーション、条件分岐、計算はCore層またはUseCase層で行う

## EntryServiceで `@Transaction` を使う
- トランザクション管理は `@Transaction` アノテーションで行う
- `TransactionManager` を直接呼び出さない（DB実装の関心がUseCase層に漏れる）

## SPI登録
- 新しいAdapter実装を追加したら `META-INF/services/` にSPI登録が必要
