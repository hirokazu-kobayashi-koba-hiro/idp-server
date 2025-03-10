package org.idp.server.core.sharedsignal;

import java.util.Map;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

/**
 * SET
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8417">SET</a>
 */
public class SecurityEventTokenEntity {
  TokenIssuer issuer;
  ClientId clientId;
  SecurityEvent securityEvent;

  public SecurityEventTokenEntity() {}

  public SecurityEventTokenEntity(
      TokenIssuer issuer, ClientId clientId, SecurityEvent securityEvent) {
    this.issuer = issuer;
    this.clientId = clientId;
    this.securityEvent = securityEvent;
  }

  public TokenIssuer issuer() {
    return issuer;
  }

  public String issuerValue() {
    return issuer.value();
  }

  public ClientId clientId() {
    return clientId;
  }

  public String clientIdValue() {
    return clientId.value();
  }

  public SecurityEvent securityEvent() {
    return securityEvent;
  }

  public String securityEventAsString() {
    return securityEvent.type().name();
  }

  public boolean isDefinedEvent() {
    return securityEvent.isDefined();
  }

  public Map<String, Object> eventAsMap() {
    return securityEvent.eventAsMap();
  }
}
