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

package org.idp.server.control_plane.management.system;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.control_plane.management.system.io.SystemConfigurationUpdateRequest;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System configuration management API.
 *
 * <p>Provides operations for managing system-wide configuration settings such as SSRF protection
 * and trusted proxies.
 *
 * <h2>Endpoints</h2>
 *
 * <ul>
 *   <li>GET - Retrieve current system configuration
 *   <li>PUT - Update system configuration
 * </ul>
 */
public interface SystemConfigurationManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.SYSTEM_READ)));
    map.put("put", new AdminPermissions(Set.of(DefaultAdminPermission.SYSTEM_WRITE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  SystemConfigurationManagementResponse get(
      AdminAuthenticationContext authenticationContext, RequestAttributes requestAttributes);

  SystemConfigurationManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      SystemConfigurationUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
