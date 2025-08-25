# AuthenticationInteractor 実装ガイド

## 1. 目的
`AuthenticationInteractor`は、各種認証方式（例：PIN認証、FIDO認証、顔認証など）の認証フローを実装するためのインターフェースです。
このガイドは、新しい認証方式を追加する開発者向けの標準手順・設計指針を示します。

---

## 2. 基本設計

### 2.1 インターフェース
- `AuthenticationInteractor`を実装する
- 主なメソッド：
    - `type()`: 認証インタラクション種別を返す
    - `operationType()`: 認証操作種別（例：CHALLENGE）を返す
    - `method()`: 認証方式名（例：PIN, FIDO_UAF）を返す
    - `interact(...)`: 認証フローの本体。リクエストを受けて認証処理を実行し、結果を返す

### 2.2 依存注入
- 認証実行用Executor群
- 認証設定リポジトリ
- 必要に応じて追加リクエストリゾルバ

---

## 3. 実装手順

### 3.1 クラス設計
- パッケージ: `org.idp.server.authentication.interactors.[認証方式名]`
- クラス名: `[認証方式]AuthenticationChallengeInteractor`

### 3.2 メソッド実装
- `type()`: `StandardAuthenticationInteraction`の該当値を返す
- `operationType()`: `OperationType.CHALLENGE`等
- `method()`: `StandardAuthenticationMethod`の該当値
- `interact(...)`:
    1. 設定リポジトリから認証設定を取得
    2. 必要な入力値（例：PINやdevice_id）を抽出
    3. 必須値チェック（未入力時はclientError返却）
    4. ExecutionRequest生成
    5. Executorで認証実行
    6. レスポンスマッピング
    7. 成功/失敗判定し、適切な結果を返却

### 3.3 エラーハンドリング
- 必須値未入力や不正値は`clientError`で返却
- 実行時エラーは`serverError`で返却

### 3.4 ログ
- `LoggerWrapper`で呼び出しやエラーを記録

---

## 4. 実装例（抜粋）

```java
public class PinAuthenticationChallengeInteractor implements AuthenticationInteractor {
  // ...依存注入...

  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("pin-authentication-challenge");
  }

  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  public String method() {
    return "pin";
  }

  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("PinAuthenticationChallengeInteractor called");

    // 設定取得
    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "pin-authentication");
    AuthenticationInteractionConfig authenticationInteractionConfig = configuration.getAuthenticationConfig("pin-authentication-challenge");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    // PIN抽出
    String pin = request.getValueAsString("pin");
    if (pin == null || pin.isEmpty()) {
      // ...clientError返却...
    }

    // ...executionRequest生成・executor実行・レスポンスマッピング...
  }
}
```

---

## 5. 注意点・ベストプラクティス

- 設定ファイル・config名・mappingルールは認証方式ごとに正しく合わせる
- 必須値チェック・エラー返却は必ず実装
- 追加リクエストや特殊処理が必要な場合は専用のResolverを用意
- テストケース（正常系・異常系）を必ず作成

---

## 6. 参考

- 既存の`FidoUafAuthenticationChallengeInteractor`や他のInteractor実装を参照
- 共通インターフェース・型・エラー処理の一貫性を保つこと
