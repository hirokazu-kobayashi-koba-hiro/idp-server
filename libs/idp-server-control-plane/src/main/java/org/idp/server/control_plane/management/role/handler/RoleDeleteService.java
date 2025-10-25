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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.role.RoleManagementContextBuilder;
import org.idp.server.control_plane.management.role.io.RoleDeleteRequest;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting roles.
 *
 * <p>Handles role deletion logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Existing role retrieval
 *   <li>Role deletion (or dry-run simulation)
 *   <li>NO_CONTENT response on success
 * </ul>
 */
public class RoleDeleteService implements RoleManagementService<RoleDeleteRequest> {

  private final RoleQueryRepository roleQueryRepository;
  private final RoleCommandRepository roleCommandRepository;

  public RoleDeleteService(
      RoleQueryRepository roleQueryRepository, RoleCommandRepository roleCommandRepository) {
    this.roleQueryRepository = roleQueryRepository;
    this.roleCommandRepository = roleCommandRepository;
  }

  @Override
  public RoleManagementResponse execute(
      RoleManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RoleDeleteRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleIdentifier identifier = request.identifier();
    Role role = roleQueryRepository.find(tenant, identifier);

    if (!role.exists()) {
      throw new ResourceNotFoundException(String.format("Role not found: %s", identifier.value()));
    }

    // Populate builder with role to be deleted
    builder.withBefore(role);

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", role.id());
      response.put("dry_run", true);
      return new RoleManagementResponse(RoleManagementStatus.OK, response);
    }

    roleCommandRepository.delete(tenant, role);

    return new RoleManagementResponse(RoleManagementStatus.NO_CONTENT, Map.of());
  }
}
