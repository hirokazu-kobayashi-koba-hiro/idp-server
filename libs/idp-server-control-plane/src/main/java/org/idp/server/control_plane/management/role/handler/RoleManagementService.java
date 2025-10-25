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

import org.idp.server.control_plane.management.role.RoleManagementContextBuilder;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementResult;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for role management operations.
 *
 * <p>Defines the contract for role management business logic following the Handler/Service pattern.
 * Each concrete implementation handles a specific role operation (create, findList, get, update,
 * removePermissions, delete).
 *
 * <h2>Design Pattern</h2>
 *
 * <p>This interface is part of the Handler/Service pattern where:
 *
 * <ul>
 *   <li>Handler orchestrates requests and delegates to appropriate Service implementations
 *   <li>Service implementations contain pure business logic
 *   <li>Services throw ManagementApiException on validation/verification failures
 *   <li>Handler catches exceptions and converts them to Result objects
 * </ul>
 *
 * <h2>Context Builder Pattern</h2>
 *
 * <p>Services receive a ContextBuilder for incremental context construction:
 *
 * <pre>{@code
 * builder.withAfter(role);  // Service populates builder
 * AuditableContext context = builder.build();  // Handler builds complete context
 * }</pre>
 *
 * @param <T> the specific request type for the operation
 * @see RoleManagementHandler
 * @see RoleManagementResult
 */
public interface RoleManagementService<T> {

  /**
   * Executes the role management operation.
   *
   * @param builder context builder for incremental context construction
   * @param tenant the tenant
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation response (Handler wraps in Result)
   */
  RoleManagementResponse execute(
      RoleManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
