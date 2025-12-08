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

package org.idp.server.core.extension.ciba;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.identity.SecurityEventUserCreatable;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

public class CibaFlowEventCreator implements SecurityEventUserCreatable {

  Tenant tenant;
  BackchannelAuthenticationRequest request;
  User user;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;
  Map<String, Object> authenticationResult;
  RequestAttributes requestAttributes;
  ClientAttributes clientAttributes;

  public CibaFlowEventCreator(
      Tenant tenant,
      BackchannelAuthenticationRequest request,
      User user,
      SecurityEventType securityEventType,
      RequestAttributes requestAttributes,
      ClientAttributes clientAttributes) {
    this.tenant = tenant;
    this.request = request;
    this.user = user;
    this.securityEventType = securityEventType;
    this.securityEventDescription = new SecurityEventDescription(securityEventType.value());
    this.authenticationResult = Map.of();
    this.requestAttributes = requestAttributes;
    this.clientAttributes = clientAttributes;
  }

  public CibaFlowEventCreator(
      Tenant tenant,
      BackchannelAuthenticationRequest request,
      User user,
      SecurityEventType securityEventType,
      Map<String, Object> authenticationResult,
      RequestAttributes requestAttributes,
      ClientAttributes clientAttributes) {
    this.tenant = tenant;
    this.request = request;
    this.user = user;
    this.securityEventType = securityEventType;
    this.securityEventDescription = new SecurityEventDescription(securityEventType.value());
    this.authenticationResult = authenticationResult != null ? authenticationResult : Map.of();
    this.requestAttributes = requestAttributes;
    this.clientAttributes = clientAttributes;
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

    String clientName = resolveClientName();
    SecurityEventClient securityEventClient =
        new SecurityEventClient(request.requestedClientId().value(), clientName);
    builder.add(securityEventClient);

    if (user != null) {
      SecurityEventUser securityEventUser = createSecurityEventUser(user);
      builder.add(securityEventUser);
      detailsMap.put("user", toDetailWithSensitiveData(user, tenant));
    }

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    if (securityEventType.isFailure() && !authenticationResult.isEmpty()) {
      detailsMap.put("execution_result", authenticationResult);
    }

    SecurityEventDetail securityEventDetail =
        createSecurityEventDetailWithScrubbing(detailsMap, tenant);

    builder.add(securityEventDetail);

    return builder.build();
  }

  private String resolveClientName() {
    if (clientAttributes == null) {
      return "";
    }
    if (!clientAttributes.hasClientName()) {
      return "";
    }
    return clientAttributes.clientName().value();
  }
}
