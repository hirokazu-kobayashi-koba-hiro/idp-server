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

package org.idp.server.control_plane.management.security.hook_result;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Control-Plane API for security event hook management.
 *
 * <p>This API provides operations to manually retry failed security event hook executions. It
 * allows administrators to re-execute specific hook results that have failed, using the original
 * security event and the latest hook configuration.
 */
public interface OrgSecurityEventHookManagementApi {

  /**
   * Gets the required permissions for each method in this API.
   *
   * @param method the API method name
   * @return required admin permissions
   */
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "findList", new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_READ)));
    map.put(
        "retry", new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_RETRY)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  SecurityEventHookManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultQueries queries,
      RequestAttributes requestAttributes);

  SecurityEventHookManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes);

  /**
   * Retries a failed security event hook execution.
   *
   * <p>This operation retrieves the original failed hook result, reconstructs the security event
   * from stored execution context, fetches the latest hook configuration, and re-executes the hook
   * with the same parameters.
   *
   * @param organizationIdentifier org id
   * @param tenantIdentifier tenant context
   * @param operator user performing the retry operation
   * @param oAuthToken authentication token
   * @param identifier identifier of the failed hook result to retry
   * @param requestAttributes request context information
   * @return result of the retry operation
   */
  SecurityEventHookManagementResponse retry(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes);
}
