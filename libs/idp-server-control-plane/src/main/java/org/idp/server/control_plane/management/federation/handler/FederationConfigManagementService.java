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

package org.idp.server.control_plane.management.federation.handler;

import org.idp.server.control_plane.management.federation.FederationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for federation configuration management operations.
 *
 * <p>Defines the contract for services in the Handler/Service pattern. Each operation (create,
 * update, delete, etc.) has its own service implementation.
 *
 * <p>Services are responsible for:
 *
 * <ul>
 *   <li>Request validation (throwing InvalidRequestException)
 *   <li>Business logic execution
 *   <li>Populating context builder with before/after states
 *   <li>Repository operations (or dry-run simulation)
 * </ul>
 *
 * @param <T> the request type (varies by operation)
 */
public interface FederationConfigManagementService<T> {

  /**
   * Executes the federation configuration management operation.
   *
   * @param builder the context builder (to be populated with before/after states)
   * @param tenant the tenant
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the request (type varies by operation)
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run
   * @return the response (not Result - Handler will convert to Result)
   */
  FederationConfigManagementResponse execute(
      FederationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
