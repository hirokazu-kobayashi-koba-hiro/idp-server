package org.idp.server.core.security.ssf;

import java.util.Map;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

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
      TokenIssuer issuer,
      RequestedClientId requestedClientId,
      SharedSecurityEvent sharedSecurityEvent) {
    this.issuer = issuer;
    this.requestedClientId = requestedClientId;
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
