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

package org.idp.server.control_plane.management.authentication.configuration.handler;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for authentication configuration management operations.
 *
 * <p>This interface defines the contract for authentication configuration management Service
 * implementations following the Handler-Service-Repository pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Execute business logic for authentication configuration operations
 *   <li>Create/update/delete authentication configurations
 *   <li>Query repository for authentication configuration data
 *   <li>Build response with appropriate data
 * </ul>
 *
 * <h2>NOT Responsibilities</h2>
 *
 * <ul>
 *   <li>Permission verification (handled by Handler)
 *   <li>Organization access control (handled by Handler)
 *   <li>Tenant retrieval (handled by Handler)
 *   <li>Audit logging (handled by EntryService)
 *   <li>Transaction management (handled by EntryService)
 * </ul>
 *
 * @param <REQUEST> the request type for the specific operation
 * @see AuthenticationConfigManagementResult
 * @see AuthenticationConfigManagementHandler
 */
public interface AuthenticationConfigManagementService<REQUEST> {

  /**
   * Executes the authentication configuration management operation.
   *
   * @param tenant the tenant context (already retrieved by Handler)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return AuthenticationConfigManagementResult containing operation outcome
   */
  AuthenticationConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      REQUEST request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
