# マルチテナンシー

このドキュメントでは、**idp-server** がどのようにマルチテナント環境をサポートしているかを説明します。
テナントごとの認証・認可処理を、安全かつ分離された形で実行する仕組みを提供します。

---

## 🏷️ テナントとは？

**テナント（Tenant）** とは、`idp-server` を共有する中で独立した設定やデータ空間を持つ単位です。例えば：

* サービスごとに独自の認証ポリシーやMFA設定を持たせたい場合
* 開発環境と本番環境を分離したい場合
* サービス提供先ごとにIdPの挙動を切り替えたい場合

といったニーズに対応するため、**idp-server** はすべての機能をテナント単位で制御・分離できる設計になっています。

---

## 🧱 設計原則

* **明示的なテナントコンテキスト**
  すべてのAPIは `TenantIdentifier` を受け取り、`TenantRepository` を通じて `Tenant` に解決されます。
  解決された `Tenant` は、すべての下流サービスに明示的に渡されます。

* **グローバルなTenantContextを使用しない**
  `idp-server` はスレッドローカルや静的なテナントコンテキストを使用しません。これにより：

    * 同時リクエスト処理の安全性
    * ユニットテストの容易さ
    * テナントの情報漏洩防止

---

## 🧭 テナント対応サービスの実装例

`OAuthFlowEntryService` は一貫した設計パターンを示しています：

```java
Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
AuthorizationRequest authorizationRequest = oAuthProtocol.get(tenant, authorizationRequestIdentifier);
```

認証・認可・ログアウトなどすべての操作は、明示的な `Tenant` を使用して処理されます。

---

## 🛠 テナントごとの動的な挙動

各 `Tenant` は以下を個別に設定できます：

* プロトコル種別：`authorizationProtocolProvider`
* MFAポリシー
* フェデレーション設定
* セッション有効期限ルール

このような動的設定は次のように解決されます：

```java
OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
```

---

## 🗃 リポジトリアクセスパターン

すべてのリポジトリ呼び出しはテナント単位でスコープされています：

```java
authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);
authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);
```

これによりデータ境界が明示的になり、将来的なDBシャーディングやスキーマ分離の基盤となります。

---

## 📊 メリット

| 機能         | 説明                                |
|------------|-----------------------------------|
| ✅ データ分離    | テナント間のデータ漏洩を防止                    |
| ✅ テストしやすい  | グローバル状態を持たないため、テストごとに任意のテナントを注入可能 |
| ✅ スケーラブル設計 | 将来的なパーティショニングや水平スケーリングに対応         |
| ✅ 柔軟な挙動    | プロトコル・ポリシー・フローをテナント単位でカスタマイズ可能    |

---

## 🔒 セキュリティへの影響

テナントの分離はアプリケーション層で徹底されます。厳密なバリデーションとテナントごとの設定により、以下を保証します：

* ユーザーは常に正しいテナントコンテキストで認証される
* トークンとセッションはテナントごとに分離される
* フェデレーションやMFA戦略はテナントごとのロジックに従う

---

## 🧩 OAuthフローの例

```java
public OAuthRequestResponse request(
        TenantIdentifier tenantIdentifier,
        Map<String, String[]> params,
        RequestAttributes requestAttributes) {

  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
  OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

  OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
  OAuthRequestResponse requestResponse = oAuthProtocol.request(oAuthRequest);

  if (requestResponse.isOK()) {
    AuthenticationTransaction authenticationTransaction =
            AuthenticationTransaction.createOnOAuthFlow(tenant, requestResponse);
    authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);
  }

  return requestResponse;
}
```

この例のように、すべてのリクエストは正しいテナントコンテキストで処理されます。

---
