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

package org.idp.server.control_plane.management.identity.user;

import java.util.HashMap;
import org.idp.server.core.openid.identity.SecurityEventUserCreatable;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Creates SecurityEvents for Management API operations.
 *
 * <p>This creator generates SecurityEvents specifically for administrative operations performed
 * through the Management API. It includes both operator (admin) and target user information in the
 * event details.
 */
public class ManagementEventCreator implements SecurityEventUserCreatable {

  private final Tenant tenant;
  private final User operator;
  private final User targetUser;
  private final OAuthToken oAuthToken;
  private final SecurityEventType securityEventType;
  private final SecurityEventDescription securityEventDescription;
  private final RequestAttributes requestAttributes;

  public ManagementEventCreator(
      Tenant tenant,
      User operator,
      User targetUser,
      OAuthToken oAuthToken,
      SecurityEventType securityEventType,
      RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.operator = operator;
    this.targetUser = targetUser;
    this.oAuthToken = oAuthToken;
    this.securityEventType = securityEventType;
    this.securityEventDescription = new SecurityEventDescription(securityEventType.value());
    this.requestAttributes = requestAttributes;
  }

  public SecurityEvent create() {
    HashMap<String, Object> detailsMap = new HashMap<>();
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(securityEventType);
    builder.add(securityEventDescription);

    // Tenant information
    SecurityEventTenant securityEventTenant =
        new SecurityEventTenant(
            tenant.identifier().value(), tenant.tokenIssuer(), tenant.name().value());
    builder.add(securityEventTenant);

    // Client information from OAuth token
    SecurityEventClient securityEventClient =
        new SecurityEventClient(
            oAuthToken.requestedClientId().value(),
            oAuthToken.clientAttributes().clientName().value());
    builder.add(securityEventClient);

    // Management operations must always have a target user
    if (targetUser == null) {
      throw new IllegalArgumentException("Target user must not be null for management operations");
    }
    SecurityEventUser securityEventUser = createSecurityEventUser(targetUser);
    builder.add(securityEventUser);

    // Add management operation details
    detailsMap.put("operation_type", "management_api");

    // Add operator information
    detailsMap.put("operator", toDetailWithSensitiveData(operator, tenant));

    // Add target user information
    detailsMap.put("user", toDetailWithSensitiveData(targetUser, tenant));

    // Add OAuth token details
    HashMap<String, Object> tokenInfo = new HashMap<>();
    tokenInfo.put("client_id", oAuthToken.clientAttributes().identifier().value());
    tokenInfo.put("scopes", oAuthToken.scopeAsList());
    detailsMap.put("oauth_token", tokenInfo);

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    SecurityEventDetail securityEventDetail =
        createSecurityEventDetailWithScrubbing(detailsMap, tenant);

    builder.add(securityEventDetail);

    return builder.build();
  }
}
