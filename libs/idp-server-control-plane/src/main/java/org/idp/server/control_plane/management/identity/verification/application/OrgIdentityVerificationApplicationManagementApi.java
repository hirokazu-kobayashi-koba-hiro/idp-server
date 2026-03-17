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

package org.idp.server.control_plane.management.identity.verification.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementResponse;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface OrgIdentityVerificationApplicationManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "findList",
        new AdminPermissions(
            Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_APPLICATION_READ)));
    map.put(
        "get",
        new AdminPermissions(
            Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_APPLICATION_READ)));
    map.put(
        "delete",
        new AdminPermissions(
            Set.of(DefaultAdminPermission.IDENTITY_VERIFICATION_APPLICATION_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  IdentityVerificationApplicationManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationQueries queries,
      RequestAttributes requestAttributes);

  IdentityVerificationApplicationManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      RequestAttributes requestAttributes);

  IdentityVerificationApplicationManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
