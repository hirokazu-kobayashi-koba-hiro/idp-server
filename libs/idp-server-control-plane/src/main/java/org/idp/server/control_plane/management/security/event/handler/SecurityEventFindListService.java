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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.security.event.SecurityEventManagementContextBuilder;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementFindListRequest;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementStatus;
import org.idp.server.control_plane.management.security.event.validator.SecurityEventQueryValidator;
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
    implements SecurityEventManagementService<SecurityEventManagementFindListRequest> {

  private final SecurityEventQueryRepository securityEventQueryRepository;

  public SecurityEventFindListService(SecurityEventQueryRepository securityEventQueryRepository) {
    this.securityEventQueryRepository = securityEventQueryRepository;
  }

  @Override
  public SecurityEventManagementResponse execute(
      SecurityEventManagementContextBuilder builder,
      Tenant targetTenant,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventManagementFindListRequest request,
      RequestAttributes requestAttributes) {

    SecurityEventQueries queries = request.queries();

    new SecurityEventQueryValidator(queries).validate();

    List<SecurityEvent> events = securityEventQueryRepository.findList(targetTenant, queries);

    int limit = queries.limit();
    boolean hasMore = events.size() > limit;

    List<SecurityEvent> resultEvents = hasMore ? events.subList(0, limit) : events;

    Map<String, Object> response = new HashMap<>();
    response.put("list", resultEvents.stream().map(SecurityEvent::toMap).toList());
    response.put("has_more", hasMore);
    response.put("limit", limit);
    response.put("offset", queries.offset());

    if (hasMore) {
      SecurityEvent lastEvent = resultEvents.get(resultEvents.size() - 1);
      String cursorValue =
          lastEvent.createdAt().valueAsString() + "|" + lastEvent.identifier().value();
      String nextCursor =
          Base64.getEncoder().encodeToString(cursorValue.getBytes(StandardCharsets.UTF_8));
      response.put("next_cursor", nextCursor);
    }

    return new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
  }
}
