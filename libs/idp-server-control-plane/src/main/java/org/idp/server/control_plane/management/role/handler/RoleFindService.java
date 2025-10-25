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

package org.idp.server.control_plane.management.role.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.role.RoleManagementContextBuilder;
import org.idp.server.control_plane.management.role.io.RoleFindRequest;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a single role by identifier.
 *
 * <p>Handles role retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Role retrieval by identifier
 *   <li>NOT_FOUND response when role doesn't exist
 * </ul>
 */
public class RoleFindService implements RoleManagementService<RoleFindRequest> {

  private final RoleQueryRepository roleQueryRepository;

  public RoleFindService(RoleQueryRepository roleQueryRepository) {
    this.roleQueryRepository = roleQueryRepository;
  }

  @Override
  public RoleManagementResponse execute(
      RoleManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RoleFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleIdentifier identifier = request.identifier();
    Role role = roleQueryRepository.find(tenant, identifier);

    if (!role.exists()) {
      throw new ResourceNotFoundException(String.format("Role not found: %s", identifier.value()));
    }

    // Populate builder with found role
    builder.withBefore(role);

    return new RoleManagementResponse(RoleManagementStatus.OK, role.toMap());
  }
}
