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
import org.idp.server.core.oidc.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.type.oauth.TokenIssuer;

/**
 * SET
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8417">SET</a>
 */
public class SecurityEventTokenEntity {
  TokenIssuer issuer;
  RequestedClientId requestedClientId;
  SharedSecurityEvent sharedSecurityEvent;

  public SecurityEventTokenEntity() {}

  public SecurityEventTokenEntity(
      String issuer, String requestedClientId, SharedSecurityEvent sharedSecurityEvent) {
    this.issuer = new TokenIssuer(issuer);
    this.requestedClientId = new RequestedClientId(requestedClientId);
    this.sharedSecurityEvent = sharedSecurityEvent;
  }

  public TokenIssuer issuer() {
    return issuer;
  }

  public String issuerValue() {
    return issuer.value();
  }

  public RequestedClientId clientId() {
    return requestedClientId;
  }

  public String clientIdValue() {
    return requestedClientId.value();
  }

  public SharedSecurityEvent securityEvent() {
    return sharedSecurityEvent;
  }

  public String securityEventAsString() {
    return sharedSecurityEvent.type().name();
  }

  public boolean isDefinedEvent() {
    return sharedSecurityEvent.isDefined();
  }

  public Map<String, Object> eventAsMap() {
    return sharedSecurityEvent.eventAsMap();
  }
}
