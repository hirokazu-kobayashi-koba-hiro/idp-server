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

package org.idp.server.control_plane.management.authentication.transaction.handler;

import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for authentication transaction management operations.
 *
 * <p>Part of Handler/Service pattern. Implementations handle specific authentication transaction
 * management operations (get, findList).
 *
 * @param <T> the request type for this service
 * @see AuthenticationTransactionManagementHandler
 * @see AuthenticationTransactionManagementResult
 */
public interface AuthenticationTransactionManagementService<T> {

  /**
   * Executes the authentication transaction management operation.
   *
   * @param contextBuilder the context builder for audit logging
   * @param tenant the tenant
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the request object (type varies by operation)
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run
   * @return the response of the operation
   */
  AuthenticationTransactionManagementResponse execute(
      AuthenticationTransactionManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
