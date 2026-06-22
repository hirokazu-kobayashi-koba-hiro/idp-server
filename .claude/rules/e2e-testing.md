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

## Node バージョン（agent / 非対話シェルでのハマり）

通常の対話ターミナルでは `cd e2e && npm test` で問題ない。nodenv/nvm が `e2e/.node-version`(20.4.0) を読んで Node 20 に切り替わるため。**この節は agent（Claude Code）や CI など、それが効かない環境向けの注意。**

agent / 非対話シェルでは nodenv init が走らず `.node-version` が無視され、PATH 上の別 node（例: Homebrew の Node 25 が nodenv シムより前）にフォールバックすることがある。すると `jsonwebtoken` の依存 `buffer-equal-constant-time` が `Buffer.prototype` undefined で落ち、`src/lib/jose` を import する**全テストが実行前にコケる**：

```
TypeError: Cannot read properties of undefined (reading 'prototype')
  at node_modules/buffer-equal-constant-time/index.js
```

回避策：`node -v` が LTS でない時は、バージョンマネージャの実体 node で jest を直接実行する（PATH 解決をバイパス）。

```bash
cd e2e
NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" \
  "$HOME/.nodenv/versions/20.4.0/bin/node" node_modules/jest/bin/jest.js \
  --testPathPattern="usecase/mfa/mfa-23"
```

## テスト構造
- `spec/`: プロトコル仕様準拠テスト
- `scenario/`: ユースケースシナリオ
- `integration/`: 統合テスト（eKYC, Federation等）
- `usecase/`: ユースケーステンプレート検証
