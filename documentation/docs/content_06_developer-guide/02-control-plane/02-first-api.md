# Control PlaneのAPI実装

## このドキュメントの目的

実際に手を動かして、**システムレベルの簡単な管理API**を実装することで、idp-serverの開発フローを体験します。

### 所要時間
⏱️ **約30分**

### 前提知識
- [01. アーキテクチャ概要](./01-overview.md)を読了済み
- Java基礎知識

---

## 今回実装する機能

**「テナント名取得API」** を実装します。

```
GET /v1/management/tenants/{tenantId}/name
```

**レスポンス例**:
```json
{
  "tenant_id": "18ffff8d-8d97-460f-a71b-33f2e8afd41e",
  "name": "Example Tenant",
  "display_name": "Example Tenant Display Name"
}
```

---

## 実装の全体フロー

```
1. Control Plane層: API契約定義（インターフェース）
2. UseCase層: EntryService実装
3. Core層: Handler実装（今回はスキップ - Repository直接呼び出し）
4. Adapter層: Repository実装（既存のものを使用）
5. Controller層: HTTPエンドポイント実装
6. テスト: E2Eテスト作成
```

---

## Step 1: API契約定義（Control Plane層）

### 1-1. インターフェース作成

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantManagementApi.java`

既存のファイルに新しいメソッドを追加します。

```java
package org.idp.server.control_plane.management.tenant;

import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface TenantManagementApi {

    // 既存メソッド...

    /**
     * テナント名を取得する
     *
     * @param tenantIdentifier テナントID
     * @param operator 操作ユーザー
     * @param oAuthToken OAuth トークン
     * @param requestAttributes リクエスト属性
     * @return テナント名レスポンス
     */
    TenantNameResponse getName(
        TenantIdentifier tenantIdentifier,
        User operator,
        OAuthToken oAuthToken,
        RequestAttributes requestAttributes
    );

    /**
     * このAPIに必要な権限を返す
     */
    default AdminPermissions getRequiredPermissions(String method) {
        if ("getName".equals(method)) {
            return AdminPermissions.create("tenant:read");
        }
        // 他のメソッドの権限定義...
        return AdminPermissions.empty();
    }
}
```

### 1-2. レスポンスDTO作成

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantNameResponse.java`

```java
package org.idp.server.control_plane.management.tenant;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantNameResponse {

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

    public TenantNameResponse(Tenant tenant) {
        this.tenantId = tenant.identifier().value();
        this.name = tenant.name().value();
        this.displayName = tenant.displayName().value();
    }

    // Getters
    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

---

## Step 2: EntryService実装（UseCase層）

**ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/TenantManagementEntryService.java`

既存のファイルに新しいメソッドを追加します。

```java
package org.idp.server.usecases.control_plane.system_manager;

import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.tenant.*;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class TenantManagementEntryService implements TenantManagementApi {

    TenantQueryRepository tenantQueryRepository;
    AuditLogPublisher auditLogPublisher;
    LoggerWrapper log = LoggerWrapper.getLogger(TenantManagementEntryService.class);

    // コンストラクタ...

    @Override
    @Transaction(readOnly = true)  // ⚠️ 読み取り専用トランザクション
    public TenantNameResponse getName(
            TenantIdentifier tenantIdentifier,
            User operator,
            OAuthToken oAuthToken,
            RequestAttributes requestAttributes) {

        // 1. 必要権限を取得
        AdminPermissions permissions = getRequiredPermissions("getName");

        // 2. Tenantを取得
        Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

        // 3. Audit Log記録（読み取り操作）
        AuditLog auditLog = AuditLogCreator.createOnRead(
            "TenantManagementApi.getName",
            "getName",
            tenant,
            operator,
            oAuthToken,
            requestAttributes
        );
        auditLogPublisher.publish(auditLog);

        // 4. 権限チェック
        if (!permissions.includesAll(operator.permissionsAsSet())) {
            log.warn("Permission denied: required {}, but user has {}",
                permissions.valuesAsString(),
                operator.permissionsAsString());
            throw new ForbiddenException("Permission denied");
        }

        // 5. レスポンス生成
        return new TenantNameResponse(tenant);
    }
}
```

### ポイント解説

1. **`@Transaction(readOnly = true)`**: 読み取り専用トランザクション（最適化）
2. **権限チェック**: `getRequiredPermissions()` で必要権限を取得し、ユーザーが持っているか確認
3. **Audit Log記録**: `createOnRead()` で読み取り操作を記録
4. **シンプルなロジック**: Repository直接呼び出し（複雑なロジックがない場合）

---

## Step 3: Controller実装（Controller層）

**ファイル**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/control_plane/restapi/management/TenantManagementV1Api.java`

既存のファイルに新しいエンドポイントを追加します。

```java
package org.idp.server.adapters.springboot.control_plane.restapi.management;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.control_plane.management.tenant.TenantManagementApi;
import org.idp.server.control_plane.management.tenant.TenantNameResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants")
public class TenantManagementV1Api implements ParameterTransformable {

    TenantManagementApi tenantManagementApi;

    public TenantManagementV1Api(IdpServerApplication idpServerApplication) {
        this.tenantManagementApi = idpServerApplication.tenantManagementApi();
    }

    // 既存エンドポイント...

