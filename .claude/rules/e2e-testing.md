---
paths:
  - "e2e/**"
---

# E2Eテストのルール

## `npm test` 経由で実行すること（必須）

**`npx jest` は禁止。** `package.json` の `test` スクリプトが `NODE_EXTRA_CA_CERTS` で mkcert のルートCAを設定している。`npx jest` 直接実行だとこの設定が抜け、自己署名証明書の検証エラー（`UNABLE_TO_VERIFY_LEAF_SIGNATURE`）でリクエストが失敗する。

```bash
# OK
cd e2e && npm test -- --testPathPattern="integration-05" --testNamePattern="テスト名"

# NG: TLSエラーになる
cd e2e && npx jest src/tests/integration/...
```

## テスト構造
- `spec/`: プロトコル仕様準拠テスト
- `scenario/`: ユースケースシナリオ
- `integration/`: 統合テスト（eKYC, Federation等）
- `usecase/`: ユースケーステンプレート検証
