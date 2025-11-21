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

package org.idp.server.control_plane.management.organization.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.organization.OrganizationManagementContextBuilder;
import org.idp.server.control_plane.management.organization.io.OrganizationDeleteRequest;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementResponse;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for deleting organizations.
 *
 * <p>Handles organization deletion logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization existence verification
 *   <li>Organization deletion from repository
 * </ul>
 */
public class OrganizationDeletionService
    implements OrganizationManagementService<OrganizationDeleteRequest> {

  private final OrganizationRepository organizationRepository;

  public OrganizationDeletionService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Override
  public OrganizationManagementResponse execute(
      OrganizationManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      OrganizationDeleteRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    OrganizationIdentifier organizationIdentifier = request.organizationIdentifier();
    // 1. Retrieve existing organization
    Organization before = organizationRepository.get(organizationIdentifier);
    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Organization not found: " + organizationIdentifier.value());
    }

    builder.withBefore(before);

    // 2. Dry-run check
    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", organizationIdentifier.value());
      response.put("dry_run", true);
      return new OrganizationManagementResponse(OrganizationManagementStatus.OK, response);
    }

    // 3. Repository operation
    organizationRepository.delete(organizationIdentifier);

    // 4. Return NO_CONTENT response
    return new OrganizationManagementResponse(OrganizationManagementStatus.NO_CONTENT, Map.of());
  }
}
