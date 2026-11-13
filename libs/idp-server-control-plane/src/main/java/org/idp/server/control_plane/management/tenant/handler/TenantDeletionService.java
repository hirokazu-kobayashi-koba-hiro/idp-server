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

package org.idp.server.control_plane.management.tenant.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantDeleteRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantDeletionSteps;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting tenants.
 *
 * <p>Handles tenant deletion logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant existence verification
 *   <li>Each feature's work before the deletion (e.g. revoking linked accounts' external tokens)
 *   <li>Tenant deletion from repository
 * </ul>
 *
 * <p>The tenant's rows go with it through foreign key cascades, which do nothing outside this
 * database. Features undo what they hold elsewhere first, through {@link TenantDeletionSteps}.
 */
public class TenantDeletionService implements TenantManagementService<TenantDeleteRequest> {

  private final TenantQueryRepository tenantQueryRepository;
  private final TenantCommandRepository tenantCommandRepository;
  private final TenantDeletionSteps deletionSteps;

  public TenantDeletionService(
      TenantQueryRepository tenantQueryRepository,
      TenantCommandRepository tenantCommandRepository,
      TenantDeletionSteps deletionSteps) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.tenantCommandRepository = tenantCommandRepository;
    this.deletionSteps = deletionSteps;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantDeleteRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TenantIdentifier tenantIdentifier = request.tenantIdentifier();
    // 1. Retrieve existing tenant
    Tenant before = tenantQueryRepository.findWithDisabled(tenantIdentifier, true);
    if (!before.exists()) {
      throw new ResourceNotFoundException("Tenant not found: " + tenantIdentifier.value());
    }

    builder.withBefore(before);

    // 2. Dry-run check
    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", tenantIdentifier.value());
      response.put("dry_run", true);
      return new TenantManagementResponse(TenantManagementStatus.OK, response);
    }

    // 3. What features hold outside this database, undone while the rows still say what it is
    deletionSteps.runBefore(before);

    // 4. Repository operation
    tenantCommandRepository.delete(tenantIdentifier);

    // 5. Return NO_CONTENT response
    return new TenantManagementResponse(TenantManagementStatus.NO_CONTENT, Map.of());
  }
}
