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

import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class UserOperationEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public UserOperationEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      AuthenticationTransaction authenticationTransaction,
      AuthenticationInteractionType type,
      boolean result,
      RequestAttributes requestAttributes) {
    SecurityEventType securityEventType = formatSecurityEventType(type, result);
    UserOperationEventCreator eventCreator =
        new UserOperationEventCreator(
            tenant, authenticationTransaction, securityEventType, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }

  private SecurityEventType formatSecurityEventType(
      AuthenticationInteractionType type, boolean result) {
    String prefix = type.formatSnakeCase();
    if (result) {
      return new SecurityEventType(prefix + "_success");
    }
    return new SecurityEventType(prefix + "_failure");
  }
}
