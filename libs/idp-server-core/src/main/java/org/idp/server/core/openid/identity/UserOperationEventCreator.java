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

package org.idp.server.core.openid.identity;

import java.util.HashMap;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

public class UserOperationEventCreator {

  Tenant tenant;
  AuthenticationTransaction authenticationTransaction;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;
  RequestAttributes requestAttributes;

  public UserOperationEventCreator(
      Tenant tenant,
      AuthenticationTransaction authenticationTransaction,
      SecurityEventType securityEventType,
      RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.authenticationTransaction = authenticationTransaction;
    this.securityEventType = securityEventType;
    this.securityEventDescription = new SecurityEventDescription(securityEventType.value());
    this.requestAttributes = requestAttributes;
  }

  public UserOperationEventCreator(
      Tenant tenant,
      AuthenticationTransaction authenticationTransaction,
      SecurityEventType securityEventType,
      SecurityEventDescription securityEventDescription,
      RequestAttributes requestAttributes) {
    this.authenticationTransaction = authenticationTransaction;
    this.securityEventType = securityEventType;
    this.securityEventDescription = securityEventDescription;
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

    SecurityEventClient securityEventClient =
        new SecurityEventClient(
            authenticationTransaction.request().requestedClientId().value(),
            authenticationTransaction.request().clientAttributes().clientName().value());
    builder.add(securityEventClient);

    User user = authenticationTransaction.request().user();

    if (user != null) {
      SecurityEventUser securityEventUser =
          new SecurityEventUser(user.sub(), user.name(), user.externalUserId());
      builder.add(securityEventUser);
      detailsMap.put("user", user.toMap());
    }

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    SecurityEventDetail securityEventDetail = new SecurityEventDetail(detailsMap);

    builder.add(securityEventDetail);

    return builder.build();
  }
}
