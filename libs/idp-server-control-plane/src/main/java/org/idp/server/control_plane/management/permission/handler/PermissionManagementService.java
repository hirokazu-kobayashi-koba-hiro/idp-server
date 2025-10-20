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

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for permission management operations.
 *
 * <p>Defines the contract for permission management business logic. Service implementations are
 * pure business logic with no orchestration concerns.
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li>Throws ManagementApiException on validation/verification failures
 *   <li>Returns PermissionManagementResult on success
 *   <li>Handler layer catches exceptions and converts to Result
 * </ul>
 *
 * @param <REQUEST> the request type for this service
 * @see PermissionManagementHandler
 * @see PermissionManagementResult
 */
public interface PermissionManagementService<REQUEST> {

  /**
   * Executes the permission management operation.
   *
   * @param tenant the tenant
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run
   * @return the operation result
   */
  PermissionManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      REQUEST request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
