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

package org.idp.server.control_plane.management.security.hook.handler;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Generic service interface for security event hook configuration management operations.
 *
 * <p>This interface enables Strategy pattern with type-safe request handling:
 *
 * <ul>
 *   <li>Each operation (create, findList, get, update, delete) has its own Service implementation
 *   <li>Services are registered in a Map&lt;String, Service&lt;?&gt;&gt;
 *   <li>Handler selects and executes the appropriate service based on method name
 * </ul>
 *
 * <h2>Type Parameter</h2>
 *
 * <ul>
 *   <li>T: Operation-specific request type
 *   <li>create: SecurityEventHookRequest
 *   <li>findList: No request object (uses method parameters)
 *   <li>get: SecurityEventHookConfigurationIdentifier
 *   <li>update: SecurityEventHookConfigUpdateRequest (identifier + request)
 *   <li>delete: SecurityEventHookConfigurationIdentifier
 * </ul>
 *
 * <h2>Exception Handling</h2>
 *
 * <p>Services throw ManagementApiException for domain errors. Handler catches and wraps in Result.
 *
 * @param <T> the operation-specific request type
 */
public interface SecurityEventHookConfigManagementService<T> {

  /**
   * Executes the security event hook configuration management operation.
   *
   * @param tenant tenant context
   * @param operator user performing the operation
   * @param oAuthToken authentication token
   * @param request operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun whether to simulate the operation without persisting changes
   * @return operation result (success or error wrapped in Result)
   */
  SecurityEventHookConfigManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
