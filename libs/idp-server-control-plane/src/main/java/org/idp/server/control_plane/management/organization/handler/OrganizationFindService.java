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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.organization.OrganizationManagementContextBuilder;
import org.idp.server.control_plane.management.organization.io.OrganizationFindRequest;
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
 * Service for finding a single organization.
 *
 * <p>Handles organization retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval from repository
 *   <li>Existence verification
 * </ul>
 *
 * <h2>NOT Responsibilities (handled by Handler/EntryService)</h2>
 *
 * <ul>
 *   <li>Permission checking
 *   <li>Admin tenant retrieval
 *   <li>Audit logging
 *   <li>Transaction management
 * </ul>
 */
public class OrganizationFindService
    implements OrganizationManagementService<OrganizationFindRequest> {

  private final OrganizationRepository organizationRepository;

  public OrganizationFindService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Override
  public OrganizationManagementResponse execute(
      OrganizationManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      OrganizationFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    OrganizationIdentifier organizationIdentifier = request.organizationIdentifier();
    // 1. Retrieve organization
    Organization organization = organizationRepository.get(organizationIdentifier);
    if (!organization.exists()) {
      throw new ResourceNotFoundException(
          "Organization not found: " + organizationIdentifier.value());
    }

    // 2. Return success result (no context for read-only operation)
    return new OrganizationManagementResponse(
        OrganizationManagementStatus.OK, organization.toMap());
  }
}
