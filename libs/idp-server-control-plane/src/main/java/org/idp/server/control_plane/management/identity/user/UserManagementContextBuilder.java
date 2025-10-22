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

package org.idp.server.control_plane.management.identity.user;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Common interface for user management context builders.
 *
 * <p>Enables incremental construction of audit contexts, supporting error scenarios where full
 * context may not be available (e.g., Tenant retrieval failure).
 *
 * <h2>Design Pattern</h2>
 *
 * <pre>{@code
 * // Handler layer: Early builder creation (before Tenant retrieval)
 * UserManagementContextBuilder builder = service.createContextBuilder(
 *     tenantIdentifier, organizationIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);
 *
 * try {
 *   // Add data as it becomes available
 *   Tenant tenant = tenantRepository.get(tenantIdentifier);
 *   builder.withTenant(tenant);
 *
 *   Organization org = organizationRepository.get(organizationIdentifier);
 *   builder.withOrganization(org);
 *
 *   // Full context for success case
 *   AuditableContext context = builder.build();
 * } catch (ManagementApiException e) {
 *   // Partial context for audit logging (may not have Tenant/Organization)
 *   AuditableContext errorContext = builder.buildPartial(e);
 * }
 * }</pre>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Incremental addition of Tenant/Organization as they're retrieved
 *   <li>Full context construction for success scenarios
 *   <li>Partial context construction for error scenarios
 *   <li>Exception tracking for audit logging
 * </ul>
 *
 * @see UserRegistrationContextBuilder
 */
public interface UserManagementContextBuilder {

  /**
   * Adds Tenant to the builder after retrieval.
   *
   * @param tenant the retrieved Tenant
   * @return this builder for chaining
   */
  UserManagementContextBuilder withTenant(Tenant tenant);

  /**
   * Adds Organization to the builder after retrieval (optional for system-level APIs).
   *
   * @param organization the retrieved Organization
   * @return this builder for chaining
   */
  UserManagementContextBuilder withOrganization(Organization organization);

  /**
   * Builds complete AuditableContext for success scenarios.
   *
   * @return full context with all available data
   * @throws IllegalStateException if required data is missing
   */
  AuditableContext build();

  /**
   * Builds partial AuditableContext for error scenarios.
   *
   * <p>Creates context with available data only, suitable for audit logging when operations fail
   * before full data is retrieved.
   *
   * @param exception the exception that caused the failure (nullable)
   * @return partial context with error information
   */
  AuditableContext buildPartial(ManagementApiException exception);
}
