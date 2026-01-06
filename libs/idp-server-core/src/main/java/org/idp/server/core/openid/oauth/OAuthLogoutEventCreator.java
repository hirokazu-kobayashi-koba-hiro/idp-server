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

package org.idp.server.core.openid.oauth;

import java.util.HashMap;
import org.idp.server.core.openid.oauth.logout.OAuthLogoutContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

/**
 * OAuthLogoutEventCreator
 *
 * <p>Creates security events for RP-Initiated Logout.
 */
public class OAuthLogoutEventCreator {

  Tenant tenant;
  OAuthLogoutContext context;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;
  RequestAttributes requestAttributes;

  public OAuthLogoutEventCreator(
      Tenant tenant,
      OAuthLogoutContext context,
      SecurityEventType securityEventType,
      RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.context = context;
    this.securityEventType = securityEventType;
    this.securityEventDescription = new SecurityEventDescription(securityEventType.value());
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

    if (context.hasClientConfiguration()) {
      SecurityEventClient securityEventClient =
          new SecurityEventClient(
              context.clientId().value(), context.clientConfiguration().clientName().value());
      builder.add(securityEventClient);
    }

    // Get subject from id_token_hint if available
    String subject = context.subject();
    if (subject != null && !subject.isEmpty()) {
      SecurityEventUser securityEventUser = new SecurityEventUser(subject, "", "", "", "");
      builder.add(securityEventUser);
      detailsMap.put("subject", subject);
    }

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    SecurityEventDetail securityEventDetail = new SecurityEventDetail(detailsMap);
    builder.add(securityEventDetail);

    return builder.build();
  }
}
