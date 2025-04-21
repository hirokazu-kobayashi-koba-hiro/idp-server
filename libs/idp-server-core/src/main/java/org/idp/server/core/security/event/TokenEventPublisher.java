package org.idp.server.core.security.event;

import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.security.RequestAttributes;

public class TokenEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public TokenEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    TokenEventCreator eventCreator =
        new TokenEventCreator(tenant, oAuthToken, type, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
