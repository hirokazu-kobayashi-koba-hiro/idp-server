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

package org.idp.server.control_plane.management.security.event.handler;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for security event management operations.
 *
 * <p>All implementations follow the Handler/Service pattern where:
 *
 * <ul>
 *   <li>Handler: Handles cross-cutting concerns (permissions, tenant retrieval, exception catching)
 *   <li>Service: Contains pure business logic for each operation
 * </ul>
 *
 * <p>Services throw ManagementApiException on validation/verification failures, which are caught by
 * Handler and converted to Result objects.
 *
 * @param <T> the request type for the operation
 */
public interface SecurityEventManagementService<T> {

  /**
   * Executes the security event management operation.
   *
   * @param tenant the tenant context
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @return SecurityEventManagementResult containing operation outcome or exception
   */
  SecurityEventManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes);
}
