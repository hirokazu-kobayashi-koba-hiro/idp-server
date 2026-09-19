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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builds the security events of the Client Instance registration flow.
 *
 * <p>There is no authenticated user to attribute these to: the endpoints are unauthenticated, and
 * what the registration establishes is a client credential, not a session. The event therefore
 * carries the tenant, whatever of client_id / device_id / instance_id the step actually knows, and
 * the caller's address.
 */
public class ClientInstanceRegistrationEventCreator {

  Tenant tenant;
  SecurityEventType type;
  Map<String, Object> details;
  RequestAttributes requestAttributes;

  public ClientInstanceRegistrationEventCreator(
      Tenant tenant,
      SecurityEventType type,
      Map<String, Object> details,
      RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.type = type;
    this.details = details;
    this.requestAttributes = requestAttributes;
  }

  public SecurityEvent create() {
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(type);
    builder.add(new SecurityEventDescription(type.value()));
    builder.add(
        new SecurityEventTenant(
            tenant.identifier().value(), tenant.tokenIssuer(), tenant.name().value()));

    // Always set, even when unknown: SecurityEvent#clientId dereferences this, and the publish
    // runs on an async thread where the resulting NPE would drop the event without a trace.
    Object clientId = details.get("client_id");
    String clientIdValue = clientId instanceof String value ? value : "";
    builder.add(new SecurityEventClient(clientIdValue, ""));

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());

    HashMap<String, Object> detailsMap = new HashMap<>(details);
    detailsMap.putAll(requestAttributes.toMap());
    builder.add(new SecurityEventDetail(detailsMap));

    return builder.build();
  }
}
