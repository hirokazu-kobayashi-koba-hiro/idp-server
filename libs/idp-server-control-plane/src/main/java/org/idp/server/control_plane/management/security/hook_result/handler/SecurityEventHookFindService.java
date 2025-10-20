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

package org.idp.server.control_plane.management.security.hook_result.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a single security event hook result.
 *
 * <p>Handles security event hook result retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Security event hook result existence verification
 *   <li>Security event hook result retrieval from repository
 * </ul>
 */
public class SecurityEventHookFindService
    implements SecurityEventHookManagementService<SecurityEventHookResultIdentifier> {

  private final SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository;

  public SecurityEventHookFindService(
      SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository) {
    this.securityEventHookResultQueryRepository = securityEventHookResultQueryRepository;
  }

  @Override
  public SecurityEventHookManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookResult hookResult =
        securityEventHookResultQueryRepository.find(tenant, identifier);

    if (!hookResult.exists()) {
      throw new ResourceNotFoundException(
          "Security event hook result not found: " + identifier.value());
    }

    SecurityEventHookManagementResponse response =
        new SecurityEventHookManagementResponse(
            SecurityEventHookManagementStatus.OK, hookResult.toMap());
    return SecurityEventHookManagementResult.success(tenant, response);
  }
}
