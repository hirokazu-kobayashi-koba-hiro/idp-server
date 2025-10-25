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

package org.idp.server.control_plane.management.oidc.authorization.handler;

import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for authorization server management operations.
 *
 * <p>Part of Handler/Service pattern. Implementations handle specific authorization server
 * management operations (get, update).
 *
 * @param <T> the request type for this service
 * @see AuthorizationServerManagementHandler
 * @see AuthorizationServerManagementResult
 */
public interface AuthorizationServerManagementService<T> {

  /**
   * Executes the authorization server management operation.
   *
   * @param contextBuilder the context builder for audit logging
   * @param tenant the tenant
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the request object (type varies by operation, may be null for get)
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run
   * @return the response of the operation
   */
  AuthorizationServerManagementResponse execute(
      AuthorizationServerManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
