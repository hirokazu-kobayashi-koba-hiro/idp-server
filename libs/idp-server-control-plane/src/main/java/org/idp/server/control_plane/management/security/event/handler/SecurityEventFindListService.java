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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.repository.SecurityEventQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving security events list.
 *
 * <p>Handles security event list retrieval logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query security events with pagination
 *   <li>Return total count for pagination
 * </ul>
 */
public class SecurityEventFindListService
    implements SecurityEventManagementService<SecurityEventQueries> {

  private final SecurityEventQueryRepository securityEventQueryRepository;

  public SecurityEventFindListService(SecurityEventQueryRepository securityEventQueryRepository) {
    this.securityEventQueryRepository = securityEventQueryRepository;
  }

  @Override
  public SecurityEventManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventQueries queries,
      RequestAttributes requestAttributes) {

    long totalCount = securityEventQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      SecurityEventManagementResponse managementResponse =
          new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
      return SecurityEventManagementResult.success(tenant, managementResponse);
    }

    List<SecurityEvent> events = securityEventQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", events.stream().map(SecurityEvent::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    SecurityEventManagementResponse managementResponse =
        new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
    return SecurityEventManagementResult.success(tenant, managementResponse);
  }
}
