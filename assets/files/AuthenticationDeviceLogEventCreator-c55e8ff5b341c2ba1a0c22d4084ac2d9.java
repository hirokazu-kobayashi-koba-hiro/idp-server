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

package org.idp.server.core.openid.identity.device;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.SecurityEventUserCreatable;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

public class AuthenticationDeviceLogEventCreator implements SecurityEventUserCreatable {

  Tenant tenant;
  User user;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;
  AuthenticationDeviceLogRequest request;
  RequestAttributes requestAttributes;

  public AuthenticationDeviceLogEventCreator(
      Tenant tenant,
      User user,
      SecurityEventType securityEventType,
      AuthenticationDeviceLogRequest request,
      RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.user = user;
    this.securityEventType = securityEventType;
    this.securityEventDescription = new SecurityEventDescription(securityEventType.value());
    this.request = request;
    this.requestAttributes = requestAttributes;
  }

  public SecurityEvent create() {
    HashMap<String, Object> detailsMap = new HashMap<>();
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(securityEventType);
    builder.add(securityEventDescription);

    SecurityEventTenant securityEventTenant =
        new SecurityEventTenant(
            tenant.identifier().value(), tenant.tokenIssuer(), tenant.name().value());
    builder.add(securityEventTenant);

    // Add client from request or use default for authentication device log
    builder.add(
        new SecurityEventClient(request.clientIdOrDefault(), request.clientNameOrDefault()));

    if (user != null && user.exists()) {
      SecurityEventUser securityEventUser = createSecurityEventUser(user);
      builder.add(securityEventUser);
      detailsMap.put("user", toDetailWithSensitiveData(user, tenant));
    }

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    Map<String, Object> requestMap = request.toMap();
    if (!requestMap.isEmpty()) {
      detailsMap.put("execution_result", requestMap);
    }

    SecurityEventDetail securityEventDetail =
        createSecurityEventDetailWithScrubbing(detailsMap, tenant);

    builder.add(securityEventDetail);

    return builder.build();
  }
}