    /**
     * テナント名取得
     */
    @GetMapping("/{tenant-id}/name")
    public ResponseEntity<TenantNameResponse> getName(
            @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
            @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
            HttpServletRequest httpServletRequest) {

        // 1. RequestAttributes変換
        RequestAttributes requestAttributes = transform(httpServletRequest);

        // 2. EntryService呼び出し
        TenantNameResponse response = tenantManagementApi.getName(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            requestAttributes
        );

        // 3. HTTPレスポンス返却
        return ResponseEntity.ok(response);
    }
}
```

### ポイント解説

1. **V1Api命名規則**: Controllerではなく`*V1Api`が正しい命名
2. **OperatorPrincipal**: `@AuthenticationPrincipal`で取得し、`.getUser()`と`.getOAuthToken()`で情報取得
3. **ParameterTransformable**: `transform(httpServletRequest)`でRequestAttributesに変換
4. **型変換のみ**: Controller層ではロジックを書かず、EntryServiceに委譲

---

## Step 4: E2Eテスト作成

**ファイル**: `e2e/spec/management/tenant-name.spec.js`

```javascript
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');

describe('Tenant Name API', () => {
  let adminToken;
  let tenantId;

  beforeAll(async () => {
    // 1. 管理者トークン取得
    const tokenResponse = await axios.post('http://localhost:8080/oauth/token', {
      grant_type: 'client_credentials',
      client_id: 'admin-client',
      client_secret: 'admin-secret',
      scope: 'tenant:read'
    });
    adminToken = tokenResponse.data.access_token;

    // 2. テストテナント作成
    const tenantResponse = await axios.post(
      'http://localhost:8080/v1/management/tenants',
      {
        name: 'test-tenant',
        display_name: 'Test Tenant for Name API'
      },
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );
    tenantId = tenantResponse.data.tenant_id;
  });

  test('should return tenant name successfully', async () => {
    // テナント名取得
    const response = await axios.get(
      `http://localhost:8080/v1/management/tenants/${tenantId}/name`,
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );

    // レスポンス検証
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty('tenant_id', tenantId);
    expect(response.data).toHaveProperty('name', 'test-tenant');
    expect(response.data).toHaveProperty('display_name', 'Test Tenant for Name API');
  });

  test('should return 403 when user lacks permission', async () => {
    // 権限のないトークンで実行
    const noPermissionTokenResponse = await axios.post('http://localhost:8080/oauth/token', {
      grant_type: 'client_credentials',
      client_id: 'public-client',
      client_secret: 'public-secret',
      scope: 'openid'  // tenant:read権限なし
    });

    try {
      await axios.get(
        `http://localhost:8080/v1/management/tenants/${tenantId}/name`,
        {
          headers: {
            Authorization: `Bearer ${noPermissionTokenResponse.data.access_token}`
          }
        }
      );
      fail('Expected 403 error');
    } catch (error) {
      expect(error.response.status).toBe(403);
    }
  });
});
```

---

## Step 5: ビルド・テスト実行

### ビルド

```bash
./gradlew spotlessApply  # フォーマット修正
./gradlew build
```

### テスト実行

```bash
cd e2e
npm test -- tenant-name.spec.js
```

---

## チェックリスト

実装完了前に以下を確認してください。

- [ ] API契約定義（インターフェース）を作成した
- [ ] レスポンスDTOを作成した（`@JsonProperty`でスネークケース対応）
- [ ] EntryServiceを実装した
  - [ ] `@Transaction`アノテーション付与
  - [ ] 権限チェック実装
  - [ ] Audit Log記録
  - [ ] 読み取り専用なら`@Transaction(readOnly = true)`
- [ ] Controllerを実装した（ロジックなし、型変換のみ）
- [ ] E2Eテストを作成した
  - [ ] 正常系テスト
  - [ ] 権限エラーテスト
- [ ] コードフォーマット（`spotlessApply`）実行
- [ ] ビルド成功
- [ ] テスト成功

---

## よくあるエラーと解決策

### エラー1: `TenantNotFoundException`

**原因**: 存在しないテナントIDを指定

**解決策**: テスト前にテナントを作成する（`beforeAll`で作成）

### エラー2: `403 Forbidden`

**原因**: 権限不足

**解決策**: トークン取得時に正しいスコープ（`tenant:read`）を指定

### エラー3: `NullPointerException in AuditLogPublisher`

**原因**: `RequestAttributes`がnull

**解決策**: Controllerで`@RequestAttribute`を正しく受け取る

---

## 次のステップ

✅ 簡単な管理APIを実装できた！

### 📖 次に読むべきドキュメント

1. [03. システムレベルAPI](./03-system-level-api.md) - CRUD実装パターン
2. [04. 組織レベルAPI](./04-organization-level-api.md) - 複雑なアクセス制御

### 🔍 さらに学ぶ

- [Context Creator パターン](../../content_10_ai_developer/ai-13-control-plane.md#context-creator-パターン) - リクエスト変換の詳細
- [EntryService 10フェーズ](../../content_10_ai_developer/ai-10-use-cases.md#entryserviceの10フェーズ) - 複雑な実装フロー
- [共通実装パターン](../06-patterns/common-patterns.md) - よく使うパターン集

---

**情報源**: [ClientManagementEntryService.java](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java)
**最終更新**: 2025-10-12
