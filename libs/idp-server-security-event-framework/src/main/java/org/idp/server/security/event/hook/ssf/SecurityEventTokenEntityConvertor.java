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

package org.idp.server.security.event.hook.ssf;

import java.util.Map;
import org.idp.server.platform.security.SecurityEvent;

public class SecurityEventTokenEntityConvertor {

  SecurityEvent securityEvent;

  public SecurityEventTokenEntityConvertor(SecurityEvent securityEvent) {
    this.securityEvent = securityEvent;
  }

  public SecurityEventTokenEntity convert() {
    String tokenIssuer = securityEvent.tokenIssuer();
    String requestedClientId = securityEvent.clientId();
    SharedSecurityEvent sharedSecurityEvent = convertToSecurityEvent();

    return new SecurityEventTokenEntity(tokenIssuer, requestedClientId, sharedSecurityEvent);
  }

  private SharedSecurityEvent convertToSecurityEvent() {
    SecurityEventType securityEventType = SecurityEventType.of(securityEvent.type());

    Map<String, String> subjectMap =
        Map.of("sub", securityEvent.userSub(), "iss", securityEvent.tokenIssuerValue());
    SecurityEventSubjectPayload securityEventSubjectPayload =
        new SecurityEventSubjectPayload(subjectMap);
    SecurityEventSubject subject =
        new SecurityEventSubject(SecuritySubjectFormat.iss_sub, securityEventSubjectPayload);

    Map<String, Object> payload = securityEvent.toMap();
    SecurityEventPayload eventPayload = new SecurityEventPayload(payload);

    return new SharedSecurityEvent(securityEventType, subject, eventPayload);
  }
}
