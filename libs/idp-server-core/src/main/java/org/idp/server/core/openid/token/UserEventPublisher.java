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

package org.idp.server.core.openid.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.SecurityEventUserCreatable;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

public class UserEventPublisher implements SecurityEventUserCreatable {

  SecurityEventPublisher securityEventPublisher;

  public UserEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    UserEventCreator eventCreator =
        new UserEventCreator(tenant, oAuthToken, type, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      DefaultSecurityEventType type,
      Map<String, Object> executionResult,
      RequestAttributes requestAttributes) {
    UserEventCreator eventCreator =
        new UserEventCreator(tenant, oAuthToken, type, executionResult, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }

  // TODO to be more correct
  public void publish(
      Tenant tenant,
      RequestedClientId requestedClientId,
      User user,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    HashMap<String, Object> detailsMap = new HashMap<>();
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(type.toEventType());
    builder.add(type.toEventDescription());

    SecurityEventTenant securityEventTenant =
        new SecurityEventTenant(
            tenant.identifier().value(), tenant.tokenIssuer(), tenant.name().value());
    builder.add(securityEventTenant);

    SecurityEventClient securityEventClient =
        new SecurityEventClient(requestedClientId.value(), "");
    builder.add(securityEventClient);

    SecurityEventUser securityEventUser = createSecurityEventUser(user);
    builder.add(securityEventUser);
    detailsMap.put("user", toDetailWithSensitiveData(user, tenant));

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    SecurityEventDetail securityEventDetail =
        createSecurityEventDetailWithScrubbing(detailsMap, tenant);
    builder.add(securityEventDetail);
    SecurityEvent securityEvent = builder.build();
    securityEventPublisher.publish(securityEvent);
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      SecurityEventType securityEventType,
      RequestAttributes requestAttributes) {
    SecurityEventDescription securityEventDescription =
        new SecurityEventDescription(securityEventType.value());
    UserEventCreator eventCreator =
        new UserEventCreator(
            tenant, oAuthToken, securityEventType, securityEventDescription, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      SecurityEventType securityEventType,
      Map<String, Object> executionResult,
      RequestAttributes requestAttributes) {
    SecurityEventDescription securityEventDescription =
        new SecurityEventDescription(securityEventType.value());
    UserEventCreator eventCreator =
        new UserEventCreator(
            tenant,
            oAuthToken,
            securityEventType,
            securityEventDescription,
            executionResult,
            requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
