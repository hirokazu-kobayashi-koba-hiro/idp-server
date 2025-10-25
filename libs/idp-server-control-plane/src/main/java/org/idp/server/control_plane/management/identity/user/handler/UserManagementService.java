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

package org.idp.server.control_plane.management.identity.user.handler;

import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for user management operations.
 *
 * <p>Each concrete implementation handles a specific user management operation (create, update,
 * delete, etc.). This follows the Strategy pattern where UserManagementHandler selects the
 * appropriate service based on the operation.
 *
 * <h2>Design Pattern</h2>
 *
 * <pre>{@code
 * UserManagementHandler (orchestrator)
 *   └─ Map<String, UserManagementService<?>>
 *       ├─ UserCreationService (implements UserManagementService<UserRegistrationRequest>)
 *       ├─ UserUpdateService (implements UserManagementService<UserUpdateRequest>)
 *       └─ UserDeletionService (implements UserManagementService<UserDeletionRequest>)
 * }</pre>
 *
 * <h2>Implementation Example</h2>
 *
 * <pre>{@code
 * public class UserCreationService implements UserManagementService<UserRegistrationRequest> {
 *     @Override
 *     public UserManagementResult execute(
 *         Tenant tenant,
 *         User operator,
 *         OAuthToken oAuthToken,
 *         UserRegistrationRequest request,  // Type-safe, no cast needed!
 *         RequestAttributes requestAttributes,
 *         boolean dryRun) {
 *         // 1. Validation
 *         // 2. Context creation
 *         // 3. Verification
 *         // 4. Repository operation
 *         // 5. Event publishing
 *         return UserManagementResult.success(...);
 *     }
 * }
 * }</pre>
 *
 * @param <REQUEST> the type of request object for this operation
 * @see UserManagementHandler
 * @see UserManagementResult
 */
public interface UserManagementService<REQUEST> {

  /**
   * Executes the user management operation.
   *
   * <p>Receives context builder from Handler, executes business logic, and populates the builder
   * with operation data.
   *
   * @param builder the context builder to populate with operation data
   * @param tenant the tenant context (retrieved by Handler layer)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object (type-safe via generics)
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return UserManagementResponse containing outcome
   */
  UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      REQUEST request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
