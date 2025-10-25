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

package org.idp.server.control_plane.management.permission.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.permission.PermissionManagementContextBuilder;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.control_plane.management.permission.io.PermissionUpdateRequest;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for updating permissions.
 *
 * <p>Handles permission update logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via PermissionRequestValidator (throws InvalidRequestException)
 *   <li>Existing permission retrieval
 *   <li>Permission update (or dry-run simulation)
 * </ul>
 */
public class PermissionUpdateService
    implements PermissionManagementService<PermissionUpdateRequest> {

  private final PermissionQueryRepository permissionQueryRepository;
  private final PermissionCommandRepository permissionCommandRepository;

  public PermissionUpdateService(
      PermissionQueryRepository permissionQueryRepository,
      PermissionCommandRepository permissionCommandRepository) {
    this.permissionQueryRepository = permissionQueryRepository;
    this.permissionCommandRepository = permissionCommandRepository;
  }

  @Override
  public PermissionManagementResponse execute(
      PermissionManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      PermissionUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing permission (throws ResourceNotFoundException if not found)
    Permission before = permissionQueryRepository.find(tenant, request.identifier());

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          String.format("Permission not found: %s", request.identifier().value()));
    }

    // 2. Request validation
    new PermissionRequestValidator(request.permissionRequest(), dryRun).validate();

    // 3. Create updated permission
    Permission after = updatePermission(before, request.permissionRequest());

    // 4. Populate builder with before/after
    builder.withBefore(before);
    builder.withAfter(after);

    // 5. Build response
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);

    if (dryRun) {
      return new PermissionManagementResponse(PermissionManagementStatus.OK, contents);
    }

    // 6. Repository operation
    permissionCommandRepository.update(tenant, after);

    return new PermissionManagementResponse(PermissionManagementStatus.OK, contents);
  }

  private Permission updatePermission(Permission before, PermissionRequest request) {
    String id = before.id();
    String name = request.getValueAsString("name");
    String description = request.optValueAsString("description", "");
    return new Permission(id, name, description);
  }
}
