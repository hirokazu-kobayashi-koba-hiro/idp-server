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

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.SecurityEventIdentifier;
import org.idp.server.platform.security.repository.SecurityEventQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving a single security event.
 *
 * <p>Handles security event retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Security event existence verification
 *   <li>Security event retrieval from repository
 * </ul>
 */
public class SecurityEventFindService
    implements SecurityEventManagementService<SecurityEventIdentifier> {

  private final SecurityEventQueryRepository securityEventQueryRepository;

  public SecurityEventFindService(SecurityEventQueryRepository securityEventQueryRepository) {
    this.securityEventQueryRepository = securityEventQueryRepository;
  }

  @Override
  public SecurityEventManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEvent event = securityEventQueryRepository.find(tenant, identifier);

    if (!event.exists()) {
      throw new ResourceNotFoundException("Security event not found: " + identifier.value());
    }

    SecurityEventManagementResponse response =
        new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, event.toMap());
    return SecurityEventManagementResult.success(tenant, response);
  }
}
