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

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for tenant management operations.
 *
 * <p>Each operation (create, update, delete, etc.) has its own Service implementation.
 *
 * @param <T> the request type for this operation
 */
public interface TenantManagementService<T> {

  /**
   * Executes the tenant management operation.
   *
   * <p>Responsibilities:
   *
   * <ul>
   *   <li>Request validation
   *   <li>Context creation
   *   <li>Business rule verification
   *   <li>Repository operations
   *   <li>Event publishing
   * </ul>
   *
   * <p>NOT responsibilities (handled by Handler/EntryService):
   *
   * <ul>
   *   <li>Permission checking
   *   <li>Audit logging
   *   <li>Transaction management
   * </ul>
   *
   * @param adminTenant the admin tenant (passed from Handler)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return TenantManagementResult containing operation outcome
   */
  TenantManagementResult execute(
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
