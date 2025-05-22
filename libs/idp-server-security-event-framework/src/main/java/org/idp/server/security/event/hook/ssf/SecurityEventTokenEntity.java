/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.security.event.hook.ssf;

import java.util.Map;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.TokenIssuer;

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
