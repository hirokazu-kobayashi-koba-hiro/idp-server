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

package org.idp.server.control_plane.management.oidc.clientinstance;

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
 * The organization-level and system-level client instance APIs manage the same resource, so they
 * have to demand the same permission. Issue #1898 is what happens when the two drift apart
 * unnoticed; the names are also stated here, so both sides drifting together fails too.
 */
class OrgClientInstanceManagementApiPermissionTest {

  private static final List<String> METHODS =
      List.of("create", "findList", "get", "revoke", "delete");

  @Test
  @DisplayName("組織レベルとシステムレベルで要求する権限が一致する")
  void orgAndSystemRequireTheSamePermissions() {
    for (String method : METHODS) {
      assertEquals(
          permissionsOf(ClientInstanceManagementApi.class, method),
          permissionsOf(OrgClientInstanceManagementApi.class, method),
          "method=" + method);
    }
  }

  @Test
  @DisplayName("要求するのは client-instance 系の権限")
  void orgRequiresClientInstancePermissions() {
    assertEquals(
        Set.of("idp:client-instance:create"),
        permissionsOf(OrgClientInstanceManagementApi.class, "create"));
    assertEquals(
        Set.of("idp:client-instance:read"),
        permissionsOf(OrgClientInstanceManagementApi.class, "findList"));
    assertEquals(
        Set.of("idp:client-instance:read"),
        permissionsOf(OrgClientInstanceManagementApi.class, "get"));
    assertEquals(
        Set.of("idp:client-instance:revoke"),
        permissionsOf(OrgClientInstanceManagementApi.class, "revoke"));
    assertEquals(
        Set.of("idp:client-instance:delete"),
        permissionsOf(OrgClientInstanceManagementApi.class, "delete"));
  }

  @Test
  @DisplayName("未対応のメソッド名は例外")
  void unsupportedMethodThrows() {
    assertThrows(
        Exception.class, () -> permissionsOf(OrgClientInstanceManagementApi.class, "update"));
  }

  /** Calls the interface's default {@code getRequiredPermissions} through a forwarding proxy. */
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
