/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.control_plane.management.authentication.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The organization-level and system-level authentication configuration APIs manage the same
 * resource, so they have to demand the same permission (Issue #1898).
 *
 * <p>The organization one required AUTHENTICATION_POLICY_CONFIG_* — a different resource — because
 * it was written from {@code OrgAuthenticationPolicyConfigManagementApi}. Nothing caught it: the
 * permission name only appears in a map inside a default method, roles that carry {@code idp:*}
 * satisfy either one, and an E2E that provisions an administrator never distinguishes them.
 *
 * <p>Parity alone would also pass if both sides drifted together, so the expected permission names
 * are stated here as well.
 */
class OrgAuthenticationConfigManagementApiPermissionTest {

  private static final List<String> METHODS =
      List.of("create", "findList", "get", "update", "delete");

  @Test
  @DisplayName("組織レベルとシステムレベルで要求する権限が一致する")
  void orgAndSystemRequireTheSamePermissions() {
    for (String method : METHODS) {
      assertEquals(
          permissionsOf(AuthenticationConfigurationManagementApi.class, method),
          permissionsOf(OrgAuthenticationConfigManagementApi.class, method),
          "method=" + method);
    }
  }

  @Test
  @DisplayName("要求するのは authentication-config 系であって authentication-policy-config ではない")
  void orgRequiresAuthenticationConfigPermissions() {
    assertEquals(
        Set.of("idp:authentication-config:create"),
        permissionsOf(OrgAuthenticationConfigManagementApi.class, "create"));
    assertEquals(
        Set.of("idp:authentication-config:read"),
        permissionsOf(OrgAuthenticationConfigManagementApi.class, "findList"));
    assertEquals(
        Set.of("idp:authentication-config:read"),
        permissionsOf(OrgAuthenticationConfigManagementApi.class, "get"));
    assertEquals(
        Set.of("idp:authentication-config:update"),
        permissionsOf(OrgAuthenticationConfigManagementApi.class, "update"));
    assertEquals(
        Set.of("idp:authentication-config:delete"),
        permissionsOf(OrgAuthenticationConfigManagementApi.class, "delete"));
  }

  @Test
  @DisplayName("未対応のメソッド名は例外")
  void unsupportedMethodThrows() {
    assertThrows(
        Exception.class, () -> permissionsOf(OrgAuthenticationConfigManagementApi.class, "patch"));
  }

  /**
   * {@code getRequiredPermissions} is a default method on an interface with five abstract methods,
   * so a proxy that forwards to the default implementation reads better than two stub classes whose
   * bodies all return null.
   */
  private static Set<String> permissionsOf(Class<?> api, String method) {
    Object proxy =
        Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            (p, m, args) -> InvocationHandler.invokeDefault(p, m, args));
    try {
      Method getRequiredPermissions = api.getMethod("getRequiredPermissions", String.class);
      AdminPermissions permissions =
          (AdminPermissions) getRequiredPermissions.invoke(proxy, method);
      return permissions.valuesAsSetString();
    } catch (ReflectiveOperationException e) {
      throw e.getCause() instanceof RuntimeException runtime
          ? runtime
          : new IllegalStateException(e);
    }
  }
}
